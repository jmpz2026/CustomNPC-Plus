package noppes.npcs.controllers;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.server.ServerLifecycleHooks;
import noppes.npcs.CustomNpcs;
import noppes.npcs.shared.common.util.LogWriter;
import noppes.npcs.controllers.PlayerDataController;
import noppes.npcs.controllers.data.PlayerData;
import noppes.npcs.controllers.data.profile.CNPCData;
import noppes.npcs.controllers.data.profile.IProfileData;
import noppes.npcs.controllers.data.profile.Profile;
import noppes.npcs.controllers.data.profile.ProfileConfig;
import noppes.npcs.controllers.data.profile.ProfileEvents;
import noppes.npcs.controllers.data.profile.ProfileInfoEntry;
import noppes.npcs.controllers.data.profile.ProfileOperation;
import noppes.npcs.controllers.data.profile.ProfileQuestSync;
import noppes.npcs.controllers.data.profile.Slot;
import noppes.npcs.util.CustomNPCsScheduler;
import noppes.npcs.util.NBTJsonUtil;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Slot/profile manager — ported to BetaZavr CustomNPCs-Unofficial 1.20.1.
 *
 * Ported faithfully (points 1-10): regions, flat slot limit, backups+rollback,
 * offline ops, clone+temporary slots, slot info, async saving.
 * Set aside: Party guard, AttributeController recalc, ProfileSlotRequirement.
 *
 * Three spots need BetaZavr-side wiring (compile as no-ops): see
 * ProfileEvents (point 5), ProfileQuestSync (point 6) and the slot limit
 * permission node (point 2).
 */
public class ProfileController {

    public static Map<String, IProfileData> profileTypes = new HashMap<>();
    public static Map<UUID, Profile> activeProfiles = new HashMap<>();
    public static String profile_directory = "profiles";

    public static ProfileController Instance;

    public ProfileController() {
        Instance = this;
        profileTypes = new HashMap<>();
        activeProfiles = new HashMap<>();
        registerProfileType(new CNPCData());
    }

    public static boolean registerProfileType(IProfileData type) {
        if (profileTypes.containsKey(type.getTagName()))
            return false;
        profileTypes.put(type.getTagName(), type);
        return true;
    }

    // ----------------------------------------------------------------- storage

    public static File getProfileDir() {
        // BetaZavr: getLevelSaveDirectory(sub) -> <world>/customnpcs/<sub>
        File file = CustomNpcs.getLevelSaveDirectory(profile_directory);
        if (file != null && !file.exists())
            file.mkdirs();
        return file;
    }

    public static File getBackupDir() {
        File backup = new File(getProfileDir(), "backup");
        if (!backup.exists())
            backup.mkdirs();
        return backup;
    }

    public synchronized CompoundTag load(UUID uuid) {
        File file = new File(getProfileDir(), uuid + ".json");
        try {
            if (file.exists())
                return NBTJsonUtil.LoadFile(file);
        } catch (Exception e) {
            LogWriter.error("Error loading profile file: " + file.getName(), e);
        }
        return new CompoundTag();
    }

    public synchronized void save(Player player, Profile profile) {
        writeAsync(profile, player.getUUID(), ProfileConfig.AllowProfileBackups);
    }

    /** point 4: offline save. */
    public synchronized void saveOffline(Profile profile, UUID uuid) {
        writeAsync(profile, uuid, ProfileConfig.AllowProfileBackups);
    }

    private void writeAsync(Profile profile, UUID uuid, boolean backup) {
        profile.setLocked(true);
        final CompoundTag compound = profile.writeToNBT();
        // point 9: async, via BetaZavr's scheduler (same as PlayerData.save).
        CustomNPCsScheduler.runTack(() -> {
            try {
                File saveDir = getProfileDir();
                File fileNew = new File(saveDir, uuid + ".json_new");
                File fileOld = new File(saveDir, uuid + ".json");
                NBTJsonUtil.SaveFile(fileNew, compound);
                if (fileOld.exists() && !fileOld.delete())
                    LogWriter.warn("Could not delete old profile: " + fileOld);
                if (!fileNew.renameTo(fileOld))
                    LogWriter.warn("Could not rename profile: " + fileNew);
                if (backup)
                    backupProfile(uuid, compound);
            } catch (Exception e) {
                LogWriter.error("Error saving profile", e);
            } finally {
                profile.setLocked(false);
            }
        });
    }

    // ----------------------------------------------------------------- backups (point 3)

    private void backupProfile(UUID uuid, CompoundTag compound) {
        try {
            File backupDir = new File(getBackupDir(), uuid.toString());
            if (!backupDir.exists())
                backupDir.mkdirs();
            String dateStr = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            NBTJsonUtil.SaveFile(new File(backupDir, dateStr + ".json"), compound);

            File[] backups = backupDir.listFiles((dir, n) -> n.endsWith(".json"));
            if (backups != null && backups.length > ProfileConfig.ProfileBackupAmount) {
                Arrays.sort(backups, Comparator.comparingLong(File::lastModified));
                for (int i = 0; i < backups.length - ProfileConfig.ProfileBackupAmount; i++)
                    backups[i].delete();
            }
        } catch (Exception e) {
            LogWriter.error("Error backing up profile", e);
        }
    }

    public boolean rollbackProfile(String username, File backupFile) {
        UUID uuid = resolveUUID(username);
        if (uuid == null)
            return false;
        try {
            CompoundTag compound = NBTJsonUtil.LoadFile(backupFile);
            File saveDir = getProfileDir();
            File main = new File(saveDir, uuid + ".json");
            File fileNew = new File(saveDir, uuid + ".json_new");
            NBTJsonUtil.SaveFile(fileNew, compound);
            if (main.exists()) main.delete();
            fileNew.renameTo(main);

            Player player = getOnlinePlayer(username);
            if (player != null) {
                activeProfiles.put(uuid, new Profile(player, compound));
                loadSlotData(player);
            }
            return true;
        } catch (Exception e) {
            LogWriter.error("Error rolling back profile", e);
            return false;
        }
    }

    // ----------------------------------------------------------------- lifecycle

    public synchronized void login(Player player) {
        if (!ProfileConfig.ProfilesEnabled || player == null)
            return;
        if (activeProfiles.containsKey(player.getUUID())) {
            activeProfiles.get(player.getUUID()).player = player;
            return;
        }

        Profile profile = new Profile(player, load(player.getUUID()));
        if (profile.getSlots().isEmpty()) {
            Slot defaultSlot = new Slot(0, "Default Slot");
            defaultSlot.setLastLoaded(System.currentTimeMillis());
            profile.getSlots().put(0, defaultSlot);
            profile.currentSlotId = 0;
        }
        if (!profile.getSlots().containsKey(profile.currentSlotId))
            profile.currentSlotId = 0;

        profile.player = player;
        activeProfiles.put(player.getUUID(), profile);

        PlayerData pdata = PlayerData.get(player);
        if (profile.getSlots().containsKey(pdata.profileSlot))
            profile.currentSlotId = pdata.profileSlot;

        saveSlotData(player);
        save(player, profile);
    }

    public synchronized void logout(Player player) {
        if (player != null && activeProfiles.containsKey(player.getUUID())) {
            saveSlotData(player);
            save(player, activeProfiles.get(player.getUUID()));
            activeProfiles.remove(player.getUUID());
        }
    }

    // ----------------------------------------------------------------- lookups

    public Profile getProfile(Player player) {
        if (player == null)
            return null;
        if (!activeProfiles.containsKey(player.getUUID()))
            login(player);
        return activeProfiles.get(player.getUUID());
    }

    public Profile getProfile(UUID uuid) {
        if (activeProfiles.containsKey(uuid))
            return activeProfiles.get(uuid);
        return new Profile(null, load(uuid));
    }

    public Profile getProfile(String username) {
        Player player = getOnlinePlayer(username);
        if (player != null)
            return getProfile(player);
        UUID uuid = resolveUUID(username);
        return uuid == null ? null : getProfile(uuid);
    }

    // point 4: BetaZavr resolves names/uuids without a Mojang HTTP call.
    private UUID resolveUUID(String username) {
        try {
            String uuid = PlayerDataController.instance.hasPlayer(username);
            if (uuid != null && !uuid.isEmpty())
                return UUID.fromString(uuid);
        } catch (Exception ignored) {}
        return null;
    }

    private Player getOnlinePlayer(String username) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? null : server.getPlayerList().getPlayerByName(username);
    }

    // ----------------------------------------------------------------- create (point 2)

    public ProfileOperation createSlot(Player player) {
        Profile profile = getProfile(player);
        if (profile == null) return ProfileOperation.error("Player not found.");
        if (profile.isLocked()) return ProfileOperation.locked("Profile is locked; cannot create slot.");
        // point 2: flat limit. TODO: optional registered "profile.max" bypass node.
        if (profile.getSlots().size() >= ProfileConfig.DefaultProfileSlots)
            return ProfileOperation.error("Maximum allowed slots reached.");

        int newSlotId = 0;
        while (profile.getSlots().containsKey(newSlotId)) newSlotId++;

        if (ProfileEvents.onCreate(player, profile, newSlotId))      // point 5
            return ProfileOperation.error("Operation cancelled.");

        Slot slot = new Slot(newSlotId, "Slot " + newSlotId);
        slot.setLastLoaded(System.currentTimeMillis());
        profile.getSlots().put(newSlotId, slot);
        save(player, profile);
        return ProfileOperation.success("New slot created successfully.");
    }

    // ----------------------------------------------------------------- clone (point 7)

    private int getNextTempSlot(Profile profile) {
        int id = -1;
        while (profile.getSlots().containsKey(id)) id--;
        return id;
    }

    public ProfileOperation cloneSlot(Player player, int sourceSlotId, int destinationSlotId, boolean temporary) {
        Profile profile = getProfile(player);
        if (profile == null) return ProfileOperation.error("Player not found.");
        if (profile.isLocked()) return ProfileOperation.locked("Profile is locked; cannot clone slot.");
        if (!profile.getSlots().containsKey(sourceSlotId)) return ProfileOperation.error("Source slot does not exist.");
        if (destinationSlotId == profile.getCurrentSlotId()) return ProfileOperation.error("Cannot clone to the current active slot.");

        if (temporary) {
            destinationSlotId = getNextTempSlot(profile);
        } else if (destinationSlotId <= 0) {
            return ProfileOperation.error("Invalid destination slot id.");
        }

        Slot source = profile.getSlots().get(sourceSlotId);
        Map<String, CompoundTag> newComponents = new HashMap<>();
        for (String key : source.getComponents().keySet())
            newComponents.put(key, source.getComponentData(key).copy());

        Slot cloned = new Slot(destinationSlotId, "Cloned Slot " + destinationSlotId,
            System.currentTimeMillis(), temporary, newComponents);
        profile.getSlots().put(destinationSlotId, cloned);
        save(player, profile);
        return ProfileOperation.success("Slot cloned successfully.");
    }

    // ----------------------------------------------------------------- remove

    public ProfileOperation removeSlot(Player player, int slotId) {
        Profile profile = getProfile(player);
        if (profile == null) return ProfileOperation.error("Player not found.");
        if (profile.isLocked()) return ProfileOperation.locked("Profile is locked; cannot remove slot.");
        if (slotId == profile.getCurrentSlotId()) return ProfileOperation.error("Cannot remove the currently active slot.");
        if (!profile.getSlots().containsKey(slotId)) return ProfileOperation.error("Slot does not exist.");

        if (ProfileEvents.onRemove(player, profile, slotId))         // point 5
            return ProfileOperation.error("Operation cancelled.");

        profile.getSlots().remove(slotId);
        save(player, profile);
        return ProfileOperation.success("Slot removed successfully.");
    }

    /** point 4: offline remove (no live swap needed). */
    public ProfileOperation removeSlot(String username, int slotId) {
        Player player = getOnlinePlayer(username);
        if (player != null) return removeSlot(player, slotId);
        UUID uuid = resolveUUID(username);
        if (uuid == null) return ProfileOperation.error("Player not found.");
        Profile profile = getProfile(uuid);
        if (slotId == profile.getCurrentSlotId()) return ProfileOperation.error("Cannot remove the currently active slot.");
        if (!profile.getSlots().containsKey(slotId)) return ProfileOperation.error("Slot does not exist.");
        profile.getSlots().remove(slotId);
        saveOffline(profile, uuid);
        return ProfileOperation.success("Slot removed successfully.");
    }

    // ----------------------------------------------------------------- change (point 1 region)

    public ProfileOperation changeSlot(Player player, int newSlotId) {
        Profile profile = getProfile(player);
        if (profile == null) return ProfileOperation.error("Player not found.");
        if (profile.isLocked()) return ProfileOperation.locked("Profile is locked; cannot change slot.");
        if (profile.getCurrentSlotId() == newSlotId) return ProfileOperation.error("Slot is already active.");
        if (!profile.getSlots().containsKey(newSlotId)) return ProfileOperation.error("Slot does not exist.");

        // point 1: region-restricted switching (bypass = op, point 2 removed perms).
        if (ProfileConfig.RegionProfileSwitching && !player.hasPermissions(2) && !inAllowedRegion(player))
            return ProfileOperation.error("Profile switching not allowed from your current location.");

        int prevSlot = profile.getCurrentSlotId();
        if (ProfileEvents.onChange(player, profile, newSlotId, prevSlot))   // point 5
            return ProfileOperation.error("Operation cancelled.");

        // Persist the OUTGOING slot by its original id before loading the new one.
        saveSlotData(player, profile, prevSlot);
        loadSlotData(player, profile, newSlotId);
        save(player, profile);
        return ProfileOperation.success("Slot changed successfully.");
    }

    /** point 4: change for an online player resolved by name (switch needs a live player). */
    public ProfileOperation changeSlot(String username, int newSlotId) {
        Player player = getOnlinePlayer(username);
        if (player == null) return ProfileOperation.error("Player must be online to switch.");
        return changeSlot(player, newSlotId);
    }

    // ----------------------------------------------------------------- per-slot swap

    private void saveSlotData(Player player, Profile profile, int slotId) {
        if (player == null || profile == null || profile.isLocked())
            return;
        Slot slot = profile.getSlots().get(slotId);
        if (slot == null) {
            slot = new Slot(slotId, "Slot " + slotId);
            profile.getSlots().put(slotId, slot);
        }
        for (IProfileData pd : profileTypes.values())
            slot.setComponentData(pd.getTagName(), pd.getCurrentNBT(player).copy());
        slot.setLastLoaded(System.currentTimeMillis());
    }

    public void saveSlotData(Player player) {
        if (player == null || !activeProfiles.containsKey(player.getUUID()))
            return;
        Profile profile = activeProfiles.get(player.getUUID());
        saveSlotData(player, profile, profile.getCurrentSlotId());
    }

    private void loadSlotData(Player player, Profile profile, int slotId) {
        if (player == null || profile == null)
            return;
        Slot slot = profile.getSlots().get(slotId);
        if (slot == null)
            return;

        for (IProfileData pd : profileTypes.values()) {
            CompoundTag data = slot.getComponents().containsKey(pd.getTagName())
                ? slot.getComponentData(pd.getTagName()).copy()
                : new CompoundTag();
            pd.setNBT(player, data);
        }
        for (IProfileData pd : profileTypes.values())
            pd.save(player);

        // Flip the active slot only after live data matches the target.
        profile.currentSlotId = slotId;

        PlayerData pdata = PlayerData.get(player);
        pdata.profileSlot = slotId;
        pdata.save(false);
        // REMOVED: AttributeController recalc (set aside).
    }

    public void loadSlotData(Player player) {
        if (player == null || !activeProfiles.containsKey(player.getUUID()))
            return;
        Profile profile = activeProfiles.get(player.getUUID());
        loadSlotData(player, profile, profile.getCurrentSlotId());
    }

    // ----------------------------------------------------------------- slot info (point 8)

    public List<ProfileInfoEntry> getProfileInfo(Player player, int slotId) {
        List<ProfileInfoEntry> infoList = new ArrayList<>();
        Profile profile = getProfile(player);
        if (profile == null)
            return infoList;

        if (slotId == profile.getCurrentSlotId()) {
            for (IProfileData pd : profileTypes.values())
                infoList.addAll(pd.getInfo(player, pd.getCurrentNBT(player)));
        } else if (profile.getSlots().containsKey(slotId)) {
            Slot slot = profile.getSlots().get(slotId);
            for (IProfileData pd : profileTypes.values()) {
                if (slot.getComponents().containsKey(pd.getTagName()))
                    infoList.addAll(pd.getInfo(player, slot.getComponentData(pd.getTagName()).copy()));
            }
        }
        return infoList;
    }

    // ----------------------------------------------------------------- quest sharing (point 6 scaffold)

    /** point 6: propagate shared-quest completion across all slots.
     *  No-op until ProfileQuestSync.sharedQuestIds() is wired on the BetaZavr side. */
    public void verifySlotQuests(Player player) {
        if (ProfileQuestSync.sharedQuestIds().isEmpty())
            return;
        // TODO: implement once PlayerQuestData exposes finishedQuests on 1.20.1.
    }

    public void shareQuestCompletion(Player player, int questId, long completeTime) {
        if (!ProfileQuestSync.sharedQuestIds().contains(questId))
            return;
        // TODO: implement once PlayerQuestData exposes finishedQuests on 1.20.1.
    }

    // ----------------------------------------------------------------- region helper (point 1)

    private boolean inAllowedRegion(Player player) {
        String dim = player.level().dimension().location().toString();
        int x = player.blockPosition().getX();
        int y = player.blockPosition().getY();
        int z = player.blockPosition().getZ();
        for (ProfileConfig.ProfileRegion r : ProfileConfig.RestrictedProfileRegions) {
            if (r.contains(dim, x, y, z))
                return true;
        }
        return false;
    }
}
