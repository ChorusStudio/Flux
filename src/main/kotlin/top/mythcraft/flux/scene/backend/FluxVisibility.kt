package top.mythcraft.flux.scene.backend

import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.protocol.game.ClientboundBundlePacket
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object FluxVisibility {
    enum class FilterResult { ALLOW, DENY }

    private data class Key(val dimension: ResourceKey<Level>, val entityId: Int)

    private val viewers = ConcurrentHashMap<Key, UUID>()

    private val bypass: ThreadLocal<Boolean> = ThreadLocal.withInitial { false }

    fun register(dimension: ResourceKey<Level>, entityId: Int, viewer: UUID) {
        viewers[Key(dimension, entityId)] = viewer
    }

    fun unregister(dimension: ResourceKey<Level>, entityId: Int) {
        viewers.remove(Key(dimension, entityId))
    }

    /** 清空全部可见性映射 */
    fun clear() {
        viewers.clear()
    }

    /**
     * 非观众的 spawn / data 包由 mixin 在写出前拦截。
     * 过滤运行于 Netty 线程，仅读取本表。
     */
    fun registerFilter() = Unit

    fun filter(packet: Packet<*>, player: ServerPlayer): FilterResult {
        if (bypass.get()) return FilterResult.ALLOW
        if (packet is ClientboundBundlePacket) return handleBundle(packet, player)
        return filterOne(packet, player)
    }

    internal fun runWithBypass(action: Runnable) {
        bypass.set(true)
        try {
            action.run()
        } finally {
            bypass.remove()
        }
    }

    private fun filterOne(packet: Packet<*>, player: ServerPlayer): FilterResult {
        val id = when (packet) {
            is ClientboundAddEntityPacket -> packet.id
            is ClientboundSetEntityDataPacket -> packet.id
            else -> return FilterResult.ALLOW
        }
        val viewer = viewers[Key(player.level().dimension(), id)] ?: return FilterResult.ALLOW
        return if (viewer == player.uuid) FilterResult.ALLOW else FilterResult.DENY
    }

    private fun handleBundle(bundle: ClientboundBundlePacket, receiver: ServerPlayer): FilterResult {
        val original = bundle.subPackets().toList()
        val filtered = original.filter { sub -> filterOne(sub, receiver) != FilterResult.DENY }
        if (filtered.size == original.size) return FilterResult.ALLOW
        if (filtered.isNotEmpty()) {
            runWithBypass { receiver.connection.send(ClientboundBundlePacket(filtered)) }
        }
        return FilterResult.DENY
    }
}
