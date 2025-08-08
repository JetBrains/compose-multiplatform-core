package androidx.compose.ui.internal

import androidx.compose.runtime.NoLiveLiterals

private var nextHash = 1

private external interface WeakMap {
    fun set(key: Any, value: Int)
    fun get(key: Any): Int?
}

private val weakMap: WeakMap = js("new WeakMap()")
@NoLiveLiterals
private fun memoizeIdentityHashCode(instance: Any): Int {
    val value = nextHash++

    weakMap.set(instance, value)

    return value
}

// For the reference check the identityHashCode in compose:runtime
internal actual fun identityHashCode(instance: Any?): Int {
    if (instance == null) {
        return 0
    }

    return weakMap.get(instance) ?: memoizeIdentityHashCode(instance)
}