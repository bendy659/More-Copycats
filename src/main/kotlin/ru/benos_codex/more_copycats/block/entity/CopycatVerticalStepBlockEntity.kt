package ru.benos_codex.more_copycats.block.entity

import com.zurrtum.create.AllBlocks
import net.minecraft.core.BlockPos
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.storage.ValueInput

class CopycatVerticalStepBlockEntity(pos: BlockPos, state: BlockState) :
    AbstractMultiMaterialCopycatBlockEntity(pos, state, 4) {

    enum class Slot {
        P0,
        P1,
        P2,
        P3
    }

    fun getSlotMaterial(slot: Slot): BlockState =
        getSlotMaterial(slot.ordinal)

    fun getSlotConsumedItem(slot: Slot): ItemStack =
        getSlotConsumedItem(slot.ordinal)

    fun hasCustomMaterial(slot: Slot): Boolean =
        hasCustomMaterial(slot.ordinal)

    fun setSlotMaterial(slot: Slot, newMaterial: BlockState, sourceStack: ItemStack) {
        setSlotMaterial(slot.ordinal, newMaterial, sourceStack)
    }

    fun clearSlotMaterial(slot: Slot) {
        clearSlotMaterial(slot.ordinal)
    }

    fun cycleSlotMaterial(slot: Slot): Boolean =
        cycleSlotMaterial(slot.ordinal)

    override fun readLegacySlot(view: ValueInput, slot: Int): SlotData? {
        val suffix = when (slot) {
            Slot.P1.ordinal -> "P1"
            Slot.P2.ordinal -> "P2"
            Slot.P3.ordinal -> "P3"
            else -> return null
        }

        val material = view.read("${suffix}Material", BlockState.CODEC).orElse(AllBlocks.COPYCAT_BASE.defaultBlockState())
        return SlotData(material)
    }
}
