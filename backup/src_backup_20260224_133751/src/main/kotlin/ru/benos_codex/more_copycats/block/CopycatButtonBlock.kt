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
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.AttachFace
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.level.block.state.properties.EnumProperty
import net.minecraft.world.level.gameevent.GameEvent
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.BooleanOp
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import ru.benos_codex.more_copycats.MoreCopycatsRegister
import ru.benos_codex.more_copycats.block.entity.CopycatRedstoneBlockEntity
import ru.benos_codex.more_copycats.datapack.CopycatDatapackManager
import java.util.function.BiConsumer
import java.util.function.Function

class CopycatButtonBlock(props: Properties) : CopycatSimpleWaterloggedBlock(props) {
    companion object {
        val FACE: EnumProperty<AttachFace> = BlockStateProperties.ATTACH_FACE
        val FACING: EnumProperty<Direction> = BlockStateProperties.HORIZONTAL_FACING
        val POWERED: BooleanProperty = BlockStateProperties.POWERED

        private const val DEFAULT_HOLD_TICKS = 20
    }

    private val shapes: Function<BlockState, VoxelShape> = makeShapes()

    init {
        registerDefaultState(
            defaultBlockState()
                .setValue(FACE, AttachFace.WALL)
                .setValue(FACING, Direction.NORTH)
                .setValue(POWERED, false)
                .setValue(WATERLOGGED, false)
        )
    }

    override fun getBlockEntityType() = MoreCopycatsRegister.REDSTONE_BE

    @Suppress("UNCHECKED_CAST")
    override fun <T : BlockEntity> getTicker(
        level: Level,
        state: BlockState,
        blockEntityType: BlockEntityType<T>
    ): BlockEntityTicker<T>? {
        if (blockEntityType != MoreCopycatsRegister.REDSTONE_BE) return null
        return BlockEntityTicker<CopycatRedstoneBlockEntity> { _, _, _, be -> be.tick() } as BlockEntityTicker<T>
    }

    override fun getShape(
        state: BlockState,
        level: BlockGetter,
        pos: BlockPos,
        context: CollisionContext
    ): VoxelShape = shapes.apply(state)

    override fun getCollisionShape(
        state: BlockState,
        level: BlockGetter,
        pos: BlockPos,
        context: CollisionContext
    ): VoxelShape = Shapes.empty()

    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.INVISIBLE

    override fun canSurvive(state: BlockState, level: LevelReader, pos: BlockPos): Boolean {
        return FaceAttachedHorizontalDirectionalBlock.canAttach(level, pos, connectedDirection(state).opposite)
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
        return if (connectedDirection(state).opposite == direction && !state.canSurvive(level, pos)) {
            Blocks.AIR.defaultBlockState()
        } else {
            super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random)
        }
    }

    override fun getStateForPlacement(ctx: BlockPlaceContext): BlockState? {
        val level = ctx.level
        val pos = ctx.clickedPos
        val fluid = level.getFluidState(pos)

        for (direction in ctx.nearestLookingDirections) {
            val candidate = if (direction.axis == Direction.Axis.Y) {
                defaultBlockState()
                    .setValue(FACE, if (direction == Direction.UP) AttachFace.CEILING else AttachFace.FLOOR)
                    .setValue(FACING, ctx.horizontalDirection)
            } else {
                defaultBlockState()
                    .setValue(FACE, AttachFace.WALL)
                    .setValue(FACING, direction.opposite)
            }
            if (candidate.canSurvive(level, pos)) {
                val placed = candidate.setValue(WATERLOGGED, fluid.type == Fluids.WATER)
                if (CopycatDatapackManager.isBlockEnabled(placed)) {
                    return placed
                }
            }
        }
        return null
    }

    override fun useWithoutItem(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hitResult: BlockHitResult
    ): InteractionResult {
        if (!CopycatDatapackManager.isBlockEnabled(state)) return InteractionResult.PASS
        if (state.getValue(POWERED)) {
            refreshPress(level, pos)
            playSound(level, pos, state, true, player)
            return InteractionResult.SUCCESS
        }
        press(state, level, pos, player)
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
        if (!CopycatDatapackManager.isBlockEnabled(state)) {
            super.onExplosionHit(state, level, pos, explosion, dropConsumer)
            return
        }
        if (explosion.canTriggerBlocks() && !state.getValue(POWERED)) {
            press(state, level, pos, null)
        }
        super.onExplosionHit(state, level, pos, explosion, dropConsumer)
    }

    override fun tick(state: BlockState, level: ServerLevel, pos: BlockPos, random: RandomSource) {
        if (!CopycatDatapackManager.isBlockEnabled(state)) {
            if (state.getValue(POWERED)) {
                level.setBlock(pos, state.setValue(POWERED, false), 3)
                updateNeighbours(state, level, pos)
            }
            return
        }
        if (!state.getValue(POWERED)) return
        val be = level.getBlockEntity(pos) as? CopycatRedstoneBlockEntity
        if (be?.hasActiveHoldTimer() == true) {
            level.scheduleTick(pos, this, 1)
            return
        }
        level.setBlock(pos, state.setValue(POWERED, false), 3)
        updateNeighbours(state, level, pos)
        playSound(level, pos, state, false, null)
        level.gameEvent(GameEvent.BLOCK_DEACTIVATE, pos, GameEvent.Context.of(state))
    }

    override fun isSignalSource(state: BlockState): Boolean = true

    override fun getSignal(state: BlockState, level: BlockGetter, pos: BlockPos, direction: Direction): Int {
        if (!CopycatDatapackManager.isBlockEnabled(state)) return 0
        return if (state.getValue(POWERED)) 15 else 0
    }

    override fun getDirectSignal(state: BlockState, level: BlockGetter, pos: BlockPos, direction: Direction): Int {
        if (!CopycatDatapackManager.isBlockEnabled(state)) return 0
        return if (state.getValue(POWERED) && connectedDirection(state) == direction) 15 else 0
    }

    override fun rotate(state: BlockState, rotation: Rotation): BlockState =
        state.setValue(FACING, rotation.rotate(state.getValue(FACING)))

    override fun mirror(state: BlockState, mirror: Mirror): BlockState =
        state.rotate(mirror.getRotation(state.getValue(FACING)))

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        super.createBlockStateDefinition(builder)
        builder.add(FACE, FACING, POWERED)
    }

    override fun getFluidState(state: BlockState): FluidState {
        return if (state.getValue(WATERLOGGED)) Fluids.WATER.getSource(false) else super.getFluidState(state)
    }

    private fun press(state: BlockState, level: Level, pos: BlockPos, player: Player?) {
        level.setBlock(pos, state.setValue(POWERED, true), 3)
        refreshPress(level, pos)
        updateNeighbours(state, level, pos)
        playSound(level, pos, state, true, player)
        level.gameEvent(player, GameEvent.BLOCK_ACTIVATE, pos)
    }

    private fun refreshPress(level: Level, pos: BlockPos) {
        val be = level.getBlockEntity(pos) as? CopycatRedstoneBlockEntity
        be?.refreshHoldTimer()
        level.scheduleTick(pos, this, getHoldTicks(level, pos))
    }

    private fun playSound(level: Level, pos: BlockPos, state: BlockState, pressed: Boolean, player: Player?) {
        val dir = connectedDirection(state)
        val x = pos.x + 0.5 + dir.stepX * 0.3125
        val y = pos.y + 0.5 + dir.stepY * 0.3125
        val z = pos.z + 0.5 + dir.stepZ * 0.3125
        level.playSound(
            if (pressed) player else null,
            x,
            y,
            z,
            if (pressed) SoundEvents.STONE_BUTTON_CLICK_ON else SoundEvents.STONE_BUTTON_CLICK_OFF,
            SoundSource.BLOCKS,
            1.0f,
            1.0f
        )
    }

    private fun updateNeighbours(state: BlockState, level: Level, pos: BlockPos) {
        val direction = connectedDirection(state).opposite
        level.updateNeighborsAt(pos, this)
        level.updateNeighborsAt(pos.relative(direction), this)
    }

    private fun connectedDirection(state: BlockState): Direction {
        return when (state.getValue(FACE)) {
            AttachFace.CEILING -> Direction.DOWN
            AttachFace.FLOOR -> Direction.UP
            AttachFace.WALL -> state.getValue(FACING)
        }
    }

    private fun makeShapes(): Function<BlockState, VoxelShape> {
        val pressedCutout = cube(14.0)
        val normalCutout = cube(12.0)
        val base = Shapes.rotateAttachFace(boxZ(6.0, 4.0, 8.0, 16.0))
        return getShapeForEachState { state ->
            val baseShape = base[state.getValue(FACE)]?.get(state.getValue(FACING))
                ?: base[AttachFace.WALL]!![Direction.NORTH]!!
            Shapes.join(baseShape, if (state.getValue(POWERED)) pressedCutout else normalCutout, BooleanOp.ONLY_FIRST)
        }
    }

    private fun getHoldTicks(level: Level, pos: BlockPos): Int {
        val state = level.getBlockState(pos)
        val fallback = CopycatDatapackManager.redstoneHoldTicksFor(state) ?: DEFAULT_HOLD_TICKS
        val be = level.getBlockEntity(pos) as? CopycatRedstoneBlockEntity
        return be?.getHoldTicks(fallback) ?: fallback
    }
}
