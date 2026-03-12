package ru.benos_codex.more_copycats.block

import com.zurrtum.create.AllItemTags
import com.zurrtum.create.AllBlocks
import com.zurrtum.create.content.decoration.copycat.CopycatBlock
import com.zurrtum.create.content.decoration.encasing.EncasedBlock
import com.zurrtum.create.content.decoration.encasing.EncasableBlock
import com.zurrtum.create.content.equipment.wrench.IWrenchable
import com.zurrtum.create.content.kinetics.base.IRotate
import com.zurrtum.create.content.kinetics.simpleRelays.CogWheelBlock
import com.zurrtum.create.content.kinetics.base.KineticBlockEntity
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
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.block.StairBlock
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import org.slf4j.LoggerFactory
import ru.benos_codex.more_copycats.MoreCopycats
import ru.benos_codex.more_copycats.MoreCopycatsRegister
import ru.benos_codex.more_copycats.block.entity.CopycatCogwheelBlockEntity
import ru.benos_codex.more_copycats.datapack.CopycatDatapackManager
import ru.benos_codex.more_copycats.util.CogwheelMaterialSlotResolver
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.round

open class CopycatCogwheelBlock(
    properties: Properties,
    private val large: Boolean = false
) : CogWheelBlock(large, properties), EncasableBlock, EncasedBlock {
    companion object {
        private const val HIT_LOG_INTERVAL_MS = 300L
        private val hitLogger = LoggerFactory.getLogger("MoreCopycats/CogwheelHit")
        private val hitLogThrottle = ConcurrentHashMap<String, Long>()
        private val interactionShapeLogThrottle = ConcurrentHashMap<String, Long>()
        private val INTERACTION_SHAPES: Map<Direction.Axis, VoxelShape> =
            Direction.Axis.entries.associateWith {
                CogwheelMaterialSlotResolver.buildInteractionShape(CogwheelMaterialSlotResolver.Layout.NORMAL, it)
            }
    }

    override fun getBlockEntityType(): BlockEntityType<out KineticBlockEntity> = MoreCopycatsRegister.COGWHEEL_BE

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState? {
        val state = super.getStateForPlacement(context) ?: return null
        return if (CopycatDatapackManager.isBlockEnabled(state)) state else null
    }

    override fun useWithoutItem(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hitResult: BlockHitResult
    ): InteractionResult {
        if (!CopycatDatapackManager.isBlockEnabled(state)) return InteractionResult.PASS
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
        if (!CopycatDatapackManager.isBlockEnabled(state)) return InteractionResult.PASS
        if (canTryEncase(state) && !player.isShiftKeyDown && player.mayBuild()) {
            val encaseResult = tryEncase(state, level, pos, stack, player, hand, hitResult)
            if (encaseResult.consumesAction())
                return encaseResult
        }

        if (stack.`is`(AllItemTags.TOOLS_WRENCH)) {
            val context = UseOnContext(player, hand, hitResult)
            val result = if (player.isShiftKeyDown)
                onSneakWrenched(state, context)
            else
                onWrenched(state, context)
            return if (result == InteractionResult.PASS) InteractionResult.SUCCESS else result
        }

        val slot = slotFromHit(hitResult, pos, state, player)
        val materialIn = getAcceptedBlockState(level, pos, stack, hitResult.direction)
            ?: return super.useItemOn(stack, state, level, pos, player, hand, hitResult)
        val material = prepareMaterial(level, pos, materialIn, slot)
            ?: return InteractionResult.TRY_WITH_EMPTY_HAND
        val blockEntity = level.getBlockEntity(pos) as? CopycatCogwheelBlockEntity
            ?: return super.useItemOn(stack, state, level, pos, player, hand, hitResult)

        val current = blockEntity.getSlotMaterial(slot)
        if (current.`is`(material.block) || blockEntity.hasCustomMaterial(slot)) {
            if (!blockEntity.cycleSlotMaterial(slot)) return InteractionResult.TRY_WITH_EMPTY_HAND
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

    override fun onWrenched(state: BlockState, context: UseOnContext): InteractionResult {
        if (!CopycatDatapackManager.isBlockEnabled(state)) return InteractionResult.PASS
        val level = context.level
        val pos = context.clickedPos
        val blockEntity = level.getBlockEntity(pos) as? CopycatCogwheelBlockEntity ?: return InteractionResult.PASS
        val slot = slotFromHit(context.clickLocation, pos, state, context.clickedFace, context.player)
        if (!blockEntity.hasCustomMaterial(slot)) return InteractionResult.PASS
        if (level.isClientSide) return InteractionResult.SUCCESS

        val removedState = blockEntity.getSlotMaterial(slot)
        val removedStack = blockEntity.getSlotConsumedItem(slot).copy()
        blockEntity.clearSlotMaterial(slot)

        val player = context.player
        if (player != null && !player.isCreative && !removedStack.isEmpty) {
            player.inventory.placeItemBackInInventory(removedStack)
        }

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
        if (!CopycatDatapackManager.isBlockEnabled(state)) return
        super.setPlacedBy(level, pos, state, placer, stack)
        if (placer == null) return

        val offhand = placer.getItemInHand(InteractionHand.OFF_HAND)
        val applied = getAcceptedBlockState(
            level,
            pos,
            offhand,
            Direction.orderedByNearest(placer).firstOrNull() ?: Direction.NORTH
        ) ?: return
        val blockEntity = level.getBlockEntity(pos) as? CopycatCogwheelBlockEntity ?: return
        val defaultSlot = defaultPlacementSlot(state)
        if (blockEntity.hasCustomMaterial(defaultSlot)) return

        if (!level.isClientSide) {
            blockEntity.setSlotMaterial(defaultSlot, applied, offhand)
        }

        if (placer is Player && placer.isCreative) return
        offhand.shrink(1)
        if (offhand.isEmpty) {
            placer.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY)
        }
    }

    override fun playerWillDestroy(level: Level, pos: BlockPos, state: BlockState, player: Player): BlockState {
        val blockEntity = level.getBlockEntity(pos) as? CopycatCogwheelBlockEntity
        if (blockEntity != null) {
            if (player.isCreative) {
                blockEntity.clearAllMaterials()
            } else if (!level.isClientSide) {
                for (slot in CopycatCogwheelBlockEntity.Slot.entries) {
                    val stored = blockEntity.getSlotConsumedItem(slot)
                    if (!stored.isEmpty) {
                        Block.popResource(level, pos, stored.copy())
                    }
                }
                blockEntity.clearAllMaterials()
            }
        }
        return super.playerWillDestroy(level, pos, state, player)
    }

    override fun getRenderShape(state: BlockState): RenderShape =
        RenderShape.INVISIBLE

    override fun getShape(
        state: BlockState,
        level: BlockGetter,
        pos: BlockPos,
        context: CollisionContext
    ): VoxelShape = INTERACTION_SHAPES.getValue(state.getValue(AXIS))

    override fun getCollisionShape(
        state: BlockState,
        level: BlockGetter,
        pos: BlockPos,
        context: CollisionContext
    ): VoxelShape {
        return (if (isLargeCog) AllBlocks.LARGE_COGWHEEL else AllBlocks.COGWHEEL)
            .defaultBlockState()
            .setValue(AXIS, state.getValue(AXIS))
            .getCollisionShape(level, pos, context)
    }

    override fun getInteractionShape(
        state: BlockState,
        level: BlockGetter,
        pos: BlockPos
    ): VoxelShape {
        val axis = state.getValue(AXIS)
        val shape = INTERACTION_SHAPES.getValue(axis)
        maybeLogInteractionShape(pos, axis, shape)
        return shape
    }

    override fun getCasing(): Block =
        AllBlocks.ANDESITE_CASING

    override fun handleEncasing(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        heldItem: ItemStack,
        player: Player?,
        hand: InteractionHand,
        ray: BlockHitResult
    ) {
        val encasedState = buildEncasedReplacementState(state, level, pos)
        KineticBlockEntity.switchToBlockState(level, pos, encasedState)
    }

    protected fun buildEncasedReplacementState(
        state: BlockState,
        level: LevelReader,
        pos: BlockPos
    ): BlockState {
        var encasedState = MoreCopycatsRegister.ENCASED_COGWHEEL_BLOCK
            .defaultBlockState()
            .setValue(AXIS, state.getValue(AXIS))
            .setValue(CopycatEncasedCogwheelBlock.TOP_SHAFT, false)
            .setValue(CopycatEncasedCogwheelBlock.BOTTOM_SHAFT, false)
        for (direction in Direction.entries) {
            if (direction.axis != state.getValue(AXIS))
                continue

            val adjacentPos = pos.relative(direction)
            val adjacentState = level.getBlockState(adjacentPos)
            val rotate = adjacentState.block as? IRotate ?: continue
            if (!rotate.hasShaftTowards(level, adjacentPos, adjacentState, direction.opposite))
                continue

            val property = if (direction.axisDirection == Direction.AxisDirection.POSITIVE)
                CopycatEncasedCogwheelBlock.TOP_SHAFT
            else
                CopycatEncasedCogwheelBlock.BOTTOM_SHAFT
            encasedState = encasedState.setValue(property, true)
        }
        return encasedState
    }

    protected fun buildUnencasedReplacementState(state: BlockState): BlockState {
        return MoreCopycatsRegister.COGWHEEL_BLOCK
            .defaultBlockState()
            .setValue(AXIS, state.getValue(AXIS))
    }

    override fun hasShaftTowards(world: LevelReader, pos: BlockPos, state: BlockState, face: Direction): Boolean {
        return super.hasShaftTowards(world, pos, state, face)
    }

    protected open fun canTryEncase(state: BlockState): Boolean = false

    protected open fun resolverLayout(state: BlockState): CogwheelMaterialSlotResolver.Layout =
        CogwheelMaterialSlotResolver.Layout.NORMAL

    protected open fun defaultPlacementSlot(state: BlockState): CopycatCogwheelBlockEntity.Slot =
        CopycatCogwheelBlockEntity.Slot.MAT_4

    private fun prepareMaterial(
        level: Level,
        pos: BlockPos,
        material: BlockState,
        slot: CopycatCogwheelBlockEntity.Slot
    ): BlockState? {
        val blockEntity = level.getBlockEntity(pos) as? CopycatCogwheelBlockEntity ?: return material
        return if (blockEntity.hasCustomMaterial(slot)) blockEntity.getSlotMaterial(slot) else material
    }

    private fun getAcceptedBlockState(level: Level, pos: BlockPos, stack: ItemStack, face: Direction?): BlockState? {
        val blockItem = stack.item as? BlockItem ?: return null
        val block = blockItem.block
        if (block is CopycatCogwheelBlock || block is CopycatBlock) return null
        if (block is EntityBlock) return null
        if (block is StairBlock) return null

        var appliedState = block.defaultBlockState()
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

    private fun slotFromHit(
        hit: BlockHitResult,
        pos: BlockPos,
        state: BlockState,
        player: Player? = null
    ): CopycatCogwheelBlockEntity.Slot =
        slotFromHit(hit.location, pos, state, hit.direction, player)

    private fun slotFromHit(
        hit: net.minecraft.world.phys.Vec3,
        pos: BlockPos,
        state: BlockState,
        face: Direction? = null,
        player: Player? = null
    ): CopycatCogwheelBlockEntity.Slot {
        val relX = hit.x - pos.x
        val relY = hit.y - pos.y
        val relZ = hit.z - pos.z

        val axis = state.getValue(AXIS)
        val layout = resolverLayout(state)
        val topShaft = if (state.block is CopycatEncasedCogwheelBlock)
            state.getValue(CopycatEncasedCogwheelBlock.TOP_SHAFT)
        else
            true
        val bottomShaft = if (state.block is CopycatEncasedCogwheelBlock)
            state.getValue(CopycatEncasedCogwheelBlock.BOTTOM_SHAFT)
        else
            true
        val raycast = buildSelectionRay(player, hit)
            ?.let { (start, end) ->
                CogwheelMaterialSlotResolver.raycast(
                    layout,
                    axis,
                    start.x - pos.x,
                    start.y - pos.y,
                    start.z - pos.z,
                    end.x - pos.x,
                    end.y - pos.y,
                    end.z - pos.z,
                    topShaft,
                    bottomShaft
                )
            }
        val strict = raycast?.slot
            ?: CogwheelMaterialSlotResolver.resolveStrict(layout, axis, relX, relY, relZ, topShaft, bottomShaft)
        val index = strict ?: if (layout == CogwheelMaterialSlotResolver.Layout.NORMAL)
            4
        else
            CogwheelMaterialSlotResolver.resolve(layout, axis, relX, relY, relZ, face, topShaft, bottomShaft)
        maybeLogHitResolution(pos, axis, relX, relY, relZ, strict, index)
        return slotByIndex(index)
    }

    private fun buildSelectionRay(player: Player?, hit: Vec3): Pair<Vec3, Vec3>? {
        if (player == null)
            return null

        val start = player.getEyePosition()
        val direction = player.lookAngle.normalize()
        val end = hit.add(direction.scale(2.0))

        return start to end
    }

    private fun maybeLogHitResolution(
        pos: BlockPos,
        axis: Direction.Axis,
        relX: Double,
        relY: Double,
        relZ: Double,
        strict: Int?,
        resolved: Int
    ) {
        if (!MoreCopycats.DEBUG_COGWHEEL_HIT_LOGS)
            return

        val local = CogwheelMaterialSlotResolver.toLocal(axis, relX, relY, relZ)
        val key = buildString {
            append(pos.asLong())
            append('|')
            append(axis.name)
            append('|')
            append(strict ?: -1)
            append('|')
            append(resolved)
            append('|')
            append((local.x * 20.0).toInt())
            append(':')
            append((local.y * 20.0).toInt())
            append(':')
            append((local.z * 20.0).toInt())
        }

        val now = System.currentTimeMillis()
        val previous = hitLogThrottle.put(key, now)
        if (previous != null && now - previous < HIT_LOG_INTERVAL_MS)
            return

        hitLogger.info(
            "hit pos={} axis={} world=({}, {}, {}) local=({}, {}, {}) strict={} resolved={}",
            pos,
            axis,
            round3(relX),
            round3(relY),
            round3(relZ),
            round3(local.x),
            round3(local.y),
            round3(local.z),
            strict?.toString() ?: "null",
            resolved
        )
    }

    private fun round3(value: Double): Double =
        round(value * 1000.0) / 1000.0

    private fun maybeLogInteractionShape(
        pos: BlockPos,
        axis: Direction.Axis,
        shape: VoxelShape
    ) {
        if (!MoreCopycats.DEBUG_COGWHEEL_HIT_LOGS)
            return

        val now = System.currentTimeMillis()
        val key = "${pos.asLong()}|${axis.name}"
        val previous = interactionShapeLogThrottle.put(key, now)
        if (previous != null && now - previous < HIT_LOG_INTERVAL_MS)
            return

        if (shape.isEmpty) {
            hitLogger.info("interaction-shape pos={} axis={} empty=true", pos, axis)
            return
        }

        val bounds = shape.bounds()
        hitLogger.info(
            "interaction-shape pos={} axis={} empty=false bounds=({}, {}, {}) -> ({}, {}, {})",
            pos,
            axis,
            round3(bounds.minX),
            round3(bounds.minY),
            round3(bounds.minZ),
            round3(bounds.maxX),
            round3(bounds.maxY),
            round3(bounds.maxZ)
        )
    }

    private fun slotByIndex(index: Int): CopycatCogwheelBlockEntity.Slot {
        return when (index) {
            0 -> CopycatCogwheelBlockEntity.Slot.MAT_0
            1 -> CopycatCogwheelBlockEntity.Slot.MAT_1
            2 -> CopycatCogwheelBlockEntity.Slot.MAT_2
            3 -> CopycatCogwheelBlockEntity.Slot.MAT_3
            4 -> CopycatCogwheelBlockEntity.Slot.MAT_4
            5 -> CopycatCogwheelBlockEntity.Slot.MAT_5
            6 -> CopycatCogwheelBlockEntity.Slot.MAT_6
            else -> CopycatCogwheelBlockEntity.Slot.MAT_7
        }
    }
}
