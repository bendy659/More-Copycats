package ru.benos_codex.more_copycats.block

import com.zurrtum.create.AllBlocks
import com.zurrtum.create.AllItemTags
import com.zurrtum.create.content.decoration.copycat.CopycatBlock
import com.zurrtum.create.content.kinetics.base.KineticBlockEntity
import com.zurrtum.create.content.kinetics.base.RotatedPillarKineticBlock
import com.zurrtum.create.foundation.block.IBE
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
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.LevelEvent
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.StairBlock
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.level.storage.loot.LootParams
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import ru.benos_codex.more_copycats.MoreCopycatsRegister
import ru.benos_codex.more_copycats.block.entity.CopycatGearboxBlockEntity
import ru.benos_codex.more_copycats.datapack.CopycatDatapackManager
import kotlin.math.abs

class CopycatGearboxBlock(properties: Properties) : RotatedPillarKineticBlock(properties), IBE<CopycatGearboxBlockEntity> {
    data class SlotBox(
        val slot: CopycatGearboxBlockEntity.Slot,
        val aabb: net.minecraft.world.phys.AABB,
        val face: Direction? = null
    )

    data class LockBox(
        val face: Direction,
        val aabb: net.minecraft.world.phys.AABB
    )

    private data class LocalBox(
        val minX: Double,
        val minY: Double,
        val minZ: Double,
        val maxX: Double,
        val maxY: Double,
        val maxZ: Double
    ) {
        fun toWorldAabb(pos: BlockPos): net.minecraft.world.phys.AABB =
            net.minecraft.world.phys.AABB(
                pos.x + minX,
                pos.y + minY,
                pos.z + minZ,
                pos.x + maxX,
                pos.y + maxY,
                pos.z + maxZ
            )
    }

    private data class LocalSlotBox(
        val slot: CopycatGearboxBlockEntity.Slot,
        val face: Direction?,
        val box: LocalBox
    )

    init {
        registerDefaultState(
            stateDefinition.any()
                .setValue(AXIS, Direction.Axis.Y)
                .setValue(LOCK_NORTH, false)
                .setValue(LOCK_EAST, false)
                .setValue(LOCK_SOUTH, false)
                .setValue(LOCK_WEST, false)
                .setValue(LOCK_TOP, false)
                .setValue(LOCK_BOTTOM, false)
        )
    }

    override fun getBlockEntityClass(): Class<CopycatGearboxBlockEntity> =
        CopycatGearboxBlockEntity::class.java

    override fun getBlockEntityType(): BlockEntityType<out CopycatGearboxBlockEntity> =
        MoreCopycatsRegister.GEARBOX_BE

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState? =
        defaultBlockState()
            .takeIf(CopycatDatapackManager::isBlockEnabled)

    override fun getDrops(state: BlockState, builder: LootParams.Builder): MutableList<ItemStack> =
        if (state.getValue(AXIS).isVertical)
            super.getDrops(state, builder)
        else
            mutableListOf(ItemStack(MoreCopycatsRegister.VERTICAL_GEARBOX_ITEM))

    override fun getCloneItemStack(
        level: LevelReader,
        pos: BlockPos,
        state: BlockState,
        includeData: Boolean
    ): ItemStack =
        if (state.getValue(AXIS).isVertical)
            super.getCloneItemStack(level, pos, state, includeData)
        else
            ItemStack(MoreCopycatsRegister.VERTICAL_GEARBOX_ITEM)

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        super.createBlockStateDefinition(builder)
        builder.add(LOCK_NORTH, LOCK_EAST, LOCK_SOUTH, LOCK_WEST, LOCK_TOP, LOCK_BOTTOM)
    }

    override fun hasShaftTowards(world: LevelReader, pos: BlockPos, state: BlockState, face: Direction): Boolean =
        face.axis != state.getValue(AXIS) && !isLocked(state, face)

    override fun getRotationAxis(state: BlockState): Direction.Axis =
        state.getValue(AXIS)

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
            ?: return super.useItemOn(stack, state, level, pos, player, hand, hitResult)
        val material = prepareMaterial(level, pos, materialIn, slot)
            ?: return InteractionResult.TRY_WITH_EMPTY_HAND
        val blockEntity = level.getBlockEntity(pos) as? CopycatGearboxBlockEntity
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

    override fun onWrenched(state: BlockState, context: UseOnContext): InteractionResult {
        if (!CopycatDatapackManager.isBlockEnabled(state))
            return InteractionResult.PASS

        val level = context.level
        val pos = context.clickedPos
        val blockEntity = level.getBlockEntity(pos) as? CopycatGearboxBlockEntity ?: return super.onWrenched(state, context)
        val slot = slotFromHit(context.clickLocation, pos, state)
        if (!blockEntity.hasCustomMaterial(slot))
            return handleLockToggle(state, context) ?: super.onWrenched(state, context)
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

    private fun handleLockToggle(state: BlockState, context: UseOnContext): InteractionResult? {
        val level = context.level
        val pos = context.clickedPos
        val click = context.clickLocation
        val lockFace = resolveLockFace(state, pos, click, context.clickedFace) ?: return null
        val property = lockProperty(lockFace)
        if (level.isClientSide)
            return InteractionResult.SUCCESS

        val next = state.cycle(property)
        KineticBlockEntity.switchToBlockState(level, pos, next)
        level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.75f, 0.95f)
        return InteractionResult.SUCCESS
    }

    override fun onSneakWrenched(state: BlockState, context: UseOnContext): InteractionResult =
        InteractionResult.PASS

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
        val blockEntity = level.getBlockEntity(pos) as? CopycatGearboxBlockEntity ?: return
        if (blockEntity.hasCustomMaterial(CopycatGearboxBlockEntity.Slot.MAT_1))
            return

        if (!level.isClientSide)
            blockEntity.setSlotMaterial(CopycatGearboxBlockEntity.Slot.MAT_1, applied, offhand)

        if (placer is Player && placer.isCreative)
            return

        offhand.shrink(1)
        if (offhand.isEmpty)
            placer.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY)
    }

    override fun playerWillDestroy(level: Level, pos: BlockPos, state: BlockState, player: Player): BlockState {
        val blockEntity = level.getBlockEntity(pos) as? CopycatGearboxBlockEntity
        if (blockEntity != null) {
            if (player.isCreative) {
                blockEntity.clearAllMaterials()
            } else if (!level.isClientSide) {
                for (slot in CopycatGearboxBlockEntity.Slot.entries) {
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
        RenderShape.MODEL

    override fun getShape(
        state: BlockState,
        level: BlockGetter,
        pos: BlockPos,
        context: CollisionContext
    ): VoxelShape =
        referenceState(state).getShape(level, pos, context)

    override fun getCollisionShape(
        state: BlockState,
        level: BlockGetter,
        pos: BlockPos,
        context: CollisionContext
    ): VoxelShape =
        referenceState(state).getCollisionShape(level, pos, context)

    override fun getInteractionShape(state: BlockState, level: BlockGetter, pos: BlockPos): VoxelShape =
        buildInteractionShape(state)

    private fun referenceState(state: BlockState): BlockState =
        AllBlocks.GEARBOX
            .defaultBlockState()
            .setValue(AXIS, state.getValue(AXIS))

    fun slotFromHit(hit: Vec3, pos: BlockPos, state: BlockState): CopycatGearboxBlockEntity.Slot {
        val localX = hit.x - pos.x
        val localY = hit.y - pos.y
        val localZ = hit.z - pos.z

        return if (isShaftHit(state.getValue(AXIS), localX, localY, localZ))
            CopycatGearboxBlockEntity.Slot.MAT_0
        else
            CopycatGearboxBlockEntity.Slot.MAT_1
    }

    fun slotBoxes(state: BlockState, pos: BlockPos): List<SlotBox> =
        slotBoxesLocal(state.getValue(AXIS)).map { local ->
            SlotBox(local.slot, local.box.toWorldAabb(pos), local.face)
        }

    fun lockTriggerBoxes(state: BlockState, pos: BlockPos): List<LockBox> =
        lockTriggerBoxesLocal(state.getValue(AXIS)).map { (face, box) -> LockBox(face, box.toWorldAabb(pos)) }

    private fun isShaftHit(axis: Direction.Axis, x: Double, y: Double, z: Double): Boolean {
        return when (axis) {
            Direction.Axis.X -> isAlongY(x, z) || isAlongZ(x, y)
            Direction.Axis.Y -> isAlongX(y, z) || isAlongZ(x, y)
            Direction.Axis.Z -> isAlongX(y, z) || isAlongY(x, z)
        }
    }

    private fun resolveLockFace(state: BlockState, pos: BlockPos, hit: Vec3, face: Direction): Direction? {
        if (face.axis == state.getValue(AXIS))
            return null
        if (!isWithinLockTrigger(pos, hit, face))
            return null
        return face
    }

    private fun isWithinLockTrigger(pos: BlockPos, hit: Vec3, face: Direction): Boolean {
        val localX = hit.x - pos.x
        val localY = hit.y - pos.y
        val localZ = hit.z - pos.z
        return when (face) {
            Direction.NORTH ->
                localX in LOCK_TRIGGER_MIN_X..LOCK_TRIGGER_MAX_X &&
                    localY in LOCK_TRIGGER_MIN_Y..LOCK_TRIGGER_MAX_Y &&
                    localZ in LOCK_TRIGGER_MIN_DEPTH..LOCK_TRIGGER_MAX_DEPTH
            Direction.SOUTH ->
                localX in LOCK_TRIGGER_MIN_X..LOCK_TRIGGER_MAX_X &&
                    localY in LOCK_TRIGGER_MIN_Y..LOCK_TRIGGER_MAX_Y &&
                    localZ in (1.0 - LOCK_TRIGGER_MAX_DEPTH)..(1.0 - LOCK_TRIGGER_MIN_DEPTH)
            Direction.WEST ->
                localZ in LOCK_TRIGGER_MIN_X..LOCK_TRIGGER_MAX_X &&
                    localY in LOCK_TRIGGER_MIN_Y..LOCK_TRIGGER_MAX_Y &&
                    localX in LOCK_TRIGGER_MIN_DEPTH..LOCK_TRIGGER_MAX_DEPTH
            Direction.EAST ->
                localZ in LOCK_TRIGGER_MIN_X..LOCK_TRIGGER_MAX_X &&
                    localY in LOCK_TRIGGER_MIN_Y..LOCK_TRIGGER_MAX_Y &&
                    localX in (1.0 - LOCK_TRIGGER_MAX_DEPTH)..(1.0 - LOCK_TRIGGER_MIN_DEPTH)
            Direction.DOWN ->
                localX in LOCK_TRIGGER_MIN_X..LOCK_TRIGGER_MAX_X &&
                    localZ in LOCK_TRIGGER_MIN_Y..LOCK_TRIGGER_MAX_Y &&
                    localY in LOCK_TRIGGER_MIN_DEPTH..LOCK_TRIGGER_MAX_DEPTH
            Direction.UP ->
                localX in LOCK_TRIGGER_MIN_X..LOCK_TRIGGER_MAX_X &&
                    localZ in LOCK_TRIGGER_MIN_Y..LOCK_TRIGGER_MAX_Y &&
                    localY in (1.0 - LOCK_TRIGGER_MAX_DEPTH)..(1.0 - LOCK_TRIGGER_MIN_DEPTH)
        }
    }

    private fun isLocked(state: BlockState, face: Direction): Boolean {
        return state.getValue(lockProperty(face))
    }

    private fun lockProperty(face: Direction): BooleanProperty {
        return when (face) {
            Direction.NORTH -> LOCK_NORTH
            Direction.EAST -> LOCK_EAST
            Direction.SOUTH -> LOCK_SOUTH
            Direction.WEST -> LOCK_WEST
            Direction.UP -> LOCK_TOP
            Direction.DOWN -> LOCK_BOTTOM
        }
    }

    private fun isAlongX(y: Double, z: Double): Boolean =
        abs(y - 0.5) <= SHAFT_HALF_WIDTH && abs(z - 0.5) <= SHAFT_HALF_WIDTH

    private fun isAlongY(x: Double, z: Double): Boolean =
        abs(x - 0.5) <= SHAFT_HALF_WIDTH && abs(z - 0.5) <= SHAFT_HALF_WIDTH

    private fun isAlongZ(x: Double, y: Double): Boolean =
        abs(x - 0.5) <= SHAFT_HALF_WIDTH && abs(y - 0.5) <= SHAFT_HALF_WIDTH

    private fun prepareMaterial(
        level: Level,
        pos: BlockPos,
        material: BlockState,
        slot: CopycatGearboxBlockEntity.Slot
    ): BlockState? {
        val blockEntity = level.getBlockEntity(pos) as? CopycatGearboxBlockEntity ?: return material

        return if (blockEntity.hasCustomMaterial(slot))
            blockEntity.getSlotMaterial(slot)
        else
            material
    }

    private fun getAcceptedBlockState(level: Level, pos: BlockPos, stack: ItemStack, face: Direction?): BlockState? {
        val blockItem = stack.item as? BlockItem ?: return null
        val block = blockItem.block
        if (block is CopycatGearboxBlock || block is CopycatBlock)
            return null
        if (block is EntityBlock || block is StairBlock)
            return null

        var appliedState = block.defaultBlockState()
        val shape = appliedState.getShape(level, pos)
        if (shape.isEmpty || shape.bounds() != Shapes.block().bounds())
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

    private fun slotBoxesLocal(axis: Direction.Axis): List<LocalSlotBox> {
        val min = 0.5 - SHAFT_HALF_WIDTH
        val max = 0.5 + SHAFT_HALF_WIDTH
        val boxes = mutableListOf<LocalSlotBox>()
        val faceBoxes = mapOf(
            Direction.NORTH to LocalBox(min, min, 0.0, max, max, 0.5),
            Direction.SOUTH to LocalBox(min, min, 0.5, max, max, 1.0),
            Direction.WEST to LocalBox(0.0, min, min, 0.5, max, max),
            Direction.EAST to LocalBox(0.5, min, min, 1.0, max, max),
            Direction.DOWN to LocalBox(min, 0.0, min, max, 0.5, max),
            Direction.UP to LocalBox(min, 0.5, min, max, 1.0, max)
        )
        for (face in Direction.entries) {
            if (face.axis == axis)
                continue
            val box = faceBoxes[face] ?: continue
            boxes += LocalSlotBox(CopycatGearboxBlockEntity.Slot.MAT_0, face, box)
        }
        boxes += LocalSlotBox(CopycatGearboxBlockEntity.Slot.MAT_1, null, LocalBox(0.0, 0.0, 0.0, 1.0, 1.0, 1.0))
        return boxes
    }

    private fun lockTriggerBoxesLocal(axis: Direction.Axis): List<Pair<Direction, LocalBox>> {
        val faces = Direction.entries.filter { it.axis != axis }
        return faces.map { face -> face to lockTriggerBox(face) }
    }

    private fun lockTriggerBox(face: Direction): LocalBox {
        return when (face) {
            Direction.NORTH ->
                LocalBox(LOCK_TRIGGER_MIN_X, LOCK_TRIGGER_MIN_Y, LOCK_TRIGGER_MIN_DEPTH, LOCK_TRIGGER_MAX_X, LOCK_TRIGGER_MAX_Y, LOCK_TRIGGER_MAX_DEPTH)
            Direction.SOUTH ->
                LocalBox(LOCK_TRIGGER_MIN_X, LOCK_TRIGGER_MIN_Y, 1.0 - LOCK_TRIGGER_MAX_DEPTH, LOCK_TRIGGER_MAX_X, LOCK_TRIGGER_MAX_Y, 1.0 - LOCK_TRIGGER_MIN_DEPTH)
            Direction.WEST ->
                LocalBox(LOCK_TRIGGER_MIN_DEPTH, LOCK_TRIGGER_MIN_Y, LOCK_TRIGGER_MIN_X, LOCK_TRIGGER_MAX_DEPTH, LOCK_TRIGGER_MAX_Y, LOCK_TRIGGER_MAX_X)
            Direction.EAST ->
                LocalBox(1.0 - LOCK_TRIGGER_MAX_DEPTH, LOCK_TRIGGER_MIN_Y, LOCK_TRIGGER_MIN_X, 1.0 - LOCK_TRIGGER_MIN_DEPTH, LOCK_TRIGGER_MAX_Y, LOCK_TRIGGER_MAX_X)
            Direction.DOWN ->
                LocalBox(LOCK_TRIGGER_MIN_X, LOCK_TRIGGER_MIN_DEPTH, LOCK_TRIGGER_MIN_Y, LOCK_TRIGGER_MAX_X, LOCK_TRIGGER_MAX_DEPTH, LOCK_TRIGGER_MAX_Y)
            Direction.UP ->
                LocalBox(LOCK_TRIGGER_MIN_X, 1.0 - LOCK_TRIGGER_MAX_DEPTH, LOCK_TRIGGER_MIN_Y, LOCK_TRIGGER_MAX_X, 1.0 - LOCK_TRIGGER_MIN_DEPTH, LOCK_TRIGGER_MAX_Y)
        }
    }

    companion object {
        val LOCK_NORTH: BooleanProperty = BooleanProperty.create("lock_north")
        val LOCK_EAST: BooleanProperty = BooleanProperty.create("lock_east")
        val LOCK_SOUTH: BooleanProperty = BooleanProperty.create("lock_south")
        val LOCK_WEST: BooleanProperty = BooleanProperty.create("lock_west")
        val LOCK_TOP: BooleanProperty = BooleanProperty.create("lock_top")
        val LOCK_BOTTOM: BooleanProperty = BooleanProperty.create("lock_bottom")
        const val SHAFT_HALF_WIDTH = 2.5 / 16.0
        private const val LOCK_TRIGGER_MIN_X = 2.5 / 16.0
        private const val LOCK_TRIGGER_MAX_X = 13.5 / 16.0
        private const val LOCK_TRIGGER_MIN_Y = 1.0 / 16.0
        private const val LOCK_TRIGGER_MAX_Y = 15.0 / 16.0
        private const val LOCK_TRIGGER_MIN_DEPTH = -0.5 / 16.0
        private const val LOCK_TRIGGER_MAX_DEPTH = 0.5 / 16.0

        private const val SHAFT_INTERACTION_MARGIN = 2.0
        private const val SHAFT_INTERACTION_DEPTH = 2.0
        private const val SHAFT_INTERACTION_THICKNESS = 2.0
        private val SHAFT_FACE_NORTH = Block.box(
            SHAFT_INTERACTION_MARGIN,
            SHAFT_INTERACTION_MARGIN,
            SHAFT_INTERACTION_DEPTH,
            16.0 - SHAFT_INTERACTION_MARGIN,
            16.0 - SHAFT_INTERACTION_MARGIN,
            SHAFT_INTERACTION_DEPTH + SHAFT_INTERACTION_THICKNESS
        )
        private val SHAFT_FACE_SOUTH = Block.box(
            SHAFT_INTERACTION_MARGIN,
            SHAFT_INTERACTION_MARGIN,
            16.0 - SHAFT_INTERACTION_DEPTH - SHAFT_INTERACTION_THICKNESS,
            16.0 - SHAFT_INTERACTION_MARGIN,
            16.0 - SHAFT_INTERACTION_MARGIN,
            16.0 - SHAFT_INTERACTION_DEPTH
        )
        private val SHAFT_FACE_WEST = Block.box(
            SHAFT_INTERACTION_DEPTH,
            SHAFT_INTERACTION_MARGIN,
            SHAFT_INTERACTION_MARGIN,
            SHAFT_INTERACTION_DEPTH + SHAFT_INTERACTION_THICKNESS,
            16.0 - SHAFT_INTERACTION_MARGIN,
            16.0 - SHAFT_INTERACTION_MARGIN
        )
        private val SHAFT_FACE_EAST = Block.box(
            16.0 - SHAFT_INTERACTION_DEPTH - SHAFT_INTERACTION_THICKNESS,
            SHAFT_INTERACTION_MARGIN,
            SHAFT_INTERACTION_MARGIN,
            16.0 - SHAFT_INTERACTION_DEPTH,
            16.0 - SHAFT_INTERACTION_MARGIN,
            16.0 - SHAFT_INTERACTION_MARGIN
        )
        private val SHAFT_FACE_DOWN = Block.box(
            SHAFT_INTERACTION_MARGIN,
            SHAFT_INTERACTION_DEPTH,
            SHAFT_INTERACTION_MARGIN,
            16.0 - SHAFT_INTERACTION_MARGIN,
            SHAFT_INTERACTION_DEPTH + SHAFT_INTERACTION_THICKNESS,
            16.0 - SHAFT_INTERACTION_MARGIN
        )
        private val SHAFT_FACE_UP = Block.box(
            SHAFT_INTERACTION_MARGIN,
            16.0 - SHAFT_INTERACTION_DEPTH - SHAFT_INTERACTION_THICKNESS,
            SHAFT_INTERACTION_MARGIN,
            16.0 - SHAFT_INTERACTION_MARGIN,
            16.0 - SHAFT_INTERACTION_DEPTH,
            16.0 - SHAFT_INTERACTION_MARGIN
        )

        private val TRIGGER_NORTH = Block.box(2.5, 1.0, -0.5, 13.5, 15.0, 0.5)
        private val TRIGGER_SOUTH = Block.box(2.5, 1.0, 15.5, 13.5, 15.0, 16.5)
        private val TRIGGER_WEST = Block.box(-0.5, 1.0, 2.5, 0.5, 15.0, 13.5)
        private val TRIGGER_EAST = Block.box(15.5, 1.0, 2.5, 16.5, 15.0, 13.5)
        private val TRIGGER_DOWN = Block.box(2.5, -0.5, 1.0, 13.5, 0.5, 15.0)
        private val TRIGGER_UP = Block.box(2.5, 15.5, 1.0, 13.5, 16.5, 15.0)

        private fun buildInteractionShape(state: BlockState): VoxelShape {
            var shape = Shapes.empty()
            when (state.getValue(AXIS)) {
                Direction.Axis.X -> {
                    if (!state.getValue(LOCK_NORTH)) shape = Shapes.or(shape, SHAFT_FACE_NORTH)
                    if (!state.getValue(LOCK_SOUTH)) shape = Shapes.or(shape, SHAFT_FACE_SOUTH)
                    if (!state.getValue(LOCK_TOP)) shape = Shapes.or(shape, SHAFT_FACE_UP)
                    if (!state.getValue(LOCK_BOTTOM)) shape = Shapes.or(shape, SHAFT_FACE_DOWN)
                    shape = Shapes.or(shape, TRIGGER_NORTH, TRIGGER_SOUTH, TRIGGER_UP, TRIGGER_DOWN)
                }
                Direction.Axis.Y -> {
                    if (!state.getValue(LOCK_NORTH)) shape = Shapes.or(shape, SHAFT_FACE_NORTH)
                    if (!state.getValue(LOCK_SOUTH)) shape = Shapes.or(shape, SHAFT_FACE_SOUTH)
                    if (!state.getValue(LOCK_EAST)) shape = Shapes.or(shape, SHAFT_FACE_EAST)
                    if (!state.getValue(LOCK_WEST)) shape = Shapes.or(shape, SHAFT_FACE_WEST)
                    shape = Shapes.or(shape, TRIGGER_NORTH, TRIGGER_SOUTH, TRIGGER_EAST, TRIGGER_WEST)
                }
                Direction.Axis.Z -> {
                    if (!state.getValue(LOCK_EAST)) shape = Shapes.or(shape, SHAFT_FACE_EAST)
                    if (!state.getValue(LOCK_WEST)) shape = Shapes.or(shape, SHAFT_FACE_WEST)
                    if (!state.getValue(LOCK_TOP)) shape = Shapes.or(shape, SHAFT_FACE_UP)
                    if (!state.getValue(LOCK_BOTTOM)) shape = Shapes.or(shape, SHAFT_FACE_DOWN)
                    shape = Shapes.or(shape, TRIGGER_EAST, TRIGGER_WEST, TRIGGER_UP, TRIGGER_DOWN)
                }
            }
            return shape
        }
    }
}
