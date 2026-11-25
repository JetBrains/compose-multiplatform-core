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
