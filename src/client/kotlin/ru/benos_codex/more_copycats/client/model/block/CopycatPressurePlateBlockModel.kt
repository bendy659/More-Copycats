package ru.benos_codex.more_copycats.client.model.block

import com.zurrtum.create.AllBlocks
import com.zurrtum.create.client.infrastructure.model.CopycatModel
import com.zurrtum.create.client.model.NormalsBakedQuad
import com.zurrtum.create.content.decoration.copycat.CopycatBlock
import net.minecraft.client.Minecraft
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
import org.joml.Vector3f
import ru.benos_codex.more_copycats.block.CopycatPressurePlateBlock
import ru.benos_codex.more_copycats.block.entity.CopycatRedstoneBlockEntity

class CopycatPressurePlateBlockModel(state: BlockState, unbaked: BlockStateModel.UnbakedRoot) : CopycatModel(state, unbaked) {
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

        resolveProgress(world, pos, state)
        val dy = 0f

        for (templatePart in templateParts) {
            val builder = QuadCollection.Builder()
            addPartRemapped(templatePart, builder, refs, block, state, debugMaterial, world, pos, dy)
            parts += SimpleModelWrapper(
                builder.build(),
                MaterialSlotDebug.ambientOcclusion(templatePart.useAmbientOcclusion()),
                templatePart.particleIcon()
            )
        }
    }

    private fun resolveProgress(world: BlockAndTintGetter, pos: BlockPos, state: BlockState): Float {
        val partialTicks = Minecraft.getInstance().deltaTracker.getGameTimeDeltaPartialTick(true)
        val be = world.getBlockEntity(pos) as? CopycatRedstoneBlockEntity
        val value = be?.getAnimationProgress(partialTicks) ?: if (state.getValue(CopycatPressurePlateBlock.POWERED)) 1f else 0f
        return easeInOut(value)
    }

    private fun easeInOut(value: Float): Float {
        val x = value.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
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
        block: CopycatBlock,
        state: BlockState,
        material: BlockState,
        world: BlockAndTintGetter,
        pos: BlockPos,
        dy: Float
    ) {
        for (quad in templatePart.getQuads(null)) {
            val ref = refs[quad.direction()]
            val remapped = CopycatConnectedTextureHelper.remapQuad(quad, ref?.sprite(), ref, world, pos, state, material)
            builder.addUnculledFace(translateQuad(remapped, dy))
        }

        for (direction in Direction.entries) {
            for (quad in templatePart.getQuads(direction)) {
                val ref = refs[direction]
                val remapped = CopycatConnectedTextureHelper.remapQuad(quad, ref?.sprite(), ref, world, pos, state, material)
                val shifted = translateQuad(remapped, dy)
                if (block.shouldFaceAlwaysRender(state, direction)) builder.addUnculledFace(shifted)
                else builder.addCulledFace(direction, shifted)
            }
        }
    }

    private fun translateQuad(quad: BakedQuad, dy: Float): BakedQuad {
        if (dy == 0f) return quad

        val p0 = Vector3f(quad.position0()).add(0f, dy, 0f)
        val p1 = Vector3f(quad.position1()).add(0f, dy, 0f)
        val p2 = Vector3f(quad.position2()).add(0f, dy, 0f)
        val p3 = Vector3f(quad.position3()).add(0f, dy, 0f)

        val out = BakedQuad(
            p0,
            p1,
            p2,
            p3,
            quad.packedUV0(),
            quad.packedUV1(),
            quad.packedUV2(),
            quad.packedUV3(),
            quad.tintIndex(),
            quad.direction(),
            quad.sprite(),
            quad.shade(),
            quad.lightEmission()
        )
        NormalsBakedQuad.setNormals(out, NormalsBakedQuad.getNormals(quad))
        return out
    }
}
