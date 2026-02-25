package ru.benos_codex.more_copycats.block.entity

import com.zurrtum.create.AllBlocks
import com.zurrtum.create.content.contraptions.StructureTransform
import com.zurrtum.create.content.decoration.copycat.CopycatBlockEntity
import com.zurrtum.create.content.schematics.requirement.ItemRequirement
import net.minecraft.core.BlockPos
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput

class CopycatFenceWallBlockEntity(pos: BlockPos, state: BlockState) : CopycatBlockEntity(pos, state) {
    enum class Slot {
        PRIMARY,
        SECONDARY,
        TERTIARY
    }

    private var secondaryMaterial: BlockState = AllBlocks.COPYCAT_BASE.defaultBlockState()
    private var tertiaryMaterial: BlockState = AllBlocks.COPYCAT_BASE.defaultBlockState()
    private var secondaryConsumedItem: ItemStack = ItemStack.EMPTY
    private var tertiaryConsumedItem: ItemStack = ItemStack.EMPTY

    private fun syncVisuals() {
        val currentLevel = level ?: return
        val state = blockState
        currentLevel.sendBlockUpdated(worldPosition, state, state, 16)
    }

    fun getSlotMaterial(slot: Slot): BlockState = when (slot) {
        Slot.PRIMARY -> material
        Slot.SECONDARY -> secondaryMaterial
        Slot.TERTIARY -> tertiaryMaterial
    }

    fun getSlotConsumedItem(slot: Slot): ItemStack = when (slot) {
        Slot.PRIMARY -> consumedItem
        Slot.SECONDARY -> secondaryConsumedItem
        Slot.TERTIARY -> tertiaryConsumedItem
    }

    fun hasCustomMaterial(slot: Slot): Boolean =
        !getSlotMaterial(slot).`is`(AllBlocks.COPYCAT_BASE)

    override fun hasCustomMaterial(): Boolean =
        super.hasCustomMaterial() || hasCustomMaterial(Slot.SECONDARY) || hasCustomMaterial(Slot.TERTIARY)

    fun setSlotMaterial(slot: Slot, newMaterial: BlockState, sourceStack: ItemStack) {
        when (slot) {
            Slot.PRIMARY -> {
                setMaterial(newMaterial)
                setConsumedItem(sourceStack)
            }

            Slot.SECONDARY -> {
                secondaryMaterial = newMaterial
                secondaryConsumedItem = sourceStack.copyWithCount(1)
                setChanged()
                notifyUpdate()
                syncVisuals()
            }

            Slot.TERTIARY -> {
                tertiaryMaterial = newMaterial
                tertiaryConsumedItem = sourceStack.copyWithCount(1)
                setChanged()
                notifyUpdate()
                syncVisuals()
            }
        }
    }

    fun clearSlotMaterial(slot: Slot) {
        setSlotMaterial(slot, AllBlocks.COPYCAT_BASE.defaultBlockState(), ItemStack.EMPTY)
    }

    fun cycleSlotMaterial(slot: Slot): Boolean {
        if (slot == Slot.PRIMARY) {
            return cycleMaterial()
        }

        val current = getSlotMaterial(slot)
        if (current.`is`(AllBlocks.COPYCAT_BASE)) return false
        val cycled = rotateMaterial(current) ?: return false

        when (slot) {
            Slot.PRIMARY -> {}
            Slot.SECONDARY -> secondaryMaterial = cycled
            Slot.TERTIARY -> tertiaryMaterial = cycled
        }

        setChanged()
        notifyUpdate()
        syncVisuals()
        return true
    }

    private fun rotateMaterial(current: BlockState): BlockState? {
        return when {
            current.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING) ->
                current.cycle(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING)

            current.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING) ->
                current.setValue(
                    net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING,
                    current.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING).clockWise
                )

            current.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS) ->
                current.cycle(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS)

            current.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_AXIS) ->
                current.cycle(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_AXIS)

            current.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT) ->
                current.cycle(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT)

            else -> null
        }
    }

    override fun preRemoveSideEffects(pos: BlockPos, oldState: BlockState) {
        super.preRemoveSideEffects(pos, oldState)
        val currentLevel = level ?: return
        if (!secondaryConsumedItem.isEmpty) {
            Block.popResource(currentLevel, pos, secondaryConsumedItem)
        }
        if (!tertiaryConsumedItem.isEmpty) {
            Block.popResource(currentLevel, pos, tertiaryConsumedItem)
        }
    }

    override fun getRequiredItems(state: BlockState): ItemRequirement {
        var required = super.getRequiredItems(state)
        if (!secondaryConsumedItem.isEmpty) {
            required = required.union(ItemRequirement(ItemRequirement.ItemUseType.CONSUME, secondaryConsumedItem))
        }
        if (!tertiaryConsumedItem.isEmpty) {
            required = required.union(ItemRequirement(ItemRequirement.ItemUseType.CONSUME, tertiaryConsumedItem))
        }
        return required
    }

    override fun transform(be: BlockEntity, transform: StructureTransform) {
        super.transform(be, transform)
        secondaryMaterial = transform.apply(secondaryMaterial)
        tertiaryMaterial = transform.apply(tertiaryMaterial)
        notifyUpdate()
    }

    override fun clearContent() {
        super.clearContent()
        secondaryMaterial = AllBlocks.COPYCAT_BASE.defaultBlockState()
        tertiaryMaterial = AllBlocks.COPYCAT_BASE.defaultBlockState()
        secondaryConsumedItem = ItemStack.EMPTY
        tertiaryConsumedItem = ItemStack.EMPTY
    }

    override fun write(view: ValueOutput, clientPacket: Boolean) {
        super.write(view, clientPacket)

        if (!secondaryConsumedItem.isEmpty) {
            view.store("SecondaryItem", ItemStack.CODEC, secondaryConsumedItem)
        }
        view.store("SecondaryMaterial", BlockState.CODEC, secondaryMaterial)

        if (!tertiaryConsumedItem.isEmpty) {
            view.store("TertiaryItem", ItemStack.CODEC, tertiaryConsumedItem)
        }
        view.store("TertiaryMaterial", BlockState.CODEC, tertiaryMaterial)
    }

    override fun read(view: ValueInput, clientPacket: Boolean) {
        val prevSecondary = secondaryMaterial
        val prevTertiary = tertiaryMaterial
        super.read(view, clientPacket)

        secondaryConsumedItem = view.read("SecondaryItem", ItemStack.CODEC).orElse(ItemStack.EMPTY)
        secondaryMaterial = view.read("SecondaryMaterial", BlockState.CODEC).orElse(AllBlocks.COPYCAT_BASE.defaultBlockState())

        tertiaryConsumedItem = view.read("TertiaryItem", ItemStack.CODEC).orElse(ItemStack.EMPTY)
        tertiaryMaterial = view.read("TertiaryMaterial", BlockState.CODEC).orElse(AllBlocks.COPYCAT_BASE.defaultBlockState())

        if (secondaryMaterial.`is`(AllBlocks.COPYCAT_BASE)) {
            secondaryConsumedItem = ItemStack.EMPTY
        }
        if (tertiaryMaterial.`is`(AllBlocks.COPYCAT_BASE)) {
            tertiaryConsumedItem = ItemStack.EMPTY
        }

        if (clientPacket && (prevSecondary != secondaryMaterial || prevTertiary != tertiaryMaterial)) {
            syncVisuals()
        }
    }
}
