package noppes.npcs.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import noppes.npcs.client.controllers.MusicController;
import noppes.npcs.client.gui.customoverlay.OverlayCustom;
import noppes.npcs.client.gui.hud.ClientHudManager;
import noppes.npcs.constants.EnumDialogStyle;
import noppes.npcs.client.gui.hud.EnumHudComponent;
import noppes.npcs.client.gui.hud.HudComponent;
import noppes.npcs.client.gui.select.GuiSoundSelection;
import noppes.npcs.client.gui.select.GuiTextureSelection;
import noppes.npcs.client.renderer.ImageData;
import noppes.npcs.controllers.data.AnimationData;
import noppes.npcs.controllers.data.Party;
import noppes.npcs.controllers.data.PlayerData;
import noppes.npcs.controllers.data.SkinOverlay;
import noppes.npcs.entity.EntityNPCInterface;
import kamkeel.npcs.network.enums.EnumSyncType;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ClientCacheHandler {
    // Lives until the player leaves the world: expiring entries never freed their GL texture and made the next use download it again
    private static final HashMap<String, ImageData> imageDataCache = new HashMap<>();
    public static PlayerData playerData = new PlayerData();
    public static HashMap<Integer, OverlayCustom> customOverlays = new HashMap<>();
    public static HashMap<UUID, HashMap<Integer, SkinOverlay>> skinOverlays = new HashMap<>();
    public static HashMap<UUID, AnimationData> playerAnimations = new HashMap<>();

    private static String activeServerKey = "";
    private static String lastServerKey = "";
    private static final Map<String, EnumMap<EnumSyncType, Integer>> clientRevisionCache = new HashMap<>();

    public static Party party;

    public static boolean allowProfiles = true;
    public static boolean allowParties = true;
    // Style the server forces on every player, or null if each player picks
    public static EnumDialogStyle forcedDialogStyle = null;

    public static void setActiveServer(String serverKey, EnumMap<EnumSyncType, Integer> serverRevisions) {
        String normalizedKey = serverKey == null ? "" : serverKey;
        String previousKey = activeServerKey == null ? "" : activeServerKey;
        lastServerKey = previousKey;

        if (!normalizedKey.equals(activeServerKey)) {
            clientRevisionCache.clear();
        }

        activeServerKey = normalizedKey;

        if (activeServerKey.isEmpty()) {
            return;
        }

        EnumMap<EnumSyncType, Integer> cached = clientRevisionCache.computeIfAbsent(
            activeServerKey,
            ignored -> new EnumMap<>(EnumSyncType.class)
        );
        if (serverRevisions != null && !serverRevisions.isEmpty()) {
            cached.keySet().retainAll(serverRevisions.keySet());
        }
    }

    public static String getLastServerKey() {
        return lastServerKey == null ? "" : lastServerKey;
    }

    public static EnumMap<EnumSyncType, Integer> getCachedRevisionsForServer(String serverKey) {
        String key = serverKey == null ? "" : serverKey;
        if (key.isEmpty()) {
            return new EnumMap<>(EnumSyncType.class);
        }
        EnumMap<EnumSyncType, Integer> revisions = clientRevisionCache.get(key);
        if (revisions == null) {
            return new EnumMap<>(EnumSyncType.class);
        }
        return new EnumMap<>(revisions);
    }

    public static void updateClientRevision(EnumSyncType type, int revision) {
        if (revision < 0) {
            return;
        }
        String key = activeServerKey == null ? "" : activeServerKey;
        if (key.isEmpty()) {
            return;
        }
        EnumMap<EnumSyncType, Integer> revisions = clientRevisionCache.computeIfAbsent(
            key,
            ignored -> new EnumMap<>(EnumSyncType.class)
        );
        revisions.put(type, revision);
    }


    public static ImageData getImageData(String directory) {
        synchronized (imageDataCache) {
            ImageData data = imageDataCache.get(directory);
            if (data == null) {
                data = new ImageData(directory);
                imageDataCache.put(directory, data);
            }
            return data;
        }
    }

    public static ImageData getNPCTexture(String directory, boolean x64, ResourceLocation resource) {
        synchronized (imageDataCache) {
            ImageData data = imageDataCache.get(resource.getResourcePath());
            if (data == null) {
                data = new ImageData(directory, x64, resource);
                imageDataCache.put(resource.getResourcePath(), data);
            }
            return data;
        }
    }

    public static boolean isCachedNPC(ResourceLocation resource) {
        synchronized (imageDataCache) {
            return imageDataCache.containsKey(resource.getResourcePath());
        }
    }

    private static void clearImageData() {
        synchronized (imageDataCache) {
            for (ImageData data : imageDataCache.values()) {
                data.release();
            }
            imageDataCache.clear();
        }
    }

    /**
     * Frees the GL texture and drops it from the TextureManager. Its own deleteTexture keeps the map entry,
     * so anything bound to that location afterwards would use a deleted id.
     */
    public static void releaseTexture(ResourceLocation location) {
        TextureManager textureManager = Minecraft.getMinecraft().getTextureManager();
        ITextureObject texture = textureManager.getTexture(location);
        if (texture != null) {
            TextureUtil.deleteTexture(texture.getGlTextureId());
            textureManager.mapTextureObjects.remove(location);
        }
    }

    // NPCs keep the location of their skin, so after freeing it they must look it up again
    private static void resetNpcTextures() {
        World world = Minecraft.getMinecraft().theWorld;
        if (world == null)
            return;
        for (Object entity : world.loadedEntityList) {
            if (entity instanceof EntityNPCInterface)
                ((EntityNPCInterface) entity).textureLocation = null;
        }
    }

    public static void clearCache() {
        clearImageData();
        ClientCacheHandler.customOverlays.clear();
        ClientCacheHandler.skinOverlays.clear();
        ClientCacheHandler.playerAnimations.clear();

        // Clear Texture Caches
        GuiSoundSelection.cachedDomains.clear();
        GuiTextureSelection.cachedTextures.clear();

        // Clear music/bard sounds
        MusicController.Instance.stopAllSounds();

        // Clear Quest Tracker
        HudComponent component = ClientHudManager.getInstance().getHudComponents().get(EnumHudComponent.QuestTracker);
        if (component != null) {
            component.loadData(new NBTTagCompound());
            component.hasData = false;
        }
    }

    public static void clearSkinCache() {
        clearImageData();
        resetNpcTextures();
        ClientCacheHandler.customOverlays.clear();
        ClientCacheHandler.skinOverlays.clear();

        // Clear Texture Caches
        GuiSoundSelection.cachedDomains.clear();
        GuiTextureSelection.cachedTextures.clear();
    }
}
