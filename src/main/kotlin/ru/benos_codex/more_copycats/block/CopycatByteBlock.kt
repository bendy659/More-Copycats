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
import ru.benos_codex.more_copycats.block.entity.CopycatByteBlockEntity

open class CopycatByteBlock(props: Properties) : AbstractCopycatPartBlock<CopycatByteBlockEntity>(props) {
    companion object {
        val PLACEMENT_HIT: ThreadLocal<Vec3?> = ThreadLocal()
        private const val PART_SIZE = 0.5

        fun getIndexFromRelative(relX: Double, relY: Double, relZ: Double): Int {
            val x = if (relX >= 0.5) 1 else 0
            val y = if (relY >= 0.5) 1 else 0
            val z = if (relZ >= 0.5) 1 else 0

            return x + (y * 2) + (z * 4)
        }

        fun getPartBox(pos: BlockPos, index: Int): AABB {
            val ox = index and 1
            val oy = (index shr 1) and 1
            val oz = (index shr 2) and 1
            val minX = pos.x + ox * 0.5
            val minY = pos.y + oy * 0.5
            val minZ = pos.z + oz * 0.5

            return AABB(minX, minY, minZ, minX + 0.5, minY + 0.5, minZ + 0.5)
        }
    }

    override val maxParts: Int = CopycatByteBlockEntity.MAX_ITEM
    override val placementHit: ThreadLocal<Vec3?> = PLACEMENT_HIT

    override fun getPartBlockEntity(level: BlockGetter, pos: BlockPos): CopycatByteBlockEntity? =
        level.getBlockEntity(pos) as? CopycatByteBlockEntity

    override fun getTargetIndex(
        hit: Vec3,
        pos: BlockPos,
        face: Direction,
        addingPart: Boolean,
        blockEntity: CopycatByteBlockEntity
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
        MoreCopycatsRegister.BYTE_BE

    override fun getIndexFromRelative(relX: Double, relY: Double, relZ: Double): Int =
        Companion.getIndexFromRelative(relX, relY, relZ)
}
