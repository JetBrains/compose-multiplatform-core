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

final class CMPPinchTest: XCTestCase {
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
    func testSimulatedPinchZoomsScrollView() {
        let window = appDelegate.window!

        let viewController = ZoomableScrollHostViewController()
        window.rootViewController = viewController
        let scrollView = viewController.scrollView

        XCTAssertEqual(scrollView.zoomScale, 1.0,
                       "Scroll view should start at zoomScale 1.0 before the simulated gesture.")

        let anchor = CGPoint(x: window.bounds.midX, y: window.bounds.midY)

        let pinch = UIEvent.pinch(at: anchor, scale: 1.0, in: window)
        XCTAssertNotNil(pinch, "UITransformEvent is unavailable; synthetic class allocation failed.")
        pumpRunLoop(0.1)

        let stepCount = 5
        let finalScale: CGFloat = 2.0
        for i in 1...stepCount {
            let scale = 1.0 + (finalScale - 1.0) * CGFloat(i) / CGFloat(stepCount)
            pinch?.pinch(byScale: scale, in: window)
            pumpRunLoop(0.1)
        }
        pinch?.endPinch(in: window)
        pumpRunLoop(0.5)

        XCTAssertGreaterThan(
            scrollView.zoomScale, 1.0,
            "UIScrollView did not zoom in response to the synthetic pinch gesture; zoomScale=\(scrollView.zoomScale)."
        )
        XCTAssertGreaterThan(
            viewController.zoomEventCount, 0,
            "UIScrollViewDelegate.scrollViewDidZoom was never called during the simulated pinch."
        )
    }

    @MainActor
    func testSimulatedPinchReachesPinchRecognizer() {
        let window = appDelegate.window!

        let viewController = PinchRecordingViewController()
        window.rootViewController = viewController

        let anchor = CGPoint(x: window.bounds.midX, y: window.bounds.midY)

        let pinch = UIEvent.pinch(at: anchor, scale: 1.0, in: window)
        XCTAssertNotNil(pinch, "UITransformEvent is unavailable; synthetic class allocation failed.")
        pumpRunLoop(0.1)

        // Ramp absolute scale from 1.0 toward 2.0 over N steps — matches what
        // UIKit would emit during a two-finger trackpad zoom-in.
        let stepCount = 5
        let finalScale: CGFloat = 2.0
        for i in 1...stepCount {
            let scale = 1.0 + (finalScale - 1.0) * CGFloat(i) / CGFloat(stepCount)
            pinch?.pinch(byScale: scale, in: window)
            pumpRunLoop(0.1)
        }
        pinch?.endPinch(in: window)

        let deadline = Date().addingTimeInterval(2.0)
        while viewController.events.isEmpty && Date() < deadline {
            pumpRunLoop(0.05)
        }

        XCTAssertFalse(
            viewController.events.isEmpty,
            "UIPinchGestureRecognizer received no events from the simulated pinch stream."
        )
    }
}

private final class ZoomableScrollHostViewController: UIViewController, UIScrollViewDelegate {
    let scrollView = UIScrollView()
    let contentView = UIView()
    private(set) var zoomEventCount = 0

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemBackground

        scrollView.frame = view.bounds
        scrollView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        scrollView.delegate = self
        scrollView.minimumZoomScale = 0.5
        scrollView.maximumZoomScale = 4.0
        scrollView.bouncesZoom = true

        contentView.frame = CGRect(x: 0, y: 0,
                                   width: view.bounds.width,
                                   height: view.bounds.height)
        contentView.backgroundColor = .systemBlue
        scrollView.contentSize = CGSizeMake(5000, 5000)
        scrollView.addSubview(contentView)

        view.addSubview(scrollView)
    }

    func viewForZooming(in scrollView: UIScrollView) -> UIView? {
        return contentView
    }

    func scrollViewDidZoom(_ scrollView: UIScrollView) {
        zoomEventCount += 1
    }
}

private final class PinchRecordingViewController: UIViewController {
    let recognizer = UIPinchGestureRecognizer()
    private(set) var events: [(state: UIGestureRecognizer.State, scale: CGFloat)] = []

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemBackground
        recognizer.addTarget(self, action: #selector(handlePinch(_:)))
        view.addGestureRecognizer(recognizer)
    }

    @objc
    func handlePinch(_ recognizer: UIPinchGestureRecognizer) {
        events.append((state: recognizer.state, scale: recognizer.scale))
    }
}
