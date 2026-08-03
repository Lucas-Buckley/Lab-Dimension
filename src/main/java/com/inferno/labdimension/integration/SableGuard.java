package com.inferno.labdimension.integration;

import com.inferno.labdimension.LabConfig;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

import java.util.Optional;

/**
 * Refuses a lab toggle while the player is standing on / riding a Sable
 * sub-level (ship). Teleporting a player off a sub-level mid-flight is the
 * single most likely crash/desync in this mod, so we refuse loudly rather
 * than attempt to "handle" it.
 */
public final class SableGuard {
    private SableGuard() {}

    public static Optional<String> blockingReason(ServerPlayer player) {
        if (!LabConfig.BLOCK_ON_SABLE_SHIP.get()) return Optional.empty();
        if (!ModList.get().isLoaded(ModIds.SABLE)) return Optional.empty();

        if (player.getRootVehicle() != player) {
            return Optional.of("You need to dismount before using /lab.");
        }

        String dimNamespace = player.level().dimension().location().getNamespace();
        if (dimNamespace.equals(ModIds.SABLE)) {
            return Optional.of("You can't use /lab while aboard a ship.");
        }

        return Optional.empty();
    }
}
