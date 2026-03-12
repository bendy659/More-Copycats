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
import ru.benos_codex.more_copycats.block.CopycatGearboxBlock
import ru.benos_codex.more_copycats.block.entity.CopycatGearboxBlockEntity

object GearboxPartOverlay {
    private const val PLACE_BASE_COLOR = 0x76DBFF
    private const val PLACE_ACTIVE_COLOR = 0xB8ECFF
    private const val REMOVE_BASE_COLOR = 0xFF9F76
    private const val REMOVE_ACTIVE_COLOR = 0xFFD3BC
    private const val LOCK_BASE_COLOR = 0xFFE266
    private const val LOCK_ACTIVE_COLOR = 0xFFF2B0
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
        val showLocks = mode == OverlayMode.REMOVE

        val hit = client.hitResult as? BlockHitResult ?: return
        val pos = hit.blockPos
        val state = level.getBlockState(pos)
        val block = state.block as? CopycatGearboxBlock ?: return
        val blockEntity = level.getBlockEntity(pos) as? CopycatGearboxBlockEntity ?: return

        val hoveredSlot = block.slotFromHit(hit.location, pos, state)
        val zones = block.slotBoxes(state, pos)
            .filter { box ->
                box.face?.let { face -> !isFaceLocked(state, face) } ?: true
            }
            .filter { box ->
                when (mode) {
                    OverlayMode.PLACE -> !blockEntity.hasCustomMaterial(box.slot)
                    OverlayMode.REMOVE -> blockEntity.hasCustomMaterial(box.slot)
                }
            }
            .mapIndexed { index, box ->
                Zone(
                    key = "more_copycats:gearbox_${mode.name.lowercase()}:${pos.asLong()}:${box.slot.name.lowercase()}:$index",
                    slot = box.slot,
                    aabb = box.aabb
                )
            }
        if (zones.isNotEmpty()) {
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
                draw("${zone.key}:focus", zone.aabb, FOCUS_COLOR, FOCUS_LINE_WIDTH)
            }
        } else if (!showLocks) {
            return
        }

        if (showLocks) {
            val lockZones = block.lockTriggerBoxes(state, pos)
                .mapIndexed { index, box ->
                    LockZone(
                        key = "more_copycats:gearbox_lock:${pos.asLong()}:${box.face.name.lowercase()}:$index",
                        aabb = box.aabb
                    )
                }
            if (lockZones.isEmpty()) return

            val activeLocks = lockZones.filter { it.aabb.contains(hit.location) }
            val focusLock = activeLocks.minByOrNull { zone ->
                distanceSquaredToAabb(hit.location.x, hit.location.y, hit.location.z, zone.aabb)
            }

            for (zone in lockZones) {
                draw(zone.key, zone.aabb, LOCK_BASE_COLOR, BASE_LINE_WIDTH)
            }
            for (zone in activeLocks) {
                draw("${zone.key}:active", zone.aabb, LOCK_ACTIVE_COLOR, ACTIVE_LINE_WIDTH)
            }
            if (focusLock != null) {
                draw("${focusLock.key}:focus", focusLock.aabb, FOCUS_COLOR, FOCUS_LINE_WIDTH)
            }
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
        if (block is CopycatGearboxBlock) return false
        if (block is CopycatBlock) return false
        if (block is EntityBlock) return false
        if (block is StairBlock) return false
        return true
    }

    private fun isWrench(stack: ItemStack): Boolean =
        stack.`is`(AllItemTags.TOOLS_WRENCH)

    private fun isFaceLocked(state: net.minecraft.world.level.block.state.BlockState, face: net.minecraft.core.Direction): Boolean {
        return when (face) {
            net.minecraft.core.Direction.NORTH -> state.getValue(CopycatGearboxBlock.LOCK_NORTH)
            net.minecraft.core.Direction.EAST -> state.getValue(CopycatGearboxBlock.LOCK_EAST)
            net.minecraft.core.Direction.SOUTH -> state.getValue(CopycatGearboxBlock.LOCK_SOUTH)
            net.minecraft.core.Direction.WEST -> state.getValue(CopycatGearboxBlock.LOCK_WEST)
            net.minecraft.core.Direction.UP -> state.getValue(CopycatGearboxBlock.LOCK_TOP)
            net.minecraft.core.Direction.DOWN -> state.getValue(CopycatGearboxBlock.LOCK_BOTTOM)
        }
    }

    private enum class OverlayMode {
        PLACE,
        REMOVE
    }

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
        val slot: CopycatGearboxBlockEntity.Slot,
        val aabb: AABB
    )

    private data class LockZone(
        val key: String,
        val aabb: AABB
    )

}
