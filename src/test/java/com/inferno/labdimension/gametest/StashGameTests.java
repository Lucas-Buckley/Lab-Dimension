package com.inferno.labdimension.gametest;

import com.inferno.labdimension.LabKeys;
import com.inferno.labdimension.dimension.LabProvider;
import com.inferno.labdimension.dimension.LabRosterData;
import com.inferno.labdimension.dimension.LabSettings;
import com.inferno.labdimension.dimension.LabPlatformBuilder;
import com.inferno.labdimension.player.PlayerStash;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;

import java.util.UUID;

/**
 * Pure-logic and single-level tests runnable via `./gradlew runGameTestServer`.
 * No client required.
 */
@GameTestHolder("labdimension")
public final class StashGameTests {

    @GameTest(template = "empty3x3x3")
    public void stashRoundTrip(GameTestHelper helper) {
        ListTag inv = new ListTag();
        CompoundTag stack = new CompoundTag();
        stack.putByte("Slot", (byte) 0);
        stack.putString("id", "minecraft:diamond");
        stack.putInt("count", 5);
        inv.add(stack);

        PlayerStash.Vitals vitals = new PlayerStash.Vitals(
                5.0f, 2.0f, new CompoundTag(), new ListTag(), 0, 300
        );

        PlayerStash original = new PlayerStash(
                inv, 0, new ListTag(),
                7, 0.42f, 123,
                GameType.SURVIVAL,
                net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.DIMENSION,
                        net.minecraft.resources.ResourceLocation.withDefaultNamespace("overworld")),
                new Vec3(1.5, 65, -3.5),
                90f, 0f,
                new CompoundTag(),
                vitals
        );

        CompoundTag saved = original.save();
        PlayerStash loaded = PlayerStash.load(saved);

        helper.assertTrue(loaded.xpLevel() == original.xpLevel(), "xpLevel mismatch");
        helper.assertTrue(loaded.totalXp() == original.totalXp(), "totalXp mismatch");
        helper.assertTrue(loaded.gameMode() == original.gameMode(), "gameMode mismatch");
        helper.assertTrue(loaded.originDim().equals(original.originDim()), "originDim mismatch");
        helper.assertTrue(loaded.originPos().equals(original.originPos()), "originPos mismatch");
        helper.assertTrue(loaded.inventory().toString().equals(original.inventory().toString()), "inventory NBT mismatch");
        helper.assertTrue(loaded.vitals() != null && loaded.vitals().health() == 5.0f, "vitals.health mismatch");
        helper.assertTrue(loaded.vitals().airSupply() == 300, "vitals.airSupply mismatch");

        // Back-compat: a stash saved before the vitals fix must load with vitals == null,
        // not throw and not silently zero the player's health.
        CompoundTag legacy = original.save();
        legacy.remove("vitals");
        PlayerStash legacyLoaded = PlayerStash.load(legacy);
        helper.assertTrue(legacyLoaded.vitals() == null, "legacy stash should load with null vitals");

        helper.succeed();
    }

    @GameTest(template = "empty3x3x3")
    public void dynamicLabCreation(GameTestHelper helper) {
        ServerLevel overworld = helper.getLevel().getServer().overworld();
        LabRosterData roster = LabRosterData.get(overworld);
        UUID owner = UUID.randomUUID();

        helper.assertFalse(LabProvider.exists(helper.getLevel().getServer(), owner), "Lab should not exist yet");

        LabProvider.getOrCreate(helper.getLevel().getServer(), owner).thenAccept(level -> {
            helper.getLevel().getServer().execute(() -> {
                boolean registered = LabProvider.exists(helper.getLevel().getServer(), owner);
                LabSettings entry = roster.entryFor(owner);
                LabPlatformBuilder.ensurePlatform(level, entry);
                boolean idempotentOk = entry.platformGenerated;
                // Calling again must not throw or regenerate.
                LabPlatformBuilder.ensurePlatform(level, entry);

                if (registered && idempotentOk) {
                    helper.succeed();
                } else {
                    helper.fail("Lab dimension was not registered or platform not marked generated");
                }
            });
        });
    }

    @GameTest(template = "empty3x3x3")
    public void parseLabPath(GameTestHelper helper) {
        UUID owner = UUID.fromString("12345678-1234-5678-1234-567812345678");
        String hex = "12345678123456781234567812345678";

        // generation 0 -- bare key, must match v1's exact format
        ResourceLocation gen0 = LabKeys.labPath(owner);
        helper.assertTrue(gen0.getPath().equals("lab_" + hex), "gen0 path mismatch: " + gen0.getPath());
        LabKeys.LabRef ref0 = LabKeys.parseLabPath(gen0);
        helper.assertTrue(ref0 != null && ref0.owner().equals(owner) && ref0.generation() == 0,
                "gen0 parse mismatch");

        // generation N
        ResourceLocation gen3 = LabKeys.labPath(owner, 3);
        helper.assertTrue(gen3.getPath().equals("lab_" + hex + "_g3"), "gen3 path mismatch: " + gen3.getPath());
        LabKeys.LabRef ref3 = LabKeys.parseLabPath(gen3);
        helper.assertTrue(ref3 != null && ref3.owner().equals(owner) && ref3.generation() == 3,
                "gen3 parse mismatch");

        // wrong namespace must not parse
        helper.assertTrue(LabKeys.parseLabPath(ResourceLocation.fromNamespaceAndPath("minecraft", "lab_" + hex)) == null,
                "wrong namespace should not parse");

        // malformed paths must not parse
        helper.assertTrue(LabKeys.parseLabPath("lab_tooshort") == null, "short hex should not parse");
        helper.assertTrue(LabKeys.parseLabPath("lab_" + hex + "_gX") == null, "non-numeric generation should not parse");
        helper.assertTrue(LabKeys.parseLabPath("not_a_lab_path") == null, "unrelated path should not parse");
        helper.assertTrue(LabKeys.parseLabPath("lab_" + hex + "extra") == null, "trailing garbage should not parse");

        helper.succeed();
    }
}
