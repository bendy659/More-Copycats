package ru.benos_codex.more_copycats.block

import com.zurrtum.create.AllItemTags
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.LevelEvent
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockBehaviour.Properties
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import ru.benos_codex.more_copycats.MoreCopycatsRegister
import ru.benos_codex.more_copycats.block.entity.CopycatVerticalStepBlockEntity
import ru.benos_codex.more_copycats.datapack.CopycatDatapackManager

class CopycatVerticalStepBlock(props: Properties) : CopycatSimpleWaterloggedBlock(props) {
    companion object {
        val P0: BooleanProperty = BooleanProperty.create("p0") // NW
        val P1: BooleanProperty = BooleanProperty.create("p1") // NE
        val P2: BooleanProperty = BooleanProperty.create("p2") // SW
        val P3: BooleanProperty = BooleanProperty.create("p3") // SE

        private val SHAPES = mapOf(
            0 to box(0.0, 0.0, 0.0, 8.0, 16.0, 8.0),
            1 to box(8.0, 0.0, 0.0, 16.0, 16.0, 8.0),
            2 to box(0.0, 0.0, 8.0, 8.0, 16.0, 16.0),
            3 to box(8.0, 0.0, 8.0, 16.0, 16.0, 16.0)
        )
    }

    private enum class Part(val slot: CopycatVerticalStepBlockEntity.Slot, val corner: Int) {
        P0(CopycatVerticalStepBlockEntity.Slot.P0, 0),
        P1(CopycatVerticalStepBlockEntity.Slot.P1, 1),
        P2(CopycatVerticalStepBlockEntity.Slot.P2, 2),
        P3(CopycatVerticalStepBlockEntity.Slot.P3, 3)
    }

    init {
        registerDefaultState(
            defaultBlockState()
                .setValue(P0, false)
                .setValue(P1, false)
                .setValue(P2, false)
                .setValue(P3, true)
                .setValue(WATERLOGGED, false)
        )
    }

    override fun getBlockEntityType(): BlockEntityType<out CopycatVerticalStepBlockEntity> =
        MoreCopycatsRegister.STEP_BE

    override fun canConnectTexturesToward(
        reader: net.minecraft.world.level.BlockAndTintGetter,
        fromPos: BlockPos,
        toPos: BlockPos,
        state: BlockState
    ): Boolean = true

    override fun useShapeForLightOcclusion(state: BlockState): Boolean = true

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        super.createBlockStateDefinition(builder)
        builder.add(P0, P1, P2, P3)
    }

    override fun canBeReplaced(state: BlockState, ctx: BlockPlaceContext): Boolean {
        if (ctx.player?.isShiftKeyDown == true) return super.canBeReplaced(state, ctx)
        if (ctx.itemInHand.`is`(this.asItem()) && state.`is`(this)) {
            return getTargetCorner(ctx.clickLocation, ctx.clickedPos, ctx.clickedFace, state, true) != null
        }
        return super.canBeReplaced(state, ctx)
    }

    override fun getStateForPlacement(ctx: BlockPlaceContext): BlockState? {
        val pos = ctx.clickedPos
        val level = ctx.level
        val current = level.getBlockState(pos)

        if (current.`is`(this)) {
            val corner = getTargetCorner(ctx.clickLocation, pos, ctx.clickedFace, current, true) ?: return null
            val placed = withCorner(current, corner, true)
            return if (CopycatDatapackManager.isBlockEnabled(placed)) placed else null
        }

        val corner = getTargetCorner(ctx.clickLocation, pos, ctx.clickedFace, clearCorners(defaultBlockState()), false) ?: 0
        val fluid = level.getFluidState(pos)
        val placed = withCorner(clearCorners(defaultBlockState()), corner, true)
            .setValue(WATERLOGGED, fluid.type == Fluids.WATER)
        return if (CopycatDatapackManager.isBlockEnabled(placed)) placed else null
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

        val blockEntity = level.getBlockEntity(pos) as? CopycatVerticalStepBlockEntity
            ?: return super.useItemOn(stack, state, level, pos, player, hand, hitResult)
        val part = partAt(hitResult, pos)
        if (!hasCorner(state, part.corner)) {
            return InteractionResult.PASS
        }

        val materialIn = getAcceptedBlockState(level, pos, stack, hitResult.direction)
            ?: return super.useItemOn(stack, state, level, pos, player, hand, hitResult)
        val material = prepareMaterial(level, pos, state, player, hand, hitResult, materialIn)
            ?: return InteractionResult.TRY_WITH_EMPTY_HAND

        val slot = part.slot
        val current = blockEntity.getSlotMaterial(slot)
        if (current.`is`(material.block) || blockEntity.hasCustomMaterial(slot)) {
            if (!blockEntity.cycleSlotMaterial(slot)) {
                return InteractionResult.TRY_WITH_EMPTY_HAND
            }
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.75f, 0.95f)
            return InteractionResult.SUCCESS
        }

        if (blockEntity.hasCustomMaterial(slot)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND
        }

        if (level.isClientSide) {
            return InteractionResult.SUCCESS
        }

        blockEntity.setSlotMaterial(slot, material, stack)
        level.playSound(null, pos, material.soundType.placeSound, SoundSource.BLOCKS, 1.0f, 0.75f)
        if (!player.isCreative) {
            stack.shrink(1)
            if (stack.isEmpty) {
                player.setItemInHand(hand, ItemStack.EMPTY)
            }
        }
        return InteractionResult.SUCCESS
    }

    override fun prepareMaterial(
        pLevel: Level,
        pPos: BlockPos,
        pState: BlockState,
        pPlayer: Player,
        pHand: InteractionHand,
        pHit: BlockHitResult,
        material: BlockState
    ): BlockState? = material

    override fun onWrenched(state: BlockState, context: UseOnContext): InteractionResult {
        if (!CopycatDatapackManager.isBlockEnabled(state)) return InteractionResult.PASS
        val level = context.level
        val pos = context.clickedPos
        val blockEntity = level.getBlockEntity(pos) as? CopycatVerticalStepBlockEntity ?: return InteractionResult.PASS
        val player = context.player ?: return InteractionResult.PASS

        if (player.isShiftKeyDown) {
            if (level.isClientSide) return InteractionResult.SUCCESS
            val count = partCount(state)
            if (!player.isCreative) {
                repeat(count) {
                    player.inventory.placeItemBackInInventory(ItemStack(this))
                }
            }
            level.playSound(null, pos, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 1f, 1f)
            level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, getId(state))
            removeOrRestoreFluid(level, pos, state)
            return InteractionResult.SUCCESS
        }

        val part = partAt(context)
        if (!hasCorner(state, part.corner)) return InteractionResult.PASS
        val slot = part.slot

        if (blockEntity.hasCustomMaterial(slot)) {
            if (level.isClientSide) return InteractionResult.SUCCESS

            val removedState = blockEntity.getSlotMaterial(slot)
            val removedStack = blockEntity.getSlotConsumedItem(slot).copy()
            blockEntity.clearSlotMaterial(slot)

            if (!player.isCreative && !removedStack.isEmpty) {
                player.inventory.placeItemBackInInventory(removedStack)
            }

            level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.75f, 0.95f)
            level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, getId(removedState))
            return InteractionResult.SUCCESS
        }

        if (level.isClientSide) return InteractionResult.SUCCESS

        if (!player.isCreative) {
            player.inventory.placeItemBackInInventory(ItemStack(this))
        }
        blockEntity.clearSlotMaterial(slot)
        val newState = withCorner(state, part.corner, false)

        if (partCount(newState) <= 0) {
            removeOrRestoreFluid(level, pos, state)
        } else {
            level.setBlock(pos, newState, 3)
        }

        level.playSound(null, pos, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 0.8f, 1.2f)
        level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, getId(state))
        return InteractionResult.SUCCESS
    }

    override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape {
        var shape = Shapes.empty()
        for (corner in 0..3) {
            if (hasCorner(state, corner)) {
                shape = Shapes.or(shape, SHAPES.getValue(corner))
            }
        }
        return if (shape.isEmpty) Shapes.empty() else shape
    }

    override fun getCollisionShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape =
        getShape(state, level, pos, context)

    override fun getLuminance(world: BlockGetter, pos: BlockPos): Int =
        (world.getBlockEntity(pos) as? CopycatVerticalStepBlockEntity)?.getMaxLightEmission() ?: super.getLuminance(world, pos)

    private fun getTargetCorner(
        hit: Vec3,
        pos: BlockPos,
        face: Direction,
        state: BlockState,
        addingPart: Boolean
    ): Int? {
        val rel = hit.subtract(Vec3.atLowerCornerOf(pos))
        val eps = 0.0001
        val insideX = (rel.x - face.stepX * eps).coerceIn(eps, 1.0 - eps)
        val insideZ = (rel.z - face.stepZ * eps).coerceIn(eps, 1.0 - eps)
        val hitCorner = cornerFromRelative(insideX, insideZ)

        if (!addingPart) return hitCorner
        if (!hasCorner(state, hitCorner)) return hitCorner

        val neighborX = insideX + face.stepX * 0.5
        val neighborZ = insideZ + face.stepZ * 0.5
        if (neighborX in 0.0..1.0 && neighborZ in 0.0..1.0) {
            val neighborCorner = cornerFromRelative(neighborX, neighborZ)
            if (!hasCorner(state, neighborCorner)) return neighborCorner
        }

        var bestCorner: Int? = null
        var bestDist = Double.MAX_VALUE
        for (corner in 0..3) {
            if (hasCorner(state, corner)) continue
            val cx = if (corner and 1 == 1) 0.75 else 0.25
            val cz = if (corner and 2 == 2) 0.75 else 0.25
            val dx = insideX - cx
            val dz = insideZ - cz
            val d2 = dx * dx + dz * dz
            if (d2 < bestDist) {
                bestDist = d2
                bestCorner = corner
            }
        }
        return bestCorner
    }

    private fun hasCorner(state: BlockState, corner: Int): Boolean = when (corner) {
        0 -> state.getValue(P0)
        1 -> state.getValue(P1)
        2 -> state.getValue(P2)
        else -> state.getValue(P3)
    }

    private fun withCorner(state: BlockState, corner: Int, value: Boolean): BlockState = when (corner) {
        0 -> state.setValue(P0, value)
        1 -> state.setValue(P1, value)
        2 -> state.setValue(P2, value)
        else -> state.setValue(P3, value)
    }

    private fun clearCorners(state: BlockState): BlockState =
        state.setValue(P0, false).setValue(P1, false).setValue(P2, false).setValue(P3, false)

    private fun partCount(state: BlockState): Int {
        var c = 0
        if (state.getValue(P0)) c++
        if (state.getValue(P1)) c++
        if (state.getValue(P2)) c++
        if (state.getValue(P3)) c++
        return c
    }

    private fun removeOrRestoreFluid(level: Level, pos: BlockPos, state: BlockState) {
        if (state.getValue(WATERLOGGED)) {
            level.setBlock(pos, Fluids.WATER.defaultFluidState().createLegacyBlock(), 3)
        } else {
            level.removeBlock(pos, false)
        }
    }

    private fun cornerFromRelative(relX: Double, relZ: Double): Int {
        val x = if (relX >= 0.5) 1 else 0
        val z = if (relZ >= 0.5) 1 else 0
        return x + (z shl 1)
    }

    private fun partAt(hit: BlockHitResult, pos: BlockPos): Part {
        val x = hit.location.x - pos.x
        val z = hit.location.z - pos.z
        return partForCorner(cornerFromRelative(x, z))
    }

    private fun partAt(context: UseOnContext): Part {
        val pos = context.clickedPos
        val x = context.clickLocation.x - pos.x
        val z = context.clickLocation.z - pos.z
        return partForCorner(cornerFromRelative(x, z))
    }

    private fun partForCorner(corner: Int): Part = when (corner) {
        0 -> Part.P0
        1 -> Part.P1
        2 -> Part.P2
        else -> Part.P3
    }
}
