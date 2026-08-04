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

@available(iOS 16.0, *)
private struct TwoExpandingComposeTextFieldsDemoView: View {
    let firstComposeView: UIView
    let secondComposeView: UIView

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Text("Two expanding Compose text fields")
                    .font(.headline)

                Text("Each field is a separate Compose view in this SwiftUI LazyVStack. Type enough text to wrap, or insert line breaks in the first field, and the second field should move down.")
                    .font(.subheadline)
                    .foregroundColor(.secondary)

                LazyVStack(alignment: .leading, spacing: 16) {
                    Text("First Compose view")
                        .font(.caption)
                        .foregroundColor(.secondary)
                    composeTextField(firstComposeView)

                    Text("Second Compose view")
                        .font(.caption)
                        .foregroundColor(.secondary)
                    composeTextField(secondComposeView)
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                Text("Expected: Each red host grows up to five text lines. Growing the first host relayouts this LazyVStack and moves the second host, without SwiftUI state changes.")
                    .font(.footnote)
                    .foregroundColor(.secondary)
            }
            .padding(20)
        }
    }

    private func composeTextField(_ composeView: UIView) -> some View {
        ComposeInSwiftUIRepresentable(composeView: composeView)
            .frame(maxWidth: .infinity, alignment: .leading)
            .border(.red, width: 2)
    }
}
