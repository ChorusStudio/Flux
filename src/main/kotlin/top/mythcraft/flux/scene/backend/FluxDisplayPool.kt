package top.mythcraft.flux.scene.backend

import com.mojang.math.Transformation
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FontDescription
import net.minecraft.network.chat.Style
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.Brightness
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import org.joml.*
import top.mythcraft.flux.FluxModReference
import top.mythcraft.flux.scene.FluxColor
import top.mythcraft.flux.scene.FluxNode
import java.util.UUID
import kotlin.collections.ArrayDeque
import kotlin.experimental.or
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sign

/**
 * 每个画布一个有界显示实体池。渲染期只做 diff 规划（[diff]），
 * 所有实体变更延迟到 [FluxCommandBuffer.flush] 时执行，不在 vanilla 集合迭代中修改实体。
 */
class FluxDisplayPool(
    private val level: ServerLevel,
    private val viewerUuid: UUID,
    private var anchor: Vec3,
) {
    companion object {
        private const val MAX_POOL_SIZE = 128
        private const val TRANSPARENT_BG = 0x00000000
        private const val ANGLE_45_RAD: Float = (PI / 4.0).toFloat()
        private const val ANGLE_90_RAD: Float = (PI / 2.0).toFloat()
        private const val VIEW_RANGE = 2.5f
        private const val SNAP_TRANSLATION_SQ = 1024.0f // 32^2

        private val HUD_BRIGHTNESS = Brightness(15, 15) // HUD 文字全亮
        private val ITEM_PLANE_BRIGHTNESS = Brightness(7, 7) // 世界里的半透明平面用较低亮度

        private val BLANK: Component = Component.literal(" ")
        private val FONT: FontDescription.Resource get() = FontDescription.Resource(FluxModReference.idOf("default"))

        private val HIDDEN: Matrix4d = Matrix4d().scale(0.0, 0.0, 0.0)
    }

    private class NodeRuntime(
        val kind: FluxNode.Kind,
        var displays: MutableList<Display>,
        var lastState: FluxNode.State?,
    ) {
        val count: Int = if (kind == FluxNode.Kind.TRIANGLE) 3 else 1

        fun matches(state: FluxNode.State): Boolean =
            this.kind == state.kind && this.displays.size == count && this.displays.none(Display::isRemoved)
    }

    private val active = HashMap<String, NodeRuntime>()
    private val freeText = ArrayDeque<Display.TextDisplay>()
    private val freeItem = ArrayDeque<Display.ItemDisplay>()
    private val transformCache = HashMap<Display, Transformation>()

    fun diff(desired: Map<String, FluxNode.State>, buffer: FluxCommandBuffer) {
        val gone = HashSet(active.keys)
        gone.removeAll(desired.keys)
        for (id in gone) {
            val runtime = active.remove(id) ?: continue
            buffer.defer { recycle(runtime) }
        }

        for ((id, state) in desired) {
            val runtime = active[id]
            if (runtime != null && runtime.matches(state)) {
                if (runtime.lastState != state) {
                    runtime.lastState = state
                    buffer.defer { applyFull(runtime, state) }
                }
            } else {
                runtime?.let { old -> buffer.defer { recycle(old) } }
                val created = NodeRuntime(state.kind, mutableListOf(), state)
                active[id] = created
                buffer.defer { spawnAndApply(created, state) }
            }
        }
    }

    fun isSameLevel(level: ServerLevel): Boolean = this.level === level

    /** 把池内实体传送到新锚点，不销毁重建。锚点立即更新，实体位移延迟到 flush。 */
    fun reanchor(newAnchor: Vec3, buffer: FluxCommandBuffer) {
        if (anchor == newAnchor) return
        anchor = newAnchor
        buffer.defer {
            for (runtime in active.values) {
                runtime.displays.forEach(::position)
            }
            freeText.forEach(::position)
            freeItem.forEach(::position)
        }
    }

    fun destroy(buffer: FluxCommandBuffer) {
        for (runtime in active.values) {
            runtime.displays.forEach { display -> buffer.defer { discard(display) } }
        }
        active.clear()
        freeText.forEach { textDisplay -> buffer.defer { discard(textDisplay) } }
        freeItem.forEach { itemDisplay -> buffer.defer { discard(itemDisplay) } }
        freeText.clear()
        freeItem.clear()
    }

    private fun spawnAndApply(runtime: NodeRuntime, state: FluxNode.State) {
        val displays = mutableListOf<Display>()
        val fresh = mutableListOf<Display>()
        when (runtime.kind) {
            FluxNode.Kind.TEXT, FluxNode.Kind.RECT, FluxNode.Kind.TRIANGLE -> repeat(runtime.count) {
                acquire(freeText, ::createText, displays, fresh)
            }

            FluxNode.Kind.ITEM_DISPLAY -> acquire(freeItem, ::createItem, displays, fresh)
        }
        runtime.displays = displays
        if (displays.size == runtime.count && displays.none(Display::isRemoved)) {
            applyFull(runtime, state)
        }
        for (display in fresh) {
            FluxVisibility.register(level.dimension(), display.id, viewerUuid)
            level.addFreshEntity(display)
        }
    }

    private fun <T : Display> acquire(
        free: ArrayDeque<T>,
        create: () -> T,
        displays: MutableList<Display>,
        fresh: MutableList<Display>,
    ) {
        val reused = takeUsable(free)
        if (reused != null) {
            position(reused)
            FluxVisibility.register(level.dimension(), reused.id, viewerUuid)
            displays.add(reused)
        } else {
            val display = create()
            fresh.add(display)
            displays.add(display)
        }
    }

    private fun createText(): Display.TextDisplay {
        val display = Display.TextDisplay(EntityTypes.TEXT_DISPLAY, level).apply {
            brightnessOverride = HUD_BRIGHTNESS
            viewRange = VIEW_RANGE
            posRotInterpolationDuration = 0
        }
        position(display)
        return display
    }

    private fun createItem(): Display.ItemDisplay {
        val display = Display.ItemDisplay(EntityTypes.ITEM_DISPLAY, level).apply {
            brightnessOverride = ITEM_PLANE_BRIGHTNESS
            itemTransform = ItemDisplayContext.FIXED
            viewRange = VIEW_RANGE
            posRotInterpolationDuration = 0
        }
        position(display)
        return display
    }

    private fun <T : Display> takeUsable(free: ArrayDeque<T>): T? {
        while (free.isNotEmpty()) {
            val display = free.removeLast()
            if (!display.isRemoved) return display
            forget(display)
        }
        return null
    }

    private fun forget(display: Display) {
        FluxVisibility.unregister(level.dimension(), display.id)
        transformCache.remove(display)
    }

    private fun position(display: Display) {
        if (display.isRemoved) return
        display.setPos(anchor.x, anchor.y, anchor.z)
    }

    private fun recycle(runtime: NodeRuntime) {
        for (display in runtime.displays) {
            when (display) {
                is Display.TextDisplay -> recycle(display, freeText) { flags = 0 }
                is Display.ItemDisplay -> recycle(display, freeItem)
            }
        }
        runtime.displays.clear()
    }

    private fun <T : Display> recycle(display: T, free: ArrayDeque<T>, extra: T.() -> Unit = {}) {
        if (display.isRemoved) {
            forget(display)
            return
        }
        if (free.size < MAX_POOL_SIZE) {
            updateTransform(display, HIDDEN, 0)
            display.apply(extra)
            transformCache.remove(display)
            free.addLast(display)
        } else {
            discard(display)
        }
    }

    private fun discard(display: Display) {
        forget(display)
        if (!display.isRemoved) display.discard()
    }

    private fun applyFull(runtime: NodeRuntime, state: FluxNode.State) {
        if (runtime.displays.size != runtime.count || runtime.displays.any(Display::isRemoved)) return
        when (state) {
            is FluxNode.TextState -> applyText(runtime.displays[0] as Display.TextDisplay, state)
            is FluxNode.RectState -> applyRect(runtime.displays[0] as Display.TextDisplay, state)
            is FluxNode.ItemState -> applyItemRect(runtime.displays[0] as Display.ItemDisplay, state)
            is FluxNode.TriangleState -> applyTriangle(runtime.displays, state)
        }
    }

    private fun applyText(display: Display.TextDisplay, state: FluxNode.TextState) {
        display.text = Component.literal(state.text).withStyle(Style.EMPTY.withFont(FONT))
        var flags = when (state.align) {
            FluxNode.Align.LEFT -> Display.TextDisplay.FLAG_ALIGN_LEFT
            FluxNode.Align.RIGHT -> Display.TextDisplay.FLAG_ALIGN_RIGHT
            else -> 0
        }
        if (state.seeThrough) {
            flags = flags or Display.TextDisplay.FLAG_SEE_THROUGH
        }
        display.flags = flags
        display.textOpacity = state.opacity.toByte()
        display.backgroundColor = TRANSPARENT_BG
        display.billboardConstraints = state.billboard
        display.brightnessOverride = HUD_BRIGHTNESS
        updateTransform(display, state.transform, state.interpolationTicks)
    }

    private fun applyRect(display: Display.TextDisplay, state: FluxNode.RectState) {
        if (display.text != BLANK) {
            display.text = BLANK
        }
        display.flags = if (state.seeThrough) Display.TextDisplay.FLAG_SEE_THROUGH else 0
        display.textOpacity = 255.toByte()
        display.backgroundColor = state.color
        display.billboardConstraints = state.billboard
        display.brightnessOverride = HUD_BRIGHTNESS
        updateTransform(display, Matrix4d(state.transform).mul(FluxPlaneMaterial.UNIT_SQUARE), state.interpolationTicks)
    }

    private fun applyItemRect(display: Display.ItemDisplay, state: FluxNode.ItemState) {
        val alpha = (state.color.a * state.opacity) / 255
        val stack = FluxPlaneMaterial.getBakedUIItemStack(FluxColor(alpha, state.color.r, state.color.g, state.color.b))
        if (!ItemStack.matches(display.itemStack, stack)) {
            display.itemStack = stack
        }
        display.billboardConstraints = state.billboard
        display.brightnessOverride = ITEM_PLANE_BRIGHTNESS
        val finalMat = Matrix4d(state.transform).translate(0.5, 0.5, 0.0)
        updateTransform(display, finalMat, state.interpolationTicks)
    }

    private fun applyTriangle(displays: List<Display>, state: FluxNode.TriangleState) {
        val p2 = Vector3d(state.p2).sub(state.p1)
        val p3 = Vector3d(state.p3).sub(state.p1)

        val zAxis = Vector3d(p2).cross(p3).normalize()
        val xAxis = Vector3d(p2).normalize()
        val yAxis = Vector3d(zAxis).cross(xAxis).normalize()

        val width = p2.length().toFloat()
        val height = Vector3d(p3).dot(yAxis).toFloat()
        val p3Width = Vector3d(p3).dot(xAxis).toFloat()

        val rotation = Quaternionf()
            .lookAlong(Vector3f(Vector3d(zAxis).mul(-1.0)), Vector3f(yAxis))
            .conjugate()
        val shear = if (width == 0f) 0f else p3Width / width
        val shearMat = FluxPlaneMaterial.getShearMatrix(0.0, 0.0, shear.toDouble(), 0.0, 0.0, 0.0)

        val local = Matrix4d()
            .translate(state.p1)
            .rotate(rotation)
            .scale(width.toDouble(), height.toDouble(), 1.0)
            .mul(shearMat)
        val final = Matrix4d(state.transform).mul(local)

        for (i in 0 until 3) {
            val display = displays[i] as Display.TextDisplay
            if (display.text != BLANK) {
                display.text = BLANK
            }
            display.flags = if (state.seeThrough) Display.TextDisplay.FLAG_SEE_THROUGH else 0
            display.textOpacity = 255.toByte()
            display.backgroundColor = state.color
            display.billboardConstraints = state.billboard
            display.brightnessOverride = HUD_BRIGHTNESS
            val zOffset = (i - 1) * 0.001
            val piece = Matrix4d(final).translate(0.0, 0.0, zOffset).mul(FluxPlaneMaterial.UNIT_TRIANGLES[i])
            updateTransform(display, piece, state.interpolationTicks, useZHack = true)
        }
    }

    private fun updateTransform(display: Display, target: Matrix4d, interpTicks: Int, useZHack: Boolean = false) {
        if (display.isRemoved) return
        var newT = Transformation(Matrix4f(target))
        val oldT = transformCache[display]
        if (useZHack && oldT != null) {
            val oldRight = Quaternionf(oldT.rightRotation())
            val newRight = Quaternionf(newT.rightRotation())
            val change = Quaternionf(oldRight).difference(newRight)
            val euler = change.getEulerAnglesXYZ(Vector3f())
            if (abs(euler.z) >= ANGLE_45_RAD) {
                val rot = ANGLE_90_RAD * sign(euler.z)
                val left = Quaternionf(newT.leftRotation()).apply { rotateZ(-rot) }
                val scale = Vector3f(newT.scale()).apply { set(y, x, z) }
                val right = Quaternionf(newT.rightRotation()).apply { rotateZ(rot) }
                newT = Transformation(newT.translation(), left, scale, right)
            }
        }
        if (oldT == null || newT != oldT) {
            val ticks =
                if (oldT == null || oldT.translation().distanceSquared(newT.translation()) > SNAP_TRANSLATION_SQ) {
                    0
                } else {
                    interpTicks
                }
            display.transformationInterpolationDuration = ticks
            display.setTransformation(newT)
            display.transformationInterpolationDelay = 0
            transformCache[display] = newT
        }
    }
}
