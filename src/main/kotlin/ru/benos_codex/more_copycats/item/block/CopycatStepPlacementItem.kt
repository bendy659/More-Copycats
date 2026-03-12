package ru.benos_codex.more_copycats.item.block

import net.minecraft.core.Direction
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.block.state.BlockState
import ru.benos_codex.more_copycats.block.CopycatStepBlock
import kotlin.math.abs

class CopycatStepPlacementItem(
    block: CopycatStepBlock,
    properties: Properties,
    private val mode: Mode
) : BlockItem(block, properties) {
    enum class Mode {
        HORIZONTAL,
        VERTICAL
    }

    override fun getPlacementState(context: BlockPlaceContext): BlockState? {
        val state = super.getPlacementState(context) ?: return null
        val clickedState = context.level.getBlockState(context.clickedPos)
        if (context.replacingClickedOnBlock() && clickedState.`is`(block)) {
            return state
        }
        return state.setValue(CopycatStepBlock.AXIS, axisForPlacement(context))
    }

    fun axisForPlacement(context: BlockPlaceContext): Direction.Axis {
        return when (mode) {
            Mode.VERTICAL -> Direction.Axis.Y
            Mode.HORIZONTAL -> {
                val placePos = if (context.replacingClickedOnBlock()) {
                    context.clickedPos
                } else {
                    context.clickedPos.relative(context.clickedFace)
                }
                val localX = context.clickLocation.x - placePos.x
                val localZ = context.clickLocation.z - placePos.z
                val dx = abs(localX - 0.5)
                val dz = abs(localZ - 0.5)
                when {
                    dx > dz -> Direction.Axis.X
                    dz > dx -> Direction.Axis.Z
                    else -> context.horizontalDirection.axis
                }
            }
        }
    }
}
