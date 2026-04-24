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

import UIKit
import XCTest

final class CMPHoverTest: XCTestCase {
   private var appDelegate: MockAppDelegate!

   override func setUpWithError() throws {
       super.setUp()
       appDelegate = MockAppDelegate()
       UIApplication.shared.delegate = appDelegate
       appDelegate.setUpClearWindow()
   }

   override func tearDownWithError() throws {
       super.tearDown()
       appDelegate?.cleanUp()
       appDelegate = nil
   }

   private func pumpRunLoop(_ seconds: TimeInterval) {
       let end = Date().addingTimeInterval(seconds)
       while Date() < end {
           RunLoop.main.run(mode: .default, before: Date().addingTimeInterval(0.01))
           RunLoop.main.run(mode: .tracking, before: Date().addingTimeInterval(0.01))
       }
   }

   @MainActor
   func testSimulatedHoverReachesHoverRecognizer() {
       let window = appDelegate.window!

       let viewController = HoverRecordingViewController()
       window.rootViewController = viewController

       let start = CGPoint(x: window.bounds.midX, y: window.bounds.midY)

       let hover = UIEvent.hover(at: start, in: window)
       XCTAssertNotNil(hover, "UIHoverEvent is unavailable; synthetic class allocation failed.")
       pumpRunLoop(0.1)

       var expectedPoints: [CGPoint] = [start]

       // Walk the cursor across the view to generate multiple hover-moved dispatches.
       let stepCount = 5
       let step = CGPoint(x: 12, y: 8)
       for i in 1...stepCount {
           let target = CGPoint(x: start.x + CGFloat(i) * step.x,
                                y: start.y + CGFloat(i) * step.y)
           hover?.hoverMove(to: target, in: window)
           expectedPoints.append(target)
           pumpRunLoop(0.1)
       }
       hover?.endHover(in: window)
       // endHover replays the last anchor, so the recognizer observes it twice.
       expectedPoints.append(expectedPoints.last!)

       let deadline = Date().addingTimeInterval(2.0)
       while viewController.receivedLocations.isEmpty && Date() < deadline {
           pumpRunLoop(0.05)
       }

       // The view fills the window, so window-space == view-space here.
       XCTAssertEqual(
           viewController.receivedLocations, expectedPoints,
           "UIHoverGestureRecognizer received hover events at unexpected points."
       )
   }
}

private final class HoverRecordingViewController: UIViewController {
   private let recognizer = UIHoverGestureRecognizer()
   private(set) var receivedLocations: [CGPoint] = []

   override func viewDidLoad() {
       super.viewDidLoad()
       view.backgroundColor = .systemBackground
       recognizer.addTarget(self, action: #selector(handleHover(_:)))
       view.addGestureRecognizer(recognizer)
   }

   @objc
   func handleHover(_ recognizer: UIHoverGestureRecognizer) {
       receivedLocations.append(recognizer.location(in: view))
   }
}
