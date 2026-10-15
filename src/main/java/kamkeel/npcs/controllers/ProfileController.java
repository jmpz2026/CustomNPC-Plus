package kamkeel.npcs.controllers;

import kamkeel.npcs.controllers.AttributeController;
import kamkeel.npcs.controllers.data.profile.CNPCData;
import kamkeel.npcs.controllers.data.profile.EnumProfileOperation;
import kamkeel.npcs.controllers.data.profile.IProfileData;
import kamkeel.npcs.controllers.data.profile.Profile;
import kamkeel.npcs.controllers.data.profile.ProfileInfoEntry;
import kamkeel.npcs.controllers.data.profile.ProfileOperation;
import kamkeel.npcs.controllers.data.profile.Slot;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import noppes.npcs.CustomNpcs;
import noppes.npcs.CustomNpcsPermissions;
import noppes.npcs.EventHooks;
import noppes.npcs.LogWriter;
import noppes.npcs.NoppesUtilServer;
import noppes.npcs.api.entity.IPlayer;
import noppes.npcs.api.handler.IPlayerData;
import noppes.npcs.api.handler.IProfileHandler;
import noppes.npcs.api.handler.data.IProfile;
import noppes.npcs.api.handler.data.ISlot;
import noppes.npcs.config.ConfigMain;
import noppes.npcs.controllers.PlayerDataController;
import noppes.npcs.controllers.QuestController;
import noppes.npcs.controllers.ScriptController;
import noppes.npcs.controllers.data.PlayerData;
import noppes.npcs.controllers.data.PlayerDataScript;
import noppes.npcs.controllers.data.PlayerQuestData;
import noppes.npcs.scripted.NpcAPI;
import noppes.npcs.util.CustomNPCsThreader;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static noppes.npcs.CustomNpcsPermissions.PROFILE_MAX;
import static noppes.npcs.CustomNpcsPermissions.PROFILE_REGION_BYPASS;
import static noppes.npcs.CustomNpcsPermissions.hasCustomPermission;

public class ProfileController implements IProfileHandler {

    // Message constants
    private static final String MSG_PLAYER_NOT_FOUND = "Player not found.";
    private static final String MSG_PROFILE_LOCKED_CLONE = "Profile is locked; cannot clone slot.";
    private static final String MSG_SOURCE_SLOT_NOT_EXIST = "Source slot does not exist.";
    private static final String MSG_CANNOT_CLONE_CURRENT = "Cannot clone to the current active slot.";
    private static final String MSG_INVALID_DEST_SLOT = "Invalid destination slot id.";
    private static final String MSG_CLONE_SUCCESS = "Slot cloned successfully.";
    private static final String MSG_PROFILE_LOCKED_REMOVE = "Profile is locked; cannot remove slot.";
    private static final String MSG_CANNOT_REMOVE_ACTIVE = "Cannot remove the currently active slot.";
    private static final String MSG_SLOT_NOT_EXIST = "Slot does not exist.";
    private static final String MSG_REMOVE_SUCCESS = "Slot removed successfully.";
    private static final String MSG_PROFILE_LOCKED_CREATE = "Profile is locked; cannot create slot.";
    private static final String MSG_MAX_SLOTS_REACHED = "Maximum allowed slots reached.";
    private static final String MSG_NEW_SLOT_CREATED = "New slot created successfully.";
    private static final String MSG_PROFILE_LOCKED_CHANGE = "Profile is locked; cannot change slot.";
    private static final String MSG_SLOT_ALREADY_ACTIVE = "Slot is already active.";
    private static final String MSG_REGION_NOT_ALLOWED = "Profile switching not allowed from your current location.";
    private static final String MSG_CHANGE_SUCCESS = "Slot changed successfully.";
    private static final String MSG_CANCELLED = "Operation cancelled.";
    private static final String MSG_QUARANTINED = "Profile could not be read and is in quarantine; ask an admin.";

    public static Map<String, IProfileData> profileTypes = new HashMap<>();
    public static Map<UUID, Profile> activeProfiles = new HashMap<>();
    public static String profile_directory = "profiles";

    public static ProfileController Instance;

    /** Last backup written per player; backups are throttled so quick saves don't rotate history away. */
    private static final Map<UUID, Long> lastBackup = new ConcurrentHashMap<>();
    private static final long BACKUP_INTERVAL = 60 * 1000;

    public ProfileController() {
        Instance = this;
        // profileTypes is not reset here: mods that register in FMLInitializationEvent run
        // before this constructor (FMLServerAboutToStartEvent) and would lose their type.
        activeProfiles = new HashMap<>();
    }

    public static boolean registerProfileType(IProfileData type) {
        if (profileTypes.containsKey(type.getTagName()))
            return false;
        profileTypes.put(type.getTagName(), type);
        return true;
    }

    public static File getProfileDir() {
        try {
            File file = new File(CustomNpcs.getWorldSaveDirectory(), profile_directory);
            if (!file.exists())
                file.mkdir();
            return file;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static File getBackupDir() {
        File base = getProfileDir();
        File backup = new File(base, "backup");
        if (!backup.exists()) {
            backup.mkdir();
        }
        return backup;
    }

    public synchronized void login(EntityPlayer player) {
        if (!ConfigMain.ProfilesEnabled)
            return;

        if (player == null)
            return;
        Profile profile = activeProfiles.get(player.getUniqueID());
        // A quarantined profile is read again on every login, so an admin can fix the file
        // and the player picks it up by reconnecting.
        if (profile != null && !profile.isQuarantined()) {
            profile.player = player;
        } else {
            NBTTagCompound compound = readWithRecovery(player.getUniqueID());
            boolean quarantined = compound == null;
            profile = new Profile(player, quarantined ? new NBTTagCompound() : compound);
            profile.setQuarantined(quarantined);

            PlayerData pdata = PlayerData.get(player);
            if (profile.getSlots().isEmpty()) {
                // In quarantine the placeholder takes the slot the player is really on, so the
                // live data keeps saving normally (PlayerData.save skips a mismatched slot).
                int slotId = quarantined ? pdata.profileSlot : 0;
                Slot defaultSlot = new Slot(slotId, quarantined ? "Slot " + slotId : "Default Slot");
                defaultSlot.setLastLoaded(System.currentTimeMillis());
                profile.getSlots().put(slotId, defaultSlot);
                profile.currentSlotId = slotId;
            }
            if (!profile.getSlots().containsKey(profile.currentSlotId)) {
                profile.currentSlotId = 0;
            }

            profile.player = player;
            activeProfiles.put(player.getUniqueID(), profile);

            if (profile.getSlots().containsKey(pdata.profileSlot)) {
                profile.currentSlotId = pdata.profileSlot;
            }

            saveSlotData(player);
            if (quarantined) {
                warnQuarantine(player);
            } else {
                verifySlotQuests(profile.player);
            }
        }
    }

    public synchronized NBTTagCompound load(EntityPlayer player) {
        return load(player.getUniqueID());
    }

    /**
     * Profile file contents, recovered from the pending write or the newest backup if the
     * main file is unreadable. Empty if nothing can be read.
     */
    public synchronized NBTTagCompound load(UUID uuid) {
        NBTTagCompound compound = readWithRecovery(uuid);
        return compound != null ? compound : new NBTTagCompound();
    }

    /**
     * Reads a profile: the main file, then the write that was in progress ({@code .dat_new}),
     * then the newest backup.
     *
     * @return the compound; empty if the player has no profile yet; null if files exist but
     * none of them can be read
     */
    private NBTTagCompound readWithRecovery(UUID uuid) {
        File saveDir = getProfileDir();
        File main = new File(saveDir, uuid + ".dat");
        NBTTagCompound compound = readCompressed(main);
        if (compound != null)
            return compound;

        boolean hadFile = main.exists();
        File pending = new File(saveDir, uuid + ".dat_new");
        compound = readCompressed(pending);
        if (compound != null) {
            LogWriter.error("Profile " + uuid + ": main file unreadable or missing, recovered from " + pending.getName());
            return compound;
        }
        hadFile |= pending.exists();

        File[] backups = listBackups(uuid);
        for (int i = backups.length - 1; i >= 0; i--) {
            compound = readCompressed(backups[i]);
            if (compound != null) {
                LogWriter.error("Profile " + uuid + ": main file unreadable or missing, recovered from backup " + backups[i].getName());
                return compound;
            }
        }
        hadFile |= backups.length > 0;

        if (hadFile) {
            LogWriter.error("Profile " + uuid + ": no readable file nor backup, profile quarantined");
            return null;
        }
        return new NBTTagCompound();
    }

    private static NBTTagCompound readCompressed(File file) {
        if (!file.isFile())
            return null;
        try (FileInputStream in = new FileInputStream(file)) {
            return CompressedStreamTools.readCompressed(in);
        } catch (Exception e) {
            LogWriter.error("Unreadable profile file: " + file.getName(), e);
            return null;
        }
    }

    /** Backups of a player, oldest first. */
    private static File[] listBackups(UUID uuid) {
        File dir = new File(getBackupDir(), uuid.toString());
        File[] backups = dir.listFiles((d, name) -> name.endsWith(".dat"));
        if (backups == null)
            return new File[0];
        Arrays.sort(backups, Comparator.comparingLong(File::lastModified));
        return backups;
    }

    private void warnQuarantine(EntityPlayer player) {
        String message = "§c[Profiles] The profile of " + player.getCommandSenderName()
            + " could not be read and is in quarantine: it is not saved and slots can't be changed. Restore profiles/"
            + player.getUniqueID() + ".dat and have the player reconnect.";
        for (Object o : MinecraftServer.getServer().getConfigurationManager().playerEntityList) {
            EntityPlayer op = (EntityPlayer) o;
            if (CustomNpcsPermissions.hasPermission(op, CustomNpcsPermissions.PROFILE_ADMIN))
                op.addChatMessage(new ChatComponentText(message));
        }
    }

    public synchronized void saveOffline(Profile profile, UUID uuid) {
        if (profile.isQuarantined())
            return;
        profile.setLocked(true);
        final NBTTagCompound compound = profile.writeToNBT();
        CustomNPCsThreader.customNPCThread.execute(() -> {
            try {
                writeProfileFile(uuid, compound);
            } finally {
                profile.setLocked(false);
            }
        });
    }

    public synchronized void save(EntityPlayer player, Profile profile) {
        saveOffline(profile, player.getUniqueID());
    }

    /** Writes the profile on the calling thread. */
    public synchronized void saveNow(EntityPlayer player, Profile profile) {
        if (profile.isQuarantined())
            return;
        writeProfileFile(player.getUniqueID(), profile.writeToNBT());
    }

    /**
     * Writes to {@code .dat_new} and moves it over the main file in one step, so a crash
     * never leaves the player without a readable profile.
     */
    private static void writeProfileFile(UUID uuid, NBTTagCompound compound) {
        try {
            File saveDir = getProfileDir();
            File fileNew = new File(saveDir, uuid + ".dat_new");
            File file = new File(saveDir, uuid + ".dat");
            try (FileOutputStream out = new FileOutputStream(fileNew)) {
                CompressedStreamTools.writeCompressed(compound, out);
            }
            try {
                Files.move(fileNew.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(fileNew.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            if (ConfigMain.AllowProfileBackups)
                backupProfile(uuid, compound);
        } catch (Exception e) {
            LogWriter.error("Error saving profile " + uuid, e);
        }
    }

    private static void backupProfile(UUID uuid, NBTTagCompound compound) {
        long now = System.currentTimeMillis();
        Long last = lastBackup.get(uuid);
        if (last != null && now - last < BACKUP_INTERVAL)
            return;
        lastBackup.put(uuid, now);
        try {
            File backupDir = new File(getBackupDir(), uuid.toString());
            if (!backupDir.exists()) {
                backupDir.mkdirs();
            }
            String dateStr = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date(now));
            File backupFile = new File(backupDir, dateStr + ".dat");
            try (FileOutputStream out = new FileOutputStream(backupFile)) {
                CompressedStreamTools.writeCompressed(compound, out);
            }
            File[] backups = listBackups(uuid);
            for (int i = 0; i < backups.length - ConfigMain.ProfileBackupAmount; i++) {
                backups[i].delete();
            }
        } catch (Exception e) {
            LogWriter.except(e);
        }
    }

    public boolean rollbackProfile(String username, File backupFile) {
        EntityPlayer player = NoppesUtilServer.getPlayerByName(username);
        if (player == null)
            return false;
        UUID uuid = player.getUniqueID();
        NBTTagCompound compound = readCompressed(backupFile);
        if (compound == null)
            return false;
        writeProfileFile(uuid, compound);
        Profile newProfile = new Profile(player, compound);
        activeProfiles.put(uuid, newProfile);
        loadSlotData(player);
        return true;
    }

    public synchronized void logout(EntityPlayer player) {
        if (player != null && activeProfiles.containsKey(player.getUniqueID())) {
            saveSlotData(player);
            Profile profile = activeProfiles.get(player.getUniqueID());
            save(player, profile);
        }
    }

    public Profile getProfile(EntityPlayer player) {
        if (player == null)
            return null;
        if (!activeProfiles.containsKey(player.getUniqueID()))
            login(player);
        Profile profile = activeProfiles.get(player.getUniqueID());
        // Dying (or leaving the End) replaces the EntityPlayerMP, but the profile kept the old
        // one until the next login. A slot change after a death then saved the dead entity's
        // NBT over the current slot and loaded the new slot into the dead entity, and the next
        // logout wrote the old character over the new slot.
        if (profile != null && profile.player != player && !player.worldObj.isRemote)
            profile.player = player;
        return profile;
    }

    public Profile getProfile(UUID uuid) {
        if (activeProfiles.containsKey(uuid))
            return activeProfiles.get(uuid);
        NBTTagCompound compound = readWithRecovery(uuid);
        Profile profile = new Profile(null, compound != null ? compound : new NBTTagCompound());
        profile.setQuarantined(compound == null);
        return profile;
    }

    public Profile getProfile(String username) {
        EntityPlayer player = NoppesUtilServer.getPlayerByName(username);
        if (player != null) {
            return getProfile(player);
        } else {
            UUID uuid = getUUIDFromUsername(username);
            if (uuid == null)
                return null;
            return getProfile(uuid);
        }
    }

    /**
     * UUID of a player by name, resolved locally: the online player, then the name map of
     * {@link PlayerDataController}, then the offline-mode UUID. Mojang is only asked when the
     * server runs in online mode and the name is unknown here.
     */
    public UUID getUUIDFromUsername(String username) {
        if (username == null || username.isEmpty())
            return null;
        EntityPlayer online = NoppesUtilServer.getPlayerByName(username);
        if (online != null)
            return online.getUniqueID();

        if (PlayerDataController.Instance != null) {
            String known = PlayerDataController.Instance.getPlayerUUIDFromName(username);
            if (known != null && !known.isEmpty()) {
                try {
                    return UUID.fromString(known);
                } catch (IllegalArgumentException ignored) {
                    // Malformed entry in the name map: fall through.
                }
            }
        }

        MinecraftServer server = MinecraftServer.getServer();
        if (server != null && !server.isServerInOnlineMode())
            return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
        return getMojangUUID(username);
    }

    private UUID getMojangUUID(String username) {
        try {
            URL url = new URL("https://api.mojang.com/users/profiles/minecraft/" + username);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            con.setConnectTimeout(5000);
            con.setReadTimeout(5000);
            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
            StringBuilder temp = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                temp.append(line);
            }
            in.close();
            con.disconnect();
            String response = temp.toString();
            if (response.contains("\"id\"")) {
                int idIndex = response.indexOf("\"id\"");
                int colonIndex = response.indexOf(":", idIndex);
                int quoteStart = response.indexOf("\"", colonIndex);
                int quoteEnd = response.indexOf("\"", quoteStart + 1);
                String idString = response.substring(quoteStart + 1, quoteEnd);
                idString = idString.replaceFirst(
                    "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)",
                    "$1-$2-$3-$4-$5"
                );
                return UUID.fromString(idString);
            }
        } catch (IOException e) {
            LogWriter.error("Error retrieving UUID for " + username, e);
        }
        return null;
    }

    private int getNextAvailableTempSlot(Profile profile) {
        int id = -1;
        while (profile.getSlots().containsKey(id)) {
            id--;
        }
        return id;
    }

    private ProfileOperation cloneSlotInternal(Profile profile, int sourceSlotId, int destinationSlotId, boolean temporary) {
        if (profile.isQuarantined()) {
            return ProfileOperation.locked(MSG_QUARANTINED);
        }
        if (profile.isLocked()) {
            return ProfileOperation.locked(MSG_PROFILE_LOCKED_CLONE);
        }
        if (profile.player != null) {
            List<IProfileData> dataList = new ArrayList<>(profileTypes.values());
            dataList.sort(Comparator.comparingInt(IProfileData::getSwitchPriority));
            for (IProfileData pd : dataList) {
                if (pd.verifySwitch(profile.player).getResult() != EnumProfileOperation.SUCCESS) {
                    return pd.verifySwitch(profile.player);
                }
            }
        }
        if (!profile.getSlots().containsKey(sourceSlotId)) {
            return ProfileOperation.error(MSG_SOURCE_SLOT_NOT_EXIST);
        }
        if (destinationSlotId == profile.getCurrentSlotId()) {
            return ProfileOperation.error(MSG_CANNOT_CLONE_CURRENT);
        }
        if (temporary) {
            destinationSlotId = getNextAvailableTempSlot(profile);
        } else {
            if (destinationSlotId <= 0) {
                return ProfileOperation.error(MSG_INVALID_DEST_SLOT);
            }
        }
        ISlot sourceSlot = profile.getSlots().get(sourceSlotId);
        Map<String, NBTTagCompound> newComponents = new HashMap<>();
        for (String key : sourceSlot.getComponents().keySet()) {
            newComponents.put(key, (NBTTagCompound) sourceSlot.getComponentData(key).copy());
        }
        Slot clonedSlot = new Slot(destinationSlotId, "Cloned Slot " + destinationSlotId, System.currentTimeMillis(), temporary, newComponents);
        profile.getSlots().put(destinationSlotId, clonedSlot);
        return ProfileOperation.success(MSG_CLONE_SUCCESS);
    }

    private ProfileOperation removeSlotInternal(Profile profile, int slotId) {
        if (profile.isQuarantined()) {
            return ProfileOperation.locked(MSG_QUARANTINED);
        }
        if (profile.isLocked()) {
            return ProfileOperation.locked(MSG_PROFILE_LOCKED_REMOVE);
        }
        if (slotId == profile.getCurrentSlotId()) {
            return ProfileOperation.error(MSG_CANNOT_REMOVE_ACTIVE);
        }
        if (!profile.getSlots().containsKey(slotId)) {
            return ProfileOperation.error(MSG_SLOT_NOT_EXIST);
        }

        if (profile.player != null) {
            PlayerDataScript handler = ScriptController.Instance.getPlayerScripts(profile.player);
            IPlayer scriptPlayer = (IPlayer) NpcAPI.Instance().getIEntity(profile.player);
            if (EventHooks.onProfileRemove(handler, scriptPlayer, profile, slotId, false))
                return ProfileOperation.error(MSG_CANCELLED);
        }

        profile.getSlots().remove(slotId);

        if (profile.player != null) {
            PlayerDataScript handler = ScriptController.Instance.getPlayerScripts(profile.player);
            IPlayer scriptPlayer = (IPlayer) NpcAPI.Instance().getIEntity(profile.player);
            EventHooks.onProfileRemove(handler, scriptPlayer, profile, slotId, true);
        }

        return ProfileOperation.success(MSG_REMOVE_SUCCESS);
    }

    public ProfileOperation createSlotInternal(Profile profile) {
        if (profile.isQuarantined()) {
            return ProfileOperation.locked(MSG_QUARANTINED);
        }
        if (profile.isLocked()) {
            return ProfileOperation.locked(MSG_PROFILE_LOCKED_CREATE);
        }
        int newSlotId = 0;
        while (profile.getSlots().containsKey(newSlotId)) {
            newSlotId++;
        }
        if (!allowSlotPermission(profile.player)) {
            return ProfileOperation.error(MSG_MAX_SLOTS_REACHED);
        }
        if (profile.player != null) {
            List<IProfileData> dataList = new ArrayList<>(profileTypes.values());
            dataList.sort(Comparator.comparingInt(IProfileData::getSwitchPriority));
            for (IProfileData pd : dataList) {
                if (pd.verifySwitch(profile.player).getResult() != EnumProfileOperation.SUCCESS) {
                    return pd.verifySwitch(profile.player);
                }
            }
        }

        if (profile.player != null) {
            PlayerDataScript handler = ScriptController.Instance.getPlayerScripts(profile.player);
            IPlayer scriptPlayer = (IPlayer) NpcAPI.Instance().getIEntity(profile.player);
            if (EventHooks.onProfileCreate(handler, scriptPlayer, profile, newSlotId, false))
                return ProfileOperation.error(MSG_CANCELLED);
        }

        Slot newSlot = new Slot(newSlotId, "Slot " + newSlotId);
        newSlot.setLastLoaded(System.currentTimeMillis());
        profile.getSlots().put(newSlotId, newSlot);
        if (profile.player != null) {
            PlayerDataScript handler = ScriptController.Instance.getPlayerScripts(profile.player);
            verifySlotQuests(profile.player);
            save(profile.player, profile);

            IPlayer scriptPlayer = (IPlayer) NpcAPI.Instance().getIEntity(profile.player);
            EventHooks.onProfileCreate(handler, scriptPlayer, profile, newSlotId, true);
        }
        return ProfileOperation.success(MSG_NEW_SLOT_CREATED);
    }

    private ProfileOperation changeSlotInternal(Profile profile, int newSlotId) {
        if (profile.isQuarantined()) {
            return ProfileOperation.locked(MSG_QUARANTINED);
        }
        if (profile.isLocked()) {
            return ProfileOperation.locked(MSG_PROFILE_LOCKED_CHANGE);
        }
        if (profile.getCurrentSlotId() == newSlotId) {
            return ProfileOperation.error(MSG_SLOT_ALREADY_ACTIVE);
        }
        if (!profile.getSlots().containsKey(newSlotId)) {
            return ProfileOperation.error(MSG_SLOT_NOT_EXIST);
        }
        if (profile.player != null) {
            if (ConfigMain.RegionProfileSwitching) {
                boolean allowed = hasCustomPermission(profile.player, PROFILE_REGION_BYPASS.name);
                if (!allowed) {
                    int playerDim = profile.player.dimension;
                    int playerX = (int) profile.player.posX;
                    int playerY = (int) profile.player.posY;
                    int playerZ = (int) profile.player.posZ;
                    for (List<Integer> region : ConfigMain.RestrictedProfileRegions) {
                        if (region.size() == 7) {
                            int dim = region.get(0);
                            int x1 = Math.min(region.get(1), region.get(4));
                            int y1 = Math.min(region.get(2), region.get(5));
                            int z1 = Math.min(region.get(3), region.get(6));
                            int x2 = Math.max(region.get(1), region.get(4));
                            int y2 = Math.max(region.get(2), region.get(5));
                            int z2 = Math.max(region.get(3), region.get(6));
                            if (playerDim == dim &&
                                playerX >= x1 && playerX <= x2 &&
                                playerY >= y1 && playerY <= y2 &&
                                playerZ >= z1 && playerZ <= z2) {
                                allowed = true;
                                break;
                            }
                        }
                    }
                }
                if (!allowed) {
                    return ProfileOperation.error(MSG_REGION_NOT_ALLOWED);
                }
            }

            List<IProfileData> dataList = new ArrayList<>(profileTypes.values());
            dataList.sort(Comparator.comparingInt(IProfileData::getSwitchPriority));
            for (IProfileData pd : dataList) {
                if (pd.verifySwitch(profile.player).getResult() != EnumProfileOperation.SUCCESS) {
                    return pd.verifySwitch(profile.player);
                }
            }

            int prevSlot = profile.getCurrentSlotId();

            PlayerDataScript handler = ScriptController.Instance.getPlayerScripts(profile.player);
            IPlayer scriptPlayer = (IPlayer) NpcAPI.Instance().getIEntity(profile.player);
            if (EventHooks.onProfileChange(handler, scriptPlayer, profile, newSlotId, prevSlot, false))
                return ProfileOperation.error(MSG_CANCELLED);

            // Persist the outgoing slot using its original id. Previously we relied on
            // profile.currentSlotId inside saveSlotData, but scripts firing during the
            // transition could change the active slot before the save completed and
            // overwrite the destination slot with stale data.
            saveSlotData(profile.player, profile, prevSlot);
            loadSlotData(profile.player, profile, newSlotId);
            persistSwitch(profile.player, profile);

            EventHooks.onProfileChange(handler, scriptPlayer, profile, newSlotId, prevSlot, true);
        } else {
            return ProfileOperation.error(MSG_PLAYER_NOT_FOUND);
        }
        return ProfileOperation.success(MSG_CHANGE_SUCCESS);
    }

    public ProfileOperation cloneSlot(EntityPlayer player, int sourceSlotId, int destinationSlotId, boolean temporary) {
        Profile profile = getProfile(player);
        if (profile == null)
            return ProfileOperation.error(MSG_PLAYER_NOT_FOUND);
        ProfileOperation result = cloneSlotInternal(profile, sourceSlotId, destinationSlotId, temporary);
        if (result.getResult() == EnumProfileOperation.SUCCESS && player != null) {
            save(player, profile);
        }
        return result;
    }

    public ProfileOperation cloneSlot(UUID uuid, int sourceSlotId, int destinationSlotId, boolean temporary) {
        Profile profile = getProfile(uuid);
        if (profile == null)
            return ProfileOperation.error(MSG_PLAYER_NOT_FOUND);
        ProfileOperation result = cloneSlotInternal(profile, sourceSlotId, destinationSlotId, temporary);
        if (result.getResult() == EnumProfileOperation.SUCCESS) {
            if (profile.player != null)
                save(profile.player, profile);
            else
                saveOffline(profile, uuid);
        }
        return result;
    }

    public ProfileOperation cloneSlot(String username, int sourceSlotId, int destinationSlotId, boolean temporary) {
        Profile profile = getProfile(username);
        if (profile == null)
            return ProfileOperation.error(MSG_PLAYER_NOT_FOUND);
        ProfileOperation result = cloneSlotInternal(profile, sourceSlotId, destinationSlotId, temporary);
        if (result.getResult() == EnumProfileOperation.SUCCESS) {
            if (profile.player != null)
                save(profile.player, profile);
            else {
                UUID uuid = getUUIDFromUsername(username);
                if (uuid != null)
                    saveOffline(profile, uuid);
            }
        }
        return result;
    }

    public ProfileOperation removeSlot(EntityPlayer player, int slotId) {
        Profile profile = getProfile(player);
        if (profile == null)
            return ProfileOperation.error(MSG_PLAYER_NOT_FOUND);
        ProfileOperation result = removeSlotInternal(profile, slotId);
        if (result.getResult() == EnumProfileOperation.SUCCESS && player != null) {
            save(player, profile);
        }
        return result;
    }

    public ProfileOperation removeSlot(String username, int slotId) {
        Profile profile = getProfile(username);
        if (profile == null)
            return ProfileOperation.error(MSG_PLAYER_NOT_FOUND);
        ProfileOperation result = removeSlotInternal(profile, slotId);
        if (result.getResult() == EnumProfileOperation.SUCCESS) {
            if (profile.player != null)
                save(profile.player, profile);
            else {
                UUID uuid = getUUIDFromUsername(username);
                if (uuid != null)
                    saveOffline(profile, uuid);
            }
        }
        return result;
    }

    public ProfileOperation changeSlot(EntityPlayer player, int newSlotId) {
        Profile profile = getProfile(player);
        if (profile == null)
            return ProfileOperation.error(MSG_PLAYER_NOT_FOUND);
        ProfileOperation result = changeSlotInternal(profile, newSlotId);
        if (result.getResult() == EnumProfileOperation.SUCCESS && player != null)
            save(player, profile);
        return result;
    }

    public ProfileOperation changeSlot(String username, int newSlotId) {
        Profile profile = getProfile(username);
        if (profile == null)
            return ProfileOperation.error(MSG_PLAYER_NOT_FOUND);
        ProfileOperation result = changeSlotInternal(profile, newSlotId);
        if (result.getResult() == EnumProfileOperation.SUCCESS) {
            if (profile.player != null)
                save(profile.player, profile);
            else {
                UUID uuid = getUUIDFromUsername(username);
                if (uuid != null)
                    saveOffline(profile, uuid);
            }
        }
        return result;
    }

    private void saveSlotData(EntityPlayer player, Profile profile, int slotId) {
        if (player == null || profile == null)
            return;
        if (profile.isLocked()) {
            return;
        }
        ISlot slot = profile.getSlots().get(slotId);
        if (slot == null) {
            slot = new Slot(slotId, "Slot " + slotId);
            profile.getSlots().put(slotId, slot);
        }
        for (IProfileData profileData : profileTypes.values()) {
            NBTTagCompound cloned = (NBTTagCompound) profileData.getCurrentNBT(player).copy();
            slot.setComponentData(profileData.getTagName(), cloned);
        }
        slot.setLastLoaded(System.currentTimeMillis());
    }

    public void saveSlotData(EntityPlayer player) {
        if (player == null || !activeProfiles.containsKey(player.getUniqueID()))
            return;
        Profile profile = activeProfiles.get(player.getUniqueID());
        saveSlotData(player, profile, profile.getCurrentSlotId());
    }

    private void loadSlotData(EntityPlayer player, Profile profile, int slotId) {
        if (player == null || profile == null)
            return;

        ISlot slot = profile.getSlots().get(slotId);
        if (slot == null)
            return;

        for (IProfileData profileData : profileTypes.values()) {
            NBTTagCompound data;
            if (slot.getComponents().containsKey(profileData.getTagName()))
                data = (NBTTagCompound) slot.getComponentData(profileData.getTagName()).copy();
            else
                data = new NBTTagCompound();
            profileData.setNBT(player, data);
        }
        for (IProfileData profileData : profileTypes.values()) {
            profileData.save(player);
        }

        // Only flip the active slot after the player's live data matches the
        // requested slot. This way, any additional saveSlotData() calls triggered by
        // scripts during the load can't persist stale information to the new slot.
        profile.currentSlotId = slotId;

        PlayerData pdata = PlayerData.get(player);
        pdata.profileSlot = slotId;
        pdata.save();

        if (ConfigMain.AttributesEnabled)
            AttributeController.getTracker(player).recalcAttributes(player);
    }

    public void loadSlotData(EntityPlayer player) {
        if (player == null || !activeProfiles.containsKey(player.getUniqueID()))
            return;
        Profile profile = activeProfiles.get(player.getUniqueID());
        // Delay flipping profile.currentSlotId until after the data for the target slot
        // has been restored to the player so other saves observe consistent state.
        loadSlotData(player, profile, profile.getCurrentSlotId());
    }

    public List<ProfileInfoEntry> getProfileInfo(EntityPlayer player, int slotId) {
        List<ProfileInfoEntry> infoList = new ArrayList<>();
        Profile profile = getProfile(player);
        if (profile == null)
            return infoList;

        List<IProfileData> dataList = new ArrayList<>(profileTypes.values());
        dataList.sort(Comparator.comparingInt(IProfileData::getSwitchPriority));
        if (slotId == profile.getCurrentSlotId()) {
            for (IProfileData pd : dataList) {
                NBTTagCompound currentNBT = pd.getCurrentNBT(player);
                List<ProfileInfoEntry> subInfo = pd.getInfo(player, currentNBT);
                infoList.addAll(subInfo);
            }
        } else {
            if (!profile.getSlots().containsKey(slotId))
                return infoList;
            ISlot slot = profile.getSlots().get(slotId);
            for (IProfileData pd : dataList) {
                if (slot.getComponents().containsKey(pd.getTagName())) {
                    NBTTagCompound sub = (NBTTagCompound) slot.getComponentData(pd.getTagName()).copy();
                    List<ProfileInfoEntry> subInfo = pd.getInfo(player, sub);
                    infoList.addAll(subInfo);
                }
            }
        }
        return infoList;
    }

    public boolean allowSlotPermission(EntityPlayer player) {
        Profile profile = getProfile(player);
        int currentSlots = profile.getSlots().size();
        if (CustomNpcsPermissions.hasCustomPermission(player, PROFILE_MAX.name)) {
            return true;
        }
        int highestAllowed = 0;
        for (int i = 1; i <= 50; i++) {
            String perm = "customnpcs.profile.max." + i;
            if (CustomNpcsPermissions.hasCustomPermission(player, perm)) {
                highestAllowed = i;
            }
        }
        if (highestAllowed == 0 || highestAllowed < ConfigMain.DefaultProfileSlots) {
            highestAllowed = ConfigMain.DefaultProfileSlots;
        }
        return currentSlots < highestAllowed;
    }

    @Override
    public IProfile getProfile(IPlayer player) {
        if (player == null || player.getMCEntity() == null)
            return null;
        return getProfile((EntityPlayer) player.getMCEntity());
    }

    @Override
    public boolean changeSlot(IPlayer player, int slotID) {
        if (player == null || player.getMCEntity() == null)
            return false;

        EntityPlayer entityPlayer = (EntityPlayer) player.getMCEntity();
        ProfileOperation profileOperation = changeSlot(entityPlayer, slotID);
        return profileOperation.getResult() == EnumProfileOperation.SUCCESS;
    }

    @Override
    public boolean hasSlot(IPlayer player, int slotID) {
        if (player == null || player.getMCEntity() == null)
            return false;

        EntityPlayer entityPlayer = (EntityPlayer) player.getMCEntity();
        Profile profile = getProfile(entityPlayer);
        if (profile == null)
            return false;
        return profile.getSlots().containsKey(slotID);
    }

    @Override
    public boolean removeSlot(IPlayer player, int slotID) {
        if (player == null || player.getMCEntity() == null)
            return false;

        EntityPlayer entityPlayer = (EntityPlayer) player.getMCEntity();
        ProfileOperation profileOperation = removeSlot(entityPlayer, slotID);
        return profileOperation.getResult() == EnumProfileOperation.SUCCESS;
    }

    @Override
    public IPlayerData getSlotPlayerData(IPlayer player, int slotID) {
        PlayerData playerData;
        if (player == null || player.getMCEntity() == null)
            return null;

        EntityPlayer entityPlayer = (EntityPlayer) player.getMCEntity();
        Profile profile = getProfile(entityPlayer);
        if (profile == null)
            return null;
        return getSlotPlayerData(entityPlayer, slotID);
    }

    @Override
    public void saveSlotData(IPlayer player) {
        if (player == null || player.getMCEntity() == null)
            return;

        EntityPlayer entityPlayer = (EntityPlayer) player.getMCEntity();
        saveSlotData(entityPlayer);
    }

    public IPlayerData getSlotPlayerData(EntityPlayer player, int slotID) {
        PlayerData playerData;
        Profile profile = getProfile(player);
        if (profile == null)
            return null;

        if (profile.currentSlotId == slotID) {
            playerData = PlayerData.get(player);
        } else {
            Slot slot = (Slot) profile.getSlots().get(slotID);
            playerData = getSlotPlayerData(player, slot);
        }
        return playerData;
    }

    public PlayerData getSlotPlayerData(EntityPlayer player, Slot slot) {
        if (slot == null)
            return null;

        PlayerData playerData = new PlayerData();
        playerData.player = player;

        NBTTagCompound compound = slot.getComponentData(new CNPCData().getTagName());
        if (compound == null)
            compound = new NBTTagCompound();
        else
            compound = (NBTTagCompound) compound.copy();
        playerData.setNBT(compound);
        return playerData;
    }

    public void verifySlotQuests(EntityPlayer player) {
        Profile profile = getProfile(player);
        if (profile == null) {
            return;
        }

        Map<Integer, Long> universalFinished = new HashMap<>();
        for (Integer questId : QuestController.Instance.sharedQuests.keySet()) {
            long maxTime = 0;
            for (ISlot slot : profile.getSlots().values()) {
                IPlayerData data = getSlotPlayerData(player, slot.getId());
                if (data == null) {
                    continue;
                }
                PlayerQuestData questData = (PlayerQuestData) data.getQuestData();
                Long t = questData.finishedQuests.get(questId);
                if (t != null && t > maxTime) {
                    maxTime = t;
                }
            }
            if (maxTime > 0) {
                universalFinished.put(questId, maxTime);
            }
        }

        // Push the universal shared completion times into every slot.
        for (ISlot slot : profile.getSlots().values()) {
            IPlayerData data = getSlotPlayerData(player, slot.getId());
            if (data != null) {
                PlayerData playerData = (PlayerData) data;
                PlayerQuestData questData = (PlayerQuestData) data.getQuestData();
                questData.finishedQuests.putAll(universalFinished);
                slot.setComponentData(new CNPCData().getTagName(), playerData.getNBT());
            }
        }
    }

    public void shareQuestCompletion(EntityPlayer player, int questId, long completeTime) {
        Profile profile = getProfile(player);
        if (profile == null) {
            return;
        }
        // The caller already updated the live data of the active slot; the other slots get
        // the date written straight into their stored NBT.
        String tag = new CNPCData().getTagName();
        for (ISlot slot : profile.getSlots().values()) {
            if (slot.getId() == profile.getCurrentSlotId())
                continue;
            NBTTagCompound stored = slot.getComponentData(tag);
            // A slot never played has no data yet; canQuestBeAccepted still sees the
            // completion through getLatestQuestCompletion.
            if (stored == null)
                continue;
            // Copy before editing: an async profile save may be serializing the stored compound.
            NBTTagCompound copy = (NBTTagCompound) stored.copy();
            if (setQuestCompletion(copy, questId, completeTime))
                slot.setComponentData(tag, copy);
        }
        save(player, profile);
    }

    /**
     * Most recent completion of a quest across every slot of the player, the active one
     * included. Null if no slot finished it.
     */
    public Long getLatestQuestCompletion(EntityPlayer player, int questId) {
        Profile profile = getProfile(player);
        if (profile == null)
            return null;
        Long latest = PlayerData.get(player).questData.finishedQuests.get(questId);
        String tag = new CNPCData().getTagName();
        for (ISlot slot : profile.getSlots().values()) {
            if (slot.getId() == profile.getCurrentSlotId())
                continue;
            Long time = getQuestCompletion(slot.getComponentData(tag), questId);
            if (time != null && (latest == null || time > latest))
                latest = time;
        }
        return latest;
    }

    private static Long getQuestCompletion(NBTTagCompound cnpc, int questId) {
        if (cnpc == null)
            return null;
        NBTTagList list = cnpc.getCompoundTag("QuestData").getTagList("CompletedQuests", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            if (entry.getInteger("Quest") == questId)
                return entry.getLong("Date");
        }
        return null;
    }

    /** Same layout as PlayerQuestData.saveNBTData. Returns false if the slot already had a later date. */
    private static boolean setQuestCompletion(NBTTagCompound cnpc, int questId, long time) {
        NBTTagCompound questData = cnpc.getCompoundTag("QuestData");
        NBTTagList list = questData.getTagList("CompletedQuests", 10);
        boolean found = false;
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            if (entry.getInteger("Quest") == questId) {
                if (entry.getLong("Date") >= time)
                    return false;
                entry.setLong("Date", time);
                found = true;
                break;
            }
        }
        if (!found) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setInteger("Quest", questId);
            entry.setLong("Date", time);
            list.appendTag(entry);
        }
        questData.setTag("CompletedQuests", list);
        cnpc.setTag("QuestData", questData);
        return true;
    }

    /**
     * Writes everything a slot change touched right away, in this order: the vanilla player
     * file (holds the DBC data in PlayerPersisted), the CustomNPC+ player file and the
     * profile. Left to the autosave, a crash in between would pair one slot's DBC character
     * with another slot's data.
     */
    private void persistSwitch(EntityPlayer player, Profile profile) {
        if (player instanceof EntityPlayerMP) {
            try {
                MinecraftServer server = MinecraftServer.getServer();
                if (server != null && server.worldServers != null && server.worldServers.length > 0)
                    server.worldServers[0].getSaveHandler().getSaveHandler().writePlayerData(player);
            } catch (Exception e) {
                LogWriter.error("Error writing player file of " + player.getCommandSenderName() + " after slot change", e);
            }
        }
        PlayerData.get(player).saveNow();
        saveNow(player, profile);
    }
}
