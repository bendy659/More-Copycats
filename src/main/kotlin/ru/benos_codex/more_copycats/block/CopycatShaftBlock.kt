package ru.benos_codex.more_copycats.block

import com.zurrtum.create.AllItemTags
import com.zurrtum.create.AllItems
import com.zurrtum.create.catnip.placement.PlacementHelpers
import com.zurrtum.create.content.decoration.bracket.BracketBlock
import com.zurrtum.create.content.decoration.copycat.CopycatBlock
import com.zurrtum.create.content.kinetics.base.KineticBlockEntity
import com.zurrtum.create.content.kinetics.simpleRelays.ShaftBlock
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.LevelEvent
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.StairBlock
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import ru.benos_codex.more_copycats.MoreCopycatsRegister
import ru.benos_codex.more_copycats.block.entity.CopycatShaftBlockEntity
import ru.benos_codex.more_copycats.datapack.CopycatDatapackManager

open class CopycatShaftBlock(properties: Properties) : ShaftBlock(properties) {
    override fun getBlockEntityType(): BlockEntityType<out KineticBlockEntity> =
        MoreCopycatsRegister.SHAFT_BE

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState? =
        super.getStateForPlacement(context)?.takeIf(CopycatDatapackManager::isBlockEnabled)

    override fun useWithoutItem(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hitResult: BlockHitResult
    ): InteractionResult {
        if (!CopycatDatapackManager.isBlockEnabled(state))
            return InteractionResult.PASS

        return super.useWithoutItem(state, level, pos, player, hitResult)
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
        if (!CopycatDatapackManager.isBlockEnabled(state))
            return InteractionResult.PASS

        if (stack.`is`(AllItemTags.TOOLS_WRENCH)) {
            val context = UseOnContext(player, hand, hitResult)
            val result = if (player.isShiftKeyDown) onSneakWrenched(state, context) else onWrenched(state, context)
            return if (result == InteractionResult.PASS) InteractionResult.SUCCESS else result
        }

        val slot = slotFromHit(hitResult.location, pos, state)
        val materialIn = getAcceptedBlockState(level, pos, stack, hitResult.direction)
        if (materialIn != null) {
            val material = prepareMaterial(level, pos, materialIn, slot)
                ?: return InteractionResult.TRY_WITH_EMPTY_HAND
            val blockEntity = level.getBlockEntity(pos) as? CopycatShaftBlockEntity
                ?: return super.useItemOn(stack, state, level, pos, player, hand, hitResult)
            val current = blockEntity.getSlotMaterial(slot)

            if (current.`is`(material.block) || blockEntity.hasCustomMaterial(slot)) {
                if (!blockEntity.cycleSlotMaterial(slot))
                    return InteractionResult.TRY_WITH_EMPTY_HAND

                level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.75f, 0.95f)
                return InteractionResult.SUCCESS
            }

            if (level.isClientSide)
                return InteractionResult.SUCCESS

            blockEntity.setSlotMaterial(slot, material, stack)
            level.playSound(null, pos, material.soundType.placeSound, SoundSource.BLOCKS, 1.0f, 0.75f)
            if (!player.isCreative) {
                stack.shrink(1)
                if (stack.isEmpty)
                    player.setItemInHand(hand, ItemStack.EMPTY)
            }
            return InteractionResult.SUCCESS
        }

        if (stack.`is`(AllItems.METAL_GIRDER))
            return InteractionResult.TRY_WITH_EMPTY_HAND

        val helper = PlacementHelpers.get(placementHelperId)
        if (helper.matchesItem(stack))
            return helper.getOffset(player, level, state, pos, hitResult)
                .placeInWorld(level, stack.item as BlockItem, player, hand)

        return super.useItemOn(stack, state, level, pos, player, hand, hitResult)
    }

    override fun onWrenched(state: BlockState, context: UseOnContext): InteractionResult {
        if (!CopycatDatapackManager.isBlockEnabled(state))
            return InteractionResult.PASS

        val level = context.level
        val pos = context.clickedPos
        val blockEntity = level.getBlockEntity(pos) as? CopycatShaftBlockEntity ?: return super.onWrenched(state, context)
        val slot = slotFromHit(context.clickLocation, pos, state)
        if (!blockEntity.hasCustomMaterial(slot))
            return super.onWrenched(state, context)
        if (level.isClientSide)
            return InteractionResult.SUCCESS

        val removedState = blockEntity.getSlotMaterial(slot)
        val removedStack = blockEntity.getSlotConsumedItem(slot).copy()
        blockEntity.clearSlotMaterial(slot)

        val player = context.player
        if (player != null && !player.isCreative && !removedStack.isEmpty)
            player.inventory.placeItemBackInInventory(removedStack)

        level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.75f, 0.95f)
        level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, getId(removedState))
        return InteractionResult.SUCCESS
    }

    override fun onSneakWrenched(state: BlockState, context: UseOnContext): InteractionResult {
        if (!CopycatDatapackManager.isBlockEnabled(state))
            return InteractionResult.PASS

        return super.onSneakWrenched(state, context)
    }

    override fun setPlacedBy(
        level: Level,
        pos: BlockPos,
        state: BlockState,
        placer: LivingEntity?,
        stack: ItemStack
    ) {
        if (!CopycatDatapackManager.isBlockEnabled(state))
            return

        super.setPlacedBy(level, pos, state, placer, stack)
        if (placer == null)
            return

        val offhand = placer.getItemInHand(InteractionHand.OFF_HAND)
        val applied = getAcceptedBlockState(
            level,
            pos,
            offhand,
            Direction.orderedByNearest(placer).firstOrNull() ?: Direction.NORTH
        ) ?: return
        val blockEntity = level.getBlockEntity(pos) as? CopycatShaftBlockEntity ?: return
        val defaultSlot = defaultPlacementSlot(state)
        if (blockEntity.hasCustomMaterial(defaultSlot))
            return

        if (!level.isClientSide)
            blockEntity.setSlotMaterial(defaultSlot, applied, offhand)

        if (placer is Player && placer.isCreative)
            return

        offhand.shrink(1)
        if (offhand.isEmpty)
            placer.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY)
    }

    override fun playerWillDestroy(level: Level, pos: BlockPos, state: BlockState, player: Player): BlockState {
        val blockEntity = level.getBlockEntity(pos) as? CopycatShaftBlockEntity
        if (blockEntity != null) {
            if (player.isCreative) {
                blockEntity.clearAllMaterials()
            } else if (!level.isClientSide) {
                for (slot in CopycatShaftBlockEntity.Slot.entries) {
                    val stored = blockEntity.getSlotConsumedItem(slot)
                    if (!stored.isEmpty)
                        Block.popResource(level, pos, stored.copy())
                }
                blockEntity.clearAllMaterials()
            }
        }

        return super.playerWillDestroy(level, pos, state, player)
    }

    override fun getRenderShape(state: BlockState): RenderShape =
        RenderShape.INVISIBLE

    protected open fun defaultPlacementSlot(state: BlockState): CopycatShaftBlockEntity.Slot =
        CopycatShaftBlockEntity.Slot.MAT_0

    protected open fun slotFromHit(hit: Vec3, pos: BlockPos, state: BlockState): CopycatShaftBlockEntity.Slot =
        CopycatShaftBlockEntity.Slot.MAT_0

    protected fun prepareMaterial(
        level: Level,
        pos: BlockPos,
        material: BlockState,
        slot: CopycatShaftBlockEntity.Slot
    ): BlockState? {
        val blockEntity = level.getBlockEntity(pos) as? CopycatShaftBlockEntity ?: return material
        return if (blockEntity.hasCustomMaterial(slot)) blockEntity.getSlotMaterial(slot) else material
    }

    protected fun getAcceptedBlockState(level: Level, pos: BlockPos, stack: ItemStack, face: Direction?): BlockState? {
        val blockItem = stack.item as? BlockItem ?: return null
        val block = blockItem.block
        if (block is BracketBlock)
            return null
        if (block is CopycatShaftBlock || block is CopycatEncasedShaftBlock || block is ShaftBlock || block is CopycatBlock)
            return null
        if (block is EntityBlock || block is StairBlock)
            return null

        var appliedState = block.defaultBlockState()
        val shape = appliedState.getShape(level, pos)
        if (shape.isEmpty || shape.bounds() != net.minecraft.world.phys.shapes.Shapes.block().bounds())
            return null
        if (appliedState.getCollisionShape(level, pos).isEmpty)
            return null

        if (face != null) {
            val axis = face.axis
            if (appliedState.hasProperty(BlockStateProperties.FACING))
                appliedState = appliedState.setValue(BlockStateProperties.FACING, face)
            if (appliedState.hasProperty(BlockStateProperties.HORIZONTAL_FACING) && axis != Direction.Axis.Y)
                appliedState = appliedState.setValue(BlockStateProperties.HORIZONTAL_FACING, face)
            if (appliedState.hasProperty(BlockStateProperties.AXIS))
                appliedState = appliedState.setValue(BlockStateProperties.AXIS, axis)
            if (appliedState.hasProperty(BlockStateProperties.HORIZONTAL_AXIS) && axis != Direction.Axis.Y)
                appliedState = appliedState.setValue(BlockStateProperties.HORIZONTAL_AXIS, axis)
        }

        return appliedState
    }
}
