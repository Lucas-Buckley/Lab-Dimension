package com.inferno.labdimension.integration;

import com.inferno.labdimension.LabConfig;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

import java.util.Optional;
import java.util.Set;

/**
 * Quark's back slot is not a compile-time dependency, and NeoForge's
 * AttachmentType does not expose a public serializer, so this mod cannot
 * generically move that data across a dimension toggle. If Quark/Zeta is
 * present and appears to be holding data on the player, refuse the toggle
 * rather than risk a silent item duplication (e.g. an elytra ghosting across
 * both inventories). Run `/lab debug attachments` on the live server to see
 * exactly which attachment keys Quark registers before deciding whether this
 * is overly strict for your modlist.
 */
public final class QuarkBackSlot {
    private QuarkBackSlot() {}

    private static final Set<String> QUARK_NAMESPACES = Set.of(ModIds.QUARK, ModIds.ZETA);

    public static Optional<String> blockingReason(ServerPlayer player) {
        if (!LabConfig.QUARK_STRICT.get()) return Optional.empty();
        if (!ModList.get().isLoaded(ModIds.QUARK) && !ModList.get().isLoaded(ModIds.ZETA)) {
            return Optional.empty();
        }
        Optional<String> found = ForeignStateScanner.findBlockingState(player, QUARK_NAMESPACES);
        if (found.isPresent()) {
            return Optional.of("Lab: can't safely move your Quark back slot. Empty it and try again.");
        }
        return Optional.empty();
    }
}
