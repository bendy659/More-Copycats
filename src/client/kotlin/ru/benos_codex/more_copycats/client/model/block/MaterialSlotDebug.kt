package ru.benos_codex.more_copycats.client.model.block

import com.zurrtum.create.AllBlocks
import net.minecraft.client.renderer.block.model.BakedQuad
import net.minecraft.core.Direction
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import ru.benos_codex.more_copycats.MoreCopycats

object MaterialSlotDebug {
    val enabled: Boolean
        get() = MoreCopycats.Config.debugCogwheelSlotColors

    fun material(slot: Int, hasCustom: Boolean, fallback: BlockState): BlockState {
        if (!enabled) return fallback
        return palette(slot, hasCustom).side
    }

    fun materialForFace(slot: Int, hasCustom: Boolean, face: Direction, fallback: BlockState): BlockState {
        if (!enabled) return fallback
        val palette = palette(slot, hasCustom)
        return when (face) {
            Direction.UP -> palette.up
            Direction.DOWN -> palette.down
            else -> palette.side
        }
    }

    fun references(
        slot: Int,
        hasCustom: Boolean,
        collector: (BlockState) -> Map<Direction, BakedQuad>
    ): Map<Direction, BakedQuad>? {
        if (!enabled) return null

        val palette = palette(slot, hasCustom)
        val sideRefs = collector(palette.side)
        val upRefs = collector(palette.up)
        val downRefs = collector(palette.down)
        val refs = mutableMapOf<Direction, BakedQuad>()

        for (direction in Direction.entries) {
            val quad = when (direction) {
                Direction.UP -> upRefs[Direction.UP] ?: sideRefs[Direction.UP] ?: downRefs[Direction.UP]
                Direction.DOWN -> downRefs[Direction.DOWN] ?: sideRefs[Direction.DOWN] ?: upRefs[Direction.DOWN]
                else -> sideRefs[direction] ?: upRefs[direction] ?: downRefs[direction]
            }
            if (quad != null) refs[direction] = quad
        }

        return refs
    }

    fun single(fallback: BlockState): BlockState =
        material(0, !fallback.`is`(AllBlocks.COPYCAT_BASE), fallback)

    fun ambientOcclusion(defaultValue: Boolean): Boolean =
        if (enabled) false else defaultValue

    private fun palette(slot: Int, hasCustom: Boolean): DebugPalette {
        if (!hasCustom) {
            return DebugPalette(
                side = Blocks.BLUE_CONCRETE.defaultBlockState(),
                up = Blocks.LIGHT_BLUE_CONCRETE.defaultBlockState(),
                down = Blocks.CYAN_CONCRETE.defaultBlockState()
            )
        }

        return when (slot) {
            0 -> DebugPalette(
                side = Blocks.RED_CONCRETE.defaultBlockState(),
                up = Blocks.PINK_CONCRETE.defaultBlockState(),
                down = Blocks.BROWN_CONCRETE.defaultBlockState()
            )
            1 -> DebugPalette(
                side = Blocks.LIME_CONCRETE.defaultBlockState(),
                up = Blocks.YELLOW_CONCRETE.defaultBlockState(),
                down = Blocks.GREEN_CONCRETE.defaultBlockState()
            )
            2 -> DebugPalette(
                side = Blocks.YELLOW_CONCRETE.defaultBlockState(),
                up = Blocks.WHITE_CONCRETE.defaultBlockState(),
                down = Blocks.ORANGE_CONCRETE.defaultBlockState()
            )
            3 -> DebugPalette(
                side = Blocks.ORANGE_CONCRETE.defaultBlockState(),
                up = Blocks.YELLOW_CONCRETE.defaultBlockState(),
                down = Blocks.RED_CONCRETE.defaultBlockState()
            )
            4 -> DebugPalette(
                side = Blocks.MAGENTA_CONCRETE.defaultBlockState(),
                up = Blocks.PINK_CONCRETE.defaultBlockState(),
                down = Blocks.PURPLE_CONCRETE.defaultBlockState()
            )
            5 -> DebugPalette(
                side = Blocks.CYAN_CONCRETE.defaultBlockState(),
                up = Blocks.LIGHT_BLUE_CONCRETE.defaultBlockState(),
                down = Blocks.BLUE_CONCRETE.defaultBlockState()
            )
            6 -> DebugPalette(
                side = Blocks.WHITE_CONCRETE.defaultBlockState(),
                up = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState(),
                down = Blocks.GRAY_CONCRETE.defaultBlockState()
            )
            else -> DebugPalette(
                side = Blocks.PINK_CONCRETE.defaultBlockState(),
                up = Blocks.MAGENTA_CONCRETE.defaultBlockState(),
                down = Blocks.PURPLE_CONCRETE.defaultBlockState()
            )
        }
    }

    private data class DebugPalette(
        val side: BlockState,
        val up: BlockState,
        val down: BlockState
    )
}
