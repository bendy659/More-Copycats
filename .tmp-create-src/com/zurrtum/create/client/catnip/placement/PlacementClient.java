package com.zurrtum.create.client.catnip.placement;

import com.zurrtum.create.catnip.animation.LerpedFloat;
import com.zurrtum.create.catnip.math.AngleHelper;
import com.zurrtum.create.catnip.placement.IPlacementHelper;
import com.zurrtum.create.catnip.placement.PlacementHelpers;
import com.zurrtum.create.catnip.placement.PlacementOffset;
import com.zurrtum.create.client.catnip.ghostblock.GhostBlocks;
import com.zurrtum.create.client.catnip.gui.render.ArrowRenderState;
import com.zurrtum.create.client.catnip.gui.render.TextureArrowRenderState;
import com.zurrtum.create.client.catnip.math.VecHelper;
import com.zurrtum.create.client.catnip.outliner.Outliner;
import com.zurrtum.create.client.ponder.config.CClient;
import com.zurrtum.create.client.ponder.enums.PonderConfig;
import com.zurrtum.create.client.ponder.enums.PonderGuiTextures;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.class_11231;
import net.minecraft.class_1268;
import net.minecraft.class_1657;
import net.minecraft.class_1799;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_243;
import net.minecraft.class_2680;
import net.minecraft.class_310;
import net.minecraft.class_332;
import net.minecraft.class_3965;
import net.minecraft.class_638;

import static com.zurrtum.create.catnip.math.VecHelper.getCenterOf;

public class PlacementClient {

    static final LerpedFloat angle = LerpedFloat.angular().chase(0, 0.25f, LerpedFloat.Chaser.EXP);
    @Nullable
    static class_2338 target = null;
    @Nullable
    static class_2338 lastTarget = null;
    static int animationTick = 0;

    public static void tick(class_310 mc) {
        setTarget(null);
        checkHelpers(mc);

        if (target == null) {
            if (animationTick > 0)
                animationTick = Math.max(animationTick - 2, 0);

            return;
        }

        if (animationTick < 10)
            animationTick++;

    }

    private static void checkHelpers(class_310 mc) {
        class_638 world = mc.field_1687;

        if (world == null)
            return;

        if (!(mc.field_1765 instanceof class_3965 ray))
            return;

        if (mc.field_1724 == null)
            return;

        if (mc.field_1724.method_5715())// for now, disable all helpers when sneaking TODO add helpers that respect
            // sneaking but still show position
            return;

        for (class_1268 hand : class_1268.values()) {

            class_1799 heldItem = mc.field_1724.method_5998(hand);

            List<IPlacementHelper> filteredForHeldItem = new ArrayList<>();
            for (IPlacementHelper helper : PlacementHelpers.getHelpersView()) {
                if (helper.matchesItem(heldItem))
                    filteredForHeldItem.add(helper);
            }

            if (filteredForHeldItem.isEmpty())
                continue;

            class_2338 pos = ray.method_17777();
            class_2680 state = world.method_8320(pos);

            List<IPlacementHelper> filteredForState = new ArrayList<>();
            for (IPlacementHelper helper : filteredForHeldItem) {
                if (helper.matchesState(state))
                    filteredForState.add(helper);
            }

            if (filteredForState.isEmpty())
                continue;

            boolean atLeastOneMatch = false;
            for (IPlacementHelper h : filteredForState) {
                PlacementOffset offset = h.getOffset(mc.field_1724, world, state, pos, ray, heldItem);

                if (offset.isSuccessful()) {
                    renderAt(h, offset);
                    setTarget(offset.getBlockPos());
                    atLeastOneMatch = true;
                    break;
                }

            }

            // at least one helper activated, no need to check the offhand if we are still
            // in the mainhand
            if (atLeastOneMatch)
                return;

        }
    }

    static void setTarget(@Nullable class_2338 target) {
        PlacementClient.target = target;

        if (target == null)
            return;

        if (lastTarget == null) {
            lastTarget = target;
            return;
        }

        if (!lastTarget.equals(target))
            lastTarget = target;
    }

    public static void onRenderCrosshairOverlay(class_310 mc, class_332 graphics, float partialTicks) {
        class_1657 player = mc.field_1724;

        if (player != null && animationTick > 0) {
            float screenY = graphics.method_51443() / 2f;
            float screenX = graphics.method_51421() / 2f;
            float progress = getCurrentAlpha();

            drawDirectionIndicator(graphics, partialTicks, screenX, screenY, progress);
        }
    }

    public static float getCurrentAlpha() {
        return Math.min(animationTick / 10f/* + event.getPartialTicks() */, 1f);
    }

    private static void drawDirectionIndicator(class_332 graphics, float partialTicks, float centerX, float centerY, float progress) {
        float r = .8f;
        float g = .8f;
        float b = .8f;
        float a = progress * progress;

        class_243 projTarget = VecHelper.projectToPlayerView(getCenterOf(lastTarget), partialTicks);

        class_243 target = new class_243(projTarget.field_1352, projTarget.field_1351, 0);
        if (projTarget.field_1350 > 0)
            target = target.method_22882();

        class_243 norm = target.method_1029();
        class_243 ref = new class_243(0, 1, 0);
        float targetAngle = AngleHelper.deg(-Math.acos(norm.method_1026(ref)));

        if (norm.field_1352 < 0)
            targetAngle = 360 - targetAngle;

        if (animationTick < 10)
            angle.setValue(targetAngle);

        angle.chase(targetAngle, .25f, LerpedFloat.Chaser.EXP);
        angle.tickChaser();

        float snapSize = 22.5f;
        float snappedAngle = (snapSize * Math.round(angle.getValue(0f) / snapSize)) % 360f;

        float length = 10;

        CClient.PlacementIndicatorSetting mode = PonderConfig.client().placementIndicator.get();
        if (mode == CClient.PlacementIndicatorSetting.TRIANGLE)
            fadedArrow(graphics, centerX, centerY, r, g, b, a, length, snappedAngle);
        else if (mode == CClient.PlacementIndicatorSetting.TEXTURE)
            textured(graphics, centerX, centerY, a, snappedAngle);
    }

    private static void fadedArrow(
        class_332 graphics,
        float centerX,
        float centerY,
        float r,
        float g,
        float b,
        float a,
        float length,
        float snappedAngle
    ) {
        Matrix3x2fStack ms = graphics.method_51448();
        ms.pushMatrix();
        ms.translate(centerX, centerY);
        ms.rotate(angle.getValue(0) * (float) (Math.PI / 180.0));
        double scale = PonderConfig.client().indicatorScale.get();
        ms.scale((float) scale, (float) scale);
        int size = (int) ((10 + length) * scale);
        graphics.field_59826.method_70919(new ArrowRenderState(new Matrix3x2f(ms), size, r, g, b, a, length));
        ms.popMatrix();
    }

    public static void textured(class_332 graphics, float centerX, float centerY, float alpha, float snappedAngle) {
        Matrix3x2fStack ms = graphics.method_51448();
        ms.pushMatrix();
        ms.translate(centerX, centerY);
        float scale = PonderConfig.client().indicatorScale.get() * .75f;
        ms.scale(scale, scale);
        ms.scale(12, 12);

        float index = snappedAngle / 22.5f;
        float tex_size = 16f / 256f;

        float tx = 0;
        float ty = index * tex_size;
        float tw = 1f;
        float th = tex_size;
        int size = (int) (36 * scale);
        class_11231 texture = PonderGuiTextures.PLACEMENT_INDICATOR_SHEET.bind();
        graphics.field_59826.method_70919(new TextureArrowRenderState(new Matrix3x2f(ms), size, alpha, texture, tx, ty, tw, th));
        ms.popMatrix();
    }


    public static void renderAt(Object slot, PlacementOffset offset) {
        displayGhost(slot, offset);
    }

    //RIP
    public static void renderArrow(class_243 center, class_243 target, class_2350 arrowPlane) {
        renderArrow(center, target, arrowPlane, 1D);
    }

    public static void renderArrow(class_243 center, class_243 target, class_2350 arrowPlane, double distanceFromCenter) {
        class_243 direction = target.method_1020(center).method_1029();
        class_243 facing = class_243.method_24954(arrowPlane.method_62675());
        class_243 start = center.method_1019(direction);
        class_243 offset = direction.method_1021(distanceFromCenter - 1);
        class_243 offsetA = direction.method_1036(facing).method_1029().method_1021(.25);
        class_243 offsetB = facing.method_1036(direction).method_1029().method_1021(.25);
        class_243 endA = center.method_1019(direction.method_1021(.75)).method_1019(offsetA);
        class_243 endB = center.method_1019(direction.method_1021(.75)).method_1019(offsetB);
        Outliner.getInstance().showLine("placementArrowA" + center + target, start.method_1019(offset), endA.method_1019(offset)).lineWidth(1 / 16f);
        Outliner.getInstance().showLine("placementArrowB" + center + target, start.method_1019(offset), endB.method_1019(offset)).lineWidth(1 / 16f);
    }

    public static void displayGhost(Object slot, PlacementOffset offset) {
        if (!offset.hasGhostState())
            return;

        GhostBlocks.getInstance().showGhostState(slot, offset.getTransform().apply(offset.getGhostState())).at(offset.getBlockPos()).breathingAlpha();
    }
}
