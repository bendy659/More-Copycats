package ru.benos_codex.more_copycats.block.entity

import com.mojang.serialization.Codec
import com.zurrtum.create.AllBlocks
import net.minecraft.core.BlockPos
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import ru.benos_codex.more_copycats.block.CopycatFenceBlock
import ru.benos_codex.more_copycats.block.CopycatWallBlock

class CopycatFenceWallBlockEntity(pos: BlockPos, state: BlockState) :
    AbstractMultiMaterialCopycatBlockEntity(pos, state, 9) {

    enum class Slot {
        PRIMARY,
        SECONDARY,
        TERTIARY
    }

    enum class WallSlot {
        POST,
        NORTH,
        EAST,
        SOUTH,
        WEST
    }

    enum class FenceSlot {
        POST,
        NORTH_TOP,
        NORTH_BOTTOM,
        EAST_TOP,
        EAST_BOTTOM,
        SOUTH_TOP,
        SOUTH_BOTTOM,
        WEST_TOP,
        WEST_BOTTOM
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

    fun getSlotMaterial(slot: WallSlot): BlockState =
        getSlotMaterial(slot.ordinal)

    fun getSlotConsumedItem(slot: WallSlot): ItemStack =
        getSlotConsumedItem(slot.ordinal)

    fun hasCustomMaterial(slot: WallSlot): Boolean =
        hasCustomMaterial(slot.ordinal)

    fun setSlotMaterial(slot: WallSlot, newMaterial: BlockState, sourceStack: ItemStack) {
        setSlotMaterial(slot.ordinal, newMaterial, sourceStack)
    }

    fun clearSlotMaterial(slot: WallSlot) {
        clearSlotMaterial(slot.ordinal)
    }

    fun cycleSlotMaterial(slot: WallSlot): Boolean =
        cycleSlotMaterial(slot.ordinal)

    fun getSlotMaterial(slot: FenceSlot): BlockState =
        getSlotMaterial(slot.ordinal)

    fun getSlotConsumedItem(slot: FenceSlot): ItemStack =
        getSlotConsumedItem(slot.ordinal)

    fun hasCustomMaterial(slot: FenceSlot): Boolean =
        hasCustomMaterial(slot.ordinal)

    fun setSlotMaterial(slot: FenceSlot, newMaterial: BlockState, sourceStack: ItemStack) {
        setSlotMaterial(slot.ordinal, newMaterial, sourceStack)
    }

    fun clearSlotMaterial(slot: FenceSlot) {
        clearSlotMaterial(slot.ordinal)
    }

    fun cycleSlotMaterial(slot: FenceSlot): Boolean =
        cycleSlotMaterial(slot.ordinal)

    override fun write(view: ValueOutput, clientPacket: Boolean) {
        super.write(view, clientPacket)
        if (blockState.block is CopycatWallBlock) {
            view.store(WALL_LAYOUT_VERSION_KEY, Codec.INT, WALL_LAYOUT_VERSION_DIRECTIONAL)
        }
        if (blockState.block is CopycatFenceBlock) {
            view.store(FENCE_LAYOUT_VERSION_KEY, Codec.INT, FENCE_LAYOUT_VERSION_DIRECTIONAL)
        }
    }

    override fun read(view: ValueInput, clientPacket: Boolean) {
        val migrateSharedWallSides = blockState.block is CopycatWallBlock &&
            view.read(WALL_LAYOUT_VERSION_KEY, Codec.INT).orElse(WALL_LAYOUT_VERSION_SHARED) < WALL_LAYOUT_VERSION_DIRECTIONAL
        val migrateSharedFenceRails = blockState.block is CopycatFenceBlock &&
            view.read(FENCE_LAYOUT_VERSION_KEY, Codec.INT).orElse(FENCE_LAYOUT_VERSION_SHARED) < FENCE_LAYOUT_VERSION_DIRECTIONAL

        super.read(view, clientPacket)

        if (migrateSharedWallSides) {
            val sharedSideMaterial = getStoredSlotMaterial(WallSlot.NORTH.ordinal)
            if (!sharedSideMaterial.`is`(AllBlocks.COPYCAT_BASE)) {
                for (slot in listOf(WallSlot.EAST, WallSlot.SOUTH, WallSlot.WEST)) {
                    storeExtraSlot(slot.ordinal, sharedSideMaterial, update = false)
                }
            }
        }

        if (migrateSharedFenceRails) {
            val sharedTopMaterial = getStoredSlotMaterial(FenceSlot.NORTH_TOP.ordinal)
            if (!sharedTopMaterial.`is`(AllBlocks.COPYCAT_BASE)) {
                for (slot in listOf(FenceSlot.EAST_TOP, FenceSlot.SOUTH_TOP, FenceSlot.WEST_TOP)) {
                    storeExtraSlot(slot.ordinal, sharedTopMaterial, update = false)
                }
            }

            val sharedBottomMaterial = getStoredSlotMaterial(FenceSlot.NORTH_BOTTOM.ordinal)
            if (!sharedBottomMaterial.`is`(AllBlocks.COPYCAT_BASE)) {
                for (slot in listOf(FenceSlot.EAST_BOTTOM, FenceSlot.SOUTH_BOTTOM, FenceSlot.WEST_BOTTOM)) {
                    storeExtraSlot(slot.ordinal, sharedBottomMaterial, update = false)
                }
            }
        }

        if (migrateSharedWallSides || migrateSharedFenceRails) {
            if (clientPacket) {
                syncVisuals()
            } else {
                setChanged()
            }
        }
    }

    override fun readLegacySlot(view: ValueInput, slot: Int): SlotData? {
        val materialKey = when (slot) {
            Slot.SECONDARY.ordinal -> "SecondaryMaterial"
            Slot.TERTIARY.ordinal -> "TertiaryMaterial"
            else -> return null
        }

        val material = view.read(materialKey, BlockState.CODEC).orElse(AllBlocks.COPYCAT_BASE.defaultBlockState())
        return SlotData(material)
    }

    private companion object {
        const val WALL_LAYOUT_VERSION_KEY = "WallLayoutVersion"
        const val WALL_LAYOUT_VERSION_SHARED = 1
        const val WALL_LAYOUT_VERSION_DIRECTIONAL = 2
        const val FENCE_LAYOUT_VERSION_KEY = "FenceLayoutVersion"
        const val FENCE_LAYOUT_VERSION_SHARED = 1
        const val FENCE_LAYOUT_VERSION_DIRECTIONAL = 2
    }
}
