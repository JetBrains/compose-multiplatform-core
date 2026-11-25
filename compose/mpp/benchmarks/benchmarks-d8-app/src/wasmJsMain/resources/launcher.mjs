import './polyfills.mjs';

if (arguments.length !== 1) {
    throw new Error('Usage: launcher.mjs -- <benchmarkName>');
}
const { launch } = await import('./__BENCHMARK_MODULE__.mjs');
await launch(arguments[0]);
console.log('BENCHMARK_COMPLETE');
