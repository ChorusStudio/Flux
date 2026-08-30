package top.mythcraft.flux.camera

/**
 * 相机任务
 *
 * 生命周期：
 * - [onStart]：任务被 [CinematicCamera.execute] 启动时调用一次。
 * - [tick]：每 tick 由 [CinematicCamera.tick] 调用；返回 true 表示任务完成。
 * - [onFinish]：任务完成时调用一次。
 * - [onInterrupt]：任务被新任务中断、或相机 [CinematicCamera.destroy] 时调用一次。
 *
 * [onFinish] 与 [onInterrupt] 互斥。
 */
interface CameraTask {
    fun onStart(camera: CinematicCamera) {}

    fun tick(camera: CinematicCamera): Boolean

    fun onFinish(camera: CinematicCamera) {}

    fun onInterrupt(camera: CinematicCamera) {}
}

/** 给任务追加完成回调（在 delegate.onFinish 之后执行） */
fun CameraTask.withCallback(onFinishCallback: () -> Unit): CameraTask {
    val delegate = this
    return object : CameraTask {
        override fun onStart(camera: CinematicCamera) = delegate.onStart(camera)
        override fun tick(camera: CinematicCamera) = delegate.tick(camera)
        override fun onFinish(camera: CinematicCamera) {
            delegate.onFinish(camera)
            onFinishCallback()
        }

        override fun onInterrupt(camera: CinematicCamera) = delegate.onInterrupt(camera)
    }
}
