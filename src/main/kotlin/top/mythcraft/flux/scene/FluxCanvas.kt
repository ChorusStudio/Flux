package top.mythcraft.flux.scene

import net.minecraft.network.chat.FontDescription
import net.minecraft.resources.Identifier
import org.joml.Matrix4d
import org.joml.Quaterniond
import org.joml.Vector3d
import java.util.*
import kotlin.math.tan

/**
 * 一帧的声明式画布：通过 [group]/[transform] 构建变换树，用 [text]/[rect]/[topLeftRect]/
 * [itemDisplay]/[triangle] 收集节点状态；过程为纯计算，不修改实体。
 *
 * 坐标系：`+x` 向右、`+y` 向上、`+z` 面向观察者；
 * `text` 按中心放置，`rect`/`itemDisplay` 以 `(x, y)` 为原点向 `+x/+y` 生长。
 * 每个节点被收集为一份不可变的 [FluxNode.State]，由 FluxDisplayPool 与上一帧 diff 后只把变化应用到显示实体。
 */
class FluxCanvas {
    companion object {
        private val UNIT_Y = Vector3d(0.0, 1.0, 0.0)
        private val UNIT_Z = Vector3d(0.0, 0.0, 1.0)

        /** 未在 [font] 中指定时，文本节点使用的默认字体。 */
        val DEFAULT_FONT: FontDescription.Resource =
            FontDescription.Resource(Identifier.withDefaultNamespace("default"))

        /**
         * 世界对齐的标准屏幕朝向（法线面向 `-Z`、上方为 `+Y`）
         * 共享常量，勿原地修改。
         */
        val STANDARD_ORIENTATION: Matrix4d = screenOrientation(UNIT_Y, UNIT_Z)

        /**
         * 由屏幕两轴构造朝向矩阵（等价旧 `lookAlong(-zAxis, yAxis).conjugate()`）。
         *
         * 正交基的第三个轴可由 `yAxis × zAxis` 推导，故无需传入。
         *
         * @param yAxis 屏幕的「上」方向
         * @param zAxis 屏幕的「法线」方向（朝向观察者）
         */
        fun screenOrientation(yAxis: Vector3d, zAxis: Vector3d): Matrix4d =
            Matrix4d().rotate(Quaterniond().lookAlong(Vector3d(zAxis).mul(-1.0), yAxis).conjugate())
    }

    /** 本帧收集出的节点状态表，key 为结构化节点 id（如 `wall/seg_3`） */
    internal val nodes = LinkedHashMap<String, FluxNode.State>()

    private val idStack = ArrayDeque<String>()
    private val matrixStack = ArrayDeque<Matrix4d>()
    private val fontStack = ArrayDeque<FontDescription.Resource>()

    private var microZ = 0f
    private var zStep = 0f

    internal fun begin(base: Matrix4d) {
        nodes.clear()
        idStack.clear()
        matrixStack.clear()
        fontStack.clear()
        matrixStack.addFirst(Matrix4d(base))
        fontStack.addFirst(DEFAULT_FONT)
        microZ = 0f
        zStep = 0f
    }

    /**
     * 设置当前作用域内后续 [text] 的默认字体。
     * 在 [group] 内调用时仅影响该组及其子组，直到组结束。
     */
    fun font(font: FontDescription.Resource) {
        fontStack.removeFirst()
        fontStack.addFirst(font)
    }

    /** 在 [block] 内使用指定字体，结束后恢复外层默认字体。 */
    fun font(font: FontDescription.Resource, block: FluxCanvas.() -> Unit) {
        fontStack.addFirst(font)
        block()
        fontStack.removeFirst()
    }

    private fun currentFont(): FontDescription.Resource = fontStack.first()

    /** 设置后续节点的 z 分层步长：每声明一个节点 z 自动累加一次 [z]，用于避免共面内容 Z-Fighting */
    fun zStep(z: Float) {
        zStep = z
    }

    private fun fullId(id: String): String {
        val prefix = idStack.joinToString("/")
        return if (prefix.isEmpty()) id else "$prefix/$id"
    }

    private fun current(): Matrix4d = matrixStack.first()

    private fun nextZ(): Float {
        val z = microZ
        microZ += zStep
        return z
    }

    private fun Matrix4d.composed(local: Matrix4d): Matrix4d = Matrix4d(this).mul(local)

    /**
     * 声明一个变换组：`block` 内节点的 id 自动带上 `id` 前缀（`/` 分隔），内部 [transform] 只影响本组
     */
    fun group(id: String, block: FluxCanvas.() -> Unit) {
        idStack.addLast(id)
        matrixStack.addFirst(Matrix4d(current()))
        fontStack.addFirst(currentFont())
        block()
        fontStack.removeFirst()
        matrixStack.removeFirst()
        idStack.removeLast()
    }

    /**
     * 修改当前组（或根）的变换矩阵
     * @see TransformScope
     */
    fun transform(block: TransformScope.() -> Unit) {
        TransformScope(current()).apply(block)
    }

    /** 声明一个文本节点
     * @see FluxNode.Text
     */
    fun text(id: String, text: String, init: FluxNode.Text.() -> Unit = {}) {
        val n = FluxNode.Text().apply(init)
        val local = Matrix4d()
            .translate(n.x, n.y, n.z + nextZ())
            .scale(n.scale.toDouble(), n.scale.toDouble(), n.scale.toDouble())
        val full = fullId(id)
        nodes[full] = FluxNode.TextState(
            id = full,
            text = text,
            font = n.font ?: currentFont(),
            opacity = n.opacity.coerceIn(0, 255),
            align = n.align,
            transform = current().composed(local),
            billboard = n.billboard,
            seeThrough = n.seeThrough,
            interpolationTicks = n.interpolationTicks,
        )
    }

    /**
     * 声明一个矩形色块
     * 以 `(x, y)` 为原点向 `+x/+y` 生长
     */
    fun rect(id: String, width: Float, height: Float, color: FluxColor, init: FluxNode.Rect.() -> Unit = {}) {
        val n = FluxNode.Rect().apply(init)
        val local = Matrix4d()
            .translate(n.x, n.y, n.z + nextZ())
            .scale(width.toDouble(), height.toDouble(), 1.0)
        val full = fullId(id)
        nodes[full] = FluxNode.RectState(
            id = full,
            color = color.toArgb(),
            transform = current().composed(local),
            billboard = n.billboard,
            seeThrough = n.seeThrough,
            interpolationTicks = n.interpolationTicks,
        )
    }

    /**
     * 声明一个绝对定位矩形：`(x, y)` 为左上角、向 `-y`（向下）生长，
     * 其余语义与 [rect] 相同。
     */
    fun topLeftRect(
        id: String,
        x: Double,
        y: Double,
        width: Float,
        height: Float,
        color: FluxColor,
        init: FluxNode.Rect.() -> Unit = {}
    ) {
        val n = FluxNode.Rect().apply(init)
        val local = Matrix4d()
            .translate(x, y - height, n.z + nextZ())
            .scale(width.toDouble(), height.toDouble(), 1.0)
        val full = fullId(id)
        nodes[full] = FluxNode.RectState(
            id = full,
            color = color.toArgb(),
            transform = current().composed(local),
            billboard = n.billboard,
            seeThrough = n.seeThrough,
            interpolationTicks = n.interpolationTicks,
        )
    }

    /**
     * 声明一个 item 展示平面，
     * 以 `(x, y)` 为原点向 `+x/+y` 生长。
     * @see FluxNode.ItemDisplay
     */
    fun itemDisplay(
        id: String,
        width: Float,
        height: Float,
        color: FluxColor,
        init: FluxNode.ItemDisplay.() -> Unit = {}
    ) {
        val n = FluxNode.ItemDisplay().apply(init)
        val local = Matrix4d()
            .translate(n.x, n.y, n.z + nextZ())
            .scale(width.toDouble(), height.toDouble(), 1.0)
        val full = fullId(id)
        nodes[full] = FluxNode.ItemState(
            id = full,
            color = color,
            opacity = n.opacity.coerceIn(0, 255),
            transform = current().composed(local),
            billboard = n.billboard,
            seeThrough = n.seeThrough,
            interpolationTicks = n.interpolationTicks,
        )
    }

    /**
     * 声明一个三角形
     * @see FluxNode.Triangle
     */
    fun triangle(
        id: String,
        p1: Vector3d,
        p2: Vector3d,
        p3: Vector3d,
        color: FluxColor,
        init: FluxNode.Triangle.() -> Unit = {},
    ) {
        val n = FluxNode.Triangle().apply(init)
        val full = fullId(id)
        nodes[full] = FluxNode.TriangleState(
            id = full,
            p1 = Vector3d(p1),
            p2 = Vector3d(p2),
            p3 = Vector3d(p3),
            color = color.toArgb(),
            transform = Matrix4d(current()),
            billboard = n.billboard,
            seeThrough = n.seeThrough,
            interpolationTicks = n.interpolationTicks,
        )
    }

    /** 变换作用域 */
    class TransformScope(private val matrix: Matrix4d) {
        fun translate(x: Float, y: Float, z: Float) {
            matrix.translate(x.toDouble(), y.toDouble(), z.toDouble())
        }

        fun rotateX(degrees: Float) {
            matrix.rotateX(Math.toRadians(degrees.toDouble()))
        }

        fun rotateY(degrees: Float) {
            matrix.rotateY(Math.toRadians(degrees.toDouble()))
        }

        fun rotateZ(degrees: Float) {
            matrix.rotateZ(Math.toRadians(degrees.toDouble()))
        }

        fun scale(x: Float, y: Float, z: Float) {
            matrix.scale(x.toDouble(), y.toDouble(), z.toDouble())
        }

        @Suppress("unused")
        fun skew(angleX: Float, angleY: Float) {
            val tanX = tan(Math.toRadians(angleX.toDouble()))
            val tanY = tan(Math.toRadians(angleY.toDouble()))
            val shear = Matrix4d(
                1.0, tanY, 0.0, 0.0,
                tanX, 1.0, 0.0, 0.0,
                0.0, 0.0, 1.0, 0.0,
                0.0, 0.0, 0.0, 1.0,
            )
            matrix.mul(shear)
        }
    }
}
