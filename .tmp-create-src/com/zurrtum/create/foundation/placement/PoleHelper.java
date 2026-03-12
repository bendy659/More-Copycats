package com.zurrtum.create.foundation.placement;

import com.zurrtum.create.catnip.placement.IPlacementHelper;
import com.zurrtum.create.catnip.placement.PlacementOffset;
import com.zurrtum.create.content.equipment.extendoGrip.ExtendoGripItem;
import com.zurrtum.create.infrastructure.config.AllConfigs;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.class_1324;
import net.minecraft.class_1657;
import net.minecraft.class_1937;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_2680;
import net.minecraft.class_2769;
import net.minecraft.class_3965;
import net.minecraft.class_5134;

public abstract class PoleHelper<T extends Comparable<T>> implements IPlacementHelper {

    protected final Predicate<class_2680> statePredicate;
    protected final class_2769<T> property;
    protected final Function<class_2680, class_2350.class_2351> axisFunction;

    public PoleHelper(Predicate<class_2680> statePredicate, Function<class_2680, class_2350.class_2351> axisFunction, class_2769<T> property) {
        this.statePredicate = statePredicate;
        this.axisFunction = axisFunction;
        this.property = property;
    }

    public boolean matchesAxis(class_2680 state, class_2350.class_2351 axis) {
        if (!statePredicate.test(state))
            return false;

        return axisFunction.apply(state) == axis;
    }

    public int attachedPoles(class_1937 world, class_2338 pos, class_2350 direction) {
        class_2338 checkPos = pos.method_10093(direction);
        class_2680 state = world.method_8320(checkPos);
        int count = 0;
        while (matchesAxis(state, direction.method_10166())) {
            count++;
            checkPos = checkPos.method_10093(direction);
            state = world.method_8320(checkPos);
        }
        return count;
    }

    @Override
    public Predicate<class_2680> getStatePredicate() {
        return this.statePredicate;
    }

    @Override
    public PlacementOffset getOffset(class_1657 player, class_1937 world, class_2680 state, class_2338 pos, class_3965 ray) {
        List<class_2350> directions = IPlacementHelper.orderedByDistance(pos, ray.method_17784(), dir -> dir.method_10166() == axisFunction.apply(state));
        for (class_2350 dir : directions) {
            int range = AllConfigs.server().equipment.placementAssistRange.get();
            if (player != null) {
                class_1324 reach = player.method_5996(class_5134.field_47758);
                if (reach != null && reach.method_6196(ExtendoGripItem.singleRangeAttributeModifier.comp_2447()))
                    range += 4;
            }
            int poles = attachedPoles(world, pos, dir);
            if (poles >= range)
                continue;

            class_2338 newPos = pos.method_10079(dir, poles + 1);
            class_2680 newState = world.method_8320(newPos);

            if (newState.method_45474())
                return PlacementOffset.success(newPos, bState -> bState.method_11657(property, state.method_11654(property)));

        }

        return PlacementOffset.fail();
    }
}
