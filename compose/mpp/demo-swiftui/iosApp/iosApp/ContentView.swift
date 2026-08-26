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
            makeSwiftUISizeThatFitsSizingDemoViewController: { composeView, example in
                makeComposeInSwiftUISizingDemoViewController(
                    composeView: composeView,
                    example: example
                )
            },
            makeSwiftUIIntrinsicSizingDemoViewController: { composeView, example in
                makeComposeInSwiftUIIntrinsicSizingDemoViewController(
                    composeView: composeView,
                    example: example
                )
            },
            makeUIKitSizingDemoViewController: { composeView, example in
                makeComposeInUIKitSizingDemoViewController(
                    composeView: composeView,
                    example: example
                )
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
