package top.mythcraft.flux.camera.task

import net.minecraft.world.phys.Vec3
import top.mythcraft.flux.camera.*
import top.mythcraft.flux.camera.CameraMath.applyLookAngles
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * 沿二次贝塞尔曲线从当前点抛到 [targetPos]。
 * 位置按 P(t) = (1-t)^2·P0 + 2t(1-t)·P1 + t^2·P2；朝向取曲线切线方向，
 * 前 20% 时间从起始朝向快速转到切线朝向（lookProg = t*5 封顶 1）。
 *
 * @param arcHeight 在起跳点与目标点较高者之上额外增加的高度（格）
 */
class ParabolaFlyTask(
    private val targetPos: Vec3,
    private val durationTicks: Int,
    private val arcHeight: Double = 5.0,
) : CameraTask {
    private var elapsedTicks = 0
    private lateinit var startPose: CameraPose
    private lateinit var controlPoint: Vec3

    override fun onStart(camera: CinematicCamera) {
        startPose = CameraPose.of(camera.entity)

        val midX = (startPose.pos.x + targetPos.x) / 2.0
        val midZ = (startPose.pos.z + targetPos.z) / 2.0
        val maxY = maxOf(startPose.pos.y, targetPos.y)

        controlPoint = Vec3(midX, maxY + arcHeight, midZ)
    }

    override fun onFinish(camera: CinematicCamera) {
        camera.entity.setPos(targetPos.x, targetPos.y, targetPos.z)
    }

    override fun tick(camera: CinematicCamera): Boolean {
        elapsedTicks++
        if (elapsedTicks >= durationTicks) return true

        val t = elapsedTicks.toDouble() / durationTicks
        val u = 1.0 - t
        val u2 = u * u
        val t2 = t * t

        // P(t) = (1-t)^2·P0 + 2t(1-t)·P1 + t^2·P2
        val currentPos = Vec3(
            u2 * startPose.pos.x + 2.0 * u * t * controlPoint.x + t2 * targetPos.x,
            u2 * startPose.pos.y + 2.0 * u * t * controlPoint.y + t2 * targetPos.y,
            u2 * startPose.pos.z + 2.0 * u * t * controlPoint.z + t2 * targetPos.z,
        )

        // 曲线导数：P'(t) = 2(1-t)(P1-P0) + 2t(P2-P1)
        val dX = 2.0 * u * (controlPoint.x - startPose.pos.x) + 2.0 * t * (targetPos.x - controlPoint.x)
        val dY = 2.0 * u * (controlPoint.y - startPose.pos.y) + 2.0 * t * (targetPos.y - controlPoint.y)
        val dZ = 2.0 * u * (controlPoint.z - startPose.pos.z) + 2.0 * t * (targetPos.z - controlPoint.z)

        val horizontalDistance = sqrt(dX * dX + dZ * dZ)
        val tangentYaw = (atan2(dZ, dX) * (180.0 / Math.PI)).toFloat() - 90f
        val tangentPitch = (-(atan2(dY, horizontalDistance) * (180.0 / Math.PI))).toFloat()

        val lookProgress = (t * 5.0).coerceAtMost(1.0).toFloat()
        val interpolatedYaw = CameraMath.lerpAngleDegrees(startPose.yaw, tangentYaw, lookProgress)
        val interpolatedPitch = startPose.pitch + (tangentPitch - startPose.pitch) * lookProgress

        camera.entity.setPos(currentPos)
        camera.entity.applyLookAngles(CameraRotation(interpolatedYaw, interpolatedPitch))

        return false
    }
}
