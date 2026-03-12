package ru.benos_codex.more_copycats.block

import net.minecraft.world.level.block.state.BlockBehaviour.Properties

import com.zurrtum.create.AllItemTags
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.tags.FluidTags
import net.minecraft.util.RandomSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.ScheduledTickAccess
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.FenceBlock
import net.minecraft.world.level.block.FenceGateBlock
import net.minecraft.world.level.block.LevelEvent
import net.minecraft.world.level.block.WallBlock
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.level.pathfinder.PathComputationType
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import ru.benos_codex.more_copycats.MoreCopycatsRegister
import ru.benos_codex.more_copycats.block.entity.CopycatFenceWallBlockEntity
import ru.benos_codex.more_copycats.datapack.CopycatDatapackManager

class CopycatFenceBlock(props: Properties) : CopycatSimpleWaterloggedBlock(props) {
    companion object {
        val NORTH: BooleanProperty = BlockStateProperties.NORTH
        val EAST: BooleanProperty = BlockStateProperties.EAST
        val SOUTH: BooleanProperty = BlockStateProperties.SOUTH
        val WEST: BooleanProperty = BlockStateProperties.WEST

        private val POST_SHAPE: VoxelShape = box(6.0, 0.0, 6.0, 10.0, 16.0, 10.0)
        private val TOP_ARM_SHAPES: Map<Direction, VoxelShape> = Shapes.rotateHorizontal(box(7.0, 12.0, 0.0, 9.0, 15.0, 8.0))
        private val BOTTOM_ARM_SHAPES: Map<Direction, VoxelShape> = Shapes.rotateHorizontal(box(7.0, 7.0, 0.0, 9.0, 10.0, 8.0))
        private val CENTER_BOX = LocalBox(6.0 / 16.0, 0.0, 6.0 / 16.0, 10.0 / 16.0, 1.0, 10.0 / 16.0)
        private val TOP_ARM_BOXES = mapOf(
            Direction.NORTH to LocalBox(7.0 / 16.0, 12.0 / 16.0, 0.0, 9.0 / 16.0, 15.0 / 16.0, 8.0 / 16.0),
            Direction.EAST to LocalBox(8.0 / 16.0, 12.0 / 16.0, 7.0 / 16.0, 1.0, 15.0 / 16.0, 9.0 / 16.0),
            Direction.SOUTH to LocalBox(7.0 / 16.0, 12.0 / 16.0, 8.0 / 16.0, 9.0 / 16.0, 15.0 / 16.0, 1.0),
            Direction.WEST to LocalBox(0.0, 12.0 / 16.0, 7.0 / 16.0, 8.0 / 16.0, 15.0 / 16.0, 9.0 / 16.0)
        )
        private val BOTTOM_ARM_BOXES = mapOf(
            Direction.NORTH to LocalBox(7.0 / 16.0, 7.0 / 16.0, 0.0, 9.0 / 16.0, 10.0 / 16.0, 8.0 / 16.0),
            Direction.EAST to LocalBox(8.0 / 16.0, 7.0 / 16.0, 7.0 / 16.0, 1.0, 10.0 / 16.0, 9.0 / 16.0),
            Direction.SOUTH to LocalBox(7.0 / 16.0, 7.0 / 16.0, 8.0 / 16.0, 9.0 / 16.0, 10.0 / 16.0, 1.0),
            Direction.WEST to LocalBox(0.0, 7.0 / 16.0, 7.0 / 16.0, 8.0 / 16.0, 10.0 / 16.0, 9.0 / 16.0)
        )
    }

    data class SlotBox(
        val slot: CopycatFenceWallBlockEntity.FenceSlot,
        val aabb: AABB
    )

    private data class LocalBox(
        val minX: Double,
        val minY: Double,
        val minZ: Double,
        val maxX: Double,
        val maxY: Double,
        val maxZ: Double
    ) {
        fun contains(x: Double, y: Double, z: Double): Boolean =
            x in minX..maxX && y in minY..maxY && z in minZ..maxZ

        fun distanceSquaredTo(x: Double, y: Double, z: Double): Double {
            val dx = when {
                x < minX -> minX - x
                x > maxX -> x - maxX
                else -> 0.0
            }
            val dy = when {
                y < minY -> minY - y
                y > maxY -> y - maxY
                else -> 0.0
            }
            val dz = when {
                z < minZ -> minZ - z
                z > maxZ -> z - maxZ
                else -> 0.0
            }
            return dx * dx + dy * dy + dz * dz
        }

        fun toWorldAabb(pos: BlockPos): AABB =
            AABB(
                pos.x + minX,
                pos.y + minY,
                pos.z + minZ,
                pos.x + maxX,
                pos.y + maxY,
                pos.z + maxZ
            )
    }

    init {
        registerDefaultState(
            defaultBlockState()
                .setValue(NORTH, false)
                .setValue(EAST, false)
                .setValue(SOUTH, false)
                .setValue(WEST, false)
                .setValue(WATERLOGGED, false)
        )
    }

    override fun getBlockEntityType(): BlockEntityType<out CopycatFenceWallBlockEntity> = MoreCopycatsRegister.FENCE_WALL_BE

    override fun canConnectTexturesToward(
        reader: net.minecraft.world.level.BlockAndTintGetter,
        fromPos: BlockPos,
        toPos: BlockPos,
        state: BlockState
    ): Boolean = false

    override fun getShape(
        state: BlockState,
        level: BlockGetter,
        pos: BlockPos,
        context: CollisionContext
    ): VoxelShape {
        var shape = POST_SHAPE
        if (state.getValue(NORTH)) {
            shape = Shapes.or(shape, TOP_ARM_SHAPES[Direction.NORTH] ?: Shapes.empty())
            shape = Shapes.or(shape, BOTTOM_ARM_SHAPES[Direction.NORTH] ?: Shapes.empty())
        }
        if (state.getValue(EAST)) {
            shape = Shapes.or(shape, TOP_ARM_SHAPES[Direction.EAST] ?: Shapes.empty())
            shape = Shapes.or(shape, BOTTOM_ARM_SHAPES[Direction.EAST] ?: Shapes.empty())
        }
        if (state.getValue(SOUTH)) {
            shape = Shapes.or(shape, TOP_ARM_SHAPES[Direction.SOUTH] ?: Shapes.empty())
            shape = Shapes.or(shape, BOTTOM_ARM_SHAPES[Direction.SOUTH] ?: Shapes.empty())
        }
        if (state.getValue(WEST)) {
            shape = Shapes.or(shape, TOP_ARM_SHAPES[Direction.WEST] ?: Shapes.empty())
            shape = Shapes.or(shape, BOTTOM_ARM_SHAPES[Direction.WEST] ?: Shapes.empty())
        }
        return shape
    }

    override fun getCollisionShape(
        state: BlockState,
        level: BlockGetter,
        pos: BlockPos,
        context: CollisionContext
    ): VoxelShape {
        return referenceFenceState(state).getCollisionShape(level, pos, context)
    }

    override fun getLuminance(world: BlockGetter, pos: BlockPos): Int =
        (world.getBlockEntity(pos) as? CopycatFenceWallBlockEntity)?.getMaxLightEmission() ?: super.getLuminance(world, pos)

    override fun getStateForPlacement(ctx: BlockPlaceContext): BlockState? {
        val level = ctx.level
        val pos = ctx.clickedPos
        val northPos = pos.north()
        val eastPos = pos.east()
        val southPos = pos.south()
        val westPos = pos.west()
        val fluid = level.getFluidState(pos)

        val placed = defaultBlockState()
            .setValue(NORTH, connectsTo(level.getBlockState(northPos), level, northPos, Direction.NORTH))
            .setValue(EAST, connectsTo(level.getBlockState(eastPos), level, eastPos, Direction.EAST))
            .setValue(SOUTH, connectsTo(level.getBlockState(southPos), level, southPos, Direction.SOUTH))
            .setValue(WEST, connectsTo(level.getBlockState(westPos), level, westPos, Direction.WEST))
            .setValue(WATERLOGGED, fluid.type == Fluids.WATER)
        return if (CopycatDatapackManager.isBlockEnabled(placed)) placed else null
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
        if (state.getValue(WATERLOGGED)) {
            scheduledTickAccess.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level))
        }
        if (direction.axis.isHorizontal) {
            return state.setValue(connectionProperty(direction), connectsTo(neighborState, level, neighborPos, direction))
        }
        return super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random)
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

        val blockEntity = level.getBlockEntity(pos) as? CopycatFenceWallBlockEntity
            ?: return super.useItemOn(stack, state, level, pos, player, hand, hitResult)
        val materialIn = getAcceptedBlockState(level, pos, stack, hitResult.direction)
            ?: return super.useItemOn(stack, state, level, pos, player, hand, hitResult)
        val material = prepareMaterial(level, pos, state, player, hand, hitResult, materialIn)
            ?: return InteractionResult.TRY_WITH_EMPTY_HAND
        val slot = slotFromHit(state, hitResult)

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
        val blockEntity = level.getBlockEntity(pos) as? CopycatFenceWallBlockEntity ?: return InteractionResult.PASS
        val player = context.player ?: return InteractionResult.PASS
        val slot = slotFromContext(state, context)

        if (!blockEntity.hasCustomMaterial(slot)) {
            return InteractionResult.PASS
        }
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

    override fun setPlacedBy(level: Level, pos: BlockPos, state: BlockState, placer: LivingEntity?, stack: ItemStack) {
        if (!CopycatDatapackManager.isBlockEnabled(state)) return
        if (placer == null) return
        val offhand = placer.getItemInHand(InteractionHand.OFF_HAND)
        val applied = getAcceptedBlockState(level, pos, offhand, Direction.orderedByNearest(placer)[0]) ?: return
        val blockEntity = level.getBlockEntity(pos) as? CopycatFenceWallBlockEntity ?: return
        if (blockEntity.hasCustomMaterial(CopycatFenceWallBlockEntity.FenceSlot.POST)) return

        blockEntity.setSlotMaterial(CopycatFenceWallBlockEntity.FenceSlot.POST, applied, offhand)
        if (placer is Player && placer.isCreative) return
        offhand.shrink(1)
        if (offhand.isEmpty) {
            placer.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY)
        }
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        super.createBlockStateDefinition(builder)
        builder.add(NORTH, EAST, SOUTH, WEST)
    }

    override fun getFluidState(state: BlockState): FluidState {
        return if (state.getValue(WATERLOGGED)) Fluids.WATER.getSource(false) else super.getFluidState(state)
    }

    override fun isPathfindable(state: BlockState, type: PathComputationType): Boolean {
        return when (type) {
            PathComputationType.WATER -> state.fluidState.`is`(FluidTags.WATER)
            else -> false
        }
    }

    private fun connectsTo(neighborState: BlockState, level: BlockGetter, neighborPos: BlockPos, direction: Direction): Boolean {
        val block = neighborState.block
        if (block is CopycatFenceBlock) return true
        if (block is FenceBlock) return false
        if (block is CopycatWallBlock || block is WallBlock) return false
        if (block is FenceGateBlock && FenceGateBlock.connectsToDirection(neighborState, direction)) return true
        return neighborState.isFaceSturdy(level, neighborPos, direction.opposite)
    }

    private fun connectionProperty(direction: Direction): BooleanProperty {
        return when (direction) {
            Direction.NORTH -> NORTH
            Direction.EAST -> EAST
            Direction.SOUTH -> SOUTH
            Direction.WEST -> WEST
            else -> NORTH
        }
    }

    fun slotFromHit(state: BlockState, hit: BlockHitResult): CopycatFenceWallBlockEntity.FenceSlot =
        slotFromLocal(
            state,
            hit.location.x - hit.blockPos.x,
            hit.location.y - hit.blockPos.y,
            hit.location.z - hit.blockPos.z,
            hit.direction
        )

    fun slotBoxes(state: BlockState, pos: BlockPos): List<SlotBox> =
        slotBoxesLocal(state).map { (slot, box) -> SlotBox(slot, box.toWorldAabb(pos)) }

    private fun slotFromContext(state: BlockState, context: UseOnContext): CopycatFenceWallBlockEntity.FenceSlot =
        slotFromLocal(
            state,
            context.clickLocation.x - context.clickedPos.x,
            context.clickLocation.y - context.clickedPos.y,
            context.clickLocation.z - context.clickedPos.z,
            context.clickedFace
        )

    private fun slotFromLocal(
        state: BlockState,
        relX: Double,
        relY: Double,
        relZ: Double,
        face: Direction
    ): CopycatFenceWallBlockEntity.FenceSlot {
        val eps = 1.0e-4
        val x = (relX - face.stepX * eps).coerceIn(eps, 1.0 - eps)
        val y = (relY - face.stepY * eps).coerceIn(eps, 1.0 - eps)
        val z = (relZ - face.stepZ * eps).coerceIn(eps, 1.0 - eps)
        val boxes = slotBoxesLocal(state)

        val containing = boxes.filter { (_, box) -> box.contains(x, y, z) }
        if (containing.isNotEmpty()) {
            return containing.minWith(
                compareBy<Pair<CopycatFenceWallBlockEntity.FenceSlot, LocalBox>> { slotPriority(it.first) }
                    .thenBy { it.second.distanceSquaredTo(x, y, z) }
            ).first
        }

        return boxes.minByOrNull { (_, box) -> box.distanceSquaredTo(x, y, z) }?.first
            ?: CopycatFenceWallBlockEntity.FenceSlot.POST
    }

    private fun slotBoxesLocal(state: BlockState): List<Pair<CopycatFenceWallBlockEntity.FenceSlot, LocalBox>> {
        val boxes = mutableListOf(
            CopycatFenceWallBlockEntity.FenceSlot.POST to CENTER_BOX
        )
        for (direction in Direction.Plane.HORIZONTAL) {
            if (!state.getValue(connectionProperty(direction))) continue
            boxes += topSlot(direction) to (TOP_ARM_BOXES[direction] ?: continue)
            boxes += bottomSlot(direction) to (BOTTOM_ARM_BOXES[direction] ?: continue)
        }
        return boxes
    }

    private fun slotPriority(slot: CopycatFenceWallBlockEntity.FenceSlot): Int =
        when (slot) {
            CopycatFenceWallBlockEntity.FenceSlot.POST -> 1
            else -> 0
        }

    private fun topSlot(direction: Direction): CopycatFenceWallBlockEntity.FenceSlot =
        when (direction) {
            Direction.NORTH -> CopycatFenceWallBlockEntity.FenceSlot.NORTH_TOP
            Direction.EAST -> CopycatFenceWallBlockEntity.FenceSlot.EAST_TOP
            Direction.SOUTH -> CopycatFenceWallBlockEntity.FenceSlot.SOUTH_TOP
            Direction.WEST -> CopycatFenceWallBlockEntity.FenceSlot.WEST_TOP
            else -> CopycatFenceWallBlockEntity.FenceSlot.POST
        }

    private fun bottomSlot(direction: Direction): CopycatFenceWallBlockEntity.FenceSlot =
        when (direction) {
            Direction.NORTH -> CopycatFenceWallBlockEntity.FenceSlot.NORTH_BOTTOM
            Direction.EAST -> CopycatFenceWallBlockEntity.FenceSlot.EAST_BOTTOM
            Direction.SOUTH -> CopycatFenceWallBlockEntity.FenceSlot.SOUTH_BOTTOM
            Direction.WEST -> CopycatFenceWallBlockEntity.FenceSlot.WEST_BOTTOM
            else -> CopycatFenceWallBlockEntity.FenceSlot.POST
        }

    private fun referenceFenceState(state: BlockState): BlockState =
        Blocks.OAK_FENCE.defaultBlockState()
            .setValue(FenceBlock.NORTH, state.getValue(NORTH))
            .setValue(FenceBlock.EAST, state.getValue(EAST))
            .setValue(FenceBlock.SOUTH, state.getValue(SOUTH))
            .setValue(FenceBlock.WEST, state.getValue(WEST))
}
