package ru.benos_codex.more_copycats.client.model.block

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.zurrtum.create.client.catnip.render.SpriteShiftEntry.getUnInterpolatedU
import com.zurrtum.create.client.catnip.render.SpriteShiftEntry.getUnInterpolatedV
import net.minecraft.client.Minecraft
import net.minecraft.client.model.geom.builders.UVPair
import net.minecraft.client.renderer.block.model.BakedQuad
import net.minecraft.client.renderer.block.model.BlockModelPart
import net.minecraft.core.Direction
import net.minecraft.resources.Identifier
import net.minecraft.util.GsonHelper
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * Resolves material slot from model json.
 *
 * Priority:
 * 1) Groups named `mat_*`
 * 2) Texture aliases `mat_*`
 * 3) Fallback to `mat_0` for the whole model
 */
object CopycatMatGroupResolver {
    private val MAT_NAME_REGEX = Regex("^mat_(\\d+)$", RegexOption.IGNORE_CASE)
    private const val EPS = 1.0e-4f
    private const val PLANE_MAX_DISTANCE = 0.14f
    private const val STRICT_PLANE_MAX_DISTANCE = 0.03f
    private const val MAX_SCORE = 4.0f
    private const val STRICT_MAX_SCORE = 0.45f

    @Volatile
    private var resourceManagerIdentity: Int = -1
    private val modelCache = ConcurrentHashMap<String, List<FaceBinding>>()

    fun forModelIds(
        modelIds: Collection<String>,
        rotateByY: Boolean = true,
        rotateByAllAxes: Boolean = false
    ): Matcher? {
        if (modelIds.isEmpty()) return null
        val ids = modelIds.mapNotNull { runCatching { Identifier.parse(it) }.getOrNull() }
        if (ids.isEmpty()) return null
        return forModels(ids, rotateByY, rotateByAllAxes)
    }

    fun forModels(
        modelIds: Collection<Identifier>,
        rotateByY: Boolean = true,
        rotateByAllAxes: Boolean = false
    ): Matcher? {
        ensureCacheForCurrentResourceManager()

        val faces = buildList {
            for (modelId in modelIds) {
                addAll(loadFaceBindings(modelId, rotateByY, rotateByAllAxes))
            }
        }

        return if (faces.isEmpty()) null else Matcher(faces)
    }

    class Matcher internal constructor(private val faces: List<FaceBinding>) {
        fun slotForHit(localX: Double, localY: Double, localZ: Double, hitFace: Direction): Int? {
            val x = normalize(localX.toFloat())
            val y = normalize(localY.toFloat())
            val z = normalize(localZ.toFloat())

            var best: FaceBinding? = null
            var bestScore = Float.POSITIVE_INFINITY

            for (face in faces) {
                if (face.direction != hitFace) continue
                val planeDelta = abs(planeCoord(x, y, z, face.direction) - face.planeCoord)
                if (planeDelta > PLANE_MAX_DISTANCE) continue

                val (a, b) = faceAxesValues(x, y, z, face.direction)
                val da = axisDistance(a, face.aMin, face.aMax)
                val db = axisDistance(b, face.bMin, face.bMax)
                val score = planeDelta * 8.0f + da + db
                if (score < bestScore) {
                    bestScore = score
                    best = face
                }
            }

            if (best == null || bestScore > MAX_SCORE) return null
            return best.slot
        }

        fun slotForPart(part: BlockModelPart): Int? {
            val hits = mutableListOf<Int>()
            for (quad in part.getQuads(null)) {
                slotForQuad(quad)?.let(hits::add)
            }
            for (direction in Direction.entries) {
                for (quad in part.getQuads(direction)) {
                    slotForQuad(quad)?.let(hits::add)
                }
            }
            if (hits.isEmpty()) return null

            val counts = hits.groupingBy { it }.eachCount()
            val winner = counts.maxByOrNull { it.value } ?: return null
            val ties = counts.count { it.value == winner.value }
            return if (ties == 1) winner.key else null
        }

        fun slotForPartStrict(part: BlockModelPart): Int? {
            val hits = mutableListOf<Int>()
            for (quad in part.getQuads(null)) {
                slotForQuadStrict(quad)?.let(hits::add)
            }
            for (direction in Direction.entries) {
                for (quad in part.getQuads(direction)) {
                    slotForQuadStrict(quad)?.let(hits::add)
                }
            }
            if (hits.isEmpty()) return null

            val counts = hits.groupingBy { it }.eachCount()
            val winner = counts.maxByOrNull { it.value } ?: return null
            val ties = counts.count { it.value == winner.value }
            return if (ties == 1) winner.key else null
        }

        fun strictMatchCount(part: BlockModelPart): Int = matchCountInternal(part, strict = true)

        fun matchCount(part: BlockModelPart): Int = matchCountInternal(part, strict = false)

        private fun matchCountInternal(part: BlockModelPart, strict: Boolean): Int {
            var hits = 0
            for (quad in part.getQuads(null)) {
                val slot = if (strict) slotForQuadStrict(quad) else slotForQuad(quad)
                if (slot != null) hits++
            }
            for (direction in Direction.entries) {
                for (quad in part.getQuads(direction)) {
                    val slot = if (strict) slotForQuadStrict(quad) else slotForQuad(quad)
                    if (slot != null) hits++
                }
            }
            return hits
        }

        fun slotForQuad(quad: BakedQuad): Int? = slotForQuadInternal(quad, allowUvFallback = true)

        fun slotForQuadStrict(quad: BakedQuad): Int? = slotForQuadInternal(quad, allowUvFallback = false)

        private fun slotForQuadInternal(quad: BakedQuad, allowUvFallback: Boolean): Int? {
            val cx = normalize((quad.position0().x() + quad.position1().x() + quad.position2().x() + quad.position3().x()) * 0.25f)
            val cy = normalize((quad.position0().y() + quad.position1().y() + quad.position2().y() + quad.position3().y()) * 0.25f)
            val cz = normalize((quad.position0().z() + quad.position1().z() + quad.position2().z() + quad.position3().z()) * 0.25f)
            val quadUv = quadUvRect(quad)
            val planeThreshold = if (allowUvFallback) PLANE_MAX_DISTANCE else STRICT_PLANE_MAX_DISTANCE
            val maxScore = if (allowUvFallback) MAX_SCORE else STRICT_MAX_SCORE

            var best: FaceBinding? = null
            var bestScore = Float.POSITIVE_INFINITY
            var bestUvOnly: FaceBinding? = null
            var bestUvOnlyScore = Float.POSITIVE_INFINITY

            for (face in faces) {
                if (face.direction != quad.direction()) continue

                val uvOnlyScore = face.uv?.let { rectDistance(quadUv, it) / 16.0f } ?: 0.0f
                if (uvOnlyScore < bestUvOnlyScore) {
                    bestUvOnlyScore = uvOnlyScore
                    bestUvOnly = face
                }

                val planeDelta = abs(planeCoord(cx, cy, cz, face.direction) - face.planeCoord)
                if (planeDelta > planeThreshold) continue

                val (a, b) = faceAxesValues(cx, cy, cz, face.direction)
                val da = axisDistance(a, face.aMin, face.aMax)
                val db = axisDistance(b, face.bMin, face.bMax)
                val posScore = planeDelta * 8.0f + da + db

                val uvScore = uvOnlyScore
                val score = posScore + uvScore
                if (score < bestScore) {
                    bestScore = score
                    best = face
                }
            }

            if (best == null || bestScore > maxScore) {
                // Fallback for rotated kinetic/static models where geometric plane checks don't match
                // baked orientation, but UV layout is still stable per material slot.
                if (allowUvFallback && bestUvOnly != null && bestUvOnlyScore <= 0.5f) {
                    return bestUvOnly.slot
                }
                return null
            }
            return best.slot
        }
    }

    private fun ensureCacheForCurrentResourceManager() {
        val identity = System.identityHashCode(Minecraft.getInstance().resourceManager)
        if (identity != resourceManagerIdentity) {
            modelCache.clear()
            resourceManagerIdentity = identity
        }
    }

    private fun loadFaceBindings(modelId: Identifier, rotateByY: Boolean, rotateByAllAxes: Boolean): List<FaceBinding> {
        val key = "${modelId}#rotY=${if (rotateByY) 1 else 0}#rotXYZ=${if (rotateByAllAxes) 1 else 0}"
        modelCache[key]?.let { return it }

        val jsonId = Identifier.parse("${modelId.namespace}:models/${modelId.path}.json")
        val root = loadJson(jsonId)
        if (root == null) {
            modelCache[key] = emptyList()
            return emptyList()
        }

        val parsed = parseFaceBindings(modelId, root, rotateByY, rotateByAllAxes)
        modelCache[key] = parsed
        return parsed
    }

    private fun loadJson(location: Identifier): JsonObject? {
        val resource = Minecraft.getInstance().resourceManager.getResource(location)
        if (resource.isEmpty) return null
        resource.get().openAsReader().use { reader ->
            return runCatching { JsonParser.parseReader(reader).asJsonObject }.getOrNull()
        }
    }

    private fun parseFaceBindings(modelId: Identifier, root: JsonObject, rotateByY: Boolean, rotateByAllAxes: Boolean): List<FaceBinding> {
        val textures = root.getAsJsonObject("textures")
        val elements = root.getAsJsonArray("elements") ?: return emptyList()
        val refs = buildElementReferenceIndex(root, elements)
        val legacySlots = buildLegacyGroupSlots(refs.byGroupName)
        val out = mutableListOf<FaceBinding>()

        for (i in 0 until elements.size()) {
            val element = elements[i].takeIf { it.isJsonObject }?.asJsonObject ?: continue
            val box = readElementBox(element) ?: continue
            val facesObj = element.getAsJsonObject("faces") ?: continue
            val explicitSlot = legacySlots[i]

            parseFace(facesObj, "north", box, textures, explicitSlot)?.let {
                out += maybeRotateBindings(it, rotateByY, rotateByAllAxes)
            }
            parseFace(facesObj, "south", box, textures, explicitSlot)?.let {
                out += maybeRotateBindings(it, rotateByY, rotateByAllAxes)
            }
            parseFace(facesObj, "east", box, textures, explicitSlot)?.let {
                out += maybeRotateBindings(it, rotateByY, rotateByAllAxes)
            }
            parseFace(facesObj, "west", box, textures, explicitSlot)?.let {
                out += maybeRotateBindings(it, rotateByY, rotateByAllAxes)
            }
            parseFace(facesObj, "up", box, textures, explicitSlot)?.let {
                out += maybeRotateBindings(it, rotateByY, rotateByAllAxes)
            }
            parseFace(facesObj, "down", box, textures, explicitSlot)?.let {
                out += maybeRotateBindings(it, rotateByY, rotateByAllAxes)
            }
        }

        return out
    }

    private fun parseFace(
        facesObj: JsonObject,
        faceName: String,
        box: Box,
        textures: JsonObject?,
        explicitSlot: Int?
    ): FaceBinding? {
        val faceJson = facesObj.getAsJsonObject(faceName) ?: return null
        val direction = parseDirection(faceName) ?: return null
        val textureRef = GsonHelper.getAsString(faceJson, "texture", null) ?: return null
        // Fallback contract: if model has no explicit mat mapping, treat the whole model as mat_0.
        val slot = explicitSlot ?: resolveTextureSlot(textureRef, textures) ?: 0
        val uv = parseUv(faceJson.getAsJsonArray("uv"))
        return makeBinding(direction, box, slot, uv)
    }

    private fun resolveTextureSlot(textureRef: String, textures: JsonObject?): Int? {
        val direct = textureRef.removePrefix("#")
        parseMatSlot(direct)?.let { return it }

        if (textures == null) return null
        var key = direct
        val visited = HashSet<String>()
        while (visited.add(key)) {
            val value = GsonHelper.getAsString(textures, key, null) ?: return null
            val normalized = value.removePrefix("#")
            parseMatSlot(normalized)?.let { return it }
            if (!value.startsWith("#")) return null
            key = normalized
        }

        return null
    }

    private fun parseMatSlot(name: String): Int? {
        val match = MAT_NAME_REGEX.matchEntire(name.trim()) ?: return null
        return match.groupValues[1].toIntOrNull()
    }

    private fun buildElementReferenceIndex(root: JsonObject, elements: JsonArray): ElementReferenceIndex {
        val byGroupName = mutableMapOf<String, MutableSet<Int>>()
        val groups = root.getAsJsonArray("groups")
        if (groups != null) {
            for (entry in groups) {
                if (!entry.isJsonObject) continue
                collectGroupElements(entry.asJsonObject, byGroupName, elements.size())
            }
        }

        return ElementReferenceIndex(byGroupName = byGroupName.mapValues { it.value.toSet() })
    }

    private fun collectGroupElements(
        group: JsonObject,
        byGroupName: MutableMap<String, MutableSet<Int>>,
        elementCount: Int
    ): Set<Int> {
        val indices = linkedSetOf<Int>()
        val children = group.getAsJsonArray("children")
        if (children != null) {
            for (child in children) {
                when {
                    child.isJsonObject -> {
                        indices.addAll(collectGroupElements(child.asJsonObject, byGroupName, elementCount))
                    }

                    child.isJsonPrimitive -> {
                        val primitive = child.asJsonPrimitive
                        val index = when {
                            primitive.isNumber -> primitive.asInt
                            primitive.isString -> primitive.asString.toIntOrNull()
                            else -> null
                        }
                        if (index != null && index in 0 until elementCount) {
                            indices.add(index)
                        }
                    }
                }
            }
        }

        val name = GsonHelper.getAsString(group, "name", null)?.trim().orEmpty()
        if (name.isNotEmpty()) {
            byGroupName.getOrPut(normalizeName(name)) { linkedSetOf() }.addAll(indices)
        }
        return indices
    }

    private fun buildLegacyGroupSlots(byGroupName: Map<String, Set<Int>>): Map<Int, Int> {
        val byElement = mutableMapOf<Int, Int>()
        for ((groupName, elementIndices) in byGroupName) {
            val slot = parseMatSlot(groupName) ?: continue
            for (index in elementIndices) {
                byElement[index] = slot
            }
        }
        return byElement
    }

    private fun normalizeName(name: String): String = name.trim().lowercase()

    private fun parseUv(uvArray: JsonArray?): UvRect? {
        if (uvArray == null || uvArray.size() < 4) return null
        val u0 = asFloat(uvArray[0]) ?: return null
        val v0 = asFloat(uvArray[1]) ?: return null
        val u1 = asFloat(uvArray[2]) ?: return null
        val v1 = asFloat(uvArray[3]) ?: return null
        return UvRect(minOf(u0, u1), maxOf(u0, u1), minOf(v0, v1), maxOf(v0, v1))
    }

    private fun readElementBox(element: JsonObject): Box? {
        val from = readVec3(element.getAsJsonArray("from")) ?: return null
        val to = readVec3(element.getAsJsonArray("to")) ?: return null
        return Box(
            minX = minOf(from[0], to[0]) / 16.0f,
            minY = minOf(from[1], to[1]) / 16.0f,
            minZ = minOf(from[2], to[2]) / 16.0f,
            maxX = maxOf(from[0], to[0]) / 16.0f,
            maxY = maxOf(from[1], to[1]) / 16.0f,
            maxZ = maxOf(from[2], to[2]) / 16.0f
        )
    }

    private fun readVec3(array: JsonArray?): FloatArray? {
        if (array == null || array.size() < 3) return null
        val x = asFloat(array[0]) ?: return null
        val y = asFloat(array[1]) ?: return null
        val z = asFloat(array[2]) ?: return null
        return floatArrayOf(x, y, z)
    }

    private fun asFloat(element: JsonElement?): Float? {
        if (element == null || !element.isJsonPrimitive || !element.asJsonPrimitive.isNumber) return null
        return element.asFloat
    }

    private fun parseDirection(name: String): Direction? = when (name.lowercase()) {
        "north" -> Direction.NORTH
        "south" -> Direction.SOUTH
        "east" -> Direction.EAST
        "west" -> Direction.WEST
        "up" -> Direction.UP
        "down" -> Direction.DOWN
        else -> null
    }

    private fun maybeRotateBindings(base: FaceBinding, rotateByY: Boolean, rotateByAllAxes: Boolean): List<FaceBinding> {
        if (!rotateByY && !rotateByAllAxes) return listOf(base)
        if (rotateByAllAxes) {
            val out = ArrayList<FaceBinding>(16)
            for (rx in 0..3) {
                val boxX = rotateAroundX(base.box, rx)
                val dirX = rotateDirectionX(base.direction, rx)
                for (ry in 0..3) {
                    val boxXY = rotateAroundY(boxX, ry)
                    val dirXY = rotateDirectionY(dirX, ry)
                    out += makeBinding(dirXY, boxXY, base.slot, base.uv)
                }
            }
            return out
        }
        val out = ArrayList<FaceBinding>(4)
        for (turn in 0..3) {
            val rotatedBox = rotateAroundY(base.box, turn)
            val rotatedDirection = rotateDirectionY(base.direction, turn)
            out += makeBinding(rotatedDirection, rotatedBox, base.slot, base.uv)
        }
        return out
    }

    private fun rotateDirectionY(direction: Direction, turns: Int): Direction {
        var result = direction
        repeat(((turns % 4) + 4) % 4) {
            result = when (result) {
                Direction.NORTH -> Direction.EAST
                Direction.EAST -> Direction.SOUTH
                Direction.SOUTH -> Direction.WEST
                Direction.WEST -> Direction.NORTH
                else -> result
            }
        }
        return result
    }

    private fun rotateDirectionX(direction: Direction, turns: Int): Direction {
        var result = direction
        repeat(((turns % 4) + 4) % 4) {
            result = when (result) {
                Direction.UP -> Direction.SOUTH
                Direction.SOUTH -> Direction.DOWN
                Direction.DOWN -> Direction.NORTH
                Direction.NORTH -> Direction.UP
                else -> result
            }
        }
        return result
    }

    private fun rotateAroundY(box: Box, quarterTurns: Int): Box {
        val turns = ((quarterTurns % 4) + 4) % 4
        if (turns == 0) return box

        var minX = Float.POSITIVE_INFINITY
        var minZ = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxZ = Float.NEGATIVE_INFINITY

        val xs = floatArrayOf(box.minX, box.maxX)
        val zs = floatArrayOf(box.minZ, box.maxZ)
        for (x in xs) {
            for (z in zs) {
                val (rx, rz) = when (turns) {
                    1 -> 1.0f - z to x
                    2 -> 1.0f - x to 1.0f - z
                    3 -> z to 1.0f - x
                    else -> x to z
                }
                minX = minOf(minX, rx)
                minZ = minOf(minZ, rz)
                maxX = maxOf(maxX, rx)
                maxZ = maxOf(maxZ, rz)
            }
        }

        return Box(minX, box.minY, minZ, maxX, box.maxY, maxZ)
    }

    private fun rotateAroundX(box: Box, quarterTurns: Int): Box {
        val turns = ((quarterTurns % 4) + 4) % 4
        if (turns == 0) return box

        var minY = Float.POSITIVE_INFINITY
        var minZ = Float.POSITIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        var maxZ = Float.NEGATIVE_INFINITY

        val ys = floatArrayOf(box.minY, box.maxY)
        val zs = floatArrayOf(box.minZ, box.maxZ)
        for (y in ys) {
            for (z in zs) {
                val (ry, rz) = when (turns) {
                    1 -> 1.0f - z to y
                    2 -> 1.0f - y to 1.0f - z
                    3 -> z to 1.0f - y
                    else -> y to z
                }
                minY = minOf(minY, ry)
                minZ = minOf(minZ, rz)
                maxY = maxOf(maxY, ry)
                maxZ = maxOf(maxZ, rz)
            }
        }

        return Box(box.minX, minY, minZ, box.maxX, maxY, maxZ)
    }

    private fun makeBinding(direction: Direction, box: Box, slot: Int, uv: UvRect?): FaceBinding {
        return when (direction) {
            Direction.NORTH -> FaceBinding(direction, slot, box, box.minZ, box.minX, box.maxX, box.minY, box.maxY, uv)
            Direction.SOUTH -> FaceBinding(direction, slot, box, box.maxZ, box.minX, box.maxX, box.minY, box.maxY, uv)
            Direction.WEST -> FaceBinding(direction, slot, box, box.minX, box.minZ, box.maxZ, box.minY, box.maxY, uv)
            Direction.EAST -> FaceBinding(direction, slot, box, box.maxX, box.minZ, box.maxZ, box.minY, box.maxY, uv)
            Direction.DOWN -> FaceBinding(direction, slot, box, box.minY, box.minX, box.maxX, box.minZ, box.maxZ, uv)
            Direction.UP -> FaceBinding(direction, slot, box, box.maxY, box.minX, box.maxX, box.minZ, box.maxZ, uv)
        }
    }

    private fun normalize(v: Float): Float = if (abs(v) > 2.0f) v / 16.0f else v

    private fun planeCoord(x: Float, y: Float, z: Float, direction: Direction): Float = when (direction) {
        Direction.NORTH, Direction.SOUTH -> z
        Direction.WEST, Direction.EAST -> x
        Direction.DOWN, Direction.UP -> y
    }

    private fun faceAxesValues(x: Float, y: Float, z: Float, direction: Direction): Pair<Float, Float> = when (direction) {
        Direction.NORTH, Direction.SOUTH -> x to y
        Direction.WEST, Direction.EAST -> z to y
        Direction.DOWN, Direction.UP -> x to z
    }

    private fun axisDistance(value: Float, min: Float, max: Float): Float {
        return when {
            value < min - EPS -> (min - value)
            value > max + EPS -> (value - max)
            else -> 0.0f
        }
    }

    private fun quadUvRect(quad: BakedQuad): UvRect {
        val sprite = quad.sprite()
        val us = floatArrayOf(
            getUnInterpolatedU(sprite, UVPair.unpackU(quad.packedUV0())),
            getUnInterpolatedU(sprite, UVPair.unpackU(quad.packedUV1())),
            getUnInterpolatedU(sprite, UVPair.unpackU(quad.packedUV2())),
            getUnInterpolatedU(sprite, UVPair.unpackU(quad.packedUV3()))
        )
        val vs = floatArrayOf(
            getUnInterpolatedV(sprite, UVPair.unpackV(quad.packedUV0())),
            getUnInterpolatedV(sprite, UVPair.unpackV(quad.packedUV1())),
            getUnInterpolatedV(sprite, UVPair.unpackV(quad.packedUV2())),
            getUnInterpolatedV(sprite, UVPair.unpackV(quad.packedUV3()))
        )
        return UvRect(
            minU = us.minOrNull() ?: 0.0f,
            maxU = us.maxOrNull() ?: 16.0f,
            minV = vs.minOrNull() ?: 0.0f,
            maxV = vs.maxOrNull() ?: 16.0f
        )
    }

    private fun rectDistance(a: UvRect, b: UvRect): Float {
        return abs(a.minU - b.minU) +
                abs(a.maxU - b.maxU) +
                abs(a.minV - b.minV) +
                abs(a.maxV - b.maxV)
    }

    internal data class FaceBinding(
        val direction: Direction,
        val slot: Int,
        val box: Box,
        val planeCoord: Float,
        val aMin: Float,
        val aMax: Float,
        val bMin: Float,
        val bMax: Float,
        val uv: UvRect?
    )

    internal data class UvRect(
        val minU: Float,
        val maxU: Float,
        val minV: Float,
        val maxV: Float
    )

    internal data class Box(
        val minX: Float,
        val minY: Float,
        val minZ: Float,
        val maxX: Float,
        val maxY: Float,
        val maxZ: Float
    )

    private data class ElementReferenceIndex(
        val byGroupName: Map<String, Set<Int>>
    )
}
