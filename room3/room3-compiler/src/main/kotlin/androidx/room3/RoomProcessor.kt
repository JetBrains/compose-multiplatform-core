/*
 * Copyright (C) 2016 The Android Open Source Project
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

package androidx.room3

import androidx.room3.compiler.processing.XProcessingEnv
import androidx.room3.compiler.processing.XRoundEnv
import androidx.room3.compiler.processing.javac.JavacBasicAnnotationProcessor
import androidx.room3.processor.Context.BooleanProcessorOptions
import androidx.room3.processor.Context.ProcessorOptions
import androidx.room3.verifier.DatabaseVerifier
import javax.lang.model.SourceVersion

/** Annotation processor option to tell Gradle that Room is an isolating annotation processor. */
private const val ISOLATING_ANNOTATION_PROCESSORS_INDICATOR =
    "org.gradle.annotation.processing.isolating"

/** The annotation processor for Room. */
class RoomProcessor :
    JavacBasicAnnotationProcessor(
        configureEnv = { options -> DatabaseProcessingStep.getEnvConfig(options) }
    ) {

    override fun processingSteps() = listOf(DatabaseProcessingStep())

    override fun getSupportedOptions(): Set<String> {
        return buildSet {
            addAll(ProcessorOptions.entries.map { it.argName })
            addAll(BooleanProcessorOptions.entries.map { it.argName })
            add(ISOLATING_ANNOTATION_PROCESSORS_INDICATOR)
        }
    }

    override fun getSupportedSourceVersion(): SourceVersion {
        return SourceVersion.latest()
    }

    override fun postRound(env: XProcessingEnv, round: XRoundEnv) {
        if (round.isProcessingOver) {
            DatabaseVerifier.cleanup()
        }
    }
}
