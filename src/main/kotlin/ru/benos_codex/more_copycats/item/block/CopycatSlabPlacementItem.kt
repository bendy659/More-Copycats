package ru.benos_codex.more_copycats.item.block

import net.minecraft.core.Direction
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.SlabType
import ru.benos_codex.more_copycats.block.CopycatSlabBlock

class CopycatSlabPlacementItem(
    block: CopycatSlabBlock,
    properties: Item.Properties,
    private val mode: Mode
) : BlockItem(block, properties) {
    enum class Mode {
        HORIZONTAL,
        VERTICAL
    }

    override fun place(context: BlockPlaceContext): InteractionResult {
        val merged = tryMergeIntoClickedBlockSpace(context)
        if (merged != null) return merged
        return super.place(context)
    }

    override fun getPlacementState(context: BlockPlaceContext): BlockState? {
        val state = super.getPlacementState(context) ?: return null
        val current = context.level.getBlockState(context.clickedPos)
        if (context.replacingClickedOnBlock() && current.`is`(block)) {
            return state
        }

        return when (mode) {
            Mode.HORIZONTAL -> state.setValue(CopycatSlabBlock.AXIS, Direction.Axis.Y)
            Mode.VERTICAL -> {
                val axis = context.horizontalDirection.opposite.axis
                val verticalAxis = if (axis == Direction.Axis.X) Direction.Axis.X else Direction.Axis.Z
                val type = resolveVerticalType(context, verticalAxis)
                state
                    .setValue(CopycatSlabBlock.AXIS, verticalAxis)
                    .setValue(CopycatSlabBlock.TYPE, type)
            }
        }
    }

    private fun tryMergeIntoClickedBlockSpace(context: BlockPlaceContext): InteractionResult? {
        val primaryPos = context.clickedPos
        val secondaryPos = primaryPos.relative(context.clickedFace.opposite)
        val first = tryMergeAt(context, primaryPos)
        if (first != null) return first
        if (secondaryPos == primaryPos) return null
        return tryMergeAt(context, secondaryPos)
    }

    private fun tryMergeAt(context: BlockPlaceContext, mergePos: net.minecraft.core.BlockPos): InteractionResult? {
        val level = context.level
        val mergeState = level.getBlockState(mergePos)
        if (!mergeState.`is`(block)) return null
        if (mergeState.getValue(CopycatSlabBlock.TYPE) == SlabType.DOUBLE) return null

        val targetAxis = when (mode) {
            Mode.HORIZONTAL -> Direction.Axis.Y
            Mode.VERTICAL -> {
                val axis = context.horizontalDirection.opposite.axis
                if (axis == Direction.Axis.X) Direction.Axis.X else Direction.Axis.Z
            }
        }
        if (mergeState.getValue(CopycatSlabBlock.AXIS) != targetAxis) return null

        val targetType = when (mode) {
            Mode.HORIZONTAL -> resolveHorizontalType(context, mergePos)
            Mode.VERTICAL -> resolveVerticalType(context, targetAxis, mergePos)
        }
        if (targetType == mergeState.getValue(CopycatSlabBlock.TYPE)) return null

        if (level.isClientSide) {
            return InteractionResult.SUCCESS
        }

        val updatedState = mergeState
            .setValue(CopycatSlabBlock.TYPE, SlabType.DOUBLE)
            .setValue(BlockStateProperties.WATERLOGGED, false)
        if (!level.setBlock(mergePos, updatedState, 11)) return null

        val player = context.player
        val soundType = updatedState.soundType
        level.playSound(
            player,
            mergePos,
            soundType.placeSound,
            SoundSource.BLOCKS,
            (soundType.volume + 1.0f) / 2.0f,
            soundType.pitch * 0.8f
        )

        val stack = context.itemInHand
        if (player == null || !player.isCreative) {
            stack.shrink(1)
        }
        return InteractionResult.SUCCESS
    }

    private fun resolveHorizontalType(context: BlockPlaceContext, pos: net.minecraft.core.BlockPos): SlabType {
        val face = context.clickedFace
        if (face.axis == Direction.Axis.Y) {
            return if (face == Direction.UP) SlabType.TOP else SlabType.BOTTOM
        }
        val localY = context.clickLocation.y - pos.y
        return if (localY > 0.5) SlabType.TOP else SlabType.BOTTOM
    }

    private fun resolveVerticalType(context: BlockPlaceContext, axis: Direction.Axis): SlabType {
        return resolveVerticalType(context, axis, context.clickedPos)
    }

    private fun resolveVerticalType(context: BlockPlaceContext, axis: Direction.Axis, pos: net.minecraft.core.BlockPos): SlabType {
        val face = context.clickedFace
        if (face.axis == axis) {
            return when (face.axisDirection) {
                Direction.AxisDirection.POSITIVE -> SlabType.BOTTOM
                Direction.AxisDirection.NEGATIVE -> SlabType.TOP
            }
        }

        val localX = context.clickLocation.x - pos.x
        val localZ = context.clickLocation.z - pos.z
        return when (axis) {
            Direction.Axis.X -> if (localX > 0.5) SlabType.TOP else SlabType.BOTTOM
            Direction.Axis.Z -> if (localZ > 0.5) SlabType.TOP else SlabType.BOTTOM
            Direction.Axis.Y -> SlabType.BOTTOM
        }
    }
}
