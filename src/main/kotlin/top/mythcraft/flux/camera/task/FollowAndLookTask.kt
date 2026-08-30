package top.mythcraft.flux.camera.task

import net.minecraft.world.phys.Vec3
import top.mythcraft.flux.camera.CameraMath
import top.mythcraft.flux.camera.CameraMath.applyLookAngles
import top.mythcraft.flux.camera.CameraTask
import top.mythcraft.flux.camera.CinematicCamera

/**
 * 持续跟随：每 tick 向 [positionProvider] 移动，并注视 [lookAtProvider]。
 * 仅在 [CinematicCamera.destroy] 或被新任务中断时结束。
 */
class FollowAndLookTask(
    private val positionProvider: () -> Vec3,
    private val lookAtProvider: () -> Vec3,
    private val speedMultiplier: Double = 0.15,
    private val maxSpeed: Double = 16.0,
) : CameraTask {

    override fun onStart(camera: CinematicCamera) {
        camera.entity.isNoAi = false
    }

    override fun onInterrupt(camera: CinematicCamera) {
        camera.entity.isNoAi = true
    }

    override fun tick(camera: CinematicCamera): Boolean {
        val targetPos = positionProvider()
        val lookAtPos = lookAtProvider()
        val bat = camera.entity

        if (bat.distanceToSqr(targetPos) > CameraMath.MIN_MOVE_DIST_SQ) {
            bat.deltaMovement = CameraMath.velocityToward(
                current = bat.position(),
                target = targetPos,
                maxSpeed = maxSpeed,
                factor = speedMultiplier
            )
            bat.hurtMarked = true
        } else {
            bat.deltaMovement = Vec3.ZERO
        }

        if (lookAtPos.subtract(bat.position()).lengthSqr() > CameraMath.MIN_LOOK_DIR_LENGTH_SQ) {
            bat.applyLookAngles(CameraMath.lookRotation(bat.position(), lookAtPos))
        }

        return false
    }
}
