package ru.benos_codex.more_copycats

import net.fabricmc.api.ModInitializer
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.resources.Identifier
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import java.util.Properties
import ru.benos_codex.more_copycats.datapack.CopycatDatapackManager
import ru.benos_codex.more_copycats.network.UvToolNetworking

@Suppress("unused")
object MoreCopycats : ModInitializer {
    val String.rl: Identifier get() = Identifier.parse(this)
    val String.mrl: Identifier get() = "more_copycats:$this".rl

    const val DEBUG_COGWHEEL_RENDER_LOGS: Boolean = false
    const val DEBUG_COGWHEEL_HIT_LOGS: Boolean = false

    val DEFAULT_MATERIAL: BlockState = Blocks.AIR.defaultBlockState()
    val DEFAULT_PROPERTIES: BlockBehaviour.Properties = BlockBehaviour.Properties
        .of()
        .noOcclusion()
        .dynamicShape()
        .isValidSpawn { _, _, _, _ -> false }

    object Config {
        var uvGridStep: Int = 1
        var showCogwheelFreeSlotOverlay: Boolean = true
        var showPartOverlay: Boolean = true
        var debugCogwheelSlotColors: Boolean = false
        var debugCogwheelSafeZoneOverlay: Boolean = false

        fun load() {
            val configDir = FabricLoader.getInstance().configDir
            val file = configDir.resolve("more_copycats.properties").toFile()
            val props = Properties()
            if (file.exists()) {
                file.inputStream().use { props.load(it) }
            }
            uvGridStep = props.getProperty("uv_grid_step", "1").toIntOrNull()?.coerceAtLeast(1) ?: 1
            showCogwheelFreeSlotOverlay = props.getProperty("cogwheel_free_slot_overlay", "true").toBoolean()
            showPartOverlay = props.getProperty(
                "part_overlay",
                props.getProperty("fence_part_overlay", props.getProperty("wall_part_overlay", "true"))
            ).toBoolean()
            debugCogwheelSlotColors = props.getProperty("debug_cogwheel_slot_colors", "false").toBoolean()
            debugCogwheelSafeZoneOverlay = props.getProperty("debug_cogwheel_safe_zone_overlay", "false").toBoolean()
            props.setProperty("uv_grid_step", uvGridStep.toString())
            props.setProperty("cogwheel_free_slot_overlay", showCogwheelFreeSlotOverlay.toString())
            props.setProperty("part_overlay", showPartOverlay.toString())
            props.setProperty("debug_cogwheel_slot_colors", debugCogwheelSlotColors.toString())
            props.setProperty("debug_cogwheel_safe_zone_overlay", debugCogwheelSafeZoneOverlay.toString())
            props.remove("fence_part_overlay")
            props.remove("wall_part_overlay")
            props.remove("debug_cogwheel_render_logs")
            props.remove("debug_cogwheel_hit_logs")
            file.outputStream().use { props.store(it, "More Copycats config") }
        }
    }

    override fun onInitialize() {
        Config.load()
        CopycatDatapackManager.init()
        MoreCopycatsRegister.init
        UvToolNetworking.init()
    }
}
