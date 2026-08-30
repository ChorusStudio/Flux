package top.mythcraft.flux.border

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3
import top.mythcraft.flux.scene.FluxCanvas
import top.mythcraft.flux.scene.FluxColor
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt

/** 水平网格绘制：以玩家为中心、随距离淡出的一块水平网格（天花板/地板）*/
object FluxGrid {
    private const val SIZE = 24f
    private const val FADE_START = 24.0
    private const val FADE_END = 48.0

    fun draw(
        canvas: FluxCanvas,
        player: ServerPlayer,
        anchor: Vec3,
        yLevel: Double,
        color: FluxColor,
        idPrefix: String,
        centerX: Double = 0.0,
        centerZ: Double = 0.0,
        clipRadius: Double? = null,
        emit: FluxCanvas.(size: Float, color: FluxColor) -> Unit,
    ) {
        // 高度差超过淡出上限时整块网格不可见，跳过后续距离计算
        if (abs(player.y - yLevel) >= FADE_END) return

        val gridRange = ceil(FADE_END / SIZE).toInt()
        val renderSize = SIZE - 6f

        val playerGridX = floor(player.x / SIZE).toInt()
        val playerGridZ = floor(player.z / SIZE).toInt()

        val radiusSq = clipRadius?.let { it * it }

        for (gx in (playerGridX - gridRange)..(playerGridX + gridRange)) {
            for (gz in (playerGridZ - gridRange)..(playerGridZ + gridRange)) {
                val worldX = gx * SIZE + (centerX % SIZE)
                val worldZ = gz * SIZE + (centerZ % SIZE)

                if (radiusSq != null) {
                    val dx = worldX - centerX
                    val dz = worldZ - centerZ
                    if (dx * dx + dz * dz >= radiusSq) continue
                }

                val distance = sqrt(player.distanceToSqr(worldX, yLevel, worldZ))
                val alpha = FluxFade.calculateFadeAlpha(distance, color.a, FADE_START, FADE_END)
                if (alpha <= 0) continue

                val dynamicColor = FluxColor(alpha, color.r, color.g, color.b)

                val relX = worldX - anchor.x
                val relY = yLevel - anchor.y
                val relZ = worldZ - anchor.z

                canvas.group("${idPrefix}_${gx}_$gz") {
                    transform {
                        translate(relX.toFloat(), relY.toFloat(), relZ.toFloat())
                        rotateX(90f)
                        translate(-renderSize / 2f, -renderSize / 2f, 0f)
                    }
                    emit(renderSize, dynamicColor)
                }
            }
        }
    }
}
