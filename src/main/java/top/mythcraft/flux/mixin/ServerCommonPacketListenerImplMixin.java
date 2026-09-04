package top.mythcraft.flux.mixin;

import com.mojang.authlib.GameProfile;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerCommonPacketListener;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.mythcraft.flux.scene.backend.FluxVisibility;

@Mixin(value = ServerCommonPacketListenerImpl.class, priority = 400)
public abstract class ServerCommonPacketListenerImplMixin implements ServerCommonPacketListener {
    @Shadow
    @Final
    protected MinecraftServer server;

    @Shadow
    public abstract GameProfile getOwner();

    @Inject(
            method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    public void flux$onSendPacket(Packet<?> packet, @Nullable ChannelFutureListener listener, CallbackInfo ci) {
        ServerPlayer player = this.server.getPlayerList().getPlayer(this.getOwner().id());
        if (player == null) {
            return;
        }
        if (FluxVisibility.INSTANCE.filter(packet, player) == FluxVisibility.FilterResult.DENY) {
            ci.cancel();
        }
    }
}
