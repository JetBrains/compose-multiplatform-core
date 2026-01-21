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

        appDelegate = MockAppDelegate()
        UIApplication.shared.delegate = appDelegate
        appDelegate.setUpClearWindow()
        TestViewController.counter = 1
    }

    override func tearDownWithError() throws {
        super.tearDown()

        appDelegate?.cleanUp()
        appDelegate = nil
    }
        
    private func expect(
        viewController: TestViewController,
        toBeInHierarchy inHierarchy: Bool,
        function: StaticString = #function,
        line: Int = #line,
        message: () -> String = { "" }
    ) {
        expect(timeout: 5.0, function: function, line: line, message: message) {
            viewController.viewIsInWindowHierarchy == inHierarchy
        }
    }
    
    private func expect(
        viewControllersToBeInHierarchy: [(TestViewController, Bool)],
        function: StaticString = #function,
        line: Int = #line,
        message: () -> String = { "" }
    ) {
        expect(timeout: 5.0, function: function, line: line, message: message) {
            viewControllersToBeInHierarchy.reduce(true) { partialResult, pair in
                let (viewController, inHierarchy) = pair
                
                return partialResult && viewController.viewIsInWindowHierarchy == inHierarchy
            }
        }
    }
    
    private func expect(
        viewControllers: [TestViewController],
        toBeInHierarchy inHierarchy: Bool,
        function: StaticString = #function,
        line: Int = #line,
        message: () -> String = { "" }
    ) {
        expect(viewControllersToBeInHierarchy: viewControllers.map {
            ($0, inHierarchy)
        }, function: function, line: line, message: message)
    }
    
    public func testNotAttached() {
        let viewController = TestViewController()
        expect(viewController: viewController, toBeInHierarchy: false)
    }
    
    public func testRootViewController() {
        let viewController = TestViewController()
        rootViewController = viewController
        expect(viewController: viewController, toBeInHierarchy: true)
        
        rootViewController = UIViewController()
        expect(viewController: viewController, toBeInHierarchy: false)
    }

    public func testPresentAndDismiss() {
        let viewController = TestViewController()

        rootViewController.present(viewController, animated: true)
        expect(viewController: viewController, toBeInHierarchy: true)

        rootViewController.dismiss(animated: true)
        
        expect(viewController: viewController, toBeInHierarchy: false)
    }

    public func testChildController() {
        let viewController1 = TestViewController()
        let viewController2 = TestViewController()
        expect(viewControllers: [viewController1, viewController2], toBeInHierarchy: false)

        rootViewController.present(viewController1, animated: true)
        expect(viewControllersToBeInHierarchy: [
            (viewController1, true),
            (viewController2, false)
        ])

        viewController1.addChild(viewController2)
        viewController2.didMove(toParent: viewController1)
        viewController1.view.addSubview(viewController2.view)
        expect(viewControllers: [viewController1, viewController2], toBeInHierarchy: true)

        viewController2.willMove(toParent: nil)
        viewController2.removeFromParent()
        viewController2.view.removeFromSuperview()
        expect(viewControllersToBeInHierarchy: [
            (viewController1, true),
            (viewController2, false)
        ])

        rootViewController.dismiss(animated: true)
        expect(viewControllers: [viewController1, viewController2], toBeInHierarchy: false)
    }

    public func testNavigationControllerPresentAndPush() {
        let viewController1 = TestViewController()
        let viewController2 = TestViewController()
        let viewController3 = TestViewController()
        
        expect(viewControllers: [
            viewController1,
            viewController2,
            viewController3
        ], toBeInHierarchy: false)
        
        let navigationController = UINavigationController(rootViewController: viewController1)

        rootViewController.present(navigationController, animated: false)

        expect(viewController: viewController1, toBeInHierarchy: true)
        expect(viewControllers: [viewController2, viewController3], toBeInHierarchy: false)

        navigationController.pushViewController(viewController2, animated: false)
        expect(viewControllers: [viewController1, viewController2], toBeInHierarchy: true)
        expect(viewController: viewController3, toBeInHierarchy: false)

        navigationController.present(viewController3, animated: false)
        expect(viewControllers: [viewController1, viewController2, viewController3], toBeInHierarchy: true)

        viewController3.dismiss(animated: false)
        expect(viewControllers: [viewController1, viewController2], toBeInHierarchy: true)
        expect(viewController: viewController3, toBeInHierarchy: false)

        navigationController.dismiss(animated: false)
        
        expect(viewControllers: [viewController1, viewController2, viewController3], toBeInHierarchy: false)
    }
    
    public func testNavigationControllerPresentAndPush2() {
        let viewController1 = TestViewController()
        let viewController2 = TestViewController()
        let viewController3 = TestViewController()
        
        let navigationController = UINavigationController(rootViewController: viewController1)

        rootViewController.present(navigationController, animated: false)
        navigationController.pushViewController(viewController2, animated: false)
        navigationController.pushViewController(viewController3, animated: false)

        navigationController.dismiss(animated: false)
        
        expect(viewControllers: [viewController1, viewController2, viewController3], toBeInHierarchy: false)
    }

    public func testTabBarControllerPresentAndPush() {
        let viewController1 = TestViewController()
        let viewController2 = TestViewController()
        let viewController3 = TestViewController()
        
        let tabBarController = UITabBarController()
        tabBarController.viewControllers = [viewController1, viewController2]

        rootViewController.present(tabBarController, animated: true)

        expect(viewControllers: [viewController2, viewController3], toBeInHierarchy: false)
        expect(viewController: viewController1, toBeInHierarchy: true)

        tabBarController.present(viewController3, animated: true)
        expect(viewControllers: [viewController1, viewController3], toBeInHierarchy: true)
        
        viewController3.dismiss(animated: true)
        expect(viewController: viewController1, toBeInHierarchy: true)
        expect(viewControllers: [viewController2, viewController3], toBeInHierarchy: false)

        tabBarController.dismiss(animated: true)

        expect(viewControllers: [viewController1, viewController2, viewController3], toBeInHierarchy: false)
    }
    
    public func testFullscreenPresentationOnTop() throws {
        let viewController = TestViewController()
        rootViewController = viewController
        
        expect(viewController: viewController, toBeInHierarchy: true)
        
        let urlStr = "https://nonexisting"
        let url = URL(string: urlStr)!
        let player = AVPlayer(url: url)
        let playerController = AVPlayerViewController()
        playerController.player = player
        
        viewController.present(playerController, animated: false)
        expect(viewController: viewController, toBeInHierarchy: true)
        playerController.dismiss(animated: false)
        
        rootViewController = UIViewController()
        expect(viewController: viewController, toBeInHierarchy: false)
    }
    
    public func testFullScreenPresentationSandwich() {
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
        
        expect(viewControllersToBeInHierarchy: [
            (viewController0, true),
            (viewController1, false),
            (viewController2, false),
            (viewController3, false),
        ]) {
            """
            Only the root view controller must be in the window hierarchy
            - viewController0.viewIsInWindowHierarchy : \(viewController0.viewIsInWindowHierarchy)
            - viewController1.viewIsInWindowHierarchy : \(viewController1.viewIsInWindowHierarchy)
            - viewController2.viewIsInWindowHierarchy : \(viewController2.viewIsInWindowHierarchy)
            - viewController3.viewIsInWindowHierarchy : \(viewController3.viewIsInWindowHierarchy)
            """
        }
        
        viewController0.present(viewController1, animated: false)
        expect(viewControllersToBeInHierarchy: [
            (viewController0, true),
            (viewController1, true),
            (viewController2, true),
            (viewController3, false),
        ]) {
            """
            Modal view controller and child view controller must be present in window hierarchy
            - viewController0.viewIsInWindowHierarchy: \(viewController0.viewIsInWindowHierarchy)
            - viewController1.viewIsInWindowHierarchy: \(viewController1.viewIsInWindowHierarchy)
            - viewController2.viewIsInWindowHierarchy: \(viewController2.viewIsInWindowHierarchy)
            - viewController3.viewIsInWindowHierarchy: \(viewController3.viewIsInWindowHierarchy)
            """
        }
        
        viewController1.present(viewController3, animated: false)
        expect(viewControllers: [viewController0, viewController1, viewController2, viewController3], toBeInHierarchy: true) {
            """
            All view controller should be in view hierarchy
            - viewController0.viewIsInWindowHierarchy: \(viewController0.viewIsInWindowHierarchy)
            - viewController1.viewIsInWindowHierarchy: \(viewController1.viewIsInWindowHierarchy)
            - viewController2.viewIsInWindowHierarchy: \(viewController2.viewIsInWindowHierarchy)
            - viewController3.viewIsInWindowHierarchy: \(viewController3.viewIsInWindowHierarchy)
            
            """
        }
                        
        viewController0.dismiss(animated: false)
        expect(viewControllersToBeInHierarchy: [
            (viewController0, true),
            (viewController1, false),
            (viewController2, false),
            (viewController3, false),
        ]) {
            """
            Only the root view controller should stay in view hierarchy
            - viewController0.viewIsInWindowHierarchy: \(viewController0.viewIsInWindowHierarchy)
            - viewController1.viewIsInWindowHierarchy: \(viewController1.viewIsInWindowHierarchy)
            - viewController2.viewIsInWindowHierarchy: \(viewController2.viewIsInWindowHierarchy)
            - viewController3.viewIsInWindowHierarchy: \(viewController3.viewIsInWindowHierarchy)            
            """
        }
        rootViewController = UIViewController()
        expect(viewControllers: [viewController0, viewController1, viewController2, viewController3], toBeInHierarchy: false)
    }

    public func testMultipleHierarchyReEntrance() {
        let viewController = TestViewController()
        
        let navigationController = UINavigationController(rootViewController: UIViewController())

        rootViewController = navigationController
        navigationController.pushViewController(viewController, animated: false)

        expect(viewControllers: [viewController], toBeInHierarchy: true)

        navigationController.popViewController(animated: false)

        expect(viewControllers: [viewController], toBeInHierarchy: false)

        navigationController.pushViewController(viewController, animated: false)

        expect(viewControllers: [viewController], toBeInHierarchy: true)
    }
    
    public func testLifecycleDelegate() {
        let delegate = LifecycleDelegate()

        autoreleasepool {
            let viewController = TestViewController(delegate: delegate)
            rootViewController = viewController
        }
        
        expect { delegate.containerWillAppearCallsCount == 1 }

        autoreleasepool {
            rootViewController = UIViewController()
        }

        expect { delegate.containerWillAppearCallsCount == 1 }
        expect { delegate.containerDidDisappearCallsCount == 1 }
        expect { delegate.containerWillDeallocCallsCount == 1 }
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
    }

    override func viewControllerDidLeaveWindowHierarchy() {
        print("TestViewController_\(id) didLeaveWindowHierarchy")
        XCTAssertTrue(viewIsInWindowHierarchy)
        viewIsInWindowHierarchy = false
    }
    
    override func userInterfaceStyleDidChange() {}
}
