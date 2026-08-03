package com.inferno.labdimension.network;

import com.inferno.labdimension.LabKeys;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = LabKeys.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class LabPayloads {
    private LabPayloads() {}

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        // optional() returns a COPY of the registrar -- must be chained, not called
        // and discarded, or clients without this mod get kicked at login instead of
        // silently lacking the channel.
        PayloadRegistrar registrar = event.registrar("1").optional();

        registrar.playToClient(S2COpenLabSettings.TYPE, S2COpenLabSettings.STREAM_CODEC,
                LabPayloads::handleOpenSettingsOnClient);
        registrar.playToServer(C2SUpdateLabSettings.TYPE, C2SUpdateLabSettings.STREAM_CODEC,
                ServerPayloadHandlers::updateSettings);

        registrar.playToClient(S2COpenLabCreation.TYPE, S2COpenLabCreation.STREAM_CODEC,
                LabPayloads::handleOpenCreationOnClient);
        registrar.playToServer(C2SCreateLab.TYPE, C2SCreateLab.STREAM_CODEC,
                ServerPayloadHandlers::createLab);
    }

    // Each kept as its own tiny method (not a lambda body inline above) so the
    // verifier only resolves the Dist.CLIENT-only ClientOnly class when the method
    // actually runs -- which never happens on a dedicated server.
    private static void handleOpenSettingsOnClient(S2COpenLabSettings payload, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.inferno.labdimension.client.ClientOnly.openSettings(payload);
        }
    }

    private static void handleOpenCreationOnClient(S2COpenLabCreation payload, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.inferno.labdimension.client.ClientOnly.openCreation(payload);
        }
    }
}
