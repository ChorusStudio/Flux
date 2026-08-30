package top.mythcraft.flux.gui

import com.mojang.logging.LogUtils
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.MenuProvider
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import org.slf4j.Logger
import top.mythcraft.flux.gui.backend.FluxGuiRegistry
import top.mythcraft.flux.gui.backend.FluxGuiRenderer

/**
 * 声明式箱子菜单：每帧由 [content] 声明组件，框架 diff 后只更新变化的槽位，点击按槽位派发。
 *
 * 状态用 [FluxGuiScope.mutableStateOf] 或 [FluxGuiScope.stateMapOf] 局部保存，写操作自动触发重渲染。
 *
 * @param player 持有该菜单的玩家
 * @param title 菜单标题
 * @param rows 菜单行数（`1..6`）
 * @param ticking 为 true 时每 tick 重渲染；否则仅在脏标记时渲染
 * @param content 每次渲染时执行，声明组件
 * @see FluxGuiScope
 */
class FluxGui(
    val player: ServerPlayer,
    val title: Component,
    val rows: Int = 6,
    private val ticking: Boolean = false,
    private val content: FluxGuiScope.() -> Unit,
) {
    init {
        require(rows in 1..6) { "Rows must be between 1 and 6" }
    }

    private val logger: Logger = LogUtils.getLogger()

    internal val excludedSlots: MutableSet<Int> = mutableSetOf()
    internal val clickCooldownMs = 50L
    internal val renderer = FluxGuiRenderer(this, rows)

    private val memory = ArrayList<Any?>()
    private var isRendering = false
    private var renderRequested = false
    private var lifecycle = Lifecycle.IDLE

    private var consecutiveRenderFailures = 0

    private var onOpenCallback: (() -> Unit)? = null
    private var onCloseCallback: (() -> Unit)? = null

    private enum class Lifecycle { IDLE, OPEN, CLOSED }

    /**
     * 打开菜单。幂等，已打开或已断线则忽略
     */
    fun open() {
        if (lifecycle == Lifecycle.OPEN) return
        if (player.hasDisconnected()) return
        render()
        player.openMenu(object : MenuProvider {
            override fun getDisplayName(): Component = title

            override fun createMenu(syncId: Int, playerInv: Inventory, player: Player): AbstractContainerMenu =
                renderer.createMenu(syncId, playerInv)
        })
        lifecycle = Lifecycle.OPEN
        FluxGuiRegistry.onOpen(this)
        onOpenCallback?.invoke()
    }

    /** 打开回调（每次 [open] 成功时触发）。 */
    fun onOpen(callback: () -> Unit): FluxGui = apply { onOpenCallback = callback }

    /** 关闭回调（菜单被移除时触发一次）。 */
    fun onClose(callback: () -> Unit): FluxGui = apply { onCloseCallback = callback }

    /**
     * 标记需要重渲染
     * 渲染由 [FluxGuiRegistry] 在 `END_SERVER_TICK` 统一 flush。
     */
    fun requestRender() {
        renderRequested = true
    }

    internal val isTicking: Boolean get() = ticking

    internal fun consumeRenderRequest(): Boolean {
        val result = renderRequested
        renderRequested = false
        return result
    }

    internal fun render() {
        if (isRendering) {
            renderRequested = true
            return
        }
        isRendering = true
        try {
            do {
                renderRequested = false
                renderPass()
            } while (renderRequested)
            consecutiveRenderFailures = 0
        } catch (e: Exception) {
            if (noteRenderFailure(e)) {
                player.closeContainer()
            }
        } finally {
            isRendering = false
        }
    }

    private fun noteRenderFailure(e: Exception): Boolean {
        consecutiveRenderFailures++
        if (consecutiveRenderFailures == 1 || consecutiveRenderFailures % 100 == 0) {
            logger.error("An error occurred while FluxGui rendering", e)
        }
        return consecutiveRenderFailures >= 3
    }

    private fun renderPass() {
        val frame = FluxGuiFrame()
        val scope = FluxGuiScope(player, this, frame, memory)
        content(scope)
        renderer.applyFrame(frame)
    }

    internal fun onMenuRemoved() {
        if (lifecycle != Lifecycle.OPEN) return
        lifecycle = Lifecycle.CLOSED
        FluxGuiRegistry.onClose(this)
        onCloseCallback?.invoke()
    }
}
