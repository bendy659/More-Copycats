package ru.benos_codex.more_copycats.client.model.block

import com.zurrtum.create.AllBlocks
import com.zurrtum.create.client.foundation.model.BakedModelHelper
import com.zurrtum.create.client.infrastructure.model.CopycatModel
import com.zurrtum.create.client.model.NormalsBakedQuad
import com.zurrtum.create.content.decoration.copycat.CopycatBlock
import net.minecraft.client.model.geom.builders.UVPair
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
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import ru.benos_codex.more_copycats.MoreCopycats.DEFAULT_MATERIAL
import ru.benos_codex.more_copycats.block.entity.CopycatByteBlockEntity
import com.zurrtum.create.client.catnip.render.SpriteShiftEntry.getUnInterpolatedU
import com.zurrtum.create.client.catnip.render.SpriteShiftEntry.getUnInterpolatedV

class CopycatByteBlockModel(state: BlockState, unbaked: BlockStateModel.UnbakedRoot): CopycatModel(state, unbaked) {

    private fun renderFace(
        material: BlockState,
        world: BlockAndTintGetter,
        pos: BlockPos,
        random: RandomSource,
        parts: MutableList<BlockModelPart>,
        box: AABB,
        face: Direction,
        sourceFace: Direction,
        uOffset: Float,
        vOffset: Float,
        w: Int,
        h: Int
    ) {
        val model = getModelOf(material)

        for (part in getMaterialParts(world, pos, material, random, model)) {
            val builder = QuadCollection.Builder()
            var any = false

            for (quad in part.getQuads(sourceFace)) {
                var q = BakedModelHelper.cropAndMove(quad, box, Vec3.ZERO)
                q = shiftUv(q, uOffset, vOffset, w, h)
                builder.addUnculledFace(q)
                any = true
            }

            if (any)
                parts += SimpleModelWrapper(builder.build(), part.useAmbientOcclusion(), part.particleIcon())
        }
    }

    override fun addPartsWithInfo(
        p0: BlockAndTintGetter,
        p1: BlockPos,
        p2: BlockState,
        p3: CopycatBlock,
        p4: BlockState,
        p5: RandomSource,
        p6: MutableList<BlockModelPart>
    ) {
        val blockEntity = p0.getBlockEntity(p1) as? CopycatByteBlockEntity
        val base = AllBlocks.COPYCAT_BASE.defaultBlockState()

        // blockEntity == null не должно происходить, но рендерим фолбэк на всякий случай
        if (blockEntity == null) return

        // Блок пустой — ничего не рендерим
        if (blockEntity.isEmpty) return

        val time = (p0 as? net.minecraft.world.level.Level)?.gameTime ?: 0L
        for (i in 0 until CopycatByteBlockEntity.MAX_ITEM) {
            if (!blockEntity.isPartEmpty(i)) {
                val ox = i and 1
                val oy = (i shr 1) and 1
                val oz = (i shr 2) and 1
                val box = AABB(
                    ox * 0.5, oy * 0.5, oz * 0.5,
                    (ox + 1) * 0.5, (oy + 1) * 0.5, (oz + 1) * 0.5
                )

                for (face in Direction.entries) {
                    val state = blockEntity.getFaceState(i, face) ?: continue
                    val material = if (state == DEFAULT_MATERIAL) base else state
                    val u = blockEntity.getUvUFloat(i, face, time)
                    val v = blockEntity.getUvVFloat(i, face, time)
                    val w = blockEntity.getUvW(i, face)
                    val h = blockEntity.getUvH(i, face)
                    val sourceFace = blockEntity.getSourceFace(i, face)
                    renderFace(material, p0, p1, p5, p6, box, face, sourceFace, u, v, w, h)
                }
            }
        }
    }

    private fun shiftUv(quad: BakedQuad, uOffset: Float, vOffset: Float, w: Int, h: Int): BakedQuad {
        val sprite = quad.sprite()
        val u0 = getUnInterpolatedU(sprite, UVPair.unpackU(quad.packedUV0()))
        val u1 = getUnInterpolatedU(sprite, UVPair.unpackU(quad.packedUV1()))
        val u2 = getUnInterpolatedU(sprite, UVPair.unpackU(quad.packedUV2()))
        val u3 = getUnInterpolatedU(sprite, UVPair.unpackU(quad.packedUV3()))
        val v0 = getUnInterpolatedV(sprite, UVPair.unpackV(quad.packedUV0()))
        val v1 = getUnInterpolatedV(sprite, UVPair.unpackV(quad.packedUV1()))
        val v2 = getUnInterpolatedV(sprite, UVPair.unpackV(quad.packedUV2()))
        val v3 = getUnInterpolatedV(sprite, UVPair.unpackV(quad.packedUV3()))
        val minU = minOf(u0, u1, u2, u3)
        val maxU = maxOf(u0, u1, u2, u3)
        val minV = minOf(v0, v1, v2, v3)
        val maxV = maxOf(v0, v1, v2, v3)
        val rangeU = (maxU - minU).coerceAtLeast(1e-6f)
        val rangeV = (maxV - minV).coerceAtLeast(1e-6f)

        fun shift(packed: Long): Long {
            val u = UVPair.unpackU(packed)
            val v = UVPair.unpackV(packed)
            val uu = getUnInterpolatedU(sprite, u)
            val vv = getUnInterpolatedV(sprite, v)
            val localU = (uu - minU) / rangeU
            val localV = (vv - minV) / rangeV
            val baseU = uOffset + localU * w
            val baseV = vOffset + localV * h
            val nu = sprite.getU(wrapPixel(baseU, sprite.contents().width().toFloat()))
            val nv = sprite.getV(wrapPixel(baseV, sprite.contents().height().toFloat()))
            return UVPair.pack(nu, nv)
        }

        val newQuad = BakedQuad(
            quad.position0(),
            quad.position1(),
            quad.position2(),
            quad.position3(),
            shift(quad.packedUV0()),
            shift(quad.packedUV1()),
            shift(quad.packedUV2()),
            shift(quad.packedUV3()),
            quad.tintIndex(),
            quad.direction(),
            quad.sprite(),
            quad.shade(),
            quad.lightEmission()
        )
        NormalsBakedQuad.setNormals(newQuad, NormalsBakedQuad.getNormals(quad))
        return newQuad
    }

    private fun wrapPixel(value: Float, size: Float): Float {
        if (size <= 0f) return 0f
        var m = value % size
        if (m < 0f) m += size
        return if (m == 0f && value != 0f) size else m
    }
}
