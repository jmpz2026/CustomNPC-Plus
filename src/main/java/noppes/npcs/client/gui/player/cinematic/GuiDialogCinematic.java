package noppes.npcs.client.gui.player.cinematic;

import kamkeel.npcs.network.PacketClient;
import kamkeel.npcs.network.packets.player.CheckPlayerValue;
import kamkeel.npcs.network.packets.player.DialogSelectPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import noppes.npcs.NoppesStringUtils;
import noppes.npcs.api.handler.data.IDialogImage;
import noppes.npcs.api.handler.data.IQuestObjective;
import noppes.npcs.client.ClientProxy;
import noppes.npcs.client.NoppesUtil;
import noppes.npcs.client.controllers.MusicController;
import noppes.npcs.client.gui.player.GuiDialogImage;
import noppes.npcs.client.gui.player.IDialogScreen;
import noppes.npcs.client.gui.util.GuiNPCInterface;
import noppes.npcs.client.gui.util.IGuiClose;
import noppes.npcs.config.ConfigClient;
import noppes.npcs.constants.EnumDialogStyle;
import noppes.npcs.constants.EnumOptionType;
import noppes.npcs.controllers.data.Dialog;
import noppes.npcs.controllers.data.DialogImage;
import noppes.npcs.controllers.data.DialogOption;
import noppes.npcs.controllers.data.PlayerData;
import noppes.npcs.controllers.data.Quest;
import noppes.npcs.controllers.data.QuestData;
import noppes.npcs.entity.EntityNPCInterface;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The cinematic dialog style: frameless bar at the bottom, options with icons on the
 * right, automatic pages for long texts, radial wheel for showWheel dialogs, quest card
 * and history. Uses its own scale instead of the GUI scale.
 *
 * Packets are the same as the classic screen: DialogSelectPacket with the slot, -1 to
 * close, and CheckQuestCompletion when it closes.
 */
public class GuiDialogCinematic extends GuiNPCInterface implements IGuiClose, IDialogScreen {
    private final CinematicView view = new CinematicView();
    private final HashMap<Integer, GuiDialogImage> dialogImages = new HashMap<Integer, GuiDialogImage>();
    private GuiScreen parent;
    private Dialog dialog;
    private boolean sentClosePacket = false;
    private String dialogSound = null;

    public GuiDialogCinematic(EntityNPCInterface npc, Dialog dialog) {
        super(npc);
        view.textSound = new CinematicView.TextSound() {
            @Override
            public void play() {
                playTextSound();
            }
        };
        appendDialog(dialog);
    }

    public GuiDialogCinematic(GuiScreen parent, EntityNPCInterface npc, Dialog dialog) {
        this(npc, dialog);
        this.parent = parent;
    }

    /**
     * Scale of the style: auto picks the largest one that keeps at least 320x240 logical
     * units, a fixed value is capped to that so the bar always fits.
     */
    public static int cinematicScale(Minecraft mc) {
        int auto = 1;
        while (mc.displayWidth / (auto + 1) >= 320 && mc.displayHeight / (auto + 1) >= 240)
            auto++;
        int fixed = ConfigClient.DialogScale;
        return fixed <= 0 ? auto : Math.min(fixed, auto);
    }

    @Override
    public void initGui() {
        super.initGui();
        drawDefaultBackground = false;
        Minecraft.getMinecraft().mouseHelper.ungrabMouseCursor();
        Keyboard.enableRepeatEvents(true);
    }

    @Override
    public EnumDialogStyle getDialogStyle() {
        return EnumDialogStyle.NEW;
    }

    @Override
    public void appendDialog(Dialog dialog) {
        closeOnEsc = !dialog.disableEsc;
        sentClosePacket = false;
        dialogImages.clear();
        this.dialog = dialog;

        if (dialog.sound != null && !dialog.sound.isEmpty()) {
            // Stop any previous dialog sound before playing the new one
            if (dialogSound != null)
                MusicController.Instance.stopSound(dialogSound);
            MusicController.Instance.stopMusic();
            MusicController.Instance.playSound(dialog.sound, (float) npc.posX, (float) npc.posY, (float) npc.posZ);
            dialogSound = dialog.sound;
        }

        List<CinematicView.Option> options = new ArrayList<CinematicView.Option>();
        for (Map.Entry<Integer, DialogOption> e : new TreeMap<Integer, DialogOption>(dialog.options).entrySet()) {
            DialogOption option = e.getValue();
            if (option == null || option.optionType == EnumOptionType.Disabled)
                continue;
            options.add(new CinematicView.Option(e.getKey(), NoppesStringUtils.formatText(option.title, player, npc),
                option.optionColor, DialogIcons.iconFor(option, npc.advanced.role)));
        }

        view.gradual = dialog.renderGradual && !ConfigClient.DialogInstantText;
        view.speed = ConfigClient.DialogSpeed;
        view.escAllowed = closeOnEsc;
        view.historyKeyName = ClientProxy.DialogHistoryKey == null ? "H" : Keyboard.getKeyName(ClientProxy.DialogHistoryKey.getKeyCode());
        view.setDialog(npc.getCommandSenderName(), dialog.titleColor, dialog.color,
            NoppesStringUtils.formatText(dialog.text, player, npc), options, dialog.showWheel, questInfo(dialog));
    }

    private CinematicView.QuestInfo questInfo(Dialog dialog) {
        Quest quest = dialog.getQuest();
        if (quest == null)
            return null;
        CinematicView.QuestInfo info = new CinematicView.QuestInfo();
        info.title = quest.getName();
        info.xp = quest.rewardExp;
        // The objectives read the player's quest data, so the quest goes in for a moment if
        // the server has not synced it yet (same trick as the modern quest screen)
        Map<Integer, QuestData> active = PlayerData.get(player).questData.activeQuests;
        boolean had = active.containsKey(quest.id);
        if (!had)
            active.put(quest.id, new QuestData(quest));
        try {
            for (IQuestObjective objective : quest.questInterface.getObjectives(player)) {
                if (objective != null)
                    info.objectives.add(objective.getText());
            }
        } finally {
            if (!had)
                active.remove(quest.id);
        }
        for (int i = 0; i < quest.rewardItems.getSizeInventory(); i++) {
            ItemStack stack = quest.rewardItems.getStackInSlot(i);
            if (stack != null)
                info.rewards.add(stack);
        }
        return info;
    }

    private void playTextSound() {
        if (!ConfigClient.DialogSound || dialog.textSound == null || dialog.textSound.isEmpty())
            return;
        mc.getSoundHandler().playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation(dialog.textSound), dialog.textPitch));
    }

    // ================================================================ drawing

    private float guiToLogical() {
        ScaledResolution sr = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        return sr.getScaleFactor() / (float) cinematicScale(mc);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        int s = cinematicScale(mc);
        int W = mc.displayWidth / s, H = mc.displayHeight / s;
        float k = guiToLogical();

        if (dialog.darkenScreen)
            drawGradientRect(0, 0, width, height, 0x40000000, 0x40000000);
        drawScreenImages();

        GL11.glPushMatrix();
        GL11.glScalef(1 / k, 1 / k, 1);
        // set before any layout: text widths depend on it
        CinematicDraw.pixelsPerUnit = s;
        if (!dialog.hideNPC) {
            float[] p = view.npcPlacement(W, H);
            drawNpc(p[0], p[1], p[2]);
        }
        GL11.glTranslatef(0, 0, 300);
        view.draw(W, H, mouseX * k, mouseY * k);
        CinematicDraw.pixelsPerUnit = 2;
        GL11.glPopMatrix();
    }

    /**
     * Images anchored to the screen (type 0), placed exactly as in the other styles.
     */
    private void drawScreenImages() {
        ScaledResolution sr = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        for (IDialogImage dialogImage : dialog.dialogImages.values()) {
            GuiDialogImage image = dialogImages.get(dialogImage.getId());
            if (image == null) {
                image = new GuiDialogImage((DialogImage) dialogImage);
                dialogImages.put(dialogImage.getId(), image);
            }
            GL11.glPushMatrix();
            GL11.glTranslatef(0, 0.5f, 100.065f);
            GL11.glEnable(GL11.GL_BLEND);
            OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glTranslatef(image.x, image.y, 0.0f);
            if (dialogImage.getImageType() == 0) {
                GL11.glTranslatef(image.alignment % 3 * (sr.getScaledWidth() / 2f), (float) (Math.floor(image.alignment / 3f) * (sr.getScaledHeight() / 2f)), 0.0F);
            } else {
                // Images tied to the text sit just above the dialog bar
                GL11.glTranslatef(width / 2f - 180, height * 0.62f - image.height * image.scale, 0.0F);
            }
            image.onRender(mc);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glPopMatrix();
        }
    }

    private void drawNpc(float feetX, float feetY, float height) {
        EntityNPCInterface entity = npc;
        float f2 = entity.renderYawOffset, f3 = entity.rotationYaw, f4 = entity.rotationPitch;
        float f5 = entity.prevRotationYawHead, f6 = entity.rotationYawHead;
        int rotation = -20;
        entity.renderYawOffset = 0.0F;
        entity.rotationYaw = (float) Math.atan(feetX / 80.0F) * 40.0F + rotation;
        entity.rotationPitch = 0;
        entity.rotationYawHead = 0.0F;
        entity.prevRotationYawHead = 0.0F;
        int orientation = npc.ais.orientation;
        npc.ais.orientation = rotation;
        int visibleName = npc.display.showName;
        npc.display.showName = 1;

        // Sized against a standard 1.8 block player, not the hitbox: a child NPC stays smaller
        float k = height / 1.8f * dialog.npcScale;
        GL11.glPushMatrix();
        // Same transform as the other styles: z flipped, then the entity turned to face the camera
        GL11.glTranslatef(feetX, feetY, 50.0F);
        GL11.glScalef(k, k, -k);
        GL11.glRotatef(180.0F, 0, 1, 0);
        GL11.glRotatef(180.0F, 0, 0, 1);
        GL11.glRotatef(-rotation, 0, 1, 0);
        RenderHelper.enableStandardItemLighting();
        RenderManager.instance.playerViewY = 180F;
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        RenderManager.instance.renderEntityWithPosYaw(npc, 0.0, 0.0, 0.0, 0.0F, 1.0F);
        GL11.glPopMatrix();

        entity.renderYawOffset = f2;
        entity.rotationYaw = f3;
        entity.rotationPitch = f4;
        entity.prevRotationYawHead = f5;
        entity.rotationYawHead = f6;
        npc.ais.orientation = orientation;
        npc.display.showName = visibleName;
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    @Override
    public void drawWorldBackground(int tint) {
    }

    // ================================================================ input

    @Override
    public void keyTyped(char c, int key) {
        if (closeOnEsc && (key == Keyboard.KEY_ESCAPE || isInventoryKey(key))) {
            if (view.isHistoryOpen() && key == Keyboard.KEY_ESCAPE) {
                view.toggleHistory();
                return;
            }
            exit();
            return;
        }
        if (ClientProxy.DialogHistoryKey != null && key == ClientProxy.DialogHistoryKey.getKeyCode()) {
            view.toggleHistory();
            return;
        }
        handle(view.key(key, isShiftKeyDown()));
    }

    @Override
    public void mouseClicked(int x, int y, int button) {
        if (button != 0)
            return;
        float k = guiToLogical();
        int hint = view.hintAt(x * k, y * k);
        if (hint == 0) {
            view.toggleHistory();
            return;
        }
        if (hint == 1 && closeOnEsc) {
            exit();
            return;
        }
        handle(view.click(x * k, y * k, isShiftKeyDown()));
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0)
            view.mouseWheel(wheel > 0 ? 1 : -1);
    }

    private void handle(CinematicView.Action action) {
        if (action == CinematicView.Action.FINISH) {
            PacketClient.sendClient(new DialogSelectPacket(dialog.id, -1));
            closed();
            close();
        } else if (action == CinematicView.Action.CHOOSE) {
            choose(view.chosenSlot());
        }
    }

    private void choose(int slot) {
        PacketClient.sendClient(new DialogSelectPacket(dialog.id, slot));
        DialogOption option = dialog.options.get(slot);
        if (!dialog.hasOtherOptions() || option == null || option.optionType != EnumOptionType.DialogOption) {
            closed();
            close();
            return;
        }
        // The server answers with the next dialog, which arrives through appendDialog
        view.setWaiting(NoppesStringUtils.formatText(option.title, player, npc));
        NoppesUtil.clickSound();
    }

    private void exit() {
        PacketClient.sendClient(new DialogSelectPacket(dialog.id, -1));
        closed();
        close();
    }

    private void closed() {
        sentClosePacket = true;
        PacketClient.sendClient(new CheckPlayerValue(CheckPlayerValue.Type.CheckQuestCompletion));
    }

    @Override
    public void save() {
    }

    @Override
    public void close() {
        super.close();
        if (parent != null)
            NoppesUtil.openGUI(player, parent);
        stopDialogSound();
    }

    @Override
    public void onGuiClosed() {
        if (!sentClosePacket) {
            if (dialog != null)
                PacketClient.sendClient(new DialogSelectPacket(dialog.id, -1));
            closed();
        }
        Keyboard.enableRepeatEvents(false);
        stopDialogSound();
        super.onGuiClosed();
    }

    private void stopDialogSound() {
        // Only the dialog sound, not Bard music or other sounds
        if (dialogSound != null) {
            MusicController.Instance.stopSound(dialogSound);
            dialogSound = null;
        }
    }

    @Override
    public void setClose(int i, NBTTagCompound data) {
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
