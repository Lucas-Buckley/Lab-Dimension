package com.inferno.labdimension.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Wire form of LabSettings.Layer -- block id as a plain string (validated
 *  server-side against BuiltInRegistries.BLOCK, never trusted as-is). */
public record LayerSpec(String block, int height) {
    public static final StreamCodec<RegistryFriendlyByteBuf, LayerSpec> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, LayerSpec::block,
            ByteBufCodecs.VAR_INT, LayerSpec::height,
            LayerSpec::new
    );
}
