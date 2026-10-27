import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";
import path from "node:path";
import fs from "node:fs";

const require = createRequire(import.meta.url);
const __dirname = path.dirname(fileURLToPath(import.meta.url));

// Resolve native Skiko SDL2 OpenGL addon
const defaultSkikoNativePath = "/Users/dustin.feucht/projects/skiko/samples/SkiaNonWebSample/native-skiko/build/Release/native_skiko.node";
const nativeSkikoPath = process.env.SKIKO_NATIVE_GL_ADDON ?? defaultSkikoNativePath;

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

class MockWebGL2RenderingContext {
    constructor() {
        return new Proxy(this, {
            get(target, prop) {
                if (prop in target) {
                    return target[prop];
                }
                if (typeof prop === "string" && nativeGL && typeof nativeGL[prop] === "function") {
                    return nativeGL[prop].bind(nativeGL);
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
    const nativeSkikoBuildDir = "/Users/dustin.feucht/projects/skiko/samples/SkiaNonWebSample/build/wasm/packages/SkiaNonWebSample/kotlin";
    if (fs.existsSync(nativeSkikoBuildDir)) {
        const skikoMjs = path.join(nativeSkikoBuildDir, "skiko.mjs");
        const skikoWasm = path.join(nativeSkikoBuildDir, "skiko.wasm");
        const destMjs = path.join(targetDir, "skiko.mjs");
        const destWasm = path.join(targetDir, "skiko.wasm");
        if (fs.existsSync(skikoMjs) && fs.existsSync(skikoWasm)) {
            try {
                fs.copyFileSync(skikoMjs, destMjs);
                fs.copyFileSync(skikoWasm, destWasm);
            } catch (err) {
                console.warn(`Could not sync native skiko artifacts: ${err.message}`);
            }
        }
    }

    // Ensure skiko-skottie.mjs does not fail eager side-module initialization if present
    const skottieMjsPath = path.join(targetDir, "skiko-skottie.mjs");
    if (fs.existsSync(skottieMjsPath)) {
        try {
            let skottieContent = fs.readFileSync(skottieMjsPath, "utf-8");
            if (skottieContent.includes("registerSkikoWasmReadyCallback(() => ensureSkottieLoaded());")) {
                skottieContent = skottieContent.replace(
                    "registerSkikoWasmReadyCallback(() => ensureSkottieLoaded());",
                    "// registerSkikoWasmReadyCallback(() => ensureSkottieLoaded());"
                );
                fs.writeFileSync(skottieMjsPath, skottieContent);
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
