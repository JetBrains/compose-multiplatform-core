# Prevent the Internal and Math classes from being obfuscated as they are created from native code.
-keep class androidx.xr.runtime.internal.** { *; }
-keep class androidx.xr.runtime.internal.**$* { *; }
-keep class androidx.xr.runtime.math.** { *; }
-keep class androidx.xr.runtime.math.**$* { *; }
-keep class * extends androidx.xr.runtime.internal.** { *; }
-keep class * extends androidx.xr.runtime.internal.**$* { *; }
-keep class * extends androidx.xr.runtime.math.** { *; }
-keep class * extends androidx.xr.runtime.math.**$* { *; }
