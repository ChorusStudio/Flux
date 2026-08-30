package top.mythcraft.flux.gui.backend

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import top.mythcraft.flux.gui.FluxGui
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

object FluxGuiRegistry {
    private val open = CopyOnWriteArrayList<FluxGui>()

    private val initialized = AtomicBoolean(false)

    internal fun init() {
        if (!initialized.compareAndSet(false, true)) return
        ServerTickEvents.END_SERVER_TICK.register { tick() }
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            for (gui in open) {
                if (gui.player.uuid != handler.player.uuid) continue
                gui.onMenuRemoved()
            }
        }
        ServerLifecycleEvents.SERVER_STOPPING.register { shutdown() }
    }

    internal fun onOpen(gui: FluxGui) {
        if (gui !in open) {
            open.add(gui)
        }
    }

    internal fun onClose(gui: FluxGui) {
        open.remove(gui)
    }

    private fun tick() {
        for (gui in open) {
            if (gui.player.hasDisconnected()) {
                gui.onMenuRemoved()
                continue
            }
            // 只渲染脏/实时 GUI
            if (gui.isTicking || gui.consumeRenderRequest()) {
                gui.render()
            }
        }
    }

    private fun shutdown() {
        for (gui in open.toList()) {
            gui.onMenuRemoved()
        }
        open.clear()
    }
}
