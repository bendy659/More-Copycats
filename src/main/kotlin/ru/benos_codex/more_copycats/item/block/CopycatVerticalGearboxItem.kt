package ru.benos_codex.more_copycats.item.block

import com.zurrtum.create.content.kinetics.base.IRotate
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.item.Item
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import ru.benos_codex.more_copycats.block.CopycatGearboxBlock

class CopycatVerticalGearboxItem(
    block: CopycatGearboxBlock,
    properties: Properties
) : BlockItem(block, properties) {
    override fun registerBlocks(blockToItemMap: MutableMap<Block, Item>, item: Item) = Unit

    override fun getPlacementState(context: BlockPlaceContext): BlockState? {
        val state = super.getPlacementState(context) ?: return null
        val pos = if (context.replacingClickedOnBlock()) context.clickedPos else context.clickedPos.relative(context.clickedFace)
        val preferredAxis = preferredAxis(context, pos)
        val axis = preferredAxis?.let(::perpendicularAxis) ?: context.horizontalDirection.axis

        return state.setValue(BlockStateProperties.AXIS, axis)
    }

    private fun preferredAxis(context: BlockPlaceContext, pos: BlockPos): Direction.Axis? {
        var preferred: Direction.Axis? = null

        for (side in Direction.Plane.HORIZONTAL) {
            val neighbourPos = pos.relative(side)
            val neighbourState = context.level.getBlockState(neighbourPos)
            val rotate = neighbourState.block as? IRotate ?: continue
            if (!rotate.hasShaftTowards(context.level, neighbourPos, neighbourState, side.opposite))
                continue

            val axis = side.axis
            if (preferred != null && preferred != axis)
                return null

            preferred = axis
        }

        return preferred
    }

    private fun perpendicularAxis(axis: Direction.Axis): Direction.Axis =
        if (axis == Direction.Axis.X) Direction.Axis.Z else Direction.Axis.X
}
