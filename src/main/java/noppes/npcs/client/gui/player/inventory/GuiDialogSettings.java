package noppes.npcs.client.gui.player.inventory;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;
import noppes.npcs.client.ClientProxy;
import noppes.npcs.client.gui.player.DialogScreens;
import noppes.npcs.client.gui.player.cinematic.CinematicDraw;
import noppes.npcs.client.gui.player.cinematic.CinematicView;
import noppes.npcs.client.gui.player.cinematic.DialogIcons;
import noppes.npcs.client.gui.util.GuiNpcButton;
import noppes.npcs.client.gui.util.GuiNpcLabel;
import noppes.npcs.client.gui.util.GuiNpcTextField;
import noppes.npcs.client.gui.util.ITextfieldListener;
import noppes.npcs.config.ConfigClient;
import noppes.npcs.constants.EnumDialogStyle;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import tconstruct.client.tabs.AbstractTab;

import java.util.ArrayList;
import java.util.List;

/**
 * "Dialogs" tab of the N menu: dialog style, scale, instant text, speed and sound,
 * with a live preview of the cinematic style.
 */
public class GuiDialogSettings extends GuiCNPCInventory implements ITextfieldListener {
    private static final ResourceLocation resource = new ResourceLocation("customnpcs", "textures/gui/standardbg.png");
    private static final String[] STYLE_KEYS = {"dialogstyle.new", "dialogstyle.classic", "dialogstyle.modern"};
    private static final String[] SCALE_KEYS = {"dialogstyle.scale.auto", "1", "2", "3", "4"};

    // Logical size the preview is laid out at, then shrunk into the box
    private static final int PREVIEW_W = 320, PREVIEW_H = 240;
    private static final int BOX_X = 112, BOX_Y = 24, BOX_W = 160, BOX_H = 120;

    private final CinematicView preview = new CinematicView();
    private long previewDoneAt = -1;
    private long lastStep;

    public GuiDialogSettings() {
        super();
        xSize = 280;
        ySize = 180;
        this.drawDefaultBackground = false;
        title = "";
        restartPreview();
    }

    @Override
    public void initGui() {
        super.initGui();
        EnumDialogStyle style = DialogScreens.effectiveStyle();
        int x = guiLeft + 8, y = guiTop + 8;

        // Every label goes above its control: the column is too narrow for both on one line
        addLabel(new GuiNpcLabel(1, "dialogstyle.style", x, y));
        GuiNpcButton styleButton = new GuiNpcButton(1, x, y + 10, 96, 20, STYLE_KEYS, style.ordinal());
        styleButton.setEnabled(!DialogScreens.isForced());
        addButton(styleButton);

        y += 34;
        addLabel(new GuiNpcLabel(2, "dialogstyle.scale", x, y));
        GuiNpcButton scaleButton = new GuiNpcButton(2, x, y + 10, 96, 20, SCALE_KEYS, ConfigClient.DialogScale);
        // The scale only applies to the cinematic style
        scaleButton.setEnabled(style == EnumDialogStyle.NEW);
        addButton(scaleButton);

        y += 34;
        addLabel(new GuiNpcLabel(3, "dialogstyle.instant", x, y));
        addButton(new GuiNpcButton(3, x, y + 10, 96, 20, new String[]{"gui.no", "gui.yes"}, ConfigClient.DialogInstantText ? 1 : 0));

        y += 34;
        addLabel(new GuiNpcLabel(4, "dialogstyle.speed", x, y));
        GuiNpcTextField speed = new GuiNpcTextField(10, this, fontRendererObj, x + 1, y + 10, 45, 18, ConfigClient.DialogSpeed + "");
        speed.integersOnly = true;
        speed.setMinMaxDefault(1, 20, 10);
        addTextField(speed);

        y += 34;
        addLabel(new GuiNpcLabel(5, "dialogstyle.sound", x, y));
        addButton(new GuiNpcButton(4, x, y + 10, 96, 20, new String[]{"gui.no", "gui.yes"}, ConfigClient.DialogSound ? 1 : 0));

        addLabel(new GuiNpcLabel(6, "dialogstyle.preview", guiLeft + BOX_X, guiTop + 8));
        if (DialogScreens.isForced())
            addLabel(new GuiNpcLabel(9, "dialogstyle.forced", guiLeft + BOX_X, guiTop + BOX_Y + BOX_H + 6, 0xA02020));
    }

    private void restartPreview() {
        List<CinematicView.Option> options = new ArrayList<CinematicView.Option>();
        options.add(new CinematicView.Option(0, tr("dialogstyle.sample.option1"), 0xe0e0e0, DialogIcons.QUEST));
        options.add(new CinematicView.Option(1, tr("dialogstyle.sample.option2"), 0xe0e0e0, DialogIcons.TALK));
        options.add(new CinematicView.Option(2, tr("dialogstyle.sample.option3"), 0xe0e0e0, DialogIcons.QUIT));
        preview.gradual = !ConfigClient.DialogInstantText;
        preview.speed = ConfigClient.DialogSpeed;
        preview.historyKeyName = ClientProxy.DialogHistoryKey == null ? "H" : Keyboard.getKeyName(ClientProxy.DialogHistoryKey.getKeyCode());
        preview.history.clear();
        preview.setDialog(tr("dialogstyle.sample.npc"), 0xe0e0e0, 0xe0e0e0, tr("dialogstyle.sample.text"), options, false, null);
        previewDoneAt = -1;
        lastStep = System.currentTimeMillis();
    }

    private static String tr(String key) {
        return StatCollector.translateToLocal(key);
    }

    @Override
    public void drawScreen(int i, int j, float f) {
        drawDefaultBackground();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        mc.renderEngine.bindTexture(resource);
        drawTexturedModalRect(guiLeft, guiTop, 0, 0, 252, 195);
        drawTexturedModalRect(guiLeft + 252, guiTop, 188, 0, 67, 195);
        super.drawScreen(i, j, f);
        drawPreview();
    }

    private void drawPreview() {
        int x = guiLeft + BOX_X, y = guiTop + BOX_Y;
        drawRect(x - 1, y - 1, x + BOX_W + 1, y + BOX_H + 1, 0xFF202020);
        drawGradientRect(x, y, x + BOX_W, y + BOX_H, 0xFF6F9FD8, 0xFF3D5F27);

        EnumDialogStyle style = DialogScreens.effectiveStyle();
        if (style != EnumDialogStyle.NEW) {
            drawStaticPreview(x, y, style);
            return;
        }

        // Loop: when the options have been on screen a while, walk them and start over
        long now = System.currentTimeMillis();
        if (previewDoneAt < 0 && now - lastStep > 3500)
            previewDoneAt = now;
        if (previewDoneAt > 0 && now - lastStep > 800) {
            preview.key(Keyboard.KEY_DOWN, false);
            lastStep = now;
        }
        if (previewDoneAt > 0 && now - previewDoneAt > 3000)
            restartPreview();
        preview.speed = ConfigClient.DialogSpeed;

        ScaledResolution sr = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int sf = sr.getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(x * sf, mc.displayHeight - (y + BOX_H) * sf, BOX_W * sf, BOX_H * sf);
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 50);
        float k = BOX_W / (float) PREVIEW_W;
        GL11.glScalef(k, k, 1);
        // The preview does not follow the mouse: it is a demo, not a control
        CinematicDraw.pixelsPerUnit = sf * k;
        preview.draw(PREVIEW_W, PREVIEW_H, -1000, -1000);
        CinematicDraw.pixelsPerUnit = 2;
        GL11.glPopMatrix();
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    /**
     * Classic and modern draw against the whole screen, so they cannot be shrunk into a
     * box: a schematic of their layout stands in for them.
     */
    private void drawStaticPreview(int x, int y, EnumDialogStyle style) {
        if (style == EnumDialogStyle.CLASSIC) {
            drawRect(x + 8, y + 58, x + BOX_W - 8, y + 60, 0xFFFEA53B);
            for (int l = 0; l < 3; l++)
                drawRect(x + 20, y + 66 + l * 8, x + 20 + 90 - l * 20, y + 70 + l * 8, 0xFFE0E0E0);
            for (int l = 0; l < 3; l++)
                drawRect(x + 20, y + 94 + l * 7, x + 70, y + 97 + l * 7, 0xFFB0B0FF);
        } else {
            drawRect(x, y + 72, x + BOX_W, y + BOX_H, 0x99000000);
            drawRect(x + 8, y + 84, x + BOX_W - 8, y + 85, 0xFFFEA53B);
            for (int l = 0; l < 2; l++)
                drawRect(x + 30, y + 92 + l * 8, x + BOX_W - 30, y + 96 + l * 8, 0xFFE0E0E0);
            for (int l = 0; l < 3; l++)
                drawRect(x + BOX_W - 62, y + 24 + l * 10, x + BOX_W - 6, y + 31 + l * 10, 0xCC303040);
        }
        String label = tr("dialogstyle.preview.static");
        CinematicDraw.text(label, x + 4, y + 4, 0xFFFFFFFF, 1);
    }

    @Override
    protected void actionPerformed(GuiButton btn) {
        if (btn instanceof AbstractTab)
            return;
        if (btn.id <= -100) {
            super.actionPerformed(btn);
            return;
        }
        if (!(btn instanceof GuiNpcButton))
            return;
        GuiNpcButton button = (GuiNpcButton) btn;
        if (button.id == 1) {
            ConfigClient.DialogStyle = EnumDialogStyle.values()[button.getValue()].configName;
            ConfigClient.DialogStyleProperty.set(ConfigClient.DialogStyle);
            initGui();
        }
        if (button.id == 2) {
            ConfigClient.DialogScale = button.getValue();
            ConfigClient.DialogScaleProperty.set(ConfigClient.DialogScale);
        }
        if (button.id == 3) {
            ConfigClient.DialogInstantText = button.getValue() == 1;
            ConfigClient.DialogInstantTextProperty.set(ConfigClient.DialogInstantText);
            restartPreview();
        }
        if (button.id == 4) {
            ConfigClient.DialogSound = button.getValue() == 1;
            ConfigClient.DialogSoundProperty.set(ConfigClient.DialogSound);
        }
        ConfigClient.config.save();
    }

    @Override
    public void keyTyped(char c, int i) {
        super.keyTyped(c, i);
        if (i == 1 || isInventoryKey(i)) {
            close();
        }
    }

    @Override
    public void save() {
    }

    @Override
    public void unFocused(GuiNpcTextField textfield) {
        if (textfield.id == 10) {
            ConfigClient.DialogSpeed = textfield.getInteger();
            ConfigClient.DialogSpeedProperty.set(ConfigClient.DialogSpeed);
            ConfigClient.config.save();
            restartPreview();
        }
    }
}
