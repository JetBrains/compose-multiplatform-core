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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import androidx.pdf.viewer.fragment.PdfViewerFragment
import com.google.android.material.button.MaterialButton

// TODO(b/386721657): Remove this activity once the switch to V2 completes
@SuppressLint("RestrictedApiAndroidX")
@RestrictTo(RestrictTo.Scope.LIBRARY)
class MainActivityV2 : AppCompatActivity() {

    private var pdfViewerFragment: PdfViewerFragment? = null

    @VisibleForTesting
    @RequiresExtension(extension = Build.VERSION_CODES.S, version = 13)
    var filePicker: ActivityResultLauncher<String> =
        registerForActivityResult(GetContent()) { uri: Uri? ->
            uri?.let {
                if (pdfViewerFragment == null) {
                    setPdfView()
                }
                pdfViewerFragment?.documentUri = uri
            }
        }

    @RequiresExtension(extension = Build.VERSION_CODES.S, version = 13)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (pdfViewerFragment == null) {
            pdfViewerFragment =
                supportFragmentManager.findFragmentByTag(PDF_VIEWER_FRAGMENT_TAG)
                    as PdfViewerFragment?
        }

        val getContentButton: MaterialButton = findViewById(R.id.launch_button)
        val searchButton: MaterialButton = findViewById(R.id.search_pdf_button)

        getContentButton.setOnClickListener { filePicker.launch(MIME_TYPE_PDF) }
        if (savedInstanceState == null) {
            setPdfView()
        }

        searchButton.setOnClickListener { pdfViewerFragment?.isTextSearchActive = true }

        handleWindowInsets()

        // Ensure WindowInsetsCompat are passed to content views without being consumed by the decor
        // view.
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }

    @RequiresExtension(extension = Build.VERSION_CODES.S, version = 13)
    private fun setPdfView() {
        val fragmentManager: FragmentManager = supportFragmentManager

        // Fragment initialization
        pdfViewerFragment = PdfViewerFragment()
        val transaction: FragmentTransaction = fragmentManager.beginTransaction()

        // Replace an existing fragment in a container with an instance of a new fragment
        transaction.replace(
            R.id.fragment_container_view,
            pdfViewerFragment!!,
            PDF_VIEWER_FRAGMENT_TAG
        )
        transaction.commitAllowingStateLoss()
        fragmentManager.executePendingTransactions()
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
    }
}
