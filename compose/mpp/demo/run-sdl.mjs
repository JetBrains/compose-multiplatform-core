import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";
import path from "node:path";
import fs from "node:fs";

const require = createRequire(import.meta.url);
const __dirname = path.dirname(fileURLToPath(import.meta.url));
const projectRoot = path.resolve(__dirname, "../../..");

const nativeSkikoPath = process.env.SKIKO_NATIVE_GL_ADDON ??
    "/Users/dustin.feucht/projects/skiko/samples/SkiaNonWebSample/native-skiko/build/Release/native_skiko.node";

if (!fs.existsSync(nativeSkikoPath)) {
    console.error(`Native Skiko addon not found at: ${nativeSkikoPath}`);
    process.exit(1);
}

const nativeGL = require(nativeSkikoPath);

const windowWidth = 1024;
const windowHeight = 768;
let windowTitle = "Compose MPP Demo (Wasm + Native SDL)";

const nativeSkikoHost = new Proxy({}, {
    get(target, prop) {
        if (prop === "createContext") {
            return (canvas, attr) => {
                if (typeof canvas === "object" && canvas !== null) {
                    if (!canvas.width || typeof canvas.width !== "number" || canvas.width <= 0) {
                        canvas.width = windowWidth;
                    }
                    if (!canvas.height || typeof canvas.height !== "number" || canvas.height <= 0) {
                        canvas.height = windowHeight;
                    }
                }
                const id = nativeGL.createContext(canvas, attr);
                if (canvas && typeof canvas === "object") {
                    canvas.__skikoNativeContext = id;
                }
                return id;
            };
        }
        const val = nativeGL[prop];
        if (typeof val === "function") {
            return val.bind(nativeGL);
        }
        return val;
    },
    has(target, prop) {
        return prop in nativeGL;
    }
});

// Expose Skiko native GL bridge
globalThis.__skikoNativeGL = nativeSkikoHost;
globalThis.__skikoNativeHost = nativeSkikoHost;

// Minimal DOM polyfill hierarchy
class NodePolyfill {
    constructor(nodeName = "DIV") {
        this.nodeName = nodeName.toUpperCase();
        this.tagName = this.nodeName;
        this.nodeType = nodeName.startsWith("#") ? 3 : 1;
        this.childNodes = [];
        this.children = [];
        this.parentNode = null;
        this.id = "";
        this.textContent = "";
        this.innerHTML = "";
        this.style = new Proxy({}, {
            get: (target, prop) => target[prop] ?? "",
            set: (target, prop, value) => {
                target[prop] = value;
                return true;
            }
        });
        this.style.setProperty = (k, v) => { this.style[k] = v; };
        this.style.getPropertyValue = (k) => this.style[k] ?? "";
        this.style.removeProperty = (k) => { delete this.style[k]; };
        this.eventListeners = new Map();
        this.attributes = new Map();
        this.dataset = {};
        this.width = windowWidth;
        this.height = windowHeight;
        this.__skikoNativeContext = 0;
    }

    get firstChild() {
        return this.childNodes[0] ?? null;
    }

    get lastChild() {
        return this.childNodes[this.childNodes.length - 1] ?? null;
    }

    get firstElementChild() {
        return this.children[0] ?? null;
    }

    get lastElementChild() {
        return this.children[this.children.length - 1] ?? null;
    }

    get parentElement() {
        return this.parentNode;
    }

    get ownerDocument() {
        return globalThis.document;
    }

    get nextSibling() {
        if (!this.parentNode) return null;
        const i = this.parentNode.childNodes.indexOf(this);
        return this.parentNode.childNodes[i + 1] ?? null;
    }

    get previousSibling() {
        if (!this.parentNode) return null;
        const i = this.parentNode.childNodes.indexOf(this);
        return i > 0 ? this.parentNode.childNodes[i - 1] : null;
    }

    get nextElementSibling() {
        if (!this.parentNode) return null;
        const i = this.parentNode.children.indexOf(this);
        return this.parentNode.children[i + 1] ?? null;
    }

    get previousElementSibling() {
        if (!this.parentNode) return null;
        const i = this.parentNode.children.indexOf(this);
        return i > 0 ? this.parentNode.children[i - 1] : null;
    }

    appendChild(child) {
        if (!child) return child;
        child.parentNode = this;
        this.childNodes.push(child);
        if (child.nodeType === 1) {
            this.children.push(child);
        }
        return child;
    }

    removeChild(child) {
        const index = this.childNodes.indexOf(child);
        if (index !== -1) {
            this.childNodes.splice(index, 1);
            const childIndex = this.children.indexOf(child);
            if (childIndex !== -1) {
                this.children.splice(childIndex, 1);
            }
            child.parentNode = null;
        }
        return child;
    }

    insertBefore(newNode, referenceNode) {
        if (!referenceNode) return this.appendChild(newNode);
        const index = this.childNodes.indexOf(referenceNode);
        if (index === -1) return this.appendChild(newNode);
        newNode.parentNode = this;
        this.childNodes.splice(index, 0, newNode);
        if (newNode.nodeType === 1) {
            const refChildIndex = this.children.indexOf(referenceNode);
            if (refChildIndex !== -1) {
                this.children.splice(refChildIndex, 0, newNode);
            } else {
                this.children.push(newNode);
            }
        }
        return newNode;
    }

    prepend(...nodes) {
        for (let i = nodes.length - 1; i >= 0; i--) {
            const node = nodes[i];
            if (typeof node === "string") {
                this.insertBefore(document.createTextNode(node), this.firstChild);
            } else if (node) {
                this.insertBefore(node, this.firstChild);
            }
        }
    }

    append(...nodes) {
        for (const node of nodes) {
            if (typeof node === "string") {
                this.appendChild(document.createTextNode(node));
            } else if (node) {
                this.appendChild(node);
            }
        }
    }

    contains(other) {
        let curr = other;
        while (curr) {
            if (curr === this) return true;
            curr = curr.parentNode;
        }
        return false;
    }

    setAttribute(name, value) {
        this.attributes.set(name, String(value));
        this[name] = value;
    }

    getAttribute(name) {
        return this.attributes.get(name) ?? this[name] ?? null;
    }

    removeAttribute(name) {
        this.attributes.delete(name);
        delete this[name];
    }

    hasAttribute(name) {
        return this.attributes.has(name) || name in this;
    }

    addEventListener(type, listener, options) {
        if (!this.eventListeners.has(type)) {
            this.eventListeners.set(type, []);
        }
        this.eventListeners.get(type).push(listener);
    }

    removeEventListener(type, listener) {
        const list = this.eventListeners.get(type);
        if (list) {
            this.eventListeners.set(type, list.filter(l => l !== listener));
        }
    }

    dispatchEvent(event) {
        if (!event.target) event.target = this;
        event.currentTarget = this;
        const list = this.eventListeners.get(event.type) || [];
        for (const listener of list) {
            if (typeof listener === "function") {
                listener.call(this, event);
            } else if (listener && typeof listener.handleEvent === "function") {
                listener.handleEvent(event);
            }
        }
        return !event.defaultPrevented;
    }

    focus() {}
    blur() {}

    getBoundingClientRect() {
        return {
            left: 0,
            top: 0,
            right: this.width || windowWidth,
            bottom: this.height || windowHeight,
            width: this.width || windowWidth,
            height: this.height || windowHeight,
            x: 0,
            y: 0
        };
    }

    hasChildNodes() {
        return this.childNodes.length > 0;
    }

    replaceChildren(...newChildren) {
        this.childNodes = [];
        this.children = [];
        for (const child of newChildren) {
            this.appendChild(child);
        }
    }

    attachShadow(init) {
        const shadow = new NodePolyfill("SHADOW-ROOT");
        shadow.mode = init?.mode || "open";
        this.shadowRoot = shadow;
        return shadow;
    }

    getContext(type, attributes) {
        if (type === "webgl2" || type === "webgl") {
            return {
                canvas: this,
                getSupportedExtensions: () => [],
                getExtension: (name) => null,
                getShaderPrecisionFormat: (shadertype, precisiontype) => ({
                    rangeMin: 127,
                    rangeMax: 127,
                    precision: 23
                }),
                getError: () => 0,
                isContextLost: () => false,
                finish: () => {},
                flush: () => {},
                viewport: () => {},
                getParameter: (p) => {
                    switch (p) {
                        case 35724: return "WebGL GLSL ES 3.00";
                        case 7938: return "WebGL 2.0 (OpenGL ES 3.0)";
                        case 7936: case 37445: return "Skiko / JetBrains";
                        case 7937: case 37446: return "Native Skiko SDL2 OpenGL";
                        default: return 0;
                    }
                }
            };
        }
        if (type === "2d") {
            return {
                canvas: this,
                fillRect() {},
                clearRect() {},
                getImageData() { return { data: new Uint8Array() }; },
                putImageData() {},
                createImageData() { return { data: new Uint8Array() }; },
                setTransform() {},
                drawImage() {},
                save() {},
                restore() {}
            };
        }
        return null;
    }
}

const createdCanvases = new Set();
const bodyElement = new NodePolyfill("BODY");
const headElement = new NodePolyfill("HEAD");
const rootCanvas = new NodePolyfill("CANVAS");
rootCanvas.id = "ComposeTarget";
createdCanvases.add(rootCanvas);

globalThis.window = globalThis;
globalThis.self = globalThis;
globalThis.global = globalThis;

globalThis.Node = NodePolyfill;
globalThis.Element = NodePolyfill;
globalThis.HTMLElement = NodePolyfill;
globalThis.HTMLDivElement = NodePolyfill;
globalThis.HTMLCanvasElement = NodePolyfill;
globalThis.HTMLInputElement = NodePolyfill;
globalThis.HTMLTextAreaElement = NodePolyfill;
globalThis.HTMLSpanElement = NodePolyfill;
globalThis.HTMLParagraphElement = NodePolyfill;

globalThis.WebGLRenderingContext = class WebGLRenderingContext {};
globalThis.WebGL2RenderingContext = class WebGL2RenderingContext extends globalThis.WebGLRenderingContext {};
globalThis.WebGLBuffer = class WebGLBuffer {};
globalThis.WebGLFramebuffer = class WebGLFramebuffer {};
globalThis.WebGLProgram = class WebGLProgram {};
globalThis.WebGLRenderbuffer = class WebGLRenderbuffer {};
globalThis.WebGLShader = class WebGLShader {};
globalThis.WebGLTexture = class WebGLTexture {};
globalThis.WebGLUniformLocation = class WebGLUniformLocation {};
globalThis.WebGLActiveInfo = class WebGLActiveInfo {};
globalThis.WebGLShaderPrecisionFormat = class WebGLShaderPrecisionFormat {};

globalThis.devicePixelRatio = 1;
globalThis.innerWidth = windowWidth;
globalThis.innerHeight = windowHeight;
globalThis.outerWidth = windowWidth;
globalThis.outerHeight = windowHeight;
globalThis.scrollX = 0;
globalThis.scrollY = 0;
globalThis.pageXOffset = 0;
globalThis.pageYOffset = 0;

globalThis.visualViewport = {
    width: windowWidth,
    height: windowHeight,
    offsetLeft: 0,
    offsetTop: 0,
    pageLeft: 0,
    pageTop: 0,
    scale: 1,
    addEventListener() {},
    removeEventListener() {}
};

globalThis.customElements = {
    define(name, constructor, options) {},
    get(name) { return undefined; },
    whenDefined(name) { return Promise.resolve(); },
    upgrade(root) {}
};

globalThis.FontFace = class {
    constructor() {}
    async load() { return this; }
};

globalThis.Event = class {
    constructor(type, eventInitDict) {
        this.type = type;
        this.bubbles = !!eventInitDict?.bubbles;
        this.cancelable = !!eventInitDict?.cancelable;
        this.defaultPrevented = false;
    }
    preventDefault() { this.defaultPrevented = true; }
    stopPropagation() {}
    stopImmediatePropagation() {}
};

globalThis.CustomEvent = class extends globalThis.Event {
    constructor(type, eventInitDict) {
        super(type, eventInitDict);
        this.detail = eventInitDict?.detail ?? null;
    }
};

globalThis.UIEvent = class extends globalThis.Event {};
globalThis.MouseEvent = class extends globalThis.UIEvent {};
globalThis.PointerEvent = class extends globalThis.MouseEvent {};
globalThis.WheelEvent = class extends globalThis.MouseEvent {};
globalThis.KeyboardEvent = class extends globalThis.UIEvent {};
globalThis.FocusEvent = class extends globalThis.UIEvent {};
globalThis.InputEvent = class extends globalThis.UIEvent {};
globalThis.TouchEvent = class extends globalThis.UIEvent {};

globalThis.isSecureContext = true;
globalThis.location = {
    origin: "http://localhost",
    pathname: "/",
    search: "",
    hash: "",
    href: "http://localhost/",
    hostname: "localhost",
    host: "localhost",
    port: "",
    protocol: "http:",
    assign(url) {},
    replace(url) {},
    reload() {},
    toString() { return this.href; }
};

globalThis.history = {
    length: 1,
    state: null,
    scrollRestoration: "auto",
    pushState(data, title, url) {
        this.state = data;
        if (url) globalThis.location.pathname = url;
    },
    replaceState(data, title, url) {
        this.state = data;
        if (url) globalThis.location.pathname = url;
    },
    back() {},
    forward() {},
    go(delta) {}
};

Object.defineProperty(globalThis, "navigator", {
    configurable: true,
    value: {
        language: "en-US",
        languages: ["en-US", "en"],
        platform: process.platform,
        userAgent: `Node.js ${process.version}`,
        clipboard: {
            readText: async () => "",
            writeText: async () => {}
        }
    }
});

globalThis.document = {
    readyState: "complete",
    compatMode: "CSS1Compat",
    get title() {
        return windowTitle;
    },
    set title(value) {
        windowTitle = String(value);
        nativeGL.setWindowTitle(windowTitle);
    },
    body: bodyElement,
    head: headElement,
    documentElement: bodyElement,
    createElement(tag) {
        const el = new NodePolyfill(tag);
        if (tag.toLowerCase() === "canvas") {
            el.width = windowWidth;
            el.height = windowHeight;
            createdCanvases.add(el);
        }
        return el;
    },
    createTextNode(text) {
        const node = new NodePolyfill("#text");
        node.textContent = String(text);
        return node;
    },
    getElementById(id) {
        if (id === "ComposeTarget" || id === "c1" || id === "root" || id === "app" || id === rootCanvas.id) return rootCanvas;
        function findInNode(node) {
            if (node.id === id) return node;
            for (const child of node.childNodes) {
                const found = findInNode(child);
                if (found) return found;
            }
            return null;
        }
        return findInNode(bodyElement);
    },
    querySelector(selector) {
        if (selector === "canvas" || selector === "#ComposeTarget" || selector === "#c1") return rootCanvas;
        if (selector === "body") return bodyElement;
        if (selector === "head") return headElement;
        return null;
    },
    querySelectorAll(selector) {
        return [];
    },
    hasFocus() {
        return true;
    },
    addEventListener(type, listener, options) {
        bodyElement.addEventListener(type, listener, options);
    },
    removeEventListener(type, listener, options) {
        bodyElement.removeEventListener(type, listener, options);
    },
    dispatchEvent(event) {
        return bodyElement.dispatchEvent(event);
    }
};

globalThis.addEventListener = (type, l, opts) => bodyElement.addEventListener(type, l, opts);
globalThis.removeEventListener = (type, l, opts) => bodyElement.removeEventListener(type, l, opts);
globalThis.dispatchEvent = (e) => bodyElement.dispatchEvent(e);

class MediaQueryList extends NodePolyfill {
    constructor(media = "") {
        super("MEDIA-QUERY-LIST");
        this.media = media;
        this.matches = false;
        this.onchange = null;
    }
    addListener(fn) { this.addEventListener("change", fn); }
    removeListener(fn) { this.removeEventListener("change", fn); }
}
globalThis.MediaQueryList = MediaQueryList;

globalThis.matchMedia = (query) => new MediaQueryList(query);

globalThis.ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
};

globalThis.MutationObserver = class {
    observe() {}
    disconnect() {}
    takeRecords() { return []; }
};

// Search directories for font, wasm, and asset resolution
const assetDirs = [
    path.resolve(projectRoot, "out/compose-multiplatform-core/build/wasm/packages/mpp-demo/kotlin"),
    path.resolve(projectRoot, "out/compose-multiplatform-core/compose/mpp/demo/build/compileSync/wasmJs/main/developmentExecutable/kotlin"),
    path.resolve(projectRoot, "out/compose-multiplatform-core/build/wasm/packages_imported"),
    path.resolve(__dirname, "src/webMain/resources"),
    path.resolve(__dirname, "src/commonMain/composeResources"),
    process.cwd()
];

function resolveAssetPath(input) {
    let cleanPath = "";
    if (typeof input === "string") {
        if (input.startsWith("file://")) {
            return fileURLToPath(input);
        }
        cleanPath = input.replace(/^\.\//, "").replace(/^http:\/\/localhost\//, "");
    } else if (input instanceof URL) {
        if (input.protocol === "file:") {
            return fileURLToPath(input);
        }
        cleanPath = input.pathname.replace(/^\//, "");
    } else if (input && typeof input.url === "string") {
        return resolveAssetPath(input.url);
    }

    if (path.isAbsolute(cleanPath) && fs.existsSync(cleanPath)) {
        return cleanPath;
    }

    for (const dir of assetDirs) {
        const candidate = path.resolve(dir, cleanPath);
        if (fs.existsSync(candidate)) {
            return candidate;
        }
    }
    return null;
}

// Polyfill XMLHttpRequest for Emscripten's local file reading
globalThis.XMLHttpRequest = class {
    constructor() {
        this.status = 0;
        this.response = null;
        this.onload = null;
        this.onerror = null;
        this.responseType = "";
    }
    open(method, url, async) {
        this._url = url;
    }
    send() {
        const localPath = resolveAssetPath(this._url);
        if (localPath && fs.existsSync(localPath)) {
            try {
                const buffer = fs.readFileSync(localPath);
                this.status = 200;
                this.response = buffer.buffer.slice(buffer.byteOffset, buffer.byteOffset + buffer.byteLength);
                if (this.onload) setTimeout(() => this.onload(), 0);
            } catch (err) {
                if (this.onerror) setTimeout(() => this.onerror(err), 0);
            }
        } else {
            if (this.onerror) setTimeout(() => this.onerror(new Error(`File not found: ${this._url}`)), 0);
        }
    }
};

// Polyfill fetch for local asset and wasm reading
const originalFetch = globalThis.fetch;
globalThis.fetch = async (input, init) => {
    const localPath = resolveAssetPath(input);
    if (localPath && fs.existsSync(localPath)) {
        const buffer = await fs.promises.readFile(localPath);
        return new Response(buffer, {
            status: 200,
            statusText: "OK",
            headers: {
                "Content-Type": localPath.endsWith(".wasm") ? "application/wasm" : "application/octet-stream"
            }
        });
    }
    if (originalFetch) {
        try {
            return await originalFetch(input, init);
        } catch (e) {
            // fallback
        }
    }
    throw new Error(`Asset not found: ${input}`);
};

const origLog = console.log;
console.log = (...args) => {
    origLog("[CONSOLE.LOG]", ...args);
};

const origError = console.error;
console.error = (...args) => {
    try {
        origError("[CONSOLE.ERROR]", ...args);
    } catch (e) {
        origError("[CONSOLE.ERROR (safe)]", String(args));
    }
};

globalThis.reportError = (err) => {
    console.error("[REPORT_ERROR]", err);
};

globalThis.onerror = (message, source, lineno, colno, error) => {
    console.error("[WINDOW_ONERROR]", message, source, lineno, colno, error);
};

process.on("unhandledRejection", (reason, p) => {
    console.error("[UNHANDLED_REJECTION]", reason);
});
process.on("uncaughtException", (err) => {
    console.error("[UNCAUGHT_EXCEPTION]", err, err?.stack);
});

// Create native SDL window
nativeGL.createWindow(windowWidth, windowHeight, windowTitle);

let running = true;
let nextAnimationFrameId = 1;
let frameTimer = null;
const animationFrameCallbacks = new Map();
let closeResolve;
const closePromise = new Promise((resolve) => {
    closeResolve = resolve;
});

function stop() {
    if (!running) return;
    running = false;
    if (frameTimer !== null) clearTimeout(frameTimer);
    frameTimer = null;
    animationFrameCallbacks.clear();
    closeResolve();
}

function pollEvents() {
    if (running && !nativeGL.pollEvents()) {
        stop();
    }
}

function scheduleFrame() {
    if (!running || frameTimer !== null) return;
    frameTimer = setTimeout(runFrame, 16);
}

function runFrame() {
    frameTimer = null;
    if (!running) return;
    pollEvents();
    if (!running) return;

    const callbacks = [...animationFrameCallbacks.values()];
    animationFrameCallbacks.clear();
    const timestamp = performance.now();
    for (const callback of callbacks) {
        try {
            callback(timestamp);
        } catch (e) {
            console.error("Frame callback error:", e);
        }
    }

    for (const canvas of createdCanvases) {
        if (canvas.__skikoNativeContext) {
            nativeGL.present(canvas.__skikoNativeContext);
        }
    }

    if (animationFrameCallbacks.size > 0) {
        scheduleFrame();
    }
}

globalThis.requestAnimationFrame = (callback) => {
    const id = nextAnimationFrameId++;
    animationFrameCallbacks.set(id, callback);
    scheduleFrame();
    return id;
};

globalThis.cancelAnimationFrame = (id) => {
    animationFrameCallbacks.delete(id);
};

globalThis.__skikoRequestAnimationFrame = globalThis.requestAnimationFrame;
globalThis.__skikoCancelAnimationFrame = globalThis.cancelAnimationFrame;

const eventInterval = setInterval(pollEvents, 16);

// Sync Skiko native runtime if available
const skikoSourceDir = "/Users/dustin.feucht/projects/skiko/samples/SkiaNonWebSample/build/wasm/packages/SkiaNonWebSample/kotlin";
const possibleWasmModules = [
    path.resolve(projectRoot, "out/compose-multiplatform-core/build/wasm/packages/mpp-demo/kotlin/mpp-demo.mjs"),
    path.resolve(projectRoot, "out/compose-multiplatform-core/compose/mpp/demo/build/compileSync/wasmJs/main/developmentExecutable/kotlin/mpp-demo.mjs"),
    path.resolve(__dirname, "build/compileSync/wasmJs/main/developmentExecutable/kotlin/mpp-demo.mjs"),
    path.resolve(__dirname, "build/wasm/packages/mpp-demo/kotlin/mpp-demo.mjs")
];

let wasmModulePath = null;
for (const p of possibleWasmModules) {
    if (fs.existsSync(p)) {
        wasmModulePath = p;
        break;
    }
}

if (!wasmModulePath) {
    console.error("Could not find compiled mpp-demo.mjs. Please build it first.");
    process.exit(1);
}

// Auto-sync skiko.mjs and skiko.wasm to the module dir if not already matching
const moduleDir = path.dirname(wasmModulePath);
if (fs.existsSync(skikoSourceDir)) {
    for (const filename of ["skiko.mjs", "skiko.wasm", "js-skiko-reexport-symbols.mjs"]) {
        const src = path.join(skikoSourceDir, filename);
        const dst = path.join(moduleDir, filename);
        if (fs.existsSync(src) && (!fs.existsSync(dst) || fs.statSync(src).size !== fs.statSync(dst).size)) {
            fs.copyFileSync(src, dst);
        }
    }
}

// Ensure skiko.mjs in target directory handles dynamic side modules gracefully
const targetSkikoMjs = path.join(moduleDir, "skiko.mjs");
if (fs.existsSync(targetSkikoMjs)) {
    let content = fs.readFileSync(targetSkikoMjs, "utf-8");
    let patched = false;
    if (content.includes('if (!wasmTable) throw new Error("Skiko core WASM does not export __indirect_function_table");')) {
        content = content.replace(
            'if (!wasmTable) throw new Error("Skiko core WASM does not export __indirect_function_table");',
            'if (!wasmTable) { wasmTable = new WebAssembly.Table({ initial: 1024, element: "anyfunc" }); }'
        );
        patched = true;
    }
    if (content.includes('throw new Error(`Unresolved Skiko side-module imports: ${unresolvedImports.join(", ")}`);')) {
        content = content.replace(
            'throw new Error(`Unresolved Skiko side-module imports: ${unresolvedImports.join(", ")}`);',
            'console.warn(`Unresolved Skiko side-module imports: ${unresolvedImports.join(", ")}`);'
        );
        patched = true;
    }
    if (patched) {
        fs.writeFileSync(targetSkikoMjs, content, "utf-8");
    }
}

try {
    console.log(`Loading Compose MPP Demo from: ${wasmModulePath}`);
    await import(wasmModulePath);
    console.log("Compose MPP Demo is running in native SDL2 window. Close window to exit.");
    await closePromise;
} catch (err) {
    console.error("Error running Compose WASM Demo:", err);
} finally {
    clearInterval(eventInterval);
    if (frameTimer !== null) clearTimeout(frameTimer);
    for (const canvas of createdCanvases) {
        if (canvas.__skikoNativeContext) {
            nativeGL.destroyContext(canvas.__skikoNativeContext);
        }
    }
    nativeGL.destroyWindow();
}
