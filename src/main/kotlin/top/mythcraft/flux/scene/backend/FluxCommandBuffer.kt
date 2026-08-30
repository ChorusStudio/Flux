package top.mythcraft.flux.scene.backend

import com.mojang.logging.LogUtils
import org.slf4j.Logger

/**
 * 渲染期只收集变更，所有实体生命周期操作（spawn/discard/ride/数据更新）都延迟到
 * [FluxSceneRegistry] 的 END_SERVER_TICK flush 点统一执行，避免在 vanilla 集合迭代中修改实体。
 */
class FluxCommandBuffer {
    private val logger: Logger = LogUtils.getLogger()

    private val tasks = ArrayDeque<() -> Unit>()

    fun defer(action: () -> Unit) {
        tasks.addLast(action)
    }

    fun flush() {
        while (tasks.isNotEmpty()) {
            val task = tasks.removeFirst()
            try {
                task.invoke()
            } catch (e: Exception) {
                logger.error("Error executing deferred FluxScene command", e)
            }
        }
    }

    fun clear() = tasks.clear()
}
