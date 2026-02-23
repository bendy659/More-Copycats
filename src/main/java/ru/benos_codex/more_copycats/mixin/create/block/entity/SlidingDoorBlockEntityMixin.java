package ru.benos_codex.more_copycats.mixin.create.block.entity;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.zurrtum.create.content.decoration.slidingDoor.SlidingDoorBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import ru.benos_codex.more_copycats.SlidingDoorBlockEntityTypeHelper;

@Mixin(value = SlidingDoorBlockEntity.class, remap = false)
public class SlidingDoorBlockEntityMixin {

    @ModifyExpressionValue(
            method = "<init>",
            at = @At(
                    value = "FIELD",
                    target = "Lcom/zurrtum/create/AllBlockEntityTypes;SLIDING_DOOR:Lnet/minecraft/world/level/block/entity/BlockEntityType;"
            ),
            remap = false
    )
    private static BlockEntityType<?> more_copycats$redirectBlockEntityType(BlockEntityType<?> original) {
        BlockEntityType<?> override = SlidingDoorBlockEntityTypeHelper.OVERRIDE.get();
        return override != null ? override : original;
    }
}
