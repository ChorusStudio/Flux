package top.mythcraft.flux.border

/**
 * 按距离计算透明度：距离不超过 fadeStart 时为 [baseAlpha]，达到 fadeEnd 时为 0，
 * 其间按二次缓出插值，并保证不低于最小可见 alpha。
 */
object FluxFade {
    private const val MIN_VISIBLE_ALPHA = 26

    fun calculateFadeAlpha(distance: Double, baseAlpha: Int, fadeStart: Double, fadeEnd: Double): Int {
        if (distance <= fadeStart) return baseAlpha
        if (distance >= fadeEnd) return 0
        val t = (distance - fadeStart) / (fadeEnd - fadeStart)
        val invT = 1.0 - t
        val ratio = invT * invT
        val alpha = MIN_VISIBLE_ALPHA + (baseAlpha - MIN_VISIBLE_ALPHA) * ratio
        return alpha.toInt().coerceIn(0, baseAlpha)
    }
}
