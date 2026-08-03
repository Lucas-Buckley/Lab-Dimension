package com.inferno.labdimension.command;

import com.inferno.labdimension.LabDimensionMod;
import com.inferno.labdimension.LabKeys;
import com.inferno.labdimension.dimension.LabAccess;
import com.inferno.labdimension.dimension.LabLifecycle;
import com.inferno.labdimension.dimension.LabProvider;
import com.inferno.labdimension.dimension.LabRosterData;
import com.inferno.labdimension.dimension.LabSettings;
import com.inferno.labdimension.integration.WorldEditGate;
import com.inferno.labdimension.player.StashManager;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class LabCommand {
    private LabCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("lab")
                .requires(src -> src.getEntity() instanceof ServerPlayer)
                .executes(LabCommand::toggleOrCreate)

                .then(Commands.literal("create")
                        .executes(LabCommand::create))
                .then(Commands.literal("delete")
                        .executes(LabCommand::delete))
                .then(Commands.literal("confirm")
                        .then(Commands.argument("token", StringArgumentType.word())
                                .executes(ctx -> LabConfirm.confirm(ctx.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(ctx, "token")))))

                .then(Commands.literal("settings")
                        .executes(LabCommand::settingsSelf)
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .requires(src -> src.hasPermission(2))
                                .executes(ctx -> settingsOther(ctx.getSource(), GameProfileArgument.getGameProfiles(ctx, "player")))))

                .then(Commands.literal("visit")
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .executes(ctx -> visit(ctx.getSource(), GameProfileArgument.getGameProfiles(ctx, "player")))))

                .then(Commands.literal("spawn")
                        .executes(LabCommand::spawn)
                        .then(Commands.literal("set")
                                .executes(LabCommand::spawnSet)))

                .then(Commands.literal("list")
                        .executes(LabCommand::list))

                .then(Commands.literal("kick")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> kick(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))

                .then(Commands.literal("invite")
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .executes(ctx -> invite(ctx.getSource(), GameProfileArgument.getGameProfiles(ctx, "player")))))
                .then(Commands.literal("uninvite")
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .executes(ctx -> uninvite(ctx.getSource(), GameProfileArgument.getGameProfiles(ctx, "player")))))

                .then(Commands.literal("whitelist")
                        .then(Commands.literal("add")
                                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                        .executes(ctx -> invite(ctx.getSource(), GameProfileArgument.getGameProfiles(ctx, "player")))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                        .executes(ctx -> uninvite(ctx.getSource(), GameProfileArgument.getGameProfiles(ctx, "player")))))
                        .then(Commands.literal("list")
                                .executes(LabCommand::listInvites)))

                .then(Commands.literal("blacklist")
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .executes(ctx -> blacklist(ctx.getSource(), GameProfileArgument.getGameProfiles(ctx, "player"))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                        .executes(ctx -> unblacklist(ctx.getSource(), GameProfileArgument.getGameProfiles(ctx, "player"))))))
                .then(Commands.literal("unban")
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .executes(ctx -> unblacklist(ctx.getSource(), GameProfileArgument.getGameProfiles(ctx, "player")))))

                .then(Commands.literal("set")
                        .then(Commands.literal("visibility")
                                .then(Commands.argument("value", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(new String[]{"public", "private"}, b))
                                        .executes(ctx -> setVisibility(ctx.getSource(), StringArgumentType.getString(ctx, "value")))))
                        .then(Commands.literal("guests")
                                .then(Commands.argument("value", StringArgumentType.word())
                                        .suggests(LabCommand::suggestGuestPermissions)
                                        .executes(ctx -> setGuests(ctx.getSource(), StringArgumentType.getString(ctx, "value")))))
                        .then(Commands.literal("name")
                                .then(Commands.argument("value", StringArgumentType.greedyString())
                                        .executes(ctx -> setName(ctx.getSource(), StringArgumentType.getString(ctx, "value"))))))

                .then(Commands.literal("help")
                        .executes(LabCommand::help))

                .then(Commands.literal("admin")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("forget")
                                .then(Commands.argument("targets", GameProfileArgument.gameProfile())
                                        .executes(ctx -> adminForget(ctx.getSource(), GameProfileArgument.getGameProfiles(ctx, "targets")))))
                        .then(Commands.literal("delete")
                                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                        .executes(ctx -> adminDelete(ctx.getSource(), GameProfileArgument.getGameProfiles(ctx, "player")))))
                        .then(Commands.literal("list")
                                .executes(LabCommand::adminList))
                        .then(Commands.literal("evict")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> adminEvict(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"))))))
                .then(Commands.literal("debug")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("attachments")
                                .executes(LabCommand::debugAttachments))
                        .then(Commands.literal("hash")
                                .executes(LabCommand::debugHash))
                        .then(Commands.literal("access")
                                .executes(LabCommand::debugAccess))
                        .then(Commands.literal("surface")
                                .executes(LabCommand::debugSurface)))
        );
    }

    // ------------------------------------------------------------------
    // toggle / create / delete
    // ------------------------------------------------------------------

    private static int toggle(ServerPlayer player, UUID labOwner) {
        StashManager.toggle(player, labOwner);
        return 1;
    }

    /** Bare /lab: if the player has no lab yet and their client has the GUI, open
     *  the creation screen instead of silently auto-creating defaults. Otherwise
     *  (no lab + no GUI, or lab already exists) behaves exactly like toggle(). */
    private static int toggleOrCreate(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        LabRosterData roster = LabRosterData.get(player.server.overworld());
        if (roster.peek(player.getUUID()) == null) {
            if (com.inferno.labdimension.network.ServerPayloadHandlers.trySendOpenCreation(player, false, "")) {
                return 1;
            }
        }
        return toggle(player, player.getUUID());
    }

    private static int create(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        LabRosterData roster = LabRosterData.get(player.server.overworld());
        LabSettings existing = roster.peek(player.getUUID());

        if (existing == null) {
            if (com.inferno.labdimension.network.ServerPayloadHandlers.trySendOpenCreation(player, false, "")) {
                return 1;
            }
            LabSettings created = roster.entryFor(player.getUUID());
            created.displayName = player.getGameProfile().getName() + "'s Lab";
            roster.setDirty();
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Lab created. There's no creation GUI on your client, so you got the defaults -- "
                            + "use /lab set and /lab whitelist to configure it.").withStyle(ChatFormatting.GREEN), false);
            return 1;
        }

        // Do NOT open the creation GUI here -- it must not appear until the player
        // actually confirms (clicking the chat [Confirm] link), otherwise the GUI
        // pops up immediately and looks like the confirmation was skipped.
        LabConfirm.request(player,
                "Creating a new lab will permanently delete your current one, including anything built in it.", () -> {
                    LabSettings replacement = existing.copy();
                    LabLifecycle.replace(player.server, player.getUUID(), replacement).thenAccept(result ->
                            reportLifecycleResult(player, result, "created"));
                });
        return 1;
    }

    private static int delete(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        LabRosterData roster = LabRosterData.get(player.server.overworld());
        if (roster.peek(player.getUUID()) == null) {
            ctx.getSource().sendFailure(Component.literal("You don't have a lab.").withStyle(ChatFormatting.RED));
            return 0;
        }
        LabConfirm.request(player, "This will permanently delete your lab, including anything built in it.", () ->
                LabLifecycle.delete(player.server, player.getUUID()).thenAccept(result ->
                        reportLifecycleResult(player, result, "deleted")));
        return 1;
    }

    private static void reportLifecycleResult(ServerPlayer player, LabLifecycle.Result result, String verb) {
        Component msg = switch (result) {
            case OK -> Component.literal("Your lab was " + verb + ".").withStyle(ChatFormatting.GREEN);
            case IN_FLIGHT -> Component.literal("A lab toggle is already in progress; try again in a moment.").withStyle(ChatFormatting.RED);
            case REGEN_LIMIT -> Component.literal("You've hit the lab regeneration limit for this server session; restart the server to regenerate again.").withStyle(ChatFormatting.RED);
            case STILL_OCCUPIED -> Component.literal("Couldn't clear everyone out of the lab; try again.").withStyle(ChatFormatting.RED);
            case NO_LAB -> Component.literal("You don't have a lab.").withStyle(ChatFormatting.RED);
        };
        player.sendSystemMessage(msg);
    }

    // ------------------------------------------------------------------
    // settings (chat fallback -- GUI lands in a later phase)
    // ------------------------------------------------------------------

    private static int settingsSelf(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        return printSettings(ctx.getSource(), player, player.getUUID(), "your");
    }

    private static int settingsOther(CommandSourceStack source, Collection<GameProfile> profiles) throws CommandSyntaxException {
        if (profiles.isEmpty()) return 0;
        GameProfile target = profiles.iterator().next();
        return printSettings(source, source.getPlayerOrException(), target.getId(), target.getName() + "'s");
    }

    private static int printSettings(CommandSourceStack source, ServerPlayer requester, UUID owner, String possessive) {
        LabRosterData roster = LabRosterData.get(source.getServer().overworld());
        LabSettings s = roster.peek(owner);
        if (s == null) {
            source.sendFailure(Component.literal("No lab found.").withStyle(ChatFormatting.RED));
            return 0;
        }

        if (com.inferno.labdimension.network.ServerPayloadHandlers.trySendOpenSettings(requester, owner)) {
            return 1;
        }

        source.sendSuccess(() -> Component.literal(
                "There's no settings GUI on your client -- editing " + possessive + " lab via chat:\n"
                        + "  worldType=" + s.worldType + " visibility=" + s.visibility
                        + " guests=" + s.guestPermission + " name=\"" + s.displayName + "\"\n"
                        + "  whitelist=" + s.whitelist.size() + " blacklist=" + s.blacklist.size()
                        + " generation=" + s.generation
                        + "\nUse /lab set, /lab whitelist, /lab blacklist."), false);
        return 1;
    }

    // ------------------------------------------------------------------
    // visit / spawn / list / kick
    // ------------------------------------------------------------------

    private static int visit(CommandSourceStack source, Collection<GameProfile> profiles) throws CommandSyntaxException {
        ServerPlayer visitor = source.getPlayerOrException();
        if (profiles.isEmpty()) return 0;
        GameProfile target = profiles.iterator().next();
        UUID ownerId = target.getId();

        if (ownerId.equals(visitor.getUUID())) {
            StashManager.toggle(visitor, ownerId);
            return 1;
        }

        LabRosterData roster = LabRosterData.get(visitor.serverLevel().getServer().overworld());
        LabSettings targetSettings = roster.peek(ownerId);
        LabAccess.Access access = LabAccess.accessFor(visitor, ownerId, targetSettings);
        if (!access.atLeast(LabAccess.Access.VISIT)) {
            source.sendFailure(Component.literal(targetSettings == null
                    ? "That player doesn't have a lab."
                    : "You haven't been invited to that lab.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        StashManager.toggle(visitor, ownerId);
        return 1;
    }

    private static int spawn(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        LabKeys.LabRef ref = LabKeys.parseLabLevel(player.level());
        if (ref == null) {
            ctx.getSource().sendFailure(Component.literal("You're not in a lab.").withStyle(ChatFormatting.RED));
            return 0;
        }
        LabRosterData roster = LabRosterData.get(player.server.overworld());
        LabSettings s = roster.peek(ref.owner());
        if (s == null) {
            ctx.getSource().sendFailure(Component.literal("This lab has no recorded settings.").withStyle(ChatFormatting.RED));
            return 0;
        }
        player.teleportTo(s.spawnPos.x, s.spawnPos.y, s.spawnPos.z);
        ctx.getSource().sendSuccess(() -> Component.literal("Teleported to the lab's spawn point."), false);
        return 1;
    }

    private static int spawnSet(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        LabKeys.LabRef ref = LabKeys.parseLabLevel(player.level());
        if (ref == null) {
            ctx.getSource().sendFailure(Component.literal("You're not in a lab.").withStyle(ChatFormatting.RED));
            return 0;
        }
        LabAccess.Access access = LabAccess.accessIn(player, player.level());
        if (!(ref.owner().equals(player.getUUID()) || ctx.getSource().hasPermission(2))) {
            ctx.getSource().sendFailure(Component.literal("Only the owner can set the lab's spawn point.").withStyle(ChatFormatting.RED));
            return 0;
        }
        LabRosterData roster = LabRosterData.get(player.server.overworld());
        LabSettings s = roster.peek(ref.owner());
        if (s == null) return 0;
        s.spawnPos = player.position();
        s.spawnYaw = player.getYRot();
        s.spawnPitch = player.getXRot();
        roster.setDirty();
        ctx.getSource().sendSuccess(() -> Component.literal("Lab spawn point updated.").withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        LabRosterData roster = LabRosterData.get(ctx.getSource().getServer().overworld());
        var publicOwners = roster.publicOwners();
        if (publicOwners.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("No public labs right now."), false);
            return 0;
        }
        var cache = ctx.getSource().getServer().getProfileCache();
        for (UUID owner : publicOwners) {
            String name = cache != null ? cache.get(owner).map(GameProfile::getName).orElse(owner.toString()) : owner.toString();
            LabSettings s = roster.peek(owner);
            String label = (s != null && !s.displayName.isEmpty()) ? s.displayName : (name + "'s lab");
            Component visitLink = Component.literal("[Visit]")
                    .withStyle(st -> st.withColor(ChatFormatting.AQUA)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/lab visit " + name))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to visit"))));
            ctx.getSource().sendSuccess(() -> Component.literal(label + " ").append(visitLink), false);
        }
        return publicOwners.size();
    }

    private static int kick(CommandSourceStack source, ServerPlayer target) throws CommandSyntaxException {
        ServerPlayer requester = source.getPlayerOrException();
        LabKeys.LabRef ref = LabKeys.parseLabLevel(target.level());
        if (ref == null) {
            source.sendFailure(Component.literal(target.getGameProfile().getName() + " is not in a lab.").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (!(ref.owner().equals(requester.getUUID()) || source.hasPermission(2))) {
            source.sendFailure(Component.literal("Only the lab owner can kick players.").withStyle(ChatFormatting.RED));
            return 0;
        }
        StashManager.toggle(target, ref.owner());
        source.sendSuccess(() -> Component.literal("Kicked " + target.getGameProfile().getName() + " from the lab."), true);
        return 1;
    }

    // ------------------------------------------------------------------
    // whitelist / invite
    // ------------------------------------------------------------------

    private static int invite(CommandSourceStack source, Collection<GameProfile> profiles) throws CommandSyntaxException {
        ServerPlayer owner = source.getPlayerOrException();
        LabRosterData roster = LabRosterData.get(owner.serverLevel().getServer().overworld());
        LabSettings entry = roster.entryFor(owner.getUUID());
        int whitelisted = 0;
        for (GameProfile p : profiles) {
            if (entry.visibility == LabSettings.Visibility.PRIVATE) {
                entry.whitelist.add(p.getId());
                whitelisted++;
            }
            sendInviteLink(source.getServer(), owner, p);
        }
        roster.setDirty();
        final int w = whitelisted;
        source.sendSuccess(() -> Component.literal("Invited " + profiles.size() + " player(s)"
                + (w > 0 ? " and whitelisted them (your lab is private)." : ".")).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static void sendInviteLink(net.minecraft.server.MinecraftServer server, ServerPlayer owner, GameProfile target) {
        ServerPlayer online = server.getPlayerList().getPlayer(target.getId());
        if (online == null) {
            owner.sendSystemMessage(Component.literal(
                    target.getName() + " is offline; they'll see the invite next time they can visit.").withStyle(ChatFormatting.GRAY));
            return;
        }
        String ownerName = owner.getGameProfile().getName();
        Component link = Component.literal("[Visit " + ownerName + "'s lab]")
                .withStyle(s -> s.withColor(ChatFormatting.AQUA)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/lab visit " + ownerName))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to teleport"))));
        online.sendSystemMessage(Component.literal(ownerName + " invited you to their lab. ").append(link));
    }

    private static int uninvite(CommandSourceStack source, Collection<GameProfile> profiles) throws CommandSyntaxException {
        ServerPlayer owner = source.getPlayerOrException();
        LabRosterData roster = LabRosterData.get(owner.serverLevel().getServer().overworld());
        LabSettings entry = roster.entryFor(owner.getUUID());
        for (GameProfile p : profiles) {
            entry.whitelist.remove(p.getId());
        }
        roster.setDirty();
        source.sendSuccess(() -> Component.literal(
                "Removed from the whitelist. This does not eject anyone already inside; use /lab kick.")
                .withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    private static int listInvites(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer owner = ctx.getSource().getPlayerOrException();
        LabRosterData roster = LabRosterData.get(owner.serverLevel().getServer().overworld());
        LabSettings entry = roster.entryFor(owner.getUUID());
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Whitelisted: " + entry.whitelist.size() + " player(s)."), false);
        return 1;
    }

    private static int blacklist(CommandSourceStack source, Collection<GameProfile> profiles) throws CommandSyntaxException {
        ServerPlayer owner = source.getPlayerOrException();
        LabRosterData roster = LabRosterData.get(owner.serverLevel().getServer().overworld());
        LabSettings entry = roster.entryFor(owner.getUUID());
        for (GameProfile p : profiles) {
            entry.blacklist.add(p.getId());
            entry.whitelist.remove(p.getId());
            ServerPlayer target = source.getServer().getPlayerList().getPlayer(p.getId());
            if (target != null) {
                LabKeys.LabRef ref = LabKeys.parseLabLevel(target.level());
                if (ref != null && ref.owner().equals(owner.getUUID())) {
                    StashManager.toggle(target, owner.getUUID());
                }
            }
        }
        roster.setDirty();
        source.sendSuccess(() -> Component.literal("Blacklisted " + profiles.size() + " player(s).").withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    private static int unblacklist(CommandSourceStack source, Collection<GameProfile> profiles) throws CommandSyntaxException {
        ServerPlayer owner = source.getPlayerOrException();
        LabRosterData roster = LabRosterData.get(owner.serverLevel().getServer().overworld());
        LabSettings entry = roster.entryFor(owner.getUUID());
        for (GameProfile p : profiles) {
            entry.blacklist.remove(p.getId());
        }
        roster.setDirty();
        source.sendSuccess(() -> Component.literal("Unbanned " + profiles.size() + " player(s).").withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    // ------------------------------------------------------------------
    // set visibility / guests / name
    // ------------------------------------------------------------------

    private static int setVisibility(CommandSourceStack source, String value) throws CommandSyntaxException {
        ServerPlayer owner = source.getPlayerOrException();
        LabRosterData roster = LabRosterData.get(owner.serverLevel().getServer().overworld());
        LabSettings entry = roster.entryFor(owner.getUUID());
        LabSettings.Visibility parsed = switch (value.toLowerCase(Locale.ROOT)) {
            case "public" -> LabSettings.Visibility.PUBLIC;
            case "private" -> LabSettings.Visibility.PRIVATE;
            default -> null;
        };
        if (parsed == null) {
            source.sendFailure(Component.literal("Visibility must be 'public' or 'private'.").withStyle(ChatFormatting.RED));
            return 0;
        }
        entry.visibility = parsed;
        roster.setDirty();
        source.sendSuccess(() -> Component.literal("Lab visibility set to " + value + ".").withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static CompletableFuture<Suggestions> suggestGuestPermissions(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        List<String> options = WorldEditGate.isActive()
                ? List.of("none", "build", "worldedit")
                : List.of("none", "build");
        return SharedSuggestionProvider.suggest(options, builder);
    }

    private static int setGuests(CommandSourceStack source, String value) throws CommandSyntaxException {
        ServerPlayer owner = source.getPlayerOrException();
        String v = value.toLowerCase(Locale.ROOT);
        if (v.equals("worldedit") && !WorldEditGate.isActive()) {
            source.sendFailure(Component.literal("WorldEdit is not installed on this server.").withStyle(ChatFormatting.RED));
            return 0;
        }
        LabSettings.GuestPermission parsed = switch (v) {
            case "none" -> LabSettings.GuestPermission.NONE;
            case "build" -> LabSettings.GuestPermission.BUILD;
            case "worldedit" -> LabSettings.GuestPermission.WORLDEDIT;
            default -> null;
        };
        if (parsed == null) {
            source.sendFailure(Component.literal("Guest permission must be 'none', 'build'"
                    + (WorldEditGate.isActive() ? ", or 'worldedit'." : ".")).withStyle(ChatFormatting.RED));
            return 0;
        }
        LabRosterData roster = LabRosterData.get(owner.serverLevel().getServer().overworld());
        LabSettings entry = roster.entryFor(owner.getUUID());
        entry.guestPermission = parsed;
        roster.setDirty();
        source.sendSuccess(() -> Component.literal("Guest permission set to " + v + ".").withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int setName(CommandSourceStack source, String value) throws CommandSyntaxException {
        ServerPlayer owner = source.getPlayerOrException();
        String stripped = ChatFormatting.stripFormatting(value) != null ? ChatFormatting.stripFormatting(value) : value;
        stripped = stripped.replaceAll("[\\r\\n]", "").trim();
        if (stripped.length() > 32) {
            stripped = stripped.substring(0, 32);
        }
        LabRosterData roster = LabRosterData.get(owner.serverLevel().getServer().overworld());
        LabSettings entry = roster.entryFor(owner.getUUID());
        entry.displayName = stripped;
        roster.setDirty();
        final String finalName = stripped;
        source.sendSuccess(() -> Component.literal("Lab name set to \"" + finalName + "\".").withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    // ------------------------------------------------------------------
    // help
    // ------------------------------------------------------------------

    private static int help(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        boolean op = source.hasPermission(2);
        boolean we = WorldEditGate.isActive();

        List<String> lines = new java.util.ArrayList<>(List.of(
                "/lab - toggle between your current location and your lab",
                "/lab create - create your first lab, or replace your existing one",
                "/lab delete - permanently delete your lab",
                "/lab settings - view your lab's settings",
                "/lab visit <player> - visit a player's lab (if allowed)",
                "/lab spawn - teleport to your lab's spawn point",
                "/lab spawn set - set the lab's spawn point to where you're standing",
                "/lab list - list public labs",
                "/lab kick <player> - eject a player from your lab",
                "/lab invite <player> - whitelist (if private) and notify a player",
                "/lab whitelist add|remove|list <player> - manage your whitelist",
                "/lab blacklist <player> - ban a player from your lab",
                "/lab unban <player> - remove a ban",
                "/lab set visibility <public|private>",
                "/lab set guests <none|build" + (we ? "|worldedit" : "") + ">",
                "/lab set name <text>"
        ));
        if (op) {
            lines.add("/lab admin forget <player> - stop recreating a lab (keeps files)");
            lines.add("/lab admin delete <player> - permanently delete a player's lab");
            lines.add("/lab admin list - count known labs");
            lines.add("/lab admin evict <player> - force-return a player from any lab");
        }
        for (String line : lines) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return lines.size();
    }

    // ------------------------------------------------------------------
    // admin
    // ------------------------------------------------------------------

    private static int adminForget(CommandSourceStack source, Collection<GameProfile> profiles) {
        LabRosterData roster = LabRosterData.get(source.getServer().overworld());
        int count = 0;
        for (GameProfile p : profiles) {
            UUID id = p.getId();
            boolean occupied = source.getServer().getPlayerList().getPlayers().stream()
                    .anyMatch(sp -> sp.level().dimension().equals(LabProvider.keyFor(id)));
            if (occupied) {
                source.sendFailure(Component.literal("Someone is currently inside " + p.getName() + "'s lab; skipped."));
                continue;
            }
            if (roster.has(id)) {
                roster.forget(id);
                count++;
            }
        }
        final int forgotten = count;
        source.sendSuccess(() -> Component.literal(
                "Forgot " + forgotten + " lab roster entr(y/ies). The dimension data is not deleted; "
                        + "recreation stops after the next server restart."), true);
        return count;
    }

    private static int adminDelete(CommandSourceStack source, Collection<GameProfile> profiles) {
        if (profiles.isEmpty()) return 0;
        GameProfile target = profiles.iterator().next();
        LabLifecycle.delete(source.getServer(), target.getId()).thenAccept(result -> {
            Component msg = switch (result) {
                case OK -> Component.literal("Deleted " + target.getName() + "'s lab.").withStyle(ChatFormatting.GREEN);
                case NO_LAB -> Component.literal(target.getName() + " doesn't have a lab.").withStyle(ChatFormatting.RED);
                case IN_FLIGHT -> Component.literal("A lab toggle is in progress; try again shortly.").withStyle(ChatFormatting.RED);
                case STILL_OCCUPIED -> Component.literal("Couldn't clear everyone out; try again.").withStyle(ChatFormatting.RED);
                case REGEN_LIMIT -> Component.literal("Unexpected regen-limit result for a delete.").withStyle(ChatFormatting.RED);
            };
            source.sendSuccess(() -> msg, true);
        });
        return 1;
    }

    private static int adminList(CommandContext<CommandSourceStack> ctx) {
        LabRosterData roster = LabRosterData.get(ctx.getSource().getServer().overworld());
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Known labs: " + roster.allOwners().size()), false);
        return roster.allOwners().size();
    }

    private static int adminEvict(CommandSourceStack source, ServerPlayer target) {
        CompoundTag persisted = target.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        CompoundTag state = persisted.getCompound(LabKeys.MOD_ID + ":state");
        if (!state.getBoolean("inLab")) {
            source.sendFailure(Component.literal(target.getGameProfile().getName() + " is not in a lab."));
            return 0;
        }
        UUID owner = state.contains("currentLabOwner")
                ? UUID.fromString(state.getString("currentLabOwner"))
                : target.getUUID();
        StashManager.toggle(target, owner);
        source.sendSuccess(() -> Component.literal("Evicted " + target.getGameProfile().getName() + " from their lab."), true);
        return 1;
    }

    // ------------------------------------------------------------------
    // debug
    // ------------------------------------------------------------------

    private static int debugAttachments(CommandContext<CommandSourceStack> ctx) {
        StringBuilder sb = new StringBuilder("Attachment types: ");
        for (ResourceLocation id : NeoForgeRegistries.ATTACHMENT_TYPES.keySet()) {
            sb.append(id).append(", ");
        }
        String out = sb.toString();
        LabDimensionMod.LOGGER.info(out);
        int count = NeoForgeRegistries.ATTACHMENT_TYPES.keySet().size();
        ctx.getSource().sendSuccess(() -> Component.literal("Dumped " + count
                + " attachment type keys to the server log."), false);
        return 1;
    }

    private static int debugHash(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        int hash = player.getInventory().save(new net.minecraft.nbt.ListTag()).toString().hashCode();
        ctx.getSource().sendSuccess(() -> Component.literal("Inventory hash: " + hash), false);
        return hash;
    }

    private static int debugAccess(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        LabAccess.Access access = LabAccess.accessHere(player);
        ctx.getSource().sendSuccess(() -> Component.literal("Access here: " + access), false);
        return 1;
    }

    private static int debugSurface(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        LabKeys.LabRef ref = LabKeys.parseLabLevel(player.level());
        if (ref == null) {
            ctx.getSource().sendFailure(Component.literal("Not in a lab."));
            return 0;
        }
        LabRosterData roster = LabRosterData.get(player.server.overworld());
        LabSettings s = roster.peek(ref.owner());
        int minY = player.level().dimensionType().minY();
        int surface = s != null ? s.surfaceY(minY) : minY;
        ctx.getSource().sendSuccess(() -> Component.literal("min_y=" + minY + " surfaceY=" + surface), false);
        return 1;
    }
}
