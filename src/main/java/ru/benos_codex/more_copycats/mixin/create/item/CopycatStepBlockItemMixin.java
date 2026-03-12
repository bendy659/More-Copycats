package ru.benos_codex.more_copycats.mixin.create.item;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.benos_codex.more_copycats.MoreCopycatsRegister;

@Mixin(BlockItem.class)
public abstract class CopycatStepBlockItemMixin {

    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void more_copycats$redirectCreateStepPlacement(
        BlockPlaceContext context,
        CallbackInfoReturnable<InteractionResult> cir
    ) {
        BlockItem self = (BlockItem) (Object) this;
        if (!more_copycats$isCreateCopycatStepItem(self)) return;

        InteractionResult result = MoreCopycatsRegister.INSTANCE.getSTEP_ITEM().place(context);
        cir.setReturnValue(result);
    }

    private static boolean more_copycats$isCreateCopycatStepItem(Item item) {
        Identifier id = BuiltInRegistries.ITEM.getKey(item);
        return "create".equals(id.getNamespace()) && "copycat_step".equals(id.getPath());
    }
}

