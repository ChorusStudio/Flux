package top.mythcraft.flux.scene.backend

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import top.mythcraft.flux.scene.FluxScene
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

object FluxSceneRegistry {
    private val scenes = CopyOnWriteArrayList<FluxScene>()
    private val buffer = FluxCommandBuffer()
    private val destroyQueue = ArrayDeque<FluxDisplayPool>()

    private val initialized = AtomicBoolean(false)

    internal fun init() {
        if (!initialized.compareAndSet(false, true)) return
        FluxVisibility.registerFilter()
        ServerTickEvents.END_SERVER_TICK.register { tick() }
        ServerLifecycleEvents.SERVER_STOPPING.register { shutdown() }
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            scenes.forEach { scene -> scene.removeViewer(handler.player) } // 清理画布
        }
    }

    internal fun register(scene: FluxScene) {
        scenes.add(scene)
    }

    internal fun unregister(scene: FluxScene) {
        scenes.remove(scene)
    }

    internal fun queueDestroy(pools: Collection<FluxDisplayPool>) {
        destroyQueue.addAll(pools)
    }

    private fun tick() {
        try {
            drainDestroyQueue()
            for (scene in scenes) {
                // 连续失败则销毁场景；日志限频
                try {
                    scene.render(buffer)
                    scene.noteSuccess()
                } catch (e: Exception) {
                    if (scene.noteFailure(e)) {
                        scene.destroy()
                    }
                }
            }
            drainDestroyQueue()
        } finally {
            buffer.flush()
        }
    }

    private fun shutdown() {
        for (scene in scenes) {
            scene.destroy()
        }
        drainDestroyQueue()
        buffer.flush()
        buffer.clear()
        FluxVisibility.clear()
    }

    private fun drainDestroyQueue() {
        while (destroyQueue.isNotEmpty()) {
            val pool = destroyQueue.removeFirst()
            pool.destroy(buffer)
        }
    }
}
