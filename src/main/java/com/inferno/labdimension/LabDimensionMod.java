package com.inferno.labdimension;

import com.inferno.labdimension.integration.WorldEditGate;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(LabKeys.MOD_ID)
public final class LabDimensionMod {
    public static final Logger LOGGER = LoggerFactory.getLogger("LabDimension");

    public LabDimensionMod(ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, LabConfig.SPEC);
    }

    @EventBusSubscriber(modid = LabKeys.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
    public static final class ModBusEvents {
        private ModBusEvents() {}

        @SubscribeEvent
        public static void onCommonSetup(FMLCommonSetupEvent event) {
            WorldEditGate.init();
        }
    }
}
