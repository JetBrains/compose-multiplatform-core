package androidx.compose.ui.internal

import androidx.compose.runtime.NoLiveLiterals

private var nextHash = 1

private external interface WeakMap {
    fun set(key: JsAny, value: Int)
    fun get(key: JsAny): Int?
}

private val weakMap: WeakMap = js("new WeakMap()")
@NoLiveLiterals
private fun memoizeIdentityHashCode(instance: JsAny): Int {
    val value = nextHash++

    weakMap.set(instance.toJsReference(), value)

    return value
}

// For the reference check the identityHashCode in compose:runtime
internal actual fun identityHashCode(instance: Any?): Int {
    if (instance == null) {
        return 0
    }

    val jsRef = instance.toJsReference()
    return weakMap.get(jsRef) ?: memoizeIdentityHashCode(jsRef)
}