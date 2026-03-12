package ru.benos_codex.more_copycats.client.renderer

import com.mojang.blaze3d.vertex.PoseStack
import com.zurrtum.create.client.catnip.render.CachedBuffers
import com.zurrtum.create.client.catnip.render.SuperBufferFactory
import com.zurrtum.create.client.catnip.render.SuperByteBuffer
import com.zurrtum.create.client.content.kinetics.base.KineticBlockEntityRenderer
import com.zurrtum.create.client.content.kinetics.base.KineticBlockEntityRenderer.KineticRenderState
import com.zurrtum.create.client.infrastructure.model.WrapperBlockStateModel
import com.zurrtum.create.content.kinetics.base.RotatedPillarKineticBlock
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.feature.ModelFeatureRenderer
import net.minecraft.client.renderer.block.model.BlockModelPart
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.core.BlockPos
import net.minecraft.util.RandomSource
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import ru.benos_codex.more_copycats.MoreCopycatsRegister
import ru.benos_codex.more_copycats.block.CopycatEncasedShaftBlock
import ru.benos_codex.more_copycats.block.entity.CopycatShaftBlockEntity
import ru.benos_codex.more_copycats.client.model.block.CopycatShaftBlockModel
import java.util.concurrent.ConcurrentHashMap

class CopycatShaftRenderer(context: BlockEntityRendererProvider.Context) :
    KineticBlockEntityRenderer<CopycatShaftBlockEntity, KineticRenderState>(context) {

    override fun extractRenderState(
        be: CopycatShaftBlockEntity,
        state: KineticRenderState,
        tickProgress: Float,
        cameraPos: Vec3,
        crumblingOverlay: ModelFeatureRenderer.CrumblingOverlay?
    ) {
        val level = be.level ?: run {
            state.support = false
            state.model = null
            return
        }

        state.support = false
        updateBaseRenderState(be, state, level, crumblingOverlay)
        state.model = getRotatedModel(be, state)
        state.angle = getAngleForBe(be, state.blockPos, state.axis)
    }

    override fun getRenderedBlockState(be: CopycatShaftBlockEntity): BlockState {
        val state = be.blockState
        if (state.block !is CopycatEncasedShaftBlock)
            return state

        return MoreCopycatsRegister.SHAFT_BLOCK
            .defaultBlockState()
            .setValue(RotatedPillarKineticBlock.AXIS, state.getValue(RotatedPillarKineticBlock.AXIS))
    }

    override fun getRenderType(be: CopycatShaftBlockEntity, state: BlockState): RenderType =
        RenderTypes.cutoutMovingBlock()

    override fun getRotatedModel(be: CopycatShaftBlockEntity, state: KineticRenderState): SuperByteBuffer {
        val level = be.level ?: return CachedBuffers.block(state.blockState)

        ensureModelCacheFresh()
        val key = buildModelCacheKey(be, level, state.blockPos, state.blockState)
        MODEL_CACHE[key]?.let { return it }

        val baked = Minecraft.getInstance().blockRenderer.getBlockModel(state.blockState)
        val wrapped = WrapperBlockStateModel.unwrapCompat(baked)
        val built = when (wrapped) {
            is CopycatShaftBlockModel -> {
                val parts = mutableListOf<BlockModelPart>()
                wrapped.addRotatingPartsWithInfo(level, state.blockPos, state.blockState, RandomSource.create(), parts)
                if (parts.isEmpty())
                    CachedBuffers.block(state.blockState)
                else
                    SuperBufferFactory.getInstance().createForBlock(parts, state.blockState, PoseStack())
            }

            else ->
                CachedBuffers.block(state.blockState)
        }

        if (MODEL_CACHE.size >= MAX_CACHE_SIZE)
            MODEL_CACHE.clear()

        MODEL_CACHE[key] = built
        return built
    }

    private fun buildModelCacheKey(
        be: CopycatShaftBlockEntity,
        level: Level,
        pos: BlockPos,
        blockState: BlockState
    ) = ModelCacheKey(
        levelHash = System.identityHashCode(level),
        pos = pos.asLong(),
        blockStateId = Block.getId(blockState),
        shaftMaterialId = Block.getId(be.getSlotMaterial(CopycatShaftBlockEntity.Slot.MAT_0)),
        northId = Block.getId(level.getBlockState(pos.north())),
        southId = Block.getId(level.getBlockState(pos.south())),
        eastId = Block.getId(level.getBlockState(pos.east())),
        westId = Block.getId(level.getBlockState(pos.west())),
        upId = Block.getId(level.getBlockState(pos.above())),
        downId = Block.getId(level.getBlockState(pos.below()))
    )

    private fun ensureModelCacheFresh() {
        val identity = System.identityHashCode(Minecraft.getInstance().resourceManager)
        if (identity == resourceManagerIdentity)
            return

        MODEL_CACHE.clear()
        resourceManagerIdentity = identity
    }

    private data class ModelCacheKey(
        val levelHash: Int,
        val pos: Long,
        val blockStateId: Int,
        val shaftMaterialId: Int,
        val northId: Int,
        val southId: Int,
        val eastId: Int,
        val westId: Int,
        val upId: Int,
        val downId: Int
    )

    companion object {
        private const val MAX_CACHE_SIZE = 512
        private val MODEL_CACHE = ConcurrentHashMap<ModelCacheKey, SuperByteBuffer>()

        @Volatile
        private var resourceManagerIdentity: Int = -1

        fun clearModelCache() {
            MODEL_CACHE.clear()
            resourceManagerIdentity = -1
        }
    }
}
