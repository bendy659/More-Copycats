package ru.benos_codex.more_copycats.client

import com.zurrtum.create.client.AllModels
import com.zurrtum.create.client.content.decoration.slidingDoor.SlidingDoorRenderer
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.resource.ResourceManagerHelper
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener
import net.fabricmc.fabric.impl.client.rendering.BlockRenderLayerMapImpl
import net.minecraft.client.gui.screens.MenuScreens
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers
import net.minecraft.client.renderer.chunk.ChunkSectionLayer
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.resources.ResourceManager
import ru.benos_codex.more_copycats.MoreCopycats
import ru.benos_codex.more_copycats.MoreCopycats.mrl
import ru.benos_codex.more_copycats.MoreCopycatsRegister
import ru.benos_codex.more_copycats.client.model.block.CopycatBiteBlockModel
import ru.benos_codex.more_copycats.client.model.block.CopycatByteBlockModel
import ru.benos_codex.more_copycats.client.model.block.CopycatBiteSimpleBlockModel
import ru.benos_codex.more_copycats.client.model.block.CopycatDoorBlockModel
import ru.benos_codex.more_copycats.client.model.block.CopycatByteSimpleBlockModel
import ru.benos_codex.more_copycats.client.model.block.CopycatButtonBlockModel
import ru.benos_codex.more_copycats.client.model.block.CopycatCogwheelBlockModel
import ru.benos_codex.more_copycats.client.model.block.CopycatFenceBlockModel
import ru.benos_codex.more_copycats.client.model.block.CopycatGearboxBlockModel
import ru.benos_codex.more_copycats.client.model.block.CopycatPressurePlateBlockModel
import ru.benos_codex.more_copycats.client.model.block.CopycatSlidingDoorBlockModel
import ru.benos_codex.more_copycats.client.model.block.CopycatSlabBlockModel
import ru.benos_codex.more_copycats.client.model.block.CopycatShaftBlockModel
import ru.benos_codex.more_copycats.client.model.block.CopycatStepBlockModel
import ru.benos_codex.more_copycats.client.model.block.CopycatStairsBlockModel
import ru.benos_codex.more_copycats.client.model.block.CopycatTrapdoorBlockModel
import ru.benos_codex.more_copycats.client.model.block.CopycatWallBlockModel
import ru.benos_codex.more_copycats.client.debug.CogwheelSafeZoneDebug
import ru.benos_codex.more_copycats.client.renderer.CopycatCogwheelRenderer
import ru.benos_codex.more_copycats.client.renderer.CopycatGearboxRenderer
import ru.benos_codex.more_copycats.client.renderer.CopycatRedstoneRenderer
import ru.benos_codex.more_copycats.client.renderer.CopycatShaftRenderer
import ru.benos_codex.more_copycats.client.screen.CopycatUvToolScreen
import ru.benos_codex.more_copycats.client.overlay.CogwheelFreeSlotOverlay
import ru.benos_codex.more_copycats.client.overlay.FencePartOverlay
import ru.benos_codex.more_copycats.client.overlay.GearboxPartOverlay
import ru.benos_codex.more_copycats.client.overlay.WallPartOverlay

@Suppress("unused")
object MoreCopycatsClient : ClientModInitializer {
    override fun onInitializeClient() {
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(object : SimpleSynchronousResourceReloadListener {
            override fun getFabricId() = "cogwheel_renderer_cache".mrl

            override fun onResourceManagerReload(manager: ResourceManager) {
                MoreCopycats.Config.load()
                CopycatCogwheelRenderer.clearModelCache()
                CopycatGearboxRenderer.clearModelCache()
                CopycatShaftRenderer.clearModelCache()
            }
        })

        // Register model //
        AllModels.register(MoreCopycatsRegister.BYTE_BLOCK, ::CopycatByteBlockModel)
        AllModels.register(MoreCopycatsRegister.BITE_BLOCK, ::CopycatBiteBlockModel)
        AllModels.register(MoreCopycatsRegister.BYTE_SIMPLE_BLOCK, ::CopycatByteSimpleBlockModel)
        AllModels.register(MoreCopycatsRegister.BITE_SIMPLE_BLOCK, ::CopycatBiteSimpleBlockModel)
        AllModels.register(MoreCopycatsRegister.STAIRS_BLOCK, ::CopycatStairsBlockModel)
        AllModels.register(MoreCopycatsRegister.STEP_BLOCK, ::CopycatStepBlockModel)
        AllModels.register(MoreCopycatsRegister.BUTTON_BLOCK, ::CopycatButtonBlockModel)
        AllModels.register(MoreCopycatsRegister.PRESSURE_PLATE_BLOCK, ::CopycatPressurePlateBlockModel)
        AllModels.register(MoreCopycatsRegister.SLAB_BLOCK, ::CopycatSlabBlockModel)
        AllModels.register(MoreCopycatsRegister.DOOR_BLOCK, ::CopycatDoorBlockModel)
        AllModels.register(MoreCopycatsRegister.SLIDING_DOOR_BLOCK, ::CopycatSlidingDoorBlockModel)
        AllModels.register(MoreCopycatsRegister.TRAPDOOR_BLOCK, ::CopycatTrapdoorBlockModel)
        AllModels.register(MoreCopycatsRegister.FENCE_BLOCK, ::CopycatFenceBlockModel)
        AllModels.register(MoreCopycatsRegister.WALL_BLOCK, ::CopycatWallBlockModel)
        AllModels.register(MoreCopycatsRegister.SHAFT_BLOCK, ::CopycatShaftBlockModel)
        AllModels.register(MoreCopycatsRegister.GEARBOX_SHAFT_BLOCK, ::CopycatShaftBlockModel)
        AllModels.register(MoreCopycatsRegister.ENCASED_SHAFT_BLOCK, ::CopycatShaftBlockModel)
        AllModels.register(MoreCopycatsRegister.GEARBOX_BLOCK, ::CopycatGearboxBlockModel)
        AllModels.register(MoreCopycatsRegister.COGWHEEL_BLOCK, ::CopycatCogwheelBlockModel)
        AllModels.register(MoreCopycatsRegister.LARGE_COGWHEEL_BLOCK, ::CopycatCogwheelBlockModel)
        AllModels.register(MoreCopycatsRegister.ENCASED_COGWHEEL_BLOCK, ::CopycatCogwheelBlockModel)
        AllModels.register(MoreCopycatsRegister.ENCASED_LARGE_COGWHEEL_BLOCK, ::CopycatCogwheelBlockModel)
        BlockEntityRenderers.register(MoreCopycatsRegister.SLIDING_DOOR_BE, ::SlidingDoorRenderer)
        BlockEntityRenderers.register(MoreCopycatsRegister.REDSTONE_BE, ::CopycatRedstoneRenderer)
        BlockEntityRenderers.register(MoreCopycatsRegister.SHAFT_BE, ::CopycatShaftRenderer)
        BlockEntityRenderers.register(MoreCopycatsRegister.GEARBOX_BE, ::CopycatGearboxRenderer)
        BlockEntityRenderers.register(MoreCopycatsRegister.COGWHEEL_BE, ::CopycatCogwheelRenderer)
        MenuScreens.register(MoreCopycatsRegister.UV_TOOL_MENU, ::CopycatUvToolScreen)

        // Setup render for model //
        BlockRenderLayerMapImpl.putBlock(MoreCopycatsRegister.BYTE_BLOCK, ChunkSectionLayer.CUTOUT)
        BlockRenderLayerMapImpl.putBlock(MoreCopycatsRegister.BITE_BLOCK, ChunkSectionLayer.CUTOUT)
        BlockRenderLayerMapImpl.putBlock(MoreCopycatsRegister.BYTE_SIMPLE_BLOCK, ChunkSectionLayer.CUTOUT)
        BlockRenderLayerMapImpl.putBlock(MoreCopycatsRegister.BITE_SIMPLE_BLOCK, ChunkSectionLayer.CUTOUT)
        BlockRenderLayerMapImpl.putBlock(MoreCopycatsRegister.STAIRS_BLOCK, ChunkSectionLayer.CUTOUT)
        BlockRenderLayerMapImpl.putBlock(MoreCopycatsRegister.STEP_BLOCK, ChunkSectionLayer.CUTOUT)
        BlockRenderLayerMapImpl.putBlock(MoreCopycatsRegister.BUTTON_BLOCK, ChunkSectionLayer.CUTOUT)
        BlockRenderLayerMapImpl.putBlock(MoreCopycatsRegister.PRESSURE_PLATE_BLOCK, ChunkSectionLayer.CUTOUT)
        BlockRenderLayerMapImpl.putBlock(MoreCopycatsRegister.SLAB_BLOCK, ChunkSectionLayer.CUTOUT)
        BlockRenderLayerMapImpl.putBlock(MoreCopycatsRegister.DOOR_BLOCK, ChunkSectionLayer.CUTOUT)
        BlockRenderLayerMapImpl.putBlock(MoreCopycatsRegister.SLIDING_DOOR_BLOCK, ChunkSectionLayer.CUTOUT)
        BlockRenderLayerMapImpl.putBlock(MoreCopycatsRegister.TRAPDOOR_BLOCK, ChunkSectionLayer.CUTOUT)
        BlockRenderLayerMapImpl.putBlock(MoreCopycatsRegister.FENCE_BLOCK, ChunkSectionLayer.CUTOUT)
        BlockRenderLayerMapImpl.putBlock(MoreCopycatsRegister.WALL_BLOCK, ChunkSectionLayer.CUTOUT)
        BlockRenderLayerMapImpl.putBlock(MoreCopycatsRegister.SHAFT_BLOCK, ChunkSectionLayer.CUTOUT)
        BlockRenderLayerMapImpl.putBlock(MoreCopycatsRegister.GEARBOX_SHAFT_BLOCK, ChunkSectionLayer.CUTOUT)
        BlockRenderLayerMapImpl.putBlock(MoreCopycatsRegister.ENCASED_SHAFT_BLOCK, ChunkSectionLayer.CUTOUT)
        BlockRenderLayerMapImpl.putBlock(MoreCopycatsRegister.GEARBOX_BLOCK, ChunkSectionLayer.CUTOUT)
        BlockRenderLayerMapImpl.putBlock(MoreCopycatsRegister.COGWHEEL_BLOCK, ChunkSectionLayer.CUTOUT)
        BlockRenderLayerMapImpl.putBlock(MoreCopycatsRegister.LARGE_COGWHEEL_BLOCK, ChunkSectionLayer.CUTOUT)
        BlockRenderLayerMapImpl.putBlock(MoreCopycatsRegister.ENCASED_COGWHEEL_BLOCK, ChunkSectionLayer.CUTOUT)
        BlockRenderLayerMapImpl.putBlock(MoreCopycatsRegister.ENCASED_LARGE_COGWHEEL_BLOCK, ChunkSectionLayer.CUTOUT)

        CogwheelFreeSlotOverlay.register()
        FencePartOverlay.register()
        WallPartOverlay.register()
        GearboxPartOverlay.register()
        CogwheelSafeZoneDebug.register()
    }
}
