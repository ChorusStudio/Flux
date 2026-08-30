package top.mythcraft.flux

import net.fabricmc.api.ModInitializer
import top.mythcraft.flux.camera.CameraRegistry
import top.mythcraft.flux.gui.backend.FluxGuiRegistry
import top.mythcraft.flux.scene.backend.FluxSceneRegistry

object FluxMod : ModInitializer {
    override fun onInitialize() {
        FluxSceneRegistry.init()
        FluxGuiRegistry.init()
        CameraRegistry.init()
    }
}
