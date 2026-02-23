package ru.benos_codex.more_copycats.block.entity

import com.zurrtum.create.AllBlocks
import com.zurrtum.create.content.decoration.slidingDoor.SlidingDoorBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.DoorBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput

class CopycatSlidingDoorBlockEntity(pos: BlockPos, state: BlockState) : SlidingDoorBlockEntity(pos, state) {
    private var lowerMaterial: BlockState = AllBlocks.COPYCAT_BASE.defaultBlockState()
    private var upperMaterial: BlockState = AllBlocks.COPYCAT_BASE.defaultBlockState()
    private var lowerConsumedItem: ItemStack = ItemStack.EMPTY
    private var upperConsumedItem: ItemStack = ItemStack.EMPTY

    fun getMaterialState(half: DoubleBlockHalf): BlockState =
        if (half == DoubleBlockHalf.UPPER) upperMaterial else lowerMaterial

    fun getMaterialState(): BlockState = getMaterialState(DoubleBlockHalf.LOWER)

    fun hasCustomMaterial(half: DoubleBlockHalf): Boolean =
        !getMaterialState(half).`is`(AllBlocks.COPYCAT_BASE)

    fun hasCustomMaterial(): Boolean =
        hasCustomMaterial(DoubleBlockHalf.LOWER) || hasCustomMaterial(DoubleBlockHalf.UPPER)

    fun getConsumedItemStack(half: DoubleBlockHalf): ItemStack =
        if (half == DoubleBlockHalf.UPPER) upperConsumedItem else lowerConsumedItem

    fun getConsumedItemStack(): ItemStack = getConsumedItemStack(DoubleBlockHalf.LOWER)

    fun setMaterialState(half: DoubleBlockHalf, state: BlockState) {
        if (half == DoubleBlockHalf.UPPER) upperMaterial = state else lowerMaterial = state
        setChanged()
        notifyUpdate()
        syncVisuals()
    }

    fun setMaterialState(state: BlockState) = setMaterialState(DoubleBlockHalf.LOWER, state)

    fun setConsumedItemStack(half: DoubleBlockHalf, stack: ItemStack) {
        val copied = stack.copyWithCount(1)
        if (half == DoubleBlockHalf.UPPER) upperConsumedItem = copied else lowerConsumedItem = copied
        setChanged()
        notifyUpdate()
        syncVisuals()
    }

    fun setConsumedItemStack(stack: ItemStack) = setConsumedItemStack(DoubleBlockHalf.LOWER, stack)

    fun setHalfMaterial(half: DoubleBlockHalf, material: BlockState, stack: ItemStack) {
        if (half == DoubleBlockHalf.UPPER) {
            upperMaterial = material
            upperConsumedItem = stack.copyWithCount(1)
        } else {
            lowerMaterial = material
            lowerConsumedItem = stack.copyWithCount(1)
        }
        setChanged()
        notifyUpdate()
        syncVisuals()
    }

    fun clearMaterial(half: DoubleBlockHalf) {
        if (half == DoubleBlockHalf.UPPER) {
            upperMaterial = AllBlocks.COPYCAT_BASE.defaultBlockState()
            upperConsumedItem = ItemStack.EMPTY
        } else {
            lowerMaterial = AllBlocks.COPYCAT_BASE.defaultBlockState()
            lowerConsumedItem = ItemStack.EMPTY
        }
        setChanged()
        notifyUpdate()
        syncVisuals()
    }

    fun clearMaterial() = clearMaterial(DoubleBlockHalf.LOWER)

    fun clearAllMaterials() {
        lowerMaterial = AllBlocks.COPYCAT_BASE.defaultBlockState()
        upperMaterial = AllBlocks.COPYCAT_BASE.defaultBlockState()
        lowerConsumedItem = ItemStack.EMPTY
        upperConsumedItem = ItemStack.EMPTY
        setChanged()
        notifyUpdate()
        syncVisuals()
    }

    fun cycleMaterial(half: DoubleBlockHalf): Boolean {
        val current = getMaterialState(half)
        if (current.`is`(AllBlocks.COPYCAT_BASE)) return false
        val cycled = rotateMaterial(current) ?: return false

        if (half == DoubleBlockHalf.UPPER) upperMaterial = cycled else lowerMaterial = cycled
        setChanged()
        notifyUpdate()
        syncVisuals()
        return true
    }

    fun cycleMaterial(): Boolean = cycleMaterial(DoubleBlockHalf.LOWER)

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

    private fun syncVisuals() {
        val currentLevel = level ?: return
        val beState = blockState
        val lowerPos = if (beState.hasProperty(DoorBlock.HALF) && beState.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) {
            worldPosition.below()
        } else {
            worldPosition
        }

        val lowerState = currentLevel.getBlockState(lowerPos)
        currentLevel.sendBlockUpdated(lowerPos, lowerState, lowerState, 8)

        val upperPos = lowerPos.above()
        val upperState = currentLevel.getBlockState(upperPos)
        if (upperState.block == lowerState.block &&
            upperState.hasProperty(DoorBlock.HALF) &&
            upperState.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER
        ) {
            currentLevel.sendBlockUpdated(upperPos, upperState, upperState, 8)
        }
    }

    override fun write(view: ValueOutput, clientPacket: Boolean) {
        super.write(view, clientPacket)
        if (!lowerConsumedItem.isEmpty) {
            view.store("LowerItem", ItemStack.CODEC, lowerConsumedItem)
        }
        view.store("LowerMaterial", BlockState.CODEC, lowerMaterial)

        if (!upperConsumedItem.isEmpty) {
            view.store("UpperItem", ItemStack.CODEC, upperConsumedItem)
        }
        view.store("UpperMaterial", BlockState.CODEC, upperMaterial)
    }

    override fun read(view: ValueInput, clientPacket: Boolean) {
        val prevLowerMaterial = lowerMaterial
        val prevUpperMaterial = upperMaterial
        super.read(view, clientPacket)

        lowerConsumedItem = view.read("LowerItem", ItemStack.CODEC)
            .orElseGet {
                view.read("Item", ItemStack.CODEC).orElse(ItemStack.EMPTY)
            }
        lowerMaterial = view.read("LowerMaterial", BlockState.CODEC)
            .orElseGet {
                view.read("Material", BlockState.CODEC).orElse(AllBlocks.COPYCAT_BASE.defaultBlockState())
            }

        upperConsumedItem = view.read("UpperItem", ItemStack.CODEC).orElse(ItemStack.EMPTY)
        upperMaterial = view.read("UpperMaterial", BlockState.CODEC).orElse(AllBlocks.COPYCAT_BASE.defaultBlockState())

        if (lowerMaterial.`is`(AllBlocks.COPYCAT_BASE)) {
            lowerConsumedItem = ItemStack.EMPTY
        }
        if (upperMaterial.`is`(AllBlocks.COPYCAT_BASE)) {
            upperConsumedItem = ItemStack.EMPTY
        }

        if (clientPacket && (prevLowerMaterial != lowerMaterial || prevUpperMaterial != upperMaterial)) {
            syncVisuals()
        }
    }
}
