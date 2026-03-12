package ru.benos_codex.more_copycats.client.debug

import com.zurrtum.create.AllItemTags
import com.zurrtum.create.client.catnip.outliner.Outliner
import com.zurrtum.create.content.kinetics.simpleRelays.CogWheelBlock.AXIS
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents
import net.minecraft.client.Minecraft
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import ru.benos_codex.more_copycats.MoreCopycats
import ru.benos_codex.more_copycats.block.CopycatCogwheelBlock
import ru.benos_codex.more_copycats.block.CopycatEncasedCogwheelBlock
import ru.benos_codex.more_copycats.util.CogwheelMaterialSlotResolver

object CogwheelSafeZoneDebug {
    private const val SHOW_ALL_ZONES = true
    private const val SHOW_HOVER_FOCUS = true
    private const val BASE_LINE_WIDTH = 1f / 280f
    private const val ACTIVE_SLOT_LINE_WIDTH = 1f / 150f
    private const val FOCUS_LINE_WIDTH = 1f / 44f

    private val SLOT_COLORS = mapOf(
        0 to 0xFF4D4D,
        1 to 0xFFD94D,
        2 to 0x66FF66,
        3 to 0x66E6FF,
        4 to 0xFF6666,
        5 to 0xFFE266,
        6 to 0xF2F2F2,
        7 to 0x8C5CFF
    )

    fun register() {
        WorldRenderEvents.END_MAIN.register { _ ->
            render(Minecraft.getInstance())
        }
    }

    private fun render(client: Minecraft) {
        if (!MoreCopycats.Config.debugCogwheelSafeZoneOverlay) return

        val level = client.level ?: return
        val player = client.player ?: return

        if (!player.mainHandItem.`is`(AllItemTags.TOOLS_WRENCH) && !player.offhandItem.`is`(AllItemTags.TOOLS_WRENCH)) {
            return
        }

        val hit = client.hitResult as? BlockHitResult ?: return
        val pos = hit.blockPos
        val state = level.getBlockState(pos)
        if (state.block !is CopycatCogwheelBlock) return

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
        )?.slot ?: CogwheelMaterialSlotResolver.resolve(layout, axis, relX, relY, relZ, hit.direction, topShaft, bottomShaft)
        val zones = CogwheelMaterialSlotResolver.allWorldBoxes(
            layout,
            axis,
            pos.x.toDouble(),
            pos.y.toDouble(),
            pos.z.toDouble(),
            topShaft,
            bottomShaft
        )
            .mapIndexed { index, box ->
                Zone(
                    key = "more_copycats:cogwheel_debug:${pos.asLong()}:${axis.name.lowercase()}:${box.slot}:$index",
                    slot = box.slot,
                    aabb = box.aabb
                )
            }

        if (zones.isEmpty()) return

        val activeZones = zones.filter { it.slot == hoveredSlot }

        if (SHOW_ALL_ZONES) {
            for (zone in zones) {
                val color = brighten(SLOT_COLORS[zone.slot] ?: 0xFFFFFF, 0.04f)
                draw(zone.key, zone.aabb, color, BASE_LINE_WIDTH)
            }
        }

        for (zone in activeZones) {
            val color = brighten(SLOT_COLORS[zone.slot] ?: 0xFFFFFF, 0.32f)
            draw("${zone.key}:active", zone.aabb, color, ACTIVE_SLOT_LINE_WIDTH)
        }

        if (SHOW_HOVER_FOCUS) {
            val color = brighten(SLOT_COLORS[hoveredSlot] ?: 0xFFFFFF, 0.56f)
            for (zone in activeZones) {
                draw("${zone.key}:focus", zone.aabb, color, FOCUS_LINE_WIDTH)
            }
        }
    }

    private fun draw(key: String, aabb: AABB, rgb: Int, lineWidth: Float) {
        Outliner.getInstance().showAABB(key, aabb, 2)
            .colored(rgb)
            .lineWidth(lineWidth)
            .disableCull()
            .disableLineNormals()
    }

    private fun brighten(rgb: Int, add: Float): Int {
        val red = (rgb shr 16) and 0xFF
        val green = (rgb shr 8) and 0xFF
        val blue = rgb and 0xFF
        val outRed = (red + ((255 - red) * add)).toInt().coerceIn(0, 255)
        val outGreen = (green + ((255 - green) * add)).toInt().coerceIn(0, 255)
        val outBlue = (blue + ((255 - blue) * add)).toInt().coerceIn(0, 255)

        return (outRed shl 16) or (outGreen shl 8) or outBlue
    }

    private data class Zone(
        val key: String,
        val slot: Int,
        val aabb: AABB
    )
}
