package noppes.npcs.config;

import cpw.mods.fml.common.FMLLog;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import noppes.npcs.constants.EnumDialogStyle;
import org.apache.logging.log4j.Level;

import java.io.File;

public class ConfigExperimental {
    public static Configuration config;

    public final static String CLIENT = "Client";
    public final static String SERVER = "Server";
    public static Property ModernGuiSystemProperty;
    public static boolean ModernGuiSystem = false;


    public static void init(File configFile) {
        config = new Configuration(configFile);

        try {
            config.load();

            ModernGuiSystemProperty = config.get(CLIENT, "Experimental Dialog GUI", false, "Deprecated: use 'Dialog Style' in the client config or the Dialogs tab of the N menu");
            ModernGuiSystem = ModernGuiSystemProperty.getBoolean(false);
            migrateDialogStyle();
        } catch (Exception e) {
            FMLLog.log(Level.ERROR, e, "CNPC+ has had a problem loading its experimental configuration");
        } finally {
            if (config.hasChanged()) {
                config.save();
            }
        }
    }

    /**
     * One time move of the old experimental switch to the client 'Dialog Style'.
     * Needs ConfigClient loaded first (LoadConfiguration does it in that order).
     */
    private static void migrateDialogStyle() {
        if (ConfigClient.config == null || ConfigClient.DialogStyleMigratedProperty == null)
            return;
        if (ConfigClient.DialogStyleMigratedProperty.getBoolean(false))
            return;
        if (ModernGuiSystem) {
            ConfigClient.DialogStyle = EnumDialogStyle.MODERN.configName;
            ConfigClient.DialogStyleProperty.set(ConfigClient.DialogStyle);
        }
        ConfigClient.DialogStyleMigratedProperty.set(true);
        ConfigClient.config.save();
    }
}
