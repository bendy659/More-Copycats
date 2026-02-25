package ru.benos_codex.more_copycats.block

import net.minecraft.core.Direction
import net.minecraft.world.level.block.Block
import net.minecraft.world.phys.shapes.VoxelShape

object CopycatSlidingDoorShapes {
    private val SE_AABB: VoxelShape = Block.box(0.0, 0.0, -13.0, 3.0, 16.0, 3.0)
    private val ES_AABB: VoxelShape = Block.box(-13.0, 0.0, 0.0, 3.0, 16.0, 3.0)
    private val NW_AABB: VoxelShape = Block.box(13.0, 0.0, 13.0, 16.0, 16.0, 29.0)
    private val WN_AABB: VoxelShape = Block.box(13.0, 0.0, 13.0, 29.0, 16.0, 16.0)
    private val SW_AABB: VoxelShape = Block.box(13.0, 0.0, -13.0, 16.0, 16.0, 3.0)
    private val WS_AABB: VoxelShape = Block.box(13.0, 0.0, 0.0, 29.0, 16.0, 3.0)
    private val NE_AABB: VoxelShape = Block.box(0.0, 0.0, 13.0, 3.0, 16.0, 29.0)
    private val EN_AABB: VoxelShape = Block.box(-13.0, 0.0, 13.0, 3.0, 16.0, 16.0)

    fun get(facing: Direction, hingeRight: Boolean): VoxelShape = when (facing) {
        Direction.SOUTH -> if (hingeRight) ES_AABB else WS_AABB
        Direction.WEST -> if (hingeRight) SW_AABB else NW_AABB
        Direction.NORTH -> if (hingeRight) WN_AABB else EN_AABB
        else -> if (hingeRight) NE_AABB else SE_AABB
    }
}
