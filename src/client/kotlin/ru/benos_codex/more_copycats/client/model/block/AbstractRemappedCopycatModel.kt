package ru.benos_codex.more_copycats.client.model.block

import com.zurrtum.create.AllBlocks
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

abstract class AbstractRemappedCopycatModel(state: BlockState, unbaked: BlockStateModel.UnbakedRoot) :
    CopycatModel(state, unbaked) {

    override fun addPartsWithInfo(
        world: BlockAndTintGetter,
        pos: BlockPos,
        state: BlockState,
        block: CopycatBlock,
        material: BlockState,
        random: RandomSource,
        parts: MutableList<BlockModelPart>
    ) {
        val hasCustom = !material.`is`(AllBlocks.COPYCAT_BASE)
        val debugMaterial = MaterialSlotDebug.single(material)
        val refs = MaterialSlotDebug.references(0, hasCustom) {
            collectMaterialReferences(world, pos, it, random)
        } ?: collectMaterialReferences(world, pos, debugMaterial, random)
        val templateParts = mutableListOf<BlockModelPart>()
        model.collectParts(random, templateParts)

        for (templatePart in templateParts) {
            val builder = QuadCollection.Builder()
            addPartRemapped(templatePart, builder, refs, block, state, debugMaterial, world, pos)
            parts += SimpleModelWrapper(
                builder.build(),
                MaterialSlotDebug.ambientOcclusion(templatePart.useAmbientOcclusion()),
                templatePart.particleIcon()
            )
        }
    }

    protected fun collectMaterialReferences(
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

    protected fun addPartRemapped(
        templatePart: BlockModelPart,
        builder: QuadCollection.Builder,
        refs: Map<Direction, BakedQuad>,
        block: CopycatBlock,
        state: BlockState,
        material: BlockState,
        world: BlockAndTintGetter,
        pos: BlockPos,
        useConnected: Boolean = true
    ) {
        for (quad in templatePart.getQuads(null)) {
            val ref = refs[quad.direction()]
            val remapped = CopycatConnectedTextureHelper.remapQuad(
                quad,
                ref?.sprite(),
                ref,
                world,
                pos,
                state,
                material,
                useConnected
            )
            if (CopycatQuadCulling.isOnOuterBoundary(quad, quad.direction()))
                builder.addCulledFace(quad.direction(), remapped)
            else
                builder.addUnculledFace(remapped)
        }

        for (direction in Direction.entries) {
            for (quad in templatePart.getQuads(direction)) {
                val ref = refs[direction]
                val remapped = CopycatConnectedTextureHelper.remapQuad(
                    quad,
                    ref?.sprite(),
                    ref,
                    world,
                    pos,
                    state,
                    material,
                    useConnected
                )
                if (block.shouldFaceAlwaysRender(state, direction)) builder.addUnculledFace(remapped)
                else builder.addCulledFace(direction, remapped)
            }
        }
    }
}
