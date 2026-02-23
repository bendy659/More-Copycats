package ru.benos_codex.more_copycats.block

import net.minecraft.core.Direction
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

enum class UvButton {
    U_PLUS,
    U_MINUS,
    V_PLUS,
    V_MINUS
}

object CopycatUvButtons {
    private const val FACE_EPSILON = 0.001

    fun getButtonBoxes(partBox: AABB, face: Direction): Map<UvButton, AABB> {
        val (uDir, vDir, nDir) = faceAxes(face)

        val size = partBox.maxX - partBox.minX
        val buttonSize = size * 0.16
        val half = size * 0.5
        val offset = half - buttonSize * 0.5

        val centerX = when (face) {
            Direction.EAST -> partBox.maxX
            Direction.WEST -> partBox.minX
            else -> (partBox.minX + partBox.maxX) * 0.5
        }
        val centerY = when (face) {
            Direction.UP -> partBox.maxY
            Direction.DOWN -> partBox.minY
            else -> (partBox.minY + partBox.maxY) * 0.5
        }
        val centerZ = when (face) {
            Direction.SOUTH -> partBox.maxZ
            Direction.NORTH -> partBox.minZ
            else -> (partBox.minZ + partBox.maxZ) * 0.5
        }
        val center = Vec3(centerX, centerY, centerZ)

        val outward = nDir.scale(buttonSize * 0.9 + FACE_EPSILON)

        val uPlus = center.add(uDir.scale(offset)).add(outward)
        val uMinus = center.add(uDir.scale(-offset)).add(outward)
        val vPlus = center.add(vDir.scale(offset)).add(outward)
        val vMinus = center.add(vDir.scale(-offset)).add(outward)

        return mapOf(
            UvButton.U_PLUS to cubeFromCenter(uPlus, buttonSize),
            UvButton.U_MINUS to cubeFromCenter(uMinus, buttonSize),
            UvButton.V_PLUS to cubeFromCenter(vPlus, buttonSize),
            UvButton.V_MINUS to cubeFromCenter(vMinus, buttonSize)
        )
    }

    fun getButtonAt(hit: Vec3, partBox: AABB, face: Direction): UvButton? {
        val (uDir, vDir, _) = faceAxes(face)

        val size = partBox.maxX - partBox.minX
        val buttonSize = size * 0.16
        val half = size * 0.5
        val offset = half - buttonSize * 0.5

        val centerX = when (face) {
            Direction.EAST -> partBox.maxX
            Direction.WEST -> partBox.minX
            else -> (partBox.minX + partBox.maxX) * 0.5
        }
        val centerY = when (face) {
            Direction.UP -> partBox.maxY
            Direction.DOWN -> partBox.minY
            else -> (partBox.minY + partBox.maxY) * 0.5
        }
        val centerZ = when (face) {
            Direction.SOUTH -> partBox.maxZ
            Direction.NORTH -> partBox.minZ
            else -> (partBox.minZ + partBox.maxZ) * 0.5
        }
        val center = Vec3(centerX, centerY, centerZ)

        val rel = hit.subtract(center)
        val u = rel.dot(uDir)
        val v = rel.dot(vDir)
        val halfBtn = buttonSize * 0.5

        fun inside(du: Double, dv: Double): Boolean =
            kotlin.math.abs(u - du) <= halfBtn && kotlin.math.abs(v - dv) <= halfBtn

        return when {
            inside(offset, 0.0) -> UvButton.U_PLUS
            inside(-offset, 0.0) -> UvButton.U_MINUS
            inside(0.0, offset) -> UvButton.V_PLUS
            inside(0.0, -offset) -> UvButton.V_MINUS
            else -> null
        }
    }

    private fun cubeFromCenter(center: Vec3, size: Double): AABB {
        val half = size * 0.5
        return AABB(
            center.x - half, center.y - half, center.z - half,
            center.x + half, center.y + half, center.z + half
        )
    }

    private data class Axes(val u: Vec3, val v: Vec3, val n: Vec3)

    private fun faceAxes(face: Direction): Axes {
        return when (face) {
            Direction.NORTH -> Axes(Vec3(1.0, 0.0, 0.0), Vec3(0.0, 1.0, 0.0), Vec3(0.0, 0.0, -1.0))
            Direction.SOUTH -> Axes(Vec3(-1.0, 0.0, 0.0), Vec3(0.0, 1.0, 0.0), Vec3(0.0, 0.0, 1.0))
            Direction.WEST -> Axes(Vec3(0.0, 0.0, 1.0), Vec3(0.0, 1.0, 0.0), Vec3(-1.0, 0.0, 0.0))
            Direction.EAST -> Axes(Vec3(0.0, 0.0, -1.0), Vec3(0.0, 1.0, 0.0), Vec3(1.0, 0.0, 0.0))
            Direction.UP -> Axes(Vec3(1.0, 0.0, 0.0), Vec3(0.0, 0.0, -1.0), Vec3(0.0, 1.0, 0.0))
            Direction.DOWN -> Axes(Vec3(1.0, 0.0, 0.0), Vec3(0.0, 0.0, 1.0), Vec3(0.0, -1.0, 0.0))
        }
    }
}
