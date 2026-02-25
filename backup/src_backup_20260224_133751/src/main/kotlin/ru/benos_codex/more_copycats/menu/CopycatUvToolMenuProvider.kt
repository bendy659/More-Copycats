package ru.benos_codex.more_copycats.menu

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu

class CopycatUvToolMenuProvider(
    private val pos: BlockPos,
    private val partIndex: Int,
    private val faceOrdinal: Int
) : ExtendedScreenHandlerFactory<UvToolOpenData> {

    override fun getScreenOpeningData(player: ServerPlayer): UvToolOpenData =
        UvToolOpenData(pos, partIndex, faceOrdinal)

    override fun getDisplayName(): Component =
        Component.literal("Copycat UV Tool")

    override fun createMenu(syncId: Int, inv: Inventory, player: Player): AbstractContainerMenu =
        CopycatUvToolMenu(syncId, inv, UvToolOpenData(pos, partIndex, faceOrdinal))
}
