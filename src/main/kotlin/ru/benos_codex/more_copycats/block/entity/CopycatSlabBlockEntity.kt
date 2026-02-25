package ru.benos_codex.more_copycats.block.entity

import com.zurrtum.create.AllBlocks
import com.zurrtum.create.content.contraptions.StructureTransform
import com.zurrtum.create.content.decoration.copycat.CopycatBlock
import com.zurrtum.create.content.decoration.copycat.CopycatBlockEntity
import com.zurrtum.create.content.schematics.requirement.ItemRequirement
import com.mojang.serialization.Codec
import net.minecraft.core.BlockPos
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.SlabType
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput

class CopycatSlabBlockEntity(pos: BlockPos, state: BlockState) : CopycatBlockEntity(pos, state) {
    private var topMaterial: BlockState = AllBlocks.COPYCAT_BASE.defaultBlockState()
    private var topConsumedItem: ItemStack = ItemStack.EMPTY
    private var primaryIsPositive: Boolean = false

    private enum class Slot {
        PRIMARY,
        SECONDARY
    }

    private fun slotForHalf(half: SlabType): Slot {
        if (half == SlabType.DOUBLE) return Slot.PRIMARY
        return if (primaryIsPositive) {
            if (half == SlabType.TOP) Slot.PRIMARY else Slot.SECONDARY
        } else {
            if (half == SlabType.BOTTOM) Slot.PRIMARY else Slot.SECONDARY
        }
    }

    fun getHalfMaterial(half: SlabType): BlockState =
        if (slotForHalf(half) == Slot.PRIMARY) material else topMaterial

    fun getHalfConsumedItem(half: SlabType): ItemStack =
        if (slotForHalf(half) == Slot.PRIMARY) consumedItem else topConsumedItem

    fun hasCustomMaterial(half: SlabType): Boolean =
        !getHalfMaterial(half).`is`(AllBlocks.COPYCAT_BASE)

    fun setHalfMaterial(half: SlabType, newMaterial: BlockState, sourceStack: ItemStack) {
        if (slotForHalf(half) == Slot.SECONDARY) {
            topMaterial = newMaterial
            topConsumedItem = sourceStack.copyWithCount(1)
            setChanged()
            notifyUpdate()
            return
        }

        material = newMaterial
        consumedItem = sourceStack
    }

    fun clearHalfMaterial(half: SlabType) {
        setHalfMaterial(half, AllBlocks.COPYCAT_BASE.defaultBlockState(), ItemStack.EMPTY)
    }

    fun clearTopConsumedItem() {
        topConsumedItem = ItemStack.EMPTY
        setChanged()
    }

    fun promoteToDouble(existingHalf: SlabType) {
        primaryIsPositive = existingHalf == SlabType.TOP
        topMaterial = AllBlocks.COPYCAT_BASE.defaultBlockState()
        topConsumedItem = ItemStack.EMPTY
        setChanged()
        notifyUpdate()
    }

    fun cycleHalfMaterial(half: SlabType): Boolean {
        if (slotForHalf(half) == Slot.SECONDARY) {
            val current = topMaterial
            if (current.`is`(AllBlocks.COPYCAT_BASE)) return false

            val cycled = when {
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
            } ?: return false

            topMaterial = cycled
            setChanged()
            notifyUpdate()
            return true
        }

        return cycleMaterial()
    }

    override fun hasCustomMaterial(): Boolean {
        return super.hasCustomMaterial() || hasCustomMaterial(SlabType.TOP)
    }

    override fun preRemoveSideEffects(pos: BlockPos, oldState: BlockState) {
        super.preRemoveSideEffects(pos, oldState)
        if (!topConsumedItem.isEmpty) {
            level?.let { Block.popResource(it, pos, topConsumedItem) }
        }
    }

    override fun transform(be: BlockEntity, transform: StructureTransform) {
        super.transform(be, transform)
        topMaterial = transform.apply(topMaterial)
        notifyUpdate()
    }

    override fun getRequiredItems(state: BlockState): ItemRequirement {
        val base = super.getRequiredItems(state)
        if (topConsumedItem.isEmpty) return base
        return base.union(ItemRequirement(ItemRequirement.ItemUseType.CONSUME, topConsumedItem))
    }

    override fun clearContent() {
        super.clearContent()
        topMaterial = AllBlocks.COPYCAT_BASE.defaultBlockState()
        topConsumedItem = ItemStack.EMPTY
        primaryIsPositive = false
    }

    override fun write(view: ValueOutput, clientPacket: Boolean) {
        super.write(view, clientPacket)

        if (!topConsumedItem.isEmpty) {
            view.store("TopItem", ItemStack.CODEC, topConsumedItem)
        }
        view.store("TopMaterial", BlockState.CODEC, topMaterial)
        if (primaryIsPositive) {
            view.store("PrimaryPositive", Codec.BOOL, true)
        }
    }

    override fun read(view: ValueInput, clientPacket: Boolean) {
        val prevTopMaterial = topMaterial
        val prevPrimaryPositive = primaryIsPositive
        super.read(view, clientPacket)

        topConsumedItem = view.read("TopItem", ItemStack.CODEC).orElse(ItemStack.EMPTY)
        topMaterial = view.read("TopMaterial", BlockState.CODEC).orElse(AllBlocks.COPYCAT_BASE.defaultBlockState())
        primaryIsPositive = view.read("PrimaryPositive", Codec.BOOL).orElse(false)

        if (topMaterial.`is`(AllBlocks.COPYCAT_BASE)) {
            topConsumedItem = ItemStack.EMPTY
        }

        if (!clientPacket) {
            val wrapperState = blockState
            val currentLevel = level ?: return
            if (wrapperState.block is CopycatBlock) {
                val accepted = (wrapperState.block as CopycatBlock).getAcceptedBlockState(currentLevel, worldPosition, topConsumedItem, null)
                if (accepted == null || !topMaterial.`is`(accepted.block)) {
                    topMaterial = AllBlocks.COPYCAT_BASE.defaultBlockState()
                    topConsumedItem = ItemStack.EMPTY
                }
            }
        }

        if (clientPacket && (prevTopMaterial != topMaterial || prevPrimaryPositive != primaryIsPositive)) {
            level?.sendBlockUpdated(worldPosition, blockState, blockState, 16)
        }
    }
}
