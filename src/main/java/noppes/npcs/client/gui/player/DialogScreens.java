package noppes.npcs.client.gui.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import noppes.npcs.CustomNpcs;
import noppes.npcs.client.ClientCacheHandler;
import noppes.npcs.client.NoppesUtil;
import noppes.npcs.client.gui.player.modern.GuiModernDialogInteract;
import noppes.npcs.client.gui.player.modern.GuiModernQuestDialog;
import noppes.npcs.config.ConfigClient;
import noppes.npcs.constants.EnumDialogStyle;
import noppes.npcs.controllers.data.Dialog;
import noppes.npcs.entity.EntityNPCInterface;

/**
 * Picks the dialog screen for the style in use. Every place that opens a dialog goes
 * through here, so the choice lives in one spot.
 */
public final class DialogScreens {
    private DialogScreens() {
    }

    /**
     * @return the style forced by the server, or else the one the player picked
     */
    public static EnumDialogStyle effectiveStyle() {
        if (ClientCacheHandler.forcedDialogStyle != null)
            return ClientCacheHandler.forcedDialogStyle;
        return playerStyle();
    }

    public static EnumDialogStyle playerStyle() {
        return EnumDialogStyle.fromConfig(ConfigClient.DialogStyle, EnumDialogStyle.NEW);
    }

    public static boolean isForced() {
        return ClientCacheHandler.forcedDialogStyle != null;
    }

    /**
     * Shows a dialog sent by the server: adds it to the open dialog screen when it is
     * the same conversation and style, otherwise opens a new screen.
     */
    public static void open(EntityNPCInterface npc, Dialog dialog, EntityPlayer player) {
        EnumDialogStyle style = effectiveStyle();
        GuiScreen current = Minecraft.getMinecraft().currentScreen;
        // The modern style shows quests on their own screen, so they never join the open one
        boolean ownQuestScreen = style == EnumDialogStyle.MODERN && dialog.hasQuest();
        if (current instanceof IDialogScreen && ((IDialogScreen) current).getDialogStyle() == style && !ownQuestScreen) {
            ((IDialogScreen) current).appendDialog(dialog);
            return;
        }
        CustomNpcs.proxy.openGui(player, create(style, npc, dialog));
    }

    /**
     * Test button of the dialog editor: same style the author uses, and closing goes back
     * to the editor. The modern screens cannot return to a parent, so they show the
     * classic one there.
     */
    public static void openPreview(GuiScreen parent, EntityNPCInterface npc, Dialog dialog, EntityPlayer player) {
        NoppesUtil.openGUI(player, new GuiDialogInteract(parent, npc, dialog));
    }

    private static GuiScreen create(EnumDialogStyle style, EntityNPCInterface npc, Dialog dialog) {
        switch (style) {
            case MODERN:
                if (dialog.hasQuest())
                    return new GuiModernQuestDialog(npc, dialog.getQuest(), dialog, -2);
                return new GuiModernDialogInteract(npc, dialog);
            case CLASSIC:
            case NEW:
            default:
                return new GuiDialogInteract(npc, dialog);
        }
    }
}
