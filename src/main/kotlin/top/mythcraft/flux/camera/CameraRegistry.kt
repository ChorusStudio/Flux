package top.mythcraft.flux.camera

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 所有实体生命周期操作（spawn / discard）经 [defer] 收集到 [CameraCommandBuffer]，
 * 在 END_SERVER_TICK 一次性 flush，避免在 vanilla 集合迭代中修改实体。
 */
object CameraRegistry {
    private val buffer = CameraCommandBuffer()
    private val initialized = AtomicBoolean(false)

    fun init() {
        if (!initialized.compareAndSet(false, true)) return
        ServerTickEvents.END_SERVER_TICK.register { tick() }
    }

    /** 相机实体生命周期操作的唯一出口。 */
    fun defer(action: () -> Unit) {
        buffer.defer(action)
    }

    private fun tick() {
        buffer.flush()
    }
}
