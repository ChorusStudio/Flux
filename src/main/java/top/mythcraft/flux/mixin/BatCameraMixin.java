package top.mythcraft.flux.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.mythcraft.flux.camera.CinematicCamera;

@Mixin(Bat.class)
public abstract class BatCameraMixin extends Entity {
    public BatCameraMixin(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(method = "customServerAiStep", at = @At("HEAD"), cancellable = true)
    private void flux$lobotomizeCameraBat(CallbackInfo ci) {
        if (this.getTags().contains(CinematicCamera.CAMERA_BAT_TAG)) {
            ci.cancel();
        }
    }
}
