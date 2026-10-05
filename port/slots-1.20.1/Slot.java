package noppes.npcs.controllers.data.profile;

import net.minecraft.nbt.CompoundTag;

import java.util.HashMap;
import java.util.Map;

/** A single character slot: a named bag of per-type NBT components (1.20.1). */
public class Slot {
    private int id;
    private String name;
    private long lastLoaded;
    private boolean temporary;          // point 7 kept (clone/temporary slots)
    private Map<String, CompoundTag> components = new HashMap<>();

    public Slot(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public Slot(int id, String name, long lastLoaded, boolean temporary, Map<String, CompoundTag> components) {
        this.id = id;
        this.name = name;
        this.lastLoaded = lastLoaded;
        this.temporary = temporary;
        this.components = components;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public long getLastLoaded() { return lastLoaded; }
    public void setLastLoaded(long time) { this.lastLoaded = time; }
    public boolean isTemporary() { return temporary; }
    public void setTemporary(boolean temporary) { this.temporary = temporary; }

    public Map<String, CompoundTag> getComponents() { return components; }
    public void setComponentData(String key, CompoundTag data) { components.put(key, data); }
    public CompoundTag getComponentData(String key) { return components.get(key); }

    public CompoundTag toNBT() {
        CompoundTag slotNBT = new CompoundTag();
        slotNBT.putString("Name", name);
        slotNBT.putLong("LastLoaded", lastLoaded);
        slotNBT.putBoolean("Temporary", temporary);
        CompoundTag comp = new CompoundTag();
        for (Map.Entry<String, CompoundTag> e : components.entrySet()) {
            comp.put(e.getKey(), e.getValue());
        }
        slotNBT.put("Components", comp);
        return slotNBT;
    }

    public static Slot fromNBT(int id, CompoundTag slotNBT) {
        Slot slot = new Slot(id, slotNBT.getString("Name"));
        slot.setLastLoaded(slotNBT.getLong("LastLoaded"));
        slot.setTemporary(slotNBT.getBoolean("Temporary"));
        if (slotNBT.contains("Components")) {
            CompoundTag comp = slotNBT.getCompound("Components");
            for (String key : comp.getAllKeys()) {
                slot.setComponentData(key, comp.getCompound(key));
            }
        }
        return slot;
    }
}
