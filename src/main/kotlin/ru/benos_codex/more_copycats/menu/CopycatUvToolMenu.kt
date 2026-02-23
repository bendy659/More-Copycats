package ru.benos_codex.more_copycats.menu

import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.BlockItem
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.SimpleContainer
import ru.benos_codex.more_copycats.MoreCopycatsRegister

class CopycatUvToolMenu : AbstractContainerMenu {
    val pos: BlockPos
    val partIndex: Int
    val faceOrdinal: Int
    private val materialContainer = SimpleContainer(1)
    val materialSlot: Slot

    constructor(syncId: Int, inv: Inventory, data: UvToolOpenData) : super(MoreCopycatsRegister.UV_TOOL_MENU, syncId) {
        pos = data.pos
        partIndex = data.partIndex
        faceOrdinal = data.faceOrdinal
        materialSlot = addSlot(MaterialSlot(materialContainer, 0, 131, 18))
        addPlayerInventory(inv)
    }

    override fun stillValid(player: Player): Boolean = true

    override fun removed(player: Player) {
        super.removed(player)
        if (!player.level().isClientSide) {
            val stack = materialContainer.getItem(0)
            if (!stack.isEmpty) {
                player.inventory.placeItemBackInInventory(stack)
                materialContainer.setItem(0, ItemStack.EMPTY)
            }
        }
    }

    fun getMaterialState(): BlockState? {
        val stack = materialSlot.item
        val item = stack.item as? BlockItem ?: return null
        return item.block.defaultBlockState()
    }

    private fun addPlayerInventory(inv: Inventory) {
        val startX = 7
        val startY = 99
        val slotSize = 18

        for (row in 0 until 3) {
            for (col in 0 until 9) {
                val index = col + row * 9 + 9
                addSlot(Slot(inv, index, startX + col * slotSize, startY + row * slotSize))
            }
        }

        val hotbarY = 157
        for (col in 0 until 9) {
            addSlot(Slot(inv, col, startX + col * slotSize, hotbarY))
        }
    }

    override fun quickMoveStack(player: Player, i: Int): ItemStack = ItemStack.EMPTY

    private class MaterialSlot(container: SimpleContainer, index: Int, x: Int, y: Int) : Slot(container, index, x, y) {
        override fun mayPlace(stack: ItemStack): Boolean = stack.item is BlockItem
    }
}

data class UvToolOpenData(
    val pos: BlockPos,
    val partIndex: Int,
    val faceOrdinal: Int
) {
    companion object {
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, UvToolOpenData> = StreamCodec.of(
            { buf, value ->
                buf.writeBlockPos(value.pos)
                buf.writeVarInt(value.partIndex)
                buf.writeVarInt(value.faceOrdinal)
            },
            { buf ->
                UvToolOpenData(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt())
            }
        )
    }
}
