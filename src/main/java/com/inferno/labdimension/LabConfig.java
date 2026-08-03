package com.inferno.labdimension;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public final class LabConfig {
    private LabConfig() {}

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue FORCE_CREATIVE;
    public static final ModConfigSpec.IntValue LAB_SPAWN_Y;
    public static final ModConfigSpec.IntValue VOID_RESCUE_Y;
    public static final ModConfigSpec.IntValue PLATFORM_RADIUS;
    public static final ModConfigSpec.ConfigValue<String> PLATFORM_BLOCK;

    public static final ModConfigSpec.BooleanValue BLOCK_WHILE_IN_COMBAT;
    public static final ModConfigSpec.IntValue COMBAT_COOLDOWN_TICKS;
    public static final ModConfigSpec.BooleanValue BLOCK_WHILE_RIDING;
    public static final ModConfigSpec.BooleanValue BLOCK_ON_SABLE_SHIP;
    public static final ModConfigSpec.BooleanValue QUARK_STRICT;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BLOCK_NAMESPACES;

    public static final ModConfigSpec.BooleanValue GATE_WORLDEDIT;

    public static final ModConfigSpec.BooleanValue PROTECT_BLOCKS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("lab");
        FORCE_CREATIVE = builder.define("forceCreative", true);
        LAB_SPAWN_Y = builder.defineInRange("labSpawnY", 64, 0, 320);
        VOID_RESCUE_Y = builder.defineInRange("voidRescueY", -16, -256, 320);
        PLATFORM_RADIUS = builder.defineInRange("platformRadius", 8, 1, 64);
        PLATFORM_BLOCK = builder.define("platformBlock", "minecraft:smooth_stone");
        builder.pop();

        builder.push("guards");
        BLOCK_WHILE_IN_COMBAT = builder.define("blockWhileInCombat", true);
        COMBAT_COOLDOWN_TICKS = builder.defineInRange("combatCooldownTicks", 100, 0, 6000);
        BLOCK_WHILE_RIDING = builder.define("blockWhileRiding", true);
        BLOCK_ON_SABLE_SHIP = builder.define("blockOnSableShip", true);
        QUARK_STRICT = builder.define("quarkStrict", true);
        BLOCK_NAMESPACES = builder.defineListAllowEmpty("blockNamespaces",
                List.of("carryon"), () -> "", o -> o instanceof String);
        builder.pop();

        builder.push("worldedit");
        GATE_WORLDEDIT = builder.define("gateWorldEdit", true);
        builder.pop();

        builder.push("protection");
        // BreakEvent/RightClickBlock are among the most contended events in the modding
        // ecosystem; on a large pack (this one ships with ~150 mods) cancelling them can
        // collide with other protection systems. One-line kill switch if that happens.
        PROTECT_BLOCKS = builder.define("protectBlocks", true);
        builder.pop();

        SPEC = builder.build();
    }
}
