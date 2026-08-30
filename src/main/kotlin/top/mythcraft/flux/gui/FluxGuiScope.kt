package top.mythcraft.flux.gui

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack

internal class FluxGuiFrame {
    val widgets = HashMap<Int, WidgetDeclaration>()
    val inputSlots = HashSet<Int>()
    var cursor = 0
    var currentCell: Int? = null
}

/** 声明式 GUI 的内容作用域：组件（[item]/[button]/[slot]）、布局（[pattern]/[at]）、
 * 局部状态（[mutableStateOf]/[stateMapOf]）与 [requestRender]。
 */
class FluxGuiScope internal constructor(
    private val player: ServerPlayer,
    private val gui: FluxGui,
    private val frame: FluxGuiFrame,
    private val memory: MutableList<Any?>,
) {
    private var memoryIndex = 0

    /**
     * 位置记忆：按调用位置返回上一帧同一实例。
     * 要求每帧调用 [remember] 的顺序稳定；
     * 顺序变化丢弃过期槽位并重新计算（同类型但错位的实例无法检测，仍要求顺序稳定）。
     */
    fun <T> remember(calc: () -> T): T {
        val i = memoryIndex++
        if (i < memory.size) {
            try {
                @Suppress("UNCHECKED_CAST")
                return memory[i] as T
            } catch (_: ClassCastException) {
                // 调用顺序变化导致错位：覆盖为重新计算的值
            }
        }
        val value = calc()
        if (i < memory.size) memory[i] = value else memory.add(value)
        return value
    }

    /**
     * 创建局部可观察状态，写操作自动调用 [requestRender]
     */
    fun <T> mutableStateOf(initial: T): MutableState<T> = remember { MutableStateImpl(initial) { requestRender() } }

    /**
     * 创建局部可观察 Map，[StateMap.set] 或 [StateMap.clear] 自动调用 [requestRender]
     */
    fun <K, V> stateMapOf(vararg pairs: Pair<K, V>): StateMap<K, V> =
        remember { StateMap(pairs.toMap()) { requestRender() } }

    /**
     * 创建局部可观察 Map，[StateMap.set] 或 [StateMap.clear] 自动调用 [requestRender]
     */
    fun <K, V> stateMapOf(initial: Map<K, V>): StateMap<K, V> = remember { StateMap(initial) { requestRender() } }

    /** 标记需要重渲染 */
    fun requestRender() {
        gui.requestRender()
    }

    /** 关闭菜单 */
    fun close() {
        player.closeContainer()
    }

    /** 在当前槽声明一个纯展示物品 */
    fun item(stack: ItemStack) {
        val slot = targetSlot()
        frame.widgets[slot] = WidgetDeclaration("item_$slot", stack)
        afterEmit()
    }

    /**
     * 在当前槽声明一个可点击按钮（视觉与点击一体）
     *
     * @param id 按钮 id（仅用于识别与调试，缺省按槽位生成；点击派发与跨帧 diff 均按槽位进行）
     * @see ClickHandlers
     */
    fun button(stack: ItemStack, id: String? = null, handlers: ClickHandlers.() -> Unit = {}) {
        val slot = targetSlot()
        val h = ClickHandlers().apply(handlers)
        frame.widgets[slot] = WidgetDeclaration(id ?: "btn_$slot", stack, h)
        afterEmit()
    }

    /** 在当前槽声明一个输入槽（玩家可放入/取出物品，内容在重渲染间保留） */
    fun slot() {
        val s = targetSlot()
        frame.inputSlots.add(s)
        afterEmit()
    }

    /** 标记当前槽为输入槽并返回其当前内容 */
    fun inputSlot(): ItemStack {
        val s = targetSlot()
        frame.inputSlots.add(s)
        afterEmit()
        return gui.renderer.container.getItem(s)
    }

    /** 读取任意槽位当前内容 */
    fun slotStack(slotIndex: Int): ItemStack = gui.renderer.container.getItem(slotIndex)

    /** 把指定槽位排除出交互（不渲染、点击被忽略） */
    fun exceptSlot(vararg slots: Int) {
        slots.forEach(gui.excludedSlots::add)
    }

    /** 清除 [exceptSlot] 标记 */
    fun clearExcept() = gui.excludedSlots.clear()

    fun at(x: Int, y: Int, action: () -> Unit) = at(y * 9 + x, action)

    fun at(slotIndex: Int, action: () -> Unit) {
        val prevCursor = frame.cursor
        frame.cursor = slotIndex
        action()
        frame.cursor = prevCursor
    }

    fun space(amount: Int = 1) {
        frame.cursor += amount
    }

    fun nextLine() {
        if (frame.cursor % 9 != 0) {
            frame.cursor = (frame.cursor / 9 + 1) * 9
        }
    }

    /**
     * 字符网格布局：每行字符串与菜单槽位一一对应，空格表示留空，
     * 其余字符按 [PatternScope.bind] 的映射在对应槽位执行内容块。
     */
    fun pattern(vararg lines: String, init: PatternScope.() -> Unit) {
        val context = PatternScope().apply(init)
        val maxRows = minOf(lines.size, gui.rows)
        for (row in 0 until maxRows) {
            val line = lines[row]
            val maxCols = minOf(line.length, 9)
            for (col in 0 until maxCols) {
                val ch = line[col]
                if (ch == ' ') continue
                val action = context.bindings[ch] ?: continue
                val cell = row * 9 + col
                val prevCell = frame.currentCell
                frame.currentCell = cell
                action(this)
                frame.currentCell = prevCell
                frame.cursor = cell + 1
            }
        }
    }

    private fun targetSlot(): Int = frame.currentCell ?: frame.cursor

    private fun afterEmit() {
        if (frame.currentCell == null) frame.cursor++
    }
}

class PatternScope {
    internal val bindings = HashMap<Char, FluxGuiScope.() -> Unit>()

    /** 把字符 [char] 绑定到一个内容块（块内组件写入该字符对应的槽位）。 */
    fun bind(char: Char, content: FluxGuiScope.() -> Unit) {
        bindings[char] = content
    }
}
