package com.inferno.labdimension.integration;

import com.inferno.labdimension.LabConfig;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.CommandEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Gates WorldEdit commands to a player's own lab (or a lab they're invited
 * into). No compile-time dependency on WorldEdit: presence is checked via
 * ModList, and command roots are discovered by walking the live command
 * dispatcher for literals starting with "/" (WorldEdit's Forge/NeoForge port
 * registers "//set" etc. as a root literal named "/set") or "worldedit:".
 *
 * Purely additive: this can only ever deny a command, never grant one that
 * WorldEdit's own permission system wouldn't already allow.
 */
public final class WorldEditGate {
    private WorldEditGate() {}

    private static boolean active = false;
    private static final Set<String> ROOTS = new HashSet<>();
    private static Predicate<ServerPlayer> mayBuildHere = p -> false;

    public static void init() {
        active = ModList.get().isLoaded(ModIds.WORLDEDIT);
    }

    public static boolean isActive() {
        return active;
    }

    public static void setMayBuildHere(Predicate<ServerPlayer> predicate) {
        mayBuildHere = predicate;
    }

    public static void discoverRoots(MinecraftServer server) {
        ROOTS.clear();
        if (!active) return;
        for (CommandNode<CommandSourceStack> node : server.getCommands().getDispatcher().getRoot().getChildren()) {
            String name = node.getName();
            if (name.startsWith("/") || name.startsWith("worldedit:")) {
                ROOTS.add(name);
            }
        }
    }

    public static void onCommand(CommandEvent event) {
        if (!active || !LabConfig.GATE_WORLDEDIT.get() || ROOTS.isEmpty()) return;

        var context = event.getParseResults().getContext();
        if (context.getNodes().isEmpty()) return;

        String root = context.getNodes().get(0).getNode().getName();
        if (!ROOTS.contains(root)) return;

        CommandSourceStack source = event.getParseResults().getContext().getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) return;

        if (mayBuildHere.test(player)) return;

        event.setCanceled(true);
        boolean inAnyLab = com.inferno.labdimension.LabKeys.parseLabLevel(player.level()) != null;
        String message = inAnyLab
                ? "You don't have WorldEdit permission in this lab."
                : "WorldEdit is only available inside a lab.";
        source.sendFailure(Component.literal(message));
    }
}
