/*
 * Copyright 2025 The Android Open Source Project
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

import AVKit
import XCTest

final class CMPViewTests: XCTestCase {
    var appDelegate: MockAppDelegate!
    private var additionalWindows: [UIWindow] = []
    var rootView: UIView? {
        get {
            appDelegate.window!.rootViewController?.view.subviews.first
        }
        set {
            appDelegate.window!.rootViewController?.view.subviews.forEach {
                $0.removeFromSuperview()
            }
            if let newValue {
                appDelegate.window!.rootViewController?.view.addSubview(newValue)
                newValue.frame = appDelegate.window!.rootViewController?.view.bounds ?? .zero
            }
        }
    }

    override func setUpWithError() throws {
        super.setUp()

        appDelegate = MockAppDelegate.installWithClearWindow()
        TestView.counter = 1
    }

    override func tearDownWithError() throws {
        super.tearDown()

        for window in additionalWindows {
            window.rootViewController = nil
            window.isHidden = true
        }
        additionalWindows.removeAll()

        appDelegate?.cleanUp()
        appDelegate = nil
    }

    private func makeAdditionalWindow() -> UIWindow {
        let window = UIWindow(frame: UIScreen.main.bounds)
        window.backgroundColor = .systemBackground
        window.layer.speed = 10000
        window.rootViewController = UIViewController()
        window.makeKeyAndVisible()

        additionalWindows.append(window)

        return window
    }
        
    @MainActor
    private func expect(
        view: TestView,
        toBeInHierarchy inHierarchy: Bool,
        line: Int = #line
    ) async {
        await expect(timeout: 5.0, line: line) {
            view.viewIsInWindowHierarchy == inHierarchy
        }
    }

    @MainActor
    private func expect(
        view: TestView,
        toBeAppeared isAppeared: Bool,
        line: Int = #line
    ) async {
        await expect(timeout: 5.0, line: line) {
            view.isViewAppeared == isAppeared
        }
    }
    
    @MainActor
    public func testNotAttached() async {
        let view = TestView()
        await expect(view: view, toBeInHierarchy: false)
    }
    
    @MainActor
    public func testViewAttach() async {
        let view = TestView()
        rootView = view
        await expect(view: view, toBeInHierarchy: true)
        
        rootView = nil
        await expect(view: view, toBeInHierarchy: false)
    }

    @MainActor
    public func testViewThroughSuperviewAttach() async {
        let view = TestView()
        view.frame = .init(x: 0, y: 0, width: 100, height: 100)
        let superview = UIView()
        superview.addSubview(view)
        
        await expect(view: view, toBeInHierarchy: false)

        rootView = superview
        await expect(view: view, toBeInHierarchy: true)
        
        rootView = nil
        await expect(view: view, toBeInHierarchy: false)
    }

    @MainActor
    public func testTransferToAnotherWindowRestartsContainer() async {
        let view = TestView()
        rootView = view

        await expect(view: view, toBeInHierarchy: true)
        XCTAssertEqual(view.didEnterWindowHierarchyCallsCount, 1)
        XCTAssertEqual(view.didLeaveWindowHierarchyCallsCount, 0)

        let secondWindow = makeAdditionalWindow()
        secondWindow.rootViewController!.view.addSubview(view)

        // Moving the view to another window must force-stop the container and start it again
        // against the new window.
        await expect { view.didEnterWindowHierarchyCallsCount == 2 }
        XCTAssertEqual(view.didLeaveWindowHierarchyCallsCount, 1)
        await expect(view: view, toBeInHierarchy: true)

        // The view never left a window hierarchy, so its appearance state is untouched.
        XCTAssertTrue(view.isViewAppeared)
    }

    @MainActor
    public func testTransferToAnotherWindowKeepsContainerStartedAfterwards() async {
        let view = TestView()
        rootView = view
        await expect(view: view, toBeInHierarchy: true)

        let secondWindow = makeAdditionalWindow()
        secondWindow.rootViewController!.view.addSubview(view)

        await expect { view.didEnterWindowHierarchyCallsCount == 2 }

        // The pending hierarchy containment check must not stop the restarted container.
        try? await Task.sleep(nanoseconds: 2_000_000_000) // 2s, several check periods

        XCTAssertTrue(view.viewIsInWindowHierarchy)
        XCTAssertEqual(view.didEnterWindowHierarchyCallsCount, 2)
        XCTAssertEqual(view.didLeaveWindowHierarchyCallsCount, 1)
    }

    @MainActor
    public func testMoveWithinSameWindowKeepsContainerStarted() async {
        let firstSuperview = UIView()
        let secondSuperview = UIView()
        rootView = firstSuperview
        appDelegate.window!.rootViewController!.view.addSubview(secondSuperview)

        let view = TestView()
        firstSuperview.addSubview(view)

        await expect(view: view, toBeInHierarchy: true)
        XCTAssertEqual(view.didEnterWindowHierarchyCallsCount, 1)

        secondSuperview.addSubview(view)

        try? await Task.sleep(nanoseconds: 1_000_000_000) // 1s, a couple of check periods

        // The window did not change, so the container must stay started.
        XCTAssertTrue(view.viewIsInWindowHierarchy)
        XCTAssertEqual(view.didEnterWindowHierarchyCallsCount, 1)
        XCTAssertEqual(view.didLeaveWindowHierarchyCallsCount, 0)
    }

    @MainActor
    public func testRepeatedWindowTransfersKeepCallbacksBalanced() async {
        let view = TestView()
        rootView = view
        await expect(view: view, toBeInHierarchy: true)

        for transfer in 1...3 {
            let window = makeAdditionalWindow()
            window.rootViewController!.view.addSubview(view)

            await expect { view.didEnterWindowHierarchyCallsCount == transfer + 1 }
            XCTAssertEqual(view.didLeaveWindowHierarchyCallsCount, transfer)
        }

        view.removeFromSuperview()

        await expect(view: view, toBeInHierarchy: false)
        try? await Task.sleep(nanoseconds: 2_000_000_000)

        XCTAssertEqual(view.didEnterWindowHierarchyCallsCount, 4)
        XCTAssertEqual(view.didLeaveWindowHierarchyCallsCount, 4)
    }

    @MainActor
    public func testLifecycleDelegate() async {
        let delegate = LifecycleDelegate()

        autoreleasepool {
            let view = TestView(delegate: delegate)
            rootView = view
        }
        
        await expect { delegate.containerWillAppearCallsCount == 1 }
        
        rootView = nil

        await expect { delegate.containerWillAppearCallsCount == 1 }
        await expect { delegate.containerDidDisappearCallsCount == 1 }
        await expect { delegate.containerWillDeallocCallsCount == 1 }
    }
    
    @MainActor
    public func testViewAppeared() async {
        let view = TestView()
        rootView = view
        await expect(view: view, toBeAppeared: true)
        
        rootView = nil
        await expect(view: view, toBeAppeared: false)
    }
}

private class TestView: CMPView {
    public static var counter: Int = 1
    
    private let id: Int
    
    public var viewIsInWindowHierarchy: Bool = false
    public var isViewAppeared: Bool = false
    public private(set) var didEnterWindowHierarchyCallsCount = 0
    public private(set) var didLeaveWindowHierarchyCallsCount = 0

    init(delegate: CMPComposeContainerLifecycleDelegate? = nil) {
        id = TestView.counter
        TestView.counter += 1
        super.init(lifecycleDelegate: delegate)
    }
    
    required init?(coder: NSCoder) {
        nil
    }
    
    override func viewDidAppear() {
        isViewAppeared = true
    }
    
    override func viewDidDisappear() {
        isViewAppeared = false
    }
    
    override func viewDidEnterWindowHierarchy() {
        print("TestView_\(id) didEnterWindowHierarchy")
        XCTAssertFalse(viewIsInWindowHierarchy)
        viewIsInWindowHierarchy = true
        didEnterWindowHierarchyCallsCount += 1
    }

    override func viewDidLeaveWindowHierarchy() {
        print("TestView_\(id) didLeaveWindowHierarchy")
        XCTAssertTrue(viewIsInWindowHierarchy)
        viewIsInWindowHierarchy = false
        didLeaveWindowHierarchyCallsCount += 1
    }
    
    override func userInterfaceStyleDidChange() {}
}
