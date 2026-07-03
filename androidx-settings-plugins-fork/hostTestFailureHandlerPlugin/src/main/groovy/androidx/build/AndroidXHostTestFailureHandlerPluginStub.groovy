package androidx.build

import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings

/**
 * A stub of plugin that is applied in the root AOSP settings.gradle,
 * but not needed in settings-fork.gradle, where plugins not applied
 */
@SuppressWarnings("unused")
abstract class AndroidXHostTestFailureHandlerPluginStub implements Plugin<Settings> {
    @Override
    void apply(Settings settings) {
    }
}
