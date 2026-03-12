package ru.benos_codex.more_copycats.block

import net.minecraft.world.level.block.state.BlockBehaviour.Properties

import com.zurrtum.create.content.decoration.copycat.CopycatBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.LevelEvent
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult
import ru.benos_codex.more_copycats.MoreCopycats.DEFAULT_MATERIAL
import ru.benos_codex.more_copycats.MoreCopycatsRegister
import ru.benos_codex.more_copycats.block.entity.CopycatBiteBlockEntity

class CopycatBiteSimpleBlock(props: Properties) : CopycatBiteBlock(props) {

    override fun getBlockEntityType(): BlockEntityType<out CopycatBlockEntity> =
        MoreCopycatsRegister.BITE_SIMPLE_BE

    override fun useItemOn(
        stack: ItemStack,
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hand: InteractionHand,
        hitResult: BlockHitResult
    ): InteractionResult {
        val be = level.getBlockEntity(pos) as? CopycatBiteBlockEntity ?: return InteractionResult.PASS

        if (stack.`is`(MoreCopycatsRegister.UV_TOOL)) {
            return InteractionResult.PASS
        }

        if (stack.`is`(this.asItem())) {
            val index = getTargetIndex(hitResult.location, pos, hitResult.direction, true, be)
            if (index != null) {
                if (level.isClientSide) return InteractionResult.SUCCESS
                be.addPart(index)
                level.playSound(null, pos, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 1f, 1f)
                if (!player.isCreative) stack.shrink(1)
                return InteractionResult.SUCCESS
            }
            return InteractionResult.PASS
        }

        val material = getAcceptedBlockState(level, pos, stack, hitResult.direction)
        if (material != null) {
            if (be.isEmpty) return InteractionResult.PASS
            val index = getTargetIndex(hitResult.location, pos, hitResult.direction, false, be) ?: return InteractionResult.CONSUME
            if (be.isPartEmpty(index)) return InteractionResult.CONSUME
            val face = hitResult.direction

            if (be.isFaceHasMaterial(index, face)) {
                if (level.isClientSide) return InteractionResult.SUCCESS
                if (!be.cycleMaterialFace(index, face)) return InteractionResult.TRY_WITH_EMPTY_HAND
                val cycled = be.getFaceState(index, face) ?: return InteractionResult.TRY_WITH_EMPTY_HAND
                be.setMaterialAllFaces(index, cycled)
                level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.75f, 0.95f)
                return InteractionResult.SUCCESS
            }

            if (level.isClientSide) return InteractionResult.SUCCESS
            be.setMaterialAllFaces(index, material)
            level.playSound(null, pos, material.soundType.placeSound, SoundSource.BLOCKS, 1f, 1f)
            return InteractionResult.SUCCESS
        }

        return InteractionResult.PASS
    }

    override fun onWrenched(state: BlockState, context: UseOnContext): InteractionResult {
        val level = context.level
        val pos = context.clickedPos
        val be = level.getBlockEntity(pos) as? CopycatBiteBlockEntity ?: return InteractionResult.PASS
        val player = context.player ?: return InteractionResult.PASS

        if (player.isShiftKeyDown) {
            if (level.isClientSide) return InteractionResult.SUCCESS

            for (i in 0 until CopycatBiteBlockEntity.MAX_ITEM) {
                if (!be.isPartEmpty(i)) {
                    player.inventory.placeItemBackInInventory(ItemStack(this.asItem()))
                }
            }

            level.playSound(null, pos, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 1f, 1f)
            level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, getId(state))
            level.removeBlock(pos, false)
            return InteractionResult.SUCCESS
        }

        val index = getTargetIndex(context.clickLocation, pos, context.clickedFace, false, be) ?: return InteractionResult.PASS
        if (be.isPartEmpty(index)) return InteractionResult.PASS
        if (level.isClientSide) return InteractionResult.SUCCESS

        player.inventory.placeItemBackInInventory(ItemStack(this.asItem()))
        level.playSound(null, pos, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 0.75f, 1.2f)
        be.removePart(index)
        level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, getId(DEFAULT_MATERIAL))

        if (be.isEmpty) {
            level.setBlock(pos, DEFAULT_MATERIAL, 3)
        }

        return InteractionResult.SUCCESS
    }
}
