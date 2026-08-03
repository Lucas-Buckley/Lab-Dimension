package com.inferno.labdimension.dimension;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class LabPlatformBuilder {
    private LabPlatformBuilder() {}

    public static void ensurePlatform(ServerLevel lab, LabSettings settings) {
        if (settings.platformGenerated) {
            return;
        }

        // Superflat labs already have ground; only VOID labs need a built platform.
        if (settings.worldType == LabSettings.WorldType.SUPERFLAT) {
            lab.setDefaultSpawnPos(BlockPos.containing(settings.spawnPos), settings.spawnYaw);
            settings.platformGenerated = true;
            return;
        }

        BlockState material = BuiltInRegistries.BLOCK
                .getOptional(settings.platformBlock)
                .map(Block::defaultBlockState)
                .orElse(Blocks.SMOOTH_STONE.defaultBlockState());

        int radius = settings.platformRadius;
        int y = (int) Math.floor(settings.spawnPos.y) - 1;

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                pos.set(x, y, z);
                lab.setBlock(pos, material, Block.UPDATE_CLIENTS);
            }
        }

        lab.setDefaultSpawnPos(BlockPos.containing(settings.spawnPos), settings.spawnYaw);

        settings.platformGenerated = true;
    }
}
