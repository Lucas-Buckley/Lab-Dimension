package com.inferno.labdimension.event;

import com.inferno.labdimension.LabConfig;
import com.inferno.labdimension.LabKeys;
import com.inferno.labdimension.dimension.LabAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Blocks guests below LabAccess.Access.BUILD from breaking/placing/using blocks
 * in a lab. Only player-facing -- machine-driven changes (pistons, Create,
 * WorldEdit itself) don't fire these events at all, so this is not airtight, and
 * it's the highest-risk addition in the mod on a 150+ mod pack: BreakEvent and
 * RightClickBlock are among the most contended events in the ecosystem. Kept
 * behind LabConfig.PROTECT_BLOCKS as a one-line kill switch.
 */
@EventBusSubscriber(modid = LabKeys.MOD_ID)
public final class LabProtectionEvents {
    private LabProtectionEvents() {}

    private static final Map<UUID, Long> LAST_DENY_MESSAGE = new HashMap<>();
    private static final long DENY_MESSAGE_COOLDOWN_TICKS = 40;

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!LabConfig.PROTECT_BLOCKS.get()) return;
        if (!(event.getLevel() instanceof ServerLevel sl)) return;
        if (!sl.dimension().location().getNamespace().equals(LabKeys.MOD_ID)) return;
        if (!(event.getPlayer() instanceof ServerPlayer p) || p instanceof FakePlayer) return;

        if (LabAccess.accessIn(p, sl).atLeast(LabAccess.Access.BUILD)) return;
        event.setCanceled(true);
        notify(p);
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!LabConfig.PROTECT_BLOCKS.get()) return;
        if (!(event.getLevel() instanceof ServerLevel sl)) return;
        if (!sl.dimension().location().getNamespace().equals(LabKeys.MOD_ID)) return;
        Entity entity = event.getEntity();
        if (!(entity instanceof ServerPlayer p) || p instanceof FakePlayer) return;

        if (LabAccess.accessIn(p, sl).atLeast(LabAccess.Access.BUILD)) return;
        event.setCanceled(true);
        notify(p);
    }

    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (!LabConfig.PROTECT_BLOCKS.get()) return;
        if (!(event.getLevel() instanceof ServerLevel sl)) return;
        if (!sl.dimension().location().getNamespace().equals(LabKeys.MOD_ID)) return;
        if (!(event.getEntity() instanceof ServerPlayer p) || p instanceof FakePlayer) return;

        if (LabAccess.accessIn(p, sl).atLeast(LabAccess.Access.BUILD)) return;

        // Deny block-use only (chests, doors, buttons), not the whole interaction --
        // a full cancel also kills item use, breaking eating, ender pearls, and the
        // WorldEdit wand (which WorldEdit itself handles via right-click).
        event.setUseBlock(TriState.FALSE);
        notify(p);
    }

    private static void notify(ServerPlayer player) {
        long now = player.level().getGameTime();
        Long last = LAST_DENY_MESSAGE.get(player.getUUID());
        if (last != null && now - last < DENY_MESSAGE_COOLDOWN_TICKS) return;
        LAST_DENY_MESSAGE.put(player.getUUID(), now);
        player.sendSystemMessage(Component.literal("You don't have build permission in this lab."));
    }
}
