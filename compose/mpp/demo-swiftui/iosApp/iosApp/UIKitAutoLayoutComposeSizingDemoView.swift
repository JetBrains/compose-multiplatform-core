import SwiftUI
import UIKit

func makeComposeInUIKitSizingDemoViewController(
    composeView: UIView,
    identifier: Int
) -> UIViewController {
    return UIHostingController(
        rootView: UIKitComposeSizingExampleRepresentable(
            composeView: composeView,
            identifier: identifier
        )
    )
}

private struct UIKitComposeSizingExampleRepresentable: UIViewControllerRepresentable {
    let composeView: UIView
    let identifier: Int

    func makeUIViewController(context: Context) -> UIViewController {
        switch identifier {
        case 20:
            return UIKitFittedComposeViewController(
                composeView: composeView,
                title: "Fixed width, fitted height",
                explanation: "UIKit owns the width constraint. It asks Compose for the preferred height for that width and applies only that height as a constraint."
            )
        case 21:
            return UIKitNaturalSizeComposeViewController(
                composeView: composeView,
                title: "Compose content changes natural size",
                explanation: "UIKit asks Compose for its preferred size with no width or height proposal. When Compose changes its content, UIKit repeats that measurement and updates both owned constraints."
            )
        case 22:
            return UIKitFullyConstrainedComposeViewController(composeView: composeView)
        default:
            fatalError("Unknown Compose-in-UIKit sizing example: \(identifier)")
        }
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

private final class UIKitNaturalSizeComposeViewController: UIViewController {
    private let composeView: UIView
    private let titleText: String
    private let explanationText: String
    private let titleLabel = UILabel()
    private let explanationLabel = UILabel()
    private let expectedLabel = UILabel()
    private var composeWidthConstraint: NSLayoutConstraint!
    private var composeHeightConstraint: NSLayoutConstraint!

    init(composeView: UIView, title: String, explanation: String) {
        self.composeView = composeView
        self.titleText = title
        self.explanationText = explanation
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func loadView() {
        view = UIView()
    }

    override func viewDidLoad() {
        super.viewDidLoad()

        view.backgroundColor = .systemBackground

        titleLabel.text = titleText
        titleLabel.font = .preferredFont(forTextStyle: .headline)

        explanationLabel.text = explanationText
        explanationLabel.textColor = .secondaryLabel
        explanationLabel.numberOfLines = 0
        explanationLabel.font = .preferredFont(forTextStyle: .subheadline)

        expectedLabel.text = "Expected: the red host follows Compose's natural width and height."
        expectedLabel.textColor = .secondaryLabel
        expectedLabel.numberOfLines = 0
        expectedLabel.font = .preferredFont(forTextStyle: .footnote)

        composeView.layer.borderColor = UIColor.systemRed.cgColor
        composeView.layer.borderWidth = 2

        [titleLabel, explanationLabel, composeView, expectedLabel].forEach {
            $0.translatesAutoresizingMaskIntoConstraints = false
            view.addSubview($0)
        }

        composeWidthConstraint = composeView.widthAnchor.constraint(equalToConstant: 1)
        composeHeightConstraint = composeView.heightAnchor.constraint(equalToConstant: 1)

        NSLayoutConstraint.activate([
            titleLabel.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 20),
            titleLabel.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 20),
            titleLabel.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -20),
            explanationLabel.topAnchor.constraint(equalTo: titleLabel.bottomAnchor, constant: 8),
            explanationLabel.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 20),
            explanationLabel.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -20),
            composeView.topAnchor.constraint(equalTo: explanationLabel.bottomAnchor, constant: 20),
            composeView.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            composeWidthConstraint,
            composeHeightConstraint,
            expectedLabel.topAnchor.constraint(equalTo: composeView.bottomAnchor, constant: 12),
            expectedLabel.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 20),
            expectedLabel.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -20),
            expectedLabel.bottomAnchor.constraint(lessThanOrEqualTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -20),
        ])
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        updateNaturalSize()
    }

    private func updateNaturalSize() {
        let fittedSize = composeView.sizeThatFits(
            CGSize(width: UIView.noIntrinsicMetric, height: UIView.noIntrinsicMetric)
        )
        if fittedSize.width > 0, fittedSize.height > 0 {
            composeWidthConstraint.constant = fittedSize.width
            composeHeightConstraint.constant = fittedSize.height
        }
    }
}

private final class UIKitFittedComposeViewController: UIViewController {
    private let composeView: UIView
    private let titleText: String
    private let explanationText: String
    private let titleLabel = UILabel()
    private let explanationLabel = UILabel()
    private let widthLabel = UILabel()
    private let widthSlider = UISlider()
    private let expectedLabel = UILabel()
    private var composeWidthConstraint: NSLayoutConstraint!
    private var composeHeightConstraint: NSLayoutConstraint!

    init(composeView: UIView, title: String, explanation: String) {
        self.composeView = composeView
        self.titleText = title
        self.explanationText = explanation
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func loadView() {
        view = UIView()
    }

    override func viewDidLoad() {
        super.viewDidLoad()

        view.backgroundColor = .systemBackground

        titleLabel.text = titleText
        titleLabel.font = .preferredFont(forTextStyle: .headline)

        explanationLabel.text = explanationText
        explanationLabel.textColor = .secondaryLabel
        explanationLabel.numberOfLines = 0
        explanationLabel.font = .preferredFont(forTextStyle: .subheadline)

        widthLabel.textColor = .secondaryLabel
        widthLabel.font = .monospacedSystemFont(ofSize: 13, weight: .regular)

        widthSlider.minimumValue = 160
        widthSlider.maximumValue = 360
        widthSlider.value = 260
        widthSlider.addTarget(self, action: #selector(widthChanged), for: .valueChanged)

        expectedLabel.text = "Expected: the red host keeps the selected width and follows Compose's preferred height."
        expectedLabel.textColor = .secondaryLabel
        expectedLabel.numberOfLines = 0
        expectedLabel.font = .preferredFont(forTextStyle: .footnote)

        composeView.layer.borderColor = UIColor.systemRed.cgColor
        composeView.layer.borderWidth = 2

        [titleLabel, explanationLabel, widthLabel, widthSlider, composeView, expectedLabel].forEach {
            $0.translatesAutoresizingMaskIntoConstraints = false
            view.addSubview($0)
        }

        composeWidthConstraint = composeView.widthAnchor.constraint(equalToConstant: CGFloat(widthSlider.value))
        composeHeightConstraint = composeView.heightAnchor.constraint(equalToConstant: 1)

        NSLayoutConstraint.activate([
            titleLabel.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 20),
            titleLabel.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 20),
            titleLabel.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -20),
            explanationLabel.topAnchor.constraint(equalTo: titleLabel.bottomAnchor, constant: 8),
            explanationLabel.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 20),
            explanationLabel.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -20),
            widthLabel.topAnchor.constraint(equalTo: explanationLabel.bottomAnchor, constant: 20),
            widthLabel.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 20),
            widthSlider.topAnchor.constraint(equalTo: widthLabel.bottomAnchor, constant: 4),
            widthSlider.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 20),
            widthSlider.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -20),
            composeView.topAnchor.constraint(equalTo: widthSlider.bottomAnchor, constant: 20),
            composeView.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            composeWidthConstraint,
            composeHeightConstraint,
            expectedLabel.topAnchor.constraint(equalTo: composeView.bottomAnchor, constant: 12),
            expectedLabel.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 20),
            expectedLabel.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -20),
            expectedLabel.bottomAnchor.constraint(lessThanOrEqualTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -20),
        ])

        updateWidthLabel()
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        updateFittedHeight()
    }

    @objc private func widthChanged() {
        composeWidthConstraint.constant = CGFloat(widthSlider.value)
        updateWidthLabel()
        view.setNeedsLayout()
    }

    private func updateWidthLabel() {
        widthLabel.text = "Fixed UIKit width: \(Int(widthSlider.value)) pt"
    }

    private func updateFittedHeight() {
        let fittedHeight = composeView.sizeThatFits(
            CGSize(width: composeWidthConstraint.constant, height: UIView.noIntrinsicMetric)
        ).height
        if fittedHeight > 0 {
            composeHeightConstraint.constant = fittedHeight
        }
    }
}

private final class UIKitFullyConstrainedComposeViewController: UIViewController {
    private let composeView: UIView

    init(composeView: UIView) {
        self.composeView = composeView
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func loadView() {
        view = UIView()
    }

    override func viewDidLoad() {
        super.viewDidLoad()

        view.backgroundColor = .systemBackground

        let titleLabel = UILabel()
        titleLabel.text = "Fill UIKit's constrained bounds"
        titleLabel.font = .preferredFont(forTextStyle: .headline)

        let explanationLabel = UILabel()
        explanationLabel.text = "UIKit pins all four edges of the Compose view. Compose fills those final bounds; no sizeThatFits feedback is needed."
        explanationLabel.textColor = .secondaryLabel
        explanationLabel.numberOfLines = 0
        explanationLabel.font = .preferredFont(forTextStyle: .subheadline)

        composeView.layer.borderColor = UIColor.systemRed.cgColor
        composeView.layer.borderWidth = 2

        [titleLabel, explanationLabel, composeView].forEach {
            $0.translatesAutoresizingMaskIntoConstraints = false
            view.addSubview($0)
        }

        NSLayoutConstraint.activate([
            titleLabel.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 20),
            titleLabel.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 20),
            titleLabel.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -20),
            explanationLabel.topAnchor.constraint(equalTo: titleLabel.bottomAnchor, constant: 8),
            explanationLabel.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 20),
            explanationLabel.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -20),
            composeView.topAnchor.constraint(equalTo: explanationLabel.bottomAnchor, constant: 20),
            composeView.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 20),
            composeView.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -20),
            composeView.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -20),
        ])
    }
}
