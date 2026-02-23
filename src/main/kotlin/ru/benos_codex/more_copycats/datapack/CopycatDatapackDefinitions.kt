package ru.benos_codex.more_copycats.datapack

import net.minecraft.resources.Identifier

data class CopycatBlockServerConfig(
    val redstoneHoldTicks: Int? = null
)

data class CopycatBlockDefinition(
    val target: Identifier,
    val type: String? = null,
    val enabled: Boolean = true,
    val server: CopycatBlockServerConfig = CopycatBlockServerConfig()
)
