package ru.benos_codex.more_copycats.client.renderer

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.zurrtum.create.catnip.theme.Color
import com.zurrtum.create.client.catnip.animation.AnimationTickHolder
import com.zurrtum.create.client.catnip.render.CachedBuffers
import com.zurrtum.create.client.catnip.render.SuperBufferFactory
import com.zurrtum.create.client.catnip.render.SuperByteBuffer
import com.zurrtum.create.client.content.kinetics.base.KineticBlockEntityRenderer
import com.zurrtum.create.client.infrastructure.model.WrapperBlockStateModel
import com.mojang.math.Axis as MojangAxis
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
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.Direction.Axis
import net.minecraft.util.RandomSource
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.phys.Vec3
import ru.benos_codex.more_copycats.MoreCopycatsRegister
import ru.benos_codex.more_copycats.block.CopycatGearboxBlock
import ru.benos_codex.more_copycats.block.entity.CopycatGearboxBlockEntity
import ru.benos_codex.more_copycats.client.model.block.CopycatShaftBlockModel
import java.util.concurrent.ConcurrentHashMap

class CopycatGearboxRenderer(context: BlockEntityRendererProvider.Context) :
    BlockEntityRenderer<CopycatGearboxBlockEntity, CopycatGearboxRenderer.GearboxRenderState> {

    override fun createRenderState(): GearboxRenderState = GearboxRenderState()

    override fun extractRenderState(
        be: CopycatGearboxBlockEntity,
        state: GearboxRenderState,
        tickProgress: Float,
        cameraPos: Vec3,
        crumblingOverlay: ModelFeatureRenderer.CrumblingOverlay?
    ) {
        BlockEntityRenderState.extractBase(be, state, crumblingOverlay)
        state.layer = RenderTypes.cutoutMovingBlock()
        state.color = KineticBlockEntityRenderer.getColor(be)

        val level = be.level ?: run {
            state.down = null
            state.up = null
            state.north = null
            state.south = null
            state.west = null
            state.east = null
            return
        }

        val boxAxis = state.blockState.getValue(BlockStateProperties.AXIS)
        val pos = state.blockPos
        val speed = be.speed
        val time = AnimationTickHolder.getRenderTime(level)
        val baseAngle = time * speed * 3.0f / 10.0f % 360.0f
        val source = be.source?.takeIf { speed != 0.0f }?.subtract(pos)
        val sourceFacing = source?.let { Direction.getApproximateNearest(it.x.toFloat(), it.y.toFloat(), it.z.toFloat()) }

        if (boxAxis != Axis.X) {
            val offset = KineticBlockEntityRenderer.getRotationOffsetForPosition(be, pos, Axis.X)
            val angle = getAngle(baseAngle, offset, Direction.EAST, source, sourceFacing)
            if (!isLocked(state.blockState, Direction.WEST)) {
                state.west = createShaftBuffer(be, level, pos, Axis.X, false)
                state.westAngle = -angle
            } else {
                state.west = null
            }
            if (!isLocked(state.blockState, Direction.EAST)) {
                state.east = createShaftBuffer(be, level, pos, Axis.X, true)
                state.eastAngle = angle
            } else {
                state.east = null
            }
        } else {
            state.west = null
            state.east = null
        }

        if (boxAxis != Axis.Y) {
            val offset = KineticBlockEntityRenderer.getRotationOffsetForPosition(be, pos, Axis.Y)
            val angle = getAngle(baseAngle, offset, Direction.UP, source, sourceFacing)
            if (!isLocked(state.blockState, Direction.DOWN)) {
                state.down = createShaftBuffer(be, level, pos, Axis.Y, false)
                state.downAngle = -angle
            } else {
                state.down = null
            }
            if (!isLocked(state.blockState, Direction.UP)) {
                state.up = createShaftBuffer(be, level, pos, Axis.Y, true)
                state.upAngle = angle
            } else {
                state.up = null
            }
        } else {
            state.down = null
            state.up = null
        }

        if (boxAxis != Axis.Z) {
            val offset = KineticBlockEntityRenderer.getRotationOffsetForPosition(be, pos, Axis.Z)
            val angle = getAngle(baseAngle, offset, Direction.SOUTH, source, sourceFacing)
            if (!isLocked(state.blockState, Direction.NORTH)) {
                state.north = createShaftBuffer(be, level, pos, Axis.Z, false)
                state.northAngle = -angle
            } else {
                state.north = null
            }
            if (!isLocked(state.blockState, Direction.SOUTH)) {
                state.south = createShaftBuffer(be, level, pos, Axis.Z, true)
                state.southAngle = angle
            } else {
                state.south = null
            }
        } else {
            state.north = null
            state.south = null
        }
    }

    override fun submit(
        state: GearboxRenderState,
        matrices: PoseStack,
        queue: SubmitNodeCollector,
        cameraState: CameraRenderState
    ) {
        queue.submitCustomGeometry(matrices, state.layer, state)
    }

    private fun createShaftBuffer(
        be: CopycatGearboxBlockEntity,
        level: Level,
        pos: BlockPos,
        axis: Axis,
        positive: Boolean
    ): SuperByteBuffer {
        ensureModelCacheFresh()
        val shaftState = MoreCopycatsRegister.GEARBOX_SHAFT_BLOCK.defaultBlockState()
            .setValue(BlockStateProperties.AXIS, axis)
        val key = ModelCacheKey(
            levelHash = System.identityHashCode(level),
            pos = pos.asLong(),
            axis = axis,
            positive = positive,
            blockStateId = Block.getId(be.blockState),
            shaftMaterialId = Block.getId(be.getSlotMaterial(CopycatGearboxBlockEntity.Slot.MAT_0)),
            northId = Block.getId(level.getBlockState(pos.north())),
            southId = Block.getId(level.getBlockState(pos.south())),
            eastId = Block.getId(level.getBlockState(pos.east())),
            westId = Block.getId(level.getBlockState(pos.west())),
            upId = Block.getId(level.getBlockState(pos.above())),
            downId = Block.getId(level.getBlockState(pos.below()))
        )
        MODEL_CACHE[key]?.let { return it }

        val baked = Minecraft.getInstance().blockRenderer.getBlockModel(shaftState)
        val wrapped = WrapperBlockStateModel.unwrapCompat(baked)
        val built = when (wrapped) {
            is CopycatShaftBlockModel -> {
                val parts = mutableListOf<BlockModelPart>()
                val poseStack = PoseStack()
                val rotatePositive = if (axis == Axis.Z) !positive else positive
                val halfPositive = if (rotatePositive) !positive else positive
                wrapped.addRotatingHalfPartsWithMaterial(
                    level,
                    pos,
                    shaftState,
                    be.getSlotMaterial(CopycatGearboxBlockEntity.Slot.MAT_0),
                    axis,
                    halfPositive,
                    RandomSource.create(),
                    parts
                )
                if (rotatePositive) {
                    poseStack.translate(0.5, 0.5, 0.5)
                    when (axis) {
                        Axis.X -> poseStack.mulPose(MojangAxis.YP.rotationDegrees(180.0f))
                        Axis.Y -> poseStack.mulPose(MojangAxis.XP.rotationDegrees(180.0f))
                        Axis.Z -> poseStack.mulPose(MojangAxis.YP.rotationDegrees(180.0f))
                    }
                    poseStack.translate(-0.5, -0.5, -0.5)
                }
                if (parts.isEmpty())
                    CachedBuffers.block(shaftState)
                else
                    SuperBufferFactory.getInstance().createForBlock(parts, shaftState, poseStack)
            }

            else ->
                CachedBuffers.block(shaftState)
        }

        if (MODEL_CACHE.size >= MAX_CACHE_SIZE)
            MODEL_CACHE.clear()

        MODEL_CACHE[key] = built
        return built
    }

    private fun ensureModelCacheFresh() {
        val identity = System.identityHashCode(Minecraft.getInstance().resourceManager)
        if (identity == resourceManagerIdentity)
            return

        MODEL_CACHE.clear()
        resourceManagerIdentity = identity
    }

    private fun getAngle(
        angle: Float,
        offset: Float,
        direction: Direction,
        source: BlockPos?,
        sourceFacing: Direction?
    ): Float {
        var adjusted = angle
        if (source != null && sourceFacing != null) {
            if (sourceFacing.axis == direction.axis) {
                adjusted *= if (sourceFacing == direction) 1.0f else -1.0f
            } else if (sourceFacing.axisDirection == direction.axisDirection) {
                adjusted *= -1.0f
            }
        }

        adjusted += offset
        return adjusted / 180.0f * Math.PI.toFloat()
    }

    private fun isLocked(state: BlockState, face: Direction): Boolean {
        return when (face) {
            Direction.NORTH -> state.getValue(CopycatGearboxBlock.LOCK_NORTH)
            Direction.EAST -> state.getValue(CopycatGearboxBlock.LOCK_EAST)
            Direction.SOUTH -> state.getValue(CopycatGearboxBlock.LOCK_SOUTH)
            Direction.WEST -> state.getValue(CopycatGearboxBlock.LOCK_WEST)
            Direction.UP -> state.getValue(CopycatGearboxBlock.LOCK_TOP)
            Direction.DOWN -> state.getValue(CopycatGearboxBlock.LOCK_BOTTOM)
        }
    }

    data class GearboxRenderState(
        var layer: RenderType = RenderTypes.cutoutMovingBlock(),
        var color: Color = Color.WHITE,
        var down: SuperByteBuffer? = null,
        var downAngle: Float = 0.0f,
        var up: SuperByteBuffer? = null,
        var upAngle: Float = 0.0f,
        var north: SuperByteBuffer? = null,
        var northAngle: Float = 0.0f,
        var south: SuperByteBuffer? = null,
        var southAngle: Float = 0.0f,
        var west: SuperByteBuffer? = null,
        var westAngle: Float = 0.0f,
        var east: SuperByteBuffer? = null,
        var eastAngle: Float = 0.0f
    ) : BlockEntityRenderState(), SubmitNodeCollector.CustomGeometryRenderer {

        override fun render(matricesEntry: PoseStack.Pose, vertexConsumer: VertexConsumer) {
            renderModel(down, downAngle, Direction.UP, matricesEntry, vertexConsumer)
            renderModel(up, upAngle, Direction.UP, matricesEntry, vertexConsumer)
            renderModel(north, northAngle, Direction.SOUTH, matricesEntry, vertexConsumer)
            renderModel(south, southAngle, Direction.SOUTH, matricesEntry, vertexConsumer)
            renderModel(west, westAngle, Direction.EAST, matricesEntry, vertexConsumer)
            renderModel(east, eastAngle, Direction.EAST, matricesEntry, vertexConsumer)
        }

        private fun renderModel(
            model: SuperByteBuffer?,
            angle: Float,
            axis: Direction,
            matricesEntry: PoseStack.Pose,
            vertexConsumer: VertexConsumer
        ) {
            if (model == null)
                return

            model.reset<SuperByteBuffer>()
                .light<SuperByteBuffer>(lightCoords)
                .rotateCentered(angle, axis)
                .color<SuperByteBuffer>(color)
                .renderInto(matricesEntry, vertexConsumer)
        }
    }

    private data class ModelCacheKey(
        val levelHash: Int,
        val pos: Long,
        val axis: Axis,
        val positive: Boolean,
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
        private const val MAX_CACHE_SIZE = 2048
        private val MODEL_CACHE = ConcurrentHashMap<ModelCacheKey, SuperByteBuffer>()

        @Volatile
        private var resourceManagerIdentity: Int = -1

        fun clearModelCache() {
            MODEL_CACHE.clear()
            resourceManagerIdentity = -1
        }
    }
}
