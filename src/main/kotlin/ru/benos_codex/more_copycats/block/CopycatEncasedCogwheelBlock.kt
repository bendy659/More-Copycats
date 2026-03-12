package ru.benos_codex.more_copycats.block

import com.zurrtum.create.AllBlocks
import com.zurrtum.create.api.contraption.transformable.TransformableBlock
import com.zurrtum.create.content.contraptions.StructureTransform
import com.zurrtum.create.content.kinetics.base.KineticBlockEntity
import com.zurrtum.create.content.kinetics.base.RotatedPillarKineticBlock
import com.zurrtum.create.content.kinetics.simpleRelays.encased.EncasedCogwheelBlock
import net.minecraft.core.Direction
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape
import ru.benos_codex.more_copycats.datapack.CopycatDatapackManager
import ru.benos_codex.more_copycats.util.CogwheelMaterialSlotResolver

class CopycatEncasedCogwheelBlock(
    properties: Properties,
    large: Boolean = false
) : CopycatCogwheelBlock(properties, large), TransformableBlock {
    companion object {
        val TOP_SHAFT: BooleanProperty = EncasedCogwheelBlock.TOP_SHAFT
        val BOTTOM_SHAFT: BooleanProperty = EncasedCogwheelBlock.BOTTOM_SHAFT
    }

    init {
        registerDefaultState(
            defaultBlockState()
                .setValue(TOP_SHAFT, false)
                .setValue(BOTTOM_SHAFT, false)
        )
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        super.createBlockStateDefinition(builder)
        builder.add(TOP_SHAFT, BOTTOM_SHAFT)
    }

    override fun getStateForPlacement(context: BlockPlaceContext) =
        super.getStateForPlacement(context)?.let { buildEncasedReplacementState(it, context.level, context.clickedPos) }

    override fun onWrenched(state: BlockState, context: UseOnContext): InteractionResult {
        if (!CopycatDatapackManager.isBlockEnabled(state))
            return InteractionResult.PASS
        return super.onWrenched(state, context)
    }

    override fun onSneakWrenched(state: BlockState, context: UseOnContext): InteractionResult {
        if (!CopycatDatapackManager.isBlockEnabled(state))
            return InteractionResult.PASS
        return super.onSneakWrenched(state, context)
    }

    override fun getRenderShape(state: BlockState): RenderShape =
        RenderShape.MODEL

    override fun getShape(
        state: BlockState,
        level: BlockGetter,
        pos: net.minecraft.core.BlockPos,
        context: CollisionContext
    ): VoxelShape = referenceEncasedState(state).getShape(level, pos, context)

    override fun getCollisionShape(
        state: BlockState,
        level: BlockGetter,
        pos: net.minecraft.core.BlockPos,
        context: CollisionContext
    ): VoxelShape = referenceEncasedState(state).getCollisionShape(level, pos, context)

    override fun getInteractionShape(
        state: BlockState,
        level: BlockGetter,
        pos: net.minecraft.core.BlockPos
    ): VoxelShape =
        CogwheelMaterialSlotResolver.buildInteractionShape(
            CogwheelMaterialSlotResolver.Layout.ENCASED_VARIANT_2,
            state.getValue(AXIS),
            topShaft = state.getValue(TOP_SHAFT),
            bottomShaft = state.getValue(BOTTOM_SHAFT)
        )

    override fun hasShaftTowards(world: LevelReader, pos: net.minecraft.core.BlockPos, state: BlockState, face: Direction): Boolean {
        if (face.axis != state.getValue(AXIS))
            return false

        return state.getValue(if (face.axisDirection == Direction.AxisDirection.POSITIVE) TOP_SHAFT else BOTTOM_SHAFT)
    }

    override fun rotate(state: BlockState, rotation: Rotation): BlockState {
        val swapped = swapShaftsForRotation(state, rotation, Direction.Axis.Y)
        return super.rotate(swapped, rotation)
    }

    override fun mirror(state: BlockState, mirror: Mirror): BlockState {
        val axis = state.getValue(AXIS)
        if (axis == Direction.Axis.X && mirror == Mirror.FRONT_BACK)
            return swapShafts(state)
        if (axis == Direction.Axis.Z && mirror == Mirror.LEFT_RIGHT)
            return swapShafts(state)

        return state
    }

    override fun transform(state: BlockState, transform: StructureTransform): BlockState {
        var transformed = state
        transform.mirror?.let { transformed = mirror(transformed, it) }

        if (transform.rotationAxis == Direction.Axis.Y)
            return rotate(transformed, transform.rotation)

        transformed = swapShaftsForRotation(transformed, transform.rotation, transform.rotationAxis)
        return transformed.setValue(AXIS, transform.rotateAxis(transformed.getValue(AXIS)))
    }

    override fun canTryEncase(state: BlockState): Boolean = false

    override fun resolverLayout(state: BlockState): CogwheelMaterialSlotResolver.Layout =
        CogwheelMaterialSlotResolver.Layout.ENCASED_VARIANT_2

    override fun defaultPlacementSlot(state: BlockState) =
        ru.benos_codex.more_copycats.block.entity.CopycatCogwheelBlockEntity.Slot.MAT_7

    private fun referenceEncasedState(state: BlockState): BlockState =
        (if (isLargeCog) AllBlocks.ANDESITE_ENCASED_LARGE_COGWHEEL else AllBlocks.ANDESITE_ENCASED_COGWHEEL)
            .defaultBlockState()
            .setValue(RotatedPillarKineticBlock.AXIS, state.getValue(AXIS))
            .setValue(EncasedCogwheelBlock.TOP_SHAFT, state.getValue(TOP_SHAFT))
            .setValue(EncasedCogwheelBlock.BOTTOM_SHAFT, state.getValue(BOTTOM_SHAFT))

    private fun swapShafts(state: BlockState): BlockState {
        val bottom = state.getValue(BOTTOM_SHAFT)
        val top = state.getValue(TOP_SHAFT)
        return state
            .setValue(BOTTOM_SHAFT, top)
            .setValue(TOP_SHAFT, bottom)
    }

    private fun swapShaftsForRotation(state: BlockState, rotation: Rotation, rotationAxis: Direction.Axis): BlockState {
        if (rotation == Rotation.NONE)
            return state

        val axis = state.getValue(AXIS)
        if (axis == rotationAxis)
            return state
        if (rotation == Rotation.CLOCKWISE_180)
            return swapShafts(state)

        val clockwise = rotation == Rotation.CLOCKWISE_90
        if (rotationAxis == Direction.Axis.X) {
            if ((axis == Direction.Axis.Z && !clockwise) || (axis == Direction.Axis.Y && clockwise))
                return swapShafts(state)
        } else if (rotationAxis == Direction.Axis.Y) {
            if ((axis == Direction.Axis.X && !clockwise) || (axis == Direction.Axis.Z && clockwise))
                return swapShafts(state)
        } else if ((axis == Direction.Axis.Y && !clockwise) || (axis == Direction.Axis.X && clockwise)) {
            return swapShafts(state)
        }

        return state
    }
}
