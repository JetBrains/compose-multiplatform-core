import SwiftUI
import UIKit

func makeComposeInSwiftUIIntrinsicSizingDemoViewController(
    composeView: UIView,
    identifier: Int
) -> UIViewController {
    switch identifier {
    case 10:
        return UIHostingController(rootView: IntrinsicFixedWidthFittedHeightExample(composeView: composeView))
    case 11:
        return UIHostingController(rootView: IntrinsicFixedHeightFittedWidthExample(composeView: composeView))
    case 13:
        return UIHostingController(rootView: IntrinsicNaturalSizeContentChangesExample(composeView: composeView))
    case 14:
        return UIHostingController(rootView: IntrinsicFillAvailableWidthFixedHeightExample(composeView: composeView))
    case 15:
        return UIHostingController(rootView: IntrinsicFixedWidthFillAvailableHeightExample(composeView: composeView))
    case 16:
        return UIHostingController(rootView: IntrinsicFillBothAvailableAxesExample(composeView: composeView))
    case 17:
        return UIHostingController(rootView: IntrinsicFillBothAxesComposeFixedHeightExample(composeView: composeView))
    case 18:
        return UIHostingController(rootView: IntrinsicFillBothAxesComposeFixedWidthExample(composeView: composeView))
    default:
        fatalError("Unknown Compose-in-SwiftUI intrinsic sizing example: \(identifier)")
    }
}

private struct IntrinsicFixedWidthFittedHeightExample: View {
    let composeView: UIView
    @State private var width: CGFloat = 260

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Text("Fixed width, fitted height")
                    .font(.headline)
                Text("SwiftUI fixes the width. updateUIView asks Compose for that width, then SwiftUI uses the view's intrinsic height.")
                    .font(.subheadline)
                    .foregroundColor(.secondary)

                IntrinsicWidthControl(width: $width)

                ComposeInSwiftUIIntrinsicRepresentable(
                    composeView: composeView,
                    fittingProposal: CGSize(width: width, height: UIView.noIntrinsicMetric)
                )
                .frame(width: width)
                .border(.orange, width: 2)

                Text("Expected: The orange host keeps this width. Its intrinsic height grows when the Compose cards wrap and shrinks when they fit on fewer rows.")
                    .font(.footnote)
                    .foregroundColor(.secondary)
            }
            .padding(20)
        }
    }
}

private struct IntrinsicFixedHeightFittedWidthExample: View {
    let composeView: UIView
    @State private var height: CGFloat = 260

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Text("Fixed height, fitted width")
                    .font(.headline)
                Text("SwiftUI fixes the height. updateUIView asks Compose for that height, then SwiftUI uses the view's intrinsic width.")
                    .font(.subheadline)
                    .foregroundColor(.secondary)

                IntrinsicHeightControl(height: $height)

                ComposeInSwiftUIIntrinsicRepresentable(
                    composeView: composeView,
                    fittingProposal: CGSize(width: UIView.noIntrinsicMetric, height: height)
                )
                .frame(height: height)
                .border(.orange, width: 2)

                Text("Expected: The orange host keeps this height. Its intrinsic width changes as Compose chooses a different number of card columns.")
                    .font(.footnote)
                    .foregroundColor(.secondary)
            }
            .padding(20)
        }
    }
}

private struct IntrinsicNaturalSizeContentChangesExample: View {
    let composeView: UIView

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Text("Natural size, Compose content changes")
                    .font(.headline)
                Text("SwiftUI fixes neither axis. When Compose changes its natural size, the host invalidates intrinsicContentSize and SwiftUI follows both axes.")
                    .font(.subheadline)
                    .foregroundColor(.secondary)

                ComposeInSwiftUIIntrinsicRepresentable(
                    composeView: composeView,
                    fittingProposal: CGSize(width: UIView.noIntrinsicMetric, height: UIView.noIntrinsicMetric)
                )
                .border(.orange, width: 2)

                Text("Expected: Open Compose Controls to toggle width, height, or both. The orange host should follow the animated Compose box size.")
                    .font(.footnote)
                    .foregroundColor(.secondary)
            }
            .padding(20)
        }
    }
}

private struct IntrinsicFillAvailableWidthFixedHeightExample: View {
    let composeView: UIView
    @State private var height: CGFloat = 260

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Text("Fill available width, fixed height")
                    .font(.headline)
                Text("Compose has no natural preferred width. SwiftUI supplies its available width and the selected height; the intrinsic measurement is used only where an axis is not constrained.")
                    .font(.subheadline)
                    .foregroundColor(.secondary)

                IntrinsicHeightControl(
                    height: $height,
                    range: 120...360,
                    values: [160, 240, 320]
                )

                ComposeInSwiftUIIntrinsicRepresentable(
                    composeView: composeView,
                    fittingProposal: CGSize(width: UIView.noIntrinsicMetric, height: height)
                )
                .frame(maxWidth: .infinity)
                .frame(height: height)
                .border(.orange, width: 2)

                Text("Expected: The orange host fills the available width and keeps the selected height. The blue Compose content fills those supplied bounds.")
                    .font(.footnote)
                    .foregroundColor(.secondary)
            }
            .padding(20)
        }
    }
}

private struct IntrinsicFixedWidthFillAvailableHeightExample: View {
    let composeView: UIView
    @State private var width: CGFloat = 260

    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            Text("Fixed width, fill available height")
                .font(.headline)
            Text("Compose has no natural preferred height. SwiftUI supplies a selected width and the remaining screen height.")
                .font(.subheadline)
                .foregroundColor(.secondary)

            IntrinsicWidthControl(width: $width, range: 140...300, values: [160, 220, 280])

            ComposeInSwiftUIIntrinsicRepresentable(
                composeView: composeView,
                fittingProposal: CGSize(width: width, height: UIView.noIntrinsicMetric)
            )
            .frame(width: width)
            .frame(maxHeight: .infinity)
            .border(.orange, width: 2)

            Text("Expected: The orange host keeps the selected width and fills the remaining screen height. The blue Compose content fills those supplied bounds.")
                .font(.footnote)
                .foregroundColor(.secondary)
        }
        .padding(20)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }
}

private struct IntrinsicFillBothAvailableAxesExample: View {
    let composeView: UIView

    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            Text("Fill both available axes")
                .font(.headline)
            Text("Compose has no natural size. SwiftUI supplies both final bounds, so intrinsic sizing is not needed for either axis.")
                .font(.subheadline)
                .foregroundColor(.secondary)

            ComposeInSwiftUIIntrinsicRepresentable(
                composeView: composeView,
                fittingProposal: CGSize(width: UIView.noIntrinsicMetric, height: UIView.noIntrinsicMetric)
            )
            .border(.orange, width: 2)
            .frame(maxWidth: .infinity, maxHeight: .infinity)

            Text("Expected: The orange Compose host fills the remaining area.")
                .font(.footnote)
                .foregroundColor(.secondary)
        }
        .padding(20)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }
}

private struct IntrinsicFillBothAxesComposeFixedHeightExample: View {
    let composeView: UIView

    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            Text("Fill both axes, Compose fixed height")
                .font(.headline)
            Text("SwiftUI tries to fill both axes, but Compose has a fixed 120 dp height and fills only the supplied width.")
                .font(.subheadline)
                .foregroundColor(.secondary)

            ComposeInSwiftUIIntrinsicRepresentable(
                composeView: composeView,
                fittingProposal: CGSize(width: UIView.noIntrinsicMetric, height: UIView.noIntrinsicMetric)
            )
            .border(.orange, width: 2)
            .frame(maxWidth: .infinity, maxHeight: .infinity)

            Text("Expected: The orange SwiftUI frame fills the remaining area. The Compose host stays 120 dp tall and fills its width.")
                .font(.footnote)
                .foregroundColor(.secondary)
        }
        .padding(20)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }
}

private struct IntrinsicFillBothAxesComposeFixedWidthExample: View {
    let composeView: UIView

    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            Text("Fill both axes, Compose fixed width")
                .font(.headline)
            Text("SwiftUI tries to fill both axes, but Compose has a fixed 180 dp width and fills only the supplied height.")
                .font(.subheadline)
                .foregroundColor(.secondary)

            ComposeInSwiftUIIntrinsicRepresentable(
                composeView: composeView,
                fittingProposal: CGSize(width: UIView.noIntrinsicMetric, height: UIView.noIntrinsicMetric)
            )
            .border(.orange, width: 2)
            .frame(maxWidth: .infinity, maxHeight: .infinity)

            Text("Expected: The orange SwiftUI frame fills the remaining area. The Compose host stays 180 dp wide and fills its height.")
                .font(.footnote)
                .foregroundColor(.secondary)
        }
        .padding(20)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }
}

private struct IntrinsicWidthControl: View {
    @Binding var width: CGFloat
    var range: ClosedRange<CGFloat> = 140...360
    var values: [CGFloat] = [160, 240, 320]

    var body: some View {
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
            Slider(value: $width, in: range)
            HStack(spacing: 8) {
                ForEach(values, id: \.self) { value in
                    Button("\(Int(value))") { width = value }
                        .buttonStyle(DefaultButtonStyle())
                }
            }
        }
    }
}

private struct IntrinsicHeightControl: View {
    @Binding var height: CGFloat
    var range: ClosedRange<CGFloat> = 100...360
    var values: [CGFloat] = [260, 340, 420]

    var body: some View {
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
            Slider(value: $height, in: range)
            HStack(spacing: 8) {
                ForEach(values, id: \.self) { value in
                    Button("\(Int(value))") { height = value }
                        .buttonStyle(DefaultButtonStyle())
                }
            }
        }
    }
}

private struct ComposeInSwiftUIIntrinsicRepresentable: UIViewRepresentable {
    let composeView: UIView
    let fittingProposal: CGSize

    func makeUIView(context: Context) -> UIView {
        composeView
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        uiView.sizeThatFits(fittingProposal)
    }
}
