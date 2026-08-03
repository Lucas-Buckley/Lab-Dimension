package com.inferno.labdimension.network;

import com.inferno.labdimension.LabKeys;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> server "create/replace my lab with these settings". As with
 *  C2SUpdateLabSettings there is deliberately no owner field -- the target is
 *  always the sending player's own connection. confirmToken is empty ("") when
 *  the player has no existing lab (nothing to confirm); when replacing an
 *  existing lab it must match the token LabConfirm issued in the
 *  S2COpenLabCreation that opened this screen. runtime carries the creation
 *  screen's name/visibility/guests/whitelist/blacklist fields so a lab is
 *  fully configured in one submission rather than needing a follow-up
 *  /lab settings save. */
public record C2SCreateLab(String confirmToken, LabGenSpec gen, LabRuntimeSpec runtime) implements CustomPacketPayload {
    public static final Type<C2SCreateLab> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LabKeys.MOD_ID, "create_lab"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SCreateLab> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, C2SCreateLab::confirmToken,
            LabGenSpec.STREAM_CODEC, C2SCreateLab::gen,
            LabRuntimeSpec.STREAM_CODEC, C2SCreateLab::runtime,
            C2SCreateLab::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
