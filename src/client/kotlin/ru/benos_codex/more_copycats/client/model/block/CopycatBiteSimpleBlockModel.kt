package ru.benos_codex.more_copycats.client.model.block

import com.zurrtum.create.AllBlocks
import com.zurrtum.create.client.foundation.model.BakedModelHelper
import com.zurrtum.create.client.infrastructure.model.CopycatModel
import com.zurrtum.create.client.model.NormalsBakedQuad
import com.zurrtum.create.content.decoration.copycat.CopycatBlock
import net.minecraft.client.Minecraft
import net.minecraft.client.model.geom.builders.UVPair
import net.minecraft.client.renderer.block.model.BakedQuad
import net.minecraft.client.renderer.block.model.BlockModelPart
import net.minecraft.client.renderer.block.model.BlockStateModel
import net.minecraft.client.renderer.block.model.SimpleModelWrapper
import net.minecraft.client.resources.model.QuadCollection
import net.minecraft.client.renderer.texture.TextureAtlas
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.resources.Identifier
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.util.RandomSource
import net.minecraft.util.GsonHelper
import net.minecraft.world.level.BlockAndTintGetter
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Vector3fc
import com.zurrtum.create.client.catnip.render.SpriteShiftEntry.getUnInterpolatedU
import com.zurrtum.create.client.catnip.render.SpriteShiftEntry.getUnInterpolatedV
import ru.benos_codex.more_copycats.MoreCopycats.DEFAULT_MATERIAL
import ru.benos_codex.more_copycats.MoreCopycats.rl
import ru.benos_codex.more_copycats.block.entity.CopycatBiteBlockEntity
import kotlin.math.max
import kotlin.math.min
import kotlin.math.abs
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.concurrent.ConcurrentHashMap

class CopycatBiteSimpleBlockModel(state: BlockState, unbaked: BlockStateModel.UnbakedRoot) : CopycatModel(state, unbaked) {

    private fun renderFace(
        material: BlockState,
        be: CopycatBiteBlockEntity,
        index: Int,
        world: BlockAndTintGetter,
        pos: BlockPos,
        random: RandomSource,
        parts: MutableList<BlockModelPart>,
        box: AABB,
        sourceFace: Direction
    ) {
        val model = getModelOf(material)
        val region = RegionBounds(
            box.minX.toFloat(), box.maxX.toFloat(),
            box.minY.toFloat(), box.maxY.toFloat(),
            box.minZ.toFloat(), box.maxZ.toFloat()
        )

        for (part in getMaterialParts(world, pos, material, random, model)) {
            val builder = QuadCollection.Builder()
            var any = false

            for (quad in part.getQuads(sourceFace)) {
                val partQuad = BakedModelHelper.cropAndMove(quad, box, Vec3.ZERO)
                val margin = (getMarginFor(material) ?: DEFAULT_MARGIN).coerceAtLeast(1)
                for (q in slice9Quads(quad, partQuad, box, region, margin)) {
                    builder.addUnculledFace(q)
                    any = true
                }
            }

            if (any) {
                parts += SimpleModelWrapper(builder.build(), part.useAmbientOcclusion(), part.particleIcon())
            }
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
        val be = p0.getBlockEntity(p1) as? CopycatBiteBlockEntity ?: return
        if (be.isEmpty) return
        val base = AllBlocks.COPYCAT_BASE.defaultBlockState()

        for (i in 0 until CopycatBiteBlockEntity.MAX_ITEM) {
            if (be.isPartEmpty(i)) continue

            val ox = i and 3
            val oy = (i shr 2) and 3
            val oz = (i shr 4) and 3
            val box = AABB(
                ox * 0.25, oy * 0.25, oz * 0.25,
                (ox + 1) * 0.25, (oy + 1) * 0.25, (oz + 1) * 0.25
            )

            for (face in Direction.entries) {
                if (hasNeighbor(be, i, face)) continue
                val state = be.getFaceState(i, face) ?: continue
                val material = MaterialSlotDebug.materialForFace(i, state != DEFAULT_MATERIAL, face, if (state == DEFAULT_MATERIAL) base else state)
                renderFace(material, be, i, p0, p1, p5, p6, box, face)
            }
        }
    }

    private fun hasNeighbor(be: CopycatBiteBlockEntity, index: Int, face: Direction): Boolean {
        val x = index and 3
        val y = (index shr 2) and 3
        val z = (index shr 4) and 3
        val nx = x + face.stepX
        val ny = y + face.stepY
        val nz = z + face.stepZ
        if (nx !in 0..3 || ny !in 0..3 || nz !in 0..3) return false
        val nIndex = nx + (ny * 4) + (nz * 16)
        return !be.isPartEmpty(nIndex)
    }

    private enum class TexAxis { X, Y, Z }

    private fun coord(v: Vector3fc, axis: TexAxis): Float = when (axis) {
        TexAxis.X -> v.x()
        TexAxis.Y -> v.y()
        TexAxis.Z -> v.z()
    }

    private data class RegionBounds(
        val minX: Float,
        val maxX: Float,
        val minY: Float,
        val maxY: Float,
        val minZ: Float,
        val maxZ: Float
    )

    private fun slice9Quads(
        source: BakedQuad,
        partQuad: BakedQuad,
        box: AABB,
        region: RegionBounds,
        margin: Int
    ): List<BakedQuad> {
        val debugSprite = getDebugSprite()
        val pos = arrayOf(
            source.position0(), source.position1(), source.position2(), source.position3()
        )

        val eps = 1e-5f
        fun range(axis: TexAxis): Float {
            val c0 = coord(pos[0], axis)
            val c1 = coord(pos[1], axis)
            val c2 = coord(pos[2], axis)
            val c3 = coord(pos[3], axis)
            val mn = min(min(c0, c1), min(c2, c3))
            val mx = max(max(c0, c1), max(c2, c3))
            return mx - mn
        }

        val axes = TexAxis.entries.filter { range(it) > eps }
        if (axes.size != 2) {
            return listOf(partQuad)
        }

        val axisA = axes[0]
        val axisB = axes[1]

        val sprite = source.sprite()
        val packed = longArrayOf(source.packedUV0(), source.packedUV1(), source.packedUV2(), source.packedUV3())
        val uVals = FloatArray(4) { getUnInterpolatedU(sprite, UVPair.unpackU(packed[it])) }
        val vVals = FloatArray(4) { getUnInterpolatedV(sprite, UVPair.unpackV(packed[it])) }

        fun slope(axis: TexAxis, values: FloatArray): Float {
            var pMean = 0f
            var vMean = 0f
            for (i in 0..3) {
                pMean += coord(pos[i], axis)
                vMean += values[i]
            }
            pMean /= 4f
            vMean /= 4f

            var num = 0f
            var den = 0f
            for (i in 0..3) {
                val dp = coord(pos[i], axis) - pMean
                num += dp * (values[i] - vMean)
                den += dp * dp
            }
            return if (den > eps) num / den else 0f
        }

        val uSlopeA = slope(axisA, uVals)
        val uSlopeB = slope(axisB, uVals)
        val chooseAForU = abs(uSlopeA) >= abs(uSlopeB)
        val uAxis = if (chooseAForU) axisA else axisB
        val vAxis = if (chooseAForU) axisB else axisA
        val uFlip = slope(uAxis, uVals) < 0f
        val vFlip = slope(vAxis, vVals) < 0f

        fun axisMin(axis: TexAxis): Float = when (axis) {
            TexAxis.X -> region.minX
            TexAxis.Y -> region.minY
            TexAxis.Z -> region.minZ
        }
        fun axisMax(axis: TexAxis): Float = when (axis) {
            TexAxis.X -> region.maxX
            TexAxis.Y -> region.maxY
            TexAxis.Z -> region.maxZ
        }

        val uMin = axisMin(uAxis)
        val uMax = axisMax(uAxis)
        val vMin = axisMin(vAxis)
        val vMax = axisMax(vAxis)
        val texMargin = margin.coerceIn(1, 8).toFloat()
        val borderUM = min((uMax - uMin) * (texMargin / 16f), (uMax - uMin) / 2f)
        val borderVM = min((vMax - vMin) * (texMargin / 16f), (vMax - vMin) / 2f)

        val uCuts = floatArrayOf(uMin, uMin + borderUM, uMax - borderUM, uMax)
        val vCuts = floatArrayOf(vMin, vMin + borderVM, vMax - borderVM, vMax)

        fun makeBox(u0: Float, u1: Float, v0: Float, v1: Float): AABB {
            var minX = box.minX
            var minY = box.minY
            var minZ = box.minZ
            var maxX = box.maxX
            var maxY = box.maxY
            var maxZ = box.maxZ

            when (uAxis) {
                TexAxis.X -> { minX = u0.toDouble(); maxX = u1.toDouble() }
                TexAxis.Y -> { minY = u0.toDouble(); maxY = u1.toDouble() }
                TexAxis.Z -> { minZ = u0.toDouble(); maxZ = u1.toDouble() }
            }
            when (vAxis) {
                TexAxis.X -> { minX = v0.toDouble(); maxX = v1.toDouble() }
                TexAxis.Y -> { minY = v0.toDouble(); maxY = v1.toDouble() }
                TexAxis.Z -> { minZ = v0.toDouble(); maxZ = v1.toDouble() }
            }

            return AABB(minX, minY, minZ, maxX, maxY, maxZ)
        }

        val out = ArrayList<BakedQuad>(9)
        val texTotalU = (uMax - uMin) * PIXELS_PER_BLOCK
        val texTotalV = (vMax - vMin) * PIXELS_PER_BLOCK
        val texMarginU = min(texMargin * (uMax - uMin), texTotalU / 2f)
        val texMarginV = min(texMargin * (vMax - vMin), texTotalV / 2f)
        val uTexCuts = floatArrayOf(0f, texMarginU, texTotalU - texMarginU, texTotalU)
        val vTexCuts = floatArrayOf(0f, texMarginV, texTotalV - texMarginV, texTotalV)
        for (vi in 0..2) {
            for (ui in 0..2) {
                val u0m = uCuts[ui]
                val u1m = uCuts[ui + 1]
                val v0m = vCuts[vi]
                val v1m = vCuts[vi + 1]
                if (u1m - u0m <= eps || v1m - v0m <= eps) continue

                val sliceBox = makeBox(u0m, u1m, v0m, v1m)
                val cropped = BakedModelHelper.cropAndMove(partQuad, sliceBox, Vec3.ZERO)
                val ut = if (uFlip) 2 - ui else ui
                val vt = if (vFlip) 2 - vi else vi
                out.add(
                    remapQuadToSprite(
                        cropped,
                        debugSprite,
                        uAxis,
                        vAxis,
                        u0m,
                        u1m,
                        v0m,
                        v1m,
                        uTexCuts[ut],
                        uTexCuts[ut + 1],
                        vTexCuts[vt],
                        vTexCuts[vt + 1]
                    )
                )
            }
        }

        return out
    }

    private fun getMarginFor(material: BlockState): Int? {
        val blockId = BuiltInRegistries.BLOCK.getKey(material.block)
        val key = blockId.toString()
        MARGIN_CACHE[key]?.let { return it }

        val modelJson = loadJson("${blockId.namespace}:models/block/${blockId.path}.json".rl) ?: return null
        val parentStr = GsonHelper.getAsString(modelJson, "parent", null) ?: return null
        val parentLoc = Identifier.parse(parentStr)
        val metaLoc = "${parentLoc.namespace}:models/${parentLoc.path}.json.mcmeta".rl
        val metaJson = loadJson(metaLoc) ?: return null
        val margin = GsonHelper.getAsInt(metaJson, "margin", -1)
        if (margin <= 0) return null
        MARGIN_CACHE[key] = margin
        return margin
    }

    private fun loadJson(loc: Identifier): JsonObject? {
        val rm = Minecraft.getInstance().resourceManager
        val opt = rm.getResource(loc)
        if (opt.isEmpty) return null
        opt.get().openAsReader().use { reader ->
            return JsonParser.parseReader(reader).asJsonObject
        }
    }

    private fun getRegionBounds(be: CopycatBiteBlockEntity, index: Int, face: Direction): RegionBounds {
        if (hasNeighbor(be, index, face)) {
            val (x, y, z) = indexToXYZ(index)
            return RegionBounds(
                x * PART_SIZE, (x + 1) * PART_SIZE,
                y * PART_SIZE, (y + 1) * PART_SIZE,
                z * PART_SIZE, (z + 1) * PART_SIZE
            )
        }

        val visited = BooleanArray(GRID_SIZE * GRID_SIZE * GRID_SIZE)
        val queue = IntArray(visited.size)
        var head = 0
        var tail = 0
        queue[tail++] = index
        visited[index] = true

        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var minZ = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var maxY = Int.MIN_VALUE
        var maxZ = Int.MIN_VALUE

        val neighbors = when (face.axis) {
            Direction.Axis.X -> arrayOf(
                intArrayOf(0, 1, 0), intArrayOf(0, -1, 0),
                intArrayOf(0, 0, 1), intArrayOf(0, 0, -1)
            )
            Direction.Axis.Y -> arrayOf(
                intArrayOf(1, 0, 0), intArrayOf(-1, 0, 0),
                intArrayOf(0, 0, 1), intArrayOf(0, 0, -1)
            )
            Direction.Axis.Z -> arrayOf(
                intArrayOf(1, 0, 0), intArrayOf(-1, 0, 0),
                intArrayOf(0, 1, 0), intArrayOf(0, -1, 0)
            )
        }

        while (head < tail) {
            val i = queue[head++]
            val (x, y, z) = indexToXYZ(i)
            if (x < minX) minX = x
            if (y < minY) minY = y
            if (z < minZ) minZ = z
            if (x > maxX) maxX = x
            if (y > maxY) maxY = y
            if (z > maxZ) maxZ = z

            for (n in neighbors) {
                val nx = x + n[0]
                val ny = y + n[1]
                val nz = z + n[2]
                if (nx !in 0 until GRID_SIZE || ny !in 0 until GRID_SIZE || nz !in 0 until GRID_SIZE) continue
                val ni = xyzToIndex(nx, ny, nz)
                if (visited[ni]) continue
                if (be.isPartEmpty(ni)) continue
                if (hasNeighbor(be, ni, face)) continue
                visited[ni] = true
                queue[tail++] = ni
            }
        }

        return RegionBounds(
            minX * PART_SIZE, (maxX + 1) * PART_SIZE,
            minY * PART_SIZE, (maxY + 1) * PART_SIZE,
            minZ * PART_SIZE, (maxZ + 1) * PART_SIZE
        )
    }

    private data class XYZ(val x: Int, val y: Int, val z: Int)

    private fun indexToXYZ(index: Int): XYZ {
        val x = index and 3
        val y = (index shr 2) and 3
        val z = (index shr 4) and 3
        return XYZ(x, y, z)
    }

    private fun xyzToIndex(x: Int, y: Int, z: Int): Int = x + (y * GRID_SIZE) + (z * GRID_SIZE * GRID_SIZE)

    private companion object {
        val MARGIN_CACHE: ConcurrentHashMap<String, Int> = ConcurrentHashMap()
        private const val GRID_SIZE = 4
        private const val PART_SIZE = 1f / GRID_SIZE
        private val DEBUG_SPRITE_ID = "more_copycats:debug_sizing_tex".rl
        private const val DEFAULT_MARGIN = 4
        private const val PIXELS_PER_BLOCK = 4f
    }

    private fun getDebugSprite(): TextureAtlasSprite {
        val atlas = Minecraft.getInstance().textureManager.getTexture(TextureAtlas.LOCATION_BLOCKS) as TextureAtlas
        return atlas.getSprite(DEBUG_SPRITE_ID)
    }

    private fun remapQuadToSprite(
        quad: BakedQuad,
        sprite: TextureAtlasSprite,
        uAxis: TexAxis,
        vAxis: TexAxis,
        uWorldMin: Float,
        uWorldMax: Float,
        vWorldMin: Float,
        vWorldMax: Float,
        uTexMin: Float,
        uTexMax: Float,
        vTexMin: Float,
        vTexMax: Float
    ): BakedQuad {
        fun mapU(v: Vector3fc): Float {
            val denom = (uWorldMax - uWorldMin).coerceAtLeast(1e-6f)
            val t = ((coord(v, uAxis) - uWorldMin) / denom).coerceIn(0f, 1f)
            return uTexMin + (uTexMax - uTexMin) * t
        }
        fun mapV(v: Vector3fc): Float {
            val denom = (vWorldMax - vWorldMin).coerceAtLeast(1e-6f)
            val t = ((coord(v, vAxis) - vWorldMin) / denom).coerceIn(0f, 1f)
            return vTexMin + (vTexMax - vTexMin) * t
        }

        val p0 = UVPair.pack(sprite.getU(mapU(quad.position0())), sprite.getV(mapV(quad.position0())))
        val p1 = UVPair.pack(sprite.getU(mapU(quad.position1())), sprite.getV(mapV(quad.position1())))
        val p2 = UVPair.pack(sprite.getU(mapU(quad.position2())), sprite.getV(mapV(quad.position2())))
        val p3 = UVPair.pack(sprite.getU(mapU(quad.position3())), sprite.getV(mapV(quad.position3())))

        val newQuad = BakedQuad(
            quad.position0(),
            quad.position1(),
            quad.position2(),
            quad.position3(),
            p0, p1, p2, p3,
            quad.tintIndex(),
            quad.direction(),
            sprite,
            quad.shade(),
            quad.lightEmission()
        )
        NormalsBakedQuad.setNormals(newQuad, NormalsBakedQuad.getNormals(quad))
        return newQuad
    }
}
