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
import ru.benos_codex.more_copycats.block.CopycatEncasedShaftBlock
import ru.benos_codex.more_copycats.block.entity.CopycatShaftBlockEntity
import kotlin.math.abs

class CopycatShaftBlockModel(state: BlockState, unbaked: BlockStateModel.UnbakedRoot) : CopycatModel(state, unbaked) {
    override fun addPartsWithInfo(
        world: BlockAndTintGetter,
        pos: BlockPos,
        state: BlockState,
        random: RandomSource,
        parts: MutableList<BlockModelPart>
    ) {
        if (state.block is CopycatEncasedShaftBlock) {
            addPartsForSlot(world, pos, state, random, parts, CopycatShaftBlockEntity.Slot.MAT_1, true)
            return
        }

        addPartsForSlot(world, pos, state, random, parts, CopycatShaftBlockEntity.Slot.MAT_0, false)
    }

    fun addRotatingPartsWithInfo(
        world: BlockAndTintGetter,
        pos: BlockPos,
        state: BlockState,
        random: RandomSource,
        parts: MutableList<BlockModelPart>
    ) {
        val blockEntity = world.getBlockEntity(pos) as? CopycatShaftBlockEntity
        val material = blockEntity?.getSlotMaterial(CopycatShaftBlockEntity.Slot.MAT_0) ?: AllBlocks.COPYCAT_BASE.defaultBlockState()
        addPartsForMaterial(world, pos, state, material, random, parts, false, true)
    }

    fun addRotatingPartsWithMaterial(
        world: BlockAndTintGetter,
        pos: BlockPos,
        state: BlockState,
        material: BlockState,
        random: RandomSource,
        parts: MutableList<BlockModelPart>
    ) {
        addPartsForMaterial(world, pos, state, material, random, parts, false, true)
    }

    fun addRotatingHalfPartsWithMaterial(
        world: BlockAndTintGetter,
        pos: BlockPos,
        state: BlockState,
        material: BlockState,
        axis: Direction.Axis,
        positive: Boolean,
        random: RandomSource,
        parts: MutableList<BlockModelPart>
    ) {
        val refs = collectMaterialReferences(world, pos, material, random)
        val templateParts = mutableListOf<BlockModelPart>()
        model.collectParts(random, templateParts)

        for (templatePart in templateParts) {
            val builder = QuadCollection.Builder()
            addPartRemappedHalf(
                templatePart,
                builder,
                refs,
                state,
                material,
                world,
                pos,
                axis,
                positive
            )
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
    ) {
        // unused: shaft materials are stored on the block entity
    }

    override fun particleSpriteWithInfo(world: BlockAndTintGetter, pos: BlockPos, state: BlockState): TextureAtlasSprite {
        val blockEntity = world.getBlockEntity(pos) as? CopycatShaftBlockEntity
        val slot = if (state.block is CopycatEncasedShaftBlock)
            CopycatShaftBlockEntity.Slot.MAT_1
        else
            CopycatShaftBlockEntity.Slot.MAT_0
        val material = blockEntity?.getSlotMaterial(slot) ?: AllBlocks.COPYCAT_BASE.defaultBlockState()

        return getModelOf(material).particleIcon()
    }

    private fun addPartsForSlot(
        world: BlockAndTintGetter,
        pos: BlockPos,
        state: BlockState,
        random: RandomSource,
        parts: MutableList<BlockModelPart>,
        slot: CopycatShaftBlockEntity.Slot,
        remapCopycatBaseOnly: Boolean,
        forceUnculled: Boolean = false
    ) {
        val blockEntity = world.getBlockEntity(pos) as? CopycatShaftBlockEntity
        val material = blockEntity?.getSlotMaterial(slot) ?: AllBlocks.COPYCAT_BASE.defaultBlockState()
        addPartsForMaterial(world, pos, state, material, random, parts, remapCopycatBaseOnly, forceUnculled)
    }

    private fun addPartsForMaterial(
        world: BlockAndTintGetter,
        pos: BlockPos,
        state: BlockState,
        material: BlockState,
        random: RandomSource,
        parts: MutableList<BlockModelPart>,
        remapCopycatBaseOnly: Boolean,
        forceUnculled: Boolean = false
    ) {
        val refs = collectMaterialReferences(world, pos, material, random)
        val templateParts = mutableListOf<BlockModelPart>()
        model.collectParts(random, templateParts)

        for (templatePart in templateParts) {
            val builder = QuadCollection.Builder()
            addPartRemapped(templatePart, builder, refs, state, material, world, pos, remapCopycatBaseOnly, forceUnculled)
            parts += SimpleModelWrapper(builder.build(), templatePart.useAmbientOcclusion(), templatePart.particleIcon())
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
                if (refs.containsKey(direction))
                    continue

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
        pos: BlockPos,
        remapCopycatBaseOnly: Boolean,
        forceUnculled: Boolean
    ) {
        for (quad in templatePart.getQuads(null)) {
            val output = remapQuad(quad, refs[quad.direction()], state, material, world, pos, remapCopycatBaseOnly)
            if (!forceUnculled && CopycatQuadCulling.isOnOuterBoundary(quad, quad.direction()))
                builder.addCulledFace(quad.direction(), output)
            else
                builder.addUnculledFace(output)
        }

        for (direction in Direction.entries) {
            for (quad in templatePart.getQuads(direction)) {
                val output = remapQuad(quad, refs[direction], state, material, world, pos, remapCopycatBaseOnly)
                if (forceUnculled)
                    builder.addUnculledFace(output)
                else
                    builder.addCulledFace(direction, output)
            }
        }
    }

    private fun addPartRemappedHalf(
        templatePart: BlockModelPart,
        builder: QuadCollection.Builder,
        refs: Map<Direction, BakedQuad>,
        state: BlockState,
        material: BlockState,
        world: BlockAndTintGetter,
        pos: BlockPos,
        axis: Direction.Axis,
        positive: Boolean
    ) {
        for (quad in templatePart.getQuads(null)) {
            if (!matchesHalf(quad, axis, positive))
                continue
            val output = remapQuad(quad, refs[quad.direction()], state, material, world, pos, false)
            builder.addUnculledFace(output)
        }

        for (direction in Direction.entries) {
            for (quad in templatePart.getQuads(direction)) {
                if (!matchesHalf(quad, axis, positive))
                    continue
                val output = remapQuad(quad, refs[direction], state, material, world, pos, false)
                builder.addUnculledFace(output)
            }
        }
    }

    private fun matchesHalf(quad: BakedQuad, axis: Direction.Axis, positive: Boolean): Boolean {
        val cx = normalize((quad.position0().x() + quad.position1().x() + quad.position2().x() + quad.position3().x()) * 0.25f)
        val cy = normalize((quad.position0().y() + quad.position1().y() + quad.position2().y() + quad.position3().y()) * 0.25f)
        val cz = normalize((quad.position0().z() + quad.position1().z() + quad.position2().z() + quad.position3().z()) * 0.25f)

        val coord = when (axis) {
            Direction.Axis.X -> cx
            Direction.Axis.Y -> cy
            Direction.Axis.Z -> cz
        }

        return if (positive) coord >= 0.5001f else coord <= 0.4999f
    }

    private fun remapQuad(
        quad: BakedQuad,
        reference: BakedQuad?,
        state: BlockState,
        material: BlockState,
        world: BlockAndTintGetter,
        pos: BlockPos,
        remapCopycatBaseOnly: Boolean
    ): BakedQuad {
        if (remapCopycatBaseOnly && shouldKeepTemplateQuad(quad))
            return quad

        return CopycatConnectedTextureHelper.remapQuad(quad, reference?.sprite(), reference, world, pos, state, material)
    }

    private fun shouldKeepTemplateQuad(quad: BakedQuad): Boolean {
        val spriteId = quad.sprite().contents().name()

        return spriteId != COPYCAT_BASE
    }

    private companion object {
        val COPYCAT_BASE = Identifier.parse("create:block/copycat_base")
    }

    private fun normalize(value: Float): Float {
        return if (abs(value) > 2.0f) value / 16.0f else value
    }
}
