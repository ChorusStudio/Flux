package top.mythcraft.flux.camera

/**
 * 缓动函数：输入 [0,1] 的线性进度，输出缓动后的进度。
 */
object Easing {
    val LINEAR: (Double) -> Double = { it }

    val OUT_QUINT: (Double) -> Double = { t ->
        val u = 1.0 - t
        1.0 - u * u * u * u * u
    }

    val IN_OUT_QUINT: (Double) -> Double = { t ->
        if (t < 0.5) {
            val v = t * 2.0
            v * v * v * v * v / 2.0
        } else {
            val u = -2.0 * t + 2.0
            1.0 - u * u * u * u * u / 2.0
        }
    }
}
