import SwiftUI
import UIKit
import shared

struct ContentView: View {
    var body: some View {
        ComposeDemoView()
            .ignoresSafeArea()
    }
}

private struct ComposeDemoView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        SwiftHelper().getViewController(
            makeHostingViewController: { index in
                UIHostingController(rootView: NestedContentView(index: index.intValue))
            },
            makeSizingDemoViewController: { composeView, identifier in
                switch identifier.intValue {
                case 10...18:
                    makeComposeInSwiftUIIntrinsicSizingDemoViewController(
                        composeView: composeView,
                        identifier: identifier.intValue
                    )
                case 20...22:
                    makeComposeInUIKitSizingDemoViewController(
                        composeView: composeView,
                        identifier: identifier.intValue
                    )
                default:
                    makeComposeInSwiftUISizingDemoViewController(
                        composeView: composeView,
                        identifier: identifier.intValue
                    )
                }
            }
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

private struct NestedContentView: View {
    let index: Int

    var body: some View {
        Text("Hello from SwiftUI #\(index)")
    }
}
