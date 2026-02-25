package ru.benos_codex.more_copycats.block

import net.minecraft.world.level.block.state.BlockBehaviour.Properties

import com.zurrtum.create.AllItemTags
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Explosion
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.ScheduledTickAccess
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.level.block.state.properties.DoorHingeSide
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf
import net.minecraft.world.level.block.state.properties.EnumProperty
import net.minecraft.world.level.gameevent.GameEvent
import net.minecraft.world.level.pathfinder.PathComputationType
import net.minecraft.world.level.redstone.Orientation
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import java.util.function.BiConsumer
import ru.benos_codex.more_copycats.datapack.CopycatDatapackManager

open class CopycatDoorBlock(props: Properties) : CopycatSimpleBlock(props) {
    companion object {
        val FACING: EnumProperty<Direction> = BlockStateProperties.HORIZONTAL_FACING
        val HALF: EnumProperty<DoubleBlockHalf> = BlockStateProperties.DOUBLE_BLOCK_HALF
        val HINGE: EnumProperty<DoorHingeSide> = BlockStateProperties.DOOR_HINGE
        val OPEN: BooleanProperty = BlockStateProperties.OPEN
        val POWERED: BooleanProperty = BlockStateProperties.POWERED

        private val SHAPES: Map<Direction, VoxelShape> = Shapes.rotateHorizontal(box(0.0, 0.0, 0.0, 16.0, 16.0, 3.0))
    }

    init {
        registerDefaultState(
            defaultBlockState()
                .setValue(FACING, Direction.NORTH)
                .setValue(OPEN, false)
                .setValue(HINGE, DoorHingeSide.LEFT)
                .setValue(POWERED, false)
                .setValue(HALF, DoubleBlockHalf.LOWER)
        )
    }

    override fun getShape(
        state: BlockState,
        level: BlockGetter,
        pos: BlockPos,
        context: CollisionContext
    ): VoxelShape {
        val facing = state.getValue(FACING)
        val doorDirection = if (state.getValue(OPEN)) {
            if (state.getValue(HINGE) == DoorHingeSide.RIGHT) facing.counterClockWise else facing.clockWise
        } else {
            facing
        }
        return SHAPES[doorDirection] ?: Shapes.empty()
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
        val half = state.getValue(HALF)
        if (direction.axis != Direction.Axis.Y || (half == DoubleBlockHalf.LOWER) != (direction == Direction.UP)) {
            return if (half == DoubleBlockHalf.LOWER && direction == Direction.DOWN && !state.canSurvive(level, pos)) {
                Blocks.AIR.defaultBlockState()
            } else {
                super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random)
            }
        }

        return if (neighborState.block is CopycatDoorBlock && neighborState.getValue(HALF) != half) {
            neighborState.setValue(HALF, half)
        } else {
            Blocks.AIR.defaultBlockState()
        }
    }

    override fun onExplosionHit(
        state: BlockState,
        level: ServerLevel,
        pos: BlockPos,
        explosion: Explosion,
        dropConsumer: BiConsumer<ItemStack, BlockPos>
    ) {
        if (explosion.canTriggerBlocks() &&
            state.getValue(HALF) == DoubleBlockHalf.LOWER &&
            !state.getValue(POWERED)) {
            setOpen(null, level, state, pos, !state.getValue(OPEN))
        }

        super.onExplosionHit(state, level, pos, explosion, dropConsumer)
    }

    override fun isPathfindable(state: BlockState, type: PathComputationType): Boolean {
        return when (type) {
            PathComputationType.LAND, PathComputationType.AIR -> state.getValue(OPEN)
            PathComputationType.WATER -> false
        }
    }

    override fun getStateForPlacement(ctx: BlockPlaceContext): BlockState? {
        val pos = ctx.clickedPos
        val level = ctx.level
        if (pos.y >= level.maxY || !level.getBlockState(pos.above()).canBeReplaced(ctx)) return null

        val facing = ctx.horizontalDirection.opposite
        val powered = level.hasNeighborSignal(pos) || level.hasNeighborSignal(pos.above())
        return defaultBlockState()
            .setValue(FACING, facing)
            .setValue(HINGE, getHinge(ctx, ctx.horizontalDirection))
            .setValue(POWERED, powered)
            .setValue(OPEN, powered)
            .setValue(HALF, DoubleBlockHalf.LOWER)
            .takeIf { CopycatDatapackManager.isBlockEnabled(it) }
    }

    override fun setPlacedBy(level: Level, pos: BlockPos, state: BlockState, placer: LivingEntity?, stack: ItemStack) {
        if (!CopycatDatapackManager.isBlockEnabled(state)) return
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), 3)
    }

    private fun getHinge(ctx: BlockPlaceContext, dir: Direction): DoorHingeSide {
        val level = ctx.level
        val pos = ctx.clickedPos
        val above = pos.above()

        val left = dir.counterClockWise
        val right = dir.clockWise

        val leftPos = pos.relative(left)
        val leftState = level.getBlockState(leftPos)
        val leftUpPos = above.relative(left)
        val leftUpState = level.getBlockState(leftUpPos)

        val rightPos = pos.relative(right)
        val rightState = level.getBlockState(rightPos)
        val rightUpPos = above.relative(right)
        val rightUpState = level.getBlockState(rightUpPos)

        val score = (if (leftState.isCollisionShapeFullBlock(level, leftPos)) -1 else 0) +
            (if (leftUpState.isCollisionShapeFullBlock(level, leftUpPos)) -1 else 0) +
            (if (rightState.isCollisionShapeFullBlock(level, rightPos)) 1 else 0) +
            (if (rightUpState.isCollisionShapeFullBlock(level, rightUpPos)) 1 else 0)

        val hasLeftDoor = leftState.block is CopycatDoorBlock && leftState.getValue(HALF) == DoubleBlockHalf.LOWER
        val hasRightDoor = rightState.block is CopycatDoorBlock && rightState.getValue(HALF) == DoubleBlockHalf.LOWER

        if ((!hasLeftDoor || hasRightDoor) && score <= 0) {
            if ((!hasRightDoor || hasLeftDoor) && score >= 0) {
                val stepX = dir.stepX
                val stepZ = dir.stepZ
                val click: Vec3 = ctx.clickLocation
                val localX = click.x - pos.x
                val localZ = click.z - pos.z
                return if ((stepX >= 0 || localZ >= 0.5) &&
                    (stepX <= 0 || localZ <= 0.5) &&
                    (stepZ >= 0 || localX <= 0.5) &&
                    (stepZ <= 0 || localX >= 0.5)
                ) {
                    DoorHingeSide.LEFT
                } else {
                    DoorHingeSide.RIGHT
                }
            }
            return DoorHingeSide.LEFT
        }

        return DoorHingeSide.RIGHT
    }

    override fun useWithoutItem(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hitResult: BlockHitResult
    ): InteractionResult {
        if (!CopycatDatapackManager.isBlockEnabled(state)) return InteractionResult.PASS
        val next = state.cycle(OPEN)
        level.setBlock(pos, next, 10)
        playSound(player, level, pos, next.getValue(OPEN))
        level.gameEvent(player, if (next.getValue(OPEN)) GameEvent.BLOCK_OPEN else GameEvent.BLOCK_CLOSE, pos)
        return InteractionResult.SUCCESS
    }

    override fun useItemOn(
        stack: ItemStack,
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hand: InteractionHand,
        hitResult: BlockHitResult
    ): InteractionResult {
        if (!CopycatDatapackManager.isBlockEnabled(state)) return InteractionResult.PASS
        if (stack.`is`(AllItemTags.TOOLS_WRENCH)) {
            var result = super.onWrenched(state, UseOnContext(player, hand, hitResult))
            if (result == InteractionResult.PASS) {
                val otherPos = if (state.getValue(HALF) == DoubleBlockHalf.LOWER) pos.above() else pos.below()
                val otherState = level.getBlockState(otherPos)
                if (otherState.block is CopycatDoorBlock) {
                    val otherHit = BlockHitResult(hitResult.location, hitResult.direction, otherPos, hitResult.isInside)
                    result = super.onWrenched(otherState, UseOnContext(player, hand, otherHit))
                }
            }
            return if (result == InteractionResult.PASS) InteractionResult.SUCCESS else result
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hitResult)
    }

    fun setOpen(entity: Entity?, level: Level, state: BlockState, pos: BlockPos, open: Boolean) {
        if (!CopycatDatapackManager.isBlockEnabled(state)) return
        if (state.block is CopycatDoorBlock && state.getValue(OPEN) != open) {
            level.setBlock(pos, state.setValue(OPEN, open), 10)
            playSound(entity, level, pos, open)
            level.gameEvent(entity, if (open) GameEvent.BLOCK_OPEN else GameEvent.BLOCK_CLOSE, pos)
        }
    }

    override fun neighborChanged(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        block: Block,
        orientation: Orientation?,
        movedByPiston: Boolean
    ) {
        if (!CopycatDatapackManager.isBlockEnabled(state)) return
        val neighborPowered = level.hasNeighborSignal(pos) ||
            level.hasNeighborSignal(pos.relative(if (state.getValue(HALF) == DoubleBlockHalf.LOWER) Direction.UP else Direction.DOWN))

        if (!defaultBlockState().`is`(block) && neighborPowered != state.getValue(POWERED)) {
            if (neighborPowered != state.getValue(OPEN)) {
                playSound(null, level, pos, neighborPowered)
                level.gameEvent(null, if (neighborPowered) GameEvent.BLOCK_OPEN else GameEvent.BLOCK_CLOSE, pos)
            }
            level.setBlock(pos, state.setValue(POWERED, neighborPowered).setValue(OPEN, neighborPowered), 2)
        }
    }

    override fun canSurvive(state: BlockState, level: LevelReader, pos: BlockPos): Boolean {
        val below = pos.below()
        val belowState = level.getBlockState(below)
        return if (state.getValue(HALF) == DoubleBlockHalf.LOWER) {
            belowState.isFaceSturdy(level, below, Direction.UP)
        } else {
            belowState.block is CopycatDoorBlock
        }
    }

    private fun playSound(entity: Entity?, level: Level, pos: BlockPos, open: Boolean) {
        level.playSound(
            entity,
            pos,
            if (open) SoundEvents.WOODEN_DOOR_OPEN else SoundEvents.WOODEN_DOOR_CLOSE,
            SoundSource.BLOCKS,
            1.0f,
            level.random.nextFloat() * 0.1f + 0.9f
        )
    }

    override fun rotate(state: BlockState, rotation: Rotation): BlockState =
        state.setValue(FACING, rotation.rotate(state.getValue(FACING)))

    override fun mirror(state: BlockState, mirror: Mirror): BlockState =
        if (mirror == Mirror.NONE) state else state.rotate(mirror.getRotation(state.getValue(FACING))).cycle(HINGE)

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        super.createBlockStateDefinition(builder)
        builder.add(HALF, FACING, OPEN, HINGE, POWERED)
    }
}
