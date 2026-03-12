package ru.benos_codex.more_copycats.block.entity

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.state.BlockState

class CopycatByteBlockEntity(pos: BlockPos, state: BlockState) :
    AbstractCopycatPartBlockEntity(pos, state, MAX_ITEM, PART_SIZE) {
    companion object {
        const val MAX_ITEM: Int = 8
        const val FACES: Int = AbstractCopycatPartBlockEntity.FACES
        private const val PART_SIZE: Int = 8
    }

    override fun partCoords(index: Int): Triple<Int, Int, Int> {
        val x = index and 1
        val y = (index shr 1) and 1
        val z = (index shr 2) and 1

        return Triple(x, y, z)
    }
}
