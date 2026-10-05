package noppes.npcs.controllers.data.profile;

import net.minecraft.world.entity.player.Player;

/**
 * point 5: script event hooks for profile operations.
 *
 * ============================ INTEGRATION TODO ============================
 * BetaZavr's EventHooks has 89 events but NONE for profiles. Adding real
 * script-visible events means: define event classes + register them in their
 * scripting API + dispatch here. Until then these are NO-OPs (never cancel).
 *
 * Contract (matches the 1.7.10 EventHooks): return true == operation CANCELLED.
 * =========================================================================
 */
public class ProfileEvents {

    public static boolean onCreate(Player player, Profile profile, int slotId) {
        // TODO: dispatch ProfileEvent.CreateEvent
        return false;
    }

    public static boolean onChange(Player player, Profile profile, int newSlotId, int prevSlotId) {
        // TODO: dispatch ProfileEvent.ChangeEvent
        return false;
    }

    public static boolean onRemove(Player player, Profile profile, int slotId) {
        // TODO: dispatch ProfileEvent.RemoveEvent
        return false;
    }
}
