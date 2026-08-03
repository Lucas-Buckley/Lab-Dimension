package com.inferno.labdimension;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LabKeys {
    private LabKeys() {}

    public static final String MOD_ID = "labdimension";

    public static final ResourceKey<DimensionType> LAB_DIMENSION_TYPE =
            ResourceKey.create(Registries.DIMENSION_TYPE, ResourceLocation.fromNamespaceAndPath(MOD_ID, "lab"));

    private static final Pattern LAB_PATH_PATTERN =
            Pattern.compile("^lab_([0-9a-f]{32})(?:_g(\\d+))?$");

    /** Generation 0 -- the bare key, kept for back-compat with every lab created by v1. */
    public static ResourceLocation labPath(UUID owner) {
        return labPath(owner, 0);
    }

    public static ResourceLocation labPath(UUID owner, int generation) {
        String hex = owner.toString().replace("-", "");
        String path = generation == 0 ? "lab_" + hex : "lab_" + hex + "_g" + generation;
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    /** Owner UUID + generation parsed from a lab dimension key/path; null if not a lab path. */
    public record LabRef(UUID owner, int generation) {}

    /** Strict parse of a lab dimension's ResourceLocation. Returns null for anything else,
     *  including malformed lab-ish paths -- callers must not guess at a fallback. */
    public static @Nullable LabRef parseLabPath(ResourceLocation loc) {
        if (!loc.getNamespace().equals(MOD_ID)) return null;
        return parseLabPath(loc.getPath());
    }

    public static @Nullable LabRef parseLabPath(String path) {
        Matcher m = LAB_PATH_PATTERN.matcher(path);
        if (!m.matches()) return null;
        String hex = m.group(1);
        String dashed = hex.replaceFirst(
                "(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5");
        UUID owner;
        try {
            owner = UUID.fromString(dashed);
        } catch (IllegalArgumentException e) {
            return null;
        }
        int generation = m.group(2) != null ? Integer.parseInt(m.group(2)) : 0;
        return new LabRef(owner, generation);
    }

    public static @Nullable LabRef parseLabLevel(Level level) {
        return parseLabPath(level.dimension().location());
    }

    public static String stashTagKey() {
        return MOD_ID + ":stash";
    }
}
