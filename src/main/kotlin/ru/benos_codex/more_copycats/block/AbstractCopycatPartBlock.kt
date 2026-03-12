package ru.benos_codex.more_copycats.block

import com.zurrtum.create.content.decoration.copycat.CopycatBlock
import com.zurrtum.create.content.decoration.copycat.CopycatBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
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
import net.minecraft.world.level.block.LevelEvent
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import ru.benos_codex.more_copycats.MoreCopycats.DEFAULT_MATERIAL
import ru.benos_codex.more_copycats.MoreCopycatsRegister
import ru.benos_codex.more_copycats.block.entity.AbstractCopycatPartBlockEntity
import ru.benos_codex.more_copycats.menu.CopycatUvToolMenuProvider

abstract class AbstractCopycatPartBlock<E : AbstractCopycatPartBlockEntity>(
    props: BlockBehaviour.Properties
) : CopycatBlock(props) {
    protected abstract val maxParts: Int
    protected abstract val placementHit: ThreadLocal<Vec3?>

    protected abstract fun getPartBlockEntity(level: BlockGetter, pos: BlockPos): E?
    protected abstract fun getTargetIndex(
        hit: Vec3,
        pos: BlockPos,
        face: Direction,
        addingPart: Boolean,
        blockEntity: E
    ): Int?

    override fun canConnectTexturesToward(
        p0: BlockAndTintGetter?,
        p1: BlockPos?,
        p2: BlockPos?,
        p3: BlockState?
    ): Boolean =
        true

    override fun setPlacedBy(
        level: Level,
        pos: BlockPos,
        state: BlockState,
        placer: LivingEntity?,
        stack: ItemStack
    ) {
        super.setPlacedBy(level, pos, state, placer, stack)
        if (level.isClientSide) {
            return
        }

        val blockEntity = getPartBlockEntity(level, pos) ?: return
        val hitPos = placementHit.get()
        val index = if (hitPos != null) getIndexFromRelative(hitPos.x - pos.x, hitPos.y - pos.y, hitPos.z - pos.z) else 0
        blockEntity.addPart(index)
    }

    override fun canBeReplaced(state: BlockState, ctx: BlockPlaceContext): Boolean {
        if (ctx.player?.isShiftKeyDown == true) {
            return super.canBeReplaced(state, ctx)
        }

        if (!ctx.itemInHand.`is`(this.asItem())) {
            return super.canBeReplaced(state, ctx)
        }

        val blockEntity = getPartBlockEntity(ctx.level, ctx.clickedPos) ?: return false

        return getTargetIndex(ctx.clickLocation, ctx.clickedPos, ctx.clickedFace, addingPart = true, blockEntity) != null
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
        val blockEntity = getPartBlockEntity(level, pos) ?: return InteractionResult.PASS

        if (stack.`is`(MoreCopycatsRegister.UV_TOOL)) {
            val index = getTargetIndex(hitResult.location, pos, hitResult.direction, addingPart = false, blockEntity)
                ?: return InteractionResult.PASS
            if (blockEntity.isPartEmpty(index)) {
                return InteractionResult.PASS
            }
            if (level.isClientSide) {
                return InteractionResult.SUCCESS
            }

            player.openMenu(CopycatUvToolMenuProvider(pos, index, hitResult.direction.ordinal))
            return InteractionResult.SUCCESS
        }

        if (stack.`is`(this.asItem())) {
            val index = getTargetIndex(hitResult.location, pos, hitResult.direction, addingPart = true, blockEntity)
            if (index == null) {
                return InteractionResult.PASS
            }
            if (level.isClientSide) {
                return InteractionResult.SUCCESS
            }

            blockEntity.addPart(index)
            level.playSound(null, pos, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 1f, 1f)
            if (!player.isCreative) {
                stack.shrink(1)
            }

            return InteractionResult.SUCCESS
        }

        val material = getAcceptedBlockState(level, pos, stack, hitResult.direction)
        if (material == null) {
            return InteractionResult.PASS
        }
        if (blockEntity.isEmpty) {
            return InteractionResult.PASS
        }

        val index = getTargetIndex(hitResult.location, pos, hitResult.direction, addingPart = false, blockEntity)
            ?: return InteractionResult.CONSUME
        if (blockEntity.isPartEmpty(index)) {
            return InteractionResult.CONSUME
        }

        val face = hitResult.direction
        if (blockEntity.isFaceHasMaterial(index, face)) {
            if (level.isClientSide) {
                return InteractionResult.SUCCESS
            }
            if (!blockEntity.cycleMaterialFace(index, face)) {
                return InteractionResult.TRY_WITH_EMPTY_HAND
            }

            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.75f, 0.95f)
            return InteractionResult.SUCCESS
        }

        val toolInOffhand = player.offhandItem.`is`(MoreCopycatsRegister.UV_TOOL)
        if (level.isClientSide) {
            return InteractionResult.SUCCESS
        }

        if (toolInOffhand && hand == InteractionHand.MAIN_HAND) {
            if (!player.isCreative && stack.count < 6) {
                return InteractionResult.CONSUME
            }

            blockEntity.setMaterialAllFaces(index, material)
            level.playSound(null, pos, material.soundType.placeSound, SoundSource.BLOCKS, 1f, 1f)
            if (!player.isCreative) {
                stack.shrink(6)
            }

            return InteractionResult.SUCCESS
        }

        blockEntity.setMaterialFace(index, face, material)
        level.playSound(null, pos, material.soundType.placeSound, SoundSource.BLOCKS, 1f, 1f)
        if (!player.isCreative) {
            stack.shrink(1)
        }

        return InteractionResult.SUCCESS
    }

    override fun onWrenched(state: BlockState, context: UseOnContext): InteractionResult {
        val level = context.level
        val pos = context.clickedPos
        val blockEntity = getPartBlockEntity(level, pos) ?: return InteractionResult.PASS
        val player = context.player ?: return InteractionResult.PASS

        if (player.isShiftKeyDown) {
            if (level.isClientSide) {
                return InteractionResult.SUCCESS
            }

            for (index in 0 until maxParts) {
                if (blockEntity.isPartEmpty(index).not()) {
                    player.inventory.placeItemBackInInventory(ItemStack(this.asItem()))
                }
            }

            level.playSound(null, pos, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 1f, 1f)
            level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, getId(state))
            level.removeBlock(pos, false)

            return InteractionResult.SUCCESS
        }

        val index = getTargetIndex(context.clickLocation, pos, context.clickedFace, addingPart = false, blockEntity)
            ?: return InteractionResult.PASS

        if (blockEntity.isPartEmpty(index)) {
            return super.onWrenched(state, context)
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS
        }

        val face = context.clickedFace
        if (blockEntity.isFaceHasMaterial(index, face)) {
            val removedState = blockEntity.getFaceState(index, face) ?: DEFAULT_MATERIAL
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 1f, 1f)
            blockEntity.clearMaterialFace(index, face)
            level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, getId(removedState))

            return InteractionResult.SUCCESS
        }

        player.inventory.placeItemBackInInventory(ItemStack(this))
        level.playSound(null, pos, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 0.75f, 1.2f)
        blockEntity.removePart(index)
        level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, getId(DEFAULT_MATERIAL))

        if (blockEntity.isEmpty) {
            level.setBlock(pos, DEFAULT_MATERIAL, 3)
        }

        return InteractionResult.SUCCESS
    }

    override fun getShape(
        state: BlockState,
        level: BlockGetter,
        pos: BlockPos,
        context: CollisionContext
    ): VoxelShape =
        getPartBlockEntity(level, pos)?.combinedShape ?: Shapes.empty()

    override fun getCollisionShape(
        state: BlockState,
        level: BlockGetter,
        pos: BlockPos,
        context: CollisionContext
    ): VoxelShape =
        getPartBlockEntity(level, pos)?.combinedShape ?: Shapes.empty()

    override fun getLuminance(world: BlockGetter, pos: BlockPos): Int =
        getPartBlockEntity(world, pos)?.getMaxLightEmission() ?: super.getLuminance(world, pos)

    abstract override fun getBlockEntityType(): BlockEntityType<out CopycatBlockEntity>?
    protected abstract fun getIndexFromRelative(relX: Double, relY: Double, relZ: Double): Int

    protected fun resolveTargetIndex(
        hit: Vec3,
        pos: BlockPos,
        face: Direction,
        addingPart: Boolean,
        partStep: Double,
        isPartEmpty: (Int) -> Boolean
    ): Int? {
        val rel = hit.subtract(Vec3.atLowerCornerOf(pos))
        val hitEpsilon = 0.0001

        val nudgedX = rel.x - face.stepX * hitEpsilon
        val nudgedY = rel.y - face.stepY * hitEpsilon
        val nudgedZ = rel.z - face.stepZ * hitEpsilon

        val insideX = nudgedX.coerceIn(hitEpsilon, 1.0 - hitEpsilon)
        val insideY = nudgedY.coerceIn(hitEpsilon, 1.0 - hitEpsilon)
        val insideZ = nudgedZ.coerceIn(hitEpsilon, 1.0 - hitEpsilon)
        val hitIndex = getIndexFromRelative(insideX, insideY, insideZ)

        if (!addingPart) {
            return hitIndex
        }
        if (!isPartEmpty(hitIndex)) {
            val neighborX = insideX + face.stepX * partStep
            val neighborY = insideY + face.stepY * partStep
            val neighborZ = insideZ + face.stepZ * partStep
            if (neighborX in 0.0..1.0 && neighborY in 0.0..1.0 && neighborZ in 0.0..1.0) {
                val neighborIndex = getIndexFromRelative(neighborX, neighborY, neighborZ)
                if (isPartEmpty(neighborIndex)) {
                    return neighborIndex
                }
            }

            return null
        }

        return hitIndex
    }
}
