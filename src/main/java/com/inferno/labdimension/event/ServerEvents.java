package com.inferno.labdimension.event;

import com.inferno.labdimension.LabConfig;
import com.inferno.labdimension.LabDimensionMod;
import com.inferno.labdimension.LabKeys;
import com.inferno.labdimension.command.LabCommand;
import com.inferno.labdimension.dimension.LabProvider;
import com.inferno.labdimension.dimension.LabRosterData;
import com.inferno.labdimension.dimension.LabSettings;
import com.inferno.labdimension.integration.WorldEditGate;
import com.inferno.labdimension.player.StashManager;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.dimension.DimensionType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.UUID;

@EventBusSubscriber(modid = LabKeys.MOD_ID)
public final class ServerEvents {
    private ServerEvents() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        LabCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        var server = event.getServer();
        LabRosterData roster = LabRosterData.get(server.overworld());

        migrateOnMinYChange(server, roster);
        com.inferno.labdimension.dimension.LabLifecycle.processPendingDeletions(server);

        int count = 0;
        for (UUID owner : roster.allOwners()) {
            LabProvider.getOrCreate(server, owner);
            count++;
        }
        LabDimensionMod.LOGGER.info("Eagerly recreating {} known lab dimension(s) so their keys are in the login set.", count);

        WorldEditGate.discoverRoots(server);
        WorldEditGate.setMayBuildHere(player ->
                com.inferno.labdimension.dimension.LabAccess.accessHere(player)
                        .atLeast(com.inferno.labdimension.dimension.LabAccess.Access.WORLDEDIT));
    }

    /**
     * If the labdimension:lab DimensionType's min_y differs from what's recorded on
     * the roster, every existing lab's chunk data is invalid (section count changed)
     * and cannot be loaded in place. Rather than let that corrupt worlds silently,
     * bump every lab's generation so each gets a fresh dimension key at a fresh
     * min_y, and queue the old (now-unreadable) directories for deletion. The first
     * boot ever (UNKNOWN_MIN_Y) is not a "change" -- there is nothing to migrate.
     */
    private static void migrateOnMinYChange(net.minecraft.server.MinecraftServer server, LabRosterData roster) {
        DimensionType liveType = server.registryAccess()
                .lookupOrThrow(Registries.DIMENSION_TYPE)
                .getOrThrow(LabKeys.LAB_DIMENSION_TYPE)
                .value();
        int liveMinY = liveType.minY();

        if (roster.getWorldMinY() == liveMinY) {
            return;
        }
        if (roster.getWorldMinY() == LabRosterData.UNKNOWN_MIN_Y) {
            roster.setWorldMinY(liveMinY);
            return;
        }

        int migrated = 0;
        for (UUID owner : roster.allOwners()) {
            LabSettings s = roster.entryFor(owner);
            ResourceLocation oldDim = LabKeys.labPath(owner, s.generation);
            roster.addPendingDeletion(oldDim);
            s.generation++;
            s.platformGenerated = false;
            migrated++;
        }
        LabDimensionMod.LOGGER.warn(
                "labdimension:lab min_y changed ({} -> {}); regenerated {} lab(s) into new dimensions. "
                        + "Old lab contents are lost -- this only affects test/dev labs.",
                roster.getWorldMinY(), liveMinY, migrated);
        roster.setWorldMinY(liveMinY);
    }

    @SubscribeEvent
    public static void onCommand(CommandEvent event) {
        WorldEditGate.onCommand(event);
    }

    @SubscribeEvent
    public static void onPlayerLogin(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            StashManager.reconcileOnLogin(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            com.inferno.labdimension.command.LabConfirm.clear(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (event.getOriginal() instanceof ServerPlayer original && event.getEntity() instanceof ServerPlayer clone) {
            CompoundTag persisted = original.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
            clone.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted.copy());
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!player.level().dimension().location().getNamespace().equals(LabKeys.MOD_ID)) return;
        if (player.getY() < LabConfig.VOID_RESCUE_Y.get()) {
            player.teleportTo(0.5, LabConfig.LAB_SPAWN_Y.get(), 0.5);
            player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        }
    }

    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            player.getPersistentData()
                    .getCompound(Player.PERSISTED_NBT_TAG)
                    .putLong(LabKeys.MOD_ID + ":lastCombat", player.level().getGameTime());
        }
    }
}
