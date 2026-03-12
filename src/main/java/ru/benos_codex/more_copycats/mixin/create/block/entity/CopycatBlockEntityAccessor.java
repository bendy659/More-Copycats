package ru.benos_codex.more_copycats.mixin.create.block.entity;

import com.zurrtum.create.content.decoration.copycat.CopycatBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = CopycatBlockEntity.class, remap = false)
public interface CopycatBlockEntityAccessor {
    @Accessor("material")
    void moreCopycats_setMaterial(BlockState material);

    @Accessor("consumedItem")
    void moreCopycats_setConsumedItem(ItemStack consumedItem);
}
