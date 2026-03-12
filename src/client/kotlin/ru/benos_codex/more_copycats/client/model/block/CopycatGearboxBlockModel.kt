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
import net.minecraft.resources.Identifier
import net.minecraft.util.RandomSource
import net.minecraft.world.level.BlockAndTintGetter
import net.minecraft.world.level.block.state.BlockState
import ru.benos_codex.more_copycats.block.entity.CopycatGearboxBlockEntity

class CopycatGearboxBlockModel(state: BlockState, unbaked: BlockStateModel.UnbakedRoot) : CopycatModel(state, unbaked) {
    override fun addPartsWithInfo(
        world: BlockAndTintGetter,
        pos: BlockPos,
        state: BlockState,
        random: RandomSource,
        parts: MutableList<BlockModelPart>
    ) {
        val blockEntity = world.getBlockEntity(pos) as? CopycatGearboxBlockEntity
        val material = blockEntity?.getSlotMaterial(CopycatGearboxBlockEntity.Slot.MAT_1) ?: AllBlocks.COPYCAT_BASE.defaultBlockState()
        val refs = collectMaterialReferences(world, pos, material, random)
        val templateParts = mutableListOf<BlockModelPart>()
        model.collectParts(random, templateParts)

        for (templatePart in templateParts) {
            val builder = QuadCollection.Builder()
            addPartRemapped(templatePart, builder, refs, state, material, world, pos)
            parts += SimpleModelWrapper(builder.build(), templatePart.useAmbientOcclusion(), templatePart.particleIcon())
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
    ) = Unit

    override fun particleSpriteWithInfo(world: BlockAndTintGetter, pos: BlockPos, state: BlockState): TextureAtlasSprite {
        val blockEntity = world.getBlockEntity(pos) as? CopycatGearboxBlockEntity
        val material = blockEntity?.getSlotMaterial(CopycatGearboxBlockEntity.Slot.MAT_1) ?: AllBlocks.COPYCAT_BASE.defaultBlockState()

        return getModelOf(material).particleIcon()
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
                if (refs.containsKey(direction))
                    continue

                val direct = part.getQuads(direction)
                if (direct.isNotEmpty()) {
                    refs[direction] = direct[0]
                    continue
                }

                for (quad in part.getQuads(null)) {
                    if (quad.direction() != direction)
                        continue

                    refs[direction] = quad
                    break
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
            val output = remapQuad(quad, refs[quad.direction()], state, material, world, pos)
            if (CopycatQuadCulling.isOnOuterBoundary(quad, quad.direction()))
                builder.addCulledFace(quad.direction(), output)
            else
                builder.addUnculledFace(output)
        }

        for (direction in Direction.entries) {
            for (quad in templatePart.getQuads(direction)) {
                val output = remapQuad(quad, refs[direction], state, material, world, pos)
                builder.addCulledFace(direction, output)
            }
        }
    }

    private fun remapQuad(
        quad: BakedQuad,
        reference: BakedQuad?,
        state: BlockState,
        material: BlockState,
        world: BlockAndTintGetter,
        pos: BlockPos
    ): BakedQuad {
        if (quad.sprite().contents().name() != COPYCAT_BASE)
            return quad

        return CopycatConnectedTextureHelper.remapQuad(quad, reference?.sprite(), reference, world, pos, state, material)
    }

    private companion object {
        val COPYCAT_BASE = Identifier.parse("create:block/copycat_base")
    }
}
