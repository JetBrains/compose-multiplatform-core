package androidx.compose.runtime.internal

import androidx.compose.runtime.DataSource
import fleet.fastutil.ints.Int2ObjectOpenHashMap
import fleet.fastutil.ints.IntOpenHashSet
import fleet.fastutil.ints.forEach
import fleet.fastutil.ints.isEmpty
import fleet.fastutil.ints.valuesToHashSet
import fleet.fastutil.longs.Long2ObjectOpenHashMap
import fleet.fastutil.longs.LongOpenHashSet
import fleet.fastutil.longs.isEmpty
import noria.ID

internal class ReadTrackingIndex {
    var key: Any? = null
    set(value) {
        if (field == value) return
        field?.let { DataSource.unregisterReadTrackingIndex(it, this) }
        value?.let { DataSource.registerReadTrackingIndex(it, this) }
        field = value
    }

    class LambdaInfo(
        val lambdaId: Int,
        val invalidate: (reason: Any?) -> Unit
    ) {
        var patterns: LongOpenHashSet? = null
        fun witness(patternHash: Long) {
            when (val ps = patterns) {
                null -> patterns = LongOpenHashSet.of(patternHash)
                else -> ps.add(patternHash)
            }
        }
    }

    var current: LambdaInfo? = null

    private val patternToKeys: Long2ObjectOpenHashMap<IntOpenHashSet> = Long2ObjectOpenHashMap()
    private val keyToPatterns: Int2ObjectOpenHashMap<LambdaInfo> = Int2ObjectOpenHashMap()

    fun all(): Set<LambdaInfo> = keyToPatterns.valuesToHashSet()

    fun witness(key: Any, patternHash: Long) {
        if (this.key == null) {
            this.key = key
        } else {
            check(this.key == key) { "Cannot connect a single ReadTrackingIndex to multiple keys" }
        }
        current?.witness(patternHash)
    }

    fun isEmpty(): Boolean {
        require(keyToPatterns.isEmpty() == patternToKeys.isEmpty()) { "index is inconsistent" }
        return keyToPatterns.isEmpty()
    }

    fun clear() {
        current = null
        keyToPatterns.clear()
        patternToKeys.clear()
        key = null
    }

    fun updateIndex(lambdaInfo: LambdaInfo) {
        val key = lambdaInfo.lambdaId
        val newPatterns = lambdaInfo.patterns ?: EMPTY
        val patternToKeys = this.patternToKeys
        val keyToPatterns = this.keyToPatterns

        val oldFrameInfo: LambdaInfo? = keyToPatterns.remove(key)
        val oldPatterns = oldFrameInfo?.patterns
        oldPatterns?.values?.let { iter ->
            while (iter.hasNext()) {
                val oldP = iter.next()
                val keys = patternToKeys[oldP]
                if (keys != null && !newPatterns.contains(oldP)) {
                    keys.remove(key)
                    if (keys.isEmpty()) {
                        patternToKeys.remove(oldP)
                    }
                }
            }
        }

        newPatterns.values.let { iter ->
            while (iter.hasNext()) {
                val newP = iter.next()
                if (oldPatterns == null || !oldPatterns.contains(newP)) {
                    val keys = patternToKeys[newP]
                    if (keys == null) {
                        val keys1 = IntOpenHashSet()
                        keys1.add(key)
                        patternToKeys[newP] = keys1
                    } else {
                        keys.add(key)
                    }
                }
            }
        }

        if (!newPatterns.isEmpty()) {
            keyToPatterns[key] = lambdaInfo
        }

        if (keyToPatterns.isEmpty()) {
            this.key = null
        }

    }

    fun forget(key: Int) {
        val keyToPatterns = this.keyToPatterns
        val patternToKeys = this.patternToKeys
        keyToPatterns.remove(key)?.patterns?.values?.let { iter ->
            while (iter.hasNext()) {
                val p = iter.next()
                patternToKeys[p]?.let { keys ->
                    when (keys.size) {
                        1 -> {
                            patternToKeys.remove(p)
                        }
                        else -> {
                            keys.remove(key)
                        }
                    }
                }
            }
        }

        if (keyToPatterns.isEmpty()) {
            this.key = null
        }

    }

    fun invalidate(patternHashes: LongArray, reason: Any?) {
        val patternToKeys = patternToKeys
        for (hash in patternHashes) {
            patternToKeys[hash]?.forEach { key ->
                keyToPatterns[key]?.invalidate(reason)
            }
        }
    }

    fun invalidateAll(reason: Any?) {
        all().forEach { lambdaInfo ->
            lambdaInfo.invalidate(reason)
        }
    }

    inline fun <T> runLambda(
        id: ID,
        noinline invalidate: (reason: Any?) -> Unit,
        lambda: () -> T
    ): T {
        val l = LambdaInfo(id.id, invalidate)
        val prev = current
        current = l
        val res = lambda()
        updateIndex(l)
        current = prev
        return res
    }
}

private val EMPTY = LongOpenHashSet()
