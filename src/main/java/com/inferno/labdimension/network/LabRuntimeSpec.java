package com.inferno.labdimension.network;

import com.inferno.labdimension.dimension.LabSettings;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;

import java.util.List;

/**
 * The runtime-changeable half of a lab's settings, shared between the
 * server->client "here's the current state" payload and the client->server
 * "save these values" payload. Player identity always travels as NAMES, never
 * UUIDs -- the server resolves names on the way in, and this record is never
 * trusted to name which lab it applies to (that always comes from the sending
 * player's own UUID, resolved server-side).
 */
public record LabRuntimeSpec(
        LabSettings.Visibility visibility,
        LabSettings.GuestPermission guestPermission,
        String displayName,
        List<String> whitelistNames,
        List<String> blacklistNames
) {
    public static final StreamCodec<RegistryFriendlyByteBuf, LabRuntimeSpec> STREAM_CODEC = StreamCodec.composite(
            NeoForgeStreamCodecs.enumCodec(LabSettings.Visibility.class), LabRuntimeSpec::visibility,
            NeoForgeStreamCodecs.enumCodec(LabSettings.GuestPermission.class), LabRuntimeSpec::guestPermission,
            ByteBufCodecs.STRING_UTF8, LabRuntimeSpec::displayName,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(64)), LabRuntimeSpec::whitelistNames,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(64)), LabRuntimeSpec::blacklistNames,
            LabRuntimeSpec::new
    );
}
