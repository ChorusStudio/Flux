package top.mythcraft.flux.camera.task

import net.minecraft.world.phys.Vec3
import top.mythcraft.flux.camera.*
import top.mythcraft.flux.camera.CameraMath.applyLookAngles
import top.mythcraft.flux.camera.CameraMath.applyPitch
import top.mythcraft.flux.camera.CameraMath.applyYaw

/**
 * 按缓动曲线从起点飞到 [targetPos]
 */
class FlyToTask(
    private val targetPos: Vec3,
    private val targetYaw: Float? = null,
    private val targetPitch: Float? = null,
    private val lookAtTarget: (() -> Vec3)? = null,
    private val durationTicks: Int,
    private val maxSpeed: Double = 15.0,
    private val easing: (Double) -> Double = Easing.OUT_QUINT,
) : CameraTask {
    private var elapsedTicks = 0
    private lateinit var startPose: CameraPose

    override fun onStart(camera: CinematicCamera) {
        startPose = CameraPose.of(camera.entity)
        camera.entity.isNoAi = false
    }

    override fun onFinish(camera: CinematicCamera) {
        camera.entity.setPos(targetPos.x, targetPos.y, targetPos.z)
        camera.entity.deltaMovement = Vec3.ZERO
        camera.entity.isNoAi = true

        val rotation = lookAtTarget?.let {
            CameraMath.lookRotation(camera.entity.position(), it())
        }
        if (rotation != null) {
            camera.entity.applyLookAngles(rotation)
            return
        }
        targetYaw?.let { camera.entity.applyYaw(it) }
        targetPitch?.let { camera.entity.applyPitch(it) }
    }

    override fun onInterrupt(camera: CinematicCamera) {
        camera.entity.isNoAi = true
    }

    override fun tick(camera: CinematicCamera): Boolean {
        elapsedTicks++
        if (elapsedTicks >= durationTicks) return true

        val progress = easing(elapsedTicks.toDouble() / durationTicks)
        val currentPos = startPose.pos.lerp(targetPos, progress)
        camera.entity.deltaMovement = CameraMath.velocityToward(
            current = camera.entity.position(),
            target = currentPos,
            maxSpeed = maxSpeed
        )
        camera.entity.hurtMarked = true

        if (lookAtTarget != null) {
            val lookAt = lookAtTarget.invoke()
            val targetRotation =
                if (lookAt.subtract(camera.entity.position()).lengthSqr() > CameraMath.MIN_LOOK_DIR_LENGTH_SQ) {
                    CameraMath.lookRotation(camera.entity.position(), lookAt)
                } else {
                    null
                }
            val yaw = targetRotation?.yaw ?: startPose.yaw
            val pitch = targetRotation?.pitch ?: startPose.pitch
            camera.entity.applyLookAngles(
                CameraRotation(
                    CameraMath.lerpAngleDegrees(startPose.yaw, yaw, progress.toFloat()),
                    startPose.pitch + (pitch - startPose.pitch) * progress.toFloat()
                )
            )
        } else {
            targetYaw?.let {
                camera.entity.applyYaw(CameraMath.lerpAngleDegrees(startPose.yaw, it, progress.toFloat()))
            }
            targetPitch?.let {
                camera.entity.applyPitch(startPose.pitch + (it - startPose.pitch) * progress.toFloat())
            }
        }

        return false
    }
}
