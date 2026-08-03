package com.inferno.labdimension.command;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Clickable-chat confirmation for destructive /lab actions (create-replace,
 * delete). Chosen over a GUI-only confirm because the destructive path must
 * still work for a client without the mod (the payload registrar is optional,
 * so that's a supported state) and from console for the admin variants. The
 * GUI's own "are you sure" overlay carries this same token in its payload
 * rather than duplicating the gate.
 */
public final class LabConfirm {
    private LabConfirm() {}

    private static final long TTL_MS = 30_000;

    private record Pending(String token, long expiresAtMillis, Runnable onConfirm, String description) {}

    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    /** Registers a pending destructive action, sends the player a clickable confirm
     *  line, and returns the token -- so the caller can also embed it in a
     *  simultaneously-sent GUI payload. Either route (chat /lab confirm, or a GUI
     *  submission that independently validates via consume()) can resolve it; the
     *  token is single-use either way. */
    public static String request(ServerPlayer player, String description, Runnable onConfirm) {
        String token = Long.toHexString(ThreadLocalRandom.current().nextLong());
        PENDING.put(player.getUUID(), new Pending(token, System.currentTimeMillis() + TTL_MS, onConfirm, description));

        Component confirmLink = Component.literal("[Confirm]")
                .withStyle(s -> s.withColor(ChatFormatting.RED).withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/lab confirm " + token))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.literal("This cannot be undone. Expires in 30s."))));

        player.sendSystemMessage(Component.literal(description + " ").withStyle(ChatFormatting.YELLOW).append(confirmLink));
        return token;
    }

    /** Validates and consumes a pending token WITHOUT running its chat-path Runnable
     *  (if any) -- for the GUI path, where the network handler performs its own
     *  action using the submitted payload instead of the original request()'s
     *  closure. Returns false (and leaves nothing consumed) on any mismatch. */
    public static boolean consume(UUID playerId, String token) {
        Pending p = PENDING.get(playerId);
        if (p == null || System.currentTimeMillis() > p.expiresAtMillis() || !p.token().equals(token)) {
            return false;
        }
        PENDING.remove(playerId);
        return true;
    }

    public static int confirm(ServerPlayer player, String token) {
        Pending pending = PENDING.get(player.getUUID());
        if (pending == null) {
            player.sendSystemMessage(Component.literal("Nothing to confirm.").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (System.currentTimeMillis() > pending.expiresAtMillis()) {
            PENDING.remove(player.getUUID());
            player.sendSystemMessage(Component.literal("That confirmation expired.").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (!pending.token().equals(token)) {
            player.sendSystemMessage(Component.literal("That confirmation token doesn't match.").withStyle(ChatFormatting.RED));
            return 0;
        }
        PENDING.remove(player.getUUID());
        pending.onConfirm().run();
        return 1;
    }

    public static void clear(UUID playerId) {
        PENDING.remove(playerId);
    }
}
