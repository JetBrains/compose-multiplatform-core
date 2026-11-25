// Only the browser APIs needed by offscreen benchmarks are provided here.
// D8's native setTimeout drives coroutine dispatch; no vsync is emulated.
globalThis.window = globalThis;
globalThis.isSecureContext = false;
globalThis.navigator = {
    languages: ['en-US', 'en'],
    language: 'en-US',
    userAgent: 'D8',
    platform: 'D8',
};

// Coroutine dispatch uses window messages. Language changes never occur offscreen.
const windowListeners = new Map([
    ['message', new Set()],
    ['languagechange', new Set()],
]);
globalThis.addEventListener = (type, listener) => {
    const listeners = windowListeners.get(type);
    if (!listeners) throw new Error(`Unsupported D8 window event: ${type}`);
    listeners.add(listener);
};
globalThis.removeEventListener = (type, listener) => windowListeners.get(type)?.delete(listener);
globalThis.postMessage = (data) => {
    setTimeout(() => {
        const event = { data, source: globalThis, stopPropagation() {} };
        for (const listener of [...windowListeners.get('message')]) listener(event);
    }, 0);
};

// Preserve native scheduling, but implement cancellation because D8 lacks clearTimeout.
const scheduleTimeout = globalThis.setTimeout.bind(globalThis);
const timers = new Set();
let nextTimer = 0;
globalThis.setTimeout = (callback, delay = 0, ...args) => {
    const id = ++nextTimer;
    timers.add(id);
    scheduleTimeout(() => {
        if (timers.delete(id)) callback(...args);
    }, delay);
    return id;
};
globalThis.clearTimeout = (id) => timers.delete(id);

// Enable real manual GC by starting D8 with --expose-gc. Never fake a successful GC.
if (typeof globalThis.gc !== 'function') {
    console.warn('Manual GC is unavailable; start D8 with --expose-gc for benchmark runs.');
}

class AbortSignalPolyfill {
    aborted = false;
    reason = undefined;
    listeners = new Set();
    addEventListener(type, listener) {
        if (type === 'abort') this.listeners.add(listener);
    }
    removeEventListener(type, listener) {
        if (type === 'abort') this.listeners.delete(listener);
    }
    throwIfAborted() {
        if (this.aborted) throw this.reason;
    }
}
globalThis.AbortSignal = AbortSignalPolyfill;
globalThis.AbortController = class {
    signal = new AbortSignalPolyfill();
    abort(reason = new Error('Resource request aborted')) {
        if (this.signal.aborted) return;
        this.signal.aborted = true;
        this.signal.reason = reason;
        for (const listener of this.signal.listeners) listener();
        this.signal.listeners.clear();
    }
};

// Resource consumers need binary blobs, not text decoding or XML/DOM parsing.
class ResourceBlob {
    constructor(bytes, type = '') {
        this.bytes = bytes;
        this.type = type;
    }
    get size() { return this.bytes.byteLength; }
    async arrayBuffer() {
        return this.bytes.slice().buffer;
    }
    slice(start = 0, end = this.size, type = '') {
        return new ResourceBlob(this.bytes.slice(start, end), type);
    }
}

globalThis.Blob = ResourceBlob;
globalThis.Response = class {
    constructor(bytes, type) {
        this.bytes = bytes;
        this.type = type;
        this.ok = true;
        this.status = 200;
        this.statusText = 'OK';
    }
    async blob() { return new ResourceBlob(this.bytes, this.type); }
    async arrayBuffer() { return this.bytes.slice().buffer; }
};

const runtimeDirectory = String(import.meta.url)
    .replace(/^file:\/\//, '')
    .replace(/\/[^/]+$/, '/');

globalThis.fetch = async (request, options = {}) => {
    options.signal?.throwIfAborted();
    if (options.method && options.method.toUpperCase() !== 'GET') {
        throw new Error('D8 fetch only supports reading local resources');
    }
    let path = String(request);
    if (/^[a-z][a-z0-9+.-]*:/i.test(path) && !path.startsWith('file://')) {
        throw new Error(`D8 fetch does not support network URLs: ${path}`);
    }
    path = decodeURIComponent(path.replace(/^file:\/\//, ''));
    const filePath = path.startsWith('/') ? path : runtimeDirectory + path;
    let bytes;
    try {
        bytes = new Uint8Array(read(filePath, 'binary'));
    } catch (error) {
        throw new Error(`Cannot read D8 resource ${filePath}: ${error}`);
    }
    const type = /\.(ttf|otf)$/i.test(path) ? 'application/octet-stream'
        : /\.png$/i.test(path) ? 'image/png'
        : /\.jpe?g$/i.test(path) ? 'image/jpeg'
        : '';
    return new Response(bytes, type);
};
