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

package androidx.annotation.keep

import kotlin.reflect.KClass

/**
 * Generate a conditional keep rule for code that indirectly accesses a constructor of a class /
 * interface, or it's subclasses / interface implementers.
 *
 * This annotation should be used in code which instantiates a class by reflection (or a similar
 * indirect means, such as JNI).
 *
 * The generated keep rule will be conditional, which indicates to optimizers / shrinkers that it
 * should only keep the target constructor in the final application if the annotated code is
 * reachable in the final application.
 *
 * `@GenerateKeepForConstructor` is a convenience for `@GenerateKeepForMethod(methodName =
 * "<init>")`
 *
 * @see GenerateKeepForMethod
 * @see GenerateKeepForField
 */
@Retention(AnnotationRetention.BINARY)
@Repeatable
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FIELD,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.CONSTRUCTOR,
)
public annotation class GenerateKeepForConstructor(
    /**
     * Class to be instantiated.
     *
     * Mutually exclusive with [className].
     */
    val classConstant: KClass<*> = Unspecified::class,

    /**
     * Class to be instantiated.
     *
     * Mutually exclusive with [classConstant].
     */
    val className: String = "",

    /**
     * Defines which constructor to keep by specifying set of parameter classes passed.
     *
     * Defaults to `[ Unspecified::class ]`, which will keep all constructors.
     *
     * Mutually exclusive with [paramClassNames].
     */
    val params: Array<KClass<*>> = [Unspecified::class],

    /**
     * Defines which constructor to keep by specifying set of parameter classes passed.
     *
     * Defaults to `[""]`, which will keep all constructors.
     *
     * Mutually exclusive with [params]
     */
    val paramClassNames: Array<String> = [""],
)
