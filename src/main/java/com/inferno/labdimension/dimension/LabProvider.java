package com.inferno.labdimension.dimension;

import com.inferno.labdimension.LabDimensionMod;
import com.inferno.labdimension.LabKeys;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.flat.FlatLayerInfo;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Resolves a player's personal lab level, creating it on first use.
 * Per-player labs (Plan A): one ResourceKey<Level> per (owner UUID, generation),
 * all sharing the labdimension:lab DimensionType so only the level key is novel.
 */
public final class LabProvider {
    private LabProvider() {}

    public static ResourceKey<Level> keyFor(UUID owner) {
        return keyFor(owner, 0);
    }

    public static ResourceKey<Level> keyFor(UUID owner, int generation) {
        return ResourceKey.create(Registries.DIMENSION, LabKeys.labPath(owner, generation));
    }

    public static boolean exists(MinecraftServer server, UUID owner) {
        return server.levels.containsKey(keyFor(owner));
    }

    public static boolean exists(MinecraftServer server, UUID owner, int generation) {
        return server.levels.containsKey(keyFor(owner, generation));
    }

    public static CompletableFuture<ServerLevel> getOrCreate(MinecraftServer server, UUID owner) {
        return getOrCreate(server, owner, LabSettingsDefault(server, owner));
    }

    public static CompletableFuture<ServerLevel> getOrCreate(MinecraftServer server, UUID owner, LabSettings settings) {
        ResourceKey<Level> key = keyFor(owner, settings.generation);
        ServerLevel existing = server.levels.get(key);
        if (existing != null) {
            return CompletableFuture.completedFuture(existing);
        }
        LevelStem stem = buildStem(server, settings);
        return DynamicDimensionLevels.getOrCreate(server, key, stem);
    }

    /** Convenience for callers (e.g. eager recreation at boot) that only have the owner
     *  and want whatever settings are already on the roster. */
    private static LabSettings LabSettingsDefault(MinecraftServer server, UUID owner) {
        LabRosterData roster = LabRosterData.get(server.overworld());
        LabSettings existing = roster.peek(owner);
        return existing != null ? existing : roster.entryFor(owner);
    }

    private static LevelStem buildStem(MinecraftServer server, LabSettings settings) {
        Holder<DimensionType> type = server.registryAccess()
                .lookupOrThrow(Registries.DIMENSION_TYPE)
                .getOrThrow(LabKeys.LAB_DIMENSION_TYPE);

        ChunkGenerator generator = settings.worldType == LabSettings.WorldType.SUPERFLAT
                ? buildSuperflatGenerator(server, settings)
                : buildVoidGenerator(server);

        return new LevelStem(type, generator);
    }

    private static ChunkGenerator buildVoidGenerator(MinecraftServer server) {
        Holder<Biome> theVoid = server.registryAccess()
                .lookupOrThrow(Registries.BIOME)
                .getOrThrow(Biomes.THE_VOID);

        FlatLevelGeneratorSettings flatSettings = new FlatLevelGeneratorSettings(
                Optional.empty(), theVoid, List.of());
        return new FlatLevelSource(flatSettings);
    }

    private static ChunkGenerator buildSuperflatGenerator(MinecraftServer server, LabSettings settings) {
        Holder<Biome> plains = server.registryAccess()
                .lookupOrThrow(Registries.BIOME)
                .getOrThrow(Biomes.PLAINS);

        FlatLevelGeneratorSettings flatSettings = new FlatLevelGeneratorSettings(
                Optional.empty(), plains, List.of());

        // getLayersInfo() is the LIVE mutable list backing this settings object.
        flatSettings.getLayersInfo().clear();
        for (LabSettings.Layer l : settings.superflatLayers) {
            Block block = BuiltInRegistries.BLOCK.getOptional(l.block()).orElse(Blocks.STONE);
            flatSettings.getLayersInfo().add(new FlatLayerInfo(l.height(), block));
        }
        // MANDATORY: without this the generator silently produces a void world --
        // getLayers() stays empty and fillFromNoise has nothing to place.
        flatSettings.updateLayers();

        if (flatSettings.getLayers().isEmpty()) {
            LabDimensionMod.LOGGER.error(
                    "Superflat lab settings produced zero generated layers (bad layer list?); "
                            + "falling back to a stone floor to avoid a silent void world.");
            flatSettings.getLayersInfo().add(new FlatLayerInfo(1, Blocks.STONE));
            flatSettings.updateLayers();
        }

        return new FlatLevelSource(flatSettings);
    }
}
