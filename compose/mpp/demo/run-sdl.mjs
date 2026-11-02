import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";
import { execSync } from "node:child_process";
import path from "node:path";
import fs from "node:fs";

const require = createRequire(import.meta.url);
const __dirname = path.dirname(fileURLToPath(import.meta.url));

// Resolve native Skiko SDL2 OpenGL addon
const defaultSkikoNativePath = "/Users/dustin.feucht/projects/skiko/samples/SkiaNonWebSample/native-skiko/build/Release/native_skiko.node";
const nativeSkikoPath = process.env.SKIKO_NATIVE_GL_ADDON ?? defaultSkikoNativePath;

if (!fs.existsSync(nativeSkikoPath)) {
    const nativeSkikoDir = path.dirname(path.dirname(nativeSkikoPath));
    const skiaDir = process.env.SKIA_DIR || (fs.existsSync("/Users/dustin.feucht/projects/skia") ? "/Users/dustin.feucht/projects/skia" : null);
    if (fs.existsSync(path.join(nativeSkikoDir, "binding.gyp")) && skiaDir) {
        try {
            console.log(`Auto-building native Skiko addon at ${nativeSkikoDir}...`);
            execSync(`SKIA_DIR="${skiaDir}" npx node-gyp rebuild`, {
                cwd: nativeSkikoDir,
                stdio: "ignore"
            });
        } catch (e) {
            console.warn(`Auto-building native Skiko addon failed: ${e.message}`);
        }
    }
}

let nativeGL = null;
if (fs.existsSync(nativeSkikoPath)) {
    try {
        nativeGL = require(nativeSkikoPath);
        globalThis.__skikoNativeGL = nativeGL;
        globalThis.__skikoNativeHost = nativeGL;
    } catch (e) {
        console.warn(`Could not load native Skiko addon from ${nativeSkikoPath}: ${e.message}`);
    }
} else {
    console.warn(`Native Skiko addon not found at ${nativeSkikoPath}.`);
}

const windowWidth = 1024;
const windowHeight = 768;
let windowTitle = "Compose MPP Demo (WASM + Native SDL)";

const glNativeFn = (name) => {
    if (!nativeGL) return null;
    if (typeof nativeGL.getGLFunction === "function") {
        const fn = nativeGL.getGLFunction(name);
        if (typeof fn === "function") return fn.bind(nativeGL);
    }
    if (typeof nativeGL[name] === "function") {
        return nativeGL[name].bind(nativeGL);
    }
    return null;
};

class MockWebGL2RenderingContext {
    constructor() {
        return new Proxy(this, {
            get(target, prop) {
                if (prop in target) {
                    return target[prop];
                }
                if (typeof prop === "string") {
                    const fn = glNativeFn(prop) ?? glNativeFn(`gl${prop.charAt(0).toUpperCase()}${prop.slice(1)}`);
                    if (fn) return fn;
                }
                return (...args) => {
                    if (prop === "getError") return 0;
                    if (prop === "checkFramebufferStatus") return 0x8CD5;
                    if (prop === "createProgram" || prop === "createShader") return 1;
                    if (prop === "getProgramParameter" || prop === "getShaderParameter") return 1;
                    if (prop === "getUniformLocation") return 0;
                    if (prop === "getAttribLocation") return 0;
                    return 0;
                };
            }
        });
    }

    createShader(type) {
        const fn = glNativeFn("glCreateShader");
        return fn ? fn(type) : 1;
    }

    shaderSource(shader, source) {
        if (!nativeGL?.glShaderSource || !capturedMemory) return;
        let transformed = String(source);
        if (transformed.includes("#version 300 es")) {
            transformed = transformed.replace(
                "#version 300 es",
                "#version 150\n#define mediump\n#define highp\n#define lowp\n#define precision //\n"
            );
            transformed = transformed.replace(/precision\s+(mediump|highp|lowp)\s+[a-zA-Z0-9_]+;/g, "");
            transformed = transformed.replace(/#extension\s+GL_OES_[a-zA-Z0-9_]+\s*:\s*[a-zA-Z]+/g, "");
        }

        const encoded = Buffer.from(transformed + "\0", "utf-8");
        const strOffset = 65536;
        if (capturedMemory.buffer.byteLength < strOffset + encoded.length + 1024) {
            return;
        }
        new Uint8Array(capturedMemory.buffer).set(encoded, strOffset);

        const stringsOffset = 32768;
        const lengthsOffset = 32772;
        new Uint32Array(capturedMemory.buffer)[stringsOffset / 4] = strOffset;
        new Uint32Array(capturedMemory.buffer)[lengthsOffset / 4] = encoded.length - 1;

        nativeGL.glShaderSource(shader, 1, stringsOffset, lengthsOffset);
    }

    compileShader(shader) {
        const fn = glNativeFn("glCompileShader");
        if (fn) fn(shader);
    }

    createProgram() {
        const fn = glNativeFn("glCreateProgram");
        return fn ? fn() : 1;
    }

    attachShader(program, shader) {
        const fn = glNativeFn("glAttachShader");
        if (fn) fn(program, shader);
    }

    linkProgram(program) {
        const fn = glNativeFn("glLinkProgram");
        if (fn) fn(program);
    }

    useProgram(program) {
        const fn = glNativeFn("glUseProgram");
        if (fn) fn(program);
    }

    deleteShader(shader) {
        const fn = glNativeFn("glDeleteShader");
        if (fn) fn(shader);
    }

    deleteProgram(program) {
        const fn = glNativeFn("glDeleteProgram");
        if (fn) fn(program);
    }

    getShaderParameter(shader, pname) {
        return 1;
    }

    getProgramParameter(program, pname) {
        return 1;
    }

    getShaderInfoLog(shader) {
        return "";
    }

    getProgramInfoLog(program) {
        return "";
    }

    getParameter(pname) {
        switch (pname) {
            case 7936: // GL_VENDOR
                return "JetBrains / Skiko";
            case 7937: // GL_RENDERER
                return "Skiko Native GL";
            case 7938: // GL_VERSION
                return "WebGL 2.0 (OpenGL ES 3.0 Native SDL)";
            case 35724: // GL_SHADING_LANGUAGE_VERSION
                return "WebGL GLSL ES 3.00";
            case 0x80A9: // SAMPLES
                return 0;
            default:
                return 0;
        }
    }

    getShaderPrecisionFormat(shaderType, precisionType) {
        return {
            rangeMin: 127,
            rangeMax: 127,
            precision: 23
        };
    }

    getError() {
        return 0;
    }

    getExtension() { return null; }
    getSupportedExtensions() { return []; }
    enable() {}
    disable() {}
    clear() {}
    viewport() {}
}

class MockWebGLRenderingContext extends MockWebGL2RenderingContext {}

globalThis.WebGLRenderingContext = MockWebGLRenderingContext;
globalThis.WebGL2RenderingContext = MockWebGL2RenderingContext;

class NodeCanvas {
    constructor(description) {
        Object.assign(this, description);
        this.style = {};
        this.__skikoNativeContext = 0;
        this._gl = new MockWebGL2RenderingContext();
    }

    getContext(type, attrs) {
        if (nativeGL?.createContext && !this.__skikoNativeContext) {
            const id = nativeGL.createContext(this, attrs);
            this.__skikoNativeContext = id;
        }
        return this._gl;
    }
}

const rootCanvas = new NodeCanvas({
    id: "ComposeTarget",
    width: windowWidth * 2,
    height: windowHeight * 2
});

const canvases = new Map([["ComposeTarget", rootCanvas]]);
const activeCanvas = rootCanvas;

globalThis.window = globalThis;
globalThis.self = globalThis;
globalThis.HTMLCanvasElement = NodeCanvas;
globalThis.devicePixelRatio = 2;

if (!globalThis.Event) {
    globalThis.Event = class Event {
        constructor(type, init = {}) {
            this.type = type;
            this.bubbles = !!init.bubbles;
            this.cancelable = !!init.cancelable;
        }
    };
}
if (!globalThis.UIEvent) {
    globalThis.UIEvent = class UIEvent extends (globalThis.Event || Object) {
        constructor(type, init = {}) {
            super(type, init);
            Object.assign(this, init);
        }
    };
}
if (!globalThis.MouseEvent) {
    globalThis.MouseEvent = class MouseEvent extends (globalThis.UIEvent || globalThis.Event || Object) {
        constructor(type, init = {}) {
            super(type, init);
            Object.assign(this, init);
        }
    };
}
if (!globalThis.WheelEvent) {
    globalThis.WheelEvent = class WheelEvent extends (globalThis.MouseEvent || globalThis.UIEvent || Object) {
        constructor(type, init = {}) {
            super(type, init);
            this.deltaX = init.deltaX || 0;
            this.deltaY = init.deltaY || 0;
            this.deltaZ = init.deltaZ || 0;
            this.deltaMode = init.deltaMode || 0;
        }
    };
}
if (!globalThis.PointerEvent) {
    globalThis.PointerEvent = class PointerEvent extends (globalThis.MouseEvent || Object) {};
}
if (!globalThis.KeyboardEvent) {
    globalThis.KeyboardEvent = class KeyboardEvent extends (globalThis.UIEvent || Object) {};
}

globalThis.addEventListener = () => {};
globalThis.removeEventListener = () => {};
globalThis.dispatchEvent = () => true;

const mockNavigator = {
    language: "en-US",
    languages: ["en-US", "en"],
    userAgent: `Node.js ${process.version}`,
    appVersion: `5.0 (Node.js ${process.version})`,
    platform: process.platform,
    appName: "Netscape",
    vendor: "Google Inc.",
    maxTouchPoints: 0,
    clipboard: {
        readText: async () => "",
        writeText: async () => {}
    }
};

try {
    Object.defineProperty(globalThis, "navigator", {
        value: mockNavigator,
        configurable: true,
        writable: true
    });
} catch (_) {
    for (const [k, v] of Object.entries(mockNavigator)) {
        try { globalThis.navigator[k] = v; } catch (_) {}
    }
}

globalThis.matchMedia = () => ({
    matches: false,
    media: "",
    onchange: null,
    addListener: () => {},
    removeListener: () => {},
    addEventListener: () => {},
    removeEventListener: () => {},
    dispatchEvent: () => false
});

globalThis.location = {
    origin: "http://localhost",
    pathname: "/",
    search: "?demo=sdl",
    hash: "",
    href: "http://localhost/?demo=sdl"
};

globalThis.document = {
    get title() {
        return windowTitle;
    },
    set title(value) {
        windowTitle = String(value);
        if (nativeGL?.setWindowTitle) {
            nativeGL.setWindowTitle(windowTitle);
        }
    },
    getElementById(id) {
        if (!canvases.has(id)) {
            canvases.set(id, new NodeCanvas({ id, width: windowWidth * 2, height: windowHeight * 2 }));
        }
        return canvases.get(id);
    },
    createElement(tag) {
        if (tag === "canvas") {
            const canvas = new NodeCanvas({ id: "dynamic_canvas", width: windowWidth * 2, height: windowHeight * 2 });
            return canvas;
        }
        return {
            tagName: tag.toUpperCase(),
            style: {},
            children: [],
            appendChild(c) { return c; },
            setAttribute() {},
            getAttribute() { return null; },
            addEventListener() {},
            removeEventListener() {},
            getBoundingClientRect: () => ({ left: 0, top: 0, right: windowWidth, bottom: windowHeight, width: windowWidth, height: windowHeight, x: 0, y: 0 })
        };
    },
    body: {
        appendChild(c) { return c; },
        style: {}
    }
};

// Intercept WebAssembly instantiation to bind Skiko memory to native OpenGL addon
let capturedMemory = null;
const OrigMemory = WebAssembly.Memory;
WebAssembly.Memory = class extends OrigMemory {
    constructor(...args) {
        super(...args);
        capturedMemory = this;
    }
};

const origInstantiate = WebAssembly.instantiate;
WebAssembly.instantiate = async function(bytesOrModule, importObject) {
    const res = await origInstantiate.call(this, bytesOrModule, importObject);
    const instance = res.instance || res;
    const mem = capturedMemory || instance?.exports?.memory || importObject?.env?.memory;
    const malloc = instance?.exports?.malloc || instance?.exports?._malloc || importObject?.env?.malloc;
    if (nativeGL?.attachWasmRuntime && mem && malloc) {
        nativeGL.attachWasmRuntime(mem, malloc);
    }
    return res;
};

const origInstantiateStreaming = WebAssembly.instantiateStreaming;
if (origInstantiateStreaming) {
    WebAssembly.instantiateStreaming = async function(response, importObject) {
        const res = await origInstantiateStreaming.call(this, response, importObject);
        const instance = res.instance || res;
        if (nativeGL?.attachWasmRuntime && instance?.exports?.memory && instance?.exports?.malloc) {
            nativeGL.attachWasmRuntime(instance.exports.memory, instance.exports.malloc);
        }
        return res;
    };
}

// Polyfill XMLHttpRequest for Emscripten loading skiko.wasm from file:// URI in Node.js
class MockXMLHttpRequest {
    constructor() {
        this.status = 0;
        this.response = null;
        this.onload = null;
        this.onerror = null;
        this.responseType = "";
    }
    open(method, url, async) {
        this.url = String(url);
    }
    send() {
        try {
            let filePath = this.url;
            if (filePath.startsWith("file://")) {
                filePath = fileURLToPath(filePath);
            }
            if (!fs.existsSync(filePath)) {
                for (const dir of resourceDirs) {
                    const candidate = path.resolve(dir, path.basename(filePath));
                    if (fs.existsSync(candidate)) {
                        filePath = candidate;
                        break;
                    }
                }
            }
            const buf = fs.readFileSync(filePath);
            this.status = 200;
            const ab = buf.buffer.slice(buf.byteOffset, buf.byteOffset + buf.byteLength);
            this.response = ab;
            if (this.onload) {
                setTimeout(() => {
                    if (this.onload) this.onload();
                }, 0);
            }
        } catch (e) {
            this.status = 404;
            if (this.onerror) {
                setTimeout(() => {
                    if (this.onerror) this.onerror(e);
                }, 0);
            }
        }
    }
}
globalThis.XMLHttpRequest = MockXMLHttpRequest;

// Locate the compiled Wasm application
const searchModulePaths = [
    path.resolve(__dirname, "../../../out/compose-multiplatform-core/build/wasm/packages/mpp-demo/kotlin/mpp-demo.mjs"),
    path.resolve(__dirname, "build/compileSync/wasmJs/main/developmentExecutable/kotlin/mpp-demo.mjs"),
    path.resolve(__dirname, "build/dist/wasmJs/productionExecutable/mpp-demo.mjs"),
];

let wasmModulePath = null;
for (const p of searchModulePaths) {
    if (fs.existsSync(p)) {
        wasmModulePath = p;
        break;
    }
}

const resourceDirs = [
    path.resolve(__dirname, "src/webMain/resources"),
    path.resolve(__dirname, "src/commonMain/composeResources"),
    path.resolve(__dirname, "build/processedResources/wasmJs/main"),
    wasmModulePath ? path.dirname(wasmModulePath) : __dirname
];

// Synchronize native Skiko WebAssembly runtime modules if available
if (wasmModulePath) {
    const targetDir = path.dirname(wasmModulePath);
    const candidateDirs = [
        "/Users/dustin.feucht/projects/skiko/samples/SkiaNonWebSample/build/wasm/packages/SkiaNonWebSample/kotlin",
        "/Users/dustin.feucht/projects/skiko/samples/SkikoExtensionsSample/build/wasm/packages/SkikoExtensionsSample/kotlin"
    ];

    const copyFromCandidates = (filename, destFilename = filename) => {
        for (const dir of candidateDirs) {
            const src = path.join(dir, filename);
            if (fs.existsSync(src)) {
                try {
                    fs.copyFileSync(src, path.join(targetDir, destFilename));
                    return true;
                } catch (err) {
                    console.warn(`Could not sync ${filename} from ${dir}: ${err.message}`);
                }
            }
        }
        return false;
    };

    // First copy skiko.mjs from SkiaNonWebSample if available (has native GL support), or fallback to extensions sample
    const nonWebSkikoMjs = "/Users/dustin.feucht/projects/skiko/samples/SkiaNonWebSample/build/wasm/packages/SkiaNonWebSample/kotlin/skiko.mjs";
    if (fs.existsSync(nonWebSkikoMjs)) {
        try {
            fs.copyFileSync(nonWebSkikoMjs, path.join(targetDir, "skiko.mjs"));
        } catch (_) {}
    } else {
        copyFromCandidates("skiko.mjs");
    }

    // Copy extension-enabled skiko.wasm, skiko-skottie.wasm, and skiko-skottie.mjs
    copyFromCandidates("skiko.wasm");
    copyFromCandidates("skiko-skottie.wasm");
    copyFromCandidates("skiko-skottie.mjs");

    // Ensure skiko.mjs in target directory handles dynamic side modules gracefully
    const targetSkikoMjs = path.join(targetDir, "skiko.mjs");
    if (fs.existsSync(targetSkikoMjs)) {
        try {
            let content = fs.readFileSync(targetSkikoMjs, "utf-8");
            let patched = false;
            if (content.includes('if (!wasmTable) throw new Error("Skiko core WASM does not export __indirect_function_table");')) {
                content = content.replace(
                    'if (!wasmTable) throw new Error("Skiko core WASM does not export __indirect_function_table");',
                    'if (!wasmTable) { wasmTable = new WebAssembly.Table({ initial: 2048, element: "anyfunc" }); }'
                );
                patched = true;
            }
            if (content.includes('throw new Error(`Unresolved Skiko side-module import: ${String(prop)}`);')) {
                content = content.replace(
                    'throw new Error(`Unresolved Skiko side-module import: ${String(prop)}`);',
                    'if (prop === "_Z8skStringPv") return (p) => {}; return (...args) => { console.warn(`Unresolved Skiko side-module import: ${String(prop)}`); return 0; };'
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
            if (content.includes('fallbackStackPointer ||= new WebAssembly.Global({value: "i32", mutable: true}, 0);')) {
                content = content.replace(
                    'fallbackStackPointer ||= new WebAssembly.Global({value: "i32", mutable: true}, 0);',
                    'fallbackStackPointer ||= new WebAssembly.Global({value: "i32", mutable: true}, (wasmExports.malloc ? wasmExports.malloc(131072) + 131072 : 131072));'
                );
                patched = true;
            }
            if (content.includes('const buf = new Uint8Array(wasmExports.memory.buffer, buf_ptr, buf_len);')) {
                content = content.replace(
                    'const buf = new Uint8Array(wasmExports.memory.buffer, buf_ptr, buf_len);',
                    'if (buf_ptr < 0 || buf_ptr + buf_len > wasmExports.memory.buffer.byteLength) return 0; const buf = new Uint8Array(wasmExports.memory.buffer, buf_ptr, buf_len);'
                );
                patched = true;
            }
            if (content.includes("const callNativeGL = (name, args) => {")) {
                const patchSnippet = `const callNativeGL = (name, args) => {
    if (name === "glShaderSource" && wasmExports?.memory) {
        const [shader, count, stringsOffset, lengthsOffset] = args;
        const u32 = new Uint32Array(wasmExports.memory.buffer);
        const i32 = new Int32Array(wasmExports.memory.buffer);
        const u8 = new Uint8Array(wasmExports.memory.buffer);
        let fullSource = "";
        for (let i = 0; i < count; i++) {
            const strPtr = u32[(stringsOffset / 4) + i];
            const len = lengthsOffset ? i32[(lengthsOffset / 4) + i] : -1;
            let str = "";
            if (len >= 0) {
                str = new TextDecoder().decode(u8.subarray(strPtr, strPtr + len));
            } else {
                let end = strPtr;
                while (u8[end] !== 0) end++;
                str = new TextDecoder().decode(u8.subarray(strPtr, end));
            }
            fullSource += str;
        }

        if (fullSource.includes("#version 300 es")) {
            fullSource = fullSource.replace(
                "#version 300 es",
                "#version 150\\n#define mediump\\n#define highp\\n#define lowp\\n#define precision //\\n"
            );
            fullSource = fullSource.replace(/precision\\s+(mediump|highp|lowp)\\s+[a-zA-Z0-9_]+;/g, "");
            fullSource = fullSource.replace(/#extension\\s+GL_OES_[a-zA-Z0-9_]+\\s*:\\s*[a-zA-Z]+/g, "");
        }

        const encoded = new TextEncoder().encode(fullSource + "\\0");
        const newStrPtr = wasmExports.malloc(encoded.length);
        new Uint8Array(wasmExports.memory.buffer).set(encoded, newStrPtr);

        const newStringsPtr = wasmExports.malloc(4);
        new Uint32Array(wasmExports.memory.buffer)[newStringsPtr / 4] = newStrPtr;

        const newLengthsPtr = wasmExports.malloc(4);
        new Int32Array(wasmExports.memory.buffer)[newLengthsPtr / 4] = encoded.length - 1;

        syncNativeGLMemory();
        nativeGLCalls.add(name);
        const res = nativeGLHost.glShaderSource(shader, 1, newStringsPtr, newLengthsPtr);
        wasmExports.free(newStrPtr);
        wasmExports.free(newStringsPtr);
        wasmExports.free(newLengthsPtr);
        return { handled: true, value: res };
    }`;
                content = content.replace("const callNativeGL = (name, args) => {", patchSnippet);
                patched = true;
            }
            if (patched) {
                fs.writeFileSync(targetSkikoMjs, content, "utf-8");
            }
        } catch (_) {}
    }
}

// Polyfill fetch for loading resource files (.ttf, .json, .svg)
globalThis.fetch = async (url) => {
    const urlStr = String(url);
    let resolvedPath = urlStr;
    if (resolvedPath.startsWith("file://")) {
        resolvedPath = fileURLToPath(resolvedPath);
    }

    if (!fs.existsSync(resolvedPath)) {
        const basename = path.basename(resolvedPath);
        for (const dir of resourceDirs) {
            const candidate = path.resolve(dir, basename);
            if (fs.existsSync(candidate)) {
                resolvedPath = candidate;
                break;
            }
        }
    }

    if (fs.existsSync(resolvedPath)) {
        const buffer = fs.readFileSync(resolvedPath);
        return {
            ok: true,
            status: 200,
            text: async () => buffer.toString("utf-8"),
            json: async () => JSON.parse(buffer.toString("utf-8")),
            arrayBuffer: async () => buffer.buffer.slice(buffer.byteOffset, buffer.byteOffset + buffer.byteLength)
        };
    }

    throw new Error(`Resource not found: ${url}`);
};

// Create the native SDL2 window if addon is present
if (nativeGL?.createWindow) {
    nativeGL.createWindow(windowWidth, windowHeight, windowTitle);
}

// Render & animation frame loop
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

function handleNativePointerEvent(type, x, y, button, scrollDeltaX, scrollDeltaY) {
    if (globalThis.__dispatchPointerEvent) {
        try {
            globalThis.__dispatchPointerEvent(type, x, y, button, scrollDeltaX, scrollDeltaY);
        } catch (err) {
            console.error("Error dispatching pointer event:", err);
        }
    }
}

function pollEvents() {
    if (running && nativeGL?.pollEvents && !nativeGL.pollEvents(handleNativePointerEvent)) {
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
        } catch (err) {
            console.error("Error in animation frame callback:", err);
        }
    }

    if (nativeGL?.present) {
        if (activeCanvas && activeCanvas.__skikoNativeContext) {
            nativeGL.present(activeCanvas.__skikoNativeContext);
        } else {
            const nonZero = [...canvases.values()].find((c) => c.__skikoNativeContext);
            if (nonZero) {
                nativeGL.present(nonZero.__skikoNativeContext);
            }
        }
    }

    if (animationFrameCallbacks.size > 0) scheduleFrame();
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

// Periodic event polling timer
const eventTimer = setInterval(pollEvents, 16);

if (!wasmModulePath) {
    console.error("Could not find compiled mpp-demo.mjs in build directories. Please compile the project first.");
    if (nativeGL?.destroyWindow) nativeGL.destroyWindow();
    process.exit(1);
}

console.log(`Launching Compose MPP Demo (DOM-less) from: ${wasmModulePath}`);

try {
    await import(wasmModulePath);
    console.log("Compose MPP Demo is running. Close the window to exit.");
    scheduleFrame();
    await closePromise;
} catch (err) {
    console.error("Failed to execute Wasm module in Node SDL runner:", err);
} finally {
    clearInterval(eventTimer);
    if (frameTimer !== null) clearTimeout(frameTimer);
    for (const canvas of canvases.values()) {
        if (canvas.__skikoNativeContext && nativeGL?.destroyContext) {
            nativeGL.destroyContext(canvas.__skikoNativeContext);
        }
    }
    if (nativeGL?.destroyWindow) {
        nativeGL.destroyWindow();
    }
    process.exit(0);
}
