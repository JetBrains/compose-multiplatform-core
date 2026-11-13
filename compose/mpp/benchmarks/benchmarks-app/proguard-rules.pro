# CMP 1.12.2 GPU benchmarks reuse private native allocation APIs from the old Swing redrawers.
-keep class org.jetbrains.skiko.swing.SwingLayerProperties { *; }
-keep class org.jetbrains.skiko.swing.MetalSwingRedrawer { *; }
-keep class org.jetbrains.skiko.swing.LinuxOpenGLSwingRedrawer { *; }
-keepclassmembers class org.jetbrains.skiko.MetalAdapter { long ptr; }

# Ktor loads the JVM engine through META-INF/services.
-keep class io.ktor.client.engine.cio.CIOEngineContainer { *; }
-keep class io.ktor.serialization.kotlinx.json.KotlinxSerializationJsonExtensionProvider { *; }

# JSON serialization resolves report serializers at runtime.
-keep class org.jetbrains.compose.benchmarks.data.** { *; }
