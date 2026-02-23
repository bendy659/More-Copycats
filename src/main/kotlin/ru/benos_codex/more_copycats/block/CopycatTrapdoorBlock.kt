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
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Explosion
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.ScheduledTickAccess
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.level.block.state.properties.EnumProperty
import net.minecraft.world.level.block.state.properties.Half
import net.minecraft.world.level.gameevent.GameEvent
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.level.pathfinder.PathComputationType
import net.minecraft.world.level.redstone.Orientation
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import java.util.function.BiConsumer
import ru.benos_codex.more_copycats.datapack.CopycatDatapackManager

class CopycatTrapdoorBlock(props: Properties) : CopycatSimpleWaterloggedBlock(props) {
    companion object {
        val FACING: EnumProperty<Direction> = BlockStateProperties.HORIZONTAL_FACING
        val OPEN: BooleanProperty = BlockStateProperties.OPEN
        val HALF: EnumProperty<Half> = BlockStateProperties.HALF
        val POWERED: BooleanProperty = BlockStateProperties.POWERED

        private val CLOSED_BOTTOM_SHAPE: VoxelShape = box(0.0, 0.0, 0.0, 16.0, 3.0, 16.0)
        private val CLOSED_TOP_SHAPE: VoxelShape = box(0.0, 13.0, 0.0, 16.0, 16.0, 16.0)
        private val OPEN_SHAPES: Map<Direction, VoxelShape> = Shapes.rotateAll(box(0.0, 0.0, 13.0, 16.0, 16.0, 16.0))
    }

    init {
        registerDefaultState(
            defaultBlockState()
                .setValue(FACING, Direction.NORTH)
                .setValue(OPEN, false)
                .setValue(HALF, Half.BOTTOM)
                .setValue(POWERED, false)
                .setValue(WATERLOGGED, false)
        )
    }

    override fun getShape(
        state: BlockState,
        level: BlockGetter,
        pos: BlockPos,
        context: CollisionContext
    ): VoxelShape {
        if (state.getValue(OPEN)) {
            return OPEN_SHAPES[state.getValue(FACING)] ?: CLOSED_BOTTOM_SHAPE
        }
        return if (state.getValue(HALF) == Half.TOP) CLOSED_TOP_SHAPE else CLOSED_BOTTOM_SHAPE
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
        level.setBlock(pos, next, 2)
        if (next.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level))
        }

        val open = next.getValue(OPEN)
        level.playSound(
            player,
            pos,
            if (open) SoundEvents.WOODEN_TRAPDOOR_OPEN else SoundEvents.WOODEN_TRAPDOOR_CLOSE,
            SoundSource.BLOCKS,
            1.0f,
            level.random.nextFloat() * 0.1f + 0.9f
        )
        level.gameEvent(player, if (open) GameEvent.BLOCK_OPEN else GameEvent.BLOCK_CLOSE, pos)
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
            val result = super.onWrenched(state, UseOnContext(player, hand, hitResult))
            return if (result == InteractionResult.PASS) InteractionResult.SUCCESS else result
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hitResult)
    }

    override fun onExplosionHit(
        state: BlockState,
        level: ServerLevel,
        pos: BlockPos,
        explosion: Explosion,
        dropConsumer: BiConsumer<ItemStack, BlockPos>
    ) {
        if (explosion.canTriggerBlocks() && !state.getValue(POWERED)) {
            val toggled = state.cycle(OPEN)
            level.setBlock(pos, toggled, 2)
            val open = toggled.getValue(OPEN)
            level.playSound(
                null,
                pos,
                if (open) SoundEvents.WOODEN_TRAPDOOR_OPEN else SoundEvents.WOODEN_TRAPDOOR_CLOSE,
                SoundSource.BLOCKS,
                1.0f,
                level.random.nextFloat() * 0.1f + 0.9f
            )
            level.gameEvent(null, if (open) GameEvent.BLOCK_OPEN else GameEvent.BLOCK_CLOSE, pos)
        }
        super.onExplosionHit(state, level, pos, explosion, dropConsumer)
    }

    override fun neighborChanged(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        block: Block,
        orientation: Orientation?,
        movedByPiston: Boolean
    ) {
        if (level.isClientSide) return

        val powered = level.hasNeighborSignal(pos)
        if (powered != state.getValue(POWERED)) {
            var next = state
            if (next.getValue(OPEN) != powered) {
                next = next.setValue(OPEN, powered)
                level.playSound(
                    null,
                    pos,
                    if (powered) SoundEvents.WOODEN_TRAPDOOR_OPEN else SoundEvents.WOODEN_TRAPDOOR_CLOSE,
                    SoundSource.BLOCKS,
                    1.0f,
                    level.random.nextFloat() * 0.1f + 0.9f
                )
                level.gameEvent(null, if (powered) GameEvent.BLOCK_OPEN else GameEvent.BLOCK_CLOSE, pos)
            }

            level.setBlock(pos, next.setValue(POWERED, powered), 2)
            if (next.getValue(WATERLOGGED)) {
                level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level))
            }
        }
    }

    override fun getStateForPlacement(ctx: BlockPlaceContext): BlockState? {
        val pos = ctx.clickedPos
        val fluid = ctx.level.getFluidState(pos)
        var state = defaultBlockState()
        val face = ctx.clickedFace

        state = if (!ctx.replacingClickedOnBlock() && face.axis.isHorizontal) {
            state
                .setValue(FACING, face)
                .setValue(HALF, if (ctx.clickLocation.y - pos.y > 0.5) Half.TOP else Half.BOTTOM)
        } else {
            state
                .setValue(FACING, ctx.horizontalDirection.opposite)
                .setValue(HALF, if (face == Direction.UP) Half.BOTTOM else Half.TOP)
        }

        if (ctx.level.hasNeighborSignal(pos)) {
            state = state.setValue(OPEN, true).setValue(POWERED, true)
        }

        val placed = state.setValue(WATERLOGGED, fluid.type == Fluids.WATER)
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
        return super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random)
    }

    override fun rotate(state: BlockState, rotation: Rotation): BlockState =
        state.setValue(FACING, rotation.rotate(state.getValue(FACING)))

    override fun mirror(state: BlockState, mirror: Mirror): BlockState =
        state.rotate(mirror.getRotation(state.getValue(FACING)))

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        super.createBlockStateDefinition(builder)
        builder.add(FACING, OPEN, HALF, POWERED)
    }

    override fun getFluidState(state: BlockState): FluidState =
        if (state.getValue(WATERLOGGED)) Fluids.WATER.getSource(false) else super.getFluidState(state)

    override fun isPathfindable(state: BlockState, type: PathComputationType): Boolean {
        return when (type) {
            PathComputationType.LAND -> state.getValue(OPEN)
            PathComputationType.WATER -> state.getValue(WATERLOGGED)
            PathComputationType.AIR -> state.getValue(OPEN)
        }
    }
}
