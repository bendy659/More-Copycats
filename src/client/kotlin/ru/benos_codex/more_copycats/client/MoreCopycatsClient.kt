package ru.benos.more_copycats.client

import com.zurrtum.create.client.AllModels
import com.zurrtum.create.catnip.theme.Color
import com.zurrtum.create.client.catnip.outliner.Outliner
import com.zurrtum.create.client.catnip.render.BindableTexture
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.impl.client.rendering.BlockRenderLayerMapImpl
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.MenuScreens
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers
import net.minecraft.client.renderer.chunk.ChunkSectionLayer
import net.minecraft.resources.Identifier
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import ru.benos.more_copycats.MoreCopycatsRegister
import ru.benos.more_copycats.client.model.block.CopycatBiteBlockModel
import ru.benos.more_copycats.client.model.block.CopycatByteBlockModel
import ru.benos.more_copycats.client.model.block.CopycatBiteSimpleBlockModel
import ru.benos.more_copycats.client.model.block.CopycatDoorBlockModel
import ru.benos.more_copycats.client.model.block.CopycatByteSimpleBlockModel
import ru.benos.more_copycats.client.model.block.CopycatSlidingDoorBlockModel
import ru.benos.more_copycats.client.model.block.CopycatSlabBlockModel
import ru.benos.more_copycats.client.model.block.CopycatSimpleBlockModel
import ru.benos.more_copycats.client.model.block.CopycatStairsBlockModel
import ru.benos.more_copycats.client.model.block.CopycatTrapdoorBlockModel
import ru.benos.more_copycats.client.screen.CopycatUvToolScreen
import com.zurrtum.create.client.content.decoration.slidingDoor.SlidingDoorRenderer
import ru.benos.more_copycats.block.CopycatBiteBlock
import ru.benos.more_copycats.block.CopycatByteBlock
import ru.benos.more_copycats.block.entity.CopycatBiteBlockEntity
import ru.benos.more_copycats.block.entity.CopycatByteBlockEntity

object MoreCopycatsClient : ClientModInitializer {
    private val PREVIEW_TEXTURE: BindableTexture = BindableTexture {
        Identifier.parse("more_copycats:textures/misc/white.png")
    }

    private var lastPreviewKey: String? = null

    override fun onInitializeClient() {
        // Register model //
        AllModels.register(MoreCopycatsRegister.BYTE_BLOCK, ::CopycatByteBlockModel)
        AllModels.register(MoreCopycatsRegister.BITE_BLOCK, ::CopycatBiteBlockModel)
        AllModels.register(MoreCopycatsRegister.BYTE_SIMPLE_BLOCK, ::CopycatByteSimpleBlockModel)
        AllModels.register(MoreCopycatsRegister.BITE_SIMPLE_BLOCK, ::CopycatBiteSimpleBlockModel)
        AllModels.register(MoreCopycatsRegister.STAIRS_BLOCK, ::CopycatStairsBlockModel)
        AllModels.register(MoreCopycatsRegister.SLAB_BLOCK, ::CopycatSlabBlockModel)
        AllModels.register(MoreCopycatsRegister.VERTICAL_SLAB_BLOCK, ::CopycatSlabBlockModel)
        AllModels.register(MoreCopycatsRegister.DOOR_BLOCK, ::CopycatDoorBlockModel)
        AllModels.register(MoreCopycatsRegister.SLIDING_DOOR_BLOCK, ::CopycatSlidingDoorBlockModel)
        AllModels.register(MoreCopycatsRegister.TRAPDOOR_BLOCK, ::CopycatTrapdoorBlockModel)
        AllModels.register(MoreCopycatsRegister.FENCE_BLOCK, ::CopycatSimpleBlockModel)
        AllModels.register(MoreCopycatsRegister.WALL_BLOCK, ::CopycatSimpleBlockModel)
        BlockEntityRenderers.register(MoreCopycatsRegister.SLIDING_DOOR_BE, ::SlidingDoorRenderer)
        MenuScreens.register(MoreCopycatsRegister.UV_TOOL_MENU, ::CopycatUvToolScreen)

        // Setup render for model //
        BlockRenderLayerMapImpl.putBlock(MoreCopycatsRegister.BYTE_BLOCK, ChunkSectionLayer.CUTOUT)
        BlockRenderLayerMapImpl.putBlock(MoreCopycatsRegister.BITE_BLOCK, ChunkSectionLayer.CUTOUT)
        BlockRenderLayerMapImpl.putBlock(MoreCopycatsRegister.BYTE_SIMPLE_BLOCK, ChunkSectionLayer.CUTOUT)
        BlockRenderLayerMapImpl.putBlock(MoreCopycatsRegister.BITE_SIMPLE_BLOCK, ChunkSectionLayer.CUTOUT)
        BlockRenderLayerMapImpl.putBlock(MoreCopycatsRegister.STAIRS_BLOCK, ChunkSectionLayer.CUTOUT)
        BlockRenderLayerMapImpl.putBlock(MoreCopycatsRegister.SLAB_BLOCK, ChunkSectionLayer.CUTOUT)
        BlockRenderLayerMapImpl.putBlock(MoreCopycatsRegister.VERTICAL_SLAB_BLOCK, ChunkSectionLayer.CUTOUT)
        BlockRenderLayerMapImpl.putBlock(MoreCopycatsRegister.DOOR_BLOCK, ChunkSectionLayer.CUTOUT)
        BlockRenderLayerMapImpl.putBlock(MoreCopycatsRegister.SLIDING_DOOR_BLOCK, ChunkSectionLayer.CUTOUT)
        BlockRenderLayerMapImpl.putBlock(MoreCopycatsRegister.TRAPDOOR_BLOCK, ChunkSectionLayer.CUTOUT)
        BlockRenderLayerMapImpl.putBlock(MoreCopycatsRegister.FENCE_BLOCK, ChunkSectionLayer.CUTOUT)
        BlockRenderLayerMapImpl.putBlock(MoreCopycatsRegister.WALL_BLOCK, ChunkSectionLayer.CUTOUT)

        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { mc ->
            renderPlacementPreview(mc)
        })

    }

    private fun renderPlacementPreview(mc: Minecraft) {
        val player = mc.player ?: return
        val level = mc.level ?: return

        if (player.isShiftKeyDown) {
            Outliner.getInstance().remove("more_copycats:byte_preview")
            Outliner.getInstance().remove("more_copycats:bite_preview")
            lastPreviewKey = null
            return
        }

        val hit = mc.hitResult as? BlockHitResult ?: run {
            Outliner.getInstance().remove("more_copycats:byte_preview")
            Outliner.getInstance().remove("more_copycats:bite_preview")
            lastPreviewKey = null
            return
        }

        val main = player.mainHandItem
        val off = player.offhandItem

        val heldIsByte = main.`is`(MoreCopycatsRegister.BYTE_ITEM) || off.`is`(MoreCopycatsRegister.BYTE_ITEM)
        val heldIsBite = main.`is`(MoreCopycatsRegister.BITE_ITEM) || off.`is`(MoreCopycatsRegister.BITE_ITEM)

        if (!heldIsByte) Outliner.getInstance().remove("more_copycats:byte_preview")
        if (!heldIsBite) Outliner.getInstance().remove("more_copycats:bite_preview")

        val pos = hit.blockPos
        val state = level.getBlockState(pos)

        if (heldIsByte && state.block is CopycatByteBlock) {
            val be = level.getBlockEntity(pos) as? CopycatByteBlockEntity
            val index = if (be != null) (state.block as CopycatByteBlock).getTargetIndex(hit.location, pos, hit.direction, true, be) else null
            if (index != null) {
                val box = byteIndexToAabb(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble(), index)
                val key = "byte:${pos.asLong()}:${hit.direction}:${index}"
                if (lastPreviewKey != null && lastPreviewKey != key) {
                    Outliner.getInstance().remove("more_copycats:byte_preview")
                }
                lastPreviewKey = key
                val params = Outliner.getInstance().chaseAABB("more_copycats:byte_preview", box)
                applyPreviewParams(params, level.gameTime)
            } else {
                Outliner.getInstance().remove("more_copycats:byte_preview")
                if (lastPreviewKey?.startsWith("byte:") == true) lastPreviewKey = null
            }
        }

        if (heldIsBite && state.block is CopycatBiteBlock) {
            val be = level.getBlockEntity(pos) as? CopycatBiteBlockEntity
            val index = if (be != null) (state.block as CopycatBiteBlock).getTargetIndex(hit.location, pos, hit.direction, true, be) else null
            if (index != null) {
                val box = biteIndexToAabb(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble(), index)
                val key = "bite:${pos.asLong()}:${hit.direction}:${index}"
                if (lastPreviewKey != null && lastPreviewKey != key) {
                    Outliner.getInstance().remove("more_copycats:bite_preview")
                }
                lastPreviewKey = key
                val params = Outliner.getInstance().chaseAABB("more_copycats:bite_preview", box)
                applyPreviewParams(params, level.gameTime)
            } else {
                Outliner.getInstance().remove("more_copycats:bite_preview")
                if (lastPreviewKey?.startsWith("bite:") == true) lastPreviewKey = null
            }
        }

        // UV buttons removed; editor is opened via tool.
    }

    private fun applyPreviewParams(params: com.zurrtum.create.client.catnip.outliner.Outline.OutlineParams, gameTime: Long) {
        val t = (gameTime % 40L).toDouble() / 40.0 * Math.PI * 2.0
        val pulse = (kotlin.math.sin(t) * 0.5 + 0.5)
        val alpha = (0.2 + pulse * 0.35)
        val a = (alpha * 255.0).toInt().coerceIn(0, 255)
        val color = Color(0x66, 0xFF, 0xD7, a)
        params.colored(color)
            .withFaceTexture(PREVIEW_TEXTURE)
            .lineWidth(1 / 32f)
            .disableCull()
    }

    private fun byteIndexToAabb(x: Double, y: Double, z: Double, index: Int): AABB {
        val ox = index and 1
        val oy = (index shr 1) and 1
        val oz = (index shr 2) and 1
        val minX = x + ox * 0.5
        val minY = y + oy * 0.5
        val minZ = z + oz * 0.5
        return AABB(minX, minY, minZ, minX + 0.5, minY + 0.5, minZ + 0.5)
    }

    private fun biteIndexToAabb(x: Double, y: Double, z: Double, index: Int): AABB {
        val ox = index and 3
        val oy = (index shr 2) and 3
        val oz = (index shr 4) and 3
        val minX = x + ox * 0.25
        val minY = y + oy * 0.25
        val minZ = z + oz * 0.25
        return AABB(minX, minY, minZ, minX + 0.25, minY + 0.25, minZ + 0.25)
    }
}
