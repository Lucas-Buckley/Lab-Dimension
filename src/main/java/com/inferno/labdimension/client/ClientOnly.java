package com.inferno.labdimension.client;

import com.inferno.labdimension.client.screen.LabCreationScreen;
import com.inferno.labdimension.client.screen.LabSettingsScreen;
import com.inferno.labdimension.network.S2COpenLabCreation;
import com.inferno.labdimension.network.S2COpenLabSettings;
import net.minecraft.client.Minecraft;

/**
 * Every method here touches net.minecraft.client.* types. This class must only
 * ever be named from inside an `if (FMLEnvironment.dist == Dist.CLIENT)` branch
 * (see LabPayloads) so a dedicated server never resolves/loads it.
 */
public final class ClientOnly {
    private ClientOnly() {}

    public static void openSettings(S2COpenLabSettings payload) {
        Minecraft.getInstance().setScreen(new LabSettingsScreen(payload));
    }

    public static void openCreation(S2COpenLabCreation payload) {
        Minecraft.getInstance().setScreen(new LabCreationScreen(payload));
    }
}
