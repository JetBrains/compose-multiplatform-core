import SwiftUI
import UIKit
import shared

func makeComposeInSwiftUISizingDemoViewController(
    composeView: UIView,
    identifier: Int
) -> UIViewController {
    if #available(iOS 16.0, *) {
        switch identifier {
        case 0:
            return UIHostingController(rootView: FixedWidthFittedHeightExample(composeView: composeView))
        case 1:
            return UIHostingController(rootView: FixedHeightFittedWidthExample(composeView: composeView))
        case 3:
            return UIHostingController(rootView: NaturalSizeContentChangesExample(composeView: composeView))
        case 4:
            return UIHostingController(rootView: FillAvailableWidthFixedHeightExample(composeView: composeView))
        case 5:
            return UIHostingController(rootView: FixedWidthFillAvailableHeightExample(composeView: composeView))
        case 6:
            return UIHostingController(rootView: FillBothAvailableAxesExample(composeView: composeView))
        case 7:
            return UIHostingController(rootView: FillBothAxesComposeFixedHeightExample(composeView: composeView))
        case 8:
            return UIHostingController(rootView: FillBothAxesComposeFixedWidthExample(composeView: composeView))
        default:
            fatalError("Unknown Compose-in-SwiftUI sizing example: \(identifier)")
        }
    }

    return UIHostingController(rootView: SizeThatFitsUnavailableExample())
}

private struct SizeThatFitsUnavailableExample: View {
    var body: some View {
        Text("This demo requires iOS 16 or later because SwiftUI's UIViewRepresentable sizeThatFits API is unavailable on earlier iOS versions.")
            .foregroundColor(.secondary)
            .multilineTextAlignment(.center)
            .padding(20)
    }
}

@available(iOS 16.0, *)
private struct FixedWidthFittedHeightExample: View {
    let composeView: UIView
    @State private var width: CGFloat = 260

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Text("Fixed width, fitted height")
                    .font(.headline)
                Text("SwiftUI fixes the width. Compose measures its preferred height for that width through `sizeThatFits`.")
                    .font(.subheadline)
                    .foregroundColor(.secondary)

                VStack(alignment: .leading, spacing: 8) {
                    HStack {
                        Text("Fixed SwiftUI width")
                            .font(.caption)
                            .foregroundColor(.secondary)
                        Spacer()
                        Text("\(Int(width)) pt")
                            .font(.system(.caption, design: .monospaced))
                            .foregroundColor(.secondary)
                    }
                    Slider(value: $width, in: 140...360)
                    HStack(spacing: 8) {
                        ForEach([CGFloat(160), 240, 320], id: \.self) { value in
                            Button("\(Int(value))") { width = value }
                                .buttonStyle(DefaultButtonStyle())
                        }
                    }
                }

                ComposeInSwiftUIRepresentable(composeView: composeView)
                    .frame(width: width)
                    .border(.red, width: 2)

                Text("Expected: The red host keeps this width. Its height grows when the Compose cards wrap and shrinks when they fit on fewer rows.")
                    .font(.footnote)
                    .foregroundColor(.secondary)
            }
            .padding(20)
        }
    }
}

@available(iOS 16.0, *)
private struct FixedHeightFittedWidthExample: View {
    let composeView: UIView
    @State private var height: CGFloat = 260

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Text("Fixed height, fitted width")
                    .font(.headline)
                Text("SwiftUI fixes the height. Compose measures its preferred width for that height through `sizeThatFits`.")
                    .font(.subheadline)
                    .foregroundColor(.secondary)

                VStack(alignment: .leading, spacing: 8) {
                    HStack {
                        Text("Fixed SwiftUI height")
                            .font(.caption)
                            .foregroundColor(.secondary)
                        Spacer()
                        Text("\(Int(height)) pt")
                            .font(.system(.caption, design: .monospaced))
                            .foregroundColor(.secondary)
                    }
                    Slider(value: $height, in: 100...360)
                    HStack(spacing: 8) {
                        ForEach([CGFloat(260), 340, 420], id: \.self) { value in
                            Button("\(Int(value))") { height = value }
                                .buttonStyle(DefaultButtonStyle())
                        }
                    }
                }

                ComposeInSwiftUIRepresentable(composeView: composeView)
                    .frame(height: height)
                    .border(.red, width: 2)

                Text("Expected: The red host keeps this height. Its width remains on screen: it grows when the cards need more columns and shrinks when they fit into fewer columns.")
                    .font(.footnote)
                    .foregroundColor(.secondary)
            }
            .padding(20)
        }
    }
}

@available(iOS 16.0, *)
private struct NaturalSizeContentChangesExample: View {
    let composeView: UIView

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Text("Natural size, Compose content changes")
                    .font(.headline)
                Text("SwiftUI fixes neither axis. Compose changes its natural width and height, and SwiftUI must follow both.")
                    .font(.subheadline)
                    .foregroundColor(.secondary)

                ComposeInSwiftUIRepresentable(composeView: composeView)
                    .border(.red, width: 2)

                Text("Expected: Open Compose Controls to toggle width, height, or both. The red host should be exactly the animated Compose box size.")
                    .font(.footnote)
                    .foregroundColor(.secondary)
            }
            .padding(20)
        }
    }
}

@available(iOS 16.0, *)
private struct FillAvailableWidthFixedHeightExample: View {
    let composeView: UIView
    @State private var height: CGFloat = 260

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Text("Fill available width, fixed height")
                    .font(.headline)
                Text("Compose has no natural preferred width here. SwiftUI supplies the available width and a chosen height.")
                    .font(.subheadline)
                    .foregroundColor(.secondary)

                VStack(alignment: .leading, spacing: 8) {
                    HStack {
                        Text("Fixed SwiftUI height")
                            .font(.caption)
                            .foregroundColor(.secondary)
                        Spacer()
                        Text("\(Int(height)) pt")
                            .font(.system(.caption, design: .monospaced))
                            .foregroundColor(.secondary)
                    }
                    Slider(value: $height, in: 120...360)
                    HStack(spacing: 8) {
                        ForEach([CGFloat(160), 240, 320], id: \.self) { value in
                            Button("\(Int(value))") { height = value }
                                .buttonStyle(DefaultButtonStyle())
                        }
                    }
                }

                ComposeInSwiftUIRepresentable(composeView: composeView)
                    .frame(maxWidth: .infinity)
                    .frame(height: height)
                    .border(.red, width: 2)

                Text("Expected: The red host fills the available width and keeps the selected height. The blue Compose content fills those supplied bounds.")
                    .font(.footnote)
                    .foregroundColor(.secondary)
            }
            .padding(20)
        }
    }
}

@available(iOS 16.0, *)
private struct FixedWidthFillAvailableHeightExample: View {
    let composeView: UIView
    @State private var width: CGFloat = 260

    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            Text("Fixed width, fill available height")
                .font(.headline)
            Text("Compose has no natural preferred height here. SwiftUI supplies a chosen width and the remaining screen height.")
                .font(.subheadline)
                .foregroundColor(.secondary)

            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    Text("Fixed SwiftUI width")
                        .font(.caption)
                        .foregroundColor(.secondary)
                    Spacer()
                    Text("\(Int(width)) pt")
                        .font(.system(.caption, design: .monospaced))
                        .foregroundColor(.secondary)
                }
                Slider(value: $width, in: 140...300)
                HStack(spacing: 8) {
                    ForEach([CGFloat(160), 220, 280], id: \.self) { value in
                        Button("\(Int(value))") { width = value }
                            .buttonStyle(DefaultButtonStyle())
                    }
                }
            }

            ComposeInSwiftUIRepresentable(composeView: composeView)
                .frame(width: width)
                .frame(maxHeight: .infinity)
                .border(.red, width: 2)

            Text("Expected: The red host keeps the selected width and fills the remaining screen height. The blue Compose content fills those supplied bounds.")
                .font(.footnote)
                .foregroundColor(.secondary)
        }
        .padding(20)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }
}

@available(iOS 16.0, *)
private struct FillBothAvailableAxesExample: View {
    let composeView: UIView

    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            Text("Fill both available axes")
                .font(.headline)
            Text("Compose has no natural size. SwiftUI supplies the available width and height.")
                .font(.subheadline)
                .foregroundColor(.secondary)

            ComposeInSwiftUIRepresentable(composeView: composeView)
                .border(.red, width: 2)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .border(.orange, width: 2)

            Text("Expected: The red Compose host and orange SwiftUI expansion frame should fill the same remaining area.")
                .font(.footnote)
                .foregroundColor(.secondary)
        }
        .padding(20)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }
}

@available(iOS 16.0, *)
private struct FillBothAxesComposeFixedHeightExample: View {
    let composeView: UIView

    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            Text("Fill both axes, Compose fixed height")
                .font(.headline)
            Text("SwiftUI tries to fill both axes, but Compose has a fixed 120 dp height and fills only the supplied width.")
                .font(.subheadline)
                .foregroundColor(.secondary)

            ComposeInSwiftUIRepresentable(composeView: composeView)
                .border(.red, width: 2)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .border(.orange, width: 2)

            Text("Expected: The orange SwiftUI expansion frame fills the remaining area. The red Compose host stays 120 dp tall and fills its width.")
                .font(.footnote)
                .foregroundColor(.secondary)
        }
        .padding(20)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }
}

@available(iOS 16.0, *)
private struct FillBothAxesComposeFixedWidthExample: View {
    let composeView: UIView

    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            Text("Fill both axes, Compose fixed width")
                .font(.headline)
            Text("SwiftUI tries to fill both axes, but Compose has a fixed 180 dp width and fills only the supplied height.")
                .font(.subheadline)
                .foregroundColor(.secondary)

            ComposeInSwiftUIRepresentable(composeView: composeView)
                .border(.red, width: 2)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .border(.orange, width: 2)

            Text("Expected: The orange SwiftUI expansion frame fills the remaining area. The red Compose host stays 180 dp wide and fills its height.")
                .font(.footnote)
                .foregroundColor(.secondary)
        }
        .padding(20)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }
}

@available(iOS 16.0, *)
struct ComposeInSwiftUIRepresentable: UIViewRepresentable {
    let composeView: UIView

    func makeUIView(context: Context) -> UIView {
        composeView
}

    func updateUIView(_ uiView: UIView, context: Context) {}

    func sizeThatFits(_ proposal: ProposedViewSize, uiView: UIView, context: Context) -> CGSize? {
        uiView.sizeThatFits(
            CGSize(
                width: proposal.width ?? UIView.noIntrinsicMetric,
                height: proposal.height ?? UIView.noIntrinsicMetric
            )
        )
    }
}
