package ru.benos_codex.more_copycats.network

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.state.BlockState
import ru.benos_codex.more_copycats.block.entity.CopycatBiteBlockEntity
import ru.benos_codex.more_copycats.block.entity.CopycatByteBlockEntity

object UvToolNetworking {
    private val UPDATE_ID = Identifier.parse("more_copycats:uv_tool_update")

    data class UvToolUpdatePayload(
        val pos: BlockPos,
        val partIndex: Int,
        val faceOrdinal: Int,
        val u: Int,
        val v: Int,
        val w: Int,
        val h: Int,
        val sourceFace: Int,
        val hasMaterial: Boolean,
        val materialId: Identifier?
    ) : CustomPacketPayload {
        override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
    }

    val TYPE: CustomPacketPayload.Type<UvToolUpdatePayload> = CustomPacketPayload.Type(UPDATE_ID)

    val CODEC: StreamCodec<RegistryFriendlyByteBuf, UvToolUpdatePayload> = StreamCodec.of(
        { buf, value ->
            buf.writeBlockPos(value.pos)
            buf.writeVarInt(value.partIndex)
            buf.writeVarInt(value.faceOrdinal)
            buf.writeVarInt(value.u)
            buf.writeVarInt(value.v)
            buf.writeVarInt(value.w)
            buf.writeVarInt(value.h)
            buf.writeByte(value.sourceFace)
            buf.writeBoolean(value.hasMaterial)
            if (value.hasMaterial && value.materialId != null) {
                buf.writeUtf(value.materialId.toString())
            }
        },
        { buf ->
            val pos = buf.readBlockPos()
            val partIndex = buf.readVarInt()
            val faceOrdinal = buf.readVarInt()
            val u = buf.readVarInt()
            val v = buf.readVarInt()
            val w = buf.readVarInt()
            val h = buf.readVarInt()
            val sourceFace = buf.readByte().toInt()
            val hasMaterial = buf.readBoolean()
            val materialId = if (hasMaterial) Identifier.parse(buf.readUtf()) else null
            UvToolUpdatePayload(pos, partIndex, faceOrdinal, u, v, w, h, sourceFace, hasMaterial, materialId)
        }
    )

    fun init() {
        PayloadTypeRegistry.playC2S().register(TYPE, CODEC)
        ServerPlayNetworking.registerGlobalReceiver(TYPE) { payload, context ->
            val player = context.player()
            val server = player.level().server
            server.execute {
                val level = player.level()
                val be = level.getBlockEntity(payload.pos) ?: return@execute
                val face = Direction.entries.getOrNull(payload.faceOrdinal) ?: return@execute
                val sourceFace = Direction.entries.getOrNull(payload.sourceFace) ?: face

                val material: BlockState? = if (payload.hasMaterial && payload.materialId != null) {
                    val block = BuiltInRegistries.BLOCK.getValue(payload.materialId)
                    block?.defaultBlockState()
                } else {
                    null
                }

                val u = payload.u
                val v = payload.v
                val w = payload.w.coerceAtLeast(1)
                val h = payload.h.coerceAtLeast(1)

                when (be) {
                    is CopycatByteBlockEntity -> be.applyUvEdit(payload.partIndex, face, u, v, w, h, sourceFace, material)
                    is CopycatBiteBlockEntity -> be.applyUvEdit(payload.partIndex, face, u, v, w, h, sourceFace, material)
                }
            }
        }
    }

}
