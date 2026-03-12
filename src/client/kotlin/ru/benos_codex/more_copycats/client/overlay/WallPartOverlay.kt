package ru.benos_codex.more_copycats.client.overlay

import com.zurrtum.create.AllItemTags
import com.zurrtum.create.client.catnip.outliner.Outliner
import com.zurrtum.create.content.decoration.copycat.CopycatBlock
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents
import net.minecraft.client.Minecraft
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.StairBlock
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import ru.benos_codex.more_copycats.MoreCopycats
import ru.benos_codex.more_copycats.block.CopycatWallBlock
import ru.benos_codex.more_copycats.block.entity.CopycatFenceWallBlockEntity

object WallPartOverlay {
    private const val PLACE_BASE_COLOR = 0x76DBFF
    private const val PLACE_ACTIVE_COLOR = 0xB8ECFF
    private const val REMOVE_BASE_COLOR = 0xFF9F76
    private const val REMOVE_ACTIVE_COLOR = 0xFFD3BC
    private const val FOCUS_COLOR = 0xFFFFFF
    private const val BASE_LINE_WIDTH = 1f / 280f
    private const val ACTIVE_LINE_WIDTH = 1f / 150f
    private const val FOCUS_LINE_WIDTH = 1f / 52f

    fun register() {
        WorldRenderEvents.END_MAIN.register { _ ->
            render(Minecraft.getInstance())
        }
    }

    private fun render(client: Minecraft) {
        if (!MoreCopycats.Config.showPartOverlay) return

        val level = client.level ?: return
        val player = client.player ?: return
        val mode = currentMode(player.mainHandItem, player.offhandItem) ?: return

        val hit = client.hitResult as? BlockHitResult ?: return
        val pos = hit.blockPos
        val state = level.getBlockState(pos)
        val block = state.block as? CopycatWallBlock ?: return
        val blockEntity = level.getBlockEntity(pos) as? CopycatFenceWallBlockEntity ?: return
        val hoveredSlot = block.slotFromHit(state, hit)
        val visibleSlots = CopycatFenceWallBlockEntity.WallSlot.entries.filter { slot ->
            when (mode) {
                OverlayMode.PLACE -> !blockEntity.hasCustomMaterial(slot)
                OverlayMode.REMOVE -> blockEntity.hasCustomMaterial(slot)
            }
        }.toSet()
        if (visibleSlots.isEmpty()) return

        val zones = block.slotBoxes(state, pos)
            .filter { it.slot in visibleSlots }
            .mapIndexed { index, box ->
                Zone(
                    key = "more_copycats:wall_${mode.name.lowercase()}:${pos.asLong()}:${box.slot.name.lowercase()}:$index",
                    slot = box.slot,
                    aabb = box.aabb
                )
            }
        if (zones.isEmpty()) return

        val activeZones = zones.filter { it.slot == hoveredSlot }
        val focusZone = activeZones.minByOrNull { zone ->
            distanceSquaredToAabb(hit.location.x, hit.location.y, hit.location.z, zone.aabb)
        }
        val baseColor = when (mode) {
            OverlayMode.PLACE -> PLACE_BASE_COLOR
            OverlayMode.REMOVE -> REMOVE_BASE_COLOR
        }
        val activeColor = when (mode) {
            OverlayMode.PLACE -> PLACE_ACTIVE_COLOR
            OverlayMode.REMOVE -> REMOVE_ACTIVE_COLOR
        }

        for (zone in zones) {
            draw(zone.key, zone.aabb, baseColor, BASE_LINE_WIDTH)
        }
        for (zone in activeZones) {
            draw("${zone.key}:active", zone.aabb, activeColor, ACTIVE_LINE_WIDTH)
        }
        if (focusZone != null) {
            draw("${focusZone.key}:focus", focusZone.aabb, FOCUS_COLOR, FOCUS_LINE_WIDTH)
        }
    }

    private fun draw(key: String, aabb: AABB, color: Int, lineWidth: Float) {
        Outliner.getInstance().showAABB(key, aabb, 2)
            .colored(color)
            .lineWidth(lineWidth)
            .disableCull()
            .disableLineNormals()
    }

    private fun currentMode(mainHand: ItemStack, offhand: ItemStack): OverlayMode? {
        if (isWrench(mainHand) || isWrench(offhand))
            return OverlayMode.REMOVE
        if (isHoldingMaterialItem(mainHand) || isHoldingMaterialItem(offhand))
            return OverlayMode.PLACE
        return null
    }

    private fun isHoldingMaterialItem(stack: ItemStack): Boolean {
        val blockItem = stack.item as? BlockItem ?: return false
        val block = blockItem.block
        if (block is CopycatWallBlock) return false
        if (block is CopycatBlock) return false
        if (block is EntityBlock) return false
        if (block is StairBlock) return false
        return true
    }

    private fun isWrench(stack: ItemStack): Boolean =
        stack.`is`(AllItemTags.TOOLS_WRENCH)

    private fun distanceSquaredToAabb(x: Double, y: Double, z: Double, aabb: AABB): Double {
        val dx = when {
            x < aabb.minX -> aabb.minX - x
            x > aabb.maxX -> x - aabb.maxX
            else -> 0.0
        }
        val dy = when {
            y < aabb.minY -> aabb.minY - y
            y > aabb.maxY -> y - aabb.maxY
            else -> 0.0
        }
        val dz = when {
            z < aabb.minZ -> aabb.minZ - z
            z > aabb.maxZ -> z - aabb.maxZ
            else -> 0.0
        }
        return dx * dx + dy * dy + dz * dz
    }

    private data class Zone(
        val key: String,
        val slot: CopycatFenceWallBlockEntity.WallSlot,
        val aabb: AABB
    )

    private enum class OverlayMode {
        PLACE,
        REMOVE
    }
}
