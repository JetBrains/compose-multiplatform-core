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

        runHoverSession(label: "single",
                        from: start,
                        step: CGPoint(x: 12, y: 8),
                        stepCount: 5,
                        viewController: viewController,
                        window: window)
    }

    @MainActor
    func testSimulatedSeveralHoverSessions() {
        let window = appDelegate.window!

        let viewController = HoverRecordingViewController()
        window.rootViewController = viewController

        let center = CGPoint(x: window.bounds.midX, y: window.bounds.midY)

        runHoverSession(label: "first",
                        from: center,
                        step: CGPoint(x: 12, y: 8),
                        stepCount: 4,
                        viewController: viewController,
                        window: window)

        viewController.reset()

        runHoverSession(label: "second",
                        from: CGPoint(x: center.x - 30, y: center.y + 20),
                        step: CGPoint(x: -10, y: 15),
                        stepCount: 3,
                        viewController: viewController,
                        window: window)
    }

    @MainActor
    private func runHoverSession(label: String,
                                 from start: CGPoint,
                                 step: CGPoint,
                                 stepCount: Int,
                                 viewController: HoverRecordingViewController,
                                 window: UIWindow) {
        let hover = UIEvent.hover(at: start, in: window)
        XCTAssertNotNil(hover, "[\(label)] UIHoverEvent is unavailable; synthetic class allocation failed.")
        pumpRunLoop(0.1)

        var walked: [CGPoint] = []
        for i in 1...stepCount {
            let target = CGPoint(x: start.x + CGFloat(i) * step.x,
                                 y: start.y + CGFloat(i) * step.y)
            hover?.hoverMove(to: target, in: window)
            walked.append(target)
            pumpRunLoop(0.1)
        }
        hover?.endHover(in: window)

        let deadline = Date().addingTimeInterval(2.0)
        while viewController.events.isEmpty && Date() < deadline {
            pumpRunLoop(0.05)
        }

        let events = viewController.events
        if events.isEmpty {
            XCTFail("[\(label)] UIHoverGestureRecognizer received no events.")
            return
        }

        let states = events.map { $0.state }
        XCTAssertEqual(states.first, .began,
                       "[\(label)] Expected first state .began, got \(describe(states.first))")
        XCTAssertEqual(states.last, .ended,
                       "[\(label)] Expected last state .ended, got \(describe(states.last))")

        let changedCount = states.filter { $0 == .changed }.count
        XCTAssertEqual(changedCount, stepCount,
                       "[\(label)] Expected exactly \(stepCount) .changed transitions, got \(changedCount); states=\(states.map(describe))")

        let allowed: Set<UIGestureRecognizer.State> = [.began, .changed, .ended]
        for state in states {
            XCTAssertTrue(allowed.contains(state),
                          "[\(label)] Unexpected state \(describe(state)) in sequence \(states.map(describe))")
        }

        let expectedLocations: [CGPoint] = [start] + walked + [walked.last ?? start]
        let recordedLocations = events.map { $0.location }
        XCTAssertEqual(recordedLocations, expectedLocations,
                       "[\(label)] UIHoverGestureRecognizer received hover events at unexpected points.")
    }
}

private func describe(_ state: UIGestureRecognizer.State?) -> String {
    guard let state = state else { return "nil" }
    switch state {
    case .possible:  return "possible"
    case .began:     return "began"
    case .changed:   return "changed"
    case .ended:     return "ended"
    case .cancelled: return "cancelled"
    case .failed:    return "failed"
    @unknown default: return "unknown(\(state.rawValue))"
    }
}

private final class HoverRecordingViewController: UIViewController {
    private let recognizer = UIHoverGestureRecognizer()
    private(set) var events: [(state: UIGestureRecognizer.State, location: CGPoint)] = []

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemBackground
        recognizer.addTarget(self, action: #selector(handleHover(_:)))
        view.addGestureRecognizer(recognizer)
    }

    func reset() {
        events.removeAll()
    }

    @objc
    func handleHover(_ recognizer: UIHoverGestureRecognizer) {
        events.append((state: recognizer.state, location: recognizer.location(in: view)))
    }
}
