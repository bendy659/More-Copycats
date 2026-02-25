package ru.benos_codex.more_copycats.client.renderer

import com.mojang.blaze3d.vertex.PoseStack
import com.zurrtum.create.client.catnip.render.CachedBuffers
import com.zurrtum.create.client.catnip.render.SuperBufferFactory
import com.zurrtum.create.client.catnip.render.SuperByteBuffer
import com.zurrtum.create.client.infrastructure.model.WrapperBlockStateModel
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.block.model.BlockModelPart
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState
import net.minecraft.client.renderer.feature.ModelFeatureRenderer
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.state.CameraRenderState
import net.minecraft.core.Direction
import net.minecraft.world.level.block.state.properties.AttachFace
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.util.RandomSource
import net.minecraft.world.phys.Vec3
import org.jetbrains.annotations.Nullable
import ru.benos_codex.more_copycats.block.entity.CopycatRedstoneBlockEntity

class CopycatRedstoneRenderer(context: BlockEntityRendererProvider.Context) :
    BlockEntityRenderer<CopycatRedstoneBlockEntity, CopycatRedstoneRenderer.RedstoneRenderState> {

    override fun createRenderState(): RedstoneRenderState = RedstoneRenderState()

    override fun extractRenderState(
        blockEntity: CopycatRedstoneBlockEntity,
        blockEntityRenderState: RedstoneRenderState,
        tickProgress: Float,
        cameraPos: Vec3,
        @Nullable crumblingOverlay: ModelFeatureRenderer.CrumblingOverlay?
    ) {
        BlockEntityRenderState.extractBase(blockEntity, blockEntityRenderState, crumblingOverlay)
        val level = blockEntity.level ?: run {
            blockEntityRenderState.buffer = null
            return
        }

        val blockState = blockEntityRenderState.blockState
        val baked = Minecraft.getInstance().blockRenderer.getBlockModel(blockState)
        val unwrapped = WrapperBlockStateModel.unwrapCompat(baked)
        blockEntityRenderState.buffer = if (unwrapped is WrapperBlockStateModel) {
            val parts = mutableListOf<BlockModelPart>()
            unwrapped.addPartsWithInfo(level, blockEntityRenderState.blockPos, blockState, RandomSource.create(), parts)
            SuperBufferFactory.getInstance().createForBlock(parts, blockState, PoseStack())
        } else {
            CachedBuffers.block(blockState)
        }
        val progress = easeInOut(blockEntity.getAnimationProgress(tickProgress).coerceIn(0f, 1f))
        if (blockState.hasProperty(BlockStateProperties.ATTACH_FACE)) {
            val face = blockState.getValue(BlockStateProperties.ATTACH_FACE)
            val facing = blockState.getValue(BlockStateProperties.HORIZONTAL_FACING)
            val dir = when (face) {
                AttachFace.FLOOR -> Direction.DOWN
                AttachFace.CEILING -> Direction.UP
                AttachFace.WALL -> facing.opposite
            }
            val distance = progress / 16f
            blockEntityRenderState.dx = dir.stepX * distance
            blockEntityRenderState.dy = dir.stepY * distance
            blockEntityRenderState.dz = dir.stepZ * distance
        } else {
            blockEntityRenderState.dx = 0f
            blockEntityRenderState.dy = -progress / 32f
            blockEntityRenderState.dz = 0f
        }
        blockEntityRenderState.layer = RenderTypes.cutoutMovingBlock()
    }

    override fun submit(
        blockEntityRenderState: RedstoneRenderState,
        poseStack: PoseStack,
        submitNodeCollector: SubmitNodeCollector,
        cameraRenderState: CameraRenderState
    ) {
        val buffer = blockEntityRenderState.buffer ?: return
        submitNodeCollector.submitCustomGeometry(poseStack, blockEntityRenderState.layer) { pose, vertexConsumer ->
            val prepared = buffer.reset<SuperByteBuffer>()
            prepared.translate(
                blockEntityRenderState.dx.toDouble(),
                blockEntityRenderState.dy.toDouble(),
                blockEntityRenderState.dz.toDouble()
            )
            val lit = prepared.light(blockEntityRenderState.lightCoords) as SuperByteBuffer
            lit.renderInto(pose, vertexConsumer)
        }
    }

    class RedstoneRenderState : BlockEntityRenderState() {
        var buffer: SuperByteBuffer? = null
        var layer: RenderType = RenderTypes.cutoutMovingBlock()
        var dx: Float = 0f
        var dy: Float = 0f
        var dz: Float = 0f
    }

    private fun easeInOut(value: Float): Float {
        val x = value.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }
}
