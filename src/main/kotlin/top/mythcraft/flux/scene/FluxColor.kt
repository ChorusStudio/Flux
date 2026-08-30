package top.mythcraft.flux.scene

import net.minecraft.util.ARGB

/**
 * ARGB 颜色，构造参数顺序为 [a]、[r]、[g]、[b]（各分量取值 `0..255`）。
 *
 * @property a 透明度（alpha），`255` 为完全不透明、`0` 为完全透明
 * @property r 红色分量
 * @property g 绿色分量
 * @property b 蓝色分量
 */
data class FluxColor(
    val a: Int,
    val r: Int,
    val g: Int,
    val b: Int,
) {
    init {
        require(a in 0..255)
        require(r in 0..255)
        require(g in 0..255)
        require(b in 0..255)
    }

    fun toArgb(): Int = ARGB.color(a, r, g, b)
}
