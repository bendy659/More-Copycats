package ru.benos_codex.more_copycats.client.model.block

import com.zurrtum.create.AllBlocks
import com.zurrtum.create.client.infrastructure.model.CopycatModel
import com.zurrtum.create.content.decoration.copycat.CopycatBlock
import net.minecraft.client.renderer.block.model.BakedQuad
import net.minecraft.client.renderer.block.model.BlockModelPart
import net.minecraft.client.renderer.block.model.BlockStateModel
import net.minecraft.client.renderer.block.model.SimpleModelWrapper
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.client.resources.model.QuadCollection
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.util.RandomSource
import net.minecraft.world.level.BlockAndTintGetter
import net.minecraft.world.level.block.DoorBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf
import ru.benos_codex.more_copycats.block.entity.CopycatSlidingDoorBlockEntity

class CopycatSlidingDoorBlockModel(state: BlockState, unbaked: BlockStateModel.UnbakedRoot) : CopycatModel(state, unbaked) {
    override fun addPartsWithInfo(
        world: BlockAndTintGetter,
        pos: BlockPos,
        state: BlockState,
        random: RandomSource,
        parts: MutableList<BlockModelPart>
    ) {
        val (material, refs) = resolveMaterialAndReferences(world, pos, state, random)
        val templateParts = mutableListOf<BlockModelPart>()
        model.collectParts(random, templateParts)

        for (templatePart in templateParts) {
            val builder = QuadCollection.Builder()
            addPartRemapped(templatePart, builder, refs, state, material, world, pos)
            // Sliding door geometry should keep world AO to avoid looking brighter than nearby blocks when closed.
            parts += SimpleModelWrapper(builder.build(), MaterialSlotDebug.ambientOcclusion(true), templatePart.particleIcon())
        }
    }

    override fun addPartsWithInfo(
        world: BlockAndTintGetter,
        pos: BlockPos,
        state: BlockState,
        block: CopycatBlock,
        material: BlockState,
        random: RandomSource,
        parts: MutableList<BlockModelPart>
    ) {
        // unused: this model works for a non-CopycatBlock implementation
    }

    override fun particleSpriteWithInfo(world: BlockAndTintGetter, pos: BlockPos, state: BlockState): TextureAtlasSprite {
        val material = resolveMaterialAndReferences(world, pos, state, null).first
        return getModelOf(material).particleIcon()
    }

    private fun resolveMaterialAndReferences(
        world: BlockAndTintGetter,
        pos: BlockPos,
        state: BlockState,
        random: RandomSource?
    ): Pair<BlockState, Map<Direction, BakedQuad>> {
        val bePos = if (state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) pos.below() else pos
        val half = state.getValue(DoorBlock.HALF)
        val blockEntity = world.getBlockEntity(bePos) as? CopycatSlidingDoorBlockEntity
        val fallback = blockEntity?.getMaterialState(half) ?: AllBlocks.COPYCAT_BASE.defaultBlockState()
        val hasCustom = blockEntity?.hasCustomMaterial(half) == true
        val material = MaterialSlotDebug.material(half.ordinal, hasCustom, fallback)
        val refs = if (random != null) {
            MaterialSlotDebug.references(half.ordinal, hasCustom) {
                collectMaterialReferences(world, pos, it, random)
            } ?: collectMaterialReferences(world, pos, material, random)
        } else {
            emptyMap()
        }
        return material to refs
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
        refs: Map<Direction, BakedQuad>,
        state: BlockState,
        material: BlockState,
        world: BlockAndTintGetter,
        pos: BlockPos
    ) {
        for (quad in templatePart.getQuads(null)) {
            val ref = refs[quad.direction()]
            val remapped = CopycatConnectedTextureHelper.remapQuad(quad, ref?.sprite(), ref, world, pos, state, material)
            if (CopycatQuadCulling.isOnOuterBoundary(quad, quad.direction())) builder.addCulledFace(quad.direction(), remapped)
            else builder.addUnculledFace(remapped)
        }

        for (direction in Direction.entries) {
            for (quad in templatePart.getQuads(direction)) {
                val ref = refs[direction]
                val remapped = CopycatConnectedTextureHelper.remapQuad(quad, ref?.sprite(), ref, world, pos, state, material)
                builder.addCulledFace(direction, remapped)
            }
        }
    }
}
