package ru.benos_codex.more_copycats.block.entity

import com.mojang.serialization.Codec
import com.zurrtum.create.content.decoration.copycat.CopycatBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.util.ExtraCodecs
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import ru.benos_codex.more_copycats.MoreCopycats.DEFAULT_MATERIAL

abstract class AbstractCopycatPartBlockEntity(
    pos: BlockPos,
    state: BlockState,
    private val maxItems: Int,
    private val partSize: Int
) : CopycatBlockEntity(pos, state) {
    companion object {
        const val FACES: Int = 6
    }

    private val partsPerAxis = 16 / partSize
    private val partStep = 1.0 / partsPerAxis

    private val partExists: BooleanArray = BooleanArray(maxItems) { false }
    val states: Array<BlockState?> = Array(maxItems * FACES) { null }
    val stacks: Array<ItemStack> = Array(maxItems * FACES) { ItemStack.EMPTY }
    val uvU: IntArray = IntArray(maxItems * FACES) { 0 }
    val uvV: IntArray = IntArray(maxItems * FACES) { 0 }
    val uvW: IntArray = IntArray(maxItems * FACES) { partSize }
    val uvH: IntArray = IntArray(maxItems * FACES) { partSize }
    val sourceFace: ByteArray = ByteArray(maxItems * FACES) { 0 }
    private val prevUvU: IntArray = IntArray(maxItems * FACES) { 0 }
    private val prevUvV: IntArray = IntArray(maxItems * FACES) { 0 }
    private val uvChangeTick: IntArray = IntArray(maxItems * FACES) { 0 }

    private var shapeDirty: Boolean = true
    private var cachedShape: VoxelShape = Shapes.empty()

    val isEmpty: Boolean
        get() = partExists.none { it }

    val combinedShape: VoxelShape
        get() {
            if (shapeDirty) {
                cachedShape = rebuildShape()
                shapeDirty = false
            }

            return cachedShape
        }

    fun getMaxLightEmission(): Int {
        var maxLight = 0
        for (state in states) {
            val material = state ?: continue
            maxLight = maxOf(
                maxLight,
                MaterialStateResolver.resolve(level, worldPosition, blockState, material).lightEmission
            )
        }
        return maxLight
    }

    fun isPartEmpty(index: Int): Boolean = !partExists[index]

    fun getFaceIndex(partIndex: Int, face: Direction): Int =
        partIndex * FACES + face.ordinal

    fun getFaceState(partIndex: Int, face: Direction): BlockState? =
        states[getFaceIndex(partIndex, face)]

    fun getFaceStack(partIndex: Int, face: Direction): ItemStack =
        stacks[getFaceIndex(partIndex, face)]

    fun isFaceHasMaterial(partIndex: Int, face: Direction): Boolean {
        if (isPartEmpty(partIndex)) {
            return false
        }

        val state = getFaceState(partIndex, face) ?: return false

        return state != DEFAULT_MATERIAL
    }

    fun isPartHasAnyMaterial(partIndex: Int): Boolean {
        if (isPartEmpty(partIndex)) {
            return false
        }

        for (face in Direction.entries) {
            if (isFaceHasMaterial(partIndex, face)) {
                return true
            }
        }

        return false
    }

    fun addPart(index: Int) {
        if (index !in 0 until maxItems) {
            return
        }

        partExists[index] = true
        initPart(index, exists = true)
        markGeometryChanged()
        notifyAndSync()
    }

    fun removePart(index: Int) {
        if (index !in 0 until maxItems) {
            return
        }

        partExists[index] = false
        initPart(index, exists = false)
        markGeometryChanged()
        notifyAndSync()
    }

    fun setMaterialAllFaces(partIndex: Int, material: BlockState) {
        for (face in Direction.entries) {
            val fi = getFaceIndex(partIndex, face)
            states[fi] = material
            stacks[fi] = ItemStack.EMPTY
        }

        notifyAndSync()
    }

    fun setMaterialFace(partIndex: Int, face: Direction, material: BlockState) {
        val fi = getFaceIndex(partIndex, face)
        states[fi] = material
        stacks[fi] = ItemStack.EMPTY

        notifyAndSync()
    }

    fun clearMaterialFace(partIndex: Int, face: Direction) {
        val fi = getFaceIndex(partIndex, face)
        states[fi] = DEFAULT_MATERIAL
        stacks[fi] = ItemStack.EMPTY

        notifyAndSync()
    }

    fun setUvOffset(partIndex: Int, face: Direction, u: Int, v: Int) {
        val fi = getFaceIndex(partIndex, face)
        prevUvU[fi] = uvU[fi]
        prevUvV[fi] = uvV[fi]
        uvChangeTick[fi] = (level?.gameTime ?: 0L).toInt()
        uvU[fi] = u
        uvV[fi] = v

        notifyUpdate()
    }

    fun shiftUv(partIndex: Int, face: Direction, du: Int, dv: Int) {
        val fi = getFaceIndex(partIndex, face)
        prevUvU[fi] = uvU[fi]
        prevUvV[fi] = uvV[fi]
        uvChangeTick[fi] = (level?.gameTime ?: 0L).toInt()
        uvU[fi] = uvU[fi] + du
        uvV[fi] = uvV[fi] + dv

        notifyUpdate()
    }

    fun getUvU(partIndex: Int, face: Direction): Int =
        uvU[getFaceIndex(partIndex, face)]

    fun getUvV(partIndex: Int, face: Direction): Int =
        uvV[getFaceIndex(partIndex, face)]

    fun getUvW(partIndex: Int, face: Direction): Int =
        uvW[getFaceIndex(partIndex, face)]

    fun getUvH(partIndex: Int, face: Direction): Int =
        uvH[getFaceIndex(partIndex, face)]

    fun setUvSize(partIndex: Int, face: Direction, w: Int, h: Int) {
        val fi = getFaceIndex(partIndex, face)
        uvW[fi] = w.coerceAtLeast(1)
        uvH[fi] = h.coerceAtLeast(1)

        notifyUpdate()
    }

    fun getSourceFace(partIndex: Int, face: Direction): Direction {
        val fi = getFaceIndex(partIndex, face)
        val ord = sourceFace[fi].toInt() and 0x7

        return Direction.entries.getOrElse(ord) { face }
    }

    fun setSourceFace(partIndex: Int, face: Direction, source: Direction) {
        val fi = getFaceIndex(partIndex, face)
        sourceFace[fi] = source.ordinal.toByte()

        notifyUpdate()
    }

    fun applyUvEdit(
        partIndex: Int,
        face: Direction,
        u: Int,
        v: Int,
        w: Int,
        h: Int,
        source: Direction,
        material: BlockState?
    ) {
        if (partIndex !in 0 until maxItems) {
            return
        }

        val fi = getFaceIndex(partIndex, face)
        if (states[fi] == null) {
            return
        }

        if (material != null) {
            states[fi] = material
            stacks[fi] = ItemStack.EMPTY
        }

        prevUvU[fi] = uvU[fi]
        prevUvV[fi] = uvV[fi]
        uvChangeTick[fi] = (level?.gameTime ?: 0L).toInt()
        uvU[fi] = u
        uvV[fi] = v
        uvW[fi] = w.coerceAtLeast(1)
        uvH[fi] = h.coerceAtLeast(1)
        sourceFace[fi] = source.ordinal.toByte()

        notifyUpdate()
    }

    fun getUvUFloat(partIndex: Int, face: Direction, time: Long): Float =
        interpolateUv(partIndex, face, time, isU = true)

    fun getUvVFloat(partIndex: Int, face: Direction, time: Long): Float =
        interpolateUv(partIndex, face, time, isU = false)

    fun cycleMaterialFace(partIndex: Int, face: Direction): Boolean {
        val current = getFaceState(partIndex, face) ?: return false
        if (current == DEFAULT_MATERIAL) {
            return false
        }

        val cycled: BlockState? = when {
            current.hasProperty(BlockStateProperties.FACING) ->
                current.cycle(BlockStateProperties.FACING)

            current.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ->
                current.setValue(
                    BlockStateProperties.HORIZONTAL_FACING,
                    current.getValue(BlockStateProperties.HORIZONTAL_FACING).clockWise
                )

            current.hasProperty(BlockStateProperties.AXIS) ->
                current.cycle(BlockStateProperties.AXIS)

            current.hasProperty(BlockStateProperties.HORIZONTAL_AXIS) ->
                current.cycle(BlockStateProperties.HORIZONTAL_AXIS)

            else -> null
        }

        if (cycled == null) {
            return false
        }

        val fi = getFaceIndex(partIndex, face)
        states[fi] = cycled
        notifyAndSync()

        return true
    }

    override fun write(view: ValueOutput, clientPacket: Boolean) {
        super.write(view, clientPacket)

        var partMask = 0L
        for (i in 0 until maxItems) {
            if (partExists[i]) {
                partMask = partMask or (1L shl i)
            }
        }
        view.store("PartMask", ExtraCodecs.NON_NEGATIVE_LONG, partMask)

        for (part in 0 until maxItems) {
            if (!partExists[part]) {
                continue
            }

            for (face in Direction.entries) {
                val fi = getFaceIndex(part, face)
                val state = states[fi]
                if (state != null && state != DEFAULT_MATERIAL) {
                    view.store("State_${part}_${face.ordinal}", BlockState.CODEC, state)
                    view.store("Stack_${part}_${face.ordinal}", ItemStack.OPTIONAL_CODEC, stacks[fi])
                }

                val u = uvU[fi]
                val v = uvV[fi]
                if (u != 0) view.store("UVU_${part}_${face.ordinal}", Codec.INT, u)
                if (v != 0) view.store("UVV_${part}_${face.ordinal}", Codec.INT, v)

                val w = uvW[fi]
                val h = uvH[fi]
                if (w != partSize) view.store("UVW_${part}_${face.ordinal}", Codec.INT, w)
                if (h != partSize) view.store("UVH_${part}_${face.ordinal}", Codec.INT, h)

                val sf = sourceFace[fi].toInt() and 0x7
                if (sf != face.ordinal) {
                    view.store("SF_${part}_${face.ordinal}", ExtraCodecs.NON_NEGATIVE_INT, sf)
                }
            }
        }
    }

    override fun read(view: ValueInput, clientPacket: Boolean) {
        super.read(view, clientPacket)

        val partMask = view.read("PartMask", ExtraCodecs.NON_NEGATIVE_LONG).orElse(0L)
        for (part in 0 until maxItems) {
            val exists = (partMask and (1L shl part)) != 0L
            partExists[part] = exists
            initPart(part, exists)

            if (!exists) {
                continue
            }

            for (face in Direction.entries) {
                val fi = getFaceIndex(part, face)
                val state = view.read("State_${part}_${face.ordinal}", BlockState.CODEC).orElse(null)
                if (state != null) {
                    states[fi] = state
                    stacks[fi] = view.read("Stack_${part}_${face.ordinal}", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY)
                }

                val uNew = view.read("UVU_${part}_${face.ordinal}", Codec.INT).orElse(Int.MIN_VALUE)
                val vNew = view.read("UVV_${part}_${face.ordinal}", Codec.INT).orElse(Int.MIN_VALUE)
                if (uNew != Int.MIN_VALUE || vNew != Int.MIN_VALUE) {
                    if (uNew != Int.MIN_VALUE) uvU[fi] = uNew
                    if (vNew != Int.MIN_VALUE) uvV[fi] = vNew
                } else {
                    val packed = view.read("UV_${part}_${face.ordinal}", ExtraCodecs.NON_NEGATIVE_INT).orElse(0)
                    if (packed != 0) {
                        uvU[fi] = packed and 0xF
                        uvV[fi] = (packed shr 4) and 0xF
                    }
                }

                val wNew = view.read("UVW_${part}_${face.ordinal}", Codec.INT).orElse(Int.MIN_VALUE)
                val hNew = view.read("UVH_${part}_${face.ordinal}", Codec.INT).orElse(Int.MIN_VALUE)
                if (wNew != Int.MIN_VALUE || hNew != Int.MIN_VALUE) {
                    if (wNew != Int.MIN_VALUE) uvW[fi] = wNew
                    if (hNew != Int.MIN_VALUE) uvH[fi] = hNew
                } else {
                    val wh = view.read("WH_${part}_${face.ordinal}", ExtraCodecs.NON_NEGATIVE_INT).orElse(0)
                    if (wh != 0) {
                        uvW[fi] = wh and 0x1F
                        uvH[fi] = (wh shr 5) and 0x1F
                    }
                }

                val sf = view.read("SF_${part}_${face.ordinal}", ExtraCodecs.NON_NEGATIVE_INT).orElse(face.ordinal)
                sourceFace[fi] = sf.toByte()
            }
        }

        markGeometryChanged()

        if (clientPacket) {
            level?.sendBlockUpdated(worldPosition, blockState, blockState, 8)
        }
    }

    override fun getUpdateTag(registries: HolderLookup.Provider): CompoundTag =
        saveWithFullMetadata(registries)

    override fun getUpdatePacket(): ClientboundBlockEntityDataPacket =
        ClientboundBlockEntityDataPacket.create(this)

    protected abstract fun partCoords(index: Int): Triple<Int, Int, Int>

    private fun interpolateUv(partIndex: Int, face: Direction, time: Long, isU: Boolean): Float {
        val fi = getFaceIndex(partIndex, face)
        val t0 = uvChangeTick[fi]
        val dt = (time.toInt() - t0).coerceAtLeast(0)
        val alpha = (dt / 6f).coerceIn(0f, 1f)

        val prev = if (isU) prevUvU[fi] else prevUvV[fi]
        val target = if (isU) uvU[fi] else uvV[fi]

        return prev + (target - prev) * alpha
    }

    private fun initPart(index: Int, exists: Boolean) {
        val (px, py, pz) = partCoords(index)
        for (face in Direction.entries) {
            val fi = getFaceIndex(index, face)
            if (exists) {
                states[fi] = DEFAULT_MATERIAL
                stacks[fi] = ItemStack.EMPTY
                val uv = defaultUvForFace(px, py, pz, face)
                uvU[fi] = uv.first
                uvV[fi] = uv.second
                uvW[fi] = partSize
                uvH[fi] = partSize
                sourceFace[fi] = face.ordinal.toByte()
            } else {
                states[fi] = null
                stacks[fi] = ItemStack.EMPTY
                uvU[fi] = 0
                uvV[fi] = 0
                uvW[fi] = partSize
                uvH[fi] = partSize
                sourceFace[fi] = face.ordinal.toByte()
            }
        }
    }

    private fun defaultUvForFace(px: Int, py: Int, pz: Int, face: Direction): Pair<Int, Int> {
        val u = when (face) {
            Direction.EAST, Direction.WEST -> pz * partSize
            Direction.UP, Direction.DOWN -> px * partSize
            else -> px * partSize
        }
        val v = when (face) {
            Direction.EAST, Direction.WEST -> py * partSize
            Direction.UP, Direction.DOWN -> pz * partSize
            else -> py * partSize
        }

        return Pair(u, v)
    }

    private fun rebuildShape(): VoxelShape {
        var shape = Shapes.empty()
        for (index in 0 until maxItems) {
            if (partExists[index].not()) {
                continue
            }

            val (px, py, pz) = partCoords(index)
            val minX = px * partStep
            val minY = py * partStep
            val minZ = pz * partStep
            shape = Shapes.or(shape, Shapes.box(minX, minY, minZ, minX + partStep, minY + partStep, minZ + partStep))
        }

        return shape
    }

    private fun markGeometryChanged() {
        shapeDirty = true
    }

    private fun notifyAndSync() {
        notifyUpdate()
        syncVisuals()
    }

    private fun syncVisuals() {
        val currentLevel = level ?: return
        val state = blockState
        MaterialLightHelper.refresh(currentLevel, worldPosition, state, 8)
    }
}
