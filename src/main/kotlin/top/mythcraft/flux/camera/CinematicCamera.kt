package top.mythcraft.flux.camera

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.ambient.Bat
import java.util.*

/**
 * 运镜相机：一个隐身的 Bat 实体 + 一个当前任务。
 *
 * 生命周期：
 * - 构造只创建并初始化 [Bat] 对象，不进入世界；spawn 经 [CameraRegistry.defer] 延迟到 END_SERVER_TICK 执行。
 * - [onSpawned] 用于在实体出生后执行 setCamera / 启动任务。
 * - [execute] 启动/切换任务；[tick] 推进任务；[destroy] 幂等销毁。
 * - discard 同样经 [CameraRegistry.defer] 延迟到 END_SERVER_TICK 执行；discard 前先使乘客离乘（`ejectPassengers`）。
 *
 * @param level 相机所在维度
 * @param initialPose 初始位姿
 * @param tag 附加到 Bat 上的识别 tag，可用于过滤该实体
 */
class CinematicCamera(
    val level: ServerLevel,
    initialPose: CameraPose,
    tag: String = CAMERA_BAT_TAG,
) {
    companion object {
        const val CAMERA_BAT_TAG = "flux_camera_bat"
    }

    val entity: Bat = Bat(EntityTypes.BAT, level).apply {
        setPos(initialPose.pos)
        yRot = initialPose.yaw
        xRot = initialPose.pitch
        yBodyRot = initialPose.yaw
        yHeadRot = initialPose.yaw
        isNoGravity = true
        isInvulnerable = true
        isNoAi = true
        isSilent = true
        isResting = false
        setPersistenceRequired()
        addEffect(
            MobEffectInstance(MobEffects.INVISIBILITY, MobEffectInstance.INFINITE_DURATION, 0, false, false, false)
        )
        addTag(tag)
    }

    private var spawned = false
    private var destroyed = false
    private var taskStarted = false
    private var currentTask: CameraTask? = null
    private val onSpawnedCallbacks = ArrayDeque<(CinematicCamera) -> Unit>()

    init {
        CameraRegistry.defer {
            if (destroyed) return@defer
            if (level.addFreshEntity(entity)) {
                spawned = true
                onSpawnedCallbacks.forEach { it(this) }
                currentTask?.let { task ->
                    if (!taskStarted) {
                        task.onStart(this)
                        taskStarted = true
                    }
                }
            } else {
                destroy()
            }
        }
    }

    val isSpawned: Boolean get() = spawned

    /** 实体加入世界后的回调；若已加入则立即执行。用于 setCamera / 启动任务 */
    fun onSpawned(action: (CinematicCamera) -> Unit) {
        if (destroyed) return
        if (spawned) action(this) else onSpawnedCallbacks.addLast(action)
    }

    /** 启动新任务并中断当前任务。已销毁则忽略。尚未加入世界时只记录任务，加入后自动启动 */
    fun execute(task: CameraTask) {
        if (destroyed) return
        currentTask?.onInterrupt(this)
        currentTask = task
        taskStarted = false
        if (spawned) {
            task.onStart(this)
            taskStarted = true
        }
    }

    /** 推进当前任务；尚未加入世界、已销毁或实体被移除则忽略。任务返回 true 时结束并触发 onFinish */
    fun tick() {
        if (!spawned || destroyed || entity.isRemoved) return
        val task = currentTask ?: return
        if (task.tick(this)) {
            task.onFinish(this)
            currentTask = null
            taskStarted = false
        }
    }

    /** 幂等销毁：中断任务、清空加入世界前回调；已加入世界则先使乘客离乘再 discard，并延迟到 END_SERVER_TICK */
    fun destroy() {
        if (destroyed) return
        destroyed = true
        currentTask?.onInterrupt(this)
        currentTask = null
        onSpawnedCallbacks.clear()
        if (spawned) {
            CameraRegistry.defer {
                if (!entity.isRemoved) {
                    entity.ejectPassengers()
                    entity.discard()
                }
            }
        }
    }
}
