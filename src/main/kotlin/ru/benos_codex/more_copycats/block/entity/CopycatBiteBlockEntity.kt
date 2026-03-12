package ru.benos_codex.more_copycats.block.entity

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.state.BlockState

class CopycatBiteBlockEntity(pos: BlockPos, state: BlockState) :
    AbstractCopycatPartBlockEntity(pos, state, MAX_ITEM, PART_SIZE) {
    companion object {
        const val MAX_ITEM: Int = 64
        const val FACES: Int = AbstractCopycatPartBlockEntity.FACES
        private const val PART_SIZE: Int = 4
    }

    override fun partCoords(index: Int): Triple<Int, Int, Int> {
        val x = index and 3
        val y = (index shr 2) and 3
        val z = (index shr 4) and 3

        return Triple(x, y, z)
    }
}
