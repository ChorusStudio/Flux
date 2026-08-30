package top.mythcraft.flux

import net.minecraft.resources.Identifier

object FluxModReference {
    const val MOD_ID: String = "flux"

    fun idOf(path: String): Identifier = Identifier.fromNamespaceAndPath(MOD_ID, path)
}
