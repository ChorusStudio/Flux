package top.mythcraft.flux.gui.backend

import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.*
import net.minecraft.world.item.ItemStack
import top.mythcraft.flux.gui.ClickHandlers
import top.mythcraft.flux.gui.FluxGui
import top.mythcraft.flux.gui.FluxGuiFrame
import top.mythcraft.flux.gui.WidgetDeclaration

class FluxGuiRenderer(
    private val gui: FluxGui,
    val rows: Int,
) {
    val container = SimpleContainer(rows * 9)
    private val unlockedSlots: MutableSet<Int> = mutableSetOf()
    private val lastWidgets = HashMap<Int, WidgetDeclaration>()
    private val lastHandlers = HashMap<Int, ClickHandlers?>()
    private val cooldown = HashMap<Int, Long>()

    internal fun applyFrame(frame: FluxGuiFrame) {
        val prevInputSlots = HashSet(unlockedSlots)
        unlockedSlots.clear()
        unlockedSlots.addAll(frame.inputSlots)

        // 清除消失的组件
        for (slot in lastWidgets.keys) {
            if (slot in frame.widgets || slot in frame.inputSlots) continue
            container.setItem(slot, ItemStack.EMPTY)
        }

        // 失效的输入槽：上一帧还是输入槽、这一帧不再声明且无组件，把玩家放入的物品返还（放不下则掉落）
        for (slot in prevInputSlots) {
            if (slot in frame.inputSlots || slot in frame.widgets) continue
            val leftover = container.getItem(slot)
            if (!leftover.isEmpty) {
                container.setItem(slot, ItemStack.EMPTY)
                gui.player.inventory.placeItemBackInInventory(leftover)
            }
        }

        // 设置新增/变化的组件（输入槽不覆盖，内容由玩家控制）
        for ((slot, widget) in frame.widgets) {
            if (slot in gui.excludedSlots || slot in frame.inputSlots) continue
            val prev = lastWidgets[slot]
            if (prev == null || !ItemStack.matches(prev.stack, widget.stack)) {
                container.setItem(slot, widget.stack)
            }
        }

        lastHandlers.clear()
        for ((slot, widget) in frame.widgets) {
            lastHandlers[slot] = widget.handlers
        }
        lastWidgets.clear()
        lastWidgets.putAll(frame.widgets)
    }

    private fun handleClick(slotId: Int, button: Int, type: ContainerInput) {
        if (slotId in unlockedSlots || slotId in gui.excludedSlots) return
        if (slotId !in 0 until container.containerSize) return
        val now = System.currentTimeMillis()
        if (now - (cooldown[slotId] ?: 0L) < gui.clickCooldownMs) return
        cooldown[slotId] = now
        lastHandlers[slotId]?.dispatch(type, button)
    }

    fun createMenu(containerId: Int, inventory: Inventory): AbstractContainerMenu = FluxMenu(containerId, inventory)

    inner class FluxMenu(
        containerId: Int,
        inventory: Inventory
    ) : ChestMenu(menuType(rows), containerId, inventory, this@FluxGuiRenderer.container, rows) {
        override fun clicked(slotId: Int, button: Int, containerInput: ContainerInput, player: Player) {
            if (slotId in unlockedSlots) {
                super.clicked(slotId, button, containerInput, player)
                return
            }
            if (slotId in 0 until this@FluxGuiRenderer.container.containerSize) {
                handleClick(slotId, button, containerInput)
                gui.render()
                return
            }
            super.clicked(slotId, button, containerInput, player)
        }

        override fun quickMoveStack(player: Player, index: Int): ItemStack = ItemStack.EMPTY

        override fun stillValid(player: Player): Boolean = true

        override fun canDragTo(slot: Slot): Boolean = false

        override fun removed(player: Player) {
            super.removed(player)
            gui.onMenuRemoved()
        }
    }

    companion object {
        private fun menuType(rows: Int): MenuType<ChestMenu> = when (rows) {
            1 -> MenuType.GENERIC_9x1
            2 -> MenuType.GENERIC_9x2
            3 -> MenuType.GENERIC_9x3
            4 -> MenuType.GENERIC_9x4
            5 -> MenuType.GENERIC_9x5
            else -> MenuType.GENERIC_9x6
        }
    }
}
