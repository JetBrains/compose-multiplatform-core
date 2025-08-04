# Keep classes in the extensions and scenecore.spatial.core packages since they can be used
# externally. Prevent classes that are only used by native code from being stripped during code
# minimization.

# Prevent entry classes from shrinking.
-keep class androidx.xr.scenecore.spatial.core.SpatialSceneRuntimeFactory { *; }

# The androidx.xr.extensions library is a "compileOnly" dependency; its actual implementation
# resides on the device.  Obfuscation may lead to miss match and optimization on stub functions may
# lead to discarded. We must keep these class and interfaces.
# TODO b/433269677: Re-evaluate these rules when the extensions package becomes part of the Android SDK.
-keep public class androidx.xr.extensions.** { *; }
-keep public interface androidx.xr.extensions.** { *; }
-keep @interface androidx.xr.extensions.**

# Prevent internal implementations of com.android.xr.extensions.function.Consumer.accept from being
# stripped when being referenced as a synthetic lambda.
-keepclassmembers class androidx.xr.extensions.** {
  public void accept(***);
}
