package ru.benos_codex.more_copycats

import com.zurrtum.create.content.decoration.copycat.CopycatBlockEntity
import net.fabricmc.fabric.api.`object`.builder.v1.block.entity.FabricBlockEntityTypeBuilder
import net.minecraft.core.BlockPos
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.network.chat.Component
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import ru.benos_codex.more_copycats.MoreCopycats.DEFAULT_PROPERTIES
import ru.benos_codex.more_copycats.MoreCopycats.mrl
import ru.benos_codex.more_copycats.block.CopycatBiteBlock
import ru.benos_codex.more_copycats.block.CopycatByteBlock
import ru.benos_codex.more_copycats.block.CopycatBiteSimpleBlock
import ru.benos_codex.more_copycats.block.CopycatByteSimpleBlock
import ru.benos_codex.more_copycats.block.CopycatSlabBlock
import ru.benos_codex.more_copycats.block.CopycatStairsBlock
import ru.benos_codex.more_copycats.block.CopycatButtonBlock
import ru.benos_codex.more_copycats.block.CopycatPressurePlateBlock
import ru.benos_codex.more_copycats.block.CopycatDoorBlock
import ru.benos_codex.more_copycats.block.CopycatFenceBlock
import ru.benos_codex.more_copycats.block.CopycatSlidingDoorBlock
import ru.benos_codex.more_copycats.block.CopycatTrapdoorBlock
import ru.benos_codex.more_copycats.block.CopycatVerticalStepBlock
import ru.benos_codex.more_copycats.block.CopycatWallBlock
import ru.benos_codex.more_copycats.block.entity.CopycatBiteBlockEntity
import ru.benos_codex.more_copycats.block.entity.CopycatByteBlockEntity
import ru.benos_codex.more_copycats.block.entity.CopycatFenceWallBlockEntity
import ru.benos_codex.more_copycats.block.entity.CopycatRedstoneBlockEntity
import ru.benos_codex.more_copycats.block.entity.CopycatSlabBlockEntity
import ru.benos_codex.more_copycats.block.entity.CopycatSlidingDoorBlockEntity
import ru.benos_codex.more_copycats.item.block.CopycatBiteBlockItem
import ru.benos_codex.more_copycats.item.block.CopycatByteBlockItem
import ru.benos_codex.more_copycats.item.block.CopycatSlabPlacementItem
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup
import ru.benos_codex.more_copycats.item.CopycatUvToolItem
import ru.benos_codex.more_copycats.menu.CopycatUvToolMenu
import ru.benos_codex.more_copycats.menu.UvToolOpenData

@Suppress("unused")
object MoreCopycatsRegister {
    // copycat_byte_block //
    val BYTE_BLOCK: CopycatByteBlock   = registerBlock("byte", ::CopycatByteBlock)
    val BYTE_ITEM : BlockItem          = registerBlockItem("byte") { props ->
        CopycatByteBlockItem(BYTE_BLOCK, props)
    }
    val BYTE_BE   : BlockEntityType<CopycatByteBlockEntity> = registerBlockEntity("byte", BYTE_BLOCK) { pos, state ->
        CopycatBlockEntityTypeHelper.OVERRIDE.set(BYTE_BE)

        try { CopycatByteBlockEntity(pos, state) } finally { CopycatBlockEntityTypeHelper.OVERRIDE.remove() }
    }

    // copycat_byte_simple_block //
    val BYTE_SIMPLE_BLOCK: CopycatByteSimpleBlock = registerBlock("byte_simple", ::CopycatByteSimpleBlock)
    val BYTE_SIMPLE_ITEM : BlockItem             = registerBlockItem("byte_simple") { props ->
        CopycatByteBlockItem(BYTE_SIMPLE_BLOCK, props)
    }
    val BYTE_SIMPLE_BE   : BlockEntityType<CopycatByteBlockEntity> = registerBlockEntity("byte_simple", BYTE_SIMPLE_BLOCK) { pos, state ->
        CopycatBlockEntityTypeHelper.OVERRIDE.set(BYTE_SIMPLE_BE)

        try { CopycatByteBlockEntity(pos, state) } finally { CopycatBlockEntityTypeHelper.OVERRIDE.remove() }
    }

    // copycat_bite_block //
    val BITE_BLOCK: CopycatBiteBlock   = registerBlock("bite", ::CopycatBiteBlock)
    val BITE_ITEM : BlockItem          = registerBlockItem("bite") { props ->
        CopycatBiteBlockItem(BITE_BLOCK, props)
    }
    val BITE_BE   : BlockEntityType<CopycatBiteBlockEntity> = registerBlockEntity("bite", BITE_BLOCK) { pos, state ->
        CopycatBlockEntityTypeHelper.OVERRIDE.set(BITE_BE)

        try { CopycatBiteBlockEntity(pos, state) } finally { CopycatBlockEntityTypeHelper.OVERRIDE.remove() }
    }

    // copycat_bite_simple_block //
    val BITE_SIMPLE_BLOCK: CopycatBiteSimpleBlock = registerBlock("bite_simple", ::CopycatBiteSimpleBlock)
    val BITE_SIMPLE_ITEM : BlockItem             = registerBlockItem("bite_simple") { props ->
        CopycatBiteBlockItem(BITE_SIMPLE_BLOCK, props)
    }
    val BITE_SIMPLE_BE   : BlockEntityType<CopycatBiteBlockEntity> = registerBlockEntity("bite_simple", BITE_SIMPLE_BLOCK) { pos, state ->
        CopycatBlockEntityTypeHelper.OVERRIDE.set(BITE_SIMPLE_BE)

        try { CopycatBiteBlockEntity(pos, state) } finally { CopycatBlockEntityTypeHelper.OVERRIDE.remove() }
    }

    // copycat_uv_tool //
    val UV_TOOL: Item = registerItem("copycat_uv_tool") { props ->
        CopycatUvToolItem(props)
    }
    val UV_TOOL_MENU: ExtendedScreenHandlerType<CopycatUvToolMenu, UvToolOpenData> =
        registerUvMenu("copycat_uv_tool")

    // simple copycat-shaped placeholders //
    val STAIRS_BLOCK: CopycatStairsBlock = registerBlock("stairs", ::CopycatStairsBlock)
    val STAIRS_ITEM: BlockItem = registerBlockItem("stairs") { props -> BlockItem(STAIRS_BLOCK, props) }

    val VERTICAL_STEP_BLOCK: CopycatVerticalStepBlock = registerBlock("vertical_step", ::CopycatVerticalStepBlock)
    val VERTICAL_STEP_ITEM: BlockItem = registerBlockItem("vertical_step") { props -> BlockItem(VERTICAL_STEP_BLOCK, props) }

    val BUTTON_BLOCK: CopycatButtonBlock = registerBlock("button", ::CopycatButtonBlock)
    val BUTTON_ITEM: BlockItem = registerBlockItem("button") { props -> BlockItem(BUTTON_BLOCK, props) }

    val PRESSURE_PLATE_BLOCK: CopycatPressurePlateBlock = registerBlock("pressure_plate", ::CopycatPressurePlateBlock)
    val PRESSURE_PLATE_ITEM: BlockItem = registerBlockItem("pressure_plate") { props -> BlockItem(PRESSURE_PLATE_BLOCK, props) }

    val REDSTONE_BE: BlockEntityType<CopycatRedstoneBlockEntity> = registerBlockEntity("redstone", BUTTON_BLOCK, PRESSURE_PLATE_BLOCK) { pos, state ->
        CopycatBlockEntityTypeHelper.OVERRIDE.set(REDSTONE_BE)
        try { CopycatRedstoneBlockEntity(pos, state) } finally { CopycatBlockEntityTypeHelper.OVERRIDE.remove() }
    }

    val SLAB_BLOCK: CopycatSlabBlock = registerBlock("slab", ::CopycatSlabBlock)
    val SLAB_ITEM: BlockItem = registerBlockItem("slab") { props ->
        CopycatSlabPlacementItem(SLAB_BLOCK, props, CopycatSlabPlacementItem.Mode.HORIZONTAL)
    }
    val VERTICAL_SLAB_ITEM: BlockItem = registerBlockItem("vertical_slab") { props ->
        CopycatSlabPlacementItem(SLAB_BLOCK, props, CopycatSlabPlacementItem.Mode.VERTICAL)
    }
    val SLAB_BE: BlockEntityType<CopycatSlabBlockEntity> = registerBlockEntity("slab", SLAB_BLOCK) { pos, state ->
        CopycatBlockEntityTypeHelper.OVERRIDE.set(SLAB_BE)
        try { CopycatSlabBlockEntity(pos, state) } finally { CopycatBlockEntityTypeHelper.OVERRIDE.remove() }
    }

    val DOOR_BLOCK: CopycatDoorBlock = registerBlock("door", ::CopycatDoorBlock)
    val DOOR_ITEM: BlockItem = registerBlockItem("door") { props -> BlockItem(DOOR_BLOCK, props) }

    val SLIDING_DOOR_BLOCK: CopycatSlidingDoorBlock = registerBlock("sliding_door", ::CopycatSlidingDoorBlock)
    val SLIDING_DOOR_ITEM: BlockItem = registerBlockItem("sliding_door") { props -> BlockItem(SLIDING_DOOR_BLOCK, props) }
    val SLIDING_DOOR_BE: BlockEntityType<CopycatSlidingDoorBlockEntity> = registerBlockEntity("sliding_door", SLIDING_DOOR_BLOCK) { pos, state ->
        SlidingDoorBlockEntityTypeHelper.OVERRIDE.set(SLIDING_DOOR_BE)
        try { CopycatSlidingDoorBlockEntity(pos, state) } finally { SlidingDoorBlockEntityTypeHelper.OVERRIDE.remove() }
    }

    val TRAPDOOR_BLOCK: CopycatTrapdoorBlock = registerBlock("trapdoor", ::CopycatTrapdoorBlock)
    val TRAPDOOR_ITEM: BlockItem = registerBlockItem("trapdoor") { props -> BlockItem(TRAPDOOR_BLOCK, props) }

    val FENCE_BLOCK: CopycatFenceBlock = registerBlock("fence", ::CopycatFenceBlock)
    val FENCE_ITEM: BlockItem = registerBlockItem("fence") { props -> BlockItem(FENCE_BLOCK, props) }

    val WALL_BLOCK: CopycatWallBlock = registerBlock("wall", ::CopycatWallBlock)
    val WALL_ITEM: BlockItem = registerBlockItem("wall") { props -> BlockItem(WALL_BLOCK, props) }

    val FENCE_WALL_BE: BlockEntityType<CopycatFenceWallBlockEntity> = registerBlockEntity("fence_wall", FENCE_BLOCK, WALL_BLOCK) { pos, state ->
        CopycatBlockEntityTypeHelper.OVERRIDE.set(FENCE_WALL_BE)
        try { CopycatFenceWallBlockEntity(pos, state) } finally { CopycatBlockEntityTypeHelper.OVERRIDE.remove() }
    }

    val SIMPLE_BE: BlockEntityType<CopycatBlockEntity> = registerBlockEntity(
        "simple",
        STAIRS_BLOCK,
        VERTICAL_STEP_BLOCK,
        DOOR_BLOCK,
        TRAPDOOR_BLOCK
    ) { pos, state ->
        CopycatBlockEntityTypeHelper.OVERRIDE.set(SIMPLE_BE)
        try { CopycatBlockEntity(pos, state) } finally { CopycatBlockEntityTypeHelper.OVERRIDE.remove() }
    }

    val CREATIVE_TAB: CreativeModeTab = registerCreativeTab()

    val init: Unit
        get() {
            // Access a registered entry to keep explicit init call meaningful.
            BYTE_BLOCK
        }

    private fun <B: Block> registerBlock(id: String, block: (BlockBehaviour.Properties) -> B): B {
        val key = ResourceKey.create(Registries.BLOCK, id.mrl)
        val props = DEFAULT_PROPERTIES.setId(key)

        return Registry.register(BuiltInRegistries.BLOCK, key, block(props))
    }

    private fun registerBlockItem(id: String, item: (Item.Properties) -> BlockItem): BlockItem {
        val key = ResourceKey.create(Registries.ITEM, id.mrl)
        val props = Item.Properties().setId(key)

        return Registry.register(BuiltInRegistries.ITEM, key, item(props))
    }

    private fun registerItem(id: String, item: (Item.Properties) -> Item): Item {
        val key = ResourceKey.create(Registries.ITEM, id.mrl)
        val props = Item.Properties().setId(key)

        return Registry.register(BuiltInRegistries.ITEM, key, item(props))
    }

    fun <BE : BlockEntity> registerBlockEntity(id: String, vararg blocks: Block, factory: (BlockPos, BlockState) -> BE): BlockEntityType<BE> {
        val key = ResourceKey.create(Registries.BLOCK_ENTITY_TYPE, id.mrl)
        val type = FabricBlockEntityTypeBuilder.create(factory, *blocks).build()

        return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, key, type)
    }

    private fun registerUvMenu(id: String): ExtendedScreenHandlerType<CopycatUvToolMenu, UvToolOpenData> {
        val key = ResourceKey.create(Registries.MENU, id.mrl)
        val type = ExtendedScreenHandlerType(::CopycatUvToolMenu, UvToolOpenData.CODEC)
        return Registry.register(BuiltInRegistries.MENU, key, type)
    }

    private fun registerCreativeTab(): CreativeModeTab {
        val key = ResourceKey.create(Registries.CREATIVE_MODE_TAB, "more_copycats".mrl)
        val tab = FabricItemGroup.builder()
            .title(Component.translatable("itemGroup.more_copycats"))
            .icon { ItemStack(STAIRS_ITEM) }
            .displayItems { _, entries ->
                entries.accept(STAIRS_ITEM)
                entries.accept(VERTICAL_STEP_ITEM)
                entries.accept(BUTTON_ITEM)
                entries.accept(PRESSURE_PLATE_ITEM)
                entries.accept(SLAB_ITEM)
                entries.accept(VERTICAL_SLAB_ITEM)
                entries.accept(DOOR_ITEM)
                entries.accept(SLIDING_DOOR_ITEM)
                entries.accept(TRAPDOOR_ITEM)
                entries.accept(FENCE_ITEM)
                entries.accept(WALL_ITEM)
            }
            .build()

        return Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, key, tab)
    }

}
