package noppes.npcs.controllers.data.profile;

import java.util.ArrayList;
import java.util.List;

/**
 * Config holder for the slot system. Wire these to BetaZavr's own config loader
 * (ConfigLoader / ConfigMain equivalent) when integrating.
 */
public class ProfileConfig {
    public static boolean ProfilesEnabled = true;

    /** point 2: flat max per player. (Granular per-tier permission nodes from the
     *  1.7.10 version were dropped; 1.20.1 uses registered PermissionNodes, not
     *  arbitrary string nodes. TODO: optional registered "profile.max" bypass node.) */
    public static int DefaultProfileSlots = 5;

    /** point 3: backups. */
    public static boolean AllowProfileBackups = true;
    public static int ProfileBackupAmount = 5;

    /** point 1: region-restricted switching. */
    public static boolean RegionProfileSwitching = false;
    public static List<ProfileRegion> RestrictedProfileRegions = new ArrayList<>();

    /** A region box keyed by dimension id, e.g. "minecraft:overworld".
     *  (1.20.1 dimensions are ResourceKeys, not ints like 1.7.10.) */
    public static class ProfileRegion {
        public final String dimension;
        public final int x1, y1, z1, x2, y2, z2;

        public ProfileRegion(String dimension, int x1, int y1, int z1, int x2, int y2, int z2) {
            this.dimension = dimension;
            this.x1 = Math.min(x1, x2); this.x2 = Math.max(x1, x2);
            this.y1 = Math.min(y1, y2); this.y2 = Math.max(y1, y2);
            this.z1 = Math.min(z1, z2); this.z2 = Math.max(z1, z2);
        }

        public boolean contains(String dim, int x, int y, int z) {
            return dimension.equals(dim)
                && x >= x1 && x <= x2 && y >= y1 && y <= y2 && z >= z1 && z <= z2;
        }
    }
}
