package com.inferno.labdimension.dimension;

import com.inferno.labdimension.LabKeys;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.UUID;

public final class LabAccess {
    private LabAccess() {}

    public enum Access {
        DENIED, VISIT, BUILD, WORLDEDIT;

        public boolean atLeast(Access other) {
            return ordinal() >= other.ordinal();
        }
    }

    /**
     * Evaluation order is deliberate: op beats blacklist, not the other way
     * round. A player must not be able to lock server staff out of their own
     * lab by blacklisting them -- that's an abuse vector on a public server.
     */
    public static Access accessFor(ServerPlayer player, UUID owner, @Nullable LabSettings s) {
        if (s == null) return Access.DENIED;
        if (owner.equals(player.getUUID())) return Access.WORLDEDIT;
        if (player.hasPermissions(2)) return Access.WORLDEDIT;
        if (s.blacklist.contains(player.getUUID())) return Access.DENIED;
        if (s.visibility == LabSettings.Visibility.PRIVATE && !s.whitelist.contains(player.getUUID())) {
            return Access.DENIED;
        }
        return switch (s.guestPermission) {
            case NONE -> Access.VISIT;
            case BUILD -> Access.BUILD;
            case WORLDEDIT -> Access.WORLDEDIT;
        };
    }

    /** DENIED if the player isn't currently standing in any lab. */
    public static Access accessHere(ServerPlayer player) {
        return accessIn(player, player.level());
    }

    public static Access accessIn(ServerPlayer player, Level level) {
        LabKeys.LabRef ref = LabKeys.parseLabLevel(level);
        if (ref == null) return Access.DENIED;
        LabRosterData roster = LabRosterData.get(player.server.overworld());
        LabSettings settings = roster.peek(ref.owner());
        // A settings object with a different generation than the level the player is
        // physically standing in means the lab was regenerated out from under them
        // (shouldn't happen -- occupants are ejected before regeneration -- but if it
        // ever does, deny rather than apply the wrong generation's permissions).
        if (settings != null && settings.generation != ref.generation()) return Access.DENIED;
        return accessFor(player, ref.owner(), settings);
    }
}
