package ru.benos_codex.more_copycats.client.model.block

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
import net.minecraft.world.level.block.state.properties.AttachFace
import org.joml.Vector3f
import ru.benos_codex.more_copycats.block.CopycatButtonBlock
import ru.benos_codex.more_copycats.block.entity.CopycatRedstoneBlockEntity

class CopycatButtonBlockModel(state: BlockState, unbaked: BlockStateModel.UnbakedRoot) : CopycatModel(state, unbaked) {
    override fun addPartsWithInfo(
        world: BlockAndTintGetter,
        pos: BlockPos,
        state: BlockState,
        block: CopycatBlock,
        material: BlockState,
        random: RandomSource,
        parts: MutableList<BlockModelPart>
    ) {
        val refs = collectMaterialReferences(world, pos, material, random)
        val templateParts = mutableListOf<BlockModelPart>()
        model.collectParts(random, templateParts)

        val progress = resolveProgress(world, pos, state)
        val (dx, dy, dz) = offsetForState(state, progress)

        for (templatePart in templateParts) {
            val builder = QuadCollection.Builder()
            addPartRemapped(templatePart, builder, refs, block, state, material, world, pos, dx, dy, dz)
            parts += SimpleModelWrapper(builder.build(), templatePart.useAmbientOcclusion(), templatePart.particleIcon())
        }
    }

    private fun resolveProgress(world: BlockAndTintGetter, pos: BlockPos, state: BlockState): Float {
        val partialTicks = Minecraft.getInstance().deltaTracker.getGameTimeDeltaPartialTick(true)
        val be = world.getBlockEntity(pos) as? CopycatRedstoneBlockEntity
        val value = be?.getAnimationProgress(partialTicks) ?: if (state.getValue(CopycatButtonBlock.POWERED)) 1f else 0f
        return easeInOut(value)
    }

    private fun offsetForState(state: BlockState, progress: Float): Triple<Float, Float, Float> {
        val distance = 0f
        val dir = when (state.getValue(CopycatButtonBlock.FACE)) {
            AttachFace.FLOOR -> Direction.DOWN
            AttachFace.CEILING -> Direction.UP
            AttachFace.WALL -> state.getValue(CopycatButtonBlock.FACING).opposite
        }
        return Triple(dir.stepX * distance, dir.stepY * distance, dir.stepZ * distance)
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
        dx: Float,
        dy: Float,
        dz: Float
    ) {
        for (quad in templatePart.getQuads(null)) {
            val ref = refs[quad.direction()]
            val remapped = CopycatConnectedTextureHelper.remapQuad(quad, ref?.sprite(), ref, world, pos, state, material)
            builder.addUnculledFace(translateQuad(remapped, dx, dy, dz))
        }

        for (direction in Direction.entries) {
            for (quad in templatePart.getQuads(direction)) {
                val ref = refs[direction]
                val remapped = CopycatConnectedTextureHelper.remapQuad(quad, ref?.sprite(), ref, world, pos, state, material)
                val shifted = translateQuad(remapped, dx, dy, dz)
                if (block.shouldFaceAlwaysRender(state, direction)) builder.addUnculledFace(shifted)
                else builder.addCulledFace(direction, shifted)
            }
        }
    }

    private fun translateQuad(quad: BakedQuad, dx: Float, dy: Float, dz: Float): BakedQuad {
        if (dx == 0f && dy == 0f && dz == 0f) return quad

        val p0 = Vector3f(quad.position0()).add(dx, dy, dz)
        val p1 = Vector3f(quad.position1()).add(dx, dy, dz)
        val p2 = Vector3f(quad.position2()).add(dx, dy, dz)
        val p3 = Vector3f(quad.position3()).add(dx, dy, dz)

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
