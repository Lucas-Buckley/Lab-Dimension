package com.inferno.labdimension.integration;

import com.inferno.labdimension.LabDimensionMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Carry On attaches a CarryOnData object to every player unconditionally
 * (for client sync), so its presence alone (player.hasData(type)) does not
 * mean the player is actually carrying anything -- that produced a false
 * positive that blocked /lab for players with empty inventories.
 *
 * CarryOnData exposes a public isCarrying() method
 * (tschipp.carryon.common.carry.CarryOnData), which we call reflectively
 * since Carry On is not a compile-time dependency.
 */
public final class CarryOnGuard {
    private CarryOnGuard() {}

    private static volatile boolean resolved = false;
    private static Method isCarryingMethod = null;

    public static Optional<String> blockingReason(ServerPlayer player) {
        if (!ModList.get().isLoaded(ModIds.CARRY_ON)) return Optional.empty();

        AttachmentType<?> type = findCarryOnAttachment();
        if (type == null) return Optional.empty();
        if (!player.hasData(type)) return Optional.empty();

        Object data = player.getData(type);
        Boolean carrying = tryIsCarrying(data);
        if (carrying == null) {
            // Reflection failed to resolve isCarrying(); fall back to the old
            // (over-eager) presence check rather than silently allow a toggle
            // that could duplicate a carried block.
            return Optional.of("Put down what you're carrying before using /lab.");
        }
        if (carrying) {
            return Optional.of("Put down what you're carrying before using /lab.");
        }
        return Optional.empty();
    }

    private static AttachmentType<?> findCarryOnAttachment() {
        for (ResourceLocation id : NeoForgeRegistries.ATTACHMENT_TYPES.keySet()) {
            if (id.getNamespace().equals(ModIds.CARRY_ON)) {
                return NeoForgeRegistries.ATTACHMENT_TYPES.get(id);
            }
        }
        return null;
    }

    private static Boolean tryIsCarrying(Object data) {
        if (data == null) return Boolean.FALSE;
        try {
            if (!resolved) {
                isCarryingMethod = data.getClass().getMethod("isCarrying");
                isCarryingMethod.setAccessible(true);
                resolved = true;
            }
            if (isCarryingMethod == null) return null;
            Object result = isCarryingMethod.invoke(data);
            return result instanceof Boolean b ? b : null;
        } catch (ReflectiveOperationException e) {
            resolved = true;
            isCarryingMethod = null;
            LabDimensionMod.LOGGER.warn("Could not reflectively call CarryOnData.isCarrying(): {}", e.toString());
            return null;
        }
    }
}
