package ru.benos_codex.more_copycats.block.entity

import com.mojang.serialization.Codec
import com.zurrtum.create.AllBlocks
import net.minecraft.core.BlockPos
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.SlabType
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput

class CopycatSlabBlockEntity(pos: BlockPos, state: BlockState) :
    AbstractMultiMaterialCopycatBlockEntity(pos, state, 2) {

    companion object {
        private const val PRIMARY_SLOT = 0
        private const val SECONDARY_SLOT = 1
    }

    private var primaryIsPositive: Boolean = false

    private fun slotForHalf(half: SlabType): Int {
        if (half == SlabType.DOUBLE) return PRIMARY_SLOT
        return if (primaryIsPositive) {
            if (half == SlabType.TOP) PRIMARY_SLOT else SECONDARY_SLOT
        } else {
            if (half == SlabType.BOTTOM) PRIMARY_SLOT else SECONDARY_SLOT
        }
    }

    fun getHalfMaterial(half: SlabType): BlockState =
        getSlotMaterial(slotForHalf(half))

    fun getHalfConsumedItem(half: SlabType): ItemStack =
        getSlotConsumedItem(slotForHalf(half))

    fun hasCustomMaterial(half: SlabType): Boolean =
        hasCustomMaterial(slotForHalf(half))

    fun setHalfMaterial(half: SlabType, newMaterial: BlockState, sourceStack: ItemStack) {
        setSlotMaterial(slotForHalf(half), newMaterial, sourceStack)
    }

    fun clearHalfMaterial(half: SlabType) {
        clearSlotMaterial(slotForHalf(half))
    }

    fun clearTopConsumedItem() {
        clearSlotConsumedItem(SECONDARY_SLOT)
    }

    fun promoteToDouble(existingHalf: SlabType) {
        primaryIsPositive = existingHalf == SlabType.TOP
        clearSlotMaterial(SECONDARY_SLOT)
    }

    fun cycleHalfMaterial(half: SlabType): Boolean =
        cycleSlotMaterial(slotForHalf(half))

    override fun write(view: ValueOutput, clientPacket: Boolean) {
        super.write(view, clientPacket)

        if (primaryIsPositive) {
            view.store("PrimaryPositive", Codec.BOOL, true)
        }
    }

    override fun read(view: ValueInput, clientPacket: Boolean) {
        val prevPrimaryPositive = primaryIsPositive
        super.read(view, clientPacket)

        primaryIsPositive = view.read("PrimaryPositive", Codec.BOOL).orElse(false)

        if (clientPacket && prevPrimaryPositive != primaryIsPositive) {
            syncVisuals()
        }
    }

    override fun readLegacySlot(view: ValueInput, slot: Int): SlotData? {
        if (slot != SECONDARY_SLOT) return null

        val material = view.read("TopMaterial", BlockState.CODEC).orElse(AllBlocks.COPYCAT_BASE.defaultBlockState())
        return SlotData(material)
    }
}
