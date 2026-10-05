package noppes.npcs.controllers.data.profile;

/** point 8: a single info line for the slot GUI (label key + color + value). */
public class ProfileInfoEntry {
    public final String key;
    public final int color;
    public final int value;

    public ProfileInfoEntry(String key, int color, int value) {
        this.key = key;
        this.color = color;
        this.value = value;
    }
}
