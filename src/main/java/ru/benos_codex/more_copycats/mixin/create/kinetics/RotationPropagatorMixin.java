package ru.benos_codex.more_copycats.mixin.create.kinetics;

import com.zurrtum.create.content.kinetics.RotationPropagator;
import com.zurrtum.create.content.kinetics.base.DirectionalShaftHalvesBlockEntity;
import com.zurrtum.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.benos_codex.more_copycats.block.entity.CopycatGearboxBlockEntity;

@Mixin(value = RotationPropagator.class, remap = false)
public class RotationPropagatorMixin {

    @Inject(method = "getAxisModifier", at = @At("HEAD"), cancellable = true, remap = false)
    private static void more_copycats$axisModifier(
            KineticBlockEntity be,
            Direction direction,
            CallbackInfoReturnable<Float> cir
    ) {
        if (!(be instanceof CopycatGearboxBlockEntity)) {
            return;
        }

        if (!(be.hasSource() || be.isSource()) || !(be instanceof DirectionalShaftHalvesBlockEntity)) {
            cir.setReturnValue(1.0f);
            return;
        }

        Direction source = ((DirectionalShaftHalvesBlockEntity) be).getSourceFacing();
        float result = direction.getAxis() == source.getAxis()
                ? (direction == source ? 1.0f : -1.0f)
                : (direction.getAxisDirection() == source.getAxisDirection() ? -1.0f : 1.0f);
        cir.setReturnValue(result);
    }
}
