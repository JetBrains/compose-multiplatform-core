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

package androidx.build.lint

import com.android.SdkConstants.ATTR_VALUE
import com.android.tools.lint.detector.api.AnnotationInfo
import com.android.tools.lint.detector.api.AnnotationOrigin
import com.android.tools.lint.detector.api.AnnotationUsageInfo
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.getMethodName
import com.android.tools.lint.detector.api.isUnconditionalReturn
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiLiteralValue
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiNamedElement
import org.jetbrains.uast.UAnnotated
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClassLiteralExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UPolyadicExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.USwitchClauseExpression
import org.jetbrains.uast.UUnaryExpression
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.UastFacade
import org.jetbrains.uast.UastPrefixOperator
import org.jetbrains.uast.evaluateString
import org.jetbrains.uast.skipParenthesizedExprDown
import org.jetbrains.uast.toUElement
import org.jetbrains.uast.tryResolve
import org.jetbrains.uast.util.isConstructorCall

/**
 * Enforced flag checking in the Android platform; see go/android-flagged-apis.
 *
 * **NOTE:** This file is a fork of the original sources in the Android lint code base, see
 * `lint-checks/src/main/java/com/android/tools/lint/checks/optional/FlaggedApiDetector.kt`
 */
class FlaggedApiDetector : Detector(), SourceCodeScanner {
    companion object Issues {
        private val IMPLEMENTATION =
            Implementation(FlaggedApiDetector::class.java, Scope.JAVA_FILE_SCOPE)

        /** Accessing flagged api without check. */
        @JvmField
        val ISSUE =
            Issue.create(
                id = "AndroidXFlaggedApi",
                explanation =
                    """
          This lint check looks for accesses of APIs marked with `@FlaggedApi(X)` without \
          a guarding `if (Flags.X)` check or equivalent gating check. See go/android-flagged-apis.
          """,
                briefDescription = "FlaggedApi access without check",
                category = Category.CORRECTNESS,
                priority = 6,
                severity = Severity.ERROR,
                androidSpecific = true,
                implementation = IMPLEMENTATION,
            )

        private const val CHECKS_ACONFIG_FLAG_ANNOTATION = "androidx.annotation.ChecksAconfigFlag"
        private const val ATTR_FLAG = "flag"
        private const val FLAGGED_API_ANNOTATION = "android.annotation.FlaggedApi"
    }

    override fun applicableAnnotations(): List<String> {
        return listOf(FLAGGED_API_ANNOTATION)
    }

    override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean {
        return when (type) {
            AnnotationUsageType.METHOD_CALL,
            AnnotationUsageType.METHOD_REFERENCE,
            AnnotationUsageType.FIELD_REFERENCE,
            AnnotationUsageType.CLASS_REFERENCE,
            AnnotationUsageType.ANNOTATION_REFERENCE,
            AnnotationUsageType.EXTENDS,
            AnnotationUsageType.DEFINITION -> true
            else -> false
        }
    }

    override fun inheritAnnotation(annotation: String): Boolean {
        return false
    }

    override fun visitAnnotationUsage(
        context: JavaContext,
        element: UElement,
        annotationInfo: AnnotationInfo,
        usageInfo: AnnotationUsageInfo,
    ) {
        val flagString = getFlaggedApiString(annotationInfo.annotation)
        if (flagString == null) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Failed to obtain flag string"
            )
            return
        }

        // Avoid checking usage of the `@FlaggedApi` annotation itself. This should only happen in
        // tests, since in practice we only define flagged APIs inside the platform SDK.
        if (
            annotationInfo.origin == AnnotationOrigin.SELF &&
                annotationInfo.qualifiedName == FLAGGED_API_ANNOTATION
        ) {
            return
        }

        // Avoid checking flagged deprecations. We don't allow adding APIs as deprecated, so we can
        // safely assume that the flag applies to the deprecated state rather than the API itself.
        if (isFlaggedDeprecation(usageInfo)) return

        // Only allowlisted libraries are allowed to call flagged APIs.
        if (!isUsageInAllowlistedLibrary(context, usageInfo.usage)) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Flagged APIs are subject to additional policies and may only be called by " +
                    "libraries that have been allowlisted by Jetpack Working Group"
            )
            return
        }

        // Only alpha libraries are allowed to call flagged APIs.
        if (!isUsageInAlphaLibrary(context, usageInfo.usage)) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Flagged APIs may only be called during alpha and must be removed before moving " +
                    "to beta"
            )
            return
        }

        // Is the usage checked? Great.
        if (isFlagChecked(element, flagString)) return

        val referenced = element.tryResolve()
        val description =
            when {
                referenced is PsiMethod -> "Method `${referenced.name}()`"
                element is UCallExpression ->
                    if (element.isConstructorCall()) {
                        val className = (element.classReference?.tryResolve() as? PsiClass)?.name
                        "Constructor for class `$className`"
                    } else {
                        "Method `${getMethodName(element)}()`"
                    }
                referenced is PsiField -> "Field `${referenced.name}`"
                referenced is PsiClass -> "Class `${referenced.name}`"
                element is UClassLiteralExpression ->
                    "Class `${element.expression?.sourcePsi?.text}`"
                referenced is PsiNamedElement -> "Reference `${referenced.name}`"
                else -> "This"
            }
        val message =
            "$description is a flagged API and must be inside a flag check for \"$flagString\""
        context.report(ISSUE, element, context.getLocation(element), message)
    }

    private val AnnotationUsageInfo.referencedElement: UElement?
        get() =
            referenced.toUElement()
                ?: if ((usage as? UCallExpression)?.isConstructorCall() == true) {
                    (usage as UCallExpression).classReference?.tryResolve().toUElement()
                } else {
                    null
                }

    private fun isUsageInAllowlistedLibrary(context: JavaContext, usage: UElement): Boolean =
        (context.evaluator.getLibrary(usage) ?: context.project.mavenCoordinate)?.let {
            allowlistedCoordinates.contains(it.groupId) ||
                allowlistedCoordinates.contains("${it.groupId}:${it.artifactId}")
        } ?: true // If we can't obtain the Maven coordinate, assume we're in a lint test.

    private fun isUsageInAlphaLibrary(context: JavaContext, usage: UElement): Boolean =
        (context.evaluator.getLibrary(usage) ?: context.project.mavenCoordinate)
            ?.version
            ?.contains("-alpha")
            ?: true // If we can't obtain the Maven coordinate, assume we're in a lint test.

    private fun isFlaggedDeprecation(usageInfo: AnnotationUsageInfo): Boolean =
        (usageInfo.referencedElement as? UAnnotated)?.let {
            it.findAnnotation("java.lang.Deprecated") != null ||
                it.findAnnotation("kotlin.Deprecated") != null
        } == true

    private fun getFlaggedApiString(annotation: UAnnotation): String? =
        (annotation.javaPsi?.findAttributeValue(ATTR_VALUE) as? PsiLiteralValue)?.value as? String

    /** Is the given [element] inside a flag check? */
    private fun isFlagChecked(
        element: UElement,
        flagString: String,
    ): Boolean {
        var curr = element.uastParent ?: return false

        var prev = element
        while (curr !is UFile) {
            if (curr is UIfExpression) {
                val condition = curr.condition
                if (prev !== condition) {
                    val fromThen = prev == curr.thenExpression
                    if (fromThen) {
                        if (isFlagExpression(condition, flagString)) {
                            return true
                        }
                    } else {
                        // Handle "if (!Flags.X) else <CALL>"
                        val op = condition.skipParenthesizedExprDown()
                        if (
                            op is UUnaryExpression &&
                                op.operator == UastPrefixOperator.LOGICAL_NOT &&
                                isFlagExpression(op.operand, flagString)
                        ) {
                            return true
                        } else if (
                            op is UPolyadicExpression &&
                                op.operator == UastBinaryOperator.LOGICAL_OR &&
                                (op.operands.any {
                                    val nested = it.skipParenthesizedExprDown()
                                    nested is UUnaryExpression &&
                                        nested.operator == UastPrefixOperator.LOGICAL_NOT &&
                                        isFlagExpression(nested.operand, flagString)
                                })
                        ) {
                            return true
                        }
                    }
                }
            } else if (curr is USwitchClauseExpression) {
                if (curr.caseValues.any { value -> isFlagExpression(value, flagString) }) {
                    return true
                }
            } else if (
                curr is UPolyadicExpression && curr.operator == UastBinaryOperator.LOGICAL_AND
            ) {
                for (operand in curr.operands) {
                    if (operand === curr) {
                        break
                    } else if (isFlagExpression(operand, flagString)) {
                        return true
                    }
                }
            } else if (curr is UMethod) {
                // See if there's an early return. We *only* handle a very simple canonical format
                // here;
                // must be first statement in method.
                val body = curr.uastBody
                if (body is UBlockExpression && body.expressions.size > 1) {
                    val first = body.expressions[0]
                    if (first is UIfExpression) {
                        val condition = first.condition.skipParenthesizedExprDown()
                        if (
                            condition is UUnaryExpression &&
                                condition.operator == UastPrefixOperator.LOGICAL_NOT &&
                                isFlagExpression(condition.operand, flagString)
                        ) {
                            // It's a flag check; make sure we just return
                            val then = first.thenExpression?.skipParenthesizedExprDown()
                            if (then != null && then.isUnconditionalReturn()) {
                                return true
                            }
                        }
                    }
                }
            }

            prev = curr
            curr = curr.uastParent ?: break
        }

        return false
    }

    /** Is the given [element] a flag expression (e.g. "Flags.set()") or equivalently annotated? */
    private fun isFlagExpression(
        element: UElement,
        flagString: String,
    ): Boolean {
        if (element is UUnaryExpression && element.operator == UastPrefixOperator.LOGICAL_NOT) {
            return !isFlagExpression(element.operand, flagString)
        } else if (element is UReferenceExpression || element is UCallExpression) {
            val resolved = element.tryResolve()
            if (resolved is PsiMethod) {
                if (
                    (resolved.toUElement() as UAnnotated)
                        .uAnnotations
                        .filter { it.qualifiedName == CHECKS_ACONFIG_FLAG_ANNOTATION }
                        .mapNotNull {
                            val attr =
                                it.findAttributeValue(ATTR_FLAG)
                                    ?: it.findAttributeValue(null)
                                    ?: return@mapNotNull null
                            attr.evaluateString()
                                ?: (attr.javaPsi as? PsiLiteralValue)?.value as? String
                        }
                        .contains(flagString)
                ) {
                    return true
                }
            } else if (resolved is PsiField) {
                // Arguably we should look for final fields here, but on the other hand
                // there may be cases where it's initialized later, so it's a bit like
                // Kotlin's "lateinit". Treat them all as constant.
                val initializer = UastFacade.getInitializerBody(resolved)
                if (initializer != null) {
                    return isFlagExpression(initializer, flagString)
                }
            }
        } else if (element is UParenthesizedExpression) {
            return isFlagExpression(element.expression, flagString)
        } else if (element is UPolyadicExpression) {
            if (element.operator == UastBinaryOperator.LOGICAL_AND) {
                for (operand in element.operands) {
                    if (isFlagExpression(operand, flagString)) {
                        return true
                    }
                }
            }
        }
        return false
    }
}

// List of libraries which are allowed to call flagged APIs, where `groupId:artifactId` represents a
// single module and `groupId` represents an entire group of modules.
private val allowlistedCoordinates =
    listOf(
        "test",
        "androidx.mediarouter",
    )
