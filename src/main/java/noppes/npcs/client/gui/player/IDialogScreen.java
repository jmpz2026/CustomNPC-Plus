package noppes.npcs.client.gui.player;

import noppes.npcs.constants.EnumDialogStyle;
import noppes.npcs.controllers.data.Dialog;

/**
 * A screen that shows dialogs and can take the next one of the same conversation
 * without being reopened.
 */
public interface IDialogScreen {
    EnumDialogStyle getDialogStyle();

    void appendDialog(Dialog dialog);
}
