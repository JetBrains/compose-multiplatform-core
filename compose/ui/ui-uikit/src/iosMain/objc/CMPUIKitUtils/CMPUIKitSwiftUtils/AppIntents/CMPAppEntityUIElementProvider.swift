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
import UIKit

@objcMembers
public final class CMPAppEntityUIElementProvider: NSObject {
    private let implementation: CMPAppEntityUIElementProviderImplementation

    public init(
        _ view: UIView,
        descriptorProvider: @escaping () -> [CMPAppEntityDescriptor]
    ) {
        // The Xcode 27 SDK first declares this API; runtime availability is checked below.
#if compiler(>=6.4)
        if #available(iOS 18.4, *) {
            implementation = .live(
                view: view,
                descriptorProvider: descriptorProvider,
                entityIdentifierResolver: .live(bundle: .main)
            )
        } else {
            implementation = .noop
        }
#else
        implementation = .noop
#endif

        super.init()
    }

    public func dispose() {
        implementation.dispose()
    }
}
