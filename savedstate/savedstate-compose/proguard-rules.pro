# Workaround for https://issuetracker.google.com/issues/346808608
#
# `androidx.savedstate.compose.LocalSavedStateRegistryOwner` will reflectively lookup for
# `androidx.compose.ui.platform.LocalSavedStateRegistryOwner` to ensure backward compatibility
# when using SavedState >= 1.3 with Compose <= 1.7.
#
# We need to keep the getter if the code using this is included.
#
# We need to suppress `ShrinkerUnresolvedReference` because the `LocalComposition` is in a
# different module.
#
#noinspection ShrinkerUnresolvedReference
-if public class androidx.compose.ui.platform.AndroidCompositionLocals_androidKt {
    public static *** getLocalSavedStateRegistryOwner();
}
-keep public class androidx.compose.ui.platform.AndroidCompositionLocals_androidKt {
    public static *** getLocalSavedStateRegistryOwner();
}
