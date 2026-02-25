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

class CopycatWallBlockModel(state: BlockState, unbaked: BlockStateModel.UnbakedRoot) : CopycatModel(state, unbaked) {
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
        val sideMaterial = blockEntity?.getSlotMaterial(CopycatFenceWallBlockEntity.Slot.SECONDARY) ?: centerMaterial

        val refsBySlot = mapOf(
            CopycatFenceWallBlockEntity.Slot.PRIMARY to collectMaterialReferences(world, pos, centerMaterial, random),
            CopycatFenceWallBlockEntity.Slot.SECONDARY to collectMaterialReferences(world, pos, sideMaterial, random)
        )
        val materialsBySlot = mapOf(
            CopycatFenceWallBlockEntity.Slot.PRIMARY to centerMaterial,
            CopycatFenceWallBlockEntity.Slot.SECONDARY to sideMaterial
        )

        val templateParts = mutableListOf<BlockModelPart>()
        model.collectParts(random, templateParts)

        for (templatePart in templateParts) {
            val builder = QuadCollection.Builder()
            addPartRemapped(templatePart, builder, refsBySlot, materialsBySlot, block, state, world, pos)
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
        builder: QuadCollection.Builder,
        refsBySlot: Map<CopycatFenceWallBlockEntity.Slot, Map<Direction, BakedQuad>>,
        materialsBySlot: Map<CopycatFenceWallBlockEntity.Slot, BlockState>,
        block: CopycatBlock,
        state: BlockState,
        world: BlockAndTintGetter,
        pos: BlockPos
    ) {
        for (quad in templatePart.getQuads(null)) {
            val slot = pickSlot(quad)
            val refs = refsBySlot[slot].orEmpty()
            val material = materialsBySlot[slot] ?: materialsBySlot.getValue(CopycatFenceWallBlockEntity.Slot.PRIMARY)
            val ref = refs[quad.direction()]
            val useConnected = slot == CopycatFenceWallBlockEntity.Slot.SECONDARY
            builder.addUnculledFace(CopycatConnectedTextureHelper.remapQuad(quad, ref?.sprite(), ref, world, pos, state, material, useConnected))
        }

        for (direction in Direction.entries) {
            for (quad in templatePart.getQuads(direction)) {
                val slot = pickSlot(quad)
                val refs = refsBySlot[slot].orEmpty()
                val material = materialsBySlot[slot] ?: materialsBySlot.getValue(CopycatFenceWallBlockEntity.Slot.PRIMARY)
                val ref = refs[direction]
                val useConnected = slot == CopycatFenceWallBlockEntity.Slot.SECONDARY
                val remapped = CopycatConnectedTextureHelper.remapQuad(quad, ref?.sprite(), ref, world, pos, state, material, useConnected)
                if (block.shouldFaceAlwaysRender(state, direction)) builder.addUnculledFace(remapped)
                else builder.addCulledFace(direction, remapped)
            }
        }
    }

    private fun pickSlot(quad: BakedQuad): CopycatFenceWallBlockEntity.Slot {
        val xs = floatArrayOf(quad.position0().x(), quad.position1().x(), quad.position2().x(), quad.position3().x())
        val zs = floatArrayOf(quad.position0().z(), quad.position1().z(), quad.position2().z(), quad.position3().z())
        val minX = xs.minOrNull() ?: 0f
        val maxX = xs.maxOrNull() ?: 0f
        val minZ = zs.minOrNull() ?: 0f
        val maxZ = zs.maxOrNull() ?: 0f
        val edge = minX < 0.12f || maxX > 0.88f || minZ < 0.12f || maxZ > 0.88f
        return if (edge) CopycatFenceWallBlockEntity.Slot.SECONDARY else CopycatFenceWallBlockEntity.Slot.PRIMARY
    }
}
