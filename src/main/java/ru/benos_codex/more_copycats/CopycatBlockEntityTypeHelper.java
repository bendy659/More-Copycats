package ru.benos.more_copycats;

import net.minecraft.world.level.block.entity.BlockEntityType;

public class CopycatBlockEntityTypeHelper {
    public static final ThreadLocal<BlockEntityType<?>> OVERRIDE = new ThreadLocal<>();
}