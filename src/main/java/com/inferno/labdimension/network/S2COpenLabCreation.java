package com.inferno.labdimension.network;

import com.inferno.labdimension.LabKeys;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record S2COpenLabCreation(
        LabGenSpec defaults,
        LabRuntimeSpec runtime,
        int minY,
        boolean replacingExisting,
        String confirmToken,
        boolean worldEditPresent
) implements CustomPacketPayload {
    public static final Type<S2COpenLabCreation> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LabKeys.MOD_ID, "open_creation"));

    // Exactly 6 top-level fields -- StreamCodec.composite's ceiling. Do not add a
    // 7th here; nest a new sub-record instead (see LabRuntimeSpec/LabGenSpec).
    public static final StreamCodec<RegistryFriendlyByteBuf, S2COpenLabCreation> STREAM_CODEC = StreamCodec.composite(
            LabGenSpec.STREAM_CODEC, S2COpenLabCreation::defaults,
            LabRuntimeSpec.STREAM_CODEC, S2COpenLabCreation::runtime,
            ByteBufCodecs.VAR_INT, S2COpenLabCreation::minY,
            ByteBufCodecs.BOOL, S2COpenLabCreation::replacingExisting,
            ByteBufCodecs.STRING_UTF8, S2COpenLabCreation::confirmToken,
            ByteBufCodecs.BOOL, S2COpenLabCreation::worldEditPresent,
            S2COpenLabCreation::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
