package ru.benos_codex.more_copycats.block.entity

import net.minecraft.core.BlockPos
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState

object MaterialLightHelper {
    fun resolvedEmission(world: BlockGetter, pos: BlockPos, hostState: BlockState, material: BlockState): Int =
        MaterialStateResolver.resolve(world as? Level, pos, hostState, material).lightEmission

    fun refresh(level: Level, pos: BlockPos, state: BlockState, flags: Int) {
        level.sendBlockUpdated(pos, state, state, flags)
        level.chunkSource.lightEngine.checkBlock(pos)
    }
}
