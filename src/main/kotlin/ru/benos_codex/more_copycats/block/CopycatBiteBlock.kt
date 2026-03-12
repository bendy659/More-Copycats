package ru.benos_codex.more_copycats.block

import com.zurrtum.create.content.decoration.copycat.CopycatBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.block.state.BlockBehaviour.Properties
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import ru.benos_codex.more_copycats.MoreCopycatsRegister
import ru.benos_codex.more_copycats.block.entity.CopycatBiteBlockEntity

open class CopycatBiteBlock(props: Properties) : AbstractCopycatPartBlock<CopycatBiteBlockEntity>(props) {
    companion object {
        val PLACEMENT_HIT: ThreadLocal<Vec3?> = ThreadLocal()

        private const val PARTS_PER_AXIS = 4
        private const val PART_SIZE = 1.0 / PARTS_PER_AXIS

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

    override val maxParts: Int = CopycatBiteBlockEntity.MAX_ITEM
    override val placementHit: ThreadLocal<Vec3?> = PLACEMENT_HIT

    override fun getPartBlockEntity(level: BlockGetter, pos: BlockPos): CopycatBiteBlockEntity? =
        level.getBlockEntity(pos) as? CopycatBiteBlockEntity

    override fun getTargetIndex(
        hit: Vec3,
        pos: BlockPos,
        face: Direction,
        addingPart: Boolean,
        blockEntity: CopycatBiteBlockEntity
    ): Int? =
        resolveTargetIndex(
            hit = hit,
            pos = pos,
            face = face,
            addingPart = addingPart,
            partStep = PART_SIZE,
            isPartEmpty = blockEntity::isPartEmpty
        )

    override fun getBlockEntityType(): BlockEntityType<out CopycatBlockEntity>? =
        MoreCopycatsRegister.BITE_BE

    override fun getIndexFromRelative(relX: Double, relY: Double, relZ: Double): Int =
        Companion.getIndexFromRelative(relX, relY, relZ)
}
