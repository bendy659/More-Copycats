package ru.benos_codex.more_copycats.client.model.block

import com.zurrtum.create.client.infrastructure.model.CopycatModel
import com.zurrtum.create.content.decoration.copycat.CopycatBlock
import net.minecraft.client.renderer.block.model.BakedQuad
import net.minecraft.client.renderer.block.model.BlockModelPart
import net.minecraft.client.renderer.block.model.BlockStateModel
import net.minecraft.client.renderer.block.model.SimpleModelWrapper
import net.minecraft.client.resources.model.QuadCollection
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.util.RandomSource
import net.minecraft.world.level.BlockAndTintGetter
import net.minecraft.world.level.block.state.BlockState
import ru.benos_codex.more_copycats.block.entity.CopycatFenceWallBlockEntity

class CopycatFenceBlockModel(state: BlockState, unbaked: BlockStateModel.UnbakedRoot) : CopycatModel(state, unbaked) {
    override fun addPartsWithInfo(
        world: BlockAndTintGetter,
        pos: BlockPos,
        state: BlockState,
        block: CopycatBlock,
        material: BlockState,
        random: RandomSource,
        parts: MutableList<BlockModelPart>
    ) {
        val blockEntity = world.getBlockEntity(pos) as? CopycatFenceWallBlockEntity
        val centerMaterial = blockEntity?.getSlotMaterial(CopycatFenceWallBlockEntity.Slot.PRIMARY) ?: material
        val topMaterial = blockEntity?.getSlotMaterial(CopycatFenceWallBlockEntity.Slot.SECONDARY) ?: centerMaterial
        val bottomMaterial = blockEntity?.getSlotMaterial(CopycatFenceWallBlockEntity.Slot.TERTIARY) ?: centerMaterial

        val refsBySlot = mapOf(
            CopycatFenceWallBlockEntity.Slot.PRIMARY to collectMaterialReferences(world, pos, centerMaterial, random),
            CopycatFenceWallBlockEntity.Slot.SECONDARY to collectMaterialReferences(world, pos, topMaterial, random),
            CopycatFenceWallBlockEntity.Slot.TERTIARY to collectMaterialReferences(world, pos, bottomMaterial, random)
        )

        val materialsBySlot = mapOf(
            CopycatFenceWallBlockEntity.Slot.PRIMARY to centerMaterial,
            CopycatFenceWallBlockEntity.Slot.SECONDARY to topMaterial,
            CopycatFenceWallBlockEntity.Slot.TERTIARY to bottomMaterial
        )

        val templateParts = mutableListOf<BlockModelPart>()
        model.collectParts(random, templateParts)

        for (templatePart in templateParts) {
            val fixedSlot = pickPartSlot(templatePart)
            val builder = QuadCollection.Builder()
            addPartRemapped(templatePart, fixedSlot, builder, refsBySlot, materialsBySlot, block, state, world, pos)
            parts += SimpleModelWrapper(builder.build(), templatePart.useAmbientOcclusion(), templatePart.particleIcon())
        }
    }

    private fun collectMaterialReferences(
        world: BlockAndTintGetter,
        pos: BlockPos,
        material: BlockState,
        random: RandomSource
    ): Map<Direction, BakedQuad> {
        val refs = mutableMapOf<Direction, BakedQuad>()
        val materialParts = getMaterialParts(world, pos, material, random, getModelOf(material))

        for (part in materialParts) {
            for (direction in Direction.entries) {
                if (refs.containsKey(direction)) continue

                val direct = part.getQuads(direction)
                if (direct.isNotEmpty()) {
                    refs[direction] = direct[0]
                    continue
                }

                for (quad in part.getQuads(null)) {
                    if (quad.direction() == direction) {
                        refs[direction] = quad
                        break
                    }
                }
            }
        }

        return refs
    }

    private fun addPartRemapped(
        templatePart: BlockModelPart,
        fixedSlot: CopycatFenceWallBlockEntity.Slot?,
        builder: QuadCollection.Builder,
        refsBySlot: Map<CopycatFenceWallBlockEntity.Slot, Map<Direction, BakedQuad>>,
        materialsBySlot: Map<CopycatFenceWallBlockEntity.Slot, BlockState>,
        block: CopycatBlock,
        state: BlockState,
        world: BlockAndTintGetter,
        pos: BlockPos
    ) {
        for (quad in templatePart.getQuads(null)) {
            val slot = fixedSlot ?: pickSlot(quad)
            val refs = refsBySlot[slot].orEmpty()
            val material = materialsBySlot[slot] ?: materialsBySlot.getValue(CopycatFenceWallBlockEntity.Slot.PRIMARY)
            val ref = refs[quad.direction()]
            builder.addUnculledFace(CopycatConnectedTextureHelper.remapQuad(quad, ref?.sprite(), ref, world, pos, state, material))
        }

        for (direction in Direction.entries) {
            for (quad in templatePart.getQuads(direction)) {
                val slot = fixedSlot ?: pickSlot(quad)
                val refs = refsBySlot[slot].orEmpty()
                val material = materialsBySlot[slot] ?: materialsBySlot.getValue(CopycatFenceWallBlockEntity.Slot.PRIMARY)
                val ref = refs[direction]
                val remapped = CopycatConnectedTextureHelper.remapQuad(quad, ref?.sprite(), ref, world, pos, state, material)
                if (block.shouldFaceAlwaysRender(state, direction)) builder.addUnculledFace(remapped)
                else builder.addCulledFace(direction, remapped)
            }
        }
    }

    private fun pickPartSlot(part: BlockModelPart): CopycatFenceWallBlockEntity.Slot? {
        var minY = Float.POSITIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY

        fun include(quad: BakedQuad) {
            minY = minOf(minY, quad.position0().y(), quad.position1().y(), quad.position2().y(), quad.position3().y())
            maxY = maxOf(maxY, quad.position0().y(), quad.position1().y(), quad.position2().y(), quad.position3().y())
        }

        for (quad in part.getQuads(null)) include(quad)
        for (dir in Direction.entries) {
            for (quad in part.getQuads(dir)) include(quad)
        }

        if (!minY.isFinite() || !maxY.isFinite()) return CopycatFenceWallBlockEntity.Slot.PRIMARY

        val scale = if (kotlin.math.max(kotlin.math.abs(minY), kotlin.math.abs(maxY)) > 2.0f) 16.0f else 1.0f
        val eps = scale / 256.0f
        val railBottomMin = (7.0f / 16.0f) * scale
        val railBottomMax = (10.0f / 16.0f) * scale
        val railTopMin = (12.0f / 16.0f) * scale
        val railTopMax = (15.0f / 16.0f) * scale

        if (minY >= railBottomMin - eps && maxY <= railBottomMax + eps) {
            return CopycatFenceWallBlockEntity.Slot.TERTIARY
        }
        if (minY >= railTopMin - eps && maxY <= railTopMax + eps) {
            return CopycatFenceWallBlockEntity.Slot.SECONDARY
        }

        val spansBothRails = minY <= railBottomMax + eps && maxY >= railTopMin - eps
        return if (spansBothRails) null else CopycatFenceWallBlockEntity.Slot.PRIMARY
    }

    private fun pickSlot(quad: BakedQuad): CopycatFenceWallBlockEntity.Slot {
        val ys = floatArrayOf(quad.position0().y(), quad.position1().y(), quad.position2().y(), quad.position3().y())
        val minY = ys.minOrNull() ?: 0f
        val maxY = ys.maxOrNull() ?: 0f
        val ysMaxAbs = maxOf(kotlin.math.abs(minY), kotlin.math.abs(maxY))
        val scale = if (ysMaxAbs > 2.0f) 16.0f else 1.0f
        val eps = scale / 256.0f

        val railBottomMin = (7.0f / 16.0f) * scale
        val railBottomMax = (10.0f / 16.0f) * scale
        val railTopMin = (12.0f / 16.0f) * scale
        val railTopMax = (15.0f / 16.0f) * scale

        // Strict by current fence model rails: only y in [7..10] or [12..15] belongs to rails.
        if (minY >= railBottomMin - eps && maxY <= railBottomMax + eps) {
            return CopycatFenceWallBlockEntity.Slot.TERTIARY
        }
        if (minY >= railTopMin - eps && maxY <= railTopMax + eps) {
            return CopycatFenceWallBlockEntity.Slot.SECONDARY
        }
        return CopycatFenceWallBlockEntity.Slot.PRIMARY
    }
}
