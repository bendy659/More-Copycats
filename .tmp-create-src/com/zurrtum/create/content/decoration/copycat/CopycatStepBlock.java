package com.zurrtum.create.content.decoration.copycat;

import com.zurrtum.create.AllBlocks;
import com.zurrtum.create.AllItems;
import com.zurrtum.create.AllShapes;
import com.zurrtum.create.catnip.math.VoxelShaper;
import com.zurrtum.create.catnip.placement.IPlacementHelper;
import com.zurrtum.create.catnip.placement.PlacementHelpers;
import com.zurrtum.create.catnip.placement.PlacementOffset;
import com.zurrtum.create.foundation.placement.PoleHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;
import net.minecraft.class_10;
import net.minecraft.class_1268;
import net.minecraft.class_1269;
import net.minecraft.class_1657;
import net.minecraft.class_1747;
import net.minecraft.class_1750;
import net.minecraft.class_1799;
import net.minecraft.class_1920;
import net.minecraft.class_1922;
import net.minecraft.class_1937;
import net.minecraft.class_2248;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_2350.class_2351;
import net.minecraft.class_2415;
import net.minecraft.class_2470;
import net.minecraft.class_265;
import net.minecraft.class_2680;
import net.minecraft.class_2689;
import net.minecraft.class_2741;
import net.minecraft.class_2754;
import net.minecraft.class_2760;
import net.minecraft.class_3726;
import net.minecraft.class_3965;

public class CopycatStepBlock extends WaterloggedCopycatBlock {

    public static final class_2754<class_2760> HALF = class_2741.field_12518;
    public static final class_2754<class_2350> FACING = class_2741.field_12481;

    private static final int placementHelperId = PlacementHelpers.register(new PlacementHelper());

    public CopycatStepBlock(class_2251 pProperties) {
        super(pProperties);
        method_9590(method_9564().method_11657(HALF, class_2760.field_12617).method_11657(FACING, class_2350.field_11035));
    }

    @Override
    protected class_1269 method_55765(
        class_1799 stack,
        class_2680 state,
        class_1937 level,
        class_2338 pos,
        class_1657 player,
        class_1268 hand,
        class_3965 hitResult
    ) {
        if (!player.method_5715() && player.method_7294()) {
            IPlacementHelper helper = PlacementHelpers.get(placementHelperId);
            if (helper.matchesItem(stack))
                return helper.getOffset(player, level, state, pos, hitResult).placeInWorld(level, (class_1747) stack.method_7909(), player, hand);
        }

        return super.method_55765(stack, state, level, pos, player, hand, hitResult);
    }

    @Override
    public boolean isIgnoredConnectivitySide(
        class_1920 reader,
        class_2680 state,
        class_2350 face,
        @Nullable class_2338 fromPos,
        @Nullable class_2338 toPos
    ) {
        if (fromPos == null || toPos == null)
            return true;

        class_2680 toState = reader.method_8320(toPos);

        if (!toState.method_27852(this))
            return true;

        class_2350 facing = state.method_11654(FACING);
        class_2338 diff = fromPos.method_10059(toPos);
        int coord = facing.method_10166().method_10173(diff.method_10263(), diff.method_10264(), diff.method_10260());

        class_2760 half = state.method_11654(HALF);
        if (half != toState.method_11654(HALF))
            return diff.method_10264() == 0;

        return facing == toState.method_11654(FACING).method_10153() && !(coord != 0 && coord != facing.method_10171().method_10181());
    }

    @Override
    public boolean canConnectTexturesToward(class_1920 reader, class_2338 fromPos, class_2338 toPos, class_2680 state) {
        class_2350 facing = state.method_11654(FACING);
        class_2680 toState = reader.method_8320(toPos);
        class_2338 diff = fromPos.method_10059(toPos);

        if (fromPos.equals(toPos.method_10093(facing)))
            return false;
        if (!toState.method_27852(this))
            return false;

        if (diff.method_10264() != 0) {
            return isOccluded(toState, state, diff.method_10264() > 0 ? class_2350.field_11036 : class_2350.field_11033);
        }

        if (isOccluded(state, toState, facing))
            return true;

        int coord = facing.method_10166().method_10173(diff.method_10263(), diff.method_10264(), diff.method_10260());
        return state.method_11657(WATERLOGGED, false) == toState.method_11657(WATERLOGGED, false) && coord == 0;
    }

    @Override
    public boolean canFaceBeOccluded(class_2680 state, class_2350 face) {
        if (face.method_10166() == class_2351.field_11052)
            return (state.method_11654(HALF) == class_2760.field_12619) == (face == class_2350.field_11036);
        return state.method_11654(FACING) == face;
    }

    @Override
    public boolean shouldFaceAlwaysRender(class_2680 state, class_2350 face) {
        return canFaceBeOccluded(state, face.method_10153());
    }

    @Override
    protected boolean method_9516(class_2680 state, class_10 pathComputationType) {
        return false;
    }

    @Override
    public class_2680 method_9605(class_1750 pContext) {
        class_2680 stateForPlacement = super.method_9605(pContext).method_11657(FACING, pContext.method_8042());
        class_2350 direction = pContext.method_8038();
        if (direction == class_2350.field_11036)
            return stateForPlacement;
        if (direction == class_2350.field_11033 || (pContext.method_17698().field_1351 - pContext.method_8037().method_10264() > 0.5D))
            return stateForPlacement.method_11657(HALF, class_2760.field_12619);
        return stateForPlacement;
    }

    @Override
    protected void method_9515(class_2689.class_2690<class_2248, class_2680> pBuilder) {
        super.method_9515(pBuilder.method_11667(HALF, FACING));
    }

    @Override
    public class_265 method_9530(class_2680 pState, class_1922 pLevel, class_2338 pPos, class_3726 pContext) {
        VoxelShaper voxelShaper = pState.method_11654(HALF) == class_2760.field_12617 ? AllShapes.STEP_BOTTOM : AllShapes.STEP_TOP;
        return voxelShaper.get(pState.method_11654(FACING));
    }

    //TODO
    //    @Override
    //    public boolean supportsExternalFaceHiding(BlockState state) {
    //        return true;
    //    }

    //TODO
    //    @Override
    //    public boolean hidesNeighborFace(BlockView level, BlockPos pos, BlockState state, BlockState neighborState, Direction dir) {
    //        if (state.isOf(this) == neighborState.isOf(this) && getMaterial(level, pos).skipRendering(
    //            getMaterial(level, pos.offset(dir)),
    //            dir.getOpposite()
    //        ))
    //            return isOccluded(state, neighborState, dir);
    //        return false;
    //    }

    public static boolean isOccluded(class_2680 state, class_2680 other, class_2350 pDirection) {
        state = state.method_11657(WATERLOGGED, false);
        other = other.method_11657(WATERLOGGED, false);

        class_2760 half = state.method_11654(HALF);
        boolean vertical = pDirection.method_10166() == class_2351.field_11052;
        if (half != other.method_11654(HALF))
            return vertical && (pDirection == class_2350.field_11036) == (half == class_2760.field_12619);
        if (vertical)
            return false;

        class_2350 facing = state.method_11654(FACING);
        if (facing.method_10153() == other.method_11654(FACING) && pDirection == facing)
            return true;
        if (other.method_11654(FACING) != facing)
            return false;
        return pDirection.method_10166() != facing.method_10166();
    }

    @Override
    public class_2680 method_9598(class_2680 pState, class_2470 pRot) {
        return pState.method_11657(FACING, pRot.method_10503(pState.method_11654(FACING)));
    }

    @Override
    public class_2680 method_9569(class_2680 pState, class_2415 pMirror) {
        return pState.method_26186(pMirror.method_10345(pState.method_11654(FACING)));
    }

    private static class PlacementHelper extends PoleHelper<class_2350> {

        public PlacementHelper() {
            super(state -> state.method_27852(AllBlocks.COPYCAT_STEP), state -> state.method_11654(FACING).method_10170().method_10166(), FACING);
        }

        @Override
        public @NotNull Predicate<class_1799> getItemPredicate() {
            return stack -> stack.method_31574(AllItems.COPYCAT_STEP);
        }

        @Override
        public @NotNull PlacementOffset getOffset(class_1657 player, class_1937 world, class_2680 state, class_2338 pos, class_3965 ray) {
            PlacementOffset offset = super.getOffset(player, world, state, pos, ray);

            if (offset.isSuccessful())
                offset.withTransform(offset.getTransform().andThen(s -> s.method_11657(HALF, state.method_11654(HALF))));

            return offset;
        }
    }

}