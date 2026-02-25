package ru.benos_codex.more_copycats.item.block

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.SlabType
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import ru.benos_codex.more_copycats.block.CopycatSlabBlock

class CopycatSlabPlacementItem(
    block: CopycatSlabBlock,
    properties: Properties,
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

        val axis = axisForMode(context)
        return if (mode == Mode.HORIZONTAL) {
            state.setValue(CopycatSlabBlock.AXIS, axis)
        } else {
            state
                .setValue(CopycatSlabBlock.AXIS, axis)
                .setValue(CopycatSlabBlock.TYPE, resolveVerticalType(context, axis, context.clickedPos))
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

    private fun tryMergeAt(context: BlockPlaceContext, mergePos: BlockPos): InteractionResult? {
        val level = context.level
        val mergeState = level.getBlockState(mergePos)
        if (!mergeState.`is`(block)) return null
        if (mergeState.getValue(CopycatSlabBlock.TYPE) == SlabType.DOUBLE) return null

        val targetAxis = axisForMode(context)
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
            null,
            mergePos,
            soundType.placeSound,
            SoundSource.BLOCKS,
            (soundType.volume + 1.0f) / 2.0f,
            soundType.pitch * 0.8f
        )
        tryApplyOffhandMaterialOnMergedHalf(context, mergePos, targetType)

        val stack = context.itemInHand
        if (player == null || !player.isCreative) {
            stack.shrink(1)
        }
        return InteractionResult.SUCCESS
    }

    private fun tryApplyOffhandMaterialOnMergedHalf(context: BlockPlaceContext, pos: BlockPos, placedHalf: SlabType) {
        val player = context.player ?: return
        val offhand = player.getItemInHand(InteractionHand.OFF_HAND)
        if (offhand.isEmpty) return

        val slab = block as CopycatSlabBlock
        val state = context.level.getBlockState(pos)
        val axis = state.getValue(CopycatSlabBlock.AXIS)
        val hitFace = faceForHalf(axis, placedHalf)
        val hit = BlockHitResult(
            Vec3(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5),
            hitFace,
            pos,
            false
        )
        slab.useItemOn(offhand, state, context.level, pos, player, InteractionHand.OFF_HAND, hit)
    }

    private fun faceForHalf(axis: Direction.Axis, half: SlabType): Direction {
        return when (axis) {
            Direction.Axis.Y -> if (half == SlabType.TOP) Direction.UP else Direction.DOWN
            Direction.Axis.X -> if (half == SlabType.TOP) Direction.EAST else Direction.WEST
            Direction.Axis.Z -> if (half == SlabType.TOP) Direction.SOUTH else Direction.NORTH
        }
    }

    private fun axisForMode(context: BlockPlaceContext): Direction.Axis {
        if (mode == Mode.HORIZONTAL) return Direction.Axis.Y
        return if (context.horizontalDirection.opposite.axis == Direction.Axis.X) Direction.Axis.X else Direction.Axis.Z
    }

    private fun resolveHorizontalType(context: BlockPlaceContext, pos: BlockPos): SlabType {
        val face = context.clickedFace
        if (face.axis == Direction.Axis.Y) {
            return if (face == Direction.UP) SlabType.TOP else SlabType.BOTTOM
        }
        val localY = context.clickLocation.y - pos.y
        return if (localY > 0.5) SlabType.TOP else SlabType.BOTTOM
    }

    private fun resolveVerticalType(context: BlockPlaceContext, axis: Direction.Axis, pos: BlockPos): SlabType {
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
