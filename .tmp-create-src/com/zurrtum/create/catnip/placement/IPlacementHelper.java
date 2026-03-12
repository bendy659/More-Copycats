package com.zurrtum.create.catnip.placement;

import com.zurrtum.create.catnip.data.Iterate;
import com.zurrtum.create.catnip.data.Pair;
import com.zurrtum.create.catnip.math.VecHelper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.class_1657;
import net.minecraft.class_1747;
import net.minecraft.class_1799;
import net.minecraft.class_1937;
import net.minecraft.class_2246;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_2382;
import net.minecraft.class_243;
import net.minecraft.class_2680;
import net.minecraft.class_3965;

public interface IPlacementHelper {

    /**
     * used as an identifier in SuperGlueHandler to skip blocks placed by helpers
     */
    class_2680 ID = new class_2680(class_2246.field_10124, null, null);

    /**
     * @return a predicate that gets tested with the items held in the players hands<br>
     * should return true if this placement helper is active with the given item
     */
    Predicate<class_1799> getItemPredicate();

    /**
     * @return a predicate that gets tested with the blockstate the player is looking at<br>
     * should return true if this placement helper is active with the given blockstate
     */
    Predicate<class_2680> getStatePredicate();

    /**
     * @param player the player that activated the placement helper
     * @param world  the world that the placement helper got activated in
     * @param state  the Blockstate of the Block that the player is looking at or clicked on
     * @param pos    the position of the Block the player is looking at or clicked on
     * @param ray    the exact raytrace result
     * @return the PlacementOffset object describing where to place the new block.<br>
     * Use {@link PlacementOffset#fail} when no new position could be found.<br>
     * Use {@link PlacementOffset#success(class_2382)} with the new BlockPos to indicate a success
     * and call {@link PlacementOffset#withTransform(Function)} if the blocks default state has to be modified before it is placed
     */
    PlacementOffset getOffset(class_1657 player, class_1937 world, class_2680 state, class_2338 pos, class_3965 ray);

    //sets the offset's ghost state with the default state of the held block item, this is used in PlacementHelpers and can be ignored in most cases
    default PlacementOffset getOffset(class_1657 player, class_1937 world, class_2680 state, class_2338 pos, class_3965 ray, class_1799 heldItem) {
        PlacementOffset offset = getOffset(player, world, state, pos, ray);
        if (heldItem.method_7909() instanceof class_1747 blockItem) {
            offset = offset.withGhostState(blockItem.method_7711().method_9564());
        }
        return offset;
    }

    static List<class_2350> orderedByDistanceOnlyAxis(class_2338 pos, class_243 hit, class_2350.class_2351 axis) {
        return orderedByDistance(pos, hit, dir -> dir.method_10166() == axis);
    }

    static List<class_2350> orderedByDistanceOnlyAxis(class_2338 pos, class_243 hit, class_2350.class_2351 axis, Predicate<class_2350> includeDirection) {
        return orderedByDistance(pos, hit, ((Predicate<class_2350>) dir -> dir.method_10166() == axis).and(includeDirection));
    }

    static List<class_2350> orderedByDistanceExceptAxis(class_2338 pos, class_243 hit, class_2350.class_2351 axis) {
        return orderedByDistance(pos, hit, dir -> dir.method_10166() != axis);
    }

    static List<class_2350> orderedByDistanceExceptAxis(class_2338 pos, class_243 hit, class_2350.class_2351 axis, Predicate<class_2350> includeDirection) {
        return orderedByDistance(pos, hit, ((Predicate<class_2350>) dir -> dir.method_10166() != axis).and(includeDirection));
    }

    static List<class_2350> orderedByDistanceExceptAxis(class_2338 pos, class_243 hit, class_2350.class_2351 first, class_2350.class_2351 second) {
        return orderedByDistanceExceptAxis(pos, hit, first, d -> d.method_10166() != second);
    }

    static List<class_2350> orderedByDistanceExceptAxis(
        class_2338 pos,
        class_243 hit,
        class_2350.class_2351 first,
        class_2350.class_2351 second,
        Predicate<class_2350> includeDirection
    ) {
        return orderedByDistanceExceptAxis(pos, hit, first, ((Predicate<class_2350>) d -> d.method_10166() != second).and(includeDirection));
    }

    static List<class_2350> orderedByDistance(class_2338 pos, class_243 hit) {
        return orderedByDistance(pos, hit, _$ -> true);
    }

    static List<class_2350> orderedByDistance(class_2338 pos, class_243 hit, Predicate<class_2350> includeDirection) {
        List<class_2350> directions = new ArrayList<>();

        for (class_2350 dir : Iterate.directions) {
            if (includeDirection.test(dir)) {
                directions.add(dir);
            }
        }

        return orderedByDistance(pos, hit, directions);
    }

    static List<class_2350> orderedByDistance(class_2338 pos, class_243 hit, Collection<class_2350> directions) {
        class_243 centerToHit = hit.method_1020(VecHelper.getCenterOf(pos));

        List<Pair<class_2350, Double>> distances = new ArrayList<>();
        for (class_2350 dir : directions) {
            distances.add(Pair.of(dir, class_243.method_24954(dir.method_62675()).method_1022(centerToHit)));
        }

        distances.sort(Comparator.comparingDouble(Pair::getSecond));

        List<class_2350> sortedDirections = new ArrayList<>();
        for (Pair<class_2350, Double> p : distances) {
            sortedDirections.add(p.getFirst());
        }

        return sortedDirections;
    }

    default boolean matchesItem(class_1799 item) {
        return getItemPredicate().test(item);
    }

    default boolean matchesState(class_2680 state) {
        return getStatePredicate().test(state);
    }
}
