/*
 * Copyright 2025 The Android Open Source Project
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

package androidx.compose.foundation.text.selection

import android.content.Context
import android.os.Build
import android.view.textclassifier.TextClassificationContext
import android.view.textclassifier.TextClassificationManager
import android.view.textclassifier.TextClassifier
import android.view.textclassifier.TextSelection
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.selection.TextClassifierHelperMethods.createTextClassifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.intl.LocaleList
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The [CoroutineContext] used for launching [TextClassifier] operations within the selection
 * features.
 *
 * This context is expected to be associated with a worker thread to ensure that text
 * classification, which can be a time-consuming operation, does not block the UI thread. Providing
 * a [CoroutineContext] not backed by a worker thread may lead to performance issues or unexpected
 * behavior with [TextClassifier].
 */
val LocalTextClassifierCoroutineContext =
    staticCompositionLocalOf<CoroutineContext> { Dispatchers.IO }

/**
 * Copied from ViewConfiguration.getSmartSelectionInitializingTimeout. This is the timeout for all
 * TextClassifier calls when the TextClassifier object is not created. If TextClassifier is not
 * created or doesn't return the call in this time, we will cancel the call and treat it as if
 * TextClassifier returns null.
 */
private const val TEXT_CLASSIFIER_UNINITIALIZED_SMART_SELECTION_TIMEOUT_MILLIS = 500L

/**
 * Copied from ViewConfiguration.getSmartSelectionInitializedTimeout. This is the timeout for all
 * TextClassifier calls when the TextClassifier object is already created. If TextClassifier doesn't
 * return in this time, we will cancel the call and treat it as if TextClassifier returns null.
 */
private const val TEXT_CLASSIFIER_INITIALIZED_SMART_SELECTION_TIMEOUT_MILLIS = 200L

@VisibleForTesting
internal var PlatformSelectionBehaviorsFactory:
    (CoroutineContext, Context, SelectedTextType, LocaleList?) -> PlatformSelectionBehaviors =
    { coroutineContext, context, selectionType, localeList ->
        PlatformSelectionBehaviorsImpl(coroutineContext, context, selectionType, localeList)
    }

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal actual fun rememberPlatformSelectionBehaviors(
    selectedTextType: SelectedTextType,
    localeList: LocaleList?,
): PlatformSelectionBehaviors? {
    if (Build.VERSION.SDK_INT < 28) {
        // Smart selection features are not supported under API 28.
        return null
    }
    val context = LocalContext.current
    val coroutineContext = LocalTextClassifierCoroutineContext.current
    return remember(coroutineContext, context, selectedTextType, localeList) {
        PlatformSelectionBehaviorsFactory(coroutineContext, context, selectedTextType, localeList)
    }
}

internal class PlatformSelectionBehaviorsImpl(
    private val coroutineContext: CoroutineContext,
    private val context: Context,
    private val selectedTextType: SelectedTextType,
    private val localeList: LocaleList?,
) : PlatformSelectionBehaviors {
    var textClassifier: TextClassifier? = null

    override suspend fun suggestSelectionForLongPressOrDoubleClick(
        text: CharSequence,
        selection: TextRange,
    ): TextRange? {
        if (text.isEmpty() || selection.collapsed) {
            return null
        }
        if (Build.VERSION.SDK_INT >= 28) {
            val timeout =
                if (this.textClassifier == null) {
                    TEXT_CLASSIFIER_UNINITIALIZED_SMART_SELECTION_TIMEOUT_MILLIS
                } else {
                    TEXT_CLASSIFIER_INITIALIZED_SMART_SELECTION_TIMEOUT_MILLIS
                }
            return withContext(coroutineContext) {
                withTimeoutOrNull(timeout) {
                    val textClassifier = requireTextClassifier()

                    val localeList =
                        this@PlatformSelectionBehaviorsImpl.localeList?.let { localeList ->
                            android.os.LocaleList(
                                *localeList.map { it.platformLocale }.toTypedArray()
                            )
                        } ?: android.os.LocaleList(Locale.current.platformLocale)

                    val request =
                        TextSelection.Request.Builder(text.toString(), selection.min, selection.max)
                            .setDefaultLocales(localeList)
                            .build()
                    val newSelection = textClassifier.suggestSelection(request)

                    TextRange(newSelection.selectionStartIndex, newSelection.selectionEndIndex)
                }
            }
        }
        return null
    }

    @RequiresApi(28)
    private fun requireTextClassifier(): TextClassifier {
        return this.textClassifier
            ?: createTextClassifier(context, selectedTextType).also { textClassifier = it }
    }
}

@RequiresApi(28)
internal object TextClassifierHelperMethods {
    @RequiresApi(28)
    fun createTextClassifier(context: Context, selectionContext: SelectedTextType): TextClassifier {
        val textClassificationManager =
            context.getSystemService(TextClassificationManager::class.java)

        val widgetType =
            when (selectionContext) {
                SelectedTextType.EditableText -> TextClassifier.WIDGET_TYPE_EDITTEXT
                SelectedTextType.StaticText -> TextClassifier.WIDGET_TYPE_TEXTVIEW
            }
        val textClassificationContext =
            TextClassificationContext.Builder(context.packageName, widgetType).build()
        return textClassificationManager.createTextClassificationSession(textClassificationContext)
    }
}
