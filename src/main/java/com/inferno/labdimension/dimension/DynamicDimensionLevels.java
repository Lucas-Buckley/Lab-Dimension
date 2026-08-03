package com.inferno.labdimension.dimension;

import com.google.common.collect.ImmutableList;
import com.inferno.labdimension.LabDimensionMod;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.border.BorderChangeListener;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.lang.reflect.Field;
import java.util.concurrent.CompletableFuture;

/**
 * Runtime construction and registration of a per-player lab ServerLevel.
 *
 * This deliberately uses the stock ServerLevel constructor (mirroring
 * MinecraftServer#createLevels) with no wrapping/subclassing, because Sable's
 * plot mixins hook that constructor directly to attach sub-level (ship) support.
 */
public final class DynamicDimensionLevels {
    private DynamicDimensionLevels() {}

    /**
     * Must be called on the server thread. Safe to call from within a tick;
     * internally this defers the actual registration via server.execute to
     * avoid mutating MinecraftServer#levels while it is being iterated.
     */
    public static CompletableFuture<ServerLevel> getOrCreate(MinecraftServer server,
                                                              ResourceKey<Level> levelKey,
                                                              LevelStem stem) {
        ServerLevel existing = server.levels.get(levelKey);
        if (existing != null) {
            return CompletableFuture.completedFuture(existing);
        }
        CompletableFuture<ServerLevel> future = new CompletableFuture<>();
        server.execute(() -> {
            try {
                ServerLevel already = server.levels.get(levelKey);
                if (already != null) {
                    future.complete(already);
                    return;
                }
                ServerLevel level = createAndRegister(server, levelKey, stem);
                future.complete(level);
            } catch (Throwable t) {
                LabDimensionMod.LOGGER.error("Failed to create lab dimension {}", levelKey.location(), t);
                future.completeExceptionally(t);
            }
        });
        return future;
    }

    private static ServerLevel createAndRegister(MinecraftServer server,
                                                  ResourceKey<Level> levelKey,
                                                  LevelStem stem) {
        addStemToRegistry(server, levelKey, stem);

        ServerLevel overworld = server.overworld();

        DerivedLevelData data = new DerivedLevelData(server.getWorldData(),
                server.getWorldData().overworldData());

        long obfSeed = BiomeManager.obfuscateSeed(server.getWorldData().worldGenOptions().seed());
        ChunkProgressListener listener = server.progressListenerFactory.create(11);

        int sizeBefore = server.levels.size();

        ServerLevel level = new ServerLevel(
                server,
                server.executor,
                server.storageSource,
                data,
                levelKey,
                stem,
                listener,
                server.getWorldData().isDebugWorld(),
                obfSeed,
                ImmutableList.of(),
                false,
                overworld.getRandomSequences()
        );

        overworld.getWorldBorder().addListener(new BorderChangeListener.DelegateBorderChangeListener(level.getWorldBorder()));

        server.levels.put(levelKey, level);

        LabDimensionMod.LOGGER.info("Created lab dimension {} ({} -> {} levels)",
                levelKey.location(), sizeBefore, server.levels.size());

        NeoForge.EVENT_BUS.post(new LevelEvent.Load(level));

        return level;
    }

    @SuppressWarnings("unchecked")
    private static void addStemToRegistry(MinecraftServer server, ResourceKey<Level> levelKey, LevelStem stem) {
        Registry<LevelStem> registry = server.registryAccess().registryOrThrow(Registries.LEVEL_STEM);
        if (registry.containsKey(levelKey.location())) {
            return;
        }
        MappedRegistry<LevelStem> mapped = (MappedRegistry<LevelStem>) registry;

        boolean wasFrozen = getFrozen(mapped);
        setFrozen(mapped, false);
        try {
            ResourceKey<LevelStem> stemKey = ResourceKey.create(Registries.LEVEL_STEM, levelKey.location());
            mapped.register(stemKey, stem, RegistrationInfo.BUILT_IN);
        } finally {
            setFrozen(mapped, wasFrozen);
        }
    }

    // --- Reflective access as a fallback if the access transformer entries don't
    // resolve against the mapped field names on a given build; prefer the AT path.
    private static boolean getFrozen(MappedRegistry<?> registry) {
        try {
            return registry.frozen;
        } catch (Throwable t) {
            return reflectBoolean(registry, "frozen");
        }
    }

    private static void setFrozen(MappedRegistry<?> registry, boolean value) {
        try {
            registry.frozen = value;
        } catch (Throwable t) {
            reflectSetBoolean(registry, "frozen", value);
        }
    }

    private static boolean reflectBoolean(Object target, String fieldName) {
        try {
            Field f = target.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            return f.getBoolean(target);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static void reflectSetBoolean(Object target, String fieldName, boolean value) {
        try {
            Field f = target.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            f.setBoolean(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
