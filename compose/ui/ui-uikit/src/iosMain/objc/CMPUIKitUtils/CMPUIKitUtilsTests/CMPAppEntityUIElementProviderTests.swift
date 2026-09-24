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
import CoreGraphics
import UIKit
import XCTest

@testable import CMPUIKitSwiftUtils

@available(iOS 18.4, *)
final class CMPAppEntityUIElementProviderTests: XCTestCase {
    @MainActor
    func testLiveProviderInstallsAndDisposesUIKitProvider() {
        let view = UIView()
        let implementation = CMPAppEntityUIElementProviderImplementation.live(
            view: view,
            descriptorProvider: { [] },
            entityIdentifierResolver: resolver()
        )

        XCTAssertNotNil(view.appEntityUIElementProvider)

        implementation.dispose()

        XCTAssertNil(view.appEntityUIElementProvider)
    }

    func testMakeUIElementsIncludesVisibleDescriptors() {
        let elements = CMPAppEntityUIElementProviderImplementation.makeUIElements(
            descriptors: [
                descriptor(id: "visible", bounds: CGRect(x: 10, y: 20, width: 30, height: 40)),
                descriptor(id: "outside", bounds: CGRect(x: 200, y: 200, width: 30, height: 40)),
            ],
            visibleRects: [CGRect(x: 0, y: 0, width: 100, height: 100)],
            includeSelected: false,
            entityIdentifierResolver: resolver()
        )

        XCTAssertEqual(elements.count, 1)
        XCTAssertEqual(elements[0].identifier.identifier, "visible")
        XCTAssertEqual(elements[0].bounds, CGRect(x: 10, y: 20, width: 30, height: 40))
        XCTAssertFalse(elements[0].state.isSelected)
    }

    func testMakeUIElementsIncludesSelectedDescriptorOutsideVisibleRects() {
        let elements = CMPAppEntityUIElementProviderImplementation.makeUIElements(
            descriptors: [
                descriptor(id: "selected", bounds: CGRect(x: 200, y: 200, width: 30, height: 40), selected: true),
                descriptor(id: "unselected", bounds: CGRect(x: 240, y: 240, width: 30, height: 40)),
            ],
            visibleRects: [CGRect(x: 0, y: 0, width: 100, height: 100)],
            includeSelected: true,
            entityIdentifierResolver: resolver()
        )

        XCTAssertEqual(elements.count, 1)
        XCTAssertEqual(elements[0].identifier.identifier, "selected")
        XCTAssertTrue(elements[0].state.isSelected)
    }

    func testMakeUIElementsOmitsDescriptorsTheResolverCannotResolve() {
        let elements = CMPAppEntityUIElementProviderImplementation.makeUIElements(
            descriptors: [descriptor(id: "missing", bounds: CGRect(x: 10, y: 20, width: 30, height: 40))],
            visibleRects: [CGRect(x: 0, y: 0, width: 100, height: 100)],
            includeSelected: false,
            entityIdentifierResolver: .init(resolve: { _, _ in nil })
        )

        XCTAssertTrue(elements.isEmpty)
    }

    private func descriptor(id: String, bounds: CGRect, selected: Bool = false) -> CMPAppEntityDescriptor {
        CMPAppEntityDescriptor(
            typeName: "TestEntity",
            rawIdentifier: id,
            bounds: bounds,
            selected: selected
        )
    }

    private func resolver() -> CMPAppIntentsEntityIdentifierResolver {
        CMPAppIntentsEntityIdentifierResolver { _, identifier in
            EntityIdentifier(for: TestEntity.self, identifier: identifier)
        }
    }
}

@available(iOS 18.4, *)
private struct TestEntity: AppEntity {
    let id: String

    static let typeDisplayRepresentation: TypeDisplayRepresentation = "Test Entity"
    static let defaultQuery = TestEntityQuery()

    var displayRepresentation: DisplayRepresentation { "Test Entity" }
}

@available(iOS 18.4, *)
private struct TestEntityQuery: EntityQuery {
    func entities(for identifiers: [String]) async throws -> [TestEntity] {
        []
    }
}
#endif
