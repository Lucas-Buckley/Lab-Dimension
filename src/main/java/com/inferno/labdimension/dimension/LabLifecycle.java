package com.inferno.labdimension.dimension;

import com.inferno.labdimension.LabDimensionMod;
import com.inferno.labdimension.LabKeys;
import com.inferno.labdimension.player.StashManager;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Replacing or deleting a lab. A live ServerLevel is NEVER removed from
 * server.levels -- that is where mods holding ServerLevel references off-thread
 * crash. Instead a replacement gets a new dimension key (lab_<uuid>_g<N>) and the
 * old level's directory is queued for deletion at the next server boot, when no
 * file handle to it exists in this JVM.
 */
public final class LabLifecycle {
    private LabLifecycle() {}

    /** Regenerations are capped per player per server session to bound the number
     *  of orphaned (empty, still-ticking) ServerLevels that accumulate until the
     *  next restart. Reset naturally on restart since this is in-memory only. */
    private static final int MAX_REGENERATIONS_PER_SESSION = 2;
    private static final Map<UUID, Integer> REGENERATIONS_THIS_SESSION = new HashMap<>();

    public enum Result { OK, IN_FLIGHT, REGEN_LIMIT, STILL_OCCUPIED, NO_LAB }

    public static CompletableFuture<Result> replace(MinecraftServer server, UUID owner, LabSettings newSettings) {
        LabRosterData roster = LabRosterData.get(server.overworld());
        LabSettings current = roster.peek(owner);
        if (current == null) {
            return CompletableFuture.completedFuture(Result.NO_LAB);
        }
        if (StashManager.isInFlight(owner)) {
            return CompletableFuture.completedFuture(Result.IN_FLIGHT);
        }
        int used = REGENERATIONS_THIS_SESSION.getOrDefault(owner, 0);
        if (used >= MAX_REGENERATIONS_PER_SESSION) {
            return CompletableFuture.completedFuture(Result.REGEN_LIMIT);
        }

        ResourceKey<Level> oldKey = LabProvider.keyFor(owner, current.generation);

        CompletableFuture<Result> result = new CompletableFuture<>();
        ejectAllThenProceed(server, oldKey, owner, 0, () -> server.execute(() -> {
            List<ServerPlayer> stillIn = occupantsOf(server, oldKey);
            if (!stillIn.isEmpty()) {
                result.complete(Result.STILL_OCCUPIED);
                return;
            }

            purgeLabStashes(server, owner, current.generation);

            ServerLevel old = server.levels.get(oldKey);
            if (old != null) {
                old.save(null, true, false);
            }

            roster.addPendingDeletion(oldKey.location());

            newSettings.generation = current.generation + 1;
            newSettings.platformGenerated = false;
            newSettings.createdAt = current.createdAt;
            // Preserve whitelist/blacklist/visibility/guestPermission/displayName from the
            // caller-provided newSettings -- callers build it from current.copy() and edit
            // only the fields they intend to change.

            roster.replaceEntry(owner, newSettings);
            roster.setDirty();

            REGENERATIONS_THIS_SESSION.merge(owner, 1, Integer::sum);

            LabProvider.getOrCreate(server, owner, newSettings)
                    .thenAccept(level -> result.complete(Result.OK))
                    .exceptionally(t -> {
                        LabDimensionMod.LOGGER.error("Failed to create replacement lab for {}", owner, t);
                        result.completeExceptionally(t);
                        return null;
                    });
        }));
        return result;
    }

    public static CompletableFuture<Result> delete(MinecraftServer server, UUID owner) {
        LabRosterData roster = LabRosterData.get(server.overworld());
        LabSettings current = roster.peek(owner);
        if (current == null) {
            return CompletableFuture.completedFuture(Result.NO_LAB);
        }
        if (StashManager.isInFlight(owner)) {
            return CompletableFuture.completedFuture(Result.IN_FLIGHT);
        }

        ResourceKey<Level> oldKey = LabProvider.keyFor(owner, current.generation);

        CompletableFuture<Result> result = new CompletableFuture<>();
        ejectAllThenProceed(server, oldKey, owner, 0, () -> server.execute(() -> {
            List<ServerPlayer> stillIn = occupantsOf(server, oldKey);
            if (!stillIn.isEmpty()) {
                result.complete(Result.STILL_OCCUPIED);
                return;
            }

            purgeLabStashes(server, owner, current.generation);

            ServerLevel old = server.levels.get(oldKey);
            if (old != null) {
                old.save(null, true, false);
            }

            roster.addPendingDeletion(oldKey.location());
            roster.forget(owner);
            result.complete(Result.OK);
        }));
        return result;
    }

    /** Ejects every occupant via the real return-toggle (never a raw teleport --
     *  that would strand them in survival with an empty inventory), re-checks after
     *  a tick, and retries up to two attempts before giving up. */
    private static void ejectAllThenProceed(MinecraftServer server, ResourceKey<Level> key, UUID owner,
                                             int attempt, Runnable onDone) {
        List<ServerPlayer> occupants = occupantsOf(server, key);
        for (ServerPlayer p : occupants) {
            if (!StashManager.isInFlight(p.getUUID())) {
                StashManager.toggle(p, owner);
            }
        }
        if (attempt >= 2) {
            onDone.run();
            return;
        }
        server.execute(() -> ejectAllThenProceed(server, key, owner, attempt + 1, onDone));
    }

    private static List<ServerPlayer> occupantsOf(MinecraftServer server, ResourceKey<Level> key) {
        return server.getPlayerList().getPlayers().stream()
                .filter(p -> p.level().dimension().equals(key))
                .collect(Collectors.toList());
    }

    private static void purgeLabStashes(MinecraftServer server, UUID owner, int generation) {
        String labKey = "labStash_" + owner;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            var persisted = p.getPersistentData()
                    .getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);
            if (persisted.contains(labKey)) {
                var tag = persisted.getCompound(labKey);
                int gen = tag.contains("gen") ? tag.getInt("gen") : 0;
                if (gen == generation) {
                    persisted.remove(labKey);
                    p.getPersistentData().put(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG, persisted);
                }
            }
        }
        // Offline players are handled by the generation check itself: enterLab
        // discards any labStash whose "gen" doesn't match the lab's current
        // generation, so nothing further is needed for players who aren't online.
    }

    /** Deletes every queued dimension directory. Must run at ServerStartedEvent,
     *  BEFORE eager recreation, on a fresh JVM where no file handle to any of these
     *  directories exists yet -- that ordering is the entire safety property. */
    public static void processPendingDeletions(MinecraftServer server) {
        LabRosterData roster = LabRosterData.get(server.overworld());
        Path worldDimensionsRoot = server.storageSource
                .getLevelPath(LevelResource.ROOT)
                .resolve("dimensions")
                .resolve(LabKeys.MOD_ID)
                .toAbsolutePath()
                .normalize();

        for (ResourceLocation id : roster.pendingDeletion()) {
            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, id);

            if (server.levels.containsKey(key)) {
                LabDimensionMod.LOGGER.error("Refusing to delete {}: level is loaded", id);
                continue;
            }

            Path dir = server.storageSource.getDimensionPath(key).toAbsolutePath().normalize();
            if (!dir.startsWith(worldDimensionsRoot)) {
                // Must never happen for a well-formed labdimension:lab_* key, but the cost
                // of a parser bug here is deleting the wrong directory -- refuse hard.
                LabDimensionMod.LOGGER.error(
                        "Refusing to delete {}: resolved path {} escapes {}", id, dir, worldDimensionsRoot);
                continue;
            }

            if (!Files.exists(dir)) {
                roster.clearPendingDeletion(id);
                continue;
            }

            try {
                deleteRecursively(dir);
                roster.clearPendingDeletion(id);
                LabDimensionMod.LOGGER.info("Deleted regenerated/removed lab dimension directory {}", dir);
            } catch (IOException e) {
                LabDimensionMod.LOGGER.warn("Deferred deletion of {} failed; will retry next boot", dir, e);
                // deliberately left queued -- self-heals on a later boot once file locks clear
            }
        }
        roster.setDirty();
    }

    private static void deleteRecursively(Path dir) throws IOException {
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path p : walk.sorted((a, b) -> b.getNameCount() - a.getNameCount()).toList()) {
                Files.delete(p);
            }
        }
    }
}
