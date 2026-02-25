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
import net.minecraft.world.entity.InsideBlockEffectApplier
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.ScheduledTickAccess
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.level.gameevent.GameEvent
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import ru.benos_codex.more_copycats.MoreCopycatsRegister
import ru.benos_codex.more_copycats.block.entity.CopycatRedstoneBlockEntity
import ru.benos_codex.more_copycats.datapack.CopycatDatapackManager

class CopycatPressurePlateBlock(props: Properties) : CopycatSimpleBlock(props) {
    companion object {
        val POWERED: BooleanProperty = BlockStateProperties.POWERED

        private const val DEFAULT_HOLD_TICKS = 20
        private val SHAPE = box(1.0, 0.0, 1.0, 15.0, 1.0, 15.0)
        private val SHAPE_PRESSED = box(1.0, 0.0, 1.0, 15.0, 0.5, 15.0)
        val TOUCH_AABB: AABB = AABB(1.0 / 16.0, 0.0, 1.0 / 16.0, 15.0 / 16.0, 0.25, 15.0 / 16.0)
    }

    init {
        registerDefaultState(defaultBlockState().setValue(POWERED, false))
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
    ): VoxelShape = if (state.getValue(POWERED)) SHAPE_PRESSED else SHAPE

    override fun getCollisionShape(
        state: BlockState,
        level: BlockGetter,
        pos: BlockPos,
        context: CollisionContext
    ): VoxelShape = Shapes.empty()

    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.INVISIBLE

    override fun canSurvive(state: BlockState, level: LevelReader, pos: BlockPos): Boolean {
        val below = pos.below()
        return canSupportRigidBlock(level, below) || canSupportCenter(level, below, Direction.UP)
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
        if (direction == Direction.DOWN && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState()
        }
        return super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random)
    }

    override fun useItemOn(
        stack: ItemStack,
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: net.minecraft.world.entity.player.Player,
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

    override fun tick(state: BlockState, level: ServerLevel, pos: BlockPos, random: RandomSource) {
        if (!CopycatDatapackManager.isBlockEnabled(state)) {
            if (state.getValue(POWERED)) {
                val off = state.setValue(POWERED, false)
                level.setBlock(pos, off, 2)
                updateNeighbours(level, pos)
            }
            return
        }
        if (!state.getValue(POWERED)) return
        checkPressed(level, pos, state, null)
    }

    override fun entityInside(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        entity: Entity,
        insideBlockEffectApplier: InsideBlockEffectApplier,
        runEffects: Boolean
    ) {
        if (level.isClientSide) return
        if (!CopycatDatapackManager.isBlockEnabled(state)) return
        if (entity.isSpectator || entity.isIgnoringBlockTriggers) return
        if (state.getValue(POWERED)) {
            val be = level.getBlockEntity(pos) as? CopycatRedstoneBlockEntity
            be?.refreshHoldTimer()
            return
        }
        checkPressed(level, pos, state, entity)
    }

    override fun isSignalSource(state: BlockState): Boolean = true

    override fun getSignal(state: BlockState, level: BlockGetter, pos: BlockPos, direction: Direction): Int {
        if (!CopycatDatapackManager.isBlockEnabled(state)) return 0
        return if (state.getValue(POWERED)) 15 else 0
    }

    override fun getDirectSignal(state: BlockState, level: BlockGetter, pos: BlockPos, direction: Direction): Int {
        if (!CopycatDatapackManager.isBlockEnabled(state)) return 0
        return if (direction == Direction.UP && state.getValue(POWERED)) 15 else 0
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        super.createBlockStateDefinition(builder)
        builder.add(POWERED)
    }

    private fun checkPressed(level: Level, pos: BlockPos, state: BlockState, source: Entity?) {
        if (!CopycatDatapackManager.isBlockEnabled(state)) {
            if (state.getValue(POWERED)) {
                val next = state.setValue(POWERED, false)
                level.setBlock(pos, next, 2)
                updateNeighbours(level, pos)
                level.setBlocksDirty(pos, state, next)
            }
            return
        }
        val poweredBefore = state.getValue(POWERED)
        val poweredNow = hasEntities(level, pos)
        val be = level.getBlockEntity(pos) as? CopycatRedstoneBlockEntity

        if (poweredBefore && !poweredNow && be?.hasActiveHoldTimer() == true) {
            level.scheduleTick(pos, this, 1)
            return
        }

        if (poweredBefore != poweredNow) {
            val next = state.setValue(POWERED, poweredNow)
            level.setBlock(pos, next, 2)
            updateNeighbours(level, pos)
            level.setBlocksDirty(pos, state, next)

            level.playSound(
                null,
                pos,
                if (poweredNow) SoundEvents.STONE_PRESSURE_PLATE_CLICK_ON else SoundEvents.STONE_PRESSURE_PLATE_CLICK_OFF,
                SoundSource.BLOCKS
            )
            level.gameEvent(source, if (poweredNow) GameEvent.BLOCK_ACTIVATE else GameEvent.BLOCK_DEACTIVATE, pos)
        }

        if (poweredNow) {
            be?.refreshHoldTimer()
            level.scheduleTick(pos, this, getHoldTicks(level, pos))
        }
    }

    private fun hasEntities(level: Level, pos: BlockPos): Boolean {
        val bounds = TOUCH_AABB.move(pos)
        return level.getEntities(null, bounds) { entity ->
            !entity.isSpectator && !entity.isIgnoringBlockTriggers
        }.isNotEmpty()
    }

    private fun updateNeighbours(level: Level, pos: BlockPos) {
        level.updateNeighborsAt(pos, this)
        level.updateNeighborsAt(pos.below(), this)
    }

    private fun getHoldTicks(level: Level, pos: BlockPos): Int {
        val state = level.getBlockState(pos)
        val fallback = CopycatDatapackManager.redstoneHoldTicksFor(state) ?: DEFAULT_HOLD_TICKS
        val be = level.getBlockEntity(pos) as? CopycatRedstoneBlockEntity
        return be?.getHoldTicks(fallback) ?: fallback
    }
}
