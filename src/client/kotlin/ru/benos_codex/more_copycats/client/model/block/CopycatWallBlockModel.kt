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
import net.minecraft.world.level.block.state.properties.WallSide
import ru.benos_codex.more_copycats.block.CopycatWallBlock
import ru.benos_codex.more_copycats.block.entity.CopycatFenceWallBlockEntity

class CopycatWallBlockModel(state: BlockState, unbaked: BlockStateModel.UnbakedRoot) : CopycatModel(state, unbaked) {
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
        val postFallback = blockEntity?.getSlotMaterial(CopycatFenceWallBlockEntity.WallSlot.POST) ?: material
        val materialsBySlot = buildMap {
            for (slot in CopycatFenceWallBlockEntity.WallSlot.entries) {
                val hasCustom = blockEntity?.hasCustomMaterial(slot) == true
                val actualMaterial = if (hasCustom) blockEntity.getSlotMaterial(slot) else postFallback
                put(slot, MaterialSlotDebug.material(slot.ordinal, hasCustom, actualMaterial))
            }
        }
        val refsBySlot = buildMap {
            for (slot in CopycatFenceWallBlockEntity.WallSlot.entries) {
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
        refsBySlot: Map<CopycatFenceWallBlockEntity.WallSlot, Map<Direction, BakedQuad>>,
        materialsBySlot: Map<CopycatFenceWallBlockEntity.WallSlot, BlockState>,
        block: CopycatBlock,
        state: BlockState,
        world: BlockAndTintGetter,
        pos: BlockPos
    ) {
        for (quad in templatePart.getQuads(null)) {
            val slot = pickSlot(quad, state)
            val refs = refsBySlot[slot].orEmpty()
            val material = materialsBySlot[slot] ?: materialsBySlot.getValue(CopycatFenceWallBlockEntity.WallSlot.POST)
            val ref = refs[quad.direction()]
            val useConnected = slot != CopycatFenceWallBlockEntity.WallSlot.POST
            val remapped = CopycatConnectedTextureHelper.remapQuad(quad, ref?.sprite(), ref, world, pos, state, material, useConnected)
            if (CopycatQuadCulling.isOnOuterBoundary(quad, quad.direction())) builder.addCulledFace(quad.direction(), remapped)
            else builder.addUnculledFace(remapped)
        }

        for (direction in Direction.entries) {
            for (quad in templatePart.getQuads(direction)) {
                val slot = pickSlot(quad, state)
                val refs = refsBySlot[slot].orEmpty()
                val material = materialsBySlot[slot] ?: materialsBySlot.getValue(CopycatFenceWallBlockEntity.WallSlot.POST)
                val ref = refs[direction]
                val useConnected = slot != CopycatFenceWallBlockEntity.WallSlot.POST
                val remapped = CopycatConnectedTextureHelper.remapQuad(quad, ref?.sprite(), ref, world, pos, state, material, useConnected)
                if (block.shouldFaceAlwaysRender(state, direction)) builder.addUnculledFace(remapped)
                else builder.addCulledFace(direction, remapped)
            }
        }
    }

    private fun pickSlot(
        quad: BakedQuad,
        state: BlockState
    ): CopycatFenceWallBlockEntity.WallSlot {
        val xs = floatArrayOf(quad.position0().x(), quad.position1().x(), quad.position2().x(), quad.position3().x())
        val zs = floatArrayOf(quad.position0().z(), quad.position1().z(), quad.position2().z(), quad.position3().z())
        val minX = xs.minOrNull() ?: 0f
        val maxX = xs.maxOrNull() ?: 0f
        val minZ = zs.minOrNull() ?: 0f
        val maxZ = zs.maxOrNull() ?: 0f
        val scale = if (maxOf(kotlin.math.abs(minX), kotlin.math.abs(maxX), kotlin.math.abs(minZ), kotlin.math.abs(maxZ)) > 2.0f) 16.0f else 1.0f
        val centerMin = 3.85f / 16.0f * scale
        val centerMax = 12.15f / 16.0f * scale
        val northExcess = (centerMin - minZ).coerceAtLeast(0f)
        val eastExcess = (maxX - centerMax).coerceAtLeast(0f)
        val southExcess = (maxZ - centerMax).coerceAtLeast(0f)
        val westExcess = (centerMin - minX).coerceAtLeast(0f)
        val bestDirection = listOf(
            Direction.NORTH to northExcess,
            Direction.EAST to eastExcess,
            Direction.SOUTH to southExcess,
            Direction.WEST to westExcess
        ).maxByOrNull { it.second }

        if (bestDirection != null && bestDirection.second > scale / 512.0f) {
            val direction = bestDirection.first
            if (state.getValue(wallProperty(direction)) != WallSide.NONE) {
                return wallSlot(direction)
            }
        }

        return CopycatFenceWallBlockEntity.WallSlot.POST
    }

    private fun wallProperty(direction: Direction) =
        when (direction) {
            Direction.NORTH -> CopycatWallBlock.NORTH_WALL
            Direction.EAST -> CopycatWallBlock.EAST_WALL
            Direction.SOUTH -> CopycatWallBlock.SOUTH_WALL
            Direction.WEST -> CopycatWallBlock.WEST_WALL
            else -> CopycatWallBlock.NORTH_WALL
        }

    private fun wallSlot(direction: Direction): CopycatFenceWallBlockEntity.WallSlot =
        when (direction) {
            Direction.NORTH -> CopycatFenceWallBlockEntity.WallSlot.NORTH
            Direction.EAST -> CopycatFenceWallBlockEntity.WallSlot.EAST
            Direction.SOUTH -> CopycatFenceWallBlockEntity.WallSlot.SOUTH
            Direction.WEST -> CopycatFenceWallBlockEntity.WallSlot.WEST
            else -> CopycatFenceWallBlockEntity.WallSlot.POST
        }
}
