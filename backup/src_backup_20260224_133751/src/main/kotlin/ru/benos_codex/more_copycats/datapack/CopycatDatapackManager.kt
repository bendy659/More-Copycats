package ru.benos_codex.more_copycats.datapack

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.world.level.block.state.BlockState
import org.slf4j.LoggerFactory

object CopycatDatapackManager {
    private const val BLOCK_DEFINITION_PATH = "blocks"
    private const val MAX_REDSTONE_HOLD_TICKS = 20 * 60

    private val logger = LoggerFactory.getLogger("MoreCopycats/Datapack")
    @Volatile
    private var definitionsByTarget: Map<Identifier, CopycatBlockDefinition> = emptyMap()

    fun init() {
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register(
            ServerLifecycleEvents.EndDataPackReload { _, resourceManager, success ->
                if (!success) {
                    logger.warn("Datapack reload failed, keeping previous More Copycats definitions")
                    return@EndDataPackReload
                }
                load(resourceManager)
            }
        )
    }

    fun definitionFor(blockState: BlockState): CopycatBlockDefinition? {
        val blockId = BuiltInRegistries.BLOCK.getKey(blockState.block)
        return definitionsByTarget[blockId]
    }

    fun isBlockEnabled(blockState: BlockState): Boolean =
        definitionFor(blockState)?.enabled ?: true

    fun redstoneHoldTicksFor(blockState: BlockState): Int? =
        definitionFor(blockState)?.server?.redstoneHoldTicks

    private fun load(resourceManager: ResourceManager) {
        val loaded = LinkedHashMap<Identifier, CopycatBlockDefinition>()
        val resources = resourceManager.listResources(BLOCK_DEFINITION_PATH) { id -> id.path.endsWith(".json") }

        for ((resourceId, resource) in resources) {
            val root = try {
                resource.openAsReader().use { reader ->
                    JsonParser.parseReader(reader).asJsonObject
                }
            } catch (error: Exception) {
                logger.warn("Failed to parse datapack block definition {}", resourceId, error)
                continue
            }

            val definition = parseDefinition(resourceId, root) ?: continue
            loaded[definition.target] = definition
        }

        definitionsByTarget = loaded
        logger.info("Loaded {} More Copycats block definition(s) from datapacks", loaded.size)
    }

    private fun parseDefinition(resourceId: Identifier, root: JsonObject): CopycatBlockDefinition? {
        val targetRaw = root.string("target") ?: root.string("id")
        val target = when {
            targetRaw != null -> {
                try {
                    Identifier.parse(targetRaw)
                } catch (_: Exception) {
                    logger.warn("Skipping {}: invalid target '{}'", resourceId, targetRaw)
                    return null
                }
            }

            else -> inferTargetFromFile(resourceId) ?: run {
                logger.warn("Skipping {}: failed to infer target id from file name", resourceId)
                return null
            }
        }

        val type = root.string("family") ?: root.string("type")
        val enabled = root.bool("enabled") ?: true

        val server = root.objectValue("server")
        val redstone = root.objectValue("redstone") ?: root.objectValue("redstone_logic")
        val holdTicks = (
            server?.int("redstone_hold_ticks")
                ?: redstone?.int("hold_ticks")
            )?.coerceIn(1, MAX_REDSTONE_HOLD_TICKS)

        return CopycatBlockDefinition(
            target = target,
            type = type,
            enabled = enabled,
            server = CopycatBlockServerConfig(
                redstoneHoldTicks = holdTicks
            )
        )
    }

    private fun JsonObject.element(name: String): JsonElement? =
        if (has(name)) get(name) else null

    private fun JsonObject.string(name: String): String? {
        val value = element(name) ?: return null
        return if (value.isJsonPrimitive && value.asJsonPrimitive.isString) value.asString else null
    }

    private fun JsonObject.bool(name: String): Boolean? {
        val value = element(name) ?: return null
        return if (value.isJsonPrimitive && value.asJsonPrimitive.isBoolean) value.asBoolean else null
    }

    private fun JsonObject.int(name: String): Int? {
        val value = element(name) ?: return null
        return if (value.isJsonPrimitive && value.asJsonPrimitive.isNumber) value.asInt else null
    }

    private fun JsonObject.objectValue(name: String): JsonObject? {
        val value = element(name) ?: return null
        return if (value.isJsonObject) value.asJsonObject else null
    }

    private fun inferTargetFromFile(resourceId: Identifier): Identifier? {
        val prefix = "$BLOCK_DEFINITION_PATH/"
        val path = resourceId.path
        if (!path.startsWith(prefix) || !path.endsWith(".json")) return null
        val blockPath = path.removePrefix(prefix).removeSuffix(".json")
        if (blockPath.isBlank()) return null
        return try {
            Identifier.parse("${resourceId.namespace}:$blockPath")
        } catch (_: Exception) {
            null
        }
    }
}
