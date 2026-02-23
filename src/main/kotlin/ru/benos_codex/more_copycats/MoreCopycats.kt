package ru.benos.more_copycats

import net.fabricmc.api.ModInitializer
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.resources.Identifier
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import java.util.Properties
import ru.benos.more_copycats.network.UvToolNetworking

object MoreCopycats : ModInitializer {
    val String.rl: Identifier get() = Identifier.parse(this)
    val String.mrl: Identifier get() = "more_copycats:$this".rl

    val DEFAULT_MATERIAL: BlockState = Blocks.AIR.defaultBlockState()
    val DEFAULT_PROPERTIES: BlockBehaviour.Properties = BlockBehaviour.Properties
        .of()
        .noOcclusion()
        .dynamicShape()
        .isValidSpawn { _, _, _, _ -> false }

    object Config {
        var uvGridStep: Int = 1

        fun load() {
            val configDir = FabricLoader.getInstance().configDir
            val file = configDir.resolve("more_copycats.properties").toFile()
            val props = Properties()
            if (file.exists()) {
                file.inputStream().use { props.load(it) }
            }
            uvGridStep = props.getProperty("uv_grid_step", "1").toIntOrNull()?.coerceAtLeast(1) ?: 1
            props.setProperty("uv_grid_step", uvGridStep.toString())
            file.outputStream().use { props.store(it, "More Copycats config") }
        }
    }

    override fun onInitialize() {
        Config.load()
        MoreCopycatsRegister.init
        UvToolNetworking.init()
    }
}
