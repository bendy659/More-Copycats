package ru.benos_codex.more_copycats.mixin.create.block.entity;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.zurrtum.create.content.decoration.copycat.CopycatBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import ru.benos_codex.more_copycats.CopycatBlockEntityTypeHelper;

@Mixin(value = CopycatBlockEntity.class, remap = false)
public class CopycatBlockEntityMixin {

    /**
     * Поле COPYCAT читается ДО вызова super(), поэтому this недоступен —
     * метод обязан быть статическим. Вместо this используем ThreadLocal,
     * который выставляется в фабрике BlockEntityType перед созданием объекта.
     */
    @ModifyExpressionValue(
            method = "<init>",
            at = @At(
                    value = "FIELD",
                    opcode = Opcodes.GETSTATIC,
                    target = "Lcom/zurrtum/create/AllBlockEntityTypes;COPYCAT:Lnet/minecraft/world/level/block/entity/BlockEntityType;"
            ),
            remap = false
    )
    @SuppressWarnings("unused")
    private static BlockEntityType<?> more_copycats$redirectBlockEntityType(BlockEntityType<?> original) {
        BlockEntityType<?> override = CopycatBlockEntityTypeHelper.OVERRIDE.get();
        return override != null ? override : original;
    }
}
