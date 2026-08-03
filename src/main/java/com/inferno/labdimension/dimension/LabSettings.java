package com.inferno.labdimension.dimension;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Per-lab configuration. Mutable rather than a record: it lives by reference in
 * LabRosterData's map and is edited in place by commands/GUI handlers rather than
 * requiring the whole map entry to be replaced on every field change.
 */
public final class LabSettings {

    public enum WorldType { VOID, SUPERFLAT }
    public enum Visibility { PRIVATE, PUBLIC }
    public enum GuestPermission { NONE, BUILD, WORLDEDIT }

    public record Layer(ResourceLocation block, int height) {}

    // --- creation-time (changing requires regeneration into a new dimension key)
    public WorldType worldType = WorldType.VOID;
    public List<Layer> superflatLayers = new ArrayList<>(defaultLayers());
    public int platformRadius = 8;
    public ResourceLocation platformBlock = ResourceLocation.parse("minecraft:smooth_stone");

    // --- runtime-changeable
    public Visibility visibility = Visibility.PRIVATE;
    public final Set<UUID> whitelist = new HashSet<>();
    public final Set<UUID> blacklist = new HashSet<>();
    public GuestPermission guestPermission = GuestPermission.WORLDEDIT;
    public String displayName = "";
    public Vec3 spawnPos = new Vec3(0.5, 64, 0.5);
    public float spawnYaw = 0f;
    public float spawnPitch = 0f;

    // --- internal
    public int generation = 0;
    public boolean platformGenerated = false;
    public long createdAt = 0L;

    public static List<Layer> defaultLayers() {
        return List.of(
                new Layer(ResourceLocation.withDefaultNamespace("bedrock"), 1),
                new Layer(ResourceLocation.withDefaultNamespace("stone"), 59),
                new Layer(ResourceLocation.withDefaultNamespace("dirt"), 3),
                new Layer(ResourceLocation.withDefaultNamespace("grass_block"), 1)
        );
    }

    /** Surface Y for the current superflatLayers stack, given the dimension's min_y. */
    public int surfaceY(int minY) {
        int sum = 0;
        for (Layer l : superflatLayers) sum += l.height();
        return minY + sum;
    }

    /** Applies the guest-permission default for the current visibility. Only call this
     *  on an explicit visibility change where the caller has not separately set
     *  guestPermission in the same edit -- otherwise it silently overwrites a
     *  deliberate choice. */
    public void applyVisibilityDefault() {
        guestPermission = visibility == Visibility.PRIVATE ? GuestPermission.WORLDEDIT : GuestPermission.NONE;
    }

    /** True if this settings object differs from other in a way that requires a fresh
     *  dimension (world type, layer stack, or void platform geometry). */
    public boolean requiresRegeneration(LabSettings other) {
        if (worldType != other.worldType) return true;
        if (worldType == WorldType.SUPERFLAT && !superflatLayers.equals(other.superflatLayers)) return true;
        if (worldType == WorldType.VOID) {
            if (platformRadius != other.platformRadius) return true;
            if (!platformBlock.equals(other.platformBlock)) return true;
        }
        return false;
    }

    public LabSettings copy() {
        LabSettings s = new LabSettings();
        s.worldType = worldType;
        s.superflatLayers = new ArrayList<>(superflatLayers);
        s.platformRadius = platformRadius;
        s.platformBlock = platformBlock;
        s.visibility = visibility;
        s.whitelist.addAll(whitelist);
        s.blacklist.addAll(blacklist);
        s.guestPermission = guestPermission;
        s.displayName = displayName;
        s.spawnPos = spawnPos;
        s.spawnYaw = spawnYaw;
        s.spawnPitch = spawnPitch;
        s.generation = generation;
        s.platformGenerated = platformGenerated;
        s.createdAt = createdAt;
        return s;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("v", 2);
        tag.putString("worldType", worldType.name());
        ListTag layers = new ListTag();
        for (Layer l : superflatLayers) {
            CompoundTag lt = new CompoundTag();
            lt.putString("block", l.block().toString());
            lt.putInt("height", l.height());
            layers.add(lt);
        }
        tag.put("layers", layers);
        tag.putInt("platformRadius", platformRadius);
        tag.putString("platformBlock", platformBlock.toString());

        tag.putString("visibility", visibility.name());
        tag.put("whitelist", uuidSetToTag(whitelist));
        tag.put("blacklist", uuidSetToTag(blacklist));
        tag.putString("guestPermission", guestPermission.name());
        tag.putString("displayName", displayName);
        tag.putDouble("spawnX", spawnPos.x);
        tag.putDouble("spawnY", spawnPos.y);
        tag.putDouble("spawnZ", spawnPos.z);
        tag.putFloat("spawnYaw", spawnYaw);
        tag.putFloat("spawnPitch", spawnPitch);

        tag.putInt("generation", generation);
        tag.putBoolean("platformGenerated", platformGenerated);
        tag.putLong("createdAt", createdAt);
        return tag;
    }

    /** Loads a v2 tag. Callers must check the "v" field and route pre-v2 (legacy Entry)
     *  tags through LabRosterData's migration path instead of calling this directly. */
    public static LabSettings load(CompoundTag tag) {
        LabSettings s = new LabSettings();
        s.worldType = enumOr(tag.getString("worldType"), WorldType.class, WorldType.VOID);
        s.superflatLayers = new ArrayList<>();
        ListTag layers = tag.getList("layers", Tag.TAG_COMPOUND);
        for (int i = 0; i < layers.size(); i++) {
            CompoundTag lt = layers.getCompound(i);
            s.superflatLayers.add(new Layer(ResourceLocation.parse(lt.getString("block")), lt.getInt("height")));
        }
        if (s.superflatLayers.isEmpty() && s.worldType == WorldType.SUPERFLAT) {
            s.superflatLayers = new ArrayList<>(defaultLayers());
        }
        s.platformRadius = tag.contains("platformRadius") ? tag.getInt("platformRadius") : 8;
        s.platformBlock = tag.contains("platformBlock")
                ? ResourceLocation.parse(tag.getString("platformBlock"))
                : ResourceLocation.parse("minecraft:smooth_stone");

        s.visibility = enumOr(tag.getString("visibility"), Visibility.class, Visibility.PRIVATE);
        s.whitelist.addAll(uuidSetFromTag(tag.getList("whitelist", Tag.TAG_STRING)));
        s.blacklist.addAll(uuidSetFromTag(tag.getList("blacklist", Tag.TAG_STRING)));
        s.guestPermission = enumOr(tag.getString("guestPermission"), GuestPermission.class, GuestPermission.WORLDEDIT);
        s.displayName = tag.getString("displayName");
        s.spawnPos = new Vec3(tag.getDouble("spawnX"), tag.getDouble("spawnY"), tag.getDouble("spawnZ"));
        s.spawnYaw = tag.getFloat("spawnYaw");
        s.spawnPitch = tag.getFloat("spawnPitch");

        s.generation = tag.getInt("generation");
        s.platformGenerated = tag.getBoolean("platformGenerated");
        s.createdAt = tag.getLong("createdAt");
        return s;
    }

    private static <E extends Enum<E>> E enumOr(String name, Class<E> type, E fallback) {
        if (name == null || name.isEmpty()) return fallback;
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private static ListTag uuidSetToTag(Set<UUID> ids) {
        ListTag list = new ListTag();
        for (UUID id : ids) list.add(StringTag.valueOf(id.toString()));
        return list;
    }

    private static Set<UUID> uuidSetFromTag(ListTag list) {
        Set<UUID> ids = new HashSet<>();
        for (int i = 0; i < list.size(); i++) {
            try {
                ids.add(UUID.fromString(list.getString(i)));
            } catch (IllegalArgumentException ignored) {
                // corrupt entry -- skip rather than fail the whole load
            }
        }
        return ids;
    }
}
