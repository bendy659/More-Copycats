package ru.benos_codex.more_copycats.block

import net.minecraft.world.level.block.state.BlockBehaviour.Properties

import com.zurrtum.create.content.decoration.copycat.CopycatBlock
import com.zurrtum.create.content.decoration.copycat.CopycatBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.BlockAndTintGetter
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.LevelEvent
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import ru.benos_codex.more_copycats.MoreCopycats.DEFAULT_MATERIAL
import ru.benos_codex.more_copycats.MoreCopycatsRegister
import ru.benos_codex.more_copycats.block.entity.CopycatBiteBlockEntity

open class CopycatBiteBlock(props: Properties) : CopycatBlock(props) {

    companion object {
        val PLACEMENT_HIT: ThreadLocal<Vec3?> = ThreadLocal()

        private const val PARTS_PER_AXIS = 4
        private const val PART_SIZE = 1.0 / PARTS_PER_AXIS
        private const val PART_PIXELS = 4.0

        fun getIndexFromRelative(relX: Double, relY: Double, relZ: Double): Int {
            val x = (relX * PARTS_PER_AXIS).toInt().coerceIn(0, PARTS_PER_AXIS - 1)
            val y = (relY * PARTS_PER_AXIS).toInt().coerceIn(0, PARTS_PER_AXIS - 1)
            val z = (relZ * PARTS_PER_AXIS).toInt().coerceIn(0, PARTS_PER_AXIS - 1)
            return x + (y * PARTS_PER_AXIS) + (z * PARTS_PER_AXIS * PARTS_PER_AXIS)
        }

        fun getPartBox(pos: BlockPos, index: Int): AABB {
            val ox = index and 3
            val oy = (index shr 2) and 3
            val oz = (index shr 4) and 3
            val minX = pos.x + ox * 0.25
            val minY = pos.y + oy * 0.25
            val minZ = pos.z + oz * 0.25
            return AABB(minX, minY, minZ, minX + 0.25, minY + 0.25, minZ + 0.25)
        }
    }

    fun getTargetIndex(hit: Vec3, pos: BlockPos, face: Direction, addingPart: Boolean, be: CopycatBiteBlockEntity): Int? {
        val rel = hit.subtract(Vec3.atLowerCornerOf(pos))
        val hitEpsilon = 0.0001

        val nudgedX = rel.x - face.stepX * hitEpsilon
        val nudgedY = rel.y - face.stepY * hitEpsilon
        val nudgedZ = rel.z - face.stepZ * hitEpsilon

        val insideX = nudgedX.coerceIn(hitEpsilon, 1.0 - hitEpsilon)
        val insideY = nudgedY.coerceIn(hitEpsilon, 1.0 - hitEpsilon)
        val insideZ = nudgedZ.coerceIn(hitEpsilon, 1.0 - hitEpsilon)

        val hitIndex = getIndexFromRelative(insideX, insideY, insideZ)

        if (!addingPart) return hitIndex

        if (!be.isPartEmpty(hitIndex)) {
            val neighborX = insideX + face.stepX * PART_SIZE
            val neighborY = insideY + face.stepY * PART_SIZE
            val neighborZ = insideZ + face.stepZ * PART_SIZE

            if (neighborX in 0.0..1.0 && neighborY in 0.0..1.0 && neighborZ in 0.0..1.0) {
                val neighborIndex = getIndexFromRelative(neighborX, neighborY, neighborZ)
                if (be.isPartEmpty(neighborIndex)) return neighborIndex
            }
            return null
        }

        return hitIndex
    }

    private fun generateShape(blockGetter: BlockGetter, pos: BlockPos): VoxelShape {
        val blockEntity = blockGetter.getBlockEntity(pos) as? CopycatBiteBlockEntity
        var shape = Shapes.empty()
        blockEntity?.let {
            for (i in 0 until CopycatBiteBlockEntity.MAX_ITEM) {
                if (!blockEntity.isPartEmpty(i)) {
                    val x = (i % PARTS_PER_AXIS) * PART_PIXELS
                    val y = ((i / PARTS_PER_AXIS) % PARTS_PER_AXIS) * PART_PIXELS
                    val z = (i / (PARTS_PER_AXIS * PARTS_PER_AXIS)) * PART_PIXELS
                    shape = Shapes.or(shape, box(x, y, z, x + PART_PIXELS, y + PART_PIXELS, z + PART_PIXELS))
                }
            }
        }
        return if (shape.isEmpty) Shapes.empty() else shape
    }

    override fun getBlockEntityType(): BlockEntityType<out CopycatBlockEntity>? =
        MoreCopycatsRegister.BITE_BE

    override fun canConnectTexturesToward(p0: BlockAndTintGetter?, p1: BlockPos?, p2: BlockPos?, p3: BlockState?): Boolean = true

    override fun setPlacedBy(level: Level, pos: BlockPos, state: BlockState, placer: LivingEntity?, stack: ItemStack) {
        super.setPlacedBy(level, pos, state, placer, stack)
        if (level.isClientSide) return
        val blockEntity = level.getBlockEntity(pos) as? CopycatBiteBlockEntity ?: return
        val hitPos = PLACEMENT_HIT.get()
        val index = if (hitPos != null) getIndexFromRelative(hitPos.x - pos.x, hitPos.y - pos.y, hitPos.z - pos.z) else 0
        blockEntity.addPart(index)
    }

    override fun canBeReplaced(state: BlockState, ctx: BlockPlaceContext): Boolean {
        if (ctx.player?.isShiftKeyDown == true) return super.canBeReplaced(state, ctx)
        if (ctx.itemInHand.`is`(this.asItem())) {
            val be = ctx.level.getBlockEntity(ctx.clickedPos) as? CopycatBiteBlockEntity ?: return false
            return getTargetIndex(ctx.clickLocation, ctx.clickedPos, ctx.clickedFace, true, be) != null
        }
        return super.canBeReplaced(state, ctx)
    }

    override fun useItemOn(stack: ItemStack, state: BlockState, level: Level, pos: BlockPos, player: Player, hand: InteractionHand, hitResult: BlockHitResult): InteractionResult {
        val be = level.getBlockEntity(pos) as? CopycatBiteBlockEntity ?: return InteractionResult.PASS

        if (stack.`is`(MoreCopycatsRegister.UV_TOOL)) {
            val index = getTargetIndex(hitResult.location, pos, hitResult.direction, false, be) ?: return InteractionResult.PASS
            if (be.isPartEmpty(index)) return InteractionResult.PASS
            if (level.isClientSide) return InteractionResult.SUCCESS
            player.openMenu(ru.benos_codex.more_copycats.menu.CopycatUvToolMenuProvider(pos, index, hitResult.direction.ordinal))
            return InteractionResult.SUCCESS
        }

        if (stack.`is`(this.asItem())) {
            val index = getTargetIndex(hitResult.location, pos, hitResult.direction, true, be)
            if (index != null) {
                if (level.isClientSide) return InteractionResult.SUCCESS
                be.addPart(index)
                level.playSound(null, pos, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 1f, 1f)
                if (!player.isCreative) stack.shrink(1)
                return InteractionResult.SUCCESS
            }
            return InteractionResult.PASS
        }

        val material = getAcceptedBlockState(level, pos, stack, null)
        if (material != null) {
            if (be.isEmpty) return InteractionResult.PASS
            val index = getTargetIndex(hitResult.location, pos, hitResult.direction, false, be) ?: return InteractionResult.CONSUME
            if (be.isPartEmpty(index)) return InteractionResult.CONSUME

            val toolInOffhand = player.offhandItem.`is`(MoreCopycatsRegister.UV_TOOL)
            if (level.isClientSide) return InteractionResult.SUCCESS

            if (toolInOffhand && hand == InteractionHand.MAIN_HAND) {
                if (!player.isCreative && stack.count < 6) return InteractionResult.CONSUME
                be.setMaterialAllFaces(index, material)
                level.playSound(null, pos, material.soundType.placeSound, SoundSource.BLOCKS, 1f, 1f)
                if (!player.isCreative) stack.shrink(6)
                return InteractionResult.SUCCESS
            }

            val face = hitResult.direction
            be.setMaterialFace(index, face, material)
            level.playSound(null, pos, material.soundType.placeSound, SoundSource.BLOCKS, 1f, 1f)
            if (!player.isCreative) stack.shrink(1)
            return InteractionResult.SUCCESS
        }
        return InteractionResult.PASS
    }

    override fun onWrenched(state: BlockState, context: UseOnContext): InteractionResult {
        val level = context.level
        val pos = context.clickedPos
        val be = level.getBlockEntity(pos) as? CopycatBiteBlockEntity ?: return InteractionResult.PASS
        val player = context.player ?: return InteractionResult.PASS

        if (player.isShiftKeyDown) {
            if (level.isClientSide) return InteractionResult.SUCCESS

            for (i in 0 until CopycatBiteBlockEntity.MAX_ITEM) {
                if (!be.isPartEmpty(i)) {
                    player.inventory.placeItemBackInInventory(ItemStack(this.asItem()))
                }
            }

            level.playSound(null, pos, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 1f, 1f)
            level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(state))
            level.removeBlock(pos, false)
            return InteractionResult.SUCCESS
        }

        val index = getTargetIndex(context.clickLocation, pos, context.clickedFace, false, be) ?: return InteractionResult.PASS

        if (!be.isPartEmpty(index)) {
            if (level.isClientSide) return InteractionResult.SUCCESS

            val face = context.clickedFace
            if (be.isFaceHasMaterial(index, face)) {
                val removedState = be.getFaceState(index, face) ?: DEFAULT_MATERIAL
                level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 1f, 1f)
                be.clearMaterialFace(index, face)
                level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(removedState))
                return InteractionResult.SUCCESS
            } else {
                player.inventory.placeItemBackInInventory(ItemStack(this))
                level.playSound(null, pos, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 0.75f, 1.2f)
                be.removePart(index)
            }

            level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(DEFAULT_MATERIAL))

            if (be.isEmpty) {
                level.setBlock(pos, DEFAULT_MATERIAL, 3)
            }

            return InteractionResult.SUCCESS
        }

        return super.onWrenched(state, context)
    }

    override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape =
        generateShape(level, pos)

    override fun getCollisionShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape =
        generateShape(level, pos)
}
