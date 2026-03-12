package ru.benos_codex.more_copycats.block

import com.zurrtum.create.AllItemTags
import com.zurrtum.create.catnip.placement.PlacementHelpers
import com.zurrtum.create.catnip.placement.PlacementOffset
import com.zurrtum.create.foundation.placement.PoleHelper
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.BlockItem
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
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.level.block.state.properties.EnumProperty
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import ru.benos_codex.more_copycats.MoreCopycatsRegister
import ru.benos_codex.more_copycats.block.entity.CopycatVerticalStepBlockEntity
import ru.benos_codex.more_copycats.datapack.CopycatDatapackManager
import ru.benos_codex.more_copycats.item.block.CopycatStepPlacementItem
import java.util.function.Predicate

class CopycatStepBlock(props: Properties) : CopycatSimpleWaterloggedBlock(props) {
    companion object {
        private const val PLACEMENT_EDGE_THRESHOLD = 3.0 / 16.0
        val AXIS: EnumProperty<Direction.Axis> = BlockStateProperties.AXIS
        val P0: BooleanProperty = BooleanProperty.create("p0") // north
        val P1: BooleanProperty = BooleanProperty.create("p1") // west
        val P2: BooleanProperty = BooleanProperty.create("p2") // east
        val P3: BooleanProperty = BooleanProperty.create("p3") // south

        private val SHAPES_X = mapOf(
            0 to box(0.0, 0.0, 0.0, 16.0, 8.0, 8.0),    // north bottom
            1 to box(0.0, 0.0, 8.0, 16.0, 8.0, 16.0),   // south bottom
            2 to box(0.0, 8.0, 0.0, 16.0, 16.0, 8.0),   // north top
            3 to box(0.0, 8.0, 8.0, 16.0, 16.0, 16.0)   // south top
        )
        private val SHAPES_Z = mapOf(
            0 to box(0.0, 0.0, 0.0, 8.0, 8.0, 16.0),    // west bottom
            1 to box(8.0, 0.0, 0.0, 16.0, 8.0, 16.0),   // east bottom
            2 to box(0.0, 8.0, 0.0, 8.0, 16.0, 16.0),   // west top
            3 to box(8.0, 8.0, 0.0, 16.0, 16.0, 16.0)   // east top
        )
        private val SHAPES_Y = mapOf(
            0 to box(0.0, 0.0, 0.0, 8.0, 16.0, 8.0),    // north-west
            1 to box(8.0, 0.0, 0.0, 16.0, 16.0, 8.0),   // north-east
            2 to box(0.0, 0.0, 8.0, 8.0, 16.0, 16.0),   // south-west
            3 to box(8.0, 0.0, 8.0, 16.0, 16.0, 16.0)   // south-east
        )

        private val placementHelperId = PlacementHelpers.register(PlacementHelper())

        private fun isStepPlacementStack(stack: ItemStack): Boolean {
            val item = stack.item
            if (item is CopycatStepPlacementItem) return true
            if (item !is BlockItem) return false

            val id = BuiltInRegistries.ITEM.getKey(item)
            return id.namespace == "create" && id.path == "copycat_step"
        }

        private fun isEdgeClick(hit: Vec3, pos: BlockPos, face: Direction): Boolean {
            val relX = hit.x - pos.x
            val relY = hit.y - pos.y
            val relZ = hit.z - pos.z
            val (a, b) = when (face.axis) {
                Direction.Axis.Y -> relX to relZ
                Direction.Axis.X -> relZ to relY
                Direction.Axis.Z -> relX to relY
            }
            return a < PLACEMENT_EDGE_THRESHOLD ||
                a > 1.0 - PLACEMENT_EDGE_THRESHOLD ||
                b < PLACEMENT_EDGE_THRESHOLD ||
                b > 1.0 - PLACEMENT_EDGE_THRESHOLD
        }

        private class PlacementHelper : PoleHelper<Direction.Axis>(
            { state -> state.block is CopycatStepBlock },
            { state -> state.getValue(AXIS) },
            AXIS
        ) {
            override fun getItemPredicate(): Predicate<ItemStack> =
                Predicate { stack -> isStepPlacementStack(stack) }

            override fun getOffset(
                player: Player,
                world: Level,
                state: BlockState,
                pos: BlockPos,
                ray: BlockHitResult
            ): PlacementOffset {
                if (!isEdgeClick(ray.location, pos, ray.direction))
                    return PlacementOffset.fail()

                val offset = super.getOffset(player, world, state, pos, ray)
                if (offset.isSuccessful) {
                    val transform = offset.transform.andThen { placed ->
                        placed
                            .setValue(P0, state.getValue(P0))
                            .setValue(P1, state.getValue(P1))
                            .setValue(P2, state.getValue(P2))
                            .setValue(P3, state.getValue(P3))
                    }
                    offset.withTransform(transform)
                }
                return offset
            }
        }
    }

    private enum class Part(val slot: CopycatVerticalStepBlockEntity.Slot, val id: Int, val direction: Direction) {
        P0(CopycatVerticalStepBlockEntity.Slot.P0, 0, Direction.NORTH),
        P1(CopycatVerticalStepBlockEntity.Slot.P1, 1, Direction.WEST),
        P2(CopycatVerticalStepBlockEntity.Slot.P2, 2, Direction.EAST),
        P3(CopycatVerticalStepBlockEntity.Slot.P3, 3, Direction.SOUTH)
    }

    init {
        registerDefaultState(
            defaultBlockState()
                .setValue(AXIS, Direction.Axis.X)
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
        builder.add(AXIS, P0, P1, P2, P3)
    }

    override fun canBeReplaced(state: BlockState, ctx: BlockPlaceContext): Boolean {
        if (ctx.player?.isShiftKeyDown == true) return super.canBeReplaced(state, ctx)
        if (state.`is`(this) && isStepPlacementStack(ctx.itemInHand)) {
            val currentAxis = state.getValue(AXIS)
            val desiredAxis = targetAxis(ctx)
            if (desiredAxis != currentAxis) return false
            return getTargetPart(ctx.clickLocation, ctx.clickedPos, currentAxis, state, true) != null
        }
        return super.canBeReplaced(state, ctx)
    }

    override fun getStateForPlacement(ctx: BlockPlaceContext): BlockState? {
        val pos = ctx.clickedPos
        val level = ctx.level
        val current = level.getBlockState(pos)

        if (current.`is`(this)) {
            val currentAxis = current.getValue(AXIS)
            val desiredAxis = targetAxis(ctx)
            if (desiredAxis != currentAxis) return null
            val part = getTargetPart(ctx.clickLocation, pos, currentAxis, current, true) ?: return null
            val placed = withPart(current, part.id, true)
            return if (CopycatDatapackManager.isBlockEnabled(placed)) placed else null
        }

        val targetAxis = targetAxis(ctx)
        val part = getTargetPart(ctx.clickLocation, pos, targetAxis, clearParts(defaultBlockState()), false) ?: Part.P3
        val fluid = level.getFluidState(pos)
        val placed = withPart(clearParts(defaultBlockState()), part.id, true)
            .setValue(AXIS, targetAxis)
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
        if (isStepPlacementStack(stack)) {
            val placeContext = BlockPlaceContext(UseOnContext(player, hand, hitResult))
            val desiredAxis = targetAxis(placeContext)
            val edgeClick = isEdgeClick(hitResult.location, pos, hitResult.direction)

            if (!player.isShiftKeyDown && player.mayBuild() && edgeClick && desiredAxis == state.getValue(AXIS)) {
                val helper = PlacementHelpers.get(placementHelperId)
                val blockItem = stack.item as? BlockItem
                if (blockItem != null && helper.matchesItem(stack)) {
                    val result = helper.getOffset(player, level, state, pos, hitResult)
                        .placeInWorld(level, blockItem, player, hand)
                    if (result.consumesAction())
                        return result
                }
            }

            return InteractionResult.PASS
        }

        val blockEntity = level.getBlockEntity(pos) as? CopycatVerticalStepBlockEntity
            ?: return super.useItemOn(stack, state, level, pos, player, hand, hitResult)
        val part = partAt(hitResult, pos, state)
        if (!hasPart(state, part.id)) return InteractionResult.PASS

        val materialIn = getAcceptedBlockState(level, pos, stack, hitResult.direction)
            ?: return super.useItemOn(stack, state, level, pos, player, hand, hitResult)
        val material = prepareMaterial(level, pos, state, player, hand, hitResult, materialIn)
            ?: return InteractionResult.TRY_WITH_EMPTY_HAND

        val slot = part.slot
        val current = blockEntity.getSlotMaterial(slot)
        if (current.`is`(material.block) || blockEntity.hasCustomMaterial(slot)) {
            if (!blockEntity.cycleSlotMaterial(slot)) return InteractionResult.TRY_WITH_EMPTY_HAND
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.75f, 0.95f)
            return InteractionResult.SUCCESS
        }

        if (blockEntity.hasCustomMaterial(slot)) return InteractionResult.TRY_WITH_EMPTY_HAND
        if (level.isClientSide) return InteractionResult.SUCCESS

        blockEntity.setSlotMaterial(slot, material, stack)
        level.playSound(null, pos, material.soundType.placeSound, SoundSource.BLOCKS, 1.0f, 0.75f)
        if (!player.isCreative) {
            stack.shrink(1)
            if (stack.isEmpty) player.setItemInHand(hand, ItemStack.EMPTY)
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
                repeat(count) { player.inventory.placeItemBackInInventory(ItemStack(this)) }
            }
            level.playSound(null, pos, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 1f, 1f)
            level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, getId(state))
            removeOrRestoreFluid(level, pos, state)
            return InteractionResult.SUCCESS
        }

        val part = partAt(context, state)
        if (!hasPart(state, part.id)) return InteractionResult.PASS
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

        if (!player.isCreative) player.inventory.placeItemBackInInventory(ItemStack(this))
        blockEntity.clearSlotMaterial(slot)
        val newState = withPart(state, part.id, false)

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
        val shapes = when (state.getValue(AXIS)) {
            Direction.Axis.X -> SHAPES_X
            Direction.Axis.Z -> SHAPES_Z
            Direction.Axis.Y -> SHAPES_Y
        }
        for (partId in 0..3) {
            if (hasPart(state, partId)) shape = Shapes.or(shape, shapes.getValue(partId))
        }
        return if (shape.isEmpty) Shapes.empty() else shape
    }

    override fun getCollisionShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape =
        getShape(state, level, pos, context)

    override fun getLuminance(world: BlockGetter, pos: BlockPos): Int =
        (world.getBlockEntity(pos) as? CopycatVerticalStepBlockEntity)?.getMaxLightEmission() ?: super.getLuminance(world, pos)

    private fun getTargetPart(
        hit: Vec3,
        pos: BlockPos,
        axis: Direction.Axis,
        state: BlockState,
        addingPart: Boolean
    ): Part? {
        val rel = hit.subtract(Vec3.atLowerCornerOf(pos))
        val hitPart = partByHit(rel.x, rel.y, rel.z, axis)

        if (!addingPart) return hitPart
        if (!hasPart(state, hitPart.id)) return hitPart

        return Part.entries.firstOrNull { !hasPart(state, it.id) }
    }

    private fun partByHit(x: Double, y: Double, z: Double, axis: Direction.Axis): Part {
        if (axis == Direction.Axis.Y) {
            val east = x >= 0.5
            val south = z >= 0.5
            return when {
                !east && !south -> Part.P0
                east && !south -> Part.P1
                !east && south -> Part.P2
                else -> Part.P3
            }
        }

        val top = y >= 0.5
        val sidePositive = if (axis == Direction.Axis.X) z >= 0.5 else x >= 0.5
        return if (top) {
            if (sidePositive) Part.P3 else Part.P2
        } else {
            if (sidePositive) Part.P1 else Part.P0
        }
    }

    private fun hasPart(state: BlockState, id: Int): Boolean = when (id) {
        0 -> state.getValue(P0)
        1 -> state.getValue(P1)
        2 -> state.getValue(P2)
        else -> state.getValue(P3)
    }

    private fun withPart(state: BlockState, id: Int, value: Boolean): BlockState = when (id) {
        0 -> state.setValue(P0, value)
        1 -> state.setValue(P1, value)
        2 -> state.setValue(P2, value)
        else -> state.setValue(P3, value)
    }

    private fun clearParts(state: BlockState): BlockState =
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

    private fun partAt(hit: BlockHitResult, pos: BlockPos, state: BlockState): Part =
        partAt(
            hit.location.x - pos.x,
            hit.location.y - pos.y,
            hit.location.z - pos.z,
            hit.direction,
            state
        )

    private fun partAt(context: UseOnContext, state: BlockState): Part =
        partAt(
            context.clickLocation.x - context.clickedPos.x,
            context.clickLocation.y - context.clickedPos.y,
            context.clickLocation.z - context.clickedPos.z,
            context.clickedFace,
            state
        )

    private fun partAt(
        relX: Double,
        relY: Double,
        relZ: Double,
        face: Direction,
        state: BlockState
    ): Part {
        val axis = state.getValue(AXIS)
        val eps = 1.0e-4
        val x = (relX - face.stepX * eps).coerceIn(eps, 1.0 - eps)
        val y = (relY - face.stepY * eps).coerceIn(eps, 1.0 - eps)
        val z = (relZ - face.stepZ * eps).coerceIn(eps, 1.0 - eps)

        val direct = partByHit(x, y, z, axis)
        if (hasPart(state, direct.id)) return direct

        return nearestExistingPart(x, y, z, axis, state) ?: direct
    }

    private fun nearestExistingPart(
        x: Double,
        y: Double,
        z: Double,
        axis: Direction.Axis,
        state: BlockState
    ): Part? {
        var nearest: Part? = null
        var best = Double.MAX_VALUE

        for (part in Part.entries) {
            if (!hasPart(state, part.id)) continue
            val center = partCenter(part, axis)
            val dx = x - center.x
            val dy = y - center.y
            val dz = z - center.z
            val dist2 = dx * dx + dy * dy + dz * dz
            if (dist2 < best) {
                best = dist2
                nearest = part
            }
        }

        return nearest
    }

    private fun partCenter(part: Part, axis: Direction.Axis): Vec3 = when (axis) {
        Direction.Axis.X -> when (part) {
            Part.P0 -> Vec3(0.5, 0.25, 0.25)
            Part.P1 -> Vec3(0.5, 0.25, 0.75)
            Part.P2 -> Vec3(0.5, 0.75, 0.25)
            Part.P3 -> Vec3(0.5, 0.75, 0.75)
        }
        Direction.Axis.Z -> when (part) {
            Part.P0 -> Vec3(0.25, 0.25, 0.5)
            Part.P1 -> Vec3(0.75, 0.25, 0.5)
            Part.P2 -> Vec3(0.25, 0.75, 0.5)
            Part.P3 -> Vec3(0.75, 0.75, 0.5)
        }
        Direction.Axis.Y -> when (part) {
            Part.P0 -> Vec3(0.25, 0.5, 0.25)
            Part.P1 -> Vec3(0.75, 0.5, 0.25)
            Part.P2 -> Vec3(0.25, 0.5, 0.75)
            Part.P3 -> Vec3(0.75, 0.5, 0.75)
        }
    }

    private fun targetAxis(ctx: BlockPlaceContext): Direction.Axis {
        val clickedState = ctx.level.getBlockState(ctx.clickedPos)
        if (clickedState.`is`(this))
            return clickedState.getValue(AXIS)

        val item = ctx.itemInHand.item
        if (item is CopycatStepPlacementItem) {
            return item.axisForPlacement(ctx)
        }

        val faceAxis = ctx.clickedFace.axis
        if (faceAxis.isHorizontal) return faceAxis
        val playerAxis = ctx.horizontalDirection.axis
        return if (playerAxis.isHorizontal) playerAxis else Direction.Axis.X
    }

}
