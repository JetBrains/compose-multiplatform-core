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

final class CMPPanTest: XCTestCase {
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
        // Run both default and tracking modes — UIKit gesture-action dispatch is
        // observed to fire in one or the other depending on how the event entered
        // the system (touch vs. synthetic UIScrollEvent).
        let end = Date().addingTimeInterval(seconds)
        while Date() < end {
            RunLoop.main.run(mode: .default, before: Date().addingTimeInterval(0.01))
            RunLoop.main.run(mode: .tracking, before: Date().addingTimeInterval(0.01))
        }
    }

    @MainActor
    func testSimulatedDragReachesPanRecognizer() {
        let window = appDelegate.window!

        let viewController = PanRecordingViewController()
        window.rootViewController = viewController

        let start = CGPoint(x: window.bounds.midX, y: window.bounds.midY)
        let perStepDelta = CGPoint(x: 20, y: 30)
        let stepCount = 5

        let scroll = UIEvent.scroll(at: start, delta: perStepDelta, in: window)
        pumpRunLoop(0.1)

        for _ in 0..<stepCount {
            scroll?.scroll(byDelta: perStepDelta, in: window)
            pumpRunLoop(0.1)
        }
        scroll?.end(in: window)
        let deadline = Date().addingTimeInterval(2.0)
        while viewController.states.isEmpty && Date() < deadline {
            pumpRunLoop(0.05)
        }

        if viewController.states.isEmpty {
            XCTFail("UIPanGestureRecognizer received no state transitions from the simulated scroll stream.")
        }

        // Expected per-fire accumulation: the recognizer only transitions to Began
        // on the second dispatch (the first `scrollByDelta`), and UIKit applies a
        // small hysteresis (~10pt on each axis) to the step that triggers the
        // Began state. After that the translation grows by exactly perStepDelta
        // on each subsequent dispatch, so the total is
        //   stepCount × perStepDelta − hysteresis
        // e.g. 5 × (20, 30) − (10, 10) = (90, 140). ±15 accommodates minor UIKit
        // differences without masking a real regression.
        let last = viewController.translations.last ?? .zero
        let expected = CGPoint(
            x: CGFloat(stepCount) * perStepDelta.x,
            y: CGFloat(stepCount) * perStepDelta.y
        )
        XCTAssertEqual(last.x, expected.x, accuracy: 15,
                       "Unexpected accumulated x translation; got \(last.x), translations=\(viewController.translations)")
        XCTAssertEqual(last.y, expected.y, accuracy: 15,
                       "Unexpected accumulated y translation; got \(last.y), translations=\(viewController.translations)")
    }

    @MainActor
    func testSimulatedDragScrollsVerticalScrollView() {
        let window = appDelegate.window!

        let viewController = VerticalScrollHostViewController()
        window.rootViewController = viewController
        let scrollView = viewController.scrollView

        XCTAssertEqual(scrollView.contentOffset.y, 0,
                       "Scroll view should start at top before the simulated gesture.")

        let start = CGPoint(x: window.bounds.midX, y: window.bounds.midY)
        // UIScrollView treats scroll-delta the same way it treats pan translation:
        // a positive Y delta drags content down (overscroll past the top, yielding
        // a negative content offset). To scroll DOWN through content — advancing
        // `contentOffset.y` — we feed a negative Y delta.
        let perStepDelta = CGPoint(x: 0, y: -40)
        let stepCount = 6

        let scroll = UIEvent.scroll(at: start, delta: perStepDelta, in: window)
        pumpRunLoop(0.1)

        for _ in 0..<stepCount {
            scroll?.scroll(byDelta: perStepDelta, in: window)
            pumpRunLoop(0.1)
        }
        scroll?.end(in: window)
        pumpRunLoop(0.5)

        XCTAssertGreaterThan(
            scrollView.contentOffset.y, 0,
            "UIScrollView did not scroll down in response to the synthetic trackpad gesture; contentOffset=\(scrollView.contentOffset)."
        )
    }
}

private final class VerticalScrollHostViewController: UIViewController {
    let scrollView = UIScrollView()

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemBackground

        scrollView.frame = view.bounds
        scrollView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        scrollView.contentSize = CGSize(width: view.bounds.width, height: 3000)
        scrollView.backgroundColor = .systemGray5
        scrollView.alwaysBounceVertical = true

        let content = UIView(frame: CGRect(x: 0, y: 0, width: view.bounds.width, height: 3000))
        content.backgroundColor = .systemBlue
        scrollView.addSubview(content)

        view.addSubview(scrollView)
    }
}

private final class PanRecordingViewController: UIViewController {
    private(set) var states: [UIGestureRecognizer.State] = []
    private(set) var translations: [CGPoint] = []

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemBackground

        let pan = UIPanGestureRecognizer(target: self, action: #selector(handlePan(_:)))
        pan.allowedScrollTypesMask = .all
        view.addGestureRecognizer(pan)
    }

    @objc
    func handlePan(_ recognizer: UIPanGestureRecognizer) {
        states.append(recognizer.state)
        translations.append(recognizer.translation(in: view))
    }
}
