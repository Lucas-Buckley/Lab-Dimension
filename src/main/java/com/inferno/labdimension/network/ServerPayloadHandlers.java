package com.inferno.labdimension.network;

import com.inferno.labdimension.LabKeys;
import com.inferno.labdimension.command.LabConfirm;
import com.inferno.labdimension.dimension.LabLifecycle;
import com.inferno.labdimension.dimension.LabRosterData;
import com.inferno.labdimension.dimension.LabSettings;
import com.inferno.labdimension.integration.WorldEditGate;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.world.level.dimension.DimensionType;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class ServerPayloadHandlers {
    private ServerPayloadHandlers() {}

    private static final int MAX_NAME_LIST = 64;

    /** Sends the settings screen to a player if their client has the channel,
     *  otherwise returns false so the caller can fall back to chat. */
    public static boolean trySendOpenSettings(ServerPlayer player, UUID owner) {
        if (!NetworkRegistry.hasChannel(player.connection, S2COpenLabSettings.TYPE.id())) {
            return false;
        }
        LabRosterData roster = LabRosterData.get(player.server.overworld());
        LabSettings s = roster.peek(owner);
        if (s == null) return false;

        MinecraftServer server = player.server;
        LabRuntimeSpec runtime = runtimeSpecFor(server, s);
        String ownerName = nameFor(server, owner);

        PacketDistributor.sendToPlayer(player, new S2COpenLabSettings(
                runtime, ownerName, WorldEditGate.isActive(), owner.equals(player.getUUID())));
        return true;
    }

    private static LabRuntimeSpec runtimeSpecFor(MinecraftServer server, LabSettings s) {
        return new LabRuntimeSpec(
                s.visibility,
                s.guestPermission,
                s.displayName,
                namesFor(server, s.whitelist),
                namesFor(server, s.blacklist)
        );
    }

    private static String nameFor(MinecraftServer server, UUID id) {
        return server.getProfileCache() != null
                ? server.getProfileCache().get(id).map(com.mojang.authlib.GameProfile::getName).orElse(id.toString())
                : id.toString();
    }

    private static List<String> namesFor(MinecraftServer server, Set<UUID> ids) {
        GameProfileCache cache = server.getProfileCache();
        List<String> names = new ArrayList<>();
        for (UUID id : ids) {
            names.add(cache != null ? cache.get(id).map(com.mojang.authlib.GameProfile::getName).orElse(id.toString()) : id.toString());
        }
        return names;
    }

    public static void updateSettings(C2SUpdateLabSettings payload, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer sender)) return;
        MinecraftServer server = sender.server;
        UUID owner = sender.getUUID(); // ALWAYS the sender -- never trust a payload-provided target

        if (!withinNameListLimits(sender, payload.runtime())) return;

        applyRuntimeAsync(server, payload.runtime()).thenAccept(rv -> server.execute(() -> {
            LabRosterData roster = LabRosterData.get(server.overworld());
            LabSettings s = roster.entryFor(owner);
            rv.applyTo(s);
            roster.setDirty();
            reportDropped(sender, rv.droppedNames());
            sender.sendSystemMessage(Component.literal("Lab settings saved.").withStyle(ChatFormatting.GREEN));
        }));
    }

    private static boolean withinNameListLimits(ServerPlayer sender, LabRuntimeSpec runtime) {
        if (runtime.whitelistNames().size() > MAX_NAME_LIST || runtime.blacklistNames().size() > MAX_NAME_LIST) {
            sender.sendSystemMessage(Component.literal("Too many names in one list (max " + MAX_NAME_LIST + ").")
                    .withStyle(ChatFormatting.RED));
            return false;
        }
        return true;
    }

    private static void reportDropped(ServerPlayer sender, List<String> dropped) {
        if (!dropped.isEmpty()) {
            sender.sendSystemMessage(Component.literal(
                    "Couldn't resolve these player names: " + String.join(", ", dropped))
                    .withStyle(ChatFormatting.YELLOW));
        }
    }

    // ------------------------------------------------------------------
    // shared runtime-settings validation + async name resolution
    // ------------------------------------------------------------------

    private record RuntimeValidated(LabSettings.Visibility visibility, LabSettings.GuestPermission guestPermission,
                                     String displayName, Set<UUID> whitelist, Set<UUID> blacklist,
                                     List<String> droppedNames) {
        void applyTo(LabSettings s) {
            s.visibility = visibility;
            s.guestPermission = guestPermission;
            s.displayName = displayName;
            s.whitelist.clear();
            s.whitelist.addAll(whitelist);
            s.blacklist.clear();
            s.blacklist.addAll(blacklist);
        }
    }

    /** Never blocks the main thread on a Mojang API lookup: resolves every name
     *  asynchronously via GameProfileCache.getAsync. Also clamps a WORLDEDIT guest
     *  tier to BUILD server-side if WorldEdit isn't installed, regardless of what
     *  the client sent -- the client is supposed to hide that option, but this is
     *  never trusted to have enforced it. Caller must check withinNameListLimits
     *  first; this assumes that's already been done. */
    private static CompletableFuture<RuntimeValidated> applyRuntimeAsync(MinecraftServer server, LabRuntimeSpec runtime) {
        LabSettings.GuestPermission guests = runtime.guestPermission();
        if (guests == LabSettings.GuestPermission.WORLDEDIT && !WorldEditGate.isActive()) {
            guests = LabSettings.GuestPermission.BUILD;
        }
        LabSettings.GuestPermission finalGuests = guests;
        String displayName = sanitizeName(runtime.displayName());

        return resolveNamesAsync(server, runtime.whitelistNames())
                .thenCombine(resolveNamesAsync(server, runtime.blacklistNames()), (whitelistResult, blacklistResult) -> {
                    List<String> dropped = new ArrayList<>(whitelistResult.unresolved());
                    dropped.addAll(blacklistResult.unresolved());

                    // A name can't sit on both lists -- the client is never trusted to have
                    // enforced this itself. Blacklist wins ties: a ban must never be silently
                    // undone just because the same name was also submitted as whitelisted.
                    Set<UUID> whitelist = new HashSet<>(whitelistResult.resolved());
                    whitelist.removeAll(blacklistResult.resolved());

                    return new RuntimeValidated(runtime.visibility(), finalGuests, displayName,
                            whitelist, blacklistResult.resolved(), dropped);
                });
    }

    private record NameResolution(Set<UUID> resolved, List<String> unresolved) {}

    private static CompletableFuture<NameResolution> resolveNamesAsync(MinecraftServer server, List<String> names) {
        GameProfileCache cache = server.getProfileCache();
        if (cache == null || names.isEmpty()) {
            return CompletableFuture.completedFuture(new NameResolution(Set.of(), List.copyOf(names)));
        }
        List<CompletableFuture<Optional<com.mojang.authlib.GameProfile>>> futures = new ArrayList<>();
        for (String name : names) {
            futures.add(cache.getAsync(name));
        }
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).thenApply(v -> {
            Set<UUID> resolved = new HashSet<>();
            List<String> unresolved = new ArrayList<>();
            for (int i = 0; i < futures.size(); i++) {
                Optional<com.mojang.authlib.GameProfile> profile = futures.get(i).join();
                if (profile.isPresent()) {
                    resolved.add(profile.get().getId());
                } else {
                    unresolved.add(names.get(i));
                }
            }
            return new NameResolution(resolved, unresolved);
        });
    }

    private static String sanitizeName(String raw) {
        String s = ChatFormatting.stripFormatting(raw) != null ? ChatFormatting.stripFormatting(raw) : raw;
        s = s.replaceAll("[\\r\\n]", "").trim();
        if (s.length() > 32) s = s.substring(0, 32);
        return s;
    }

    // ------------------------------------------------------------------
    // lab creation / regeneration
    // ------------------------------------------------------------------

    /** Sends the creation screen to a player if their client has the channel.
     *  If they already have a lab, this is a "replace" request: the caller must
     *  have already registered a LabConfirm token (via request()) and pass it
     *  here so the GUI can carry it back on submission. */
    public static boolean trySendOpenCreation(ServerPlayer player, boolean replacingExisting, String confirmToken) {
        if (!NetworkRegistry.hasChannel(player.connection, S2COpenLabCreation.TYPE.id())) {
            return false;
        }
        MinecraftServer server = player.server;
        LabRosterData roster = LabRosterData.get(server.overworld());
        LabSettings existing = roster.peek(player.getUUID());
        LabGenSpec defaults = existing != null ? LabGenSpec.from(existing) : LabGenSpec.from(new LabSettings());
        LabRuntimeSpec runtime = existing != null ? runtimeSpecFor(server, existing)
                : new LabRuntimeSpec(LabSettings.Visibility.PRIVATE,
                        WorldEditGate.isActive() ? LabSettings.GuestPermission.WORLDEDIT : LabSettings.GuestPermission.BUILD,
                        "", List.of(), List.of());
        int minY = labDimensionType(server).minY();

        PacketDistributor.sendToPlayer(player, new S2COpenLabCreation(
                defaults, runtime, minY, replacingExisting, confirmToken, WorldEditGate.isActive()));
        return true;
    }

    private static DimensionType labDimensionType(MinecraftServer server) {
        return server.registryAccess()
                .lookupOrThrow(Registries.DIMENSION_TYPE)
                .getOrThrow(LabKeys.LAB_DIMENSION_TYPE)
                .value();
    }

    public static void createLab(C2SCreateLab payload, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer sender)) return;
        MinecraftServer server = sender.server;
        UUID owner = sender.getUUID(); // ALWAYS the sender

        if (!withinNameListLimits(sender, payload.runtime())) return;

        Optional<LabValidated> validated = validateGenSpec(server, payload.gen());
        if (validated.isEmpty()) {
            sender.sendSystemMessage(Component.literal("Invalid lab settings; nothing changed.").withStyle(ChatFormatting.RED));
            return;
        }

        LabRosterData roster = LabRosterData.get(server.overworld());
        LabSettings existing = roster.peek(owner);

        if (existing == null) {
            applyRuntimeAsync(server, payload.runtime()).thenAccept(rv -> server.execute(() -> {
                LabSettings s = roster.entryFor(owner);
                validated.get().applyTo(s);
                rv.applyTo(s);
                if (s.displayName.isEmpty()) {
                    s.displayName = sender.getGameProfile().getName() + "'s Lab";
                }
                roster.setDirty();
                reportDropped(sender, rv.droppedNames());
                sender.sendSystemMessage(Component.literal("Lab created.").withStyle(ChatFormatting.GREEN));
            }));
            return;
        }

        if (!LabConfirm.consume(owner, payload.confirmToken())) {
            sender.sendSystemMessage(Component.literal(
                    "That confirmation expired; run /lab create again.").withStyle(ChatFormatting.RED));
            return;
        }

        applyRuntimeAsync(server, payload.runtime()).thenAccept(rv -> server.execute(() -> {
            LabSettings replacement = existing.copy();
            validated.get().applyTo(replacement);
            rv.applyTo(replacement);
            reportDropped(sender, rv.droppedNames());
            LabLifecycle.replace(server, owner, replacement).thenAccept(result -> {
                Component msg = switch (result) {
                    case OK -> Component.literal("Your lab was recreated.").withStyle(ChatFormatting.GREEN);
                    case IN_FLIGHT -> Component.literal("A lab toggle is already in progress; try again in a moment.").withStyle(ChatFormatting.RED);
                    case REGEN_LIMIT -> Component.literal("You've hit the lab regeneration limit for this server session; restart the server to regenerate again.").withStyle(ChatFormatting.RED);
                    case STILL_OCCUPIED -> Component.literal("Couldn't clear everyone out of the lab; try again.").withStyle(ChatFormatting.RED);
                    case NO_LAB -> Component.literal("You don't have a lab.").withStyle(ChatFormatting.RED);
                };
                sender.sendSystemMessage(msg);
            });
        }));
    }

    private record LabValidated(LabSettings.WorldType worldType, List<LabSettings.Layer> layers,
                                 int platformRadius, ResourceLocation platformBlock) {
        void applyTo(LabSettings s) {
            s.worldType = worldType;
            s.superflatLayers = new ArrayList<>(layers);
            s.platformRadius = platformRadius;
            s.platformBlock = platformBlock;
        }
    }

    /** Never trust the client: every block id must resolve, heights must be
     *  positive, the layer count and stack height must fit the dimension, and
     *  the platform radius must be sane. Returns empty on any failure. */
    private static Optional<LabValidated> validateGenSpec(MinecraftServer server, LabGenSpec gen) {
        if (gen.layers().size() > 32) return Optional.empty();

        List<LabSettings.Layer> layers = new ArrayList<>();
        int sum = 0;
        for (LayerSpec l : gen.layers()) {
            if (l.height() < 1) return Optional.empty();
            ResourceLocation blockId;
            try {
                blockId = ResourceLocation.parse(l.block());
            } catch (Exception e) {
                return Optional.empty();
            }
            if (!BuiltInRegistries.BLOCK.containsKey(blockId)) return Optional.empty();
            layers.add(new LabSettings.Layer(blockId, l.height()));
            sum += l.height();
        }
        int dimensionHeight = labDimensionType(server).height();
        if (gen.worldType() == LabSettings.WorldType.SUPERFLAT && (layers.isEmpty() || sum > dimensionHeight)) {
            return Optional.empty();
        }

        if (gen.platformRadius() < 1 || gen.platformRadius() > 64) return Optional.empty();
        ResourceLocation platformBlock;
        try {
            platformBlock = ResourceLocation.parse(gen.platformBlock());
        } catch (Exception e) {
            return Optional.empty();
        }
        if (!BuiltInRegistries.BLOCK.containsKey(platformBlock)) return Optional.empty();

        return Optional.of(new LabValidated(gen.worldType(), layers, gen.platformRadius(), platformBlock));
    }
}
