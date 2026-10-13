package noppes.npcs.constants;

/**
 * Visual style used by the client to show dialogs. Only the presentation changes:
 * the dialog data and the packets are the same for every style.
 */
public enum EnumDialogStyle {
    NEW("new"), CLASSIC("classic"), MODERN("modern");

    public final String configName;

    EnumDialogStyle(String configName) {
        this.configName = configName;
    }

    /**
     * @return the style with that config name, or {@code fallback} if none matches
     */
    public static EnumDialogStyle fromConfig(String name, EnumDialogStyle fallback) {
        if (name != null) {
            for (EnumDialogStyle style : values()) {
                if (style.configName.equalsIgnoreCase(name.trim()))
                    return style;
            }
        }
        return fallback;
    }

    public EnumDialogStyle next() {
        return values()[(ordinal() + 1) % values().length];
    }
}
