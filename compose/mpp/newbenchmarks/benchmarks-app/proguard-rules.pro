# Ktor loads the JVM engine through META-INF/services.
-keep class io.ktor.client.engine.cio.CIOEngineContainer { *; }
-keep class io.ktor.serialization.kotlinx.json.KotlinxSerializationJsonExtensionProvider { *; }

# JSON serialization resolves report serializers at runtime.
-keep class org.jetbrains.compose.benchmarks.report.** { *; }
