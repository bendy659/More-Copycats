package ru.benos_codex.more_copycats.client.renderer

import com.mojang.blaze3d.vertex.PoseStack
import com.zurrtum.create.client.content.kinetics.base.KineticBlockEntityRenderer.KineticRenderState
import com.zurrtum.create.client.content.kinetics.base.KineticBlockEntityRenderer
import com.zurrtum.create.client.catnip.render.CachedBuffers
import com.zurrtum.create.client.catnip.render.SuperBufferFactory
import com.zurrtum.create.client.catnip.render.SuperByteBuffer
import com.zurrtum.create.client.infrastructure.model.WrapperBlockStateModel
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.block.model.BlockModelPart
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.client.renderer.feature.ModelFeatureRenderer
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.state.CameraRenderState
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.util.RandomSource
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import ru.benos_codex.more_copycats.MoreCopycatsRegister
import ru.benos_codex.more_copycats.block.CopycatCogwheelBlock
import ru.benos_codex.more_copycats.block.CopycatEncasedCogwheelBlock
import ru.benos_codex.more_copycats.block.entity.CopycatCogwheelBlockEntity
import ru.benos_codex.more_copycats.client.model.block.CopycatCogwheelBlockModel
import java.util.concurrent.ConcurrentHashMap
import com.zurrtum.create.content.kinetics.simpleRelays.CogWheelBlock.AXIS

class CopycatCogwheelRenderer(context: BlockEntityRendererProvider.Context) :
    KineticBlockEntityRenderer<CopycatCogwheelBlockEntity, CopycatCogwheelRenderer.CogwheelRenderState>(context) {

    override fun createRenderState(): CogwheelRenderState = CogwheelRenderState()

    override fun extractRenderState(
        be: CopycatCogwheelBlockEntity,
        state: CogwheelRenderState,
        tickProgress: Float,
        cameraPos: Vec3,
        crumblingOverlay: ModelFeatureRenderer.CrumblingOverlay?
    ) {
        val level = be.level ?: run {
            state.staticModel = null
            state.shaftModel = null
            state.model = null
            return
        }
        state.support = false
        updateBaseRenderState(be, state, level, crumblingOverlay)
        state.model = createRotatingBuffer(be, level, state.blockPos, state.blockState)
        state.staticModel = createStaticBuffer(be, level, state.blockPos, state.blockState)
        state.shaftModel = createShaftBuffer(be, level, state.blockPos, state.blockState)
        state.angle = getAngleForBe(be, state.blockPos, state.axis)
    }

    override fun submit(
        state: CogwheelRenderState,
        poseStack: PoseStack,
        submitNodeCollector: SubmitNodeCollector,
        cameraRenderState: CameraRenderState
    ) {
        if (state.support) {
            return
        }

        submitNodeCollector.submitCustomGeometry(poseStack, state.layer) { pose, vertexConsumer ->
            state.staticModel?.let { staticModel ->
                val prepared = staticModel.reset<SuperByteBuffer>()
                prepared.light<SuperByteBuffer>(state.lightCoords)
                prepared.color<SuperByteBuffer>(state.color)
                prepared.renderInto(pose, vertexConsumer)
            }

            state.model?.let { rotatingModel ->
                val prepared = rotatingModel.reset<SuperByteBuffer>()
                prepared.light<SuperByteBuffer>(state.lightCoords)
                prepared.rotateCentered(state.angle, state.direction)
                prepared.color<SuperByteBuffer>(state.color)
                prepared.renderInto(pose, vertexConsumer)
            }

            state.shaftModel?.let { shaftModel ->
                val prepared = shaftModel.reset<SuperByteBuffer>()
                prepared.light<SuperByteBuffer>(state.lightCoords)
                prepared.rotateCentered(state.angle, state.direction)
                prepared.color<SuperByteBuffer>(state.color)
                prepared.renderInto(pose, vertexConsumer)
            }
        }
    }

    override fun getRenderType(be: CopycatCogwheelBlockEntity, state: BlockState): RenderType {
        return RenderTypes.cutoutMovingBlock()
    }

    private fun createRotatingBuffer(
        be: CopycatCogwheelBlockEntity,
        level: Level,
        pos: BlockPos,
        blockState: BlockState
    ): SuperByteBuffer {
        ensureModelCacheFresh()
        val rotatingState = rotatingState(blockState)
        val key = buildModelCacheKey(BufferLayer.ROTATING, be, level, pos, blockState)
        MODEL_CACHE[key]?.let { return it }

        val baked = Minecraft.getInstance().blockRenderer.getBlockModel(rotatingState)
        val wrapped = WrapperBlockStateModel.unwrapCompat(baked)
        val built = when (wrapped) {
            is CopycatCogwheelBlockModel -> {
                val parts = mutableListOf<BlockModelPart>()
                wrapped.addPartsWithInfo(level, pos, rotatingState, RandomSource.create(), parts)
                val filtered = if (blockState.block is CopycatEncasedCogwheelBlock) {
                    parts
                        .filterNot(::looksLikeShaftPart)
                        .filterNot(::looksLikeAxisPart)
                } else {
                    parts
                }
                SuperBufferFactory.getInstance().createForBlock(filtered, rotatingState, PoseStack())
            }
            is WrapperBlockStateModel -> {
                val parts = mutableListOf<BlockModelPart>()
                wrapped.addPartsWithInfo(level, pos, rotatingState, RandomSource.create(), parts)
                val filtered = if (blockState.block is CopycatEncasedCogwheelBlock) {
                    parts.filterNot(::looksLikeShaftPart)
                } else {
                    parts
                }
                SuperBufferFactory.getInstance().createForBlock(filtered, rotatingState, PoseStack())
            }
            else -> {
                CachedBuffers.block(rotatingState)
            }
        }

        if (MODEL_CACHE.size >= MAX_CACHE_SIZE) {
            MODEL_CACHE.clear()
        }
        MODEL_CACHE[key] = built
        return built
    }

    private fun rotatingState(state: BlockState): BlockState {
        if (state.block !is CopycatEncasedCogwheelBlock) {
            return state
        }
        val base = if ((state.block as CopycatEncasedCogwheelBlock).isLargeCog)
            MoreCopycatsRegister.LARGE_COGWHEEL_BLOCK
        else
            MoreCopycatsRegister.COGWHEEL_BLOCK
        return base
            .defaultBlockState()
            .setValue(AXIS, state.getValue(AXIS))
    }

    private fun createStaticBuffer(
        be: CopycatCogwheelBlockEntity,
        level: Level,
        pos: BlockPos,
        blockState: BlockState
    ): SuperByteBuffer? =
        null

    private fun createShaftBuffer(
        be: CopycatCogwheelBlockEntity,
        level: Level,
        pos: BlockPos,
        blockState: BlockState
    ): SuperByteBuffer? {
        if (blockState.block !is CopycatEncasedCogwheelBlock)
            return null

        ensureModelCacheFresh()
        val key = buildModelCacheKey(BufferLayer.SHAFT, be, level, pos, blockState)
        MODEL_CACHE[key]?.let { return it }

        val baked = Minecraft.getInstance().blockRenderer.getBlockModel(blockState)
        val wrapped = WrapperBlockStateModel.unwrapCompat(baked)
        if (wrapped !is CopycatCogwheelBlockModel)
            return null

        val parts = mutableListOf<BlockModelPart>()
        wrapped.addShaftPartsWithInfo(level, pos, blockState, RandomSource.create(), parts)
        if (parts.isEmpty())
            return null

        val built = SuperBufferFactory.getInstance().createForBlock(parts, blockState, PoseStack())
        if (MODEL_CACHE.size >= MAX_CACHE_SIZE)
            MODEL_CACHE.clear()

        MODEL_CACHE[key] = built
        return built
    }

    private fun buildModelCacheKey(
        layer: BufferLayer,
        be: CopycatCogwheelBlockEntity,
        level: Level,
        pos: BlockPos,
        blockState: BlockState
    ): ModelCacheKey {
        var slotHash = 1
        for (slot in CopycatCogwheelBlockEntity.Slot.entries) {
            slotHash = 31 * slotHash + Block.getId(be.getSlotMaterial(slot))
        }
        return ModelCacheKey(
            layer = layer,
            levelHash = System.identityHashCode(level),
            pos = pos.asLong(),
            blockStateId = Block.getId(blockState),
            slotHash = slotHash,
            northId = Block.getId(level.getBlockState(pos.north())),
            southId = Block.getId(level.getBlockState(pos.south())),
            eastId = Block.getId(level.getBlockState(pos.east())),
            westId = Block.getId(level.getBlockState(pos.west())),
            upId = Block.getId(level.getBlockState(pos.above())),
            downId = Block.getId(level.getBlockState(pos.below()))
        )
    }

    private fun ensureModelCacheFresh() {
        val identity = System.identityHashCode(Minecraft.getInstance().resourceManager)
        if (identity != resourceManagerIdentity) {
            MODEL_CACHE.clear()
            resourceManagerIdentity = identity
        }
    }

    private fun looksLikeShaftPart(part: BlockModelPart): Boolean {
        val bounds = estimateBounds(part) ?: return false
        val sizes = listOf(
            bounds.maxX - bounds.minX,
            bounds.maxY - bounds.minY,
            bounds.maxZ - bounds.minZ
        ).sorted()
        return sizes[2] >= 0.92f && sizes[1] <= 0.36f && sizes[0] <= 0.36f
    }

    private fun looksLikeAxisPart(part: BlockModelPart): Boolean {
        val bounds = estimateBounds(part) ?: return false
        val sizes = listOf(
            bounds.maxX - bounds.minX,
            bounds.maxY - bounds.minY,
            bounds.maxZ - bounds.minZ
        ).sorted()
        if (sizes[2] < 0.45f || sizes[2] > 0.55f) {
            return false
        }
        if (sizes[1] > 0.30f || sizes[0] > 0.30f) {
            return false
        }

        val centerX = (bounds.minX + bounds.maxX) * 0.5f
        val centerY = (bounds.minY + bounds.maxY) * 0.5f
        val centerZ = (bounds.minZ + bounds.maxZ) * 0.5f
        val centers = listOf(centerX, centerY, centerZ).sorted()
        return centers[0] >= 0.20f && centers[2] <= 0.80f
    }

    private fun estimateBounds(part: BlockModelPart): Bounds? {
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var minZ = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        var maxZ = Float.NEGATIVE_INFINITY
        var any = false

        fun include(x: Float, y: Float, z: Float) {
            val nx = normalize(x)
            val ny = normalize(y)
            val nz = normalize(z)
            minX = minOf(minX, nx)
            minY = minOf(minY, ny)
            minZ = minOf(minZ, nz)
            maxX = maxOf(maxX, nx)
            maxY = maxOf(maxY, ny)
            maxZ = maxOf(maxZ, nz)
            any = true
        }

        fun includeQuad(quad: net.minecraft.client.renderer.block.model.BakedQuad) {
            include(quad.position0().x(), quad.position0().y(), quad.position0().z())
            include(quad.position1().x(), quad.position1().y(), quad.position1().z())
            include(quad.position2().x(), quad.position2().y(), quad.position2().z())
            include(quad.position3().x(), quad.position3().y(), quad.position3().z())
        }

        for (quad in part.getQuads(null)) {
            includeQuad(quad)
        }
        for (direction in Direction.entries) {
            for (quad in part.getQuads(direction)) {
                includeQuad(quad)
            }
        }

        if (!any) {
            return null
        }

        return Bounds(minX, minY, minZ, maxX, maxY, maxZ)
    }

    private fun normalize(value: Float): Float {
        return if (kotlin.math.abs(value) > 2.0f) value / 16.0f else value
    }

    private data class ModelCacheKey(
        val layer: BufferLayer,
        val levelHash: Int,
        val pos: Long,
        val blockStateId: Int,
        val slotHash: Int,
        val northId: Int,
        val southId: Int,
        val eastId: Int,
        val westId: Int,
        val upId: Int,
        val downId: Int
    )

    class CogwheelRenderState : KineticRenderState() {
        var staticModel: SuperByteBuffer? = null
        var shaftModel: SuperByteBuffer? = null
    }

    private enum class BufferLayer {
        ROTATING,
        STATIC,
        SHAFT
    }

    private data class Bounds(
        val minX: Float,
        val minY: Float,
        val minZ: Float,
        val maxX: Float,
        val maxY: Float,
        val maxZ: Float
    )

    companion object {
        private const val MAX_CACHE_SIZE = 8192
        private val MODEL_CACHE = ConcurrentHashMap<ModelCacheKey, SuperByteBuffer>()

        @Volatile
        private var resourceManagerIdentity: Int = -1

        fun clearModelCache() {
            MODEL_CACHE.clear()
            resourceManagerIdentity = -1
        }
    }
}
