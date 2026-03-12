package ru.benos_codex.more_copycats.client.model.block

import net.minecraft.client.renderer.block.model.BakedQuad
import net.minecraft.core.Direction

object CopycatQuadCulling {
    private const val EPS = 1.0e-4f

    fun isOnOuterBoundary(quad: BakedQuad, direction: Direction): Boolean {
        val bounds = quadBounds(quad)
        return when (direction) {
            Direction.WEST -> isNear(bounds.minX, 0f)
            Direction.EAST -> isNear(bounds.maxX, 1f)
            Direction.NORTH -> isNear(bounds.minZ, 0f)
            Direction.SOUTH -> isNear(bounds.maxZ, 1f)
            Direction.DOWN -> isNear(bounds.minY, 0f)
            Direction.UP -> isNear(bounds.maxY, 1f)
        }
    }

    private data class QuadBounds(
        val minX: Float,
        val maxX: Float,
        val minY: Float,
        val maxY: Float,
        val minZ: Float,
        val maxZ: Float
    )

    private fun quadBounds(quad: BakedQuad): QuadBounds {
        val x0 = quad.position0().x()
        val x1 = quad.position1().x()
        val x2 = quad.position2().x()
        val x3 = quad.position3().x()
        val y0 = quad.position0().y()
        val y1 = quad.position1().y()
        val y2 = quad.position2().y()
        val y3 = quad.position3().y()
        val z0 = quad.position0().z()
        val z1 = quad.position1().z()
        val z2 = quad.position2().z()
        val z3 = quad.position3().z()
        return QuadBounds(
            minX = minOf(x0, x1, x2, x3),
            maxX = maxOf(x0, x1, x2, x3),
            minY = minOf(y0, y1, y2, y3),
            maxY = maxOf(y0, y1, y2, y3),
            minZ = minOf(z0, z1, z2, z3),
            maxZ = maxOf(z0, z1, z2, z3)
        )
    }

    private fun isNear(value: Float, target: Float): Boolean = kotlin.math.abs(value - target) <= EPS
}

