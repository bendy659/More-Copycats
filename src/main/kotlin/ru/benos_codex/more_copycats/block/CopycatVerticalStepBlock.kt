package ru.benos_codex.more_copycats.block

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.block.state.BlockBehaviour.Properties
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.EnumProperty
import net.minecraft.world.level.block.state.properties.Half
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.level.pathfinder.PathComputationType
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import ru.benos_codex.more_copycats.datapack.CopycatDatapackManager

class CopycatVerticalStepBlock(props: Properties) : CopycatSimpleWaterloggedBlock(props) {
    companion object {
        val HALF: EnumProperty<Half> = BlockStateProperties.HALF
        val FACING: EnumProperty<Direction> = BlockStateProperties.HORIZONTAL_FACING

        private val BASE_BOTTOM_SOUTH: VoxelShape = box(8.0, 0.0, 8.0, 16.0, 16.0, 16.0)
        private val BASE_TOP_SOUTH: VoxelShape = box(0.0, 0.0, 8.0, 8.0, 16.0, 16.0)
        private val SHAPE_BOTTOM: Map<Direction, VoxelShape> = Shapes.rotateHorizontal(BASE_BOTTOM_SOUTH)
        private val SHAPE_TOP: Map<Direction, VoxelShape> = Shapes.rotateHorizontal(BASE_TOP_SOUTH)
    }

    init {
        registerDefaultState(
            defaultBlockState()
                .setValue(HALF, Half.BOTTOM)
                .setValue(FACING, Direction.SOUTH)
                .setValue(WATERLOGGED, false)
        )
    }

    override fun useShapeForLightOcclusion(state: BlockState): Boolean = true

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        super.createBlockStateDefinition(builder)
        builder.add(HALF, FACING)
    }

    override fun getStateForPlacement(ctx: BlockPlaceContext): BlockState? {
        val fluid = ctx.level.getFluidState(ctx.clickedPos)
        val facing = ctx.horizontalDirection
        val half = resolveHalf(ctx, facing)

        val placed = defaultBlockState()
            .setValue(FACING, facing)
            .setValue(HALF, half)
            .setValue(WATERLOGGED, fluid.type == Fluids.WATER)

        return if (CopycatDatapackManager.isBlockEnabled(placed)) placed else null
    }

    private fun resolveHalf(ctx: BlockPlaceContext, facing: Direction): Half {
        val sideForBottom = facing.counterClockWise
        val clickedFace = ctx.clickedFace

        if (clickedFace.axis == sideForBottom.axis) {
            return if (clickedFace == sideForBottom) Half.BOTTOM else Half.TOP
        }

        val pos = ctx.clickedPos
        val localX = ctx.clickLocation.x - pos.x
        val localZ = ctx.clickLocation.z - pos.z
        val bottomProgress = when (sideForBottom) {
            Direction.EAST -> localX
            Direction.WEST -> 1.0 - localX
            Direction.SOUTH -> localZ
            Direction.NORTH -> 1.0 - localZ
            else -> 0.5
        }
        return if (bottomProgress > 0.5) Half.BOTTOM else Half.TOP
    }

    override fun getShape(
        state: BlockState,
        level: BlockGetter,
        pos: BlockPos,
        context: CollisionContext
    ): VoxelShape {
        val facing = state.getValue(FACING)
        return if (state.getValue(HALF) == Half.BOTTOM) SHAPE_BOTTOM[facing] ?: Shapes.empty()
        else SHAPE_TOP[facing] ?: Shapes.empty()
    }

    override fun canFaceBeOccluded(state: BlockState, face: Direction): Boolean {
        val facing = state.getValue(FACING)
        val sideFace = if (state.getValue(HALF) == Half.BOTTOM) facing.counterClockWise else facing.clockWise
        return face == facing || face == sideFace
    }

    override fun shouldFaceAlwaysRender(state: BlockState, face: Direction): Boolean {
        return canFaceBeOccluded(state, face.opposite)
    }

    override fun canConnectTexturesToward(
        reader: net.minecraft.world.level.BlockAndTintGetter,
        fromPos: BlockPos,
        toPos: BlockPos,
        state: BlockState
    ): Boolean {
        if (fromPos == toPos.relative(state.getValue(FACING))) return false
        return true
    }

    override fun rotate(state: BlockState, rotation: Rotation): BlockState =
        state.setValue(FACING, rotation.rotate(state.getValue(FACING)))

    override fun mirror(state: BlockState, mirror: Mirror): BlockState =
        state.rotate(mirror.getRotation(state.getValue(FACING)))

    override fun getFluidState(state: BlockState): FluidState =
        if (state.getValue(WATERLOGGED)) Fluids.WATER.getSource(false) else super.getFluidState(state)

    override fun isPathfindable(state: BlockState, type: PathComputationType): Boolean = false
}

