package ru.benos_codex.more_copycats.client.mixin.create.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zurrtum.create.client.catnip.render.CachedBuffers;
import com.zurrtum.create.client.catnip.render.SuperBufferFactory;
import com.zurrtum.create.client.catnip.render.SuperByteBuffer;
import com.zurrtum.create.client.content.decoration.slidingDoor.SlidingDoorRenderer;
import com.zurrtum.create.client.infrastructure.model.WrapperBlockStateModel;
import com.zurrtum.create.content.decoration.slidingDoor.SlidingDoorBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import ru.benos_codex.more_copycats.block.entity.CopycatSlidingDoorBlockEntity;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = SlidingDoorRenderer.class, remap = false)
public class SlidingDoorRendererMixin {

    @Redirect(
            method = "extractRenderState(Lcom/zurrtum/create/content/decoration/slidingDoor/SlidingDoorBlockEntity;Lcom/zurrtum/create/client/content/decoration/slidingDoor/SlidingDoorRenderer$DoorRenderState;FLnet/minecraft/world/phys/Vec3;Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/zurrtum/create/client/catnip/render/CachedBuffers;block(Lnet/minecraft/world/level/block/state/BlockState;)Lcom/zurrtum/create/client/catnip/render/SuperByteBuffer;"
            ),
            remap = false
    )
    @SuppressWarnings("unused")
    private SuperByteBuffer more_copycats$renderCopycatSlidingDoorMaterial(
            BlockState toRender,
            SlidingDoorBlockEntity be,
            SlidingDoorRenderer.DoorRenderState state,
            float tickProgress,
            Vec3 cameraPos,
            @Nullable ModelFeatureRenderer.CrumblingOverlay crumblingOverlay
    ) {
        if (!(be instanceof CopycatSlidingDoorBlockEntity) || be.getLevel() == null) {
            return CachedBuffers.block(toRender);
        }

        BlockStateModel baked = Minecraft.getInstance().getBlockRenderer().getBlockModel(toRender);
        BlockStateModel unwrapped = WrapperBlockStateModel.unwrapCompat(baked);
        if (!(unwrapped instanceof WrapperBlockStateModel wrapper)) {
            return CachedBuffers.block(toRender)
                    .useLevelLight(be.getLevel());
        }

        BlockPos renderPos = be.getBlockPos();
        if (toRender.hasProperty(DoorBlock.HALF) && toRender.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) {
            renderPos = renderPos.above();
        }

        List<BlockModelPart> parts = new ArrayList<>();
        wrapper.addPartsWithInfo(be.getLevel(), renderPos, toRender, RandomSource.create(), parts);
        SuperByteBuffer buffer = SuperBufferFactory.getInstance().createForBlock(parts, toRender, new PoseStack());
        buffer.useLevelLight(be.getLevel());
        return buffer;
    }
}
