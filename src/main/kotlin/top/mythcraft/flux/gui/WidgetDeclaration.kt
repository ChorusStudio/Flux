package top.mythcraft.flux.gui

import net.minecraft.world.inventory.ClickType
import net.minecraft.world.item.ItemStack

/** 槽位声明的组件 */
class WidgetDeclaration(
    val id: String,
    val stack: ItemStack,
    val handlers: ClickHandlers? = null,
)

/** 回调在点击发生时被 [dispatch] 依次触发 */
class ClickHandlers {
    var onLeft: (() -> Unit)? = null
    var onRight: (() -> Unit)? = null
    var onShiftLeft: (() -> Unit)? = null
    var onShiftRight: (() -> Unit)? = null
    var onAny: (() -> Unit)? = null

    /** 左键点击 */
    fun left(action: () -> Unit) {
        onLeft = action
    }

    /** 右键点击 */
    fun right(action: () -> Unit) {
        onRight = action
    }

    /** Shift + 左键 */
    fun shiftLeft(action: () -> Unit) {
        onShiftLeft = action
    }

    /** Shift + 右键 */
    fun shiftRight(action: () -> Unit) {
        onShiftRight = action
    }

    /** 任意点击（先于具体按键回调触发） */
    fun any(action: () -> Unit) {
        onAny = action
    }

    /**
     * 把四向点击统一映射为有符号增量：左键/Shift左键为正、右键/Shift右键为负。
     *
     * @param step 普通点击的步长
     * @param shiftStep Shift 点击的步长
     * @param onDelta 收到增量后的回调（已带符号）
     */
    fun stepper(step: Int = 1, shiftStep: Int = 8, onDelta: (Int) -> Unit) {
        left { onDelta(step) }
        right { onDelta(-step) }
        shiftLeft { onDelta(shiftStep) }
        shiftRight { onDelta(-shiftStep) }
    }

    fun dispatch(type: ClickType, button: Int) {
        onAny?.invoke()
        when (type) {
            ClickType.PICKUP if button == 0 -> onLeft?.invoke()
            ClickType.PICKUP if button == 1 -> onRight?.invoke()
            ClickType.QUICK_MOVE if button == 0 -> onShiftLeft?.invoke()
            ClickType.QUICK_MOVE if button == 1 -> onShiftRight?.invoke()
            else -> {}
        }
    }
}
