package com.zurrtum.create.catnip.placement;

import org.jetbrains.annotations.Nullable;

import java.util.function.Function;
import net.minecraft.class_1268;
import net.minecraft.class_1269;
import net.minecraft.class_1657;
import net.minecraft.class_174;
import net.minecraft.class_1747;
import net.minecraft.class_1799;
import net.minecraft.class_1937;
import net.minecraft.class_2338;
import net.minecraft.class_2382;
import net.minecraft.class_2498;
import net.minecraft.class_2680;
import net.minecraft.class_2741;
import net.minecraft.class_3222;
import net.minecraft.class_3419;
import net.minecraft.class_3468;
import net.minecraft.class_3610;
import net.minecraft.class_3612;
import net.minecraft.class_5712;

public class PlacementOffset {

    private final boolean success;
    private class_2382 pos;
    private Function<class_2680, class_2680> stateTransform;
    @Nullable
    private class_2680 ghostState;

    private PlacementOffset(boolean success) {
        this.success = success;
        this.pos = class_2338.field_10980;
        this.stateTransform = Function.identity();
        this.ghostState = null;
    }

    public static PlacementOffset fail() {
        return new PlacementOffset(false);
    }

    public static PlacementOffset success() {
        return new PlacementOffset(true);
    }

    public static PlacementOffset success(class_2382 pos) {
        return success().at(pos);
    }

    public static PlacementOffset success(class_2382 pos, Function<class_2680, class_2680> transform) {
        return success().at(pos).withTransform(transform);
    }

    public PlacementOffset at(class_2382 pos) {
        this.pos = pos;
        return this;
    }

    public PlacementOffset withTransform(Function<class_2680, class_2680> stateTransform) {
        this.stateTransform = stateTransform;
        return this;
    }

    public PlacementOffset withGhostState(class_2680 ghostState) {
        this.ghostState = ghostState;
        return this;
    }

    public boolean isSuccessful() {
        return success;
    }

    public class_2382 getPos() {
        return pos;
    }

    public class_2338 getBlockPos() {
        if (pos instanceof class_2338)
            return (class_2338) pos;

        return new class_2338(pos);
    }

    public Function<class_2680, class_2680> getTransform() {
        return stateTransform;
    }

    public boolean hasGhostState() {
        return ghostState != null;
    }

    @Nullable
    public class_2680 getGhostState() {
        return ghostState;
    }

    public boolean isReplaceable(class_1937 world) {
        if (!success)
            return false;

        return world.method_8320(new class_2338(pos)).method_45474();
    }

    public class_1269 placeInWorld(class_1937 world, class_1747 blockItem, class_1657 player, class_1268 hand) {

        if (!isReplaceable(world))
            return class_1269.field_52423;

        if (world.method_8608())
            return class_1269.field_5812;

        class_2338 newPos = new class_2338(pos);
        class_1799 stackBefore = player.method_5998(hand).method_7972();

        if (!world.method_8505(player, newPos))
            return class_1269.field_52423;

        class_2680 newState = stateTransform.apply(blockItem.method_7711().method_9564());
        if (newState.method_28498(class_2741.field_12508)) {
            class_3610 fluidState = world.method_8316(newPos);
            newState = newState.method_11657(class_2741.field_12508, fluidState.method_15772() == class_3612.field_15910);
        }

        world.method_8501(newPos, newState);
        class_2498 soundtype = newState.method_26231();
        world.method_8396(
            null,
            newPos,
            soundtype.method_10598(),
            class_3419.field_15245,
            (soundtype.method_10597() + 1.0F) / 2.0F,
            soundtype.method_10599() * 0.8F
        );
        world.method_43276(class_5712.field_28164, newPos, class_5712.class_7397.method_43286(player, newState));

        player.method_7259(class_3468.field_15372.method_14956(blockItem));
        newState.method_26204().method_9567(world, newPos, newState, player, stackBefore);

        if (player instanceof class_3222 serverPlayer)
            class_174.field_1191.method_23889(serverPlayer, newPos, player.method_5998(hand));

        if (!player.method_68878())
            player.method_5998(hand).method_7934(1);

        return class_1269.field_5812;
    }
}
