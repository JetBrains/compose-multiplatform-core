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

import Foundation

// This file exists only to exercise Swift -> ObjC -> Kotlin interop in integration tests.
// Do not add production API here.
@objc(CMPUIKitSwiftInterop)
public protocol CMPUIKitSwiftInterop {
    var seed: Int { get }
    func combinedValueWithSuffix(_ suffix: String) -> String
}

@objcMembers
public final class CMPUIKitSwiftInteropBox: NSObject, CMPUIKitSwiftInterop {
    public let seed: Int

    public init(seed: Int) {
        self.seed = seed
    }

    public func combinedValueWithSuffix(_ suffix: String) -> String {
        "swift-\(seed)-\(suffix)"
    }
}
