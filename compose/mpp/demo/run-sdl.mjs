import { createRequire } from "module";
import { fileURLToPath } from "url";
import path from "path";
import fs from "fs";

// Forward to compose/mpp/demo/runtimes/node/run-sdl.mjs
const require = createRequire(import.meta.url);
const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const targetRunner = path.resolve(__dirname, "runtimes/node/run-sdl.mjs");
if (fs.existsSync(targetRunner)) {
    await import(`file://${targetRunner}`);
} else {
    throw new Error(`Runner script not found at: ${targetRunner}`);
}
