/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.pdf.testapp

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.GetContent
import androidx.annotation.RequiresExtension
import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.os.BundleCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import androidx.pdf.testapp.ui.v2.PdfViewerFragmentExtended
import androidx.pdf.testapp.ui.v2.StyledPdfViewerFragment
import androidx.pdf.testapp.util.BehaviorFlags
import androidx.pdf.viewer.fragment.PdfViewerFragment
import com.google.android.material.button.MaterialButton

// TODO(b/386721657): Remove this activity once the switch to V2 completes
@SuppressLint("RestrictedApiAndroidX")
@RestrictTo(RestrictTo.Scope.LIBRARY)
class MainActivityV2 : AppCompatActivity() {

    private var pdfViewerFragment: PdfViewerFragment? = null
    private var isCustomLinkHandlingEnabled = false

    @VisibleForTesting
    @RequiresExtension(extension = Build.VERSION_CODES.S, version = 13)
    private var filePicker: ActivityResultLauncher<String> =
        registerForActivityResult(GetContent()) { uri: Uri? ->
            uri?.let {
                if (pdfViewerFragment == null) {
                    setPdfView()
                }
                if (pdfViewerFragment is PdfViewerFragment) {
                    (pdfViewerFragment as PdfViewerFragment).documentUri = uri
                }
            }
        }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("custom_link_handling_enabled", isCustomLinkHandlingEnabled)
    }

    @RequiresExtension(extension = Build.VERSION_CODES.S, version = 13)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        pdfViewerFragment =
            supportFragmentManager.findFragmentByTag(PDF_VIEWER_FRAGMENT_TAG) as PdfViewerFragment?

        isCustomLinkHandlingEnabled =
            savedInstanceState?.getBoolean("custom_link_handling_enabled") ?: false

        val getContentButton: MaterialButton = findViewById(R.id.launch_button)
        val searchButton: MaterialButton = findViewById(R.id.search_pdf_button)
        val customLinkHandlingSwitch: SwitchCompat = findViewById(R.id.custom_link_handling_switch)

        customLinkHandlingSwitch.isChecked = isCustomLinkHandlingEnabled
        customLinkHandlingSwitch.setOnCheckedChangeListener { _, isChecked ->
            isCustomLinkHandlingEnabled = isChecked
            updateFragment()
        }

        getContentButton.setOnClickListener { filePicker.launch(MIME_TYPE_PDF) }
        if (savedInstanceState == null) {
            pdfViewerFragment = getFragmentForCurrentState() as? PdfViewerFragment
            setPdfView()
        }

        searchButton.setOnClickListener { pdfViewerFragment?.isTextSearchActive = true }

        handleWindowInsets()

        // Ensure WindowInsetsCompat are passed to content views without being consumed by the decor
        // view.
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }

    @RequiresExtension(extension = Build.VERSION_CODES.S, version = 13)
    private fun updateFragment() {
        val currentUri = pdfViewerFragment?.documentUri

        val newFragment = getFragmentForCurrentState()
        pdfViewerFragment = newFragment as? PdfViewerFragment

        val transaction: FragmentTransaction = supportFragmentManager.beginTransaction()
        transaction.replace(R.id.fragment_container_view, newFragment)
        transaction.commit()
        supportFragmentManager.executePendingTransactions()

        // Restore URI if applicable
        if (newFragment is PdfViewerFragment && currentUri != null) {
            newFragment.documentUri = currentUri
        }
    }

    @RequiresExtension(extension = Build.VERSION_CODES.S, version = 13)
    private fun setPdfView() {
        pdfViewerFragment?.let {
            val transaction: FragmentTransaction = supportFragmentManager.beginTransaction()
            transaction.replace(R.id.fragment_container_view, pdfViewerFragment!!)
            transaction.commitAllowingStateLoss()
            supportFragmentManager.executePendingTransactions()
        }
    }

    @RequiresExtension(extension = Build.VERSION_CODES.S, version = 13)
    private fun getFragmentForCurrentState(): Fragment {
        val fragmentType = getFragmentTypeFromIntent()

        val flags =
            BehaviorFlags.Builder()
                .setCustomLinkHandlingEnabled(isCustomLinkHandlingEnabled)
                .build()

        return when (fragmentType) {
            FragmentType.BASIC_FRAGMENT -> {
                if (flags.isCustomLinkHandlingEnabled()) {
                    PdfViewerFragmentExtended.newInstance(flags)
                } else {
                    PdfViewerFragment()
                }
            }
            FragmentType.STYLED_FRAGMENT -> {
                StyledPdfViewerFragment.newInstance(flags)
            }
        }
    }

    private fun getFragmentTypeFromIntent(): FragmentType {
        return intent.extras?.let {
            BundleCompat.getSerializable(it, FRAGMENT_TYPE_KEY, FragmentType::class.java)
        } ?: FragmentType.BASIC_FRAGMENT
    }

    private fun handleWindowInsets() {
        val pdfContainerView: View = findViewById(R.id.pdf_container_view)

        ViewCompat.setOnApplyWindowInsetsListener(pdfContainerView) { view, insets ->
            // Get the insets for the system bars (status bar, navigation bar)
            val systemBarsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            // Adjust the padding of the container view to accommodate system windows
            view.setPadding(
                view.paddingLeft,
                systemBarsInsets.top,
                view.paddingRight,
                systemBarsInsets.bottom
            )

            insets
        }
    }

    companion object {
        private const val MIME_TYPE_PDF = "application/pdf"
        private const val PDF_VIEWER_FRAGMENT_TAG = "pdf_viewer_fragment_tag"
        internal const val FRAGMENT_TYPE_KEY = "fragmentTypeKey"

        internal enum class FragmentType {
            BASIC_FRAGMENT,
            STYLED_FRAGMENT
        }
    }
}
