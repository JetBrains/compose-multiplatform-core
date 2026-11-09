/*
 * Copyright 2023 The Android Open Source Project
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

final class CMPViewControllerTests: XCTestCase {
    var appDelegate: MockAppDelegate!
    private var additionalWindows: [UIWindow] = []
    var rootViewController: UIViewController {
        get {
            appDelegate.window!.rootViewController!
        }
        set {
            appDelegate.window!.rootViewController = newValue
        }
    }

    override func setUpWithError() throws {
        super.setUp()

        appDelegate = MockAppDelegate.installWithClearWindow()
        TestViewController.counter = 1
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
        window.rootViewController = UIViewController()
        window.makeKeyAndVisible()

        additionalWindows.append(window)

        return window
    }

    @MainActor
    private func move(_ viewController: UIViewController, to parent: UIViewController) {
        viewController.willMove(toParent: nil)
        viewController.view.removeFromSuperview()
        viewController.removeFromParent()

        parent.addChild(viewController)
        parent.view.addSubview(viewController.view)
        viewController.didMove(toParent: parent)
    }
        
    @MainActor
    private func expect(
        viewController: TestViewController,
        toBeInHierarchy inHierarchy: Bool,
        function: StaticString = #function,
        line: Int = #line
    ) async {
        await expect(timeout: 5.0, function: function, line: line) {
            viewController.viewIsInWindowHierarchy == inHierarchy
        }
    }

    @MainActor
    private func expect(
        viewControllersToBeInHierarchy: [(TestViewController, Bool)],
        function: StaticString = #function,
        line: Int = #line
    ) async {
        await expect(timeout: 5.0, function: function, line: line) {
            viewControllersToBeInHierarchy.reduce(true) { partialResult, pair in
                let (viewController, inHierarchy) = pair

                return partialResult && viewController.viewIsInWindowHierarchy == inHierarchy
            }
        }
    }

    @MainActor
    private func expect(
        viewControllers: [TestViewController],
        toBeInHierarchy inHierarchy: Bool,
        function: StaticString = #function,
        line: Int = #line
    ) async  {
        await expect(viewControllersToBeInHierarchy: viewControllers.map {
            ($0, inHierarchy)
        }, function: function, line: line)
    }

    @MainActor
    public func testNotAttached() async {
        let viewController = TestViewController()
        await expect(viewController: viewController, toBeInHierarchy: false)
    }

    @MainActor
    public func testRootViewController() async {
        let viewController = TestViewController()
        rootViewController = viewController
        await expect(viewController: viewController, toBeInHierarchy: true)

        rootViewController = UIViewController()
        await expect(viewController: viewController, toBeInHierarchy: false)
    }

    @MainActor
    public func testPresentAndDismiss() async {
        let viewController = TestViewController()

        await rootViewController.presentAndWait(viewController)
        await expect(viewController: viewController, toBeInHierarchy: true)

        await rootViewController.dismissAndWait()

        await expect(viewController: viewController, toBeInHierarchy: false)
    }

    @MainActor
    public func testChildController() async {
        let viewController1 = TestViewController()
        let viewController2 = TestViewController()
        await expect(viewControllers: [viewController1, viewController2], toBeInHierarchy: false)

        await rootViewController.presentAndWait(viewController1)
        await expect(viewControllersToBeInHierarchy: [
            (viewController1, true),
            (viewController2, false)
        ])

        viewController1.addChild(viewController2)
        viewController2.didMove(toParent: viewController1)
        viewController1.view.addSubview(viewController2.view)
        await expect(viewControllers: [viewController1, viewController2], toBeInHierarchy: true)

        viewController2.willMove(toParent: nil)
        viewController2.removeFromParent()
        viewController2.view.removeFromSuperview()
        await expect(viewControllersToBeInHierarchy: [
            (viewController1, true),
            (viewController2, false)
        ])

        await rootViewController.dismissAndWait()
        await expect(viewControllers: [viewController1, viewController2], toBeInHierarchy: false)
    }

    @MainActor
    public func testNavigationControllerPresentAndPush() async {
        let viewController1 = TestViewController()
        let viewController2 = TestViewController()
        let viewController3 = TestViewController()

        await expect(viewControllers: [
            viewController1,
            viewController2,
            viewController3
        ], toBeInHierarchy: false)

        let navigationController = UINavigationController(rootViewController: viewController1)

        await rootViewController.presentAndWait(navigationController)

        await expect(viewController: viewController1, toBeInHierarchy: true)
        await expect(viewControllers: [viewController2, viewController3], toBeInHierarchy: false)

        navigationController.pushViewController(viewController2, animated: false)
        await expect(viewControllers: [viewController1, viewController2], toBeInHierarchy: true)
        await expect(viewController: viewController3, toBeInHierarchy: false)

        await navigationController.presentAndWait(viewController3)
        await expect(viewControllers: [viewController1, viewController2, viewController3], toBeInHierarchy: true)

        await viewController3.dismissAndWait()
        await expect(viewControllers: [viewController1, viewController2], toBeInHierarchy: true)
        await expect(viewController: viewController3, toBeInHierarchy: false)

        await navigationController.dismissAndWait()

        await expect(viewControllers: [viewController1, viewController2, viewController3], toBeInHierarchy: false)
    }

    @MainActor
    public func testNavigationControllerPresentAndPush2() async {
        let viewController1 = TestViewController()
        let viewController2 = TestViewController()
        let viewController3 = TestViewController()

        let navigationController = UINavigationController(rootViewController: viewController1)

        await rootViewController.presentAndWait(navigationController)
        navigationController.pushViewController(viewController2, animated: false)
        navigationController.pushViewController(viewController3, animated: false)

        await navigationController.dismissAndWait()

        await expect(viewControllers: [viewController1, viewController2, viewController3], toBeInHierarchy: false)
    }

    @MainActor
    public func testTabBarControllerPresentAndPush() async {
        let viewController1 = TestViewController()
        let viewController2 = TestViewController()
        let viewController3 = TestViewController()

        let tabBarController = UITabBarController()
        tabBarController.viewControllers = [viewController1, viewController2]

        await rootViewController.presentAndWait(tabBarController)

        await expect(viewControllers: [viewController2, viewController3], toBeInHierarchy: false)
        await expect(viewController: viewController1, toBeInHierarchy: true)

        await tabBarController.presentAndWait(viewController3)
        await expect(viewControllers: [viewController1, viewController3], toBeInHierarchy: true)

        await viewController3.dismissAndWait()
        await expect(viewController: viewController1, toBeInHierarchy: true)
        await expect(viewControllers: [viewController2, viewController3], toBeInHierarchy: false)

        await tabBarController.dismissAndWait()

        await expect(viewControllers: [viewController1, viewController2, viewController3], toBeInHierarchy: false)
    }

    @MainActor
    public func testFullscreenPresentationOnTop() async throws {
        let viewController = TestViewController()
        rootViewController = viewController

        await expect(viewController: viewController, toBeInHierarchy: true)

        let urlStr = "https://nonexisting"
        let url = URL(string: urlStr)!
        let player = AVPlayer(url: url)
        let playerController = AVPlayerViewController()
        playerController.player = player

        await viewController.presentAndWait(playerController)
        await expect(viewController: viewController, toBeInHierarchy: true)
        await playerController.dismissAndWait()

        rootViewController = UIViewController()
        await expect(viewController: viewController, toBeInHierarchy: false)
    }

    @MainActor
    public func testFullScreenPresentationSandwich() async {
        let viewController0 = TestViewController()

        rootViewController = viewController0

        let viewController1 = TestViewController()
        viewController1.modalPresentationStyle = .fullScreen

        let viewController2 = TestViewController()
        viewController1.addChild(viewController2)
        viewController1.view.addSubview(viewController2.view)
        viewController2.didMove(toParent: viewController1)

        let viewController3 = TestViewController()
        viewController3.modalPresentationStyle = .fullScreen

        await expect(viewControllersToBeInHierarchy: [
            (viewController0, true),
            (viewController1, false),
            (viewController2, false),
            (viewController3, false),
        ])

        await viewController0.presentAndWait(viewController1)
        await expect(viewControllersToBeInHierarchy: [
            (viewController0, true),
            (viewController1, true),
            (viewController2, true),
            (viewController3, false),
        ])

        await viewController1.presentAndWait(viewController3)
        await expect(viewControllers: [viewController0, viewController1, viewController2, viewController3], toBeInHierarchy: true)

        await viewController0.dismissAndWait()
        await expect(viewControllersToBeInHierarchy: [
            (viewController0, true),
            (viewController1, false),
            (viewController2, false),
            (viewController3, false),
        ])
        rootViewController = UIViewController()
        await expect(viewControllers: [viewController0, viewController1, viewController2, viewController3], toBeInHierarchy: false)
    }

    @MainActor
    public func testMultipleHierarchyReEntrance() async {
        let viewController = TestViewController()

        let navigationController = UINavigationController(rootViewController: UIViewController())

        rootViewController = navigationController
        navigationController.pushViewController(viewController, animated: false)

        await expect(viewControllers: [viewController], toBeInHierarchy: true)

        navigationController.popViewController(animated: false)

        await expect(viewControllers: [viewController], toBeInHierarchy: false)

        navigationController.pushViewController(viewController, animated: false)

        await expect(viewControllers: [viewController], toBeInHierarchy: true)
    }

    @MainActor
    public func testTransferToAnotherWindowRestartsContainer() async {
        let viewController = TestViewController()
        move(viewController, to: rootViewController)

        await expect(viewController: viewController, toBeInHierarchy: true)
        XCTAssertEqual(viewController.didEnterWindowHierarchyCallsCount, 1)
        XCTAssertEqual(viewController.didLeaveWindowHierarchyCallsCount, 0)

        let secondWindow = makeAdditionalWindow()
        move(viewController, to: secondWindow.rootViewController!)

        await expect { viewController.didEnterWindowHierarchyCallsCount == 2 }
        XCTAssertEqual(viewController.didLeaveWindowHierarchyCallsCount, 1)
        await expect(viewController: viewController, toBeInHierarchy: true)
    }

    @MainActor
    public func testDirectViewTransferToAnotherWindowRestartsContainer() async {
        let viewController = TestViewController()
        move(viewController, to: rootViewController)
        await expect(viewController: viewController, toBeInHierarchy: true)

        let secondWindow = makeAdditionalWindow()
        secondWindow.rootViewController!.view.addSubview(viewController.view)

        await expect { viewController.didEnterWindowHierarchyCallsCount == 2 }
        XCTAssertEqual(viewController.didLeaveWindowHierarchyCallsCount, 1)
        await expect(viewController: viewController, toBeInHierarchy: true)
    }

    @MainActor
    public func testMoveWithinSameWindowKeepsContainerStarted() async {
        let firstContainer = UIView()
        let secondContainer = UIView()
        rootViewController.view.addSubview(firstContainer)
        rootViewController.view.addSubview(secondContainer)

        let viewController = TestViewController()
        rootViewController.addChild(viewController)
        firstContainer.addSubview(viewController.view)
        viewController.didMove(toParent: rootViewController)

        await expect(viewController: viewController, toBeInHierarchy: true)
        XCTAssertEqual(viewController.didEnterWindowHierarchyCallsCount, 1)

        secondContainer.addSubview(viewController.view)

        try? await Task.sleep(nanoseconds: 1_000_000_000) // 1s, a couple of check periods

        // The window did not change, so the container must stay started.
        XCTAssertTrue(viewController.viewIsInWindowHierarchy)
        XCTAssertEqual(viewController.didEnterWindowHierarchyCallsCount, 1)
        XCTAssertEqual(viewController.didLeaveWindowHierarchyCallsCount, 0)
    }

    @MainActor
    public func testRepeatedWindowTransfersKeepCallbacksBalanced() async {
        let viewController = TestViewController()
        move(viewController, to: rootViewController)
        await expect(viewController: viewController, toBeInHierarchy: true)

        for transfer in 1...3 {
            let window = makeAdditionalWindow()
            move(viewController, to: window.rootViewController!)

            await expect { viewController.didEnterWindowHierarchyCallsCount == transfer + 1 }
            XCTAssertEqual(viewController.didLeaveWindowHierarchyCallsCount, transfer)
        }

        viewController.willMove(toParent: nil)
        viewController.view.removeFromSuperview()
        viewController.removeFromParent()

        await expect(viewController: viewController, toBeInHierarchy: false)

        // Every restart leaves a single pending hierarchy check behind, so detaching the container
        // for good delivers exactly one more leave callback.
        try? await Task.sleep(nanoseconds: 2_000_000_000)

        XCTAssertEqual(viewController.didEnterWindowHierarchyCallsCount, 4)
        XCTAssertEqual(viewController.didLeaveWindowHierarchyCallsCount, 4)
    }

    @MainActor
    public func testLifecycleDelegate() async {
        let delegate = LifecycleDelegate()

        autoreleasepool {
            let viewController = TestViewController(delegate: delegate)
            rootViewController = viewController
        }

        await expect { delegate.containerWillAppearCallsCount == 1 }

        rootViewController = UIViewController()

        await expect { delegate.containerWillAppearCallsCount == 1 }
        await expect { delegate.containerDidDisappearCallsCount == 1 }
        await expect { delegate.containerWillDeallocCallsCount == 1 }
    }
}

extension UIViewController {
    func presentAndWait(_ viewController: UIViewController) async {
        await withCheckedContinuation { continuation in
            self.present(viewController, animated: false, completion: { continuation.resume() })
        }
    }
    
    func dismissAndWait() async {
        await withCheckedContinuation { continuation in
            self.dismiss(animated: false, completion: { continuation.resume() })
        }
    }
}

class LifecycleDelegate: CMPComposeContainerLifecycleDelegate {
    var containerWillAppearCallsCount = 0
    func composeContainerWillAppear() {
        containerWillAppearCallsCount += 1
    }
    
    var containerDidDisappearCallsCount = 0
    func composeContainerDidDisappear() {
        containerDidDisappearCallsCount += 1
    }
    
    var containerWillDeallocCallsCount = 0
    func composeContainerWillDealloc() {
        containerWillDeallocCallsCount += 1
    }
}

private class TestViewController: CMPViewController {
    public static var counter: Int = 1
    
    private let id: Int
    
    public var viewIsInWindowHierarchy: Bool = false
    public private(set) var didEnterWindowHierarchyCallsCount = 0
    public private(set) var didLeaveWindowHierarchyCallsCount = 0

    init(delegate: CMPComposeContainerLifecycleDelegate? = nil) {
        id = TestViewController.counter
        TestViewController.counter += 1
        super.init(lifecycleDelegate: delegate)
    }
    
    required init?(coder: NSCoder) {
        nil
    }
    
    override func viewControllerDidEnterWindowHierarchy() {
        print("TestViewController_\(id) didEnterWindowHierarchy")
        XCTAssertFalse(viewIsInWindowHierarchy)
        viewIsInWindowHierarchy = true
        didEnterWindowHierarchyCallsCount += 1
    }

    override func viewControllerDidLeaveWindowHierarchy() {
        print("TestViewController_\(id) didLeaveWindowHierarchy")
        XCTAssertTrue(viewIsInWindowHierarchy)
        viewIsInWindowHierarchy = false
        didLeaveWindowHierarchyCallsCount += 1
    }
    
    override func userInterfaceStyleDidChange() {}
}
