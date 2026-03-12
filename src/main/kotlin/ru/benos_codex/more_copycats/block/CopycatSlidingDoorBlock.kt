package ru.benos_codex.more_copycats.block

import net.minecraft.world.level.block.state.BlockBehaviour.Properties

import com.zurrtum.create.AllItemTags
import com.zurrtum.create.AllBlocks
import com.zurrtum.create.content.decoration.copycat.CopycatBlock
import com.zurrtum.create.content.decoration.slidingDoor.SlidingDoorBlock
import com.zurrtum.create.content.decoration.slidingDoor.SlidingDoorBlockEntity
import com.zurrtum.create.foundation.block.AppearanceControlBlock
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.BlockAndTintGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.DoorBlock
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.LevelEvent
import net.minecraft.world.level.block.StairBlock
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.Shapes
import ru.benos_codex.more_copycats.MoreCopycatsRegister
import ru.benos_codex.more_copycats.block.entity.CopycatSlidingDoorBlockEntity
import ru.benos_codex.more_copycats.datapack.CopycatDatapackManager

class CopycatSlidingDoorBlock(props: Properties) :
    SlidingDoorBlock(props, TRAIN_SET_TYPE.get(), false), AppearanceControlBlock {

    @Suppress("UNCHECKED_CAST")
    override fun getBlockEntityClass(): Class<SlidingDoorBlockEntity> = CopycatSlidingDoorBlockEntity::class.java as Class<SlidingDoorBlockEntity>

    override fun getBlockEntityType(): BlockEntityType<out CopycatSlidingDoorBlockEntity> = MoreCopycatsRegister.SLIDING_DOOR_BE

    // Allow external CT models to "see" this block as its current copycat material.
    override fun getAppearance(
        state: BlockState,
        level: BlockAndTintGetter,
        toPos: BlockPos,
        side: Direction,
        reference: BlockState?,
        fromPos: BlockPos?
    ): BlockState {
        if (state.hasProperty(VISIBLE) && !state.getValue(VISIBLE)) {
            return state
        }
        val half = if (state.hasProperty(HALF)) state.getValue(HALF) else DoubleBlockHalf.LOWER
        val be = findDoorBlockEntity(level, toPos, state)
        return be?.getMaterialState(half) ?: AllBlocks.COPYCAT_BASE.defaultBlockState()
    }

    override fun getStateForPlacement(ctx: BlockPlaceContext): BlockState? {
        val state = super.getStateForPlacement(ctx) ?: return null
        return if (CopycatDatapackManager.isBlockEnabled(state)) state else null
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
            val result = onWrenched(state, UseOnContext(player, hand, hitResult))
            return if (result == InteractionResult.PASS) InteractionResult.SUCCESS else result
        }

        val material = getAcceptedBlockState(level, pos, stack, hitResult.direction)
            ?: return super.useItemOn(stack, state, level, pos, player, hand, hitResult)
        val be = findDoorBlockEntity(level, pos, state) ?: return super.useItemOn(stack, state, level, pos, player, hand, hitResult)
        val targetHalf = state.getValue(HALF)

        if (level.isClientSide) {
            return InteractionResult.SUCCESS
        }

        if (be.getMaterialState(targetHalf).`is`(material.block) || be.hasCustomMaterial(targetHalf)) {
            if (!be.cycleMaterial(targetHalf)) {
                return InteractionResult.TRY_WITH_EMPTY_HAND
            }
            level.playSound(null, pos, net.minecraft.sounds.SoundEvents.ITEM_FRAME_ADD_ITEM, net.minecraft.sounds.SoundSource.BLOCKS, 0.75f, 0.95f)
            return InteractionResult.SUCCESS
        }

        if (be.hasCustomMaterial(targetHalf)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND
        }

        be.setHalfMaterial(targetHalf, material, stack)
        level.playSound(
            null,
            pos,
            material.soundType.placeSound,
            net.minecraft.sounds.SoundSource.BLOCKS,
            1.0f,
            0.75f
        )

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
        val be = findDoorBlockEntity(level, pos, state) ?: return InteractionResult.PASS
        val targetHalf = state.getValue(HALF)
        if (!be.hasCustomMaterial(targetHalf)) return InteractionResult.PASS

        val player = context.player
        val removedState = be.getMaterialState(targetHalf)
        val removedStack = be.getConsumedItemStack(targetHalf).copy()

        if (player != null && !player.isCreative && !level.isClientSide) {
            player.inventory.placeItemBackInInventory(removedStack)
        }

        if (!level.isClientSide) {
            level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, getId(removedState))
            be.clearMaterial(targetHalf)
        }

        return InteractionResult.SUCCESS
    }

    override fun setPlacedBy(level: Level, pos: BlockPos, state: BlockState, placer: LivingEntity?, stack: ItemStack) {
        if (!CopycatDatapackManager.isBlockEnabled(state)) return
        super.setPlacedBy(level, pos, state, placer, stack)
        if (placer == null) return

        val offhand = placer.getItemInHand(InteractionHand.OFF_HAND)
        val appliedState = getAcceptedBlockState(level, pos, offhand, Direction.orderedByNearest(placer).firstOrNull() ?: Direction.NORTH)
            ?: return
        val be = findDoorBlockEntity(level, pos, state) ?: return
        if (be.hasCustomMaterial(DoubleBlockHalf.LOWER)) return

        if (!level.isClientSide) {
            be.setHalfMaterial(DoubleBlockHalf.LOWER, appliedState, offhand)
        }

        if (placer is Player && placer.isCreative) return
        offhand.shrink(1)
        if (offhand.isEmpty) {
            placer.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY)
        }
    }

    override fun playerWillDestroy(level: Level, pos: BlockPos, state: BlockState, player: Player): BlockState {
        if (player.isCreative) {
            findDoorBlockEntity(level, pos, state)?.clearAllMaterials()
        }
        return super.playerWillDestroy(level, pos, state, player)
    }

    private fun findDoorBlockEntity(level: BlockAndTintGetter, pos: BlockPos, state: BlockState): CopycatSlidingDoorBlockEntity? {
        val preferred = if (state.getValue(HALF) == DoubleBlockHalf.UPPER) pos.below() else pos
        (level.getBlockEntity(preferred) as? CopycatSlidingDoorBlockEntity)?.let { return it }
        (level.getBlockEntity(pos) as? CopycatSlidingDoorBlockEntity)?.let { return it }
        (level.getBlockEntity(pos.below()) as? CopycatSlidingDoorBlockEntity)?.let { return it }
        return level.getBlockEntity(pos.above()) as? CopycatSlidingDoorBlockEntity
    }

    private fun getAcceptedBlockState(level: Level, pos: BlockPos, stack: ItemStack, face: Direction?): BlockState? {
        val blockItem = stack.item as? BlockItem ?: return null
        val block = blockItem.block
        if (block is CopycatSlidingDoorBlock || block is CopycatBlock) return null

        var appliedState = block.defaultBlockState()
        if (block is EntityBlock) return null
        if (block is StairBlock) return null

        val shape = appliedState.getShape(level, pos)
        if (shape.isEmpty || shape.bounds() != Shapes.block().bounds()) return null
        if (appliedState.getCollisionShape(level, pos).isEmpty) return null

        if (face != null) {
            val axis = face.axis
            if (appliedState.hasProperty(BlockStateProperties.FACING)) {
                appliedState = appliedState.setValue(BlockStateProperties.FACING, face)
            }
            if (appliedState.hasProperty(BlockStateProperties.HORIZONTAL_FACING) && axis != Direction.Axis.Y) {
                appliedState = appliedState.setValue(BlockStateProperties.HORIZONTAL_FACING, face)
            }
            if (appliedState.hasProperty(BlockStateProperties.AXIS)) {
                appliedState = appliedState.setValue(BlockStateProperties.AXIS, axis)
            }
            if (appliedState.hasProperty(BlockStateProperties.HORIZONTAL_AXIS) && axis != Direction.Axis.Y) {
                appliedState = appliedState.setValue(BlockStateProperties.HORIZONTAL_AXIS, axis)
            }
        }

        return appliedState
    }
}
