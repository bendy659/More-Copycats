package ru.benos_codex.more_copycats.block.entity

import com.zurrtum.create.AllBlocks
import com.zurrtum.create.api.behaviour.BlockEntityBehaviour
import com.zurrtum.create.api.contraption.transformable.TransformableBlockEntity
import com.zurrtum.create.content.contraptions.StructureTransform
import com.zurrtum.create.content.decoration.bracket.BracketedBlockEntityBehaviour
import com.zurrtum.create.content.kinetics.simpleRelays.AbstractSimpleShaftBlock
import com.zurrtum.create.content.kinetics.simpleRelays.SimpleKineticBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import ru.benos_codex.more_copycats.block.CopycatShaftBlock

class CopycatShaftBlockEntity(
    type: BlockEntityType<*>,
    pos: BlockPos,
    state: BlockState
) : SimpleKineticBlockEntity(type, pos, state), TransformableBlockEntity {

    enum class Slot {
        MAT_0,
        MAT_1
    }

    private val slotMaterials: Array<BlockState> =
        Array(Slot.entries.size) { AllBlocks.COPYCAT_BASE.defaultBlockState() }
    private val slotItems: Array<ItemStack> =
        Array(Slot.entries.size) { ItemStack.EMPTY }

    override fun addBehaviours(behaviours: MutableList<BlockEntityBehaviour<*>>) {
        if (blockState.block is CopycatShaftBlock)
            behaviours.add(BracketedBlockEntityBehaviour(this) { state -> state.block is AbstractSimpleShaftBlock })

        super.addBehaviours(behaviours)
    }

    override fun transform(be: BlockEntity, transform: StructureTransform) {
        getBehaviour(BracketedBlockEntityBehaviour.TYPE)?.transformBracket(transform)
    }

    private fun getStoredSlotMaterial(slot: Slot): BlockState =
        slotMaterials[slot.ordinal]

    fun getSlotMaterial(slot: Slot): BlockState =
        MaterialStateResolver.resolve(level, worldPosition, blockState, getStoredSlotMaterial(slot))

    fun getSlotConsumedItem(slot: Slot): ItemStack =
        slotItems[slot.ordinal]

    fun hasCustomMaterial(slot: Slot): Boolean =
        !getStoredSlotMaterial(slot).`is`(AllBlocks.COPYCAT_BASE)

    fun hasAnyCustomMaterial(): Boolean =
        Slot.entries.any(::hasCustomMaterial)

    fun getMaxLightEmission(): Int =
        Slot.entries.maxOf { getSlotMaterial(it).lightEmission }

    fun setSlotMaterial(slot: Slot, state: BlockState, stack: ItemStack) {
        val idx = slot.ordinal
        slotMaterials[idx] = state
        slotItems[idx] = if (state.`is`(AllBlocks.COPYCAT_BASE)) ItemStack.EMPTY else stack.copyWithCount(1)
        setChanged()
        notifyUpdate()
        syncVisuals()
    }

    fun clearSlotMaterial(slot: Slot) {
        val idx = slot.ordinal
        slotMaterials[idx] = AllBlocks.COPYCAT_BASE.defaultBlockState()
        slotItems[idx] = ItemStack.EMPTY
        setChanged()
        notifyUpdate()
        syncVisuals()
    }

    fun clearAllMaterials() {
        for (slot in Slot.entries) {
            val idx = slot.ordinal
            slotMaterials[idx] = AllBlocks.COPYCAT_BASE.defaultBlockState()
            slotItems[idx] = ItemStack.EMPTY
        }

        setChanged()
        notifyUpdate()
        syncVisuals()
    }

    fun cycleSlotMaterial(slot: Slot): Boolean {
        val idx = slot.ordinal
        val current = getStoredSlotMaterial(slot)
        if (current.`is`(AllBlocks.COPYCAT_BASE))
            return false

        val cycled = rotateMaterial(current) ?: return false
        slotMaterials[idx] = cycled
        setChanged()
        notifyUpdate()
        syncVisuals()
        return true
    }

    private fun rotateMaterial(current: BlockState): BlockState? {
        return when {
            current.hasProperty(BlockStateProperties.FACING) ->
                current.cycle(BlockStateProperties.FACING)

            current.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ->
                current.setValue(
                    BlockStateProperties.HORIZONTAL_FACING,
                    current.getValue(BlockStateProperties.HORIZONTAL_FACING).clockWise
                )

            current.hasProperty(BlockStateProperties.AXIS) ->
                current.cycle(BlockStateProperties.AXIS)

            current.hasProperty(BlockStateProperties.HORIZONTAL_AXIS) ->
                current.cycle(BlockStateProperties.HORIZONTAL_AXIS)

            else -> null
        }
    }

    private fun syncVisuals() {
        val currentLevel = level ?: return
        MaterialLightHelper.refresh(currentLevel, worldPosition, blockState, 16)
    }

    override fun write(view: ValueOutput, clientPacket: Boolean) {
        super.write(view, clientPacket)

        for (slot in Slot.entries) {
            val idx = slot.ordinal
            view.store("Material$idx", BlockState.CODEC, slotMaterials[idx])
            val item = slotItems[idx]
            if (!item.isEmpty)
                view.store("Item$idx", ItemStack.CODEC, item)
        }
    }

    override fun read(view: ValueInput, clientPacket: Boolean) {
        val previous = slotMaterials.copyOf()
        super.read(view, clientPacket)

        for (slot in Slot.entries) {
            val idx = slot.ordinal
            slotMaterials[idx] = view.read("Material$idx", BlockState.CODEC)
                .orElse(AllBlocks.COPYCAT_BASE.defaultBlockState())
            slotItems[idx] = view.read("Item$idx", ItemStack.CODEC).orElse(ItemStack.EMPTY)
            if (slotMaterials[idx].`is`(AllBlocks.COPYCAT_BASE))
                slotItems[idx] = ItemStack.EMPTY
        }

        if (clientPacket && previous.indices.any { previous[it] != slotMaterials[it] })
            syncVisuals()
    }
}