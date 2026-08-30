package top.mythcraft.flux.camera

import com.mojang.logging.LogUtils
import org.slf4j.Logger

/**
 * 收集需要延迟到 [CameraRegistry] 的 END_SERVER_TICK flush 点统一执行的动作
 */
class CameraCommandBuffer {
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
                logger.error("Error executing deferred camera command", e)
            }
        }
    }
}
