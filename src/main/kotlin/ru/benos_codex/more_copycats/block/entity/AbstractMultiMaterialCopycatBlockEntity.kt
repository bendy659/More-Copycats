package ru.benos_codex.more_copycats.block.entity

import com.zurrtum.create.AllBlocks
import com.zurrtum.create.content.contraptions.StructureTransform
import com.zurrtum.create.content.decoration.copycat.CopycatBlockEntity
import com.zurrtum.create.content.redstone.RoseQuartzLampBlock
import net.minecraft.core.BlockPos
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import ru.benos_codex.more_copycats.mixin.create.block.entity.CopycatBlockEntityAccessor

abstract class AbstractMultiMaterialCopycatBlockEntity(
    pos: BlockPos,
    state: BlockState,
    private val slotCount: Int
) : CopycatBlockEntity(pos, state) {

    protected data class SlotData(val material: BlockState)

    private val materials: MutableMap<Int, BlockState> = mutableMapOf()

    protected fun getSlotMaterial(slot: Int): BlockState {
        val stored = getStoredSlotMaterial(slot)
        return MaterialStateResolver.resolve(level, worldPosition, blockState, stored)
    }

    protected fun getStoredSlotMaterial(slot: Int): BlockState {
        require(slot in 0 until slotCount) { "Material slot out of range: $slot" }
        return if (slot == 0) materials[0] ?: material else materials[slot] ?: defaultMaterial()
    }

    protected fun getSlotConsumedItem(slot: Int): ItemStack {
        require(slot in 0 until slotCount) { "Material slot out of range: $slot" }
        return ItemStack.EMPTY
    }

    protected fun hasCustomMaterial(slot: Int): Boolean =
        !getStoredSlotMaterial(slot).`is`(AllBlocks.COPYCAT_BASE)

    fun getMaxLightEmission(): Int {
        var maxLight = 0
        for (slot in 0 until slotCount) {
            maxLight = maxOf(maxLight, getSlotMaterial(slot).lightEmission)
        }
        return maxLight
    }

    protected fun setSlotMaterial(slot: Int, newMaterial: BlockState, sourceStack: ItemStack) {
        require(slot in 0 until slotCount) { "Material slot out of range: $slot" }

        if (slot == 0) {
            setMaterial(newMaterial)
            setConsumedItem(ItemStack.EMPTY)
            if (newMaterial.`is`(AllBlocks.COPYCAT_BASE)) {
                materials.remove(slot)
            } else {
                materials[slot] = newMaterial
            }
            return
        }

        storeExtraSlot(slot, newMaterial, update = true)
    }

    protected fun clearSlotMaterial(slot: Int) {
        setSlotMaterial(slot, defaultMaterial(), ItemStack.EMPTY)
    }

    protected fun clearSlotConsumedItem(slot: Int) {
        require(slot in 0 until slotCount) { "Material slot out of range: $slot" }
        if (slot == 0) {
            setConsumedItem(ItemStack.EMPTY)
            return
        }

        setChanged()
        syncVisuals()
    }

    protected fun cycleSlotMaterial(slot: Int): Boolean {
        require(slot in 0 until slotCount) { "Material slot out of range: $slot" }
        if (slot == 0) {
            val changed = cycleMaterial()
            if (changed) {
                materials[slot] = material
                setConsumedItem(ItemStack.EMPTY)
            }
            return changed
        }

        val current = getStoredSlotMaterial(slot)
        if (current.`is`(AllBlocks.COPYCAT_BASE)) return false

        val cycled = rotateMaterial(current) ?: return false
        materials[slot] = cycled
        setChanged()
        notifyUpdate()
        syncVisuals()
        return true
    }

    override fun hasCustomMaterial(): Boolean =
        materials.isNotEmpty() || super.hasCustomMaterial()

    override fun transform(be: BlockEntity, transform: StructureTransform) {
        super.transform(be, transform)
        if (material.`is`(AllBlocks.COPYCAT_BASE)) {
            materials.remove(0)
        } else {
            materials[0] = material
        }
        for ((slot, material) in materials.toMap()) {
            if (slot == 0) continue
            materials[slot] = transform.apply(material)
        }
        setConsumedItem(ItemStack.EMPTY)
        notifyUpdate()
    }

    override fun clearContent() {
        super.clearContent()
        materials.clear()
    }

    override fun write(view: ValueOutput, clientPacket: Boolean) {
        super.write(view, clientPacket)

        val materials = view.child("Materials")
        for (slot in 0 until slotCount) {
            val material = getStoredSlotMaterial(slot)
            if (!material.`is`(AllBlocks.COPYCAT_BASE)) {
                materials.store(slot.matKey, BlockState.CODEC, material)
            }
        }
    }

    override fun read(view: ValueInput, clientPacket: Boolean) {
        val previous = materials.toMap()
        super.read(view, clientPacket)

        val legacyPrimary = if (!material.`is`(AllBlocks.COPYCAT_BASE)) material else null
        materials.clear()

        val materialsView = view.child("Materials").orElse(null)
        for (slot in 0 until slotCount) {
            val legacy = readLegacySlot(view, slot)
            val storedMaterial = materialsView?.read(slot.matKey, BlockState.CODEC)?.orElse(null)
            val slotMaterial = (storedMaterial ?: if (slot == 0) legacyPrimary else legacy?.material) ?: continue

            materials[slot] = slotMaterial
            if (slot != 0) {
                storeExtraSlot(slot, slotMaterial, update = false)
            }
        }

        if (0 !in materials && legacyPrimary != null) {
            materials[0] = legacyPrimary
        }

        syncPrimaryMaterialFromStored()

        if (clientPacket && previous != this.materials) {
            syncVisuals()
        }
    }

    protected open fun readLegacySlot(view: ValueInput, slot: Int): SlotData? = null

    protected open fun syncVisuals() {
        val currentLevel = level ?: return
        val state = blockState
        MaterialLightHelper.refresh(currentLevel, worldPosition, state, 16)
    }

    protected fun storeExtraSlot(slot: Int, newMaterial: BlockState, update: Boolean) {
        require(slot in 1 until slotCount) { "Extra material slot out of range: $slot" }
        if (newMaterial.`is`(AllBlocks.COPYCAT_BASE)) {
            materials.remove(slot)
        } else {
            materials[slot] = newMaterial
        }

        if (!update) return
        setChanged()
        notifyUpdate()
        syncVisuals()
    }

    private fun rotateMaterial(current: BlockState): BlockState? {
        return when {
            current.hasProperty(BlockStateProperties.FACING) ->
                current.cycle(BlockStateProperties.FACING)

            current.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ->
                current.setValue(
                    BlockStateProperties.HORIZONTAL_FACING,
                    current.getValue(BlockStateProperties.HORIZONTAL_FACING).clockWise
                )

            current.hasProperty(BlockStateProperties.AXIS) ->
                current.cycle(BlockStateProperties.AXIS)

            current.hasProperty(BlockStateProperties.HORIZONTAL_AXIS) ->
                current.cycle(BlockStateProperties.HORIZONTAL_AXIS)

            else -> null
        }
    }

    private val Int.matKey: String get() = "Mat_$this"

    private fun defaultMaterial(): BlockState = AllBlocks.COPYCAT_BASE.defaultBlockState()

    private fun syncPrimaryMaterialFromStored() {
        val accessor = this as CopycatBlockEntityAccessor
        accessor.moreCopycats_setMaterial(materials[0] ?: defaultMaterial())
        accessor.moreCopycats_setConsumedItem(ItemStack.EMPTY)
    }
}
