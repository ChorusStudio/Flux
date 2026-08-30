package top.mythcraft.flux.camera

import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.math.sqrt

class CameraMathTest {

    @Test
    fun `lerpAngleDegrees wraps across plus minus 180`() {
        assertEquals(180f, CameraMath.lerpAngleDegrees(170f, -170f, 0.5f), 0.001f)
        assertEquals(-180f, CameraMath.lerpAngleDegrees(-170f, 170f, 0.5f), 0.001f)
    }

    @Test
    fun `lerpAngleDegrees interpolates on same side`() {
        assertEquals(0f, CameraMath.lerpAngleDegrees(0f, 90f, 0f), 0.001f)
        assertEquals(45f, CameraMath.lerpAngleDegrees(0f, 90f, 0.5f), 0.001f)
        assertEquals(90f, CameraMath.lerpAngleDegrees(0f, 90f, 1f), 0.001f)
    }

    @Test
    fun `lookRotation computes yaw and pitch`() {
        // 看向 +X（东）：MC 中 yaw 为 -90
        val east = CameraMath.lookRotation(Vec3.ZERO, Vec3(1.0, 0.0, 0.0))
        assertEquals(-90f, east.yaw, 0.001f)
        assertEquals(0f, east.pitch, 0.001f)

        // 看向 +Z（南）：yaw 为 0
        val south = CameraMath.lookRotation(Vec3.ZERO, Vec3(0.0, 0.0, 1.0))
        assertEquals(0f, south.yaw, 0.001f)
        assertEquals(0f, south.pitch, 0.001f)

        // 看向正上方：pitch 为 -90
        val up = CameraMath.lookRotation(Vec3.ZERO, Vec3(0.0, 1.0, 0.0))
        assertEquals(-90f, up.pitch, 0.001f)
    }

    @Test
    fun `velocityToward clamps to maxSpeed`() {
        val velocity = CameraMath.velocityToward(
            current = Vec3.ZERO,
            target = Vec3(10.0, 0.0, 0.0),
            maxSpeed = 5.0
        )
        assertEquals(5.0, velocity.length(), 0.0001)
        assertEquals(5.0, velocity.x, 0.0001)
    }

    @Test
    fun `velocityToward applies speed factor`() {
        val velocity = CameraMath.velocityToward(
            current = Vec3.ZERO,
            target = Vec3(10.0, 0.0, 0.0),
            maxSpeed = 16.0,
            factor = 0.15
        )
        assertEquals(1.5, velocity.x, 0.0001)
        assertEquals(sqrt(1.5 * 1.5), velocity.length(), 0.0001)
    }
}
