package top.mythcraft.flux.gui

/**
 * 可观察状态
 */
interface MutableState<T> {
    var value: T
}

internal class MutableStateImpl<T>(initial: T, private val onChange: () -> Unit) : MutableState<T> {
    private var v: T = initial

    override var value: T
        get() = v
        set(newValue) {
            if (v != newValue) {
                v = newValue
                onChange()
            }
        }
}

/**
 * 有变更通知的 Map，[set] 和 [clear] 触发 [onChange]
 *
 * 视图只读，避免绕过通知直接修改
 */
class StateMap<K, V>(
    initial: Map<K, V>,
    private val onChange: () -> Unit,
) {
    private val map: LinkedHashMap<K, V> = LinkedHashMap(initial)

    val keys: Set<K> get() = map.keys
    val values: Collection<V> get() = map.values
    val size: Int get() = map.size

    operator fun get(key: K): V? = map[key]

    operator fun set(key: K, value: V) {
        map[key] = value
        onChange()
    }

    operator fun contains(key: K): Boolean = key in map

    fun forEach(action: (K, V) -> Unit) = map.forEach(action)

    fun clear() {
        map.clear()
        onChange()
    }

    fun toMap(): Map<K, V> = LinkedHashMap(map)
}
