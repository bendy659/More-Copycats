package ru.benos_codex.more_copycats.block.entity

import com.zurrtum.create.content.decoration.copycat.CopycatBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.core.Direction
import com.mojang.serialization.Codec
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import ru.benos_codex.more_copycats.MoreCopycats.DEFAULT_MATERIAL

class CopycatBiteBlockEntity(pos: BlockPos, state: BlockState): CopycatBlockEntity(pos, state) {
    companion object {
        const val MAX_ITEM: Int = 64
        const val FACES: Int = 6
    }

    private val partExists: BooleanArray = BooleanArray(MAX_ITEM) { false }
    val states: Array<BlockState?> = Array(MAX_ITEM * FACES) { null }
    val stacks: Array<ItemStack> = Array(MAX_ITEM * FACES) { ItemStack.EMPTY }
    val uvU: IntArray = IntArray(MAX_ITEM * FACES) { 0 }
    val uvV: IntArray = IntArray(MAX_ITEM * FACES) { 0 }
    val uvW: IntArray = IntArray(MAX_ITEM * FACES) { 4 }
    val uvH: IntArray = IntArray(MAX_ITEM * FACES) { 4 }
    val sourceFace: ByteArray = ByteArray(MAX_ITEM * FACES) { 0 }
    private val prevUvU: IntArray = IntArray(MAX_ITEM * FACES) { 0 }
    private val prevUvV: IntArray = IntArray(MAX_ITEM * FACES) { 0 }
    private val uvChangeTick: IntArray = IntArray(MAX_ITEM * FACES) { 0 }

    val isEmpty: Boolean get() = partExists.none { it }

    fun isPartEmpty(index: Int): Boolean = !partExists[index]

    fun getFaceIndex(partIndex: Int, face: Direction): Int =
        partIndex * FACES + face.ordinal

    fun getFaceState(partIndex: Int, face: Direction): BlockState? =
        states[getFaceIndex(partIndex, face)]

    fun getFaceStack(partIndex: Int, face: Direction): ItemStack =
        stacks[getFaceIndex(partIndex, face)]

    fun isFaceHasMaterial(partIndex: Int, face: Direction): Boolean {
        if (isPartEmpty(partIndex)) return false
        val state = getFaceState(partIndex, face) ?: return false
        return state != DEFAULT_MATERIAL
    }

    fun isPartHasAnyMaterial(partIndex: Int): Boolean {
        if (isPartEmpty(partIndex)) return false
        for (f in Direction.entries) {
            if (isFaceHasMaterial(partIndex, f)) return true
        }
        return false
    }

    fun addPart(index: Int) {
        partExists[index] = true
        val (px, py, pz) = partCoords(index)
        val partSize = 4
        for (f in Direction.entries) {
            val fi = getFaceIndex(index, f)
            states[fi] = DEFAULT_MATERIAL
            stacks[fi] = ItemStack.EMPTY
            val uv = defaultUvForFace(px, py, pz, partSize, f)
            uvU[fi] = uv.first
            uvV[fi] = uv.second
            uvW[fi] = partSize
            uvH[fi] = partSize
            sourceFace[fi] = f.ordinal.toByte()
        }
        notifyUpdate()
    }

    fun removePart(index: Int) {
        partExists[index] = false
        val partSize = 4
        for (f in Direction.entries) {
            val fi = getFaceIndex(index, f)
            states[fi] = null
            stacks[fi] = ItemStack.EMPTY
            uvU[fi] = 0
            uvV[fi] = 0
            uvW[fi] = partSize
            uvH[fi] = partSize
            sourceFace[fi] = f.ordinal.toByte()
        }
        notifyUpdate()
    }

    fun setMaterialAllFaces(partIndex: Int, material: BlockState) {
        for (f in Direction.entries) {
            val fi = getFaceIndex(partIndex, f)
            states[fi] = material
            stacks[fi] = ItemStack.EMPTY
        }
        notifyUpdate()
    }

    fun setMaterialFace(partIndex: Int, face: Direction, material: BlockState) {
        val fi = getFaceIndex(partIndex, face)
        states[fi] = material
        stacks[fi] = ItemStack.EMPTY
        notifyUpdate()
    }

    fun clearMaterialFace(partIndex: Int, face: Direction) {
        val fi = getFaceIndex(partIndex, face)
        states[fi] = DEFAULT_MATERIAL
        stacks[fi] = ItemStack.EMPTY
        notifyUpdate()
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
        if (partIndex !in 0 until MAX_ITEM) return
        val fi = getFaceIndex(partIndex, face)
        if (states[fi] == null) return

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

    private fun partCoords(index: Int): Triple<Int, Int, Int> {
        val x = index and 3
        val y = (index shr 2) and 3
        val z = (index shr 4) and 3
        return Triple(x, y, z)
    }

    private fun defaultUvForFace(px: Int, py: Int, pz: Int, size: Int, face: Direction): Pair<Int, Int> {
        val u = when (face) {
            Direction.EAST, Direction.WEST -> pz * size
            Direction.UP, Direction.DOWN -> px * size
            else -> px * size
        }
        val v = when (face) {
            Direction.EAST, Direction.WEST -> py * size
            Direction.UP, Direction.DOWN -> pz * size
            else -> py * size
        }
        return Pair(u, v)
    }

    fun getUvUFloat(partIndex: Int, face: Direction, time: Long): Float =
        interpolateUv(partIndex, face, time, true)

    fun getUvVFloat(partIndex: Int, face: Direction, time: Long): Float =
        interpolateUv(partIndex, face, time, false)

    private fun interpolateUv(partIndex: Int, face: Direction, time: Long, isU: Boolean): Float {
        val fi = getFaceIndex(partIndex, face)
        val t0 = uvChangeTick[fi]
        val dt = (time.toInt() - t0).coerceAtLeast(0)
        val alpha = (dt / 6f).coerceIn(0f, 1f)

        val prev = if (isU) prevUvU[fi] else prevUvV[fi]
        val target = if (isU) uvU[fi] else uvV[fi]
        return (prev + (target - prev) * alpha)
    }

    fun cycleMaterialFace(partIndex: Int, face: Direction): Boolean {
        val current = getFaceState(partIndex, face) ?: return false
        if (current == DEFAULT_MATERIAL) return false

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

        if (cycled == null) return false
        val fi = getFaceIndex(partIndex, face)
        states[fi] = cycled
        notifyUpdate()
        return true
    }

    override fun write(view: ValueOutput, clientPacket: Boolean) {
        super.write(view, clientPacket)

        var partMask = 0L
        for (i in 0 until MAX_ITEM) if (partExists[i]) partMask = partMask or (1L shl i)
        view.store("PartMask", net.minecraft.util.ExtraCodecs.NON_NEGATIVE_LONG, partMask)

        for (p in 0 until MAX_ITEM) {
            if (!partExists[p]) continue
            for (f in Direction.entries) {
                val fi = getFaceIndex(p, f)
                val state = states[fi]
                if (state != null && state != DEFAULT_MATERIAL) {
                    view.store("State_${p}_${f.ordinal}", BlockState.CODEC, state)
                    view.store("Stack_${p}_${f.ordinal}", ItemStack.OPTIONAL_CODEC, stacks[fi])
                }
                val u = uvU[fi]
                val v = uvV[fi]
                if (u != 0) view.store("UVU_${p}_${f.ordinal}", Codec.INT, u)
                if (v != 0) view.store("UVV_${p}_${f.ordinal}", Codec.INT, v)
                val w = uvW[fi]
                val h = uvH[fi]
                if (w != 4) view.store("UVW_${p}_${f.ordinal}", Codec.INT, w)
                if (h != 4) view.store("UVH_${p}_${f.ordinal}", Codec.INT, h)
                val sf = sourceFace[fi].toInt() and 0x7
                if (sf != f.ordinal) {
                    view.store("SF_${p}_${f.ordinal}", net.minecraft.util.ExtraCodecs.NON_NEGATIVE_INT, sf)
                }
            }
        }
    }

    override fun read(view: ValueInput, clientPacket: Boolean) {
        super.read(view, clientPacket)

        val partMask = view.read("PartMask", net.minecraft.util.ExtraCodecs.NON_NEGATIVE_LONG).orElse(0L)
        for (p in 0 until MAX_ITEM) {
            val exists = partMask and (1L shl p) != 0L
            partExists[p] = exists
            val (px, py, pz) = partCoords(p)
            val partSize = 4
            for (f in Direction.entries) {
                val fi = getFaceIndex(p, f)
                if (exists) {
                    states[fi] = DEFAULT_MATERIAL
                    stacks[fi] = ItemStack.EMPTY
                    val uv = defaultUvForFace(px, py, pz, partSize, f)
                    uvU[fi] = uv.first
                    uvV[fi] = uv.second
                    uvW[fi] = partSize
                    uvH[fi] = partSize
                    sourceFace[fi] = f.ordinal.toByte()

                    val state = view.read("State_${p}_${f.ordinal}", BlockState.CODEC).orElse(null)
                    if (state != null) {
                        states[fi] = state
                        stacks[fi] = view.read("Stack_${p}_${f.ordinal}", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY)
                    }
                    val uNew = view.read("UVU_${p}_${f.ordinal}", Codec.INT).orElse(Int.MIN_VALUE)
                    val vNew = view.read("UVV_${p}_${f.ordinal}", Codec.INT).orElse(Int.MIN_VALUE)
                    if (uNew != Int.MIN_VALUE || vNew != Int.MIN_VALUE) {
                        if (uNew != Int.MIN_VALUE) uvU[fi] = uNew
                        if (vNew != Int.MIN_VALUE) uvV[fi] = vNew
                    } else {
                        val packed = view.read("UV_${p}_${f.ordinal}", net.minecraft.util.ExtraCodecs.NON_NEGATIVE_INT).orElse(0)
                        if (packed != 0) {
                            uvU[fi] = (packed and 0xF)
                            uvV[fi] = ((packed shr 4) and 0xF)
                        }
                    }
                    val wNew = view.read("UVW_${p}_${f.ordinal}", Codec.INT).orElse(Int.MIN_VALUE)
                    val hNew = view.read("UVH_${p}_${f.ordinal}", Codec.INT).orElse(Int.MIN_VALUE)
                    if (wNew != Int.MIN_VALUE || hNew != Int.MIN_VALUE) {
                        if (wNew != Int.MIN_VALUE) uvW[fi] = wNew
                        if (hNew != Int.MIN_VALUE) uvH[fi] = hNew
                    } else {
                        val wh = view.read("WH_${p}_${f.ordinal}", net.minecraft.util.ExtraCodecs.NON_NEGATIVE_INT).orElse(0)
                        if (wh != 0) {
                            uvW[fi] = (wh and 0x1F)
                            uvH[fi] = ((wh shr 5) and 0x1F)
                        }
                    }
                    val sf = view.read("SF_${p}_${f.ordinal}", net.minecraft.util.ExtraCodecs.NON_NEGATIVE_INT).orElse(f.ordinal)
                    sourceFace[fi] = sf.toByte()
                } else {
                    states[fi] = null
                    stacks[fi] = ItemStack.EMPTY
                    uvU[fi] = 0
                    uvV[fi] = 0
                    uvW[fi] = partSize
                    uvH[fi] = partSize
                    sourceFace[fi] = f.ordinal.toByte()
                }
            }
        }

        if (clientPacket)
            level?.sendBlockUpdated(worldPosition, blockState, blockState, 8)
    }

    override fun getUpdateTag(registries: HolderLookup.Provider): CompoundTag =
        saveWithFullMetadata(registries)

    override fun getUpdatePacket(): ClientboundBlockEntityDataPacket =
        ClientboundBlockEntityDataPacket.create(this)
}
