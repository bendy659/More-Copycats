package ru.benos_codex.more_copycats.client.model.block

import com.zurrtum.create.AllBlocks
import com.zurrtum.create.client.infrastructure.model.CopycatModel
import com.zurrtum.create.client.model.NormalsBakedQuad
import com.zurrtum.create.content.decoration.copycat.CopycatBlock
import com.zurrtum.create.content.kinetics.simpleRelays.CogWheelBlock.AXIS
import net.minecraft.client.renderer.block.model.BakedQuad
import net.minecraft.client.renderer.block.model.BlockModelPart
import net.minecraft.client.renderer.block.model.BlockStateModel
import net.minecraft.client.renderer.block.model.SimpleModelWrapper
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.client.resources.model.QuadCollection
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.Direction.Axis
import net.minecraft.util.RandomSource
import net.minecraft.world.level.BlockAndTintGetter
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import org.slf4j.LoggerFactory
import ru.benos_codex.more_copycats.MoreCopycats
import ru.benos_codex.more_copycats.block.CopycatEncasedCogwheelBlock
import ru.benos_codex.more_copycats.block.entity.CopycatCogwheelBlockEntity
import ru.benos_codex.more_copycats.util.CogwheelMaterialSlotResolver
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max

class CopycatCogwheelBlockModel(state: BlockState, unbaked: BlockStateModel.UnbakedRoot) : CopycatModel(state, unbaked) {
    override fun addPartsWithInfo(
        world: BlockAndTintGetter,
        pos: BlockPos,
        state: BlockState,
        random: RandomSource,
        parts: MutableList<BlockModelPart>
    ) {
        if (state.block is CopycatEncasedCogwheelBlock) {
            addPartsWithInfo(world, pos, state, random, parts, RenderLayer.STATIC, includeShaft = false)
            return
        }

        addPartsWithInfo(world, pos, state, random, parts, RenderLayer.ALL, includeShaft = true)
    }

    fun addStaticPartsWithInfo(
        world: BlockAndTintGetter,
        pos: BlockPos,
        state: BlockState,
        random: RandomSource,
        parts: MutableList<BlockModelPart>
    ) {
        addPartsWithInfo(world, pos, state, random, parts, RenderLayer.STATIC, includeShaft = false)
    }

    fun addStaticShellParts(
        state: BlockState,
        random: RandomSource,
        parts: MutableList<BlockModelPart>
    ) {
        if (state.block !is CopycatEncasedCogwheelBlock) {
            return
        }

        val innerMatcher = CopycatMatGroupResolver.forModelIds(
            INNER_MODEL_IDS,
            rotateByY = true,
            rotateByAllAxes = true
        )
        val outerMatcher = CopycatMatGroupResolver.forModelIds(
            OUTER_MODEL_IDS,
            rotateByY = true,
            rotateByAllAxes = true
        )
        val toothMatcher = CopycatMatGroupResolver.forModelIds(
            TOOTH_MODEL_IDS,
            rotateByY = true,
            rotateByAllAxes = true
        )
        val shaftMatcher = CopycatMatGroupResolver.forModelIds(
            SHAFT_MODEL_IDS,
            rotateByY = true,
            rotateByAllAxes = true
        )
        val templateParts = mutableListOf<BlockModelPart>()
        model.collectParts(random, templateParts)

        for (templatePart in templateParts) {
            val partKind = detectPartKind(templatePart, innerMatcher, outerMatcher, toothMatcher, shaftMatcher)
            if (partKind == PartKind.SHAFT) {
                continue
            }
            parts += templatePart
        }
    }

    fun addShaftPartsWithInfo(
        world: BlockAndTintGetter,
        pos: BlockPos,
        state: BlockState,
        random: RandomSource,
        parts: MutableList<BlockModelPart>
    ) {
        addPartsWithInfo(world, pos, state, random, parts, RenderLayer.SHAFT_ONLY, includeShaft = true)
    }

    fun addFixedSlotPartWithInfo(
        world: BlockAndTintGetter,
        pos: BlockPos,
        state: BlockState,
        random: RandomSource,
        templatePart: BlockModelPart,
        slot: CopycatCogwheelBlockEntity.Slot,
        parts: MutableList<BlockModelPart>
    ) {
        val blockEntity = world.getBlockEntity(pos) as? CopycatCogwheelBlockEntity
        val actualMaterial = blockEntity?.getSlotMaterial(slot) ?: AllBlocks.COPYCAT_BASE.defaultBlockState()
        val hasCustom = blockEntity?.hasCustomMaterial(slot) == true
        val renderMaterial = renderMaterialForSlot(slot, hasCustom, actualMaterial)
        val refs = if (DEBUG_SLOT_COLORS) {
            collectDebugMaterialReferences(world, pos, slot, hasCustom, random)
        } else {
            collectMaterialReferences(world, pos, renderMaterial, random)
        }

        val builder = QuadCollection.Builder()
        addFixedSlotRemapped(templatePart, slot, builder, refs, renderMaterial, state, world, pos)
        parts += SimpleModelWrapper(
            builder.build(),
            if (DEBUG_SLOT_COLORS) false else templatePart.useAmbientOcclusion(),
            templatePart.particleIcon()
        )
    }

    fun addRotatingPartsWithInfo(
        world: BlockAndTintGetter,
        pos: BlockPos,
        state: BlockState,
        random: RandomSource,
        parts: MutableList<BlockModelPart>,
        includeShaft: Boolean
    ) {
        addPartsWithInfo(world, pos, state, random, parts, RenderLayer.ROTATING, includeShaft)
    }

    private fun addPartsWithInfo(
        world: BlockAndTintGetter,
        pos: BlockPos,
        state: BlockState,
        random: RandomSource,
        parts: MutableList<BlockModelPart>,
        renderLayer: RenderLayer,
        includeShaft: Boolean
    ) {
        val blockEntity = world.getBlockEntity(pos) as? CopycatCogwheelBlockEntity
        val materialsBySlot = linkedMapOf<CopycatCogwheelBlockEntity.Slot, BlockState>()
        val refsBySlot = linkedMapOf<CopycatCogwheelBlockEntity.Slot, Map<Direction, BakedQuad>>()
        for (slot in CopycatCogwheelBlockEntity.Slot.entries) {
            val actualMaterial = blockEntity?.getSlotMaterial(slot) ?: AllBlocks.COPYCAT_BASE.defaultBlockState()
            val hasCustom = blockEntity?.hasCustomMaterial(slot) == true
            val renderMaterial = renderMaterialForSlot(slot, hasCustom, actualMaterial)
            materialsBySlot[slot] = renderMaterial
            refsBySlot[slot] = if (DEBUG_SLOT_COLORS) {
                collectDebugMaterialReferences(world, pos, slot, hasCustom, random)
            } else {
                collectMaterialReferences(world, pos, renderMaterial, random)
            }
        }

        val axis = state.getValue(AXIS)
        val innerMatcher = CopycatMatGroupResolver.forModelIds(
            INNER_MODEL_IDS,
            rotateByY = true,
            rotateByAllAxes = true
        )
        val outerMatcher = CopycatMatGroupResolver.forModelIds(
            OUTER_MODEL_IDS,
            rotateByY = true,
            rotateByAllAxes = true
        )
        val toothMatcher = CopycatMatGroupResolver.forModelIds(
            TOOTH_MODEL_IDS,
            rotateByY = true,
            rotateByAllAxes = true
        )
        val shaftMatcher = CopycatMatGroupResolver.forModelIds(
            SHAFT_MODEL_IDS,
            rotateByY = true,
            rotateByAllAxes = true
        )
        val templateParts = mutableListOf<BlockModelPart>()
        model.collectParts(random, templateParts)

        for (templatePart in templateParts) {
            val partKind = detectPartKind(templatePart, innerMatcher, outerMatcher, toothMatcher, shaftMatcher)
            if (!shouldRenderPart(state, partKind, renderLayer, includeShaft))
                continue
            val builder = QuadCollection.Builder()
            addPartRemapped(
                templatePart,
                partKind,
                axis,
                innerMatcher,
                outerMatcher,
                toothMatcher,
                shaftMatcher,
                builder,
                refsBySlot,
                materialsBySlot,
                state,
                world,
                pos
            )
            parts += SimpleModelWrapper(
                builder.build(),
                if (DEBUG_SLOT_COLORS) false else templatePart.useAmbientOcclusion(),
                templatePart.particleIcon()
            )
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
        // unused: this model supports non-CopycatBlock BE-driven materials
    }

    override fun particleSpriteWithInfo(world: BlockAndTintGetter, pos: BlockPos, state: BlockState): TextureAtlasSprite {
        val blockEntity = world.getBlockEntity(pos) as? CopycatCogwheelBlockEntity
        val material = blockEntity?.getSlotMaterial(fallbackMaterialSlot(state))
            ?: AllBlocks.COPYCAT_BASE.defaultBlockState()
        return getModelOf(material).particleIcon()
    }

    private fun shouldRenderPart(
        state: BlockState,
        partKind: PartKind,
        renderLayer: RenderLayer,
        includeShaft: Boolean
    ): Boolean {
        if (renderLayer == RenderLayer.ALL)
            return true

        return when (renderLayer) {
            RenderLayer.ALL -> true
            RenderLayer.STATIC ->
                state.block is CopycatEncasedCogwheelBlock && partKind == PartKind.UNKNOWN
            RenderLayer.SHAFT_ONLY ->
                state.block is CopycatEncasedCogwheelBlock && partKind == PartKind.SHAFT
            RenderLayer.ROTATING ->
                partKind != PartKind.UNKNOWN && (includeShaft || partKind != PartKind.SHAFT)
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
        partKind: PartKind,
        axis: Axis,
        innerMatcher: CopycatMatGroupResolver.Matcher?,
        outerMatcher: CopycatMatGroupResolver.Matcher?,
        toothMatcher: CopycatMatGroupResolver.Matcher?,
        shaftMatcher: CopycatMatGroupResolver.Matcher?,
        builder: QuadCollection.Builder,
        refsBySlot: Map<CopycatCogwheelBlockEntity.Slot, Map<Direction, BakedQuad>>,
        materialsBySlot: Map<CopycatCogwheelBlockEntity.Slot, BlockState>,
        state: BlockState,
        world: BlockAndTintGetter,
        pos: BlockPos
    ) {
        val forcedToothSlot = resolveToothSlotForPart(templatePart, axis, pos)
        val fallbackSlot = fallbackMaterialSlot(state)

        for (quad in templatePart.getQuads(null)) {
            val slot = forcedToothSlot ?: pickSlot(quad, partKind, axis, state, innerMatcher, outerMatcher, toothMatcher, shaftMatcher)
            val refs = refsBySlot[slot] ?: refsBySlot.getValue(fallbackSlot)
            val material = materialsBySlot[slot] ?: materialsBySlot.getValue(fallbackSlot)
            val ref = refs[quad.direction()]
            val keepTemplate = shouldKeepTemplateQuad(state, slot, quad)
            val remapped = if (keepTemplate) {
                postProcessDebugQuad(quad)
            } else {
                postProcessDebugQuad(
                    CopycatConnectedTextureHelper.remapQuad(
                        quad,
                        ref?.sprite(),
                        ref,
                        world,
                        pos,
                        state,
                        material,
                        useConnected = !DEBUG_SLOT_COLORS
                    )
                )
            }
            if (keepTemplate) builder.addUnculledFace(remapped)
            else if (CopycatQuadCulling.isOnOuterBoundary(quad, quad.direction())) builder.addCulledFace(quad.direction(), remapped)
            else builder.addUnculledFace(remapped)
        }

        for (direction in Direction.entries) {
            for (quad in templatePart.getQuads(direction)) {
                val slot = forcedToothSlot ?: pickSlot(quad, partKind, axis, state, innerMatcher, outerMatcher, toothMatcher, shaftMatcher)
                val refs = refsBySlot[slot] ?: refsBySlot.getValue(fallbackSlot)
                val material = materialsBySlot[slot] ?: materialsBySlot.getValue(fallbackSlot)
                val ref = refs[direction]
                val keepTemplate = shouldKeepTemplateQuad(state, slot, quad)
                val remapped = if (keepTemplate) {
                    postProcessDebugQuad(quad)
                } else {
                    postProcessDebugQuad(
                        CopycatConnectedTextureHelper.remapQuad(
                            quad,
                            ref?.sprite(),
                            ref,
                            world,
                            pos,
                            state,
                            material,
                            useConnected = !DEBUG_SLOT_COLORS
                        )
                    )
                }
                if (keepTemplate) builder.addUnculledFace(remapped)
                else builder.addCulledFace(direction, remapped)
            }
        }
    }

    private fun addFixedSlotRemapped(
        templatePart: BlockModelPart,
        slot: CopycatCogwheelBlockEntity.Slot,
        builder: QuadCollection.Builder,
        refs: Map<Direction, BakedQuad>,
        material: BlockState,
        state: BlockState,
        world: BlockAndTintGetter,
        pos: BlockPos
    ) {
        for (quad in templatePart.getQuads(null)) {
            val ref = refs[quad.direction()]
            val remapped = postProcessDebugQuad(
                CopycatConnectedTextureHelper.remapQuad(
                    quad,
                    ref?.sprite(),
                    ref,
                    world,
                    pos,
                    state,
                    material,
                    useConnected = !DEBUG_SLOT_COLORS
                )
            )
            if (CopycatQuadCulling.isOnOuterBoundary(quad, quad.direction())) builder.addCulledFace(quad.direction(), remapped)
            else builder.addUnculledFace(remapped)
        }

        for (direction in Direction.entries) {
            for (quad in templatePart.getQuads(direction)) {
                val ref = refs[direction]
                val remapped = postProcessDebugQuad(
                    CopycatConnectedTextureHelper.remapQuad(
                        quad,
                        ref?.sprite(),
                        ref,
                        world,
                        pos,
                        state,
                        material,
                        useConnected = !DEBUG_SLOT_COLORS
                    )
                )
                builder.addCulledFace(direction, remapped)
            }
        }
    }

    private fun shouldKeepTemplateQuad(
        state: BlockState,
        slot: CopycatCogwheelBlockEntity.Slot,
        quad: BakedQuad
    ): Boolean {
        if (DEBUG_SLOT_COLORS)
            return false
        if (state.block !is CopycatEncasedCogwheelBlock)
            return false
        if (slot != CopycatCogwheelBlockEntity.Slot.MAT_7)
            return false

        val spriteId = quad.sprite().contents().name()

        return spriteId.namespace != "create" || spriteId.path != "block/copycat_base"
    }

    private fun postProcessDebugQuad(quad: BakedQuad): BakedQuad {
        if (!DEBUG_SLOT_COLORS)
            return quad

        val out = BakedQuad(
            quad.position0(),
            quad.position1(),
            quad.position2(),
            quad.position3(),
            quad.packedUV0(),
            quad.packedUV1(),
            quad.packedUV2(),
            quad.packedUV3(),
            -1,
            quad.direction(),
            quad.sprite(),
            false,
            15
        )
        NormalsBakedQuad.setNormals(out, NormalsBakedQuad.getNormals(quad))
        return out
    }

    private fun detectPartKind(
        templatePart: BlockModelPart,
        innerMatcher: CopycatMatGroupResolver.Matcher?,
        outerMatcher: CopycatMatGroupResolver.Matcher?,
        toothMatcher: CopycatMatGroupResolver.Matcher?,
        shaftMatcher: CopycatMatGroupResolver.Matcher?
    ): PartKind {
        val bounds = estimateBounds(templatePart)
        if (bounds != null && looksLikeShaft(bounds)) {
            return PartKind.SHAFT
        }

        val strictCounts = linkedMapOf(
            PartKind.SHAFT to (shaftMatcher?.strictMatchCount(templatePart) ?: 0),
            PartKind.TOOTH to (toothMatcher?.strictMatchCount(templatePart) ?: 0),
            PartKind.OUTER to (outerMatcher?.strictMatchCount(templatePart) ?: 0),
            PartKind.INNER to (innerMatcher?.strictMatchCount(templatePart) ?: 0)
        )
        bestPartKind(strictCounts, bounds)?.let { return it }

        // Fallback when strict geometric matching loses precision after baking transforms.
        val softCounts = linkedMapOf(
            PartKind.SHAFT to (shaftMatcher?.matchCount(templatePart) ?: 0),
            PartKind.TOOTH to (toothMatcher?.matchCount(templatePart) ?: 0),
            PartKind.OUTER to (outerMatcher?.matchCount(templatePart) ?: 0),
            PartKind.INNER to (innerMatcher?.matchCount(templatePart) ?: 0)
        )
        bestPartKind(softCounts, bounds)?.let { return it }

        return PartKind.UNKNOWN
    }

    private fun resolveToothSlotForPart(
        part: BlockModelPart,
        axis: Axis,
        pos: BlockPos
    ): CopycatCogwheelBlockEntity.Slot? {
        val points = mutableListOf<CogwheelMaterialSlotResolver.LocalHit>()

        fun collectPoint(x: Float, y: Float, z: Float) {
            val nx = normalize(x).toDouble()
            val ny = normalize(y).toDouble()
            val nz = normalize(z).toDouble()
            points += CogwheelMaterialSlotResolver.toLocal(axis, nx, ny, nz)
        }

        fun collectQuad(quad: BakedQuad) {
            collectPoint(quad.position0().x(), quad.position0().y(), quad.position0().z())
            collectPoint(quad.position1().x(), quad.position1().y(), quad.position1().z())
            collectPoint(quad.position2().x(), quad.position2().y(), quad.position2().z())
            collectPoint(quad.position3().x(), quad.position3().y(), quad.position3().z())
        }

        for (quad in part.getQuads(null))
            collectQuad(quad)
        for (direction in Direction.entries) {
            for (quad in part.getQuads(direction))
                collectQuad(quad)
        }

        if (points.isEmpty())
            return null

        val maxRadial = points.maxOf { max(abs(it.x - 0.5), abs(it.y - 0.5)) }
        if (maxRadial < TOOTH_MIN_RADIAL)
            return null.also {
                maybeLogToothPartDecision(pos, axis, points, maxRadial, 0.0, 0.0, 0.0, null, "radial_gate")
            }

        val minX = points.minOf { it.x }
        val maxX = points.maxOf { it.x }
        val minY = points.minOf { it.y }
        val maxY = points.maxOf { it.y }
        val spanX = maxX - minX
        val spanY = maxY - minY
        if (spanX - spanY >= TOOTH_PART_STRAIGHT_BIAS) {
            val slot = CopycatCogwheelBlockEntity.Slot.MAT_0
            maybeLogToothPartDecision(pos, axis, points, maxRadial, spanX, spanY, 0.0, slot, "span_x")
            return slot
        }
        if (spanY - spanX >= TOOTH_PART_STRAIGHT_BIAS) {
            val slot = CopycatCogwheelBlockEntity.Slot.MAT_2
            maybeLogToothPartDecision(pos, axis, points, maxRadial, spanX, spanY, 0.0, slot, "span_y")
            return slot
        }

        var correlation = 0.0
        for (point in points) {
            val dx = point.x - 0.5
            val dy = point.y - 0.5
            val radial = max(abs(dx), abs(dy))
            if (radial < TOOTH_PART_DIAGONAL_MIN_RADIAL)
                continue
            correlation += dx * dy
        }

        if (correlation < 0.0) {
            val slot = CopycatCogwheelBlockEntity.Slot.MAT_1
            maybeLogToothPartDecision(pos, axis, points, maxRadial, spanX, spanY, correlation, slot, "diag_neg")
            return slot
        }
        if (correlation > 0.0) {
            val slot = CopycatCogwheelBlockEntity.Slot.MAT_3
            maybeLogToothPartDecision(pos, axis, points, maxRadial, spanX, spanY, correlation, slot, "diag_pos")
            return slot
        }

        maybeLogToothPartDecision(pos, axis, points, maxRadial, spanX, spanY, correlation, null, "fallback_null")
        return null
    }

    private fun maybeLogToothPartDecision(
        pos: BlockPos,
        axis: Axis,
        points: List<CogwheelMaterialSlotResolver.LocalHit>,
        maxRadial: Double,
        spanX: Double,
        spanY: Double,
        correlation: Double,
        slot: CopycatCogwheelBlockEntity.Slot?,
        reason: String
    ) {
        if (!DEBUG_SLOT_LOGS)
            return

        val key = buildString {
            append(pos.asLong())
            append('|')
            append(axis.name)
            append('|')
            append(slot?.ordinal ?: -1)
            append('|')
            append(reason)
            append('|')
            append((spanX * 100.0).toInt())
            append(':')
            append((spanY * 100.0).toInt())
            append(':')
            append((correlation * 1000.0).toInt())
        }
        val now = System.currentTimeMillis()
        val previous = toothPartLogThrottle.put(key, now)
        if (previous != null && now - previous < TOOTH_DEBUG_LOG_INTERVAL_MS)
            return

        toothPartLogger.info(
            "tooth-part pos={} axis={} reason={} slot={} points={} maxR={} spanX={} spanY={} corr={}",
            pos,
            axis,
            reason,
            slot?.name ?: "null",
            points.size,
            round3(maxRadial),
            round3(spanX),
            round3(spanY),
            round3(correlation)
        )
    }

    private fun round3(value: Double): Double =
        kotlin.math.round(value * 1000.0) / 1000.0

    private fun bestPartKind(counts: LinkedHashMap<PartKind, Int>, bounds: Bounds?): PartKind? {
        val filtered = if (bounds != null && !looksLikeShaft(bounds)) {
            LinkedHashMap(counts.filterKeys { it != PartKind.SHAFT })
        } else {
            counts
        }

        val best = filtered.maxByOrNull { it.value } ?: return null
        if (best.value <= 0) return null
        val ties = filtered.count { it.value == best.value }
        // Keep deterministic priority by insertion order in case of tie.
        return if (ties == 1) best.key else filtered.entries.first { it.value == best.value }.key
    }

    private fun pickSlot(
        quad: BakedQuad,
        partKind: PartKind,
        axis: Axis,
        state: BlockState,
        innerMatcher: CopycatMatGroupResolver.Matcher?,
        outerMatcher: CopycatMatGroupResolver.Matcher?,
        toothMatcher: CopycatMatGroupResolver.Matcher?,
        shaftMatcher: CopycatMatGroupResolver.Matcher?
    ): CopycatCogwheelBlockEntity.Slot {
        if (state.block is CopycatEncasedCogwheelBlock && partKind == PartKind.UNKNOWN)
            return CopycatCogwheelBlockEntity.Slot.MAT_7

        if (partKind == PartKind.SHAFT || shaftMatcher?.slotForQuadStrict(quad) != null)
            return CopycatCogwheelBlockEntity.Slot.MAT_6

        val localBounds = quadBoundsLocal(quad, axis)
        if (looksLikeShaftBounds(localBounds))
            return CopycatCogwheelBlockEntity.Slot.MAT_6

        val maxRadial = quadMaxRadialLocal(quad, axis)
        if (maxRadial <= OUTER_MAX_RADIAL)
            return CopycatCogwheelBlockEntity.Slot.MAT_4
        if (maxRadial <= INNER_MAX_RADIAL)
            return CopycatCogwheelBlockEntity.Slot.MAT_5
        if (maxRadial >= TOOTH_MIN_RADIAL)
            return resolveToothSlot(quad, axis, localBounds)

        resolveSlotFromQuadOverlap(quad, axis, localBounds)?.let { return it }

        when (partKind) {
            PartKind.SHAFT -> return CopycatCogwheelBlockEntity.Slot.MAT_6
            PartKind.INNER -> return CopycatCogwheelBlockEntity.Slot.MAT_5
            PartKind.OUTER -> return CopycatCogwheelBlockEntity.Slot.MAT_4
            PartKind.TOOTH -> return resolveToothSlot(quad, axis, localBounds)
            PartKind.UNKNOWN -> {}
        }

        if (toothMatcher?.slotForQuadStrict(quad) != null)
            return resolveToothSlot(quad, axis, localBounds)
        if (outerMatcher?.slotForQuadStrict(quad) != null) {
            return CopycatCogwheelBlockEntity.Slot.MAT_4
        }
        if (innerMatcher?.slotForQuadStrict(quad) != null) {
            return CopycatCogwheelBlockEntity.Slot.MAT_5
        }
        if (shaftMatcher?.slotForQuadStrict(quad) != null) {
            return CopycatCogwheelBlockEntity.Slot.MAT_6
        }

        resolveSlotFromQuadCenterStrict(quad, axis)?.let { resolved ->
            if (resolved != CopycatCogwheelBlockEntity.Slot.MAT_6 || shaftMatcher?.slotForQuadStrict(quad) != null) {
                return resolved
            }
        }

        resolveSlotFromQuadCenterNearest(quad, axis)?.let { resolved ->
            if (resolved != CopycatCogwheelBlockEntity.Slot.MAT_6 || shaftMatcher?.slotForQuadStrict(quad) != null) {
                return resolved
            }
        }

        return CopycatCogwheelBlockEntity.Slot.MAT_4
    }

    private fun resolveSlotFromQuadOverlap(
        quad: BakedQuad,
        axis: Axis,
        bounds: BoundsD
    ): CopycatCogwheelBlockEntity.Slot? {
        var bestSlot: Int? = null
        var bestScore = 0.0
        var bestPriority = Int.MIN_VALUE

        for (box in localBoxes) {
            if (box.slot == 6) continue

            val score = overlapScore(bounds, box)
            if (score <= 0.0) continue

            val priority = renderPriority(box.slot)
            if (score > bestScore || (abs(score - bestScore) <= 1.0e-9 && priority > bestPriority)) {
                bestScore = score
                bestSlot = box.slot
                bestPriority = priority
            }
        }

        if (bestSlot == null) {
            return null
        }

        val center = quadCenterLocal(quad, axis)
        val maxRadial = quadMaxRadialLocal(quad, axis)
        return finalizeSlot(bestSlot, center, maxRadial, quad, axis)
    }

    private fun resolveToothSlotByAngle(quad: BakedQuad, axis: Axis): CopycatCogwheelBlockEntity.Slot {
        val cx = normalize((quad.position0().x() + quad.position1().x() + quad.position2().x() + quad.position3().x()) * 0.25f).toDouble()
        val cy = normalize((quad.position0().y() + quad.position1().y() + quad.position2().y() + quad.position3().y()) * 0.25f).toDouble()
        val cz = normalize((quad.position0().z() + quad.position1().z() + quad.position2().z() + quad.position3().z()) * 0.25f).toDouble()

        val (a, b) = when (axis) {
            Axis.X -> cy to cz
            Axis.Y -> cx to cz
            Axis.Z -> cx to cy
        }

        val index = toothIndex(a - 0.5, b - 0.5)
        return when (index) {
            0 -> CopycatCogwheelBlockEntity.Slot.MAT_0
            1 -> CopycatCogwheelBlockEntity.Slot.MAT_1
            2 -> CopycatCogwheelBlockEntity.Slot.MAT_2
            else -> CopycatCogwheelBlockEntity.Slot.MAT_3
        }
    }

    private fun resolveToothSlot(
        quad: BakedQuad,
        axis: Axis,
        bounds: BoundsD
    ): CopycatCogwheelBlockEntity.Slot {
        resolveToothSlotByOverlap(bounds)?.let { return it }
        resolveToothSlotByFarthestVertex(quad, axis)?.let { return it }
        return resolveToothSlotByAngle(quad, axis)
    }

    private fun resolveToothSlotByFarthestVertex(
        quad: BakedQuad,
        axis: Axis
    ): CopycatCogwheelBlockEntity.Slot? {
        val vertices = arrayOf(quad.position0(), quad.position1(), quad.position2(), quad.position3())
        var bestX = 0.5
        var bestY = 0.5
        var bestZ = 0.5
        var bestRadial = Double.NEGATIVE_INFINITY

        for (vertex in vertices) {
            val x = normalize(vertex.x()).toDouble()
            val y = normalize(vertex.y()).toDouble()
            val z = normalize(vertex.z()).toDouble()
            val local = CogwheelMaterialSlotResolver.toLocal(axis, x, y, z)
            val dx = local.x - 0.5
            val dy = local.y - 0.5
            val radial = max(abs(dx), abs(dy))

            if (radial > bestRadial) {
                bestRadial = radial
                bestX = local.x
                bestY = local.y
                bestZ = local.z
            }
        }

        if (bestRadial < TOOTH_VERTEX_MIN_RADIAL)
            return null

        val nearest = localBoxes
            .asSequence()
            .filter { it.slot in 0..3 }
            .minWithOrNull(
                compareBy<CogwheelMaterialSlotResolver.SlotBox> {
                    distanceSquaredToAabb(bestX, bestY, bestZ, it)
                }.thenByDescending {
                    centerProximityScore(it, bestX, bestY, bestZ)
                }
            ) ?: return null

        return slotByIndex(nearest.slot)
    }

    private fun resolveToothSlotByOverlap(bounds: BoundsD): CopycatCogwheelBlockEntity.Slot? {
        val centerX = (bounds.minX + bounds.maxX) * 0.5
        val centerY = (bounds.minY + bounds.maxY) * 0.5
        val centerZ = (bounds.minZ + bounds.maxZ) * 0.5
        var bestSlot: Int? = null
        var bestScore = 0.0
        var secondBestScore = 0.0
        var bestCenterScore = Double.NEGATIVE_INFINITY

        for (box in localBoxes) {
            if (box.slot !in 0..3)
                continue

            val score = overlapScore(bounds, box)
            if (score <= 0.0)
                continue

            val centerScore = centerProximityScore(box, centerX, centerY, centerZ)
            if (score > bestScore || (abs(score - bestScore) <= 1.0e-9 && centerScore > bestCenterScore)) {
                secondBestScore = bestScore
                bestScore = score
                bestCenterScore = centerScore
                bestSlot = box.slot
            } else if (score > secondBestScore) {
                secondBestScore = score
            }
        }

        if (bestScore < TOOTH_OVERLAP_MIN_SCORE)
            return null
        if (secondBestScore > 0.0) {
            val ratio = bestScore / secondBestScore
            if (ratio < TOOTH_OVERLAP_CONFIDENCE_RATIO || bestScore - secondBestScore < TOOTH_OVERLAP_MIN_DELTA)
                return null
        }

        return bestSlot?.let(::slotByIndex)
    }

    private fun toothIndex(dx: Double, dy: Double): Int {
        var phi = atan2(dy, -dx)
        if (phi < 0.0) phi += PI
        if (phi >= PI) phi -= PI
        val step = PI / 4.0
        return (((phi / step) + 0.5).toInt() and 3)
    }

    private fun slotByIndex(index: Int): CopycatCogwheelBlockEntity.Slot {
        return when (index) {
            0 -> CopycatCogwheelBlockEntity.Slot.MAT_0
            1 -> CopycatCogwheelBlockEntity.Slot.MAT_1
            2 -> CopycatCogwheelBlockEntity.Slot.MAT_2
            3 -> CopycatCogwheelBlockEntity.Slot.MAT_3
            4 -> CopycatCogwheelBlockEntity.Slot.MAT_4
            5 -> CopycatCogwheelBlockEntity.Slot.MAT_5
            6 -> CopycatCogwheelBlockEntity.Slot.MAT_6
            else -> CopycatCogwheelBlockEntity.Slot.MAT_7
        }
    }

    private fun normalize(v: Float): Float {
        return if (kotlin.math.abs(v) > 2.0f) v / 16.0f else v
    }

    private fun estimateBounds(part: BlockModelPart): Bounds? {
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var minZ = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        var maxZ = Float.NEGATIVE_INFINITY
        var any = false

        fun includeQuad(quad: BakedQuad) {
            val points = arrayOf(quad.position0(), quad.position1(), quad.position2(), quad.position3())
            for (point in points) {
                val x = normalize(point.x())
                val y = normalize(point.y())
                val z = normalize(point.z())
                minX = minOf(minX, x)
                minY = minOf(minY, y)
                minZ = minOf(minZ, z)
                maxX = maxOf(maxX, x)
                maxY = maxOf(maxY, y)
                maxZ = maxOf(maxZ, z)
                any = true
            }
        }

        for (quad in part.getQuads(null)) {
            includeQuad(quad)
        }
        for (direction in Direction.entries) {
            for (quad in part.getQuads(direction)) {
                includeQuad(quad)
            }
        }

        if (!any) {
            return null
        }

        return Bounds(minX, minY, minZ, maxX, maxY, maxZ)
    }

    private fun looksLikeShaft(bounds: Bounds): Boolean {
        val sizes = listOf(
            bounds.maxX - bounds.minX,
            bounds.maxY - bounds.minY,
            bounds.maxZ - bounds.minZ
        ).sorted()

        return looksLikeFullShaftF(sizes) || looksLikeShaftHalfF(sizes)
    }

    private fun looksLikeFullShaftF(sizes: List<Float>): Boolean =
        sizes[2] >= 0.92f && sizes[1] <= 0.36f && sizes[0] <= 0.36f

    private fun looksLikeShaftHalfF(sizes: List<Float>): Boolean =
        sizes[2] in 0.45f..0.55f && sizes[1] <= 0.30f && sizes[0] <= 0.30f

    private fun resolveSlotFromQuadCenterStrict(quad: BakedQuad, axis: Axis): CopycatCogwheelBlockEntity.Slot? {
        val local = quadCenterLocal(quad, axis)

        val candidates = localBoxes
            .asSequence()
            .filter { contains(it, local.x, local.y, local.z, STRICT_HIT_EPS) }
            .toList()
        if (candidates.isEmpty()) return null

        val best = candidates.maxWithOrNull(
            compareBy<CogwheelMaterialSlotResolver.SlotBox> { renderPriority(it.slot) }
                .thenByDescending { centerProximityScore(it, local.x, local.y, local.z) }
        ) ?: return null

        return finalizeSlot(best.slot, local, quadMaxRadialLocal(quad, axis), quad, axis)
    }

    private fun resolveSlotFromQuadCenterNearest(quad: BakedQuad, axis: Axis): CopycatCogwheelBlockEntity.Slot? {
        val local = quadCenterLocal(quad, axis)
        val nearest = localBoxes.minWithOrNull(
            compareBy<CogwheelMaterialSlotResolver.SlotBox> {
                distanceSquaredToAabb(local.x, local.y, local.z, it)
            }.thenByDescending {
                renderPriority(it.slot)
            }
        ) ?: return null

        return finalizeSlot(nearest.slot, local, quadMaxRadialLocal(quad, axis), quad, axis)
    }

    private fun quadCenterLocal(quad: BakedQuad, axis: Axis): CogwheelMaterialSlotResolver.LocalHit {
        val cx = normalize((quad.position0().x() + quad.position1().x() + quad.position2().x() + quad.position3().x()) * 0.25f).toDouble()
        val cy = normalize((quad.position0().y() + quad.position1().y() + quad.position2().y() + quad.position3().y()) * 0.25f).toDouble()
        val cz = normalize((quad.position0().z() + quad.position1().z() + quad.position2().z() + quad.position3().z()) * 0.25f).toDouble()
        return CogwheelMaterialSlotResolver.toLocal(axis, cx, cy, cz)
    }

    private fun quadBoundsLocal(quad: BakedQuad, axis: Axis): BoundsD {
        val points = arrayOf(quad.position0(), quad.position1(), quad.position2(), quad.position3())
        var minX = Double.POSITIVE_INFINITY
        var minY = Double.POSITIVE_INFINITY
        var minZ = Double.POSITIVE_INFINITY
        var maxX = Double.NEGATIVE_INFINITY
        var maxY = Double.NEGATIVE_INFINITY
        var maxZ = Double.NEGATIVE_INFINITY

        for (point in points) {
            val x = normalize(point.x()).toDouble()
            val y = normalize(point.y()).toDouble()
            val z = normalize(point.z()).toDouble()
            val local = CogwheelMaterialSlotResolver.toLocal(axis, x, y, z)

            minX = minOf(minX, local.x)
            minY = minOf(minY, local.y)
            minZ = minOf(minZ, local.z)
            maxX = maxOf(maxX, local.x)
            maxY = maxOf(maxY, local.y)
            maxZ = maxOf(maxZ, local.z)
        }

        return BoundsD(minX, minY, minZ, maxX, maxY, maxZ)
    }

    private fun finalizeSlot(
        rawSlot: Int,
        local: CogwheelMaterialSlotResolver.LocalHit,
        maxRadial: Double,
        quad: BakedQuad,
        axis: Axis
    ): CopycatCogwheelBlockEntity.Slot {
        val radial = max(abs(local.x - 0.5), abs(local.y - 0.5))
        if (rawSlot == 4) {
            if (maxRadial >= TOOTH_RADIAL_THRESHOLD) {
                return resolveToothSlotByAngle(quad, axis)
            }
            if (maxRadial >= OUTER_TO_INNER_SWITCH_MAX || radial >= OUTER_TO_INNER_SWITCH_CENTER) {
                return CopycatCogwheelBlockEntity.Slot.MAT_5
            }
        }

        return slotByIndex(rawSlot)
    }

    private fun quadMaxRadialLocal(quad: BakedQuad, axis: Axis): Double {
        val points = arrayOf(quad.position0(), quad.position1(), quad.position2(), quad.position3())
        var maxRadial = 0.0
        for (point in points) {
            val x = normalize(point.x()).toDouble()
            val y = normalize(point.y()).toDouble()
            val z = normalize(point.z()).toDouble()
            val local = CogwheelMaterialSlotResolver.toLocal(axis, x, y, z)
            val radial = max(abs(local.x - 0.5), abs(local.y - 0.5))
            if (radial > maxRadial) {
                maxRadial = radial
            }
        }
        return maxRadial
    }

    private fun looksLikeShaftBounds(bounds: BoundsD): Boolean {
        val xSize = bounds.maxX - bounds.minX
        val ySize = bounds.maxY - bounds.minY
        val zSize = bounds.maxZ - bounds.minZ
        val centerX = (bounds.minX + bounds.maxX) * 0.5
        val centerY = (bounds.minY + bounds.maxY) * 0.5
        val centerZ = (bounds.minZ + bounds.maxZ) * 0.5
        val centered = kotlin.math.abs(centerX - 0.5) <= 0.16 && kotlin.math.abs(centerY - 0.5) <= 0.16
        if (!centered)
            return false

        if (zSize >= 0.92 && xSize <= 0.36 && ySize <= 0.36)
            return true

        val halfCentered = centerZ <= 0.30 || centerZ >= 0.70
        return zSize in 0.45..0.55 && xSize <= 0.30 && ySize <= 0.30 && halfCentered
    }

    private fun contains(
        box: CogwheelMaterialSlotResolver.SlotBox,
        x: Double,
        y: Double,
        z: Double,
        eps: Double
    ): Boolean {
        return x >= box.minX - eps &&
                x <= box.maxX + eps &&
                y >= box.minY - eps &&
                y <= box.maxY + eps &&
                z >= box.minZ - eps &&
                z <= box.maxZ + eps
    }

    private fun distanceSquaredToAabb(
        x: Double,
        y: Double,
        z: Double,
        box: CogwheelMaterialSlotResolver.SlotBox
    ): Double {
        val dx = when {
            x < box.minX -> box.minX - x
            x > box.maxX -> x - box.maxX
            else -> 0.0
        }
        val dy = when {
            y < box.minY -> box.minY - y
            y > box.maxY -> y - box.maxY
            else -> 0.0
        }
        val dz = when {
            z < box.minZ -> box.minZ - z
            z > box.maxZ -> z - box.maxZ
            else -> 0.0
        }
        return dx * dx + dy * dy + dz * dz
    }

    private fun overlapScore(bounds: BoundsD, box: CogwheelMaterialSlotResolver.SlotBox): Double {
        val dx = minOf(bounds.maxX, box.maxX) - maxOf(bounds.minX, box.minX)
        val dy = minOf(bounds.maxY, box.maxY) - maxOf(bounds.minY, box.minY)
        val dz = minOf(bounds.maxZ, box.maxZ) - maxOf(bounds.minZ, box.minZ)

        if (dx <= 0.0 || dy <= 0.0 || dz <= 0.0) {
            return 0.0
        }

        // Works for thin quads too: if one axis is tiny, pairwise areas still keep meaningful overlap ordering.
        return dx * dy + dx * dz + dy * dz
    }

    private fun centerProximityScore(
        box: CogwheelMaterialSlotResolver.SlotBox,
        x: Double,
        y: Double,
        z: Double
    ): Double {
        val cx = (box.minX + box.maxX) * 0.5
        val cy = (box.minY + box.maxY) * 0.5
        val cz = (box.minZ + box.maxZ) * 0.5
        val dx = x - cx
        val dy = y - cy
        val dz = z - cz
        return -(dx * dx + dy * dy + dz * dz)
    }

    private fun renderPriority(slot: Int): Int {
        return when (slot) {
            6 -> 400
            0, 1, 2, 3 -> 300
            4 -> 200
            5 -> 100
            7 -> 50
            else -> 0
        }
    }

    private companion object {
        private val DEBUG_SLOT_COLORS: Boolean
            get() = MoreCopycats.Config.debugCogwheelSlotColors
        private val DEBUG_SLOT_LOGS: Boolean
            get() = MoreCopycats.DEBUG_COGWHEEL_RENDER_LOGS
        private const val TOOTH_DEBUG_LOG_INTERVAL_MS = 500L
        private const val STRICT_HIT_EPS = 0.001
        private const val OUTER_MAX_RADIAL = 0.27
        private const val INNER_MAX_RADIAL = 0.40
        private const val TOOTH_MIN_RADIAL = 0.43
        private const val TOOTH_RADIAL_THRESHOLD = 0.44
        private const val TOOTH_VERTEX_MIN_RADIAL = 0.47
        private const val TOOTH_OVERLAP_MIN_SCORE = 0.003
        private const val TOOTH_OVERLAP_MIN_DELTA = 0.0015
        private const val TOOTH_OVERLAP_CONFIDENCE_RATIO = 1.35
        private const val TOOTH_PART_STRAIGHT_BIAS = 0.08
        private const val TOOTH_PART_DIAGONAL_MIN_RADIAL = 0.42
        private const val OUTER_TO_INNER_SWITCH_CENTER = 0.295
        private const val OUTER_TO_INNER_SWITCH_MAX = 0.315
        private val toothPartLogger = LoggerFactory.getLogger("MoreCopycats/CogwheelTooth")
        private val toothPartLogThrottle = java.util.concurrent.ConcurrentHashMap<String, Long>()
        private val localBoxes = CogwheelMaterialSlotResolver.allLocalBoxes()
        val INNER_MODEL_IDS = listOf("more_copycats:block/cogwheel/normal/inner_part")
        val OUTER_MODEL_IDS = listOf("more_copycats:block/cogwheel/normal/outer_part")
        val TOOTH_MODEL_IDS = listOf(
            "more_copycats:block/cogwheel/normal/gear_0_part",
            "more_copycats:block/cogwheel/normal/gear_1_part",
            "more_copycats:block/cogwheel/normal/gear_2_part",
            "more_copycats:block/cogwheel/normal/gear_3_part"
        )
        val SHAFT_MODEL_IDS = listOf(
            "more_copycats:block/shaft/normal",
            "more_copycats:block/shaft/axis"
        )
    }

    private fun fallbackMaterialSlot(state: BlockState): CopycatCogwheelBlockEntity.Slot =
        if (state.block is CopycatEncasedCogwheelBlock)
            CopycatCogwheelBlockEntity.Slot.MAT_7
        else
            CopycatCogwheelBlockEntity.Slot.MAT_4

    private fun renderMaterialForSlot(
        slot: CopycatCogwheelBlockEntity.Slot,
        hasCustom: Boolean,
        fallback: BlockState
    ): BlockState {
        if (!DEBUG_SLOT_COLORS)
            return fallback
        if (!hasCustom)
            return Blocks.BLUE_CONCRETE.defaultBlockState()

        return when (slot) {
            CopycatCogwheelBlockEntity.Slot.MAT_0 -> Blocks.RED_CONCRETE.defaultBlockState()
            CopycatCogwheelBlockEntity.Slot.MAT_1 -> Blocks.LIME_CONCRETE.defaultBlockState()
            CopycatCogwheelBlockEntity.Slot.MAT_2 -> Blocks.YELLOW_CONCRETE.defaultBlockState()
            CopycatCogwheelBlockEntity.Slot.MAT_3 -> Blocks.ORANGE_CONCRETE.defaultBlockState()
            CopycatCogwheelBlockEntity.Slot.MAT_4 -> Blocks.MAGENTA_CONCRETE.defaultBlockState()
            CopycatCogwheelBlockEntity.Slot.MAT_5 -> Blocks.CYAN_CONCRETE.defaultBlockState()
            CopycatCogwheelBlockEntity.Slot.MAT_6 -> Blocks.WHITE_CONCRETE.defaultBlockState()
            CopycatCogwheelBlockEntity.Slot.MAT_7 -> Blocks.BLACK_CONCRETE.defaultBlockState()
        }
    }

    private fun collectDebugMaterialReferences(
        world: BlockAndTintGetter,
        pos: BlockPos,
        slot: CopycatCogwheelBlockEntity.Slot,
        hasCustom: Boolean,
        random: RandomSource
    ): Map<Direction, BakedQuad> {
        val palette = debugPaletteForSlot(slot, hasCustom)
        val sideRefs = collectMaterialReferences(world, pos, palette.side, random)
        val upRefs = collectMaterialReferences(world, pos, palette.up, random)
        val downRefs = collectMaterialReferences(world, pos, palette.down, random)
        val refs = mutableMapOf<Direction, BakedQuad>()

        for (direction in Direction.entries) {
            val quad = when (direction) {
                Direction.UP -> upRefs[Direction.UP] ?: sideRefs[Direction.UP] ?: downRefs[Direction.UP]
                Direction.DOWN -> downRefs[Direction.DOWN] ?: sideRefs[Direction.DOWN] ?: upRefs[Direction.DOWN]
                else -> sideRefs[direction] ?: upRefs[direction] ?: downRefs[direction]
            }
            if (quad != null)
                refs[direction] = quad
        }

        return refs
    }

    private fun debugPaletteForSlot(
        slot: CopycatCogwheelBlockEntity.Slot,
        hasCustom: Boolean
    ): DebugPalette {
        if (!hasCustom) {
            return DebugPalette(
                side = Blocks.BLUE_CONCRETE.defaultBlockState(),
                up = Blocks.LIGHT_BLUE_CONCRETE.defaultBlockState(),
                down = Blocks.CYAN_CONCRETE.defaultBlockState()
            )
        }

        return when (slot) {
            CopycatCogwheelBlockEntity.Slot.MAT_0 -> DebugPalette(
                side = Blocks.RED_CONCRETE.defaultBlockState(),
                up = Blocks.PINK_CONCRETE.defaultBlockState(),
                down = Blocks.BROWN_CONCRETE.defaultBlockState()
            )
            CopycatCogwheelBlockEntity.Slot.MAT_1 -> DebugPalette(
                side = Blocks.LIME_CONCRETE.defaultBlockState(),
                up = Blocks.YELLOW_CONCRETE.defaultBlockState(),
                down = Blocks.GREEN_CONCRETE.defaultBlockState()
            )
            CopycatCogwheelBlockEntity.Slot.MAT_2 -> DebugPalette(
                side = Blocks.YELLOW_CONCRETE.defaultBlockState(),
                up = Blocks.WHITE_CONCRETE.defaultBlockState(),
                down = Blocks.ORANGE_CONCRETE.defaultBlockState()
            )
            CopycatCogwheelBlockEntity.Slot.MAT_3 -> DebugPalette(
                side = Blocks.ORANGE_CONCRETE.defaultBlockState(),
                up = Blocks.YELLOW_CONCRETE.defaultBlockState(),
                down = Blocks.RED_CONCRETE.defaultBlockState()
            )
            CopycatCogwheelBlockEntity.Slot.MAT_4 -> DebugPalette(
                side = Blocks.MAGENTA_CONCRETE.defaultBlockState(),
                up = Blocks.PINK_CONCRETE.defaultBlockState(),
                down = Blocks.PURPLE_CONCRETE.defaultBlockState()
            )
            CopycatCogwheelBlockEntity.Slot.MAT_5 -> DebugPalette(
                side = Blocks.CYAN_CONCRETE.defaultBlockState(),
                up = Blocks.LIGHT_BLUE_CONCRETE.defaultBlockState(),
                down = Blocks.BLUE_CONCRETE.defaultBlockState()
            )
            CopycatCogwheelBlockEntity.Slot.MAT_6 -> DebugPalette(
                side = Blocks.WHITE_CONCRETE.defaultBlockState(),
                up = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState(),
                down = Blocks.GRAY_CONCRETE.defaultBlockState()
            )
            CopycatCogwheelBlockEntity.Slot.MAT_7 -> DebugPalette(
                side = Blocks.BLACK_CONCRETE.defaultBlockState(),
                up = Blocks.GRAY_CONCRETE.defaultBlockState(),
                down = Blocks.BROWN_CONCRETE.defaultBlockState()
            )
        }
    }

    private data class DebugPalette(
        val side: BlockState,
        val up: BlockState,
        val down: BlockState
    )

    private enum class PartKind {
        SHAFT, INNER, OUTER, TOOTH, UNKNOWN
    }

    private enum class RenderLayer {
        ALL, STATIC, SHAFT_ONLY, ROTATING
    }

    private data class Bounds(
        val minX: Float,
        val minY: Float,
        val minZ: Float,
        val maxX: Float,
        val maxY: Float,
        val maxZ: Float
    )

    private data class BoundsD(
        val minX: Double,
        val minY: Double,
        val minZ: Double,
        val maxX: Double,
        val maxY: Double,
        val maxZ: Double
    )
}
