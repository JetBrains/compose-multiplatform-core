import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnLockMismatchReport
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension
import org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnPlugin
import org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnRootExtension

plugins {
    alias(libs.plugins.kotlin.multiplatform).apply(false)
    alias(libs.plugins.kotlin.jvm).apply(false)
    alias(libs.plugins.kotlin.serialization).apply(false)
    alias(libs.plugins.compose.compiler).apply(false)
    alias(libs.plugins.compose.multiplatform).apply(false)
}

// Local core always uses current initialization, regardless of the catalog Compose version.
val usePublishedCompose = providers.gradleProperty("composeCorePath").orNull.isNullOrBlank()
if (usePublishedCompose) {
    val composeVersion = libs.versions.compose.multiplatform.get()
    val composeInitModule = when {
        composeVersion.startsWith("1.12.") -> {
            "benchmarks-compose-init-1-12"
        }
        //1.13.0–1.13.9
        Regex("""1\.13\.[0-9](?:[-+].*)?""").matches(composeVersion) -> {
            "benchmarks-compose-init-1-13"
        }
        else -> null
    }
    if (composeInitModule != null) {
        configure(
            listOf(project(":benchmarks-app"), project(":benchmarks-d8-app"))
        ) {
            configurations.configureEach {
                resolutionStrategy.dependencySubstitution {
                    substitute(project(":benchmarks-compose-init-current"))
                        .using(project(":$composeInitModule"))
                        .because("Published Compose requires matching Skiko initialization")
                }
            }
        }
    }
}

plugins.withType<YarnPlugin> {
    extensions.configure<YarnRootExtension> {
        yarnLockMismatchReportProperty = YarnLockMismatchReport.WARNING
        yarnLockAutoReplaceProperty = true
    }
}

plugins.withType<WasmYarnPlugin> {
    extensions.configure<WasmYarnRootExtension> {
        yarnLockMismatchReportProperty = YarnLockMismatchReport.WARNING
        yarnLockAutoReplaceProperty = true
    }
}
