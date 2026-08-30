package top.mythcraft.flux.camera

import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import kotlin.math.atan2
import kotlin.math.sqrt

/** 相机运动与朝向的纯数学函数 */
object CameraMath {
    /** lookDir 长度平方小于该值时不修正朝向，避免 atan2 抖动。 */
    const val MIN_LOOK_DIR_LENGTH_SQ = 0.001

    /** 与目标距离平方小于该值时直接停止，避免反复微调。 */
    const val MIN_MOVE_DIST_SQ = 0.05

    /** 从 [from] 看向 [to] 的朝向角。 */
    fun lookRotation(from: Vec3, to: Vec3): CameraRotation {
        val lookDir = to.subtract(from)
        if (lookDir.lengthSqr() <= MIN_LOOK_DIR_LENGTH_SQ) {
            return CameraRotation(0f, 0f)
        }

        val yaw = (atan2(lookDir.z, lookDir.x) * (180.0 / Math.PI) - 90.0).toFloat()
        val pitch = -(atan2(lookDir.y, sqrt(lookDir.x * lookDir.x + lookDir.z * lookDir.z)) * (180.0 / Math.PI))
            .toFloat()
        return CameraRotation(yaw, pitch)
    }

    /** 角度线性插值，自动处理 ±180° 环绕。 */
    fun lerpAngleDegrees(start: Float, end: Float, fraction: Float): Float {
        var delta = end - start
        while (delta < -180f) delta += 360f
        while (delta >= 180f) delta -= 360f
        return start + delta * fraction
    }

    /** 朝目标方向的速度，带最大速度钳制。[factor] 为每 tick 接近比例（1.0 表示直接飞向目标）。 */
    fun velocityToward(current: Vec3, target: Vec3, maxSpeed: Double, factor: Double = 1.0): Vec3 {
        var velocity = target.subtract(current).scale(factor)
        val maxSpeedSq = maxSpeed * maxSpeed
        if (velocity.lengthSqr() > maxSpeedSq) {
            velocity = velocity.normalize().scale(maxSpeed)
        }
        return velocity
    }

    internal fun LivingEntity.applyYaw(yaw: Float) {
        yRot = yaw
        yHeadRot = yaw
        yBodyRot = yaw
    }

    internal fun LivingEntity.applyPitch(pitch: Float) {
        xRot = pitch
    }

    internal fun LivingEntity.applyLookAngles(rotation: CameraRotation) {
        applyYaw(rotation.yaw)
        applyPitch(rotation.pitch)
    }
}
