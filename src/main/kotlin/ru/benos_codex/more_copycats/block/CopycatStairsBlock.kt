package ru.benos_codex.more_copycats.block

import net.minecraft.world.level.block.state.BlockBehaviour.Properties

import com.mojang.math.OctahedralGroup
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.util.RandomSource
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.ScheduledTickAccess
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.block.StairBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.EnumProperty
import net.minecraft.world.level.block.state.properties.Half
import net.minecraft.world.level.block.state.properties.StairsShape
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.level.pathfinder.PathComputationType
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import ru.benos_codex.more_copycats.datapack.CopycatDatapackManager

class CopycatStairsBlock(props: Properties) : CopycatSimpleWaterloggedBlock(props) {
    companion object {
        val FACING: EnumProperty<Direction> = BlockStateProperties.HORIZONTAL_FACING
        val HALF: EnumProperty<Half> = BlockStateProperties.HALF
        val SHAPE: EnumProperty<StairsShape> = BlockStateProperties.STAIRS_SHAPE

        private val SHAPE_OUTER: VoxelShape = Shapes.or(
            box(0.0, 0.0, 0.0, 16.0, 8.0, 16.0),
            box(0.0, 8.0, 0.0, 8.0, 16.0, 8.0)
        )
        private val SHAPE_STRAIGHT: VoxelShape = Shapes.or(
            SHAPE_OUTER,
            Shapes.rotate(SHAPE_OUTER, OctahedralGroup.BLOCK_ROT_Y_90)
        )
        private val SHAPE_INNER: VoxelShape = Shapes.or(
            SHAPE_STRAIGHT,
            Shapes.rotate(SHAPE_STRAIGHT, OctahedralGroup.BLOCK_ROT_Y_90)
        )

        private val SHAPE_BOTTOM_OUTER: Map<Direction, VoxelShape> = Shapes.rotateHorizontal(SHAPE_OUTER)
        private val SHAPE_BOTTOM_STRAIGHT: Map<Direction, VoxelShape> = Shapes.rotateHorizontal(SHAPE_STRAIGHT)
        private val SHAPE_BOTTOM_INNER: Map<Direction, VoxelShape> = Shapes.rotateHorizontal(SHAPE_INNER)
        private val SHAPE_TOP_OUTER: Map<Direction, VoxelShape> = Shapes.rotateHorizontal(SHAPE_OUTER, OctahedralGroup.INVERT_Y)
        private val SHAPE_TOP_STRAIGHT: Map<Direction, VoxelShape> = Shapes.rotateHorizontal(SHAPE_STRAIGHT, OctahedralGroup.INVERT_Y)
        private val SHAPE_TOP_INNER: Map<Direction, VoxelShape> = Shapes.rotateHorizontal(SHAPE_INNER, OctahedralGroup.INVERT_Y)
    }

    init {
        registerDefaultState(
            defaultBlockState()
                .setValue(FACING, Direction.NORTH)
                .setValue(HALF, Half.BOTTOM)
                .setValue(SHAPE, StairsShape.STRAIGHT)
                .setValue(WATERLOGGED, false)
        )
    }

    override fun useShapeForLightOcclusion(state: BlockState): Boolean = true

    override fun getShape(
        state: BlockState,
        level: BlockGetter,
        pos: BlockPos,
        context: CollisionContext
    ): VoxelShape {
        val isBottom = state.getValue(HALF) == Half.BOTTOM
        val facing = state.getValue(FACING)
        val stairShape = state.getValue(SHAPE)

        val direction = when (stairShape) {
            StairsShape.STRAIGHT, StairsShape.OUTER_LEFT, StairsShape.INNER_RIGHT -> facing
            StairsShape.INNER_LEFT -> facing.counterClockWise
            StairsShape.OUTER_RIGHT -> facing.clockWise
        }

        val map = when (stairShape) {
            StairsShape.STRAIGHT -> if (isBottom) SHAPE_BOTTOM_STRAIGHT else SHAPE_TOP_STRAIGHT
            StairsShape.OUTER_LEFT, StairsShape.OUTER_RIGHT -> if (isBottom) SHAPE_BOTTOM_OUTER else SHAPE_TOP_OUTER
            StairsShape.INNER_LEFT, StairsShape.INNER_RIGHT -> if (isBottom) SHAPE_BOTTOM_INNER else SHAPE_TOP_INNER
        }
        return map[direction] ?: Shapes.block()
    }

    override fun getStateForPlacement(ctx: BlockPlaceContext): BlockState? {
        val face = ctx.clickedFace
        val pos = ctx.clickedPos
        val fluid = ctx.level.getFluidState(pos)
        val state = defaultBlockState()
            .setValue(FACING, ctx.horizontalDirection)
            .setValue(
                HALF,
                if (face != Direction.DOWN && (face == Direction.UP || !(ctx.clickLocation.y - pos.y > 0.5))) Half.BOTTOM else Half.TOP
            )
            .setValue(WATERLOGGED, fluid.type == Fluids.WATER)
        val placed = state.setValue(SHAPE, getStairsShape(state, ctx.level, pos))
        return if (CopycatDatapackManager.isBlockEnabled(placed)) placed else null
    }

    override fun updateShape(
        state: BlockState,
        level: LevelReader,
        scheduledTickAccess: ScheduledTickAccess,
        pos: BlockPos,
        direction: Direction,
        neighborPos: BlockPos,
        neighborState: BlockState,
        random: RandomSource
    ): BlockState {
        if (state.getValue(WATERLOGGED)) {
            scheduledTickAccess.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level))
        }
        return if (direction.axis.isHorizontal) {
            state.setValue(SHAPE, getStairsShape(state, level, pos))
        } else {
            super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random)
        }
    }

    private fun getStairsShape(state: BlockState, level: BlockGetter, pos: BlockPos): StairsShape {
        val facing = state.getValue(FACING)
        val half = state.getValue(HALF)

        val frontState = level.getBlockState(pos.relative(facing))
        if (isStairs(frontState) && half == frontState.getValue(HALF)) {
            val frontFacing = frontState.getValue(FACING)
            if (frontFacing.axis != facing.axis && canTakeShape(state, level, pos, frontFacing.opposite)) {
                return if (frontFacing == facing.counterClockWise) StairsShape.OUTER_LEFT else StairsShape.OUTER_RIGHT
            }
        }

        val backState = level.getBlockState(pos.relative(facing.opposite))
        if (isStairs(backState) && half == backState.getValue(HALF)) {
            val backFacing = backState.getValue(FACING)
            if (backFacing.axis != facing.axis && canTakeShape(state, level, pos, backFacing)) {
                return if (backFacing == facing.counterClockWise) StairsShape.INNER_LEFT else StairsShape.INNER_RIGHT
            }
        }

        return StairsShape.STRAIGHT
    }

    private fun canTakeShape(state: BlockState, level: BlockGetter, pos: BlockPos, direction: Direction): Boolean {
        val sideState = level.getBlockState(pos.relative(direction))
        return !isStairs(sideState) ||
            sideState.getValue(FACING) != state.getValue(FACING) ||
            sideState.getValue(HALF) != state.getValue(HALF)
    }

    private fun isStairs(state: BlockState): Boolean {
        val block = state.block
        return block is CopycatStairsBlock || block is StairBlock
    }

    override fun rotate(state: BlockState, rotation: Rotation): BlockState =
        state.setValue(FACING, rotation.rotate(state.getValue(FACING)))

    override fun mirror(state: BlockState, mirror: Mirror): BlockState {
        val facing = state.getValue(FACING)
        val shape = state.getValue(SHAPE)

        when (mirror) {
            Mirror.LEFT_RIGHT -> {
                if (facing.axis == Direction.Axis.Z) {
                    return when (shape) {
                        StairsShape.OUTER_LEFT -> state.rotate(Rotation.CLOCKWISE_180).setValue(SHAPE, StairsShape.OUTER_RIGHT)
                        StairsShape.INNER_RIGHT -> state.rotate(Rotation.CLOCKWISE_180).setValue(SHAPE, StairsShape.INNER_LEFT)
                        StairsShape.INNER_LEFT -> state.rotate(Rotation.CLOCKWISE_180).setValue(SHAPE, StairsShape.INNER_RIGHT)
                        StairsShape.OUTER_RIGHT -> state.rotate(Rotation.CLOCKWISE_180).setValue(SHAPE, StairsShape.OUTER_LEFT)
                        else -> state.rotate(Rotation.CLOCKWISE_180)
                    }
                }
            }

            Mirror.FRONT_BACK -> {
                if (facing.axis == Direction.Axis.X) {
                    return when (shape) {
                        StairsShape.STRAIGHT -> state.rotate(Rotation.CLOCKWISE_180)
                        StairsShape.OUTER_LEFT -> state.rotate(Rotation.CLOCKWISE_180).setValue(SHAPE, StairsShape.OUTER_RIGHT)
                        StairsShape.INNER_RIGHT -> state.rotate(Rotation.CLOCKWISE_180).setValue(SHAPE, StairsShape.INNER_RIGHT)
                        StairsShape.INNER_LEFT -> state.rotate(Rotation.CLOCKWISE_180).setValue(SHAPE, StairsShape.INNER_LEFT)
                        StairsShape.OUTER_RIGHT -> state.rotate(Rotation.CLOCKWISE_180).setValue(SHAPE, StairsShape.OUTER_LEFT)
                    }
                }
            }

            else -> {}
        }

        return super.mirror(state, mirror)
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        super.createBlockStateDefinition(builder)
        builder.add(FACING, HALF, SHAPE)
    }

    override fun getFluidState(state: BlockState): FluidState =
        if (state.getValue(WATERLOGGED)) Fluids.WATER.getSource(false) else super.getFluidState(state)

    override fun isPathfindable(state: BlockState, type: PathComputationType): Boolean = false
}
