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
import ru.benos_codex.more_copycats.block.CopycatFenceBlock
import ru.benos_codex.more_copycats.block.entity.CopycatFenceWallBlockEntity

class CopycatFenceBlockModel(state: BlockState, unbaked: BlockStateModel.UnbakedRoot) : CopycatModel(state, unbaked) {
    override fun addPartsWithInfo(
        world: BlockAndTintGetter,
        pos: BlockPos,
        state: BlockState,
        block: CopycatBlock,
        material: BlockState,
        random: RandomSource,
        parts: MutableList<BlockModelPart>
    ) {
        val blockEntity = world.getBlockEntity(pos) as? CopycatFenceWallBlockEntity
        val postFallback = blockEntity?.getSlotMaterial(CopycatFenceWallBlockEntity.FenceSlot.POST) ?: material
        val materialsBySlot = buildMap {
            for (slot in CopycatFenceWallBlockEntity.FenceSlot.entries) {
                val hasCustom = blockEntity?.hasCustomMaterial(slot) == true
                val actualMaterial = if (hasCustom) blockEntity.getSlotMaterial(slot) else postFallback
                put(slot, MaterialSlotDebug.material(slot.ordinal, hasCustom, actualMaterial))
            }
        }
        val refsBySlot = buildMap {
            for (slot in CopycatFenceWallBlockEntity.FenceSlot.entries) {
                val hasCustom = blockEntity?.hasCustomMaterial(slot) == true
                val materialForRefs = materialsBySlot.getValue(slot)
                val refs = MaterialSlotDebug.references(slot.ordinal, hasCustom) {
                    collectMaterialReferences(world, pos, it, random)
                } ?: collectMaterialReferences(world, pos, materialForRefs, random)
                put(slot, refs)
            }
        }

        val templateParts = mutableListOf<BlockModelPart>()
        model.collectParts(random, templateParts)

        for (templatePart in templateParts) {
            val builder = QuadCollection.Builder()
            addPartRemapped(templatePart, builder, refsBySlot, materialsBySlot, block, state, world, pos)
            parts += SimpleModelWrapper(
                builder.build(),
                MaterialSlotDebug.ambientOcclusion(templatePart.useAmbientOcclusion()),
                templatePart.particleIcon()
            )
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
        refsBySlot: Map<CopycatFenceWallBlockEntity.FenceSlot, Map<Direction, BakedQuad>>,
        materialsBySlot: Map<CopycatFenceWallBlockEntity.FenceSlot, BlockState>,
        block: CopycatBlock,
        state: BlockState,
        world: BlockAndTintGetter,
        pos: BlockPos
    ) {
        for (quad in templatePart.getQuads(null)) {
            val slot = pickSlot(quad, state)
            val refs = refsBySlot[slot].orEmpty()
            val material = materialsBySlot[slot] ?: materialsBySlot.getValue(CopycatFenceWallBlockEntity.FenceSlot.POST)
            val ref = refs[quad.direction()]
            val remapped = CopycatConnectedTextureHelper.remapQuad(quad, ref?.sprite(), ref, world, pos, state, material)
            if (CopycatQuadCulling.isOnOuterBoundary(quad, quad.direction())) builder.addCulledFace(quad.direction(), remapped)
            else builder.addUnculledFace(remapped)
        }

        for (direction in Direction.entries) {
            for (quad in templatePart.getQuads(direction)) {
                val slot = pickSlot(quad, state)
                val refs = refsBySlot[slot].orEmpty()
                val material = materialsBySlot[slot] ?: materialsBySlot.getValue(CopycatFenceWallBlockEntity.FenceSlot.POST)
                val ref = refs[direction]
                val remapped = CopycatConnectedTextureHelper.remapQuad(quad, ref?.sprite(), ref, world, pos, state, material)
                if (block.shouldFaceAlwaysRender(state, direction)) builder.addUnculledFace(remapped)
                else builder.addCulledFace(direction, remapped)
            }
        }
    }

    private fun pickSlot(
        quad: BakedQuad,
        state: BlockState
    ): CopycatFenceWallBlockEntity.FenceSlot {
        val ys = floatArrayOf(quad.position0().y(), quad.position1().y(), quad.position2().y(), quad.position3().y())
        val xs = floatArrayOf(quad.position0().x(), quad.position1().x(), quad.position2().x(), quad.position3().x())
        val zs = floatArrayOf(quad.position0().z(), quad.position1().z(), quad.position2().z(), quad.position3().z())
        val minY = ys.minOrNull() ?: 0f
        val maxY = ys.maxOrNull() ?: 0f
        val minX = xs.minOrNull() ?: 0f
        val maxX = xs.maxOrNull() ?: 0f
        val minZ = zs.minOrNull() ?: 0f
        val maxZ = zs.maxOrNull() ?: 0f
        val scale = if (maxOf(kotlin.math.abs(minX), kotlin.math.abs(maxX), kotlin.math.abs(minZ), kotlin.math.abs(maxZ), kotlin.math.abs(minY), kotlin.math.abs(maxY)) > 2.0f) 16.0f else 1.0f
        val centerMin = 6.0f / 16.0f * scale
        val centerMax = 10.0f / 16.0f * scale
        val topMin = 12.0f / 16.0f * scale
        val topMax = 15.0f / 16.0f * scale
        val bottomMin = 7.0f / 16.0f * scale
        val bottomMax = 10.0f / 16.0f * scale
        val eps = scale / 512.0f

        val bestDirection = listOf(
            Direction.NORTH to (centerMin - minZ).coerceAtLeast(0f),
            Direction.EAST to (maxX - centerMax).coerceAtLeast(0f),
            Direction.SOUTH to (maxZ - centerMax).coerceAtLeast(0f),
            Direction.WEST to (centerMin - minX).coerceAtLeast(0f)
        ).maxByOrNull { it.second }

        if (bestDirection != null && bestDirection.second > eps && state.getValue(connectionProperty(bestDirection.first))) {
            return when {
                minY >= topMin - eps && maxY <= topMax + eps -> topSlot(bestDirection.first)
                minY >= bottomMin - eps && maxY <= bottomMax + eps -> bottomSlot(bestDirection.first)
                else -> CopycatFenceWallBlockEntity.FenceSlot.POST
            }
        }

        return CopycatFenceWallBlockEntity.FenceSlot.POST
    }

    private fun connectionProperty(direction: Direction) =
        when (direction) {
            Direction.NORTH -> CopycatFenceBlock.NORTH
            Direction.EAST -> CopycatFenceBlock.EAST
            Direction.SOUTH -> CopycatFenceBlock.SOUTH
            Direction.WEST -> CopycatFenceBlock.WEST
            else -> CopycatFenceBlock.NORTH
        }

    private fun topSlot(direction: Direction): CopycatFenceWallBlockEntity.FenceSlot =
        when (direction) {
            Direction.NORTH -> CopycatFenceWallBlockEntity.FenceSlot.NORTH_TOP
            Direction.EAST -> CopycatFenceWallBlockEntity.FenceSlot.EAST_TOP
            Direction.SOUTH -> CopycatFenceWallBlockEntity.FenceSlot.SOUTH_TOP
            Direction.WEST -> CopycatFenceWallBlockEntity.FenceSlot.WEST_TOP
            else -> CopycatFenceWallBlockEntity.FenceSlot.POST
        }

    private fun bottomSlot(direction: Direction): CopycatFenceWallBlockEntity.FenceSlot =
        when (direction) {
            Direction.NORTH -> CopycatFenceWallBlockEntity.FenceSlot.NORTH_BOTTOM
            Direction.EAST -> CopycatFenceWallBlockEntity.FenceSlot.EAST_BOTTOM
            Direction.SOUTH -> CopycatFenceWallBlockEntity.FenceSlot.SOUTH_BOTTOM
            Direction.WEST -> CopycatFenceWallBlockEntity.FenceSlot.WEST_BOTTOM
            else -> CopycatFenceWallBlockEntity.FenceSlot.POST
        }
}
