package ru.benos_codex.more_copycats.block

import net.minecraft.world.level.block.state.BlockBehaviour.Properties

import com.zurrtum.create.content.decoration.copycat.CopycatBlock
import com.zurrtum.create.content.decoration.copycat.CopycatBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.BlockAndTintGetter
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult
import ru.benos_codex.more_copycats.block.entity.MaterialLightHelper
import ru.benos_codex.more_copycats.MoreCopycatsRegister
import ru.benos_codex.more_copycats.datapack.CopycatDatapackManager

open class CopycatSimpleBlock(props: Properties) : CopycatBlock(props) {
    override fun getBlockEntityType(): BlockEntityType<out CopycatBlockEntity> = MoreCopycatsRegister.SIMPLE_BE

    override fun canConnectTexturesToward(
        reader: BlockAndTintGetter,
        fromPos: BlockPos,
        toPos: BlockPos,
        state: BlockState
    ): Boolean = true

    override fun getStateForPlacement(ctx: BlockPlaceContext): BlockState? {
        val state = super.getStateForPlacement(ctx) ?: return null
        return if (CopycatDatapackManager.isBlockEnabled(state)) state else null
    }

    override fun useWithoutItem(
        state: BlockState,
        level: net.minecraft.world.level.Level,
        pos: BlockPos,
        player: Player,
        hitResult: BlockHitResult
    ): InteractionResult {
        if (!CopycatDatapackManager.isBlockEnabled(state)) {
            return InteractionResult.PASS
        }
        return super.useWithoutItem(state, level, pos, player, hitResult)
    }

    override fun useItemOn(
        stack: ItemStack,
        state: BlockState,
        level: net.minecraft.world.level.Level,
        pos: BlockPos,
        player: Player,
        hand: InteractionHand,
        hitResult: BlockHitResult
    ): InteractionResult {
        if (!CopycatDatapackManager.isBlockEnabled(state)) {
            return InteractionResult.PASS
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hitResult)
    }

    override fun getLuminance(world: BlockGetter, pos: BlockPos): Int {
        val state = world.getBlockState(pos)
        if (!state.`is`(this)) return super.getLuminance(world, pos)

        val be = world.getBlockEntity(pos) as? CopycatBlockEntity ?: return super.getLuminance(world, pos)
        if (!be.hasCustomMaterial()) return super.getLuminance(world, pos)

        return MaterialLightHelper.resolvedEmission(world, pos, state, be.material)
    }

    override fun prepareMaterial(
        pLevel: net.minecraft.world.level.Level,
        pPos: BlockPos,
        pState: BlockState,
        pPlayer: Player,
        pHand: InteractionHand,
        pHit: BlockHitResult,
        material: BlockState
    ): BlockState? {
        val be = pLevel.getBlockEntity(pPos) as? CopycatBlockEntity ?: return material
        return if (be.hasCustomMaterial()) be.material else material
    }

    override fun onWrenched(state: BlockState, context: UseOnContext): InteractionResult {
        if (!CopycatDatapackManager.isBlockEnabled(state)) {
            return InteractionResult.PASS
        }
        return super.onWrenched(state, context)
    }
}
