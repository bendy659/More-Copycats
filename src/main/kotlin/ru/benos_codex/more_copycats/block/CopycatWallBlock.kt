package ru.benos_codex.more_copycats.block

import net.minecraft.world.level.block.state.BlockBehaviour.Properties

import com.zurrtum.create.AllItemTags
import com.zurrtum.create.content.decoration.copycat.CopycatBlock
import com.zurrtum.create.foundation.block.AppearanceControlBlock
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.tags.BlockTags
import net.minecraft.tags.FluidTags
import net.minecraft.util.RandomSource
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
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.ScheduledTickAccess
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.FenceBlock
import net.minecraft.world.level.block.FenceGateBlock
import net.minecraft.world.level.block.IronBarsBlock
import net.minecraft.world.level.block.LevelEvent
import net.minecraft.world.level.block.WallBlock
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.level.block.state.properties.EnumProperty
import net.minecraft.world.level.block.state.properties.Property
import net.minecraft.world.level.block.state.properties.WallSide
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.level.pathfinder.PathComputationType
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.BooleanOp
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import ru.benos_codex.more_copycats.MoreCopycatsRegister
import ru.benos_codex.more_copycats.block.entity.CopycatFenceWallBlockEntity
import ru.benos_codex.more_copycats.datapack.CopycatDatapackManager

class CopycatWallBlock(props: Properties) : CopycatSimpleWaterloggedBlock(props), AppearanceControlBlock {
    companion object {
        val UP: BooleanProperty = BlockStateProperties.UP
        val NORTH_WALL: EnumProperty<WallSide> = BlockStateProperties.NORTH_WALL
        val EAST_WALL: EnumProperty<WallSide> = BlockStateProperties.EAST_WALL
        val SOUTH_WALL: EnumProperty<WallSide> = BlockStateProperties.SOUTH_WALL
        val WEST_WALL: EnumProperty<WallSide> = BlockStateProperties.WEST_WALL

        private val POST_SHAPE: VoxelShape = box(4.0, 0.0, 4.0, 12.0, 16.0, 12.0)
        private val LOW_SHAPES: Map<Direction, VoxelShape> = Shapes.rotateHorizontal(box(5.0, 0.0, 0.0, 11.0, 14.0, 8.0))
        private val TALL_SHAPES: Map<Direction, VoxelShape> = Shapes.rotateHorizontal(box(5.0, 0.0, 0.0, 11.0, 16.0, 8.0))
        private val POST_COLLISION_SHAPE: VoxelShape = box(4.0, 0.0, 4.0, 12.0, 24.0, 12.0)
        private val SIDE_COLLISION_SHAPES: Map<Direction, VoxelShape> = Shapes.rotateHorizontal(box(5.0, 0.0, 0.0, 11.0, 24.0, 8.0))
        private val TEST_SHAPE_POST: VoxelShape = column(2.0, 0.0, 16.0)
        private val TEST_SHAPES_WALL: Map<Direction, VoxelShape> = Shapes.rotateHorizontal(boxZ(2.0, 16.0, 0.0, 9.0))
        private val CENTER_BOX = LocalBox(4.0 / 16.0, 0.0, 4.0 / 16.0, 12.0 / 16.0, 1.0, 12.0 / 16.0)
        private val LOW_SIDE_BOXES = mapOf(
            Direction.NORTH to LocalBox(5.0 / 16.0, 0.0, 0.0, 11.0 / 16.0, 14.0 / 16.0, 8.0 / 16.0),
            Direction.EAST to LocalBox(8.0 / 16.0, 0.0, 5.0 / 16.0, 1.0, 14.0 / 16.0, 11.0 / 16.0),
            Direction.SOUTH to LocalBox(5.0 / 16.0, 0.0, 8.0 / 16.0, 11.0 / 16.0, 14.0 / 16.0, 1.0),
            Direction.WEST to LocalBox(0.0, 0.0, 5.0 / 16.0, 8.0 / 16.0, 14.0 / 16.0, 11.0 / 16.0)
        )
        private val TALL_SIDE_BOXES = mapOf(
            Direction.NORTH to LocalBox(5.0 / 16.0, 0.0, 0.0, 11.0 / 16.0, 1.0, 8.0 / 16.0),
            Direction.EAST to LocalBox(8.0 / 16.0, 0.0, 5.0 / 16.0, 1.0, 1.0, 11.0 / 16.0),
            Direction.SOUTH to LocalBox(5.0 / 16.0, 0.0, 8.0 / 16.0, 11.0 / 16.0, 1.0, 1.0),
            Direction.WEST to LocalBox(0.0, 0.0, 5.0 / 16.0, 8.0 / 16.0, 1.0, 11.0 / 16.0)
        )
    }

    data class SlotBox(
        val slot: CopycatFenceWallBlockEntity.WallSlot,
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
                .setValue(UP, true)
                .setValue(NORTH_WALL, WallSide.NONE)
                .setValue(EAST_WALL, WallSide.NONE)
                .setValue(SOUTH_WALL, WallSide.NONE)
                .setValue(WEST_WALL, WallSide.NONE)
                .setValue(WATERLOGGED, false)
        )
    }

    override fun getBlockEntityType(): BlockEntityType<out CopycatFenceWallBlockEntity> = MoreCopycatsRegister.FENCE_WALL_BE

    override fun canConnectTexturesToward(
        reader: BlockAndTintGetter,
        fromPos: BlockPos,
        toPos: BlockPos,
        state: BlockState
    ): Boolean = true

    override fun getShape(
        state: BlockState,
        level: BlockGetter,
        pos: BlockPos,
        context: CollisionContext
    ): VoxelShape {
        var shape = if (state.getValue(UP)) POST_SHAPE else Shapes.empty()
        shape = appendWallShape(shape, state.getValue(NORTH_WALL), Direction.NORTH)
        shape = appendWallShape(shape, state.getValue(EAST_WALL), Direction.EAST)
        shape = appendWallShape(shape, state.getValue(SOUTH_WALL), Direction.SOUTH)
        shape = appendWallShape(shape, state.getValue(WEST_WALL), Direction.WEST)
        return shape
    }

    override fun getCollisionShape(
        state: BlockState,
        level: BlockGetter,
        pos: BlockPos,
        context: CollisionContext
    ): VoxelShape {
        var shape = if (state.getValue(UP)) POST_COLLISION_SHAPE else Shapes.empty()
        shape = appendCollisionWallShape(shape, state.getValue(NORTH_WALL), Direction.NORTH)
        shape = appendCollisionWallShape(shape, state.getValue(EAST_WALL), Direction.EAST)
        shape = appendCollisionWallShape(shape, state.getValue(SOUTH_WALL), Direction.SOUTH)
        shape = appendCollisionWallShape(shape, state.getValue(WEST_WALL), Direction.WEST)
        return shape
    }

    override fun getBlockSupportShape(state: BlockState, level: BlockGetter, pos: BlockPos): VoxelShape {
        val base = getCollisionShape(state, level, pos, CollisionContext.empty())
        if (state.getValue(UP)) return base

        val hasAnySide = state.getValue(NORTH_WALL) != WallSide.NONE
                || state.getValue(EAST_WALL) != WallSide.NONE
                || state.getValue(SOUTH_WALL) != WallSide.NONE
                || state.getValue(WEST_WALL) != WallSide.NONE
        if (!hasAnySide) return base

        // Allow top-support placement on wall arms; post visibility still follows vanilla wall rules.
        return Shapes.or(base, POST_SHAPE)
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
        val abovePos = pos.above()
        val northState = level.getBlockState(northPos)
        val eastState = level.getBlockState(eastPos)
        val southState = level.getBlockState(southPos)
        val westState = level.getBlockState(westPos)
        val aboveState = level.getBlockState(abovePos)
        val north = connectsTo(northState, northState.isFaceSturdy(level, northPos, Direction.SOUTH), Direction.SOUTH)
        val east = connectsTo(eastState, eastState.isFaceSturdy(level, eastPos, Direction.WEST), Direction.WEST)
        val south = connectsTo(southState, southState.isFaceSturdy(level, southPos, Direction.NORTH), Direction.NORTH)
        val west = connectsTo(westState, westState.isFaceSturdy(level, westPos, Direction.EAST), Direction.EAST)
        val fluid = level.getFluidState(pos)

        val state = defaultBlockState()
            .setValue(WATERLOGGED, fluid.type == Fluids.WATER)
        val placed = updateWallShape(level, state, abovePos, aboveState, north, east, south, west)
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

        if (direction == Direction.DOWN) {
            return super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random)
        }
        if (direction == Direction.UP) {
            return topUpdate(level, state, neighborPos, neighborState)
        }
        return sideUpdate(level, pos, state, neighborPos, neighborState, direction)
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
        val player = context.player ?: return InteractionResult.PASS
        val blockEntity = level.getBlockEntity(pos) as? CopycatFenceWallBlockEntity ?: return InteractionResult.PASS
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
        val slot = if (state.getValue(UP)) {
            CopycatFenceWallBlockEntity.WallSlot.POST
        } else {
            firstConnectedWallSlot(state) ?: CopycatFenceWallBlockEntity.WallSlot.POST
        }
        if (blockEntity.hasCustomMaterial(slot)) return

        blockEntity.setSlotMaterial(slot, applied, offhand)
        if (placer is Player && placer.isCreative) return
        offhand.shrink(1)
        if (offhand.isEmpty) {
            placer.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY)
        }
    }

    override fun getAppearance(
        state: BlockState,
        level: BlockAndTintGetter,
        toPos: BlockPos,
        side: Direction,
        reference: BlockState?,
        fromPos: BlockPos?
    ): BlockState {
        val be = level.getBlockEntity(toPos) as? CopycatFenceWallBlockEntity ?: return state
        val primary = be.getSlotMaterial(CopycatFenceWallBlockEntity.WallSlot.POST)

        if (side.axis.isHorizontal) {
            val wallSide = state.getValue(wallProperty(side))
            if (wallSide != WallSide.NONE) {
                return be.getSlotMaterial(wallSlot(side))
            }
        }

        return primary
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        super.createBlockStateDefinition(builder)
        builder.add(UP, NORTH_WALL, EAST_WALL, SOUTH_WALL, WEST_WALL)
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

    private fun topUpdate(level: LevelReader, state: BlockState, abovePos: BlockPos, aboveState: BlockState): BlockState {
        val north = isConnected(state, NORTH_WALL)
        val east = isConnected(state, EAST_WALL)
        val south = isConnected(state, SOUTH_WALL)
        val west = isConnected(state, WEST_WALL)
        return updateWallShape(level, state, abovePos, aboveState, north, east, south, west)
    }

    private fun sideUpdate(
        level: LevelReader,
        pos: BlockPos,
        state: BlockState,
        neighborPos: BlockPos,
        neighborState: BlockState,
        direction: Direction
    ): BlockState {
        val opposite = direction.opposite
        val connect = connectsTo(neighborState, neighborState.isFaceSturdy(level, neighborPos, opposite), opposite)
        val north = if (direction == Direction.NORTH) connect else isConnected(state, NORTH_WALL)
        val east = if (direction == Direction.EAST) connect else isConnected(state, EAST_WALL)
        val south = if (direction == Direction.SOUTH) connect else isConnected(state, SOUTH_WALL)
        val west = if (direction == Direction.WEST) connect else isConnected(state, WEST_WALL)
        val abovePos = pos.above()
        val aboveState = level.getBlockState(abovePos)
        return updateWallShape(level, state, abovePos, aboveState, north, east, south, west)
    }

    private fun updateWallShape(
        level: LevelReader,
        state: BlockState,
        abovePos: BlockPos,
        aboveState: BlockState,
        north: Boolean,
        east: Boolean,
        south: Boolean,
        west: Boolean
    ): BlockState {
        val aboveDownFace = aboveState.getCollisionShape(level, abovePos).getFaceShape(Direction.DOWN)
        val withSides = updateSides(state, north, east, south, west, aboveDownFace)
        return withSides.setValue(UP, shouldRaisePost(withSides, aboveState, aboveDownFace))
    }

    private fun updateSides(
        state: BlockState,
        north: Boolean,
        east: Boolean,
        south: Boolean,
        west: Boolean,
        aboveDownFace: VoxelShape
    ): BlockState {
        return state
            .setValue(NORTH_WALL, makeWallState(north, aboveDownFace, TEST_SHAPES_WALL[Direction.NORTH] ?: Shapes.empty()))
            .setValue(EAST_WALL, makeWallState(east, aboveDownFace, TEST_SHAPES_WALL[Direction.EAST] ?: Shapes.empty()))
            .setValue(SOUTH_WALL, makeWallState(south, aboveDownFace, TEST_SHAPES_WALL[Direction.SOUTH] ?: Shapes.empty()))
            .setValue(WEST_WALL, makeWallState(west, aboveDownFace, TEST_SHAPES_WALL[Direction.WEST] ?: Shapes.empty()))
    }

    private fun makeWallState(connects: Boolean, aboveDownFace: VoxelShape, testWallShape: VoxelShape): WallSide {
        if (!connects) return WallSide.NONE
        return if (isCovered(aboveDownFace, testWallShape)) WallSide.TALL else WallSide.LOW
    }

    private fun connectsTo(neighborState: BlockState, canAttachToSturdyFace: Boolean, direction: Direction): Boolean {
        val block = neighborState.block
        if (block is CopycatWallBlock) return true
        if (block is WallBlock) return false
        if (block is CopycatFenceBlock || block is FenceBlock) return false
        val gateConnects = block is FenceGateBlock && FenceGateBlock.connectsToDirection(neighborState, direction)
        return (!isExceptionForConnection(neighborState) && canAttachToSturdyFace)
                || block is IronBarsBlock
                || gateConnects
    }

    private fun shouldRaisePost(state: BlockState, aboveState: BlockState, aboveDownFace: VoxelShape): Boolean {
        val blockAbove = aboveState.block
        val aboveWallWithPost = blockAbove is CopycatWallBlock && aboveState.getValue(UP)
        if (aboveWallWithPost) return true

        val north = state.getValue(NORTH_WALL)
        val south = state.getValue(SOUTH_WALL)
        val east = state.getValue(EAST_WALL)
        val west = state.getValue(WEST_WALL)

        val southNone = south == WallSide.NONE
        val westNone = west == WallSide.NONE
        val eastNone = east == WallSide.NONE
        val northNone = north == WallSide.NONE

        val raiseByPattern = (northNone && southNone && westNone && eastNone)
                || (northNone != southNone)
                || (westNone != eastNone)
        if (raiseByPattern) return true

        val tallOppositePair = (north == WallSide.TALL && south == WallSide.TALL)
                || (east == WallSide.TALL && west == WallSide.TALL)
        if (tallOppositePair) return false

        return aboveState.`is`(BlockTags.WALL_POST_OVERRIDE) || isCovered(aboveDownFace, TEST_SHAPE_POST)
    }

    private fun isConnected(state: BlockState, property: Property<WallSide>): Boolean {
        return state.getValue(property) != WallSide.NONE
    }

    private fun isCovered(coverShape: VoxelShape, targetShape: VoxelShape): Boolean {
        return !Shapes.joinIsNotEmpty(targetShape, coverShape, BooleanOp.ONLY_FIRST)
    }

    private fun wallProperty(direction: Direction): EnumProperty<WallSide> {
        return when (direction) {
            Direction.NORTH -> NORTH_WALL
            Direction.EAST -> EAST_WALL
            Direction.SOUTH -> SOUTH_WALL
            Direction.WEST -> WEST_WALL
            else -> NORTH_WALL
        }
    }

    private fun appendWallShape(base: VoxelShape, side: WallSide, direction: Direction): VoxelShape {
        return when (side) {
            WallSide.NONE -> base
            WallSide.LOW -> Shapes.or(base, LOW_SHAPES[direction] ?: Shapes.empty())
            WallSide.TALL -> Shapes.or(base, TALL_SHAPES[direction] ?: Shapes.empty())
        }
    }

    private fun appendCollisionWallShape(base: VoxelShape, side: WallSide, direction: Direction): VoxelShape {
        return when (side) {
            WallSide.NONE -> base
            WallSide.LOW, WallSide.TALL -> Shapes.or(base, SIDE_COLLISION_SHAPES[direction] ?: Shapes.empty())
        }
    }

    fun slotFromHit(state: BlockState, hit: BlockHitResult): CopycatFenceWallBlockEntity.WallSlot =
        slotFromLocal(
            state,
            hit.location.x - hit.blockPos.x,
            hit.location.y - hit.blockPos.y,
            hit.location.z - hit.blockPos.z,
            hit.direction
        )

    fun slotBoxes(state: BlockState, pos: BlockPos): List<SlotBox> =
        slotBoxesLocal(state).map { (slot, box) -> SlotBox(slot, box.toWorldAabb(pos)) }

    private fun slotFromContext(state: BlockState, context: UseOnContext): CopycatFenceWallBlockEntity.WallSlot =
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
    ): CopycatFenceWallBlockEntity.WallSlot {
        val eps = 1.0e-4
        val x = (relX - face.stepX * eps).coerceIn(eps, 1.0 - eps)
        val y = (relY - face.stepY * eps).coerceIn(eps, 1.0 - eps)
        val z = (relZ - face.stepZ * eps).coerceIn(eps, 1.0 - eps)
        val boxes = slotBoxesLocal(state)

        val containing = boxes.filter { (_, box) -> box.contains(x, y, z) }
        if (containing.isNotEmpty()) {
            return containing.minWith(
                compareBy<Pair<CopycatFenceWallBlockEntity.WallSlot, LocalBox>> { slotPriority(it.first) }
                    .thenBy { it.second.distanceSquaredTo(x, y, z) }
            ).first
        }

        return boxes.minByOrNull { (_, box) -> box.distanceSquaredTo(x, y, z) }?.first
            ?: firstConnectedWallSlot(state)
            ?: CopycatFenceWallBlockEntity.WallSlot.POST
    }

    private fun slotBoxesLocal(state: BlockState): List<Pair<CopycatFenceWallBlockEntity.WallSlot, LocalBox>> {
        val boxes = mutableListOf<Pair<CopycatFenceWallBlockEntity.WallSlot, LocalBox>>()
        if (state.getValue(UP)) {
            boxes += CopycatFenceWallBlockEntity.WallSlot.POST to CENTER_BOX
        }
        for (direction in Direction.Plane.HORIZONTAL) {
            val wallSide = state.getValue(wallProperty(direction))
            val box = when (wallSide) {
                WallSide.NONE -> null
                WallSide.LOW -> LOW_SIDE_BOXES[direction]
                WallSide.TALL -> TALL_SIDE_BOXES[direction]
            } ?: continue
            boxes += wallSlot(direction) to box
        }
        return boxes
    }

    private fun slotPriority(slot: CopycatFenceWallBlockEntity.WallSlot): Int =
        when (slot) {
            CopycatFenceWallBlockEntity.WallSlot.POST -> 1
            else -> 0
        }

    private fun wallSlot(direction: Direction): CopycatFenceWallBlockEntity.WallSlot =
        when (direction) {
            Direction.NORTH -> CopycatFenceWallBlockEntity.WallSlot.NORTH
            Direction.EAST -> CopycatFenceWallBlockEntity.WallSlot.EAST
            Direction.SOUTH -> CopycatFenceWallBlockEntity.WallSlot.SOUTH
            Direction.WEST -> CopycatFenceWallBlockEntity.WallSlot.WEST
            else -> CopycatFenceWallBlockEntity.WallSlot.POST
        }

    private fun firstConnectedWallSlot(state: BlockState): CopycatFenceWallBlockEntity.WallSlot? {
        for (direction in Direction.Plane.HORIZONTAL) {
            if (state.getValue(wallProperty(direction)) != WallSide.NONE) {
                return wallSlot(direction)
            }
        }
        return null
    }
}
