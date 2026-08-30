package top.mythcraft.flux.camera

import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3

/**
 * 相机位姿：位置 + 朝向角（度）。不可变，用于构造相机与任务目标。
 */
data class CameraPose(
    val pos: Vec3,
    val yaw: Float,
    val pitch: Float,
) {
    companion object {
        fun of(entity: Entity): CameraPose = CameraPose(entity.position(), entity.yRot, entity.xRot)
    }
}
