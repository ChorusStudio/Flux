package top.mythcraft.flux.scene.backend

import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.joml.Matrix4d
import top.mythcraft.flux.FluxModReference
import top.mythcraft.flux.scene.FluxColor

/**
 * 平面材质与单位几何：把 [FluxColor]（+透明度）烘焙成染色玻璃板 [ItemStack] 并提供矩形/三角形拼合用的单位变换与剪切矩阵。
 */
internal object FluxPlaneMaterial {
    /** 单位矩形：把 [net.minecraft.world.entity.Display.TextDisplay] 的空白文本背景映射成一块矩形。 */
    internal val UNIT_SQUARE: Matrix4d = Matrix4d()
        .translate(-0.1 + 0.5, -0.5 + 0.5, 0.0)
        .scale(8.0, 4.0, 1.0)

    /** 三块拼成三角形的单位片（配合 [getShearMatrix] 的剪切）。 */
    internal val UNIT_TRIANGLES: Array<Matrix4d> = Array(3) { Matrix4d() }.apply {
        this[0] = Matrix4d().scale(0.5).mul(Matrix4d(UNIT_SQUARE))

        val offset = 1.0
        val shearYX = getShearMatrix(0.0, 0.0, -offset, 0.0, 0.0, 0.0)
        this[1] = Matrix4d().scale(0.5)
            .translate(1.0, 0.0, 0.0)
            .mul(shearYX)
            .mul(Matrix4d(UNIT_SQUARE))

        val shearXY = getShearMatrix(-1.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        this[2] = Matrix4d().scale(0.5)
            .translate(0.0, 1.0, 0.0)
            .mul(shearXY)
            .mul(Matrix4d(UNIT_SQUARE))
    }

    private data class PaletteEntry(val r: Int, val g: Int, val b: Int, val name: String)

    private val PALETTE: List<PaletteEntry> = listOf(
        PaletteEntry(255, 255, 255, "white"),
        PaletteEntry(216, 127, 51, "orange"),
        PaletteEntry(178, 76, 216, "magenta"),
        PaletteEntry(102, 153, 216, "light_blue"),
        PaletteEntry(229, 229, 51, "yellow"),
        PaletteEntry(127, 204, 25, "lime"),
        PaletteEntry(242, 127, 165, "pink"),
        PaletteEntry(76, 76, 76, "gray"),
        PaletteEntry(153, 153, 153, "light_gray"),
        PaletteEntry(76, 127, 153, "cyan"),
        PaletteEntry(127, 63, 178, "purple"),
        PaletteEntry(51, 76, 178, "blue"),
        PaletteEntry(102, 76, 51, "brown"),
        PaletteEntry(102, 127, 51, "green"),
        PaletteEntry(153, 51, 51, "red"),
        PaletteEntry(25, 25, 25, "black"),
    )

    internal fun getBakedUIItemStack(color: FluxColor, opacity: Int = 255): ItemStack {
        val alphaLevel = ((opacity * color.a / 255.0).toInt() / 17).coerceIn(0, 15)
        if (alphaLevel == 0) return ItemStack.EMPTY
        var minDist = Int.MAX_VALUE
        var best = PALETTE[0]
        for (e in PALETTE) {
            val dr = color.r - e.r
            val dg = color.g - e.g
            val db = color.b - e.b
            val dist = dr * dr + dg * dg + db * db
            if (dist < minDist) {
                minDist = dist
                best = e
            }
        }
        return ItemStack(Items.STAINED_GLASS_PANE.white).apply {
            set(DataComponents.ITEM_MODEL, FluxModReference.idOf("ui_plane_${best.name}_$alphaLevel"))
        }
    }

    @Suppress("SameParameterValue")
    internal fun getShearMatrix(xy: Double, xz: Double, yx: Double, yz: Double, zx: Double, zy: Double): Matrix4d =
        Matrix4d(
            1.0, xy, xz, 0.0,
            yx, 1.0, yz, 0.0,
            zx, zy, 1.0, 0.0,
            0.0, 0.0, 0.0, 1.0,
        )
}
