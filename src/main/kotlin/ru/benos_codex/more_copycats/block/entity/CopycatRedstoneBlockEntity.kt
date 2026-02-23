package ru.benos_codex.more_copycats.block.entity

import com.mojang.serialization.Codec
import com.zurrtum.create.catnip.animation.LerpedFloat
import com.zurrtum.create.content.decoration.copycat.CopycatBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import ru.benos_codex.more_copycats.block.CopycatPressurePlateBlock
import kotlin.math.abs

class CopycatRedstoneBlockEntity(pos: BlockPos, state: BlockState) : CopycatBlockEntity(pos, state) {
    companion object {
        private const val DEFAULT_HOLD_TICKS = 20
        private const val MIN_HOLD_TICKS = 1
        private const val MAX_HOLD_TICKS = 20 * 60
        private const val ANIM_SPEED_PRESS = 0.22
        private const val ANIM_SPEED_RELEASE = 0.30
        private const val SNAP_EPSILON = 0.0001
    }

    private var holdTicks: Int = DEFAULT_HOLD_TICKS
    private var remainingTicks: Int = 0
    private var wasPowered: Boolean = false
    private var animationInitialized: Boolean = false
    private val pressedAnimation: LerpedFloat = LerpedFloat.linear()
        .startWithValue(0.0)
        .chase(0.0, ANIM_SPEED_RELEASE, LerpedFloat.Chaser.EXP)

    fun getHoldTicks(fallback: Int): Int {
        val defaultTicks = fallback.coerceIn(MIN_HOLD_TICKS, MAX_HOLD_TICKS)
        return holdTicks.coerceAtLeast(defaultTicks)
    }

    fun setHoldTicks(ticks: Int) {
        holdTicks = ticks.coerceIn(MIN_HOLD_TICKS, MAX_HOLD_TICKS)
        setChanged()
        notifyUpdate()
    }

    fun getAnimationProgress(): Float = pressedAnimation.getValue()

    fun getAnimationProgress(partialTicks: Float): Float = pressedAnimation.getValue(partialTicks)

    fun refreshHoldTimer() {
        remainingTicks = getHoldTicks(DEFAULT_HOLD_TICKS)
        wasPowered = true
        pressedAnimation.chase(1.0, ANIM_SPEED_PRESS, LerpedFloat.Chaser.EXP)
    }

    fun hasActiveHoldTimer(): Boolean = remainingTicks > 0

    override fun tick() {
        super.tick()
        level ?: return
        val state = blockState
        val powered = state.hasProperty(BlockStateProperties.POWERED) && state.getValue(BlockStateProperties.POWERED)
        val hold = getHoldTicks(DEFAULT_HOLD_TICKS)
        val plateContact = powered && isPressurePlateContact(state)
        if (powered) {
            if (plateContact) {
                remainingTicks = hold
            } else if (!wasPowered) {
                remainingTicks = hold
            } else if (remainingTicks > 0) {
                remainingTicks--
            }
        } else {
            remainingTicks = 0
        }
        wasPowered = powered

        // While powered, release smoothly across remaining cooldown ticks.
        // Refreshing hold timer (button re-press / entity on plate) pushes it back to 1.0.
        val target = when {
            !powered -> 0.0
            plateContact -> 1.0
            hold <= 0 -> 0.0
            else -> (remainingTicks.toDouble() / hold.toDouble()).coerceIn(0.0, 1.0)
        }
        val speed = if (target > pressedAnimation.getValue().toDouble()) ANIM_SPEED_PRESS else ANIM_SPEED_RELEASE

        pressedAnimation.chase(target, speed, LerpedFloat.Chaser.EXP)
        val before = pressedAnimation.getValue().toDouble()
        pressedAnimation.tickChaser()
        var after = pressedAnimation.getValue().toDouble()
        if (abs(after - target) <= SNAP_EPSILON) {
            pressedAnimation.startWithValue(target)
            after = target
        }
        val changed = abs(before - after) > SNAP_EPSILON
        if (!changed) return
    }

    private fun isPressurePlateContact(state: BlockState): Boolean {
        if (state.block !is CopycatPressurePlateBlock) return false
        val currentLevel = level ?: return false
        val bounds = CopycatPressurePlateBlock.TOUCH_AABB.move(worldPosition)
        return currentLevel.getEntities(null, bounds) { entity ->
            !entity.isSpectator && !entity.isIgnoringBlockTriggers
        }.isNotEmpty()
    }

    override fun write(view: ValueOutput, clientPacket: Boolean) {
        super.write(view, clientPacket)
        if (holdTicks != DEFAULT_HOLD_TICKS) {
            view.store("HoldTicks", Codec.INT, holdTicks)
        }
        if (remainingTicks > 0) {
            view.store("RemainingTicks", Codec.INT, remainingTicks)
        }
    }

    override fun read(view: ValueInput, clientPacket: Boolean) {
        super.read(view, clientPacket)
        holdTicks = view.read("HoldTicks", Codec.INT).orElse(DEFAULT_HOLD_TICKS).coerceIn(MIN_HOLD_TICKS, MAX_HOLD_TICKS)
        remainingTicks = view.read("RemainingTicks", Codec.INT).orElse(0).coerceAtLeast(0)

        val state = blockState
        if (state.hasProperty(BlockStateProperties.POWERED)) {
            val powered = state.getValue(BlockStateProperties.POWERED)
            wasPowered = powered
            val hold = getHoldTicks(DEFAULT_HOLD_TICKS)
            if (powered && remainingTicks <= 0) {
                remainingTicks = hold
            }
            val value = when {
                !powered -> 0.0
                hold <= 0 -> 0.0
                else -> (remainingTicks.toDouble() / hold.toDouble()).coerceIn(0.0, 1.0)
            }
            if (!animationInitialized || !clientPacket) {
                pressedAnimation.startWithValue(value)
                animationInitialized = true
            }
            val speed = if (value > pressedAnimation.getValue().toDouble()) ANIM_SPEED_PRESS else ANIM_SPEED_RELEASE
            pressedAnimation.chase(value, speed, LerpedFloat.Chaser.EXP)
        }

        if (clientPacket)
            level?.sendBlockUpdated(worldPosition, state, state, 8)
    }
}
