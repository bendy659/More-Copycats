package ru.benos_codex.more_copycats.block

import net.minecraft.world.level.block.state.BlockBehaviour.Properties

import com.zurrtum.create.content.decoration.copycat.CopycatBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.tags.FluidTags
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.BlockAndTintGetter
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.LevelEvent
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.SlabType
import net.minecraft.world.level.material.Fluid
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.level.pathfinder.PathComputationType
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import ru.benos_codex.more_copycats.MoreCopycatsRegister
import ru.benos_codex.more_copycats.block.entity.CopycatSlabBlockEntity
import ru.benos_codex.more_copycats.datapack.CopycatDatapackManager

class CopycatSlabBlock(props: Properties) : CopycatSimpleWaterloggedBlock(props) {
    companion object {
        val TYPE = BlockStateProperties.SLAB_TYPE
        val AXIS = BlockStateProperties.AXIS

        private val SHAPE_BOTTOM_Y: VoxelShape = box(0.0, 0.0, 0.0, 16.0, 8.0, 16.0)
        private val SHAPE_TOP_Y: VoxelShape = box(0.0, 8.0, 0.0, 16.0, 16.0, 16.0)
        private val SHAPE_BOTTOM_X: VoxelShape = box(0.0, 0.0, 0.0, 8.0, 16.0, 16.0)
        private val SHAPE_TOP_X: VoxelShape = box(8.0, 0.0, 0.0, 16.0, 16.0, 16.0)
        private val SHAPE_BOTTOM_Z: VoxelShape = box(0.0, 0.0, 0.0, 16.0, 16.0, 8.0)
        private val SHAPE_TOP_Z: VoxelShape = box(0.0, 0.0, 8.0, 16.0, 16.0, 16.0)
    }

    private data class Placement(val type: SlabType, val axis: Direction.Axis)

    init {
        registerDefaultState(
            defaultBlockState()
                .setValue(TYPE, SlabType.BOTTOM)
                .setValue(AXIS, Direction.Axis.Y)
                .setValue(WATERLOGGED, false)
        )
    }

    override fun getBlockEntityType(): BlockEntityType<out CopycatBlockEntity> = MoreCopycatsRegister.SLAB_BE

    override fun canConnectTexturesToward(
        reader: BlockAndTintGetter,
        fromPos: BlockPos,
        toPos: BlockPos,
        state: BlockState
    ): Boolean = fromPos.y == toPos.y

    override fun useShapeForLightOcclusion(state: BlockState): Boolean = state.getValue(TYPE) != SlabType.DOUBLE

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        super.createBlockStateDefinition(builder)
        builder.add(TYPE, AXIS)
    }

    override fun getShape(
        state: BlockState,
        level: BlockGetter,
        pos: BlockPos,
        context: CollisionContext
    ): VoxelShape {
        val type = state.getValue(TYPE)
        if (type == SlabType.DOUBLE) return Shapes.block()

        return when (state.getValue(AXIS)) {
            Direction.Axis.Y -> if (type == SlabType.TOP) SHAPE_TOP_Y else SHAPE_BOTTOM_Y
            Direction.Axis.X -> if (type == SlabType.TOP) SHAPE_TOP_X else SHAPE_BOTTOM_X
            Direction.Axis.Z -> if (type == SlabType.TOP) SHAPE_TOP_Z else SHAPE_BOTTOM_Z
        }
    }

    private fun placementForEmpty(ctx: BlockPlaceContext): Placement {
        val pos = ctx.clickedPos
        val face = ctx.clickedFace
        val localY = ctx.clickLocation.y - pos.y

        val type = if (face != Direction.DOWN && (face == Direction.UP || localY <= 0.5)) {
            SlabType.BOTTOM
        } else {
            SlabType.TOP
        }
        return Placement(type, Direction.Axis.Y)
    }

    private fun placementForExisting(state: BlockState, ctx: BlockPlaceContext): Placement {
        val pos = ctx.clickedPos
        val axis = state.getValue(AXIS)
        val face = ctx.clickedFace
        val localX = ctx.clickLocation.x - pos.x
        val localY = ctx.clickLocation.y - pos.y
        val localZ = ctx.clickLocation.z - pos.z

        val type = if (face.axis == axis) {
            when (axis) {
                Direction.Axis.Y -> if (face == Direction.UP) SlabType.TOP else SlabType.BOTTOM
                Direction.Axis.X -> if (face == Direction.EAST) SlabType.TOP else SlabType.BOTTOM
                Direction.Axis.Z -> if (face == Direction.SOUTH) SlabType.TOP else SlabType.BOTTOM
            }
        } else {
            when (axis) {
                Direction.Axis.Y -> if (localY > 0.5) SlabType.TOP else SlabType.BOTTOM
                Direction.Axis.X -> if (localX > 0.5) SlabType.TOP else SlabType.BOTTOM
                Direction.Axis.Z -> if (localZ > 0.5) SlabType.TOP else SlabType.BOTTOM
            }
        }

        return Placement(type, axis)
    }

    override fun getStateForPlacement(ctx: BlockPlaceContext): BlockState? {
        val pos = ctx.clickedPos
        val existing = ctx.level.getBlockState(pos)
        if (existing.`is`(this)) {
            if (!CopycatDatapackManager.isBlockEnabled(existing)) return null
            val existingType = existing.getValue(TYPE)
            if (existingType != SlabType.DOUBLE) {
                val desired = placementForExisting(existing, ctx)
                if (desired.axis == existing.getValue(AXIS) && desired.type != existingType) {
                    return existing.setValue(TYPE, SlabType.DOUBLE).setValue(WATERLOGGED, false)
                }
            }
            return existing
        }

        val fluid = ctx.level.getFluidState(pos)
        val placement = placementForEmpty(ctx)
        val placed = defaultBlockState()
            .setValue(TYPE, placement.type)
            .setValue(AXIS, placement.axis)
            .setValue(WATERLOGGED, fluid.type == Fluids.WATER)
        return if (CopycatDatapackManager.isBlockEnabled(placed)) placed else null
    }

    override fun canBeReplaced(state: BlockState, ctx: BlockPlaceContext): Boolean {
        if (!CopycatDatapackManager.isBlockEnabled(state)) return false
        if (state.getValue(TYPE) == SlabType.DOUBLE || !ctx.itemInHand.`is`(asItem())) return false
        if (!ctx.replacingClickedOnBlock()) return true

        val desired = placementForExisting(state, ctx)
        return desired.axis == state.getValue(AXIS) && desired.type != state.getValue(TYPE)
    }

    override fun onPlace(state: BlockState, level: Level, pos: BlockPos, oldState: BlockState, movedByPiston: Boolean) {
        super.onPlace(state, level, pos, oldState, movedByPiston)
        if (level.isClientSide) return
        if (!oldState.`is`(this)) return
        if (state.getValue(TYPE) != SlabType.DOUBLE || oldState.getValue(TYPE) == SlabType.DOUBLE) return

        val blockEntity = level.getBlockEntity(pos) as? CopycatSlabBlockEntity ?: return
        blockEntity.promoteToDouble(oldState.getValue(TYPE))
    }

    override fun setPlacedBy(level: Level, pos: BlockPos, state: BlockState, placer: LivingEntity?, stack: ItemStack) {
        if (!CopycatDatapackManager.isBlockEnabled(state)) return
        if (placer == null) return

        val offhandItem = placer.getItemInHand(InteractionHand.OFF_HAND)
        val appliedState = getAcceptedBlockState(level, pos, offhandItem, Direction.orderedByNearest(placer)[0]) ?: return
        val blockEntity = level.getBlockEntity(pos) as? CopycatSlabBlockEntity ?: return

        val targetHalf = if (state.getValue(TYPE) == SlabType.TOP) SlabType.TOP else SlabType.BOTTOM
        if (blockEntity.hasCustomMaterial(targetHalf)) return

        blockEntity.setHalfMaterial(targetHalf, appliedState, offhandItem)

        if (placer is Player && placer.isCreative) return
        offhandItem.shrink(1)
        if (offhandItem.isEmpty) {
            placer.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY)
        }
    }

    private fun targetHalf(state: BlockState, hitX: Double, hitY: Double, hitZ: Double, face: Direction): SlabType {
        val slabType = state.getValue(TYPE)
        if (slabType != SlabType.DOUBLE) return slabType

        val axis = state.getValue(AXIS)
        if (face.axis == axis) {
            return when (axis) {
                Direction.Axis.Y -> if (face == Direction.UP) SlabType.TOP else SlabType.BOTTOM
                Direction.Axis.X -> if (face == Direction.EAST) SlabType.TOP else SlabType.BOTTOM
                Direction.Axis.Z -> if (face == Direction.SOUTH) SlabType.TOP else SlabType.BOTTOM
            }
        }

        return when (axis) {
            Direction.Axis.Y -> if (hitY > 0.5) SlabType.TOP else SlabType.BOTTOM
            Direction.Axis.X -> if (hitX > 0.5) SlabType.TOP else SlabType.BOTTOM
            Direction.Axis.Z -> if (hitZ > 0.5) SlabType.TOP else SlabType.BOTTOM
        }
    }

    public override fun useItemOn(
        stack: ItemStack,
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hand: InteractionHand,
        hitResult: BlockHitResult
    ): InteractionResult {
        if (!CopycatDatapackManager.isBlockEnabled(state)) return InteractionResult.PASS
        val blockEntity = level.getBlockEntity(pos) as? CopycatSlabBlockEntity
            ?: return super.useItemOn(stack, state, level, pos, player, hand, hitResult)

        val materialIn = getAcceptedBlockState(level, pos, stack, hitResult.direction)
            ?: return super.useItemOn(stack, state, level, pos, player, hand, hitResult)
        val material = prepareMaterial(level, pos, state, player, hand, hitResult, materialIn)
            ?: return InteractionResult.TRY_WITH_EMPTY_HAND

        val localX = hitResult.location.x - pos.x
        val localY = hitResult.location.y - pos.y
        val localZ = hitResult.location.z - pos.z
        val half = targetHalf(state, localX, localY, localZ, hitResult.direction)

        val current = blockEntity.getHalfMaterial(half)
        if (current.`is`(material.block)) {
            if (!blockEntity.cycleHalfMaterial(half)) {
                return InteractionResult.TRY_WITH_EMPTY_HAND
            }
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.75f, 0.95f)
            return InteractionResult.SUCCESS
        }

        if (blockEntity.hasCustomMaterial(half)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND
        }

        if (level.isClientSide) {
            return InteractionResult.SUCCESS
        }

        blockEntity.setHalfMaterial(half, material, stack)
        level.playSound(null, pos, material.soundType.placeSound, SoundSource.BLOCKS, 1f, 0.75f)
        if (!player.isCreative) {
            stack.shrink(1)
            if (stack.isEmpty) {
                player.setItemInHand(hand, ItemStack.EMPTY)
            }
        }
        return InteractionResult.SUCCESS
    }

    override fun onWrenched(state: BlockState, context: UseOnContext): InteractionResult {
        if (!CopycatDatapackManager.isBlockEnabled(state)) return InteractionResult.PASS
        val level = context.level
        val pos = context.clickedPos
        val player = context.player ?: return InteractionResult.PASS
        val blockEntity = level.getBlockEntity(pos) as? CopycatSlabBlockEntity ?: return InteractionResult.PASS

        val localX = context.clickLocation.x - pos.x
        val localY = context.clickLocation.y - pos.y
        val localZ = context.clickLocation.z - pos.z
        val half = targetHalf(state, localX, localY, localZ, context.clickedFace)

        if (!blockEntity.hasCustomMaterial(half)) {
            return InteractionResult.PASS
        }

        if (level.isClientSide) return InteractionResult.SUCCESS

        val removedState = blockEntity.getHalfMaterial(half)
        val removedStack = blockEntity.getHalfConsumedItem(half).copy()
        blockEntity.clearHalfMaterial(half)

        if (!player.isCreative && !removedStack.isEmpty) {
            player.inventory.placeItemBackInInventory(removedStack)
        }

        level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.75f, 0.95f)
        level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, getId(removedState))
        return InteractionResult.SUCCESS
    }

    override fun playerWillDestroy(level: Level, pos: BlockPos, state: BlockState, player: Player): BlockState {
        if (player.isCreative) {
            (level.getBlockEntity(pos) as? CopycatSlabBlockEntity)?.clearTopConsumedItem()
        }
        return super.playerWillDestroy(level, pos, state, player)
    }

    override fun getFluidState(state: BlockState): FluidState {
        return if (state.getValue(WATERLOGGED)) Fluids.WATER.getSource(false) else super.getFluidState(state)
    }

    override fun getLuminance(world: BlockGetter, pos: BlockPos): Int {
        val state = world.getBlockState(pos)
        if (!state.`is`(this)) return super.getLuminance(world, pos)

        val be = world.getBlockEntity(pos) as? CopycatSlabBlockEntity ?: return super.getLuminance(world, pos)
        return when (state.getValue(TYPE)) {
            SlabType.BOTTOM -> be.getHalfMaterial(SlabType.BOTTOM).lightEmission
            SlabType.TOP -> be.getHalfMaterial(SlabType.TOP).lightEmission
            SlabType.DOUBLE -> maxOf(
                be.getHalfMaterial(SlabType.BOTTOM).lightEmission,
                be.getHalfMaterial(SlabType.TOP).lightEmission
            )
        }
    }

    override fun placeLiquid(level: LevelAccessor, pos: BlockPos, state: BlockState, fluidState: FluidState): Boolean {
        return if (state.getValue(TYPE) != SlabType.DOUBLE) {
            super.placeLiquid(level, pos, state, fluidState)
        } else {
            false
        }
    }

    override fun canPlaceLiquid(
        entity: LivingEntity?,
        level: BlockGetter,
        pos: BlockPos,
        state: BlockState,
        fluid: Fluid
    ): Boolean {
        return if (state.getValue(TYPE) != SlabType.DOUBLE) {
            super.canPlaceLiquid(entity, level, pos, state, fluid)
        } else {
            false
        }
    }

    override fun isPathfindable(state: BlockState, type: PathComputationType): Boolean {
        return when (type) {
            PathComputationType.WATER -> state.fluidState.`is`(FluidTags.WATER)
            else -> false
        }
    }
}
