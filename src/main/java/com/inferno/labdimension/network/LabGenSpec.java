package com.inferno.labdimension.network;

import com.inferno.labdimension.dimension.LabSettings;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;

import java.util.List;

/** The creation-time half of a lab's settings -- changing any of this requires
 *  regenerating into a new dimension (see LabLifecycle.replace). */
public record LabGenSpec(
        LabSettings.WorldType worldType,
        List<LayerSpec> layers,
        int platformRadius,
        String platformBlock
) {
    public static final StreamCodec<RegistryFriendlyByteBuf, LabGenSpec> STREAM_CODEC = StreamCodec.composite(
            NeoForgeStreamCodecs.enumCodec(LabSettings.WorldType.class), LabGenSpec::worldType,
            LayerSpec.STREAM_CODEC.apply(ByteBufCodecs.list(32)), LabGenSpec::layers,
            ByteBufCodecs.VAR_INT, LabGenSpec::platformRadius,
            ByteBufCodecs.STRING_UTF8, LabGenSpec::platformBlock,
            LabGenSpec::new
    );

    public static LabGenSpec from(LabSettings s) {
        return new LabGenSpec(
                s.worldType,
                s.superflatLayers.stream().map(l -> new LayerSpec(l.block().toString(), l.height())).toList(),
                s.platformRadius,
                s.platformBlock.toString()
        );
    }
}
