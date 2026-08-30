package top.mythcraft.flux.scene

import net.minecraft.world.entity.Display
import org.joml.Matrix4d
import org.joml.Vector3d

/**
 * 节点族：节点类别、文本对齐、声明配置与渲染状态均嵌套在此命名空间下，
 * 对应 MC `Display`（`Display.TextDisplay` / `Display.TextDisplay.Align` /
 * `Display.TextDisplay.TextRenderState`）。
 */
object FluxNode {
    /** 文本的水平对齐方式（对应 `Display.TextDisplay.Align`）。 */
    enum class Align {
        /** 左对齐 */
        LEFT,

        /** 水平居中 */
        CENTER,

        /** 右对齐 */
        RIGHT,
    }

    /** 节点类别，决定底层用哪种显示实体承载 */
    enum class Kind {
        /** 使用 [Display.TextDisplay] */
        TEXT,

        /** 使用 [Display.TextDisplay] */
        RECT,

        /** 使用 [Display.ItemDisplay] */
        ITEM_DISPLAY,

        /** 使用 3 块 [Display.TextDisplay] */
        TRIANGLE,
    }

    /** 各类节点共有的声明配置 */
    interface Props {
        /** 固定朝向 */
        var billboard: Display.BillboardConstraints

        /** 是否穿透墙体可见 */
        var seeThrough: Boolean

        /** 变换插值时长（tick 数，`0` 为瞬间到位） */
        var interpolationTicks: Int
    }

    /** [FluxCanvas.text] 的节点配置 */
    class Text : Props {
        var x = 0.0
        var y = 0.0
        var z = 0.0
        var scale = 0.45f
        var opacity = 255
        var align = Align.CENTER
        override var billboard = Display.BillboardConstraints.FIXED
        override var seeThrough = false
        override var interpolationTicks = 0
    }

    /** [FluxCanvas.rect] / [FluxCanvas.topLeftRect] 的节点配置 */
    class Rect : Props {
        var x = 0.0
        var y = 0.0
        var z = 0.0
        override var billboard = Display.BillboardConstraints.FIXED
        override var seeThrough = false
        override var interpolationTicks = 0
    }

    /** [FluxCanvas.itemDisplay] 的节点配置 */
    class ItemDisplay : Props {
        var x = 0.0
        var y = 0.0
        var z = 0.0
        var opacity = 255
        override var billboard = Display.BillboardConstraints.FIXED
        override var seeThrough = false
        override var interpolationTicks = 0
    }

    /** [FluxCanvas.triangle] 的节点配置 */
    class Triangle : Props {
        override var billboard = Display.BillboardConstraints.FIXED
        override var seeThrough = false
        override var interpolationTicks = 0
    }

    /** 一帧收集到的节点状态，作为 diff 的基本单元（对应 [Display.RenderState]） */
    sealed interface State {
        val id: String
        val kind: Kind
        val transform: Matrix4d
        val billboard: Display.BillboardConstraints
        val seeThrough: Boolean
        val interpolationTicks: Int
    }

    data class TextState(
        override val id: String,
        val text: String,
        val opacity: Int,
        val align: Align,
        override val transform: Matrix4d,
        override val billboard: Display.BillboardConstraints,
        override val seeThrough: Boolean,
        override val interpolationTicks: Int,
    ) : State {
        override val kind: Kind = Kind.TEXT
    }

    data class RectState(
        override val id: String,
        val color: Int,
        override val transform: Matrix4d,
        override val billboard: Display.BillboardConstraints,
        override val seeThrough: Boolean,
        override val interpolationTicks: Int,
    ) : State {
        override val kind: Kind = Kind.RECT
    }

    data class ItemState(
        override val id: String,
        val color: FluxColor,
        val opacity: Int,
        override val transform: Matrix4d,
        override val billboard: Display.BillboardConstraints,
        override val seeThrough: Boolean,
        override val interpolationTicks: Int,
    ) : State {
        override val kind: Kind = Kind.ITEM_DISPLAY
    }

    data class TriangleState(
        override val id: String,
        val p1: Vector3d,
        val p2: Vector3d,
        val p3: Vector3d,
        val color: Int,
        override val transform: Matrix4d,
        override val billboard: Display.BillboardConstraints,
        override val seeThrough: Boolean,
        override val interpolationTicks: Int,
    ) : State {
        override val kind: Kind = Kind.TRIANGLE
    }
}
