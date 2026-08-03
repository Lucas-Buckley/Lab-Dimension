package com.inferno.labdimension.network;

import com.inferno.labdimension.LabKeys;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record S2COpenLabSettings(
        LabRuntimeSpec runtime,
        String ownerName,
        boolean worldEditPresent,
        boolean canEditGeneration
) implements CustomPacketPayload {
    public static final Type<S2COpenLabSettings> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LabKeys.MOD_ID, "open_settings"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2COpenLabSettings> STREAM_CODEC = StreamCodec.composite(
            LabRuntimeSpec.STREAM_CODEC, S2COpenLabSettings::runtime,
            ByteBufCodecs.STRING_UTF8, S2COpenLabSettings::ownerName,
            ByteBufCodecs.BOOL, S2COpenLabSettings::worldEditPresent,
            ByteBufCodecs.BOOL, S2COpenLabSettings::canEditGeneration,
            S2COpenLabSettings::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
