package ru.benos_codex.more_copycats.client.model.block

import com.zurrtum.create.client.infrastructure.model.CopycatModel
import com.zurrtum.create.content.decoration.copycat.CopycatBlock
import net.minecraft.client.renderer.block.model.BakedQuad
import net.minecraft.client.renderer.block.model.BlockModelPart
import net.minecraft.client.renderer.block.model.BlockStateModel
import net.minecraft.client.renderer.block.model.SimpleModelWrapper
import net.minecraft.client.resources.model.QuadCollection
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.util.RandomSource
import net.minecraft.world.level.BlockAndTintGetter
import net.minecraft.world.level.block.state.BlockState
import ru.benos_codex.more_copycats.block.CopycatVerticalStepBlock
import ru.benos_codex.more_copycats.block.entity.CopycatVerticalStepBlockEntity

class CopycatVerticalStepBlockModel(state: BlockState, unbaked: BlockStateModel.UnbakedRoot) : CopycatModel(state, unbaked) {
    override fun addPartsWithInfo(
        world: BlockAndTintGetter,
        pos: BlockPos,
        state: BlockState,
        block: CopycatBlock,
        material: BlockState,
        random: RandomSource,
        parts: MutableList<BlockModelPart>
    ) {
        val blockEntity = world.getBlockEntity(pos) as? CopycatVerticalStepBlockEntity
        val p0Fallback = blockEntity?.getSlotMaterial(CopycatVerticalStepBlockEntity.Slot.P0) ?: material
        val p0HasCustom = blockEntity?.hasCustomMaterial(CopycatVerticalStepBlockEntity.Slot.P0) == true
        val p1HasCustom = blockEntity?.hasCustomMaterial(CopycatVerticalStepBlockEntity.Slot.P1) == true
        val p2HasCustom = blockEntity?.hasCustomMaterial(CopycatVerticalStepBlockEntity.Slot.P2) == true
        val p3HasCustom = blockEntity?.hasCustomMaterial(CopycatVerticalStepBlockEntity.Slot.P3) == true
        val p0 = MaterialSlotDebug.material(0, p0HasCustom, p0Fallback)
        val p1 = MaterialSlotDebug.material(1, p1HasCustom, blockEntity?.getSlotMaterial(CopycatVerticalStepBlockEntity.Slot.P1) ?: p0Fallback)
        val p2 = MaterialSlotDebug.material(2, p2HasCustom, blockEntity?.getSlotMaterial(CopycatVerticalStepBlockEntity.Slot.P2) ?: p0Fallback)
        val p3 = MaterialSlotDebug.material(3, p3HasCustom, blockEntity?.getSlotMaterial(CopycatVerticalStepBlockEntity.Slot.P3) ?: p0Fallback)

        val refsBySlot = mapOf(
            CopycatVerticalStepBlockEntity.Slot.P0 to (
                MaterialSlotDebug.references(0, p0HasCustom) { collectMaterialReferences(world, pos, it, random) }
                    ?: collectMaterialReferences(world, pos, p0, random)
                ),
            CopycatVerticalStepBlockEntity.Slot.P1 to (
                MaterialSlotDebug.references(1, p1HasCustom) { collectMaterialReferences(world, pos, it, random) }
                    ?: collectMaterialReferences(world, pos, p1, random)
                ),
            CopycatVerticalStepBlockEntity.Slot.P2 to (
                MaterialSlotDebug.references(2, p2HasCustom) { collectMaterialReferences(world, pos, it, random) }
                    ?: collectMaterialReferences(world, pos, p2, random)
                ),
            CopycatVerticalStepBlockEntity.Slot.P3 to (
                MaterialSlotDebug.references(3, p3HasCustom) { collectMaterialReferences(world, pos, it, random) }
                    ?: collectMaterialReferences(world, pos, p3, random)
                )
        )
        val materialsBySlot = mapOf(
            CopycatVerticalStepBlockEntity.Slot.P0 to p0,
            CopycatVerticalStepBlockEntity.Slot.P1 to p1,
            CopycatVerticalStepBlockEntity.Slot.P2 to p2,
            CopycatVerticalStepBlockEntity.Slot.P3 to p3
        )

        val templateParts = mutableListOf<BlockModelPart>()
        model.collectParts(random, templateParts)

        for (templatePart in templateParts) {
            val builder = QuadCollection.Builder()
            addPartRemapped(templatePart, builder, refsBySlot, materialsBySlot, state, world, pos)
            parts += SimpleModelWrapper(builder.build(), MaterialSlotDebug.ambientOcclusion(templatePart.useAmbientOcclusion()), templatePart.particleIcon())
        }
    }

    private fun collectMaterialReferences(
        world: BlockAndTintGetter,
        pos: BlockPos,
        material: BlockState,
        random: RandomSource
    ): Map<Direction, BakedQuad> {
        val refs = mutableMapOf<Direction, BakedQuad>()
        val materialParts = getMaterialParts(world, pos, material, random, getModelOf(material))

        for (part in materialParts) {
            for (direction in Direction.entries) {
                if (refs.containsKey(direction)) continue

                val direct = part.getQuads(direction)
                if (direct.isNotEmpty()) {
                    refs[direction] = direct[0]
                    continue
                }

                for (quad in part.getQuads(null)) {
                    if (quad.direction() == direction) {
                        refs[direction] = quad
                        break
                    }
                }
            }
        }

        return refs
    }

    private fun addPartRemapped(
        templatePart: BlockModelPart,
        builder: QuadCollection.Builder,
        refsBySlot: Map<CopycatVerticalStepBlockEntity.Slot, Map<Direction, BakedQuad>>,
        materialsBySlot: Map<CopycatVerticalStepBlockEntity.Slot, BlockState>,
        state: BlockState,
        world: BlockAndTintGetter,
        pos: BlockPos
    ) {
        for (quad in templatePart.getQuads(null)) {
            val slot = pickSlot(quad)
            val refs = refsBySlot[slot].orEmpty()
            val slotMaterial = materialsBySlot.getValue(slot)
            val ref = refs[quad.direction()]
            val remapped = CopycatConnectedTextureHelper.remapQuad(quad, ref?.sprite(), ref, world, pos, state, slotMaterial)
            addQuadWithDynamicCull(builder, remapped, quad.direction(), slot, state)
        }

        for (direction in Direction.entries) {
            for (quad in templatePart.getQuads(direction)) {
                val slot = pickSlot(quad)
                val refs = refsBySlot[slot].orEmpty()
                val slotMaterial = materialsBySlot.getValue(slot)
                val ref = refs[direction]
                val remapped = CopycatConnectedTextureHelper.remapQuad(quad, ref?.sprite(), ref, world, pos, state, slotMaterial)
                addQuadWithDynamicCull(builder, remapped, direction, slot, state)
            }
        }
    }

    private fun addQuadWithDynamicCull(
        builder: QuadCollection.Builder,
        quad: BakedQuad,
        direction: Direction,
        slot: CopycatVerticalStepBlockEntity.Slot,
        state: BlockState
    ) {
        if (shouldSkipInternalFace(quad, direction, slot, state)) return

        if (isOuterBoundaryFace(quad, direction)) {
            builder.addCulledFace(direction, quad)
        } else {
            builder.addUnculledFace(quad)
        }
    }

    private fun shouldSkipInternalFace(
        quad: BakedQuad,
        direction: Direction,
        slot: CopycatVerticalStepBlockEntity.Slot,
        state: BlockState
    ): Boolean {
        if (!isInnerSeamFace(quad, direction)) return false
        val adjacent = adjacentSlot(slot, direction) ?: return false
        return hasSlot(state, adjacent)
    }

    private fun hasSlot(state: BlockState, slot: CopycatVerticalStepBlockEntity.Slot): Boolean = when (slot) {
        CopycatVerticalStepBlockEntity.Slot.P0 -> state.getValue(CopycatVerticalStepBlock.P0)
        CopycatVerticalStepBlockEntity.Slot.P1 -> state.getValue(CopycatVerticalStepBlock.P1)
        CopycatVerticalStepBlockEntity.Slot.P2 -> state.getValue(CopycatVerticalStepBlock.P2)
        CopycatVerticalStepBlockEntity.Slot.P3 -> state.getValue(CopycatVerticalStepBlock.P3)
    }

    private fun adjacentSlot(
        slot: CopycatVerticalStepBlockEntity.Slot,
        direction: Direction
    ): CopycatVerticalStepBlockEntity.Slot? = when (slot) {
        CopycatVerticalStepBlockEntity.Slot.P0 -> when (direction) {
            Direction.EAST -> CopycatVerticalStepBlockEntity.Slot.P1
            Direction.SOUTH -> CopycatVerticalStepBlockEntity.Slot.P2
            else -> null
        }

        CopycatVerticalStepBlockEntity.Slot.P1 -> when (direction) {
            Direction.WEST -> CopycatVerticalStepBlockEntity.Slot.P0
            Direction.SOUTH -> CopycatVerticalStepBlockEntity.Slot.P3
            else -> null
        }

        CopycatVerticalStepBlockEntity.Slot.P2 -> when (direction) {
            Direction.EAST -> CopycatVerticalStepBlockEntity.Slot.P3
            Direction.NORTH -> CopycatVerticalStepBlockEntity.Slot.P0
            else -> null
        }

        CopycatVerticalStepBlockEntity.Slot.P3 -> when (direction) {
            Direction.WEST -> CopycatVerticalStepBlockEntity.Slot.P2
            Direction.NORTH -> CopycatVerticalStepBlockEntity.Slot.P1
            else -> null
        }
    }

    private fun isOuterBoundaryFace(quad: BakedQuad, direction: Direction): Boolean {
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

    private fun isInnerSeamFace(quad: BakedQuad, direction: Direction): Boolean {
        val bounds = quadBounds(quad)
        return when (direction) {
            Direction.WEST, Direction.EAST -> isNear((bounds.minX + bounds.maxX) * 0.5f, 0.5f)
            Direction.NORTH, Direction.SOUTH -> isNear((bounds.minZ + bounds.maxZ) * 0.5f, 0.5f)
            else -> false
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

    private fun isNear(value: Float, target: Float): Boolean = kotlin.math.abs(value - target) <= 1.0e-4f

    private fun pickSlot(
        quad: BakedQuad
    ): CopycatVerticalStepBlockEntity.Slot {
        val cx = (quad.position0().x() + quad.position1().x() + quad.position2().x() + quad.position3().x()) * 0.25f
        val cz = (quad.position0().z() + quad.position1().z() + quad.position2().z() + quad.position3().z()) * 0.25f
        val direction = quad.direction()
        val east = splitAtHalf(cx, direction, negativeOnSeamFace = Direction.EAST, positiveOnSeamFace = Direction.WEST)
        val south = splitAtHalf(cz, direction, negativeOnSeamFace = Direction.SOUTH, positiveOnSeamFace = Direction.NORTH)

        return when {
            !east && !south -> CopycatVerticalStepBlockEntity.Slot.P0
            east && !south -> CopycatVerticalStepBlockEntity.Slot.P1
            !east && south -> CopycatVerticalStepBlockEntity.Slot.P2
            else -> CopycatVerticalStepBlockEntity.Slot.P3
        }
    }

    private fun splitAtHalf(
        coord: Float,
        direction: Direction,
        negativeOnSeamFace: Direction,
        positiveOnSeamFace: Direction
    ): Boolean {
        val eps = 1.0e-4f
        if (coord > 0.5f + eps) return true
        if (coord < 0.5f - eps) return false

        return when (direction) {
            negativeOnSeamFace -> false
            positiveOnSeamFace -> true
            else -> false
        }
    }
}
