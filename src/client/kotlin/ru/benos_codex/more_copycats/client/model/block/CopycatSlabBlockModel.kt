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
import net.minecraft.world.level.block.state.properties.SlabType
import ru.benos_codex.more_copycats.block.CopycatSlabBlock
import ru.benos_codex.more_copycats.block.entity.CopycatSlabBlockEntity

class CopycatSlabBlockModel(state: BlockState, unbaked: BlockStateModel.UnbakedRoot) : CopycatModel(state, unbaked) {
    override fun addPartsWithInfo(
        world: BlockAndTintGetter,
        pos: BlockPos,
        state: BlockState,
        block: CopycatBlock,
        material: BlockState,
        random: RandomSource,
        parts: MutableList<BlockModelPart>
    ) {
        val slabType = state.getValue(CopycatSlabBlock.TYPE)
        val axis = state.getValue(CopycatSlabBlock.AXIS)

        val slabEntity = world.getBlockEntity(pos) as? CopycatSlabBlockEntity
        val bottomMaterial = slabEntity?.getHalfMaterial(SlabType.BOTTOM) ?: material
        val topMaterial = slabEntity?.getHalfMaterial(SlabType.TOP) ?: material
        val bottomRefs = collectMaterialReferences(world, pos, bottomMaterial, random)
        val topRefs = collectMaterialReferences(world, pos, topMaterial, random)

        val templateParts = mutableListOf<BlockModelPart>()
        model.collectParts(random, templateParts)

        for (templatePart in templateParts) {
            val builder = QuadCollection.Builder()
            when (slabType) {
                SlabType.BOTTOM -> addPartRemapped(templatePart, builder, bottomRefs, block, state, bottomMaterial, world, pos)
                SlabType.TOP -> addPartRemapped(templatePart, builder, topRefs, block, state, topMaterial, world, pos)
                SlabType.DOUBLE -> addPartRemappedSplit(templatePart, builder, bottomRefs, topRefs, bottomMaterial, topMaterial, axis, block, state, world, pos)
            }
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
        refs: Map<Direction, BakedQuad>,
        block: CopycatBlock,
        state: BlockState,
        material: BlockState,
        world: BlockAndTintGetter,
        pos: BlockPos
    ) {
        for (quad in templatePart.getQuads(null)) {
            val ref = refs[quad.direction()]
            builder.addUnculledFace(CopycatConnectedTextureHelper.remapQuad(quad, ref?.sprite(), ref, world, pos, state, material))
        }

        for (direction in Direction.entries) {
            for (quad in templatePart.getQuads(direction)) {
                val ref = refs[direction]
                val remapped = CopycatConnectedTextureHelper.remapQuad(quad, ref?.sprite(), ref, world, pos, state, material)
                if (block.shouldFaceAlwaysRender(state, direction)) builder.addUnculledFace(remapped)
                else builder.addCulledFace(direction, remapped)
            }
        }
    }

    private fun addPartRemappedSplit(
        templatePart: BlockModelPart,
        builder: QuadCollection.Builder,
        bottomRefs: Map<Direction, BakedQuad>,
        topRefs: Map<Direction, BakedQuad>,
        bottomMaterial: BlockState,
        topMaterial: BlockState,
        axis: Direction.Axis,
        block: CopycatBlock,
        state: BlockState,
        world: BlockAndTintGetter,
        pos: BlockPos
    ) {
        fun pickRefs(quad: BakedQuad): Map<Direction, BakedQuad> {
            return if (pickHalf(quad, axis) == SlabType.TOP) topRefs else bottomRefs
        }
        fun pickMaterial(quad: BakedQuad): BlockState {
            return if (pickHalf(quad, axis) == SlabType.TOP) topMaterial else bottomMaterial
        }

        for (quad in templatePart.getQuads(null)) {
            val refs = pickRefs(quad)
            val ref = refs[quad.direction()]
            val material = pickMaterial(quad)
            builder.addUnculledFace(CopycatConnectedTextureHelper.remapQuad(quad, ref?.sprite(), ref, world, pos, state, material))
        }

        for (direction in Direction.entries) {
            for (quad in templatePart.getQuads(direction)) {
                val refs = pickRefs(quad)
                val ref = refs[direction]
                val material = pickMaterial(quad)
                val remapped = CopycatConnectedTextureHelper.remapQuad(quad, ref?.sprite(), ref, world, pos, state, material)
                if (block.shouldFaceAlwaysRender(state, direction)) builder.addUnculledFace(remapped)
                else builder.addCulledFace(direction, remapped)
            }
        }
    }

    private fun pickHalf(quad: BakedQuad, axis: Direction.Axis): SlabType {
        val c0 = axisCoord(quad.position0(), axis)
        val c1 = axisCoord(quad.position1(), axis)
        val c2 = axisCoord(quad.position2(), axis)
        val c3 = axisCoord(quad.position3(), axis)
        val center = (c0 + c1 + c2 + c3) * 0.25f
        val eps = 1e-4f

        if (center > 0.5f + eps) return SlabType.TOP
        if (center < 0.5f - eps) return SlabType.BOTTOM

        return when (axis) {
            Direction.Axis.Y -> if (quad.direction() == Direction.UP) SlabType.TOP else SlabType.BOTTOM
            Direction.Axis.X -> if (quad.direction() == Direction.EAST) SlabType.TOP else SlabType.BOTTOM
            Direction.Axis.Z -> if (quad.direction() == Direction.SOUTH) SlabType.TOP else SlabType.BOTTOM
        }
    }

    private fun axisCoord(v: org.joml.Vector3fc, axis: Direction.Axis): Float = when (axis) {
        Direction.Axis.X -> v.x()
        Direction.Axis.Y -> v.y()
        Direction.Axis.Z -> v.z()
    }

}
