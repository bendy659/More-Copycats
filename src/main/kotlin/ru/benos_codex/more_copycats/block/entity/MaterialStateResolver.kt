package ru.benos_codex.more_copycats.block.entity

import com.zurrtum.create.content.redstone.RoseQuartzLampBlock
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties

object MaterialStateResolver {
    fun resolve(level: Level?, pos: BlockPos, hostState: BlockState, material: BlockState): BlockState {
        var resolved = material

        val powered = when {
            hostState.hasProperty(BlockStateProperties.POWERED) ->
                hostState.getValue(BlockStateProperties.POWERED)

            level != null ->
                level.hasNeighborSignal(pos)

            else -> false
        }

        val lit = when {
            hostState.hasProperty(BlockStateProperties.LIT) ->
                hostState.getValue(BlockStateProperties.LIT)

            else -> powered
        }

        if (resolved.hasProperty(BlockStateProperties.POWERED)) {
            resolved = resolved.setValue(BlockStateProperties.POWERED, powered)
        }
        if (resolved.hasProperty(BlockStateProperties.LIT)) {
            resolved = resolved.setValue(BlockStateProperties.LIT, lit)
        }
        if (resolved.hasProperty(RoseQuartzLampBlock.POWERING)) {
            resolved = resolved.setValue(RoseQuartzLampBlock.POWERING, powered)
        }

        return resolved
    }
}
