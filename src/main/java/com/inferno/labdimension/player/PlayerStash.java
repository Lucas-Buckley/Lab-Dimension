package com.inferno.labdimension.player;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public record PlayerStash(
        ListTag inventory,
        int selectedSlot,
        ListTag enderChest,
        int xpLevel,
        float xpProgress,
        int totalXp,
        GameType gameMode,
        ResourceKey<Level> originDim,
        Vec3 originPos,
        float yaw,
        float pitch,
        CompoundTag foreignState,
        Vitals vitals
) {
    /**
     * Health/hunger/effects snapshot. Nullable on PlayerStash so a stash saved
     * before this field existed loads as vitals == null and restore() simply
     * skips it -- see PlayerStash.load(). Without this, the lab's forced
     * Creative mode regenerates the player, turning a /lab round trip into a
     * free full heal + status cure.
     */
    public record Vitals(
            float health,
            float absorption,
            CompoundTag foodData,
            ListTag effects,
            int remainingFireTicks,
            int airSupply
    ) {
        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putFloat("health", health);
            tag.putFloat("absorption", absorption);
            tag.put("food", foodData);
            tag.put("effects", effects);
            tag.putInt("fire", remainingFireTicks);
            tag.putInt("air", airSupply);
            return tag;
        }

        public static Vitals load(CompoundTag tag) {
            return new Vitals(
                    tag.getFloat("health"),
                    tag.getFloat("absorption"),
                    tag.getCompound("food"),
                    tag.getList("effects", Tag.TAG_COMPOUND),
                    tag.getInt("fire"),
                    tag.getInt("air")
            );
        }
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("inventory", inventory);
        tag.putInt("selectedSlot", selectedSlot);
        tag.put("enderChest", enderChest);
        tag.putInt("xpLevel", xpLevel);
        tag.putFloat("xpProgress", xpProgress);
        tag.putInt("totalXp", totalXp);
        tag.putString("gameMode", gameMode.getName());
        tag.putString("originDim", originDim.location().toString());
        tag.putDouble("originX", originPos.x);
        tag.putDouble("originY", originPos.y);
        tag.putDouble("originZ", originPos.z);
        tag.putFloat("yaw", yaw);
        tag.putFloat("pitch", pitch);
        tag.put("foreignState", foreignState);
        if (vitals != null) {
            tag.put("vitals", vitals.save());
        }
        return tag;
    }

    public static PlayerStash load(CompoundTag tag) {
        ResourceLocation dimLoc = ResourceLocation.parse(tag.getString("originDim"));
        Vitals vitals = tag.contains("vitals", Tag.TAG_COMPOUND)
                ? Vitals.load(tag.getCompound("vitals"))
                : null;
        return new PlayerStash(
                tag.getList("inventory", 10),
                tag.getInt("selectedSlot"),
                tag.getList("enderChest", 10),
                tag.getInt("xpLevel"),
                tag.getFloat("xpProgress"),
                tag.getInt("totalXp"),
                gameTypeByName(tag.getString("gameMode")),
                ResourceKey.create(Registries.DIMENSION, dimLoc),
                new Vec3(tag.getDouble("originX"), tag.getDouble("originY"), tag.getDouble("originZ")),
                tag.getFloat("yaw"),
                tag.getFloat("pitch"),
                tag.getCompound("foreignState"),
                vitals
        );
    }

    private static GameType gameTypeByName(String name) {
        for (GameType t : GameType.values()) {
            if (t.getName().equals(name)) return t;
        }
        return GameType.SURVIVAL;
    }
}
