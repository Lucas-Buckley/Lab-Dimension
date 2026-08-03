package com.inferno.labdimension.network;

import com.inferno.labdimension.LabKeys;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> server "save my lab settings". The target lab is ALWAYS the
 *  sending player's own (resolved server-side from the connection, never
 *  from any field here) -- there is deliberately no owner/UUID field. */
public record C2SUpdateLabSettings(LabRuntimeSpec runtime) implements CustomPacketPayload {
    public static final Type<C2SUpdateLabSettings> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LabKeys.MOD_ID, "update_settings"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SUpdateLabSettings> STREAM_CODEC =
            LabRuntimeSpec.STREAM_CODEC.map(C2SUpdateLabSettings::new, C2SUpdateLabSettings::runtime);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
