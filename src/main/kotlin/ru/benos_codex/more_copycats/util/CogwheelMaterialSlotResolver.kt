package ru.benos_codex.more_copycats.util

import net.minecraft.core.Direction
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import kotlin.math.abs

/**
 * Slot indices:
 * 0..3 - gear_part_0..3
 * 4    - gear_case_outer_part
 * 5    - gear_case_inner_part
 * 6    - shaft
 * 7    - encased casing
 *
 * Local coordinate space is defined with shaft along local Z.
 */
object CogwheelMaterialSlotResolver {
    enum class Layout {
        NORMAL,
        ENCASED_VARIANT_2
    }

    private const val HIT_EPS = 0.005
    private const val RAYCAST_EPS = 1.0E-6
    private const val ENCASED_SHAFT_CORE_RADIAL = 0.145
    private const val ENCASED_OUTER_TO_INNER_SWITCH = 0.34
    private const val ENCASED_TOOTH_MIN_RADIAL = 0.43
    private const val ENCASED_DIAGONAL_TOOTH_MIN_OFFSET = 0.2

    data class SlotBox(
        val slot: Int,
        val minX: Double,
        val minY: Double,
        val minZ: Double,
        val maxX: Double,
        val maxY: Double,
        val maxZ: Double
    )

    data class WorldSlotBox(
        val slot: Int,
        val aabb: AABB
    )

    data class LocalHit(
        val x: Double,
        val y: Double,
        val z: Double
    )

    data class RaycastHit(
        val slot: Int,
        val hit: LocalHit,
        val distanceSquared: Double
    )

    private data class BoxRule(
        val box: SlotBox,
        val priority: Int
    )

    private data class RayCandidate(
        val box: SlotBox,
        val priority: Int,
        val hit: LocalHit,
        val distanceSquared: Double
    )

    private data class LayoutData(
        val interactionBoxes: List<SlotBox>,
        val displayBoxes: List<SlotBox>,
        val rules: List<BoxRule>
    )

    private fun b(
        slot: Int,
        x0: Double,
        y0: Double,
        z0: Double,
        x1: Double,
        y1: Double,
        z1: Double
    ): SlotBox =
        SlotBox(
            slot = slot,
            minX = x0 / 16.0,
            minY = y0 / 16.0,
            minZ = z0 / 16.0,
            maxX = x1 / 16.0,
            maxY = y1 / 16.0,
            maxZ = z1 / 16.0
        )

    /**
     * Blockbench source for encased shapes is authored with shaft along Y.
     * Swap Y/Z once so the runtime layout stays in the same local-Z convention.
     */
    private fun by(
        slot: Int,
        x0: Double,
        y0: Double,
        z0: Double,
        x1: Double,
        y1: Double,
        z1: Double
    ): SlotBox =
        b(slot, x0, z0, y0, x1, z1, y1)

    private fun rule(box: SlotBox, priority: Int = priority(box.slot)): BoxRule =
        BoxRule(box, priority)

    private fun priority(slot: Int): Int =
        when (slot) {
            6 -> 400
            7 -> 350
            4 -> 300
            5 -> 250
            else -> 100
        }

    private val normalBoxes: List<SlotBox> = buildList {
        add(b(6, 5.5, 5.5, 0.0, 10.5, 10.5, 16.0))

        add(b(4, 3.5, 3.5, 5.5, 12.5, 5.5, 10.5))
        add(b(4, 3.5, 10.5, 5.5, 12.5, 12.5, 10.5))
        add(b(4, 3.5, 5.5, 5.5, 5.5, 10.5, 10.5))
        add(b(4, 10.5, 5.5, 5.5, 12.5, 10.5, 10.5))

        add(b(5, 1.5, 1.5, 6.0, 14.5, 3.5, 10.0))
        add(b(5, 1.5, 12.5, 6.0, 14.5, 14.5, 10.0))
        add(b(5, 1.5, 3.5, 6.0, 3.5, 12.5, 10.0))
        add(b(5, 12.5, 3.5, 6.0, 14.5, 12.5, 10.0))

        add(b(0, -1.0, 6.5, 6.5, 17.0, 9.5, 9.5))

        add(b(1, 0.0, 12.0, 7.0, 4.0, 16.0, 9.0))
        add(b(1, 12.0, 0.0, 7.0, 16.0, 4.0, 9.0))

        add(b(2, 6.5, -1.0, 6.5, 9.5, 17.0, 9.5))

        add(b(3, 12.0, 12.0, 7.0, 16.0, 16.0, 9.0))
        add(b(3, 0.0, 0.0, 7.0, 4.0, 4.0, 9.0))
    }

    private val encasedVariant2InteractionBaseBoxes: List<SlotBox> = buildList {
        add(by(-1, 2.0, 2.0, 2.0, 14.0, 14.0, 14.0))
        add(by(-1, 0.0, 10.0, 0.0, 16.0, 16.0, 2.0))
        add(by(-1, 0.0, 0.0, 0.0, 16.0, 6.0, 2.0))
        add(by(-1, 0.0, 10.0, 14.0, 16.0, 16.0, 16.0))
        add(by(-1, 0.0, 0.0, 14.0, 16.0, 6.0, 16.0))
        add(by(-1, 0.0, 10.0, 2.0, 2.0, 16.0, 14.0))
        add(by(-1, 14.0, 10.0, 2.0, 16.0, 16.0, 14.0))
        add(by(-1, 14.0, 0.0, 2.0, 16.0, 6.0, 14.0))
        add(by(-1, 0.0, 0.0, 2.0, 2.0, 6.0, 14.0))
    }

    private val encasedVariant2TopInteractionShaftBox: SlotBox =
        by(-1, 6.0, 8.0, 6.0, 10.0, 16.0, 10.0)

    private val encasedVariant2BottomInteractionShaftBox: SlotBox =
        by(-1, 6.0, 0.0, 6.0, 10.0, 8.0, 10.0)

    private val encasedVariant2PrimaryBoxes: List<SlotBox> = buildList {
        add(by(4, 3.5, 5.5, 3.5, 12.5, 10.5, 12.5))
        add(by(5, 1.5, 6.5, 1.5, 14.5, 9.5, 14.5))

        add(by(2, 6.5, 7.0, 0.0, 9.5, 9.0, 16.0))
        add(by(0, 0.0, 7.0, 6.5, 16.0, 9.0, 9.5))

        add(by(3, 12.0, 7.5, 12.0, 15.5, 8.5, 15.5))
        add(by(3, 0.5, 7.5, 0.5, 4.0, 8.5, 4.0))

        add(by(1, 0.5, 7.5, 12.0, 4.0, 8.5, 15.5))
        add(by(1, 12.0, 7.5, 0.5, 15.5, 8.5, 4.0))
    }

    private val encasedVariant2TopShaftBox: SlotBox =
        by(6, 6.0, 8.0, 6.0, 10.0, 16.0, 10.0)

    private val encasedVariant2BottomShaftBox: SlotBox =
        by(6, 6.0, 0.0, 6.0, 10.0, 8.0, 10.0)

    private val encasedVariant2CasingBoxes: List<SlotBox> =
        buildList {
            add(by(7, -0.5, 14.0, 2.0, 2.0, 16.5, 14.0))
            add(by(7, 14.0, 14.0, 2.0, 16.5, 16.5, 14.0))
            add(by(7, 2.0, 14.0, 14.0, 14.0, 16.5, 16.5))
            add(by(7, 2.0, 14.0, -0.5, 14.0, 16.5, 2.0))
            add(by(7, 2.0, -0.5, -0.5, 14.0, 2.0, 2.0))
            add(by(7, 2.0, -0.5, 14.0, 14.0, 2.0, 16.5))
            add(by(7, -0.5, -0.5, 2.0, 2.0, 2.0, 14.0))
            add(by(7, 14.0, -0.5, 2.0, 16.5, 2.0, 14.0))
            add(by(7, 14.0, 2.0, 14.0, 16.5, 14.0, 16.5))
            add(by(7, -0.5, 2.0, 14.0, 2.0, 14.0, 16.5))
            add(by(7, -0.5, 2.0, -0.5, 2.0, 14.0, 2.0))
            add(by(7, 14.0, 2.0, -0.5, 16.5, 14.0, 2.0))
        }

    private fun encasedVariant2InteractionBoxes(topShaft: Boolean, bottomShaft: Boolean): List<SlotBox> =
        buildList {
            addAll(encasedVariant2InteractionBaseBoxes)
            if (bottomShaft)
                add(encasedVariant2BottomInteractionShaftBox)
            if (topShaft)
                add(encasedVariant2TopInteractionShaftBox)
        }

    private fun encasedVariant2DisplayBoxes(topShaft: Boolean, bottomShaft: Boolean): List<SlotBox> =
        buildList {
            if (bottomShaft)
                add(encasedVariant2BottomShaftBox)
            if (topShaft)
                add(encasedVariant2TopShaftBox)
            addAll(encasedVariant2PrimaryBoxes)
            addAll(encasedVariant2CasingBoxes)
        }

    private fun layoutData(layout: Layout, topShaft: Boolean = true, bottomShaft: Boolean = true): LayoutData =
        when (layout) {
            Layout.NORMAL ->
                LayoutData(
                    interactionBoxes = normalBoxes,
                    displayBoxes = normalBoxes,
                    rules = normalBoxes.map(::rule)
                )

            Layout.ENCASED_VARIANT_2 ->
                LayoutData(
                    interactionBoxes = encasedVariant2InteractionBoxes(topShaft, bottomShaft),
                    displayBoxes = encasedVariant2DisplayBoxes(topShaft, bottomShaft),
                    rules = buildList {
                        if (bottomShaft)
                            add(rule(encasedVariant2BottomShaftBox))
                        if (topShaft)
                            add(rule(encasedVariant2TopShaftBox))
                        addAll(encasedVariant2PrimaryBoxes.map(::rule))
                        addAll(encasedVariant2CasingBoxes.map(::rule))
                    }
                )
        }

    fun buildInteractionShape(layout: Layout = Layout.NORMAL, axis: Direction.Axis): VoxelShape {
        return buildInteractionShape(layout, axis, topShaft = true, bottomShaft = true)
    }

    fun buildInteractionShape(
        layout: Layout = Layout.NORMAL,
        axis: Direction.Axis,
        topShaft: Boolean,
        bottomShaft: Boolean
    ): VoxelShape {
        var shape = Shapes.empty()

        for (box in layoutData(layout, topShaft, bottomShaft).interactionBoxes) {
            val worldMin = localToWorld(axis, box.minX, box.minY, box.minZ)
            val worldMax = localToWorld(axis, box.maxX, box.maxY, box.maxZ)

            shape = Shapes.or(
                shape,
                Shapes.create(
                    minOf(worldMin.x, worldMax.x),
                    minOf(worldMin.y, worldMax.y),
                    minOf(worldMin.z, worldMax.z),
                    maxOf(worldMin.x, worldMax.x),
                    maxOf(worldMin.y, worldMax.y),
                    maxOf(worldMin.z, worldMax.z)
                )
            )
        }

        return shape.optimize()
    }

    fun allLocalBoxes(): List<SlotBox> =
        allLocalBoxes(Layout.NORMAL)

    fun allLocalBoxes(layout: Layout = Layout.NORMAL): List<SlotBox> =
        layoutData(layout).displayBoxes

    fun allLocalBoxes(layout: Layout = Layout.NORMAL, topShaft: Boolean, bottomShaft: Boolean): List<SlotBox> =
        layoutData(layout, topShaft, bottomShaft).displayBoxes

    fun allWorldBoxes(
        axis: Direction.Axis,
        blockX: Double,
        blockY: Double,
        blockZ: Double
    ): List<WorldSlotBox> =
        allWorldBoxes(Layout.NORMAL, axis, blockX, blockY, blockZ)

    fun allWorldBoxes(
        layout: Layout = Layout.NORMAL,
        axis: Direction.Axis,
        blockX: Double,
        blockY: Double,
        blockZ: Double
    ): List<WorldSlotBox> =
        layoutData(layout).displayBoxes.map { box ->
            WorldSlotBox(
                slot = box.slot,
                aabb = toWorldAabb(axis, blockX, blockY, blockZ, box)
            )
        }

    fun allWorldBoxes(
        layout: Layout = Layout.NORMAL,
        axis: Direction.Axis,
        blockX: Double,
        blockY: Double,
        blockZ: Double,
        topShaft: Boolean,
        bottomShaft: Boolean
    ): List<WorldSlotBox> =
        layoutData(layout, topShaft, bottomShaft).displayBoxes.map { box ->
            WorldSlotBox(
                slot = box.slot,
                aabb = toWorldAabb(axis, blockX, blockY, blockZ, box)
            )
        }

    fun slotWorldBoxes(
        axis: Direction.Axis,
        blockX: Double,
        blockY: Double,
        blockZ: Double
    ): List<WorldSlotBox> =
        slotWorldBoxes(Layout.NORMAL, axis, blockX, blockY, blockZ)

    fun slotWorldBoxes(
        layout: Layout = Layout.NORMAL,
        axis: Direction.Axis,
        blockX: Double,
        blockY: Double,
        blockZ: Double
    ): List<WorldSlotBox> =
        allWorldBoxes(layout, axis, blockX, blockY, blockZ)
            .groupBy(WorldSlotBox::slot)
            .toSortedMap()
            .flatMap { (slot, boxes) ->
                if (layout != Layout.NORMAL || slot !in setOf(4, 5) || boxes.size < 3)
                    boxes
                else
                    listOf(WorldSlotBox(slot, boxes.map(WorldSlotBox::aabb).reduce(::union)))
            }

    fun slotWorldBoxes(
        layout: Layout = Layout.NORMAL,
        axis: Direction.Axis,
        blockX: Double,
        blockY: Double,
        blockZ: Double,
        topShaft: Boolean,
        bottomShaft: Boolean
    ): List<WorldSlotBox> =
        allWorldBoxes(layout, axis, blockX, blockY, blockZ, topShaft, bottomShaft)
            .groupBy(WorldSlotBox::slot)
            .toSortedMap()
            .flatMap { (slot, boxes) ->
                if (layout != Layout.NORMAL || slot !in setOf(4, 5) || boxes.size < 3)
                    boxes
                else
                    listOf(WorldSlotBox(slot, boxes.map(WorldSlotBox::aabb).reduce(::union)))
            }

    fun resolve(
        axis: Direction.Axis,
        relX: Double,
        relY: Double,
        relZ: Double,
        hitFace: Direction? = null
    ): Int =
        resolve(Layout.NORMAL, axis, relX, relY, relZ, hitFace)

    fun resolve(
        layout: Layout = Layout.NORMAL,
        axis: Direction.Axis,
        relX: Double,
        relY: Double,
        relZ: Double,
        hitFace: Direction? = null
    ): Int {
        return resolve(layout, axis, relX, relY, relZ, hitFace, topShaft = true, bottomShaft = true)
    }

    fun resolve(
        layout: Layout = Layout.NORMAL,
        axis: Direction.Axis,
        relX: Double,
        relY: Double,
        relZ: Double,
        hitFace: Direction? = null,
        topShaft: Boolean,
        bottomShaft: Boolean
    ): Int {
        resolveStrict(layout, axis, relX, relY, relZ, topShaft, bottomShaft)?.let { return it }

        val local = toLocal(axis, relX, relY, relZ)
        val nearest = layoutData(layout, topShaft, bottomShaft).rules.minWithOrNull(
            compareBy<BoxRule> { distanceSquaredToAabb(local.x, local.y, local.z, it.box) }
                .thenByDescending { it.priority }
        )

        val rawSlot = nearest?.box?.slot ?: 6

        return finalizeResolvedSlot(layout, local, rawSlot, emptyList())
    }

    fun raycast(
        layout: Layout = Layout.NORMAL,
        axis: Direction.Axis,
        startX: Double,
        startY: Double,
        startZ: Double,
        endX: Double,
        endY: Double,
        endZ: Double,
        topShaft: Boolean = true,
        bottomShaft: Boolean = true
    ): RaycastHit? {
        val data = layoutData(layout, topShaft, bottomShaft)
        val localStart = toLocal(axis, startX, startY, startZ)
        val localEnd = toLocal(axis, endX, endY, endZ)
        val start = Vec3(localStart.x, localStart.y, localStart.z)
        val end = Vec3(localEnd.x, localEnd.y, localEnd.z)
        val hits = data.rules.mapNotNull { rule ->
            val clipped = AABB(
                rule.box.minX,
                rule.box.minY,
                rule.box.minZ,
                rule.box.maxX,
                rule.box.maxY,
                rule.box.maxZ
            ).clip(start, end).orElse(null) ?: return@mapNotNull null

            RayCandidate(
                box = rule.box,
                priority = rule.priority,
                hit = LocalHit(clipped.x, clipped.y, clipped.z),
                distanceSquared = clipped.distanceToSqr(start)
            )
        }
        if (hits.isEmpty())
            return null

        val nearestDistance = hits.minOf(RayCandidate::distanceSquared)
        val nearestHits = hits.filter { abs(it.distanceSquared - nearestDistance) <= RAYCAST_EPS }
        val best = nearestHits.maxWithOrNull(
            compareBy<RayCandidate> { it.priority }
                .thenByDescending { centerProximityScore(it.box, it.hit.x, it.hit.y, it.hit.z) }
        ) ?: return null
        val containing = data.rules.filter { contains(it.box, best.hit.x, best.hit.y, best.hit.z, HIT_EPS) }
        val slot = finalizeResolvedSlot(layout, best.hit, best.box.slot, containing)

        return RaycastHit(slot, best.hit, best.distanceSquared)
    }

    fun resolveStrict(
        axis: Direction.Axis,
        relX: Double,
        relY: Double,
        relZ: Double
    ): Int? =
        resolveStrict(Layout.NORMAL, axis, relX, relY, relZ)

    fun resolveStrict(
        layout: Layout = Layout.NORMAL,
        axis: Direction.Axis,
        relX: Double,
        relY: Double,
        relZ: Double
    ): Int? {
        return resolveStrict(layout, axis, relX, relY, relZ, topShaft = true, bottomShaft = true)
    }

    fun resolveStrict(
        layout: Layout = Layout.NORMAL,
        axis: Direction.Axis,
        relX: Double,
        relY: Double,
        relZ: Double,
        topShaft: Boolean,
        bottomShaft: Boolean
    ): Int? {
        val local = toLocal(axis, relX, relY, relZ)
        val containing = layoutData(layout, topShaft, bottomShaft).rules
            .asSequence()
            .filter { contains(it.box, local.x, local.y, local.z, HIT_EPS) }
            .toList()

        if (containing.isEmpty())
            return null

        val rawSlot = containing.maxWithOrNull(
            compareBy<BoxRule> { it.priority }
                .thenByDescending { centerProximityScore(it.box, local.x, local.y, local.z) }
        )!!.box.slot

        return finalizeResolvedSlot(layout, local, rawSlot, containing)
    }

    private fun finalizeResolvedSlot(
        layout: Layout,
        local: LocalHit,
        rawSlot: Int,
        containing: List<BoxRule>
    ): Int {
        if (layout != Layout.ENCASED_VARIANT_2)
            return rawSlot

        val offsetX = kotlin.math.abs(local.x - 0.5)
        val offsetY = kotlin.math.abs(local.y - 0.5)
        val radial = kotlin.math.max(offsetX, offsetY)
        val diagonal = kotlin.math.min(offsetX, offsetY)
        val availableSlots = containing.asSequence().map { it.box.slot }.toSet()

        if (availableSlots.any { it in 0..3 } &&
            (radial >= ENCASED_TOOTH_MIN_RADIAL || diagonal >= ENCASED_DIAGONAL_TOOTH_MIN_OFFSET)
        )
            return toothIndex(local.x - 0.5, local.y - 0.5)

        if (rawSlot == 6 && availableSlots.any { it == 4 || it == 5 } && radial >= ENCASED_SHAFT_CORE_RADIAL) {
            return if (radial >= ENCASED_OUTER_TO_INNER_SWITCH)
                5
            else
                4
        }

        if (rawSlot == 5 && radial < ENCASED_OUTER_TO_INNER_SWITCH)
            return 4

        if ((rawSlot == 4 || rawSlot == 5) && 4 in availableSlots && 5 in availableSlots)
            return if (radial >= ENCASED_OUTER_TO_INNER_SWITCH) 5 else 4

        return rawSlot
    }

    private fun toothIndex(dx: Double, dy: Double): Int {
        var phi = kotlin.math.atan2(dy, -dx)
        if (phi < 0.0)
            phi += kotlin.math.PI
        if (phi >= kotlin.math.PI)
            phi -= kotlin.math.PI

        val step = kotlin.math.PI / 4.0

        return (((phi / step) + 0.5).toInt() and 3)
    }

    fun toLocal(axis: Direction.Axis, relX: Double, relY: Double, relZ: Double): LocalHit =
        when (axis) {
            Direction.Axis.Z -> LocalHit(relX, relY, relZ)
            Direction.Axis.X -> LocalHit(relY, relZ, relX)
            Direction.Axis.Y -> LocalHit(relX, relZ, relY)
        }

    fun toWorldAabb(
        axis: Direction.Axis,
        blockX: Double,
        blockY: Double,
        blockZ: Double,
        box: SlotBox
    ): AABB {
        var minX = Double.POSITIVE_INFINITY
        var minY = Double.POSITIVE_INFINITY
        var minZ = Double.POSITIVE_INFINITY
        var maxX = Double.NEGATIVE_INFINITY
        var maxY = Double.NEGATIVE_INFINITY
        var maxZ = Double.NEGATIVE_INFINITY

        for (lx in listOf(box.minX, box.maxX)) {
            for (ly in listOf(box.minY, box.maxY)) {
                for (lz in listOf(box.minZ, box.maxZ)) {
                    val world = localToWorld(axis, lx, ly, lz)
                    minX = minOf(minX, world.x)
                    minY = minOf(minY, world.y)
                    minZ = minOf(minZ, world.z)
                    maxX = maxOf(maxX, world.x)
                    maxY = maxOf(maxY, world.y)
                    maxZ = maxOf(maxZ, world.z)
                }
            }
        }

        return AABB(
            blockX + minX,
            blockY + minY,
            blockZ + minZ,
            blockX + maxX,
            blockY + maxY,
            blockZ + maxZ
        )
    }

    private fun localToWorld(axis: Direction.Axis, localX: Double, localY: Double, localZ: Double): LocalHit =
        when (axis) {
            Direction.Axis.Z -> LocalHit(localX, localY, localZ)
            Direction.Axis.X -> LocalHit(localZ, localX, localY)
            Direction.Axis.Y -> LocalHit(localX, localZ, localY)
        }

    private fun union(first: AABB, second: AABB): AABB =
        AABB(
            minOf(first.minX, second.minX),
            minOf(first.minY, second.minY),
            minOf(first.minZ, second.minZ),
            maxOf(first.maxX, second.maxX),
            maxOf(first.maxY, second.maxY),
            maxOf(first.maxZ, second.maxZ)
        )

    private fun contains(box: SlotBox, x: Double, y: Double, z: Double, eps: Double): Boolean =
        x >= box.minX - eps &&
            x <= box.maxX + eps &&
            y >= box.minY - eps &&
            y <= box.maxY + eps &&
            z >= box.minZ - eps &&
            z <= box.maxZ + eps

    private fun distanceSquaredToAabb(x: Double, y: Double, z: Double, box: SlotBox): Double {
        val dx = when {
            x < box.minX -> box.minX - x
            x > box.maxX -> x - box.maxX
            else -> 0.0
        }
        val dy = when {
            y < box.minY -> box.minY - y
            y > box.maxY -> y - box.maxY
            else -> 0.0
        }
        val dz = when {
            z < box.minZ -> box.minZ - z
            z > box.maxZ -> z - box.maxZ
            else -> 0.0
        }

        return dx * dx + dy * dy + dz * dz
    }

    private fun centerProximityScore(box: SlotBox, x: Double, y: Double, z: Double): Double {
        val cx = (box.minX + box.maxX) * 0.5
        val cy = (box.minY + box.maxY) * 0.5
        val cz = (box.minZ + box.maxZ) * 0.5
        val dx = x - cx
        val dy = y - cy
        val dz = z - cz

        return -(dx * dx + dy * dy + dz * dz)
    }
}
