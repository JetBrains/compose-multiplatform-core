package androidx.compose.ui.kdt.macos

import androidx.compose.ui.kdt.LightweightWindowId
import org.jetbrains.desktop.macos.DragInfo
import org.jetbrains.desktop.macos.Window
import org.jetbrains.desktop.macos.WindowEvent
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import org.jetbrains.desktop.macos.WindowId

private val nextWindowId = AtomicLong(0L)
private val heavyToLight = ConcurrentHashMap<WindowId, LightweightWindowId>()

fun Window.lightweightWindowId(): LightweightWindowId? {
    return heavyToLight[windowId()]
}

/**
 * Might return null if the window was created not by kdt, or was already destroyed
 */
fun WindowId.toLightweightWindowId(): LightweightWindowId? {
    return heavyToLight[this]
}

/**
 * Might return null if the window was created not by kdt, or was already destroyed
 */
fun WindowEvent.lightweightWindowId(): LightweightWindowId? {
    return heavyToLight[windowId]
}

fun DragInfo.lightweightDestinationWindowId(): LightweightWindowId? {
    return heavyToLight[destinationWindowId]
}

fun Window.assignNewLightweightWindowId(): LightweightWindowId {
    val lightweightWindowId = LightweightWindowId(nextWindowId.getAndIncrement())
    heavyToLight[windowId()] = lightweightWindowId
    return lightweightWindowId
}

fun Window.destroyLightweightWindowId() {
    heavyToLight.remove(windowId())
}
