package top.mythcraft.flux.scene

import com.mojang.logging.LogUtils
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4d
import org.slf4j.Logger
import top.mythcraft.flux.scene.backend.FluxCommandBuffer
import top.mythcraft.flux.scene.backend.FluxDisplayPool
import top.mythcraft.flux.scene.backend.FluxSceneRegistry
import java.util.*

/**
 * 声明式显示场景：每 tick 对每个观众调用 [view] 声明其画面，
 * 由 [FluxSceneRegistry] 在 `END_SERVER_TICK` 执行 diff 并应用到实体。
 *
 * 每个观众对应一个独立画布（内含各自的 [FluxDisplayPool]）；
 * 观众离开（连续不再调用 [view]、显式调用 [removeViewer]、或断线）时其画布自动销毁。
 * 构造即注册到 [FluxSceneRegistry]，用 [destroy] 显式注销并释放全部实体。
 *
 * @param sceneId 场景标识（仅用于日志与调试，不参与运行时隔离）
 */
class FluxScene(private val sceneId: String) {
    companion object {
        private const val REANCHOR_THRESHOLD_SQ = 104.0 * 104.0 // 画面原点约 120 格一跳。viewRange 2.5 ≈ 160
    }

    private val logger: Logger = LogUtils.getLogger()

    private class CanvasRuntime(
        var anchor: Vec3,
        val pool: FluxDisplayPool,
        var content: (FluxCanvas.() -> Unit)? = null,
        var screenCenter: Vec3 = Vec3.ZERO,
        var orientation: Matrix4d = Matrix4d(),
        var activeThisTick: Boolean = false,
    )

    private val canvases = HashMap<UUID, CanvasRuntime>()
    private val toDestroy: MutableList<FluxDisplayPool> = mutableListOf()
    private var consecutiveFailures = 0
    private var destroyed = false

    // 渲染期复用的画布与基础矩阵，避免每观众每 tick 分配 FluxCanvas/Matrix4d
    private val renderCanvas = FluxCanvas()
    private val renderBase = Matrix4d()

    init {
        FluxSceneRegistry.register(this)
    }

    /**
     * 声明（更新）某个观众本 tick 的画面。每 tick 需对每个仍在观看的观众调用一次，
     * 否则其画布会被当作「离开」而销毁。
     *
     * @param viewer 观众
     * @param screenCenter 屏幕锚点（画布实体放置的位置；漂移超过阈值时传送该实体）
     * @param orientation 屏幕朝向矩阵
     * @param content 声明式内容块，在 [FluxCanvas] 上绘制本观众的画面
     * @see FluxCanvas.screenOrientation
     */
    fun view(viewer: ServerPlayer, screenCenter: Vec3, orientation: Matrix4d, content: FluxCanvas.() -> Unit) {
        if (destroyed) return
        val runtime = canvases[viewer.uuid]
        if (runtime == null || !runtime.pool.isSameLevel(viewer.level())) {
            runtime?.let { toDestroy.add(it.pool) }
            val created = CanvasRuntime(
                screenCenter,
                FluxDisplayPool(viewer.level(), viewer.uuid, screenCenter)
            )
            canvases[viewer.uuid] = created
            fill(created, screenCenter, orientation, content)
        } else {
            fill(runtime, screenCenter, orientation, content)
        }
    }

    private fun fill(
        runtime: CanvasRuntime,
        screenCenter: Vec3,
        orientation: Matrix4d,
        content: FluxCanvas.() -> Unit
    ) {
        runtime.content = content
        runtime.screenCenter = screenCenter
        runtime.orientation = orientation
        runtime.activeThisTick = true
    }

    /** 立即移除某观众并释放其画布（等价于该观众此后不再调用 [view]） */
    fun removeViewer(viewer: ServerPlayer) {
        if (destroyed) return
        val runtime = canvases.remove(viewer.uuid) ?: return
        toDestroy.add(runtime.pool)
    }

    /** 销毁：注销 registry 并释放全部画布实体，之后不可再使用。幂等。 */
    fun destroy() {
        if (destroyed) return
        destroyed = true
        FluxSceneRegistry.unregister(this)
        FluxSceneRegistry.queueDestroy(toDestroy)
        FluxSceneRegistry.queueDestroy(canvases.values.map(CanvasRuntime::pool))
        toDestroy.clear()
        canvases.clear()
    }

    internal fun render(buffer: FluxCommandBuffer) {
        toDestroy.forEach { it.destroy(buffer) }
        toDestroy.clear()

        val stale = mutableListOf<UUID>()
        for ((uuid, runtime) in canvases) {
            if (!runtime.activeThisTick) {
                stale.add(uuid)
                runtime.pool.destroy(buffer)
                continue
            }
            if (runtime.anchor.distanceToSqr(runtime.screenCenter) > REANCHOR_THRESHOLD_SQ) {
                runtime.pool.reanchor(runtime.screenCenter, buffer)
                runtime.anchor = runtime.screenCenter
            }
            renderBase.identity()
                .translate(
                    runtime.screenCenter.x - runtime.anchor.x,
                    runtime.screenCenter.y - runtime.anchor.y,
                    runtime.screenCenter.z - runtime.anchor.z
                )
                .mul(runtime.orientation)
            renderCanvas.begin(renderBase)
            runtime.content?.invoke(renderCanvas)
            runtime.pool.diff(renderCanvas.nodes, buffer)
            runtime.activeThisTick = false
        }
        stale.forEach(canvases::remove)
    }

    internal fun noteFailure(e: Exception): Boolean {
        consecutiveFailures++
        if (consecutiveFailures == 1 || consecutiveFailures % 100 == 0) {
            logger.error("An error occurred while FluxScene '$sceneId' rendering", e)
        }
        return consecutiveFailures >= 3
    }

    internal fun noteSuccess() {
        consecutiveFailures = 0
    }
}
