package com.zurrtum.create.client.infrastructure.model;

import com.zurrtum.create.catnip.data.Iterate;
import com.zurrtum.create.client.foundation.model.BakedModelHelper;
import com.zurrtum.create.content.decoration.copycat.CopycatBlock;
import com.zurrtum.create.content.decoration.copycat.CopycatStepBlock;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.class_10801;
import net.minecraft.class_10817;
import net.minecraft.class_1087;
import net.minecraft.class_10889;
import net.minecraft.class_1920;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_2680;
import net.minecraft.class_2760;
import net.minecraft.class_5819;
import net.minecraft.class_777;

public class CopycatStepModel extends CopycatModel {
    protected static final class_243 VEC_Y_3 = new class_243(0, .75, 0);
    protected static final class_243 VEC_Y_2 = new class_243(0, .5, 0);
    protected static final class_243 VEC_Y_N2 = new class_243(0, -.5, 0);
    protected static final class_238 CUBE_AABB = new class_238(class_2338.field_10980);

    public CopycatStepModel(class_2680 state, class_9979 unbaked) {
        super(state, unbaked);
    }

    @Override
    protected void addPartsWithInfo(
        class_1920 world,
        class_2338 pos,
        class_2680 state,
        CopycatBlock block,
        class_2680 material,
        class_5819 random,
        List<class_10889> parts
    ) {
        class_2350 facing = state.method_61767(CopycatStepBlock.FACING, class_2350.field_11035);
        boolean upperHalf = state.method_61767(CopycatStepBlock.HALF, class_2760.field_12617) == class_2760.field_12619;
        class_243 normal = class_243.method_24954(facing.method_62675());
        class_243 normalScaled2 = normal.method_1021(.5);
        class_243 normalScaledN3 = normal.method_1021(-.75);
        class_238 bb = CUBE_AABB.method_1002(-normal.field_1352 * .75, .75, -normal.field_1350 * .75);

        OcclusionData occlusionData = gatherOcclusionData(world, pos, state, material, block);
        class_1087 model = getModelOf(material);
        for (class_10889 part : getMaterialParts(world, pos, material, random, model)) {
            class_10817.class_10818 builder = new class_10817.class_10818();
            addCroppedQuads(facing, upperHalf, normalScaled2, normalScaledN3, bb, part.method_68509(null), builder::method_68051);
            for (class_2350 direction : Iterate.directions) {
                if (occlusionData.isOccluded(direction))
                    continue;
                addCroppedQuads(
                    facing,
                    upperHalf,
                    normalScaled2,
                    normalScaledN3,
                    bb,
                    part.method_68509(direction),
                    block.shouldFaceAlwaysRender(state, direction) ? builder::method_68051 : (class_777 quad) -> builder.method_68053(
                        direction,
                        quad
                    )
                );
            }
            parts.add(new class_10801(builder.method_68050(), part.comp_3751(), part.comp_3752()));
        }
    }

    protected void addCroppedQuads(
        class_2350 facing,
        boolean upperHalf,
        class_243 normalScaled2,
        class_243 normalScaledN3,
        class_238 bb,
        List<class_777> quads,
        Consumer<class_777> consumer
    ) {
        int size = quads.size();
        if (size == 0) {
            return;
        }
        for (boolean top : Iterate.trueAndFalse) {
            for (boolean front : Iterate.trueAndFalse) {
                class_238 bb1 = bb;
                if (front)
                    bb1 = bb1.method_997(normalScaledN3);
                if (top)
                    bb1 = bb1.method_997(VEC_Y_3);

                class_243 offset = class_243.field_1353;
                if (front)
                    offset = offset.method_1019(normalScaled2);
                if (top != upperHalf)
                    offset = offset.method_1019(upperHalf ? VEC_Y_2 : VEC_Y_N2);

                for (int i = 0; i < size; i++) {
                    class_777 quad = quads.get(i);
                    class_2350 direction = quad.comp_3723();

                    if (front && direction == facing)
                        continue;
                    if (!front && direction == facing.method_10153())
                        continue;
                    if (!top && direction == class_2350.field_11036)
                        continue;
                    if (top && direction == class_2350.field_11033)
                        continue;

                    consumer.accept(BakedModelHelper.cropAndMove(quad, bb1, offset));
                }
            }
        }
    }
}
