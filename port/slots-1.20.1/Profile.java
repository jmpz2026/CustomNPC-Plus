package noppes.npcs.controllers.data.profile;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;

/** One profile per player: the slots + which one is active (1.20.1). */
public class Profile {
    public Player player;
    public int currentSlotId;
    private final Map<Integer, Slot> slots = new HashMap<>();
    private boolean locked = false;

    public Profile(Player player, CompoundTag compound) {
        this.player = player;
        this.currentSlotId = compound.contains("CurrentSlotId") ? compound.getInt("CurrentSlotId") : 0;

        if (compound.contains("Slots")) {
            CompoundTag slotsCompound = compound.getCompound("Slots");
            for (String key : slotsCompound.getAllKeys()) {
                try {
                    int slotId = Integer.parseInt(key);
                    slots.put(slotId, Slot.fromNBT(slotId, slotsCompound.getCompound(key)));
                } catch (NumberFormatException ignored) {
                    // Skip keys that are not valid slot IDs.
                }
            }
        }
    }

    public Player getPlayer() { return player; }
    public int getCurrentSlotId() { return currentSlotId; }
    public Map<Integer, Slot> getSlots() { return slots; }
    public boolean isLocked() { return locked; }
    public void setLocked(boolean locked) { this.locked = locked; }

    public CompoundTag writeToNBT() {
        CompoundTag compound = new CompoundTag();
        if (player != null) {
            compound.putString("Name", player.getName().getString());
        }
        compound.putInt("CurrentSlotId", currentSlotId);
        CompoundTag slotsCompound = new CompoundTag();
        for (Map.Entry<Integer, Slot> e : slots.entrySet()) {
            // point 7: temporary slots stay in memory only, never persisted.
            if (e.getValue().isTemporary()) continue;
            slotsCompound.put(String.valueOf(e.getKey()), e.getValue().toNBT());
        }
        compound.put("Slots", slotsCompound);
        return compound;
    }
}
