// Skiko's shell build otherwise resolves skiko.wasm relative to the working directory.
const runtimeDirectory = String(import.meta.url)
    .replace(/^file:\/\//, '')
    .replace(/\/[^/]+$/, '/');

export function resolveSkikoPath(path) {
    return path.startsWith('/') ? path : runtimeDirectory + path;
}

export const d8SkikoOptions = {
    locateFile: resolveSkikoPath,
    instantiateWasm(imports, receiveInstance) {
        const bytes = read(runtimeDirectory + 'skiko.wasm', 'binary');
        const module = new WebAssembly.Module(bytes);
        const instance = new WebAssembly.Instance(module, imports);
        receiveInstance(instance, module);
        return instance.exports;
    },
};
