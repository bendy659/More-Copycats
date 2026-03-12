package ru.benos_codex.more_copycats.client.model.block

import com.zurrtum.create.client.foundation.block.connected.AllCTTypes
import com.zurrtum.create.client.foundation.block.connected.CTSpriteShiftEntry
import com.zurrtum.create.client.foundation.block.connected.ConnectedTextureBehaviour
import com.zurrtum.create.client.foundation.model.BakedModelHelper
import com.zurrtum.create.client.infrastructure.model.CTModel
import com.zurrtum.create.client.infrastructure.model.WrapperBlockStateModel
import com.zurrtum.create.client.model.NormalsBakedQuad
import com.zurrtum.create.content.decoration.copycat.CopycatBlock
import net.minecraft.client.Minecraft
import net.minecraft.client.model.geom.builders.UVPair
import net.minecraft.client.renderer.block.model.BakedQuad
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.BlockAndTintGetter
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import org.joml.Vector3fc

object CopycatConnectedTextureHelper {
    private val ctMaterialCache = java.util.IdentityHashMap<BlockState, Boolean>()

    private object Behaviour : ConnectedTextureBehaviour.Base() {
        override fun getShift(state: BlockState, direction: Direction, sprite: TextureAtlasSprite?): CTSpriteShiftEntry? = null

        override fun getDataType(
            world: BlockAndTintGetter,
            pos: BlockPos,
            state: BlockState,
            direction: Direction
        ) = AllCTTypes.RECTANGLE

        override fun connectsTo(
            state: BlockState,
            other: BlockState,
            reader: BlockAndTintGetter,
            pos: BlockPos,
            otherPos: BlockPos,
            face: Direction,
            primaryOffset: Direction?,
            secondaryOffset: Direction?
        ): Boolean {
            val fromCopycat = reader.getBlockState(pos)
            val toCopycat = reader.getBlockState(otherPos)
            val fromBlock = fromCopycat.block
            val toBlock = toCopycat.block
            if (fromBlock is CopycatBlock && !fromBlock.canConnectTexturesToward(reader, pos, otherPos, fromCopycat)) return false
            if (toBlock is CopycatBlock && !toBlock.canConnectTexturesToward(reader, otherPos, pos, toCopycat)) return false

            // `state`/`other` are already appearance-resolved by CT behaviour (via getCTBlockState),
            // so multi-slot copycats (wall/fence/etc.) can provide per-face materials.
            if (state.isAir || other.isAir) return false
            return state.block == other.block
        }
    }

    fun remapQuad(
        quad: BakedQuad,
        targetSprite: TextureAtlasSprite?,
        referenceQuad: BakedQuad?,
        world: BlockAndTintGetter,
        pos: BlockPos,
        copycatState: BlockState,
        materialState: BlockState,
        useConnected: Boolean = true
    ): BakedQuad {
        if (copycatState.isAir) return quad

        if (!useConnected) {
            if (referenceQuad != null && shouldUseReferenceOrientation(materialState)) {
                return remapFromReference(quad, referenceQuad)
            }
            val sprite = referenceQuad?.sprite() ?: targetSprite ?: return quad
            return remapSprite(quad, sprite)
        }

        val hasCt = materialHasCtModel(materialState)
        if (hasCt && targetSprite != null && shouldUseConnected(world, pos, materialState, quad.direction())) {
            return remapWorld(quad, targetSprite)
        }

        if (referenceQuad != null && shouldUseReferenceOrientation(materialState)) {
            return remapFromReference(quad, referenceQuad)
        }

        val sprite = referenceQuad?.sprite() ?: targetSprite ?: return quad
        return remapSprite(quad, sprite)
    }

    private fun shouldUseReferenceOrientation(materialState: BlockState): Boolean {
        return materialState.hasProperty(BlockStateProperties.AXIS) ||
            materialState.hasProperty(BlockStateProperties.HORIZONTAL_AXIS)
    }

    private fun materialHasCtModel(materialState: BlockState): Boolean {
        ctMaterialCache[materialState]?.let { return it }

        val baked = Minecraft.getInstance().blockRenderer.getBlockModel(materialState)
        val unwrapped = WrapperBlockStateModel.unwrapCompat(baked)
        val result = unwrapped is CTModel
        ctMaterialCache[materialState] = result
        return result
    }

    private fun shouldUseConnected(world: BlockAndTintGetter, pos: BlockPos, state: BlockState, face: Direction): Boolean {
        val requirement = AllCTTypes.RECTANGLE.contextRequirement
        val c = Behaviour.buildContext(world, pos, state, face, requirement)
        return c.up || c.down || c.left || c.right
    }

    private fun remapSprite(quad: BakedQuad, targetSprite: TextureAtlasSprite): BakedQuad {
        if (quad.sprite() == targetSprite) return quad
        val original = quad.sprite()
        val out = BakedQuad(
            quad.position0(),
            quad.position1(),
            quad.position2(),
            quad.position3(),
            BakedModelHelper.calcSpriteUv(quad.packedUV0(), original, targetSprite),
            BakedModelHelper.calcSpriteUv(quad.packedUV1(), original, targetSprite),
            BakedModelHelper.calcSpriteUv(quad.packedUV2(), original, targetSprite),
            BakedModelHelper.calcSpriteUv(quad.packedUV3(), original, targetSprite),
            quad.tintIndex(),
            quad.direction(),
            targetSprite,
            quad.shade(),
            quad.lightEmission()
        )
        NormalsBakedQuad.setNormals(out, NormalsBakedQuad.getNormals(quad))
        return out
    }

    private fun remapWorld(quad: BakedQuad, sprite: TextureAtlasSprite): BakedQuad {
        val base = remapSprite(quad, sprite)

        fun mapVertex(v: Vector3fc): Long {
            val uv = faceLocalUv(base.direction(), v)
            return UVPair.pack(sprite.getU(uv.first), sprite.getV(uv.second))
        }

        val out = BakedQuad(
            base.position0(),
            base.position1(),
            base.position2(),
            base.position3(),
            mapVertex(base.position0()),
            mapVertex(base.position1()),
            mapVertex(base.position2()),
            mapVertex(base.position3()),
            base.tintIndex(),
            base.direction(),
            sprite,
            base.shade(),
            base.lightEmission()
        )
        NormalsBakedQuad.setNormals(out, NormalsBakedQuad.getNormals(base))
        return out
    }

    private fun remapFromReference(template: BakedQuad, reference: BakedQuad): BakedQuad {
        val dir = template.direction()
        val refPacked = longArrayOf(reference.packedUV0(), reference.packedUV1(), reference.packedUV2(), reference.packedUV3())
        val refPos = arrayOf(reference.position0(), reference.position1(), reference.position2(), reference.position3())
        val refSt = Array(4) { i -> faceLocalUv(dir, refPos[i]) }
        val templateSprite = template.sprite()

        fun nearest(targetS: Float, targetT: Float): Int {
            var best = 0
            var bestScore = Float.MAX_VALUE
            for (i in 0..3) {
                val ds = refSt[i].first - targetS
                val dt = refSt[i].second - targetT
                val score = ds * ds + dt * dt
                if (score < bestScore) {
                    bestScore = score
                    best = i
                }
            }
            return best
        }

        val i00 = nearest(0f, 0f)
        val i10 = nearest(1f, 0f)
        val i01 = nearest(0f, 1f)
        val i11 = nearest(1f, 1f)

        val u00 = UVPair.unpackU(refPacked[i00]); val v00 = UVPair.unpackV(refPacked[i00])
        val u10 = UVPair.unpackU(refPacked[i10]); val v10 = UVPair.unpackV(refPacked[i10])
        val u01 = UVPair.unpackU(refPacked[i01]); val v01 = UVPair.unpackV(refPacked[i01])
        val u11 = UVPair.unpackU(refPacked[i11]); val v11 = UVPair.unpackV(refPacked[i11])

        fun bilerp(a00: Float, a10: Float, a01: Float, a11: Float, s: Float, t: Float): Float {
            val a0 = a00 + (a10 - a00) * s
            val a1 = a01 + (a11 - a01) * s
            return a0 + (a1 - a0) * t
        }

        val du = (templateSprite.u1 - templateSprite.u0).takeIf { kotlin.math.abs(it) > 1e-6f } ?: 1f
        val dv = (templateSprite.v1 - templateSprite.v0).takeIf { kotlin.math.abs(it) > 1e-6f } ?: 1f

        fun map(packedUv: Long): Long {
            val rawU = UVPair.unpackU(packedUv)
            val rawV = UVPair.unpackV(packedUv)
            val s = ((rawU - templateSprite.u0) / du).coerceIn(0f, 1f)
            val t = ((rawV - templateSprite.v0) / dv).coerceIn(0f, 1f)
            return UVPair.pack(
                bilerp(u00, u10, u01, u11, s, t),
                bilerp(v00, v10, v01, v11, s, t)
            )
        }

        val out = BakedQuad(
            template.position0(),
            template.position1(),
            template.position2(),
            template.position3(),
            map(template.packedUV0()),
            map(template.packedUV1()),
            map(template.packedUV2()),
            map(template.packedUV3()),
            template.tintIndex(),
            template.direction(),
            reference.sprite(),
            template.shade(),
            template.lightEmission()
        )
        NormalsBakedQuad.setNormals(out, NormalsBakedQuad.getNormals(template))
        return out
    }

    private fun faceLocalUv(face: Direction, v: Vector3fc): Pair<Float, Float> {
        val x = v.x().coerceIn(0f, 1f)
        val y = v.y().coerceIn(0f, 1f)
        val z = v.z().coerceIn(0f, 1f)

        return when (face) {
            Direction.NORTH -> (1f - x) to (1f - y)
            Direction.SOUTH -> x to (1f - y)
            Direction.WEST -> z to (1f - y)
            Direction.EAST -> (1f - z) to (1f - y)
            Direction.UP -> x to z
            Direction.DOWN -> x to (1f - z)
        }
    }
}
