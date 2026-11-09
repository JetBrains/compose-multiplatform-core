import './polyfills.mjs';
import { d8BenchmarksRunner } from './mpp-benchmarks.mjs';

await d8BenchmarksRunner(Array.from(arguments).join(' '));
console.log('Finished');
