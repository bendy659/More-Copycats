package ru.benos_codex.more_copycats.client.overlay

import com.zurrtum.create.AllItemTags
import com.zurrtum.create.client.catnip.outliner.Outliner
import com.zurrtum.create.content.decoration.copycat.CopycatBlock
import com.zurrtum.create.content.kinetics.simpleRelays.CogWheelBlock.AXIS
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents
import net.minecraft.client.Minecraft
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.StairBlock
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import ru.benos_codex.more_copycats.MoreCopycats
import ru.benos_codex.more_copycats.block.CopycatCogwheelBlock
import ru.benos_codex.more_copycats.block.CopycatEncasedCogwheelBlock
import ru.benos_codex.more_copycats.block.entity.CopycatCogwheelBlockEntity
import ru.benos_codex.more_copycats.util.CogwheelMaterialSlotResolver

object CogwheelFreeSlotOverlay {
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
        if (!MoreCopycats.Config.showCogwheelFreeSlotOverlay) return

        val level = client.level ?: return
        val player = client.player ?: return
        val mode = currentMode(player.mainHandItem, player.offhandItem) ?: return

        val hit = client.hitResult as? BlockHitResult ?: return
        val pos = hit.blockPos
        val state = level.getBlockState(pos)
        if (state.block !is CopycatCogwheelBlock) return

        val blockEntity = level.getBlockEntity(pos) as? CopycatCogwheelBlockEntity ?: return
        val axis = state.getValue(AXIS)
        val layout = if (state.block is CopycatEncasedCogwheelBlock)
            CogwheelMaterialSlotResolver.Layout.ENCASED_VARIANT_2
        else
            CogwheelMaterialSlotResolver.Layout.NORMAL
        val topShaft = if (state.block is CopycatEncasedCogwheelBlock) state.getValue(CopycatEncasedCogwheelBlock.TOP_SHAFT) else true
        val bottomShaft = if (state.block is CopycatEncasedCogwheelBlock) state.getValue(CopycatEncasedCogwheelBlock.BOTTOM_SHAFT) else true
        val relX = hit.location.x - pos.x
        val relY = hit.location.y - pos.y
        val relZ = hit.location.z - pos.z
        val rayStart = player.getEyePosition()
        val rayEnd = hit.location.add(player.lookAngle.normalize().scale(2.0))
        val hoveredSlot = CogwheelMaterialSlotResolver.raycast(
            layout,
            axis,
            rayStart.x - pos.x,
            rayStart.y - pos.y,
            rayStart.z - pos.z,
            rayEnd.x - pos.x,
            rayEnd.y - pos.y,
            rayEnd.z - pos.z,
            topShaft,
            bottomShaft
        )?.slot ?: CogwheelMaterialSlotResolver.resolveStrict(layout, axis, relX, relY, relZ, topShaft, bottomShaft)
            ?: if (layout == CogwheelMaterialSlotResolver.Layout.NORMAL)
                4
            else
                CogwheelMaterialSlotResolver.resolve(layout, axis, relX, relY, relZ, topShaft = topShaft, bottomShaft = bottomShaft)
        val visibleSlots = CopycatCogwheelBlockEntity.Slot.entries
            .filter { slot ->
                when (mode) {
                    OverlayMode.PLACE -> !blockEntity.hasCustomMaterial(slot)
                    OverlayMode.REMOVE -> blockEntity.hasCustomMaterial(slot)
                }
            }
            .map { it.ordinal }
            .toSet()
        if (visibleSlots.isEmpty()) return

        val zones = CogwheelMaterialSlotResolver.slotWorldBoxes(
            layout,
            axis,
            pos.x.toDouble(),
            pos.y.toDouble(),
            pos.z.toDouble(),
            topShaft,
            bottomShaft
        )
            .filter { it.slot in visibleSlots }
            .mapIndexed { index, box ->
                Zone(
                    key = "more_copycats:cogwheel_${mode.name.lowercase()}:${pos.asLong()}:${axis.name.lowercase()}:${box.slot}:$index",
                    slot = box.slot,
                    aabb = box.aabb
                )
            }
        if (zones.isEmpty()) return

        val activeZones = zones.filter { it.slot == hoveredSlot }
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
        for (zone in activeZones) {
            draw("${zone.key}:focus", zone.aabb, FOCUS_COLOR, FOCUS_LINE_WIDTH)
        }
    }

    private fun draw(key: String, aabb: AABB, color: Int, lineWidth: Float) {
        Outliner.getInstance().showAABB(key, aabb, 2)
            .colored(color)
            .lineWidth(lineWidth)
            .disableCull()
            .disableLineNormals()
    }

    private fun isHoldingMaterialItem(stack: ItemStack): Boolean {
        val blockItem = stack.item as? BlockItem ?: return false
        val block = blockItem.block
        if (block is CopycatCogwheelBlock) return false
        if (block is CopycatBlock) return false
        if (block is EntityBlock) return false
        if (block is StairBlock) return false
        return true
    }

    private fun currentMode(mainHand: ItemStack, offhand: ItemStack): OverlayMode? {
        if (isWrench(mainHand) || isWrench(offhand))
            return OverlayMode.REMOVE
        if (isHoldingMaterialItem(mainHand) || isHoldingMaterialItem(offhand))
            return OverlayMode.PLACE
        return null
    }

    private fun isWrench(stack: ItemStack): Boolean =
        stack.`is`(AllItemTags.TOOLS_WRENCH)

    private data class Zone(
        val key: String,
        val slot: Int,
        val aabb: AABB
    )

    private enum class OverlayMode {
        PLACE,
        REMOVE
    }
}
