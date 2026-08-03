package com.inferno.labdimension.integration;

import com.inferno.labdimension.LabConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.Optional;
import java.util.Set;

/**
 * Detects third-party mod state attached to a player outside vanilla
 * inventory slots (e.g. Quark's back slot via a data attachment, or Carry
 * On's carried block via persisted NBT).
 *
 * NeoForge's AttachmentType does not expose its serializer publicly, so this
 * mod cannot generically read/write arbitrary attachment data across a
 * dimension toggle. Rather than risk silently losing or duplicating state we
 * cannot serialize, presence of configured namespaces simply blocks the
 * toggle -- see LabConfig.swapNamespaces/blockNamespaces and quarkStrict.
 */
public final class ForeignStateScanner {
    private ForeignStateScanner() {}

    public static Optional<String> findBlockingState(ServerPlayer player, Set<String> namespaces) {
        if (namespaces.isEmpty()) return Optional.empty();

        for (ResourceLocation id : NeoForgeRegistries.ATTACHMENT_TYPES.keySet()) {
            if (!namespaces.contains(id.getNamespace())) continue;
            AttachmentType<?> type = NeoForgeRegistries.ATTACHMENT_TYPES.get(id);
            if (type != null && player.hasData(type)) {
                return Optional.of(id.getNamespace());
            }
        }

        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        for (String key : persisted.getAllKeys()) {
            for (String ns : namespaces) {
                if (key.startsWith(ns)) {
                    return Optional.of(ns);
                }
            }
        }
        return Optional.empty();
    }

    public static Optional<String> findBlockingState(ServerPlayer player) {
        return findBlockingState(player, Set.copyOf(LabConfig.BLOCK_NAMESPACES.get()));
    }
}
