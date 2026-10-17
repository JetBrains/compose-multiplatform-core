/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { awaitSkiko } from "./skiko-runtime.mjs";
export * from "./skiko-runtime.mjs";

// Complete native initialization before Kotlin constructs any benchmark or test state.
await awaitSkiko;

// RootNodeOwner constructs web platform services even for headless listener/layer workloads.
// Install a minimal DOM stub after Skiko initializes, so its loader still detects Node rather than a browser.
const { performance } = globalThis;
globalThis.window = {
    performance,
    navigator: {},
    isSecureContext: false,
    document: {}
};
globalThis.document = globalThis.window.document;