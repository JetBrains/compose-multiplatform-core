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

#if compiler(>=6.4)
import AppIntents
#endif
import Foundation
import UIKit

struct CMPAppEntityUIElementProviderImplementation {
    let dispose: () -> Void
}

#if compiler(>=6.4)
@available(iOS 18.4, *)
extension CMPAppEntityUIElementProviderImplementation {
    static func live(
        view: UIView,
        descriptorProvider: @escaping () -> [CMPAppEntityDescriptor],
        entityIdentifierResolver: CMPAppIntentsEntityIdentifierResolver
    ) -> Self {
        view.appEntityUIElementProvider = { _, context in
            let visibleRects: [CGRect] = context.requests.compactMap {
                guard case let .visible(rect) = $0 else { return nil }
                return rect
            }
            return makeUIElements(
                descriptors: descriptorProvider(),
                visibleRects: visibleRects,
                includeSelected: context.requests.contains(.selected),
                entityIdentifierResolver: entityIdentifierResolver
            )
        }

        return Self(
            dispose: { [weak view] in
                view?.appEntityUIElementProvider = nil
            }
        )
    }

    static func makeUIElements(
        descriptors: [CMPAppEntityDescriptor],
        visibleRects: [CGRect],
        includeSelected: Bool,
        entityIdentifierResolver: CMPAppIntentsEntityIdentifierResolver
    ) -> [AppEntityUIElement] {
        descriptors.compactMap { descriptor in
            let isVisible = visibleRects.contains(where: descriptor.bounds.intersects)
            guard isVisible || (includeSelected && descriptor.selected) else { return nil }
            guard let entityIdentifier = entityIdentifierResolver.resolve(
                descriptor.typeName,
                descriptor.rawIdentifier
            ) else {
                return nil
            }

            return AppEntityUIElement(
                identifier: entityIdentifier,
                bounds: descriptor.bounds,
                state: .init(isSelected: descriptor.selected)
            )
        }
    }
}
#endif

extension CMPAppEntityUIElementProviderImplementation {
    static let noop = Self(
        dispose: {}
    )
}
