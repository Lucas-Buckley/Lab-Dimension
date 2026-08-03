package com.inferno.labdimension.player;

import com.inferno.labdimension.LabConfig;
import com.inferno.labdimension.LabDimensionMod;
import com.inferno.labdimension.LabKeys;
import com.inferno.labdimension.dimension.LabPlatformBuilder;
import com.inferno.labdimension.dimension.LabProvider;
import com.inferno.labdimension.dimension.LabRosterData;
import com.inferno.labdimension.dimension.LabSettings;
import com.inferno.labdimension.integration.CarryOnGuard;
import com.inferno.labdimension.integration.QuarkBackSlot;
import com.inferno.labdimension.integration.SableGuard;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Orchestrates the /lab toggle: guards, inventory swap, gamemode, and teleport. */
public final class StashManager {
    private StashManager() {}

    private static final String STATE_TAG = LabKeys.MOD_ID + ":state";
    private static final Set<UUID> IN_FLIGHT = new HashSet<>();

    public static boolean isInFlight(UUID playerId) {
        return IN_FLIGHT.contains(playerId);
    }

    public static void toggle(ServerPlayer player, UUID labOwner) {
        UUID selfId = player.getUUID();
        if (!IN_FLIGHT.add(selfId)) {
            player.sendSystemMessage(fail("Lab toggle already in progress."));
            return;
        }

        Optional<String> guard = checkGuards(player);
        if (guard.isPresent()) {
            IN_FLIGHT.remove(selfId);
            player.sendSystemMessage(fail(guard.get()));
            return;
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            IN_FLIGHT.remove(selfId);
            return;
        }

        boolean currentlyInOwnLab = player.level().dimension().equals(LabProvider.keyFor(labOwner))
                && labOwner.equals(selfId);

        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        CompoundTag state = persisted.getCompound(STATE_TAG);
        boolean inLab = state.getBoolean("inLab");

        if (inLab) {
            returnFromLab(player, server, persisted, state, selfId);
        } else {
            enterLab(player, server, persisted, state, labOwner, selfId);
        }
    }

    private static String labStashKey(UUID owner) {
        return "labStash_" + owner;
    }

    private static Optional<PlayerStash> resolvePreviousLabStash(CompoundTag persisted, String labKey, int currentGeneration) {
        if (!persisted.contains(labKey)) {
            return Optional.empty();
        }
        CompoundTag labTag = persisted.getCompound(labKey);
        int savedGen = labTag.contains("gen") ? labTag.getInt("gen") : 0;
        if (savedGen != currentGeneration) {
            return Optional.empty();
        }
        return Optional.of(PlayerStash.load(labTag));
    }

    private static void enterLab(ServerPlayer player, MinecraftServer server, CompoundTag persisted,
                                  CompoundTag state, UUID labOwner, UUID selfId) {
        PlayerStash outgoing = capture(player);
        CompoundTag stashTag = outgoing.save();

        LabRosterData roster = LabRosterData.get(server.overworld());
        LabSettings creationSettings = roster.entryFor(labOwner); // ensure the entry exists before the lab is built

        persisted.put("overworldStash", stashTag);
        state.putBoolean("pendingToggle", true);
        state.putBoolean("inLab", false); // flips true only after successful teleport
        state.putString("currentLabOwner", labOwner.toString());
        state.putInt("currentLabGeneration", creationSettings.generation);
        persisted.put(STATE_TAG, state);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);

        clearLiveState(player);

        // A lab stash left behind by a since-deleted/regenerated lab must not be replayed
        // into the new one -- that's a dupe vector if the old lab is ever restored from a
        // backup. Discard silently if its recorded generation doesn't match the current one.
        String labKey = labStashKey(labOwner);
        final Optional<PlayerStash> previousLabStash = resolvePreviousLabStash(persisted, labKey, creationSettings.generation);

        LabProvider.getOrCreate(server, labOwner, creationSettings).thenAccept(level -> server.execute(() -> {
            // Re-look-up rather than capturing the entry above: once /lab delete exists
            // the entry can be replaced/removed between this tick and the previous one.
            LabSettings entry = roster.entryFor(labOwner);
            LabPlatformBuilder.ensurePlatform(level, entry);
            roster.setDirty();

            Vec3 spawnPos = previousLabStash
                    .map(PlayerStash::originPos)
                    .filter(p -> p.y > LabConfig.VOID_RESCUE_Y.get())
                    .orElse(entry.spawnPos);
            float spawnYaw = previousLabStash.map(PlayerStash::yaw).orElse(entry.spawnYaw);
            float spawnPitch = previousLabStash.map(PlayerStash::pitch).orElse(entry.spawnPitch);

            DimensionTransition transition = new DimensionTransition(
                    level, spawnPos, Vec3.ZERO, spawnYaw, spawnPitch,
                    DimensionTransition.DO_NOTHING
            );
            ServerPlayer movedTmp = player;
            if (player.changeDimension(transition) instanceof ServerPlayer sp) {
                movedTmp = sp;
            }
            final ServerPlayer moved = movedTmp;

            previousLabStash.ifPresent(stash -> restore(moved, stash));

            if (LabConfig.FORCE_CREATIVE.get()) {
                moved.setGameMode(GameType.CREATIVE);
            }
            moved.setPortalCooldown();
            moved.resetFallDistance();
            moved.setDeltaMovement(Vec3.ZERO);

            CompoundTag p2 = moved.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
            CompoundTag st = p2.getCompound(STATE_TAG);
            st.putBoolean("inLab", true);
            st.putBoolean("pendingToggle", false);
            p2.put(STATE_TAG, st);
            p2.remove(labKey);
            moved.getPersistentData().put(Player.PERSISTED_NBT_TAG, p2);

            IN_FLIGHT.remove(selfId);
            moved.sendSystemMessage(Component.literal("Entered the lab.").withStyle(ChatFormatting.GREEN));
        })).exceptionally(t -> {
            LabDimensionMod.LOGGER.error("Failed to enter lab for {}", selfId, t);
            IN_FLIGHT.remove(selfId);
            player.sendSystemMessage(fail("Failed to enter the lab. Try again."));
            return null;
        });
    }

    private static void returnFromLab(ServerPlayer player, MinecraftServer server, CompoundTag persisted,
                                       CompoundTag state, UUID selfId) {
        CompoundTag stashTag = persisted.getCompound("overworldStash");
        PlayerStash incoming = PlayerStash.load(stashTag);

        // Save what's currently in the player's lab inventory before we clear it,
        // so it's restored on their next visit to this same lab (generation permitting).
        UUID currentLabOwner = state.contains("currentLabOwner")
                ? UUID.fromString(state.getString("currentLabOwner"))
                : player.getUUID();
        int currentLabGeneration = state.contains("currentLabGeneration")
                ? state.getInt("currentLabGeneration")
                : 0;
        PlayerStash labState = capture(player);
        CompoundTag labStashTag = labState.save();
        labStashTag.putInt("gen", currentLabGeneration);
        persisted.put(labStashKey(currentLabOwner), labStashTag);

        ServerLevel targetLevel = server.getLevel(incoming.originDim());
        if (targetLevel == null) {
            targetLevel = server.overworld();
        }

        state.putBoolean("pendingToggle", true);
        persisted.put(STATE_TAG, state);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);

        clearLiveState(player);

        final ServerLevel dest = targetLevel;
        server.execute(() -> {
            DimensionTransition transition = new DimensionTransition(
                    dest,
                    incoming.originPos(),
                    Vec3.ZERO,
                    incoming.yaw(), incoming.pitch(),
                    DimensionTransition.DO_NOTHING
            );
            ServerPlayer moved = player;
            if (player.changeDimension(transition) instanceof ServerPlayer sp) {
                moved = sp;
            }

            restore(moved, incoming);
            moved.setGameMode(incoming.gameMode());
            moved.setPortalCooldown();
            moved.resetFallDistance();
            moved.setDeltaMovement(Vec3.ZERO);

            CompoundTag p2 = moved.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
            CompoundTag st = p2.getCompound(STATE_TAG);
            st.putBoolean("inLab", false);
            st.putBoolean("pendingToggle", false);
            p2.put(STATE_TAG, st);
            p2.remove("overworldStash");
            moved.getPersistentData().put(Player.PERSISTED_NBT_TAG, p2);

            IN_FLIGHT.remove(selfId);
            moved.sendSystemMessage(Component.literal("Left your lab.").withStyle(ChatFormatting.GREEN));
        });
    }

    /** Called on login if a toggle was interrupted mid-flight (server crash/kill). */
    public static void reconcileOnLogin(ServerPlayer player) {
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        CompoundTag state = persisted.getCompound(STATE_TAG);
        if (!state.getBoolean("pendingToggle")) return;

        LabDimensionMod.LOGGER.warn("Reconciling interrupted lab toggle for {}", player.getGameProfile().getName());

        boolean inLab = player.level().dimension().location().getNamespace().equals(LabKeys.MOD_ID);
        state.putBoolean("inLab", inLab);
        state.putBoolean("pendingToggle", false);
        persisted.put(STATE_TAG, state);

        if (persisted.contains("overworldStash")) {
            if (!inLab) {
                restore(player, PlayerStash.load(persisted.getCompound("overworldStash")));
                persisted.remove("overworldStash");
            }
        }
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        player.sendSystemMessage(Component.literal(
                "Your last /lab toggle was interrupted; state has been reconciled.").withStyle(ChatFormatting.YELLOW));
    }

    private static Optional<String> checkGuards(ServerPlayer player) {
        Optional<String> sable = SableGuard.blockingReason(player);
        if (sable.isPresent()) return sable;

        Optional<String> carryOn = CarryOnGuard.blockingReason(player);
        if (carryOn.isPresent()) return carryOn;

        Optional<String> quark = QuarkBackSlot.blockingReason(player);
        if (quark.isPresent()) return quark;

        if (LabConfig.BLOCK_WHILE_RIDING.get() && player.isPassenger()) {
            return Optional.of("Dismount before using /lab.");
        }
        if (player.isSleeping()) {
            return Optional.of("You can't use /lab while sleeping.");
        }
        if (player.containerMenu != player.inventoryMenu) {
            return Optional.of("Close your open container before using /lab.");
        }
        if (LabConfig.BLOCK_WHILE_IN_COMBAT.get()) {
            CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
            long lastCombat = persisted.getLong(LabKeys.MOD_ID + ":lastCombat");
            long elapsed = player.level().getGameTime() - lastCombat;
            if (lastCombat > 0 && elapsed < LabConfig.COMBAT_COOLDOWN_TICKS.get()) {
                return Optional.of("You can't use /lab while in combat.");
            }
        }
        return Optional.empty();
    }

    private static PlayerStash capture(ServerPlayer player) {
        ListTag inv = player.getInventory().save(new ListTag());
        ListTag ender = player.getEnderChestInventory().createTag(player.registryAccess());
        CompoundTag foreign = new CompoundTag();

        CompoundTag food = new CompoundTag();
        player.getFoodData().addAdditionalSaveData(food);

        ListTag effects = new ListTag();
        for (MobEffectInstance inst : player.getActiveEffects()) {
            effects.add(inst.save());
        }

        PlayerStash.Vitals vitals = new PlayerStash.Vitals(
                player.getHealth(),
                player.getAbsorptionAmount(),
                food,
                effects,
                player.getRemainingFireTicks(),
                player.getAirSupply()
        );

        return new PlayerStash(
                inv,
                player.getInventory().selected,
                ender,
                player.experienceLevel,
                player.experienceProgress,
                player.totalExperience,
                player.gameMode.getGameModeForPlayer(),
                player.level().dimension(),
                player.position(),
                player.getYRot(),
                player.getXRot(),
                foreign,
                vitals
        );
    }

    private static void clearLiveState(ServerPlayer player) {
        player.getInventory().clearContent();
        player.getEnderChestInventory().clearContent();

        // Vitals must be reset too, not just inventory -- otherwise poison/wither/fire
        // follow the player into the lab and get captured back into the lab-side stash.
        player.removeAllEffects();
        player.clearFire();
        player.setRemainingFireTicks(0);
        player.setAbsorptionAmount(0f);
        player.setAirSupply(player.getMaxAirSupply());
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(5f);
        player.connection.send(new ClientboundSetHealthPacket(
                player.getHealth(), player.getFoodData().getFoodLevel(), player.getFoodData().getSaturationLevel()));
    }

    private static void restore(ServerPlayer player, PlayerStash stash) {
        player.getInventory().clearContent();
        player.getInventory().load(stash.inventory());
        player.getInventory().selected = stash.selectedSlot();
        player.getEnderChestInventory().fromTag(stash.enderChest(), player.registryAccess());
        player.containerMenu = player.inventoryMenu;
        player.inventoryMenu.broadcastChanges();

        player.experienceLevel = stash.xpLevel();
        player.experienceProgress = stash.xpProgress();
        player.totalExperience = stash.totalXp();
        player.connection.send(new ClientboundSetExperiencePacket(
                stash.xpProgress(), stash.totalXp(), stash.xpLevel()));

        PlayerStash.Vitals vitals = stash.vitals();
        if (vitals != null) {
            player.getFoodData().readAdditionalSaveData(vitals.foodData());
            player.setAbsorptionAmount(vitals.absorption());
            player.setHealth(Mth.clamp(vitals.health(), 0.0f, player.getMaxHealth()));
            player.setRemainingFireTicks(vitals.remainingFireTicks());
            player.setAirSupply(vitals.airSupply());

            player.removeAllEffects();
            ListTag effects = vitals.effects();
            for (int i = 0; i < effects.size(); i++) {
                MobEffectInstance inst = MobEffectInstance.load(effects.getCompound(i));
                if (inst != null) {
                    player.addEffect(inst);
                }
            }

            player.connection.send(new ClientboundSetHealthPacket(
                    player.getHealth(), player.getFoodData().getFoodLevel(), player.getFoodData().getSaturationLevel()));
        }
        // vitals == null means this is a pre-vitals-fix stash: leave health/food/effects
        // exactly as they currently are rather than risk zeroing them out.
    }

    private static Component fail(String msg) {
        return Component.literal(msg).withStyle(ChatFormatting.RED);
    }
}
