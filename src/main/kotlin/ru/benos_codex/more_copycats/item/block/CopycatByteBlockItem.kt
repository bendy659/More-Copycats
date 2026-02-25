package ru.benos_codex.more_copycats.item.block

import net.minecraft.world.item.Item.Properties

import net.minecraft.world.InteractionResult
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.block.Block
import ru.benos_codex.more_copycats.block.CopycatByteBlock

class CopycatByteBlockItem(block: Block, props: Properties) : BlockItem(block, props) {
    override fun place(context: BlockPlaceContext): InteractionResult {
        CopycatByteBlock.PLACEMENT_HIT.set(context.clickLocation)
        return try {
            super.place(context)
        } finally {
            CopycatByteBlock.PLACEMENT_HIT.remove()
        }
    }
}