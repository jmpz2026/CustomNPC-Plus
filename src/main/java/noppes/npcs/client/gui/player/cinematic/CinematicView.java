package noppes.npcs.client.gui.player.cinematic;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything the cinematic dialog style draws and decides, in logical units and
 * without packets: the screen feeds it a dialog and turns its answers into packets,
 * and the settings tab uses it as a live preview with made up content.
 *
 * Layout and behaviour follow the approved mockup
 * (DbrServerPack/docs/mockups/customnpc-dialogos/nuevo.html).
 */
public class CinematicView {

    public static class Option {
        public final int slot;
        public final String title;
        public final int color;
        public final int icon;

        public Option(int slot, String title, int color, int icon) {
            this.slot = slot;
            this.title = title;
            this.color = color;
            this.icon = icon;
        }
    }

    public static class QuestInfo {
        public String title = "";
        public final List<String> objectives = new ArrayList<String>();
        public final List<ItemStack> rewards = new ArrayList<ItemStack>();
        public int xp;
    }

    public static class HistoryEntry {
        // null when the player said it
        public final String who;
        public final String text;

        public HistoryEntry(String who, String text) {
            this.who = who;
            this.text = text;
        }
    }

    public enum Action {NONE, CHOOSE, FINISH}

    public interface TextSound {
        void play();
    }

    // Slot angles of the wheel, same slots as the classic one: 0-2 right top to bottom, 3-5 left
    private static final float[][] SLOT_ANGLES = {{-90, -30}, {-30, 30}, {30, 90}, {210, 270}, {150, 210}, {90, 150}};
    private static final int[] WHEEL_LABEL_Y = {-27, -4, 19};
    private static final int DEFAULT_COLOR = 0xe0e0e0;
    private static final long OPTION_DELAY_MS = 200;

    private static final int GOLD = 0xFFFFD37A;
    private static final int GOLD_LINE = 0xFFFFC94A;
    private static final int CREAM = 0xFFF3EEE0;
    private static final int INK = 0xFF1C1C22;
    private static final int PANEL = 0xB0121218;

    private static final RenderItem itemRender = new RenderItem();

    // ---------------------------------------------------------------- content
    private String npcName = "";
    private int titleColor = GOLD;
    private int textColor = 0xFFF2F2F2;
    private String text = "";
    private List<Integer> pauses = new ArrayList<Integer>();
    private List<Option> options = new ArrayList<Option>();
    private boolean wheel;
    private QuestInfo quest;
    private boolean waiting;
    public final List<HistoryEntry> history = new ArrayList<HistoryEntry>();

    // ---------------------------------------------------------------- settings
    public boolean gradual = true;
    public int speed = 15;
    public boolean escAllowed = true;
    public String historyKeyName = "H";
    public TextSound textSound;

    // ---------------------------------------------------------------- state
    private List<List<String>> pages;
    private int[] pausesBefore;
    private int pagesKeyW = -1;
    private int pagesKeyH = -1;
    private int page;
    private float revealed;
    private boolean full;
    private int handledPauses;
    private long pauseLeftMs;
    private long lastFrame;
    private long optT0;
    private int soundChars;
    private int scroll;
    private int kb = -1;
    private int hover = -1;
    private int wheelSel = 1;
    private float[] hoverAnim = new float[64];
    private boolean historyOpen;
    private int historyScroll;
    private float lastMx = -1000, lastMy = -1000;
    private boolean mouseMoved;

    // last layout, for clicks
    private int W, H;
    private Layout L;
    private final List<float[]> boxes = new ArrayList<float[]>();
    private final List<float[]> hintBoxes = new ArrayList<float[]>();

    private static class Layout {
        boolean compact;
        int fh, lineH, wrapW, textX, textY, nameY, maxLines;
        int optW, optX, top, bottom, rowH, gap, rows, listY;
        int cardH, cardY;
        List<String> objLines = new ArrayList<String>();
    }

    // ================================================================ content API

    /**
     * Shows a new dialog. The text may carry {N} pause marks of the gradual text.
     */
    public void setDialog(String npcName, int titleColor, int textColor, String rawText, List<Option> options, boolean wheel, QuestInfo quest) {
        this.npcName = npcName == null ? "" : npcName;
        this.titleColor = (titleColor & 0xFFFFFF) == DEFAULT_COLOR ? GOLD : 0xFF000000 | titleColor;
        this.textColor = (textColor & 0xFFFFFF) == DEFAULT_COLOR ? 0xFFF2F2F2 : 0xFF000000 | textColor;
        this.pauses = new ArrayList<Integer>();
        this.text = extractPauses(rawText == null ? "" : rawText, this.pauses);
        this.options = options;
        this.wheel = wheel;
        this.quest = quest;
        this.waiting = false;
        this.pages = null;
        this.page = 0;
        this.scroll = 0;
        this.kb = -1;
        this.wheelSel = firstWheelSlot();
        startPage();
        history.add(new HistoryEntry(this.npcName, DialogPager.strip(this.text)));
    }

    /**
     * The player picked an option that keeps the conversation going: hide the options
     * until the server sends the next dialog.
     */
    public void setWaiting(String chosenTitle) {
        waiting = true;
        history.add(new HistoryEntry(null, chosenTitle));
    }

    public void toggleHistory() {
        historyOpen = !historyOpen;
        historyScroll = 0;
    }

    public boolean isHistoryOpen() {
        return historyOpen;
    }

    private static String extractPauses(String raw, List<Integer> out) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < raw.length()) {
            char c = raw.charAt(i);
            if (c == '{') {
                int close = raw.indexOf('}', i);
                if (close > i + 1 && raw.substring(i + 1, close).matches("\\d{1,4}")) {
                    out.add(Integer.parseInt(raw.substring(i + 1, close)));
                    sb.append(DialogPager.PAUSE);
                    i = close + 1;
                    continue;
                }
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    private int firstWheelSlot() {
        for (Option o : options)
            if (o.slot == 1)
                return 1;
        for (Option o : options)
            if (o.slot >= 0 && o.slot < 6)
                return o.slot;
        return 1;
    }

    // ================================================================ text flow

    private void startPage() {
        revealed = 0;
        handledPauses = 0;
        pauseLeftMs = 0;
        soundChars = 0;
        full = !gradual;
        lastFrame = System.currentTimeMillis();
        optT0 = full ? lastFrame : 0;
    }

    private boolean lastPage() {
        return pages == null || page >= pages.size() - 1;
    }

    private boolean textDone() {
        return full && lastPage();
    }

    private boolean optionsReady() {
        return textDone() && !waiting && System.currentTimeMillis() - optT0 > OPTION_DELAY_MS;
    }

    private String pageJoined() {
        StringBuilder sb = new StringBuilder();
        for (String l : pages.get(page))
            sb.append(l);
        return sb.toString();
    }

    private float charsPerSecond() {
        // Same pace as the classic style: one char per tick at 10
        if (speed > 10)
            return 20f * (speed - 9);
        return 20f / (11 - Math.max(1, speed));
    }

    private void advanceText() {
        long now = System.currentTimeMillis();
        long dt = Math.min(200, now - lastFrame);
        lastFrame = now;
        if (full || pages == null)
            return;
        String joined = pageJoined();
        int total = DialogPager.visibleLength(joined);
        if (pauseLeftMs > 0) {
            pauseLeftMs -= dt;
            return;
        }
        int before = (int) revealed;
        revealed += dt * charsPerSecond() / 1000f;
        int crossed = DialogPager.pausesWithin(joined, (int) revealed);
        if (crossed > handledPauses) {
            int index = pausesBefore[page] + handledPauses;
            handledPauses++;
            if (index < pauses.size())
                pauseLeftMs = pauses.get(index) * 50L;
        }
        int now_ = (int) revealed;
        soundChars += Math.max(0, Math.min(now_, total) - before);
        // about 22 blips a second whatever the speed, like the mockup
        int every = Math.max(2, Math.round(charsPerSecond() / 22f));
        if (soundChars >= every && textSound != null) {
            soundChars = 0;
            textSound.play();
        }
        if (revealed >= total) {
            full = true;
            optT0 = now;
        }
    }

    private void completePage() {
        if (!full) {
            full = true;
            optT0 = System.currentTimeMillis();
        }
    }

    private void nextPage() {
        page++;
        startPage();
    }

    // ================================================================ layout

    private Layout layout(int W, int H) {
        Layout l = new Layout();
        l.compact = W < 520;
        l.fh = CinematicDraw.fontHeight();
        l.lineH = l.fh + 2;
        l.wrapW = Math.min(360, W - 48);
        l.maxLines = H < 300 ? 3 : 4;
        if (pages == null || pagesKeyW != W || pagesKeyH != H) {
            pages = DialogPager.paginate(text, l.wrapW, l.maxLines);
            pausesBefore = new int[pages.size()];
            int acc = 0;
            for (int p = 0; p < pages.size(); p++) {
                pausesBefore[p] = acc;
                for (String line : pages.get(p))
                    for (char c : line.toCharArray())
                        if (c == DialogPager.PAUSE)
                            acc++;
            }
            pagesKeyW = W;
            pagesKeyH = H;
            page = Math.min(page, pages.size() - 1);
        }
        int maxPageLines = 1;
        for (List<String> p : pages)
            maxPageLines = Math.max(maxPageLines, p.size());
        l.textX = Math.round(W / 2f - l.wrapW / 2f);
        l.textY = H - 16 - maxPageLines * l.lineH;
        l.nameY = l.textY - l.fh - 6;

        int longest = 0;
        for (Option o : options)
            longest = Math.max(longest, CinematicDraw.width(o.title));
        l.optW = Math.max(130, Math.min(Math.min(Math.round(W * (l.compact ? 0.5f : 0.36f)), 240), longest + 18 + 22));
        l.optX = W - l.optW - (l.compact ? 8 : 16);
        l.top = 26;
        l.bottom = l.nameY - 14;
        l.rowH = Math.max(16, l.fh + 7);
        l.gap = 3;
        if (quest != null) {
            for (String o : quest.objectives)
                l.objLines.addAll(DialogPager.wrap("- " + o, l.optW - 12));
            l.cardH = 16 + l.lineH + l.objLines.size() * l.lineH + (quest.rewards.isEmpty() && quest.xp == 0 ? 2 : 16);
        }
        int listTop = l.top + (l.cardH > 0 ? l.cardH + 6 : 0);
        int maxRows = Math.max(1, (l.bottom - listTop + l.gap) / (l.rowH + l.gap));
        l.rows = Math.min(options.size(), maxRows);
        int listH = l.rows * (l.rowH + l.gap) - l.gap;
        l.listY = l.bottom - Math.max(0, listH);
        l.cardY = l.listY - l.cardH - 6;
        return l;
    }

    /**
     * Where the screen should draw the 3D NPC: feet x, feet y and height, in logical units.
     */
    public float[] npcPlacement(int W, int H) {
        Layout l = layout(W, H);
        float h = Math.min(H * 0.42f, 130);
        return new float[]{Math.round(W * (l.compact ? 0.28f : 0.32f)), l.nameY + 10, h};
    }

    // ================================================================ drawing

    public void draw(int W, int H, float mx, float my) {
        this.W = W;
        this.H = H;
        if (mx != lastMx || my != lastMy) {
            mouseMoved = true;
            lastMx = mx;
            lastMy = my;
        }
        advanceText();
        Layout l = layout(W, H);
        this.L = l;

        hintBoxes.clear();
        drawHints(l, mx, my);
        if (historyOpen) {
            boxes.clear();
            drawHistory();
            return;
        }

        // bottom bar: frameless gradient
        int barTop = l.nameY - 34;
        CinematicDraw.vgradient(0, barTop, W, H - barTop, 0x00000000, 0xD8000000, 1);
        int nw = CinematicDraw.width(npcName);
        float nx = Math.round(W / 2f - nw / 2f);
        CinematicDraw.hgradient(nx - 46, l.nameY + l.fh / 2f, 40, 1, GOLD_LINE & 0x00FFFFFF, 0xC0000000 | (GOLD_LINE & 0xFFFFFF), 1);
        CinematicDraw.hgradient(nx + nw + 6, l.nameY + l.fh / 2f, 40, 1, 0xC0000000 | (GOLD_LINE & 0xFFFFFF), GOLD_LINE & 0x00FFFFFF, 1);
        CinematicDraw.text(npcName, nx, l.nameY, titleColor, 1);

        // text: left aligned inside a centered block, so it does not jump while typing
        List<String> lines = pages.get(page);
        int left = full ? Integer.MAX_VALUE : (int) revealed;
        for (int i = 0; i < lines.size() && left > 0; i++) {
            String line = lines.get(i);
            int len = DialogPager.visibleLength(line);
            String part = left >= len ? line : DialogPager.prefix(line, left);
            CinematicDraw.text(DialogPager.strip(part), l.textX, l.textY + i * l.lineH, textColor, 1);
            left -= len;
        }

        // page counter and "there is more" arrow
        int textBottom = l.textY + maxLines() * l.lineH;
        if (pages.size() > 1) {
            String pg = (page + 1) + "/" + pages.size();
            CinematicDraw.text(pg, l.textX + l.wrapW - 10 - CinematicDraw.width(pg), textBottom - l.fh - 1, 0x80FFFFFF, 1);
        }
        boolean more = full && (!lastPage() || options.isEmpty()) && !waiting;
        if (more && (System.currentTimeMillis() / 450) % 2 == 0) {
            float tx = l.textX + l.wrapW - 6, ty = textBottom - 5;
            CinematicDraw.rect(tx, ty, 5, 1, GOLD, 1);
            CinematicDraw.rect(tx + 1, ty + 1, 3, 1, GOLD, 1);
            CinematicDraw.rect(tx + 2, ty + 2, 1, 1, GOLD, 1);
        }

        if (quest != null && textDone())
            drawQuestCard(l);
        boxes.clear();
        if (wheel)
            drawWheel(l, mx, my);
        else
            drawList(l, mx, my);
    }

    private int maxLines() {
        int m = 1;
        for (List<String> p : pages)
            m = Math.max(m, p.size());
        return m;
    }

    private float anim(long start, long delay, float duration) {
        float t = (System.currentTimeMillis() - start - delay) / duration;
        t = Math.max(0, Math.min(1, t));
        return 1 - (1 - t) * (1 - t) * (1 - t);
    }

    private void drawHints(Layout l, float mx, float my) {
        List<String[]> items = new ArrayList<String[]>();
        items.add(new String[]{historyKeyName, StatCollector.translateToLocal("dialog.history"), "H"});
        if (escAllowed)
            items.add(new String[]{"Esc", StatCollector.translateToLocal("dialog.exit"), "E"});
        float x = W - 8;
        for (int i = items.size() - 1; i >= 0; i--) {
            String[] it = items.get(i);
            int kw = CinematicDraw.width(it[0]) + 6, lw = CinematicDraw.width(it[1]);
            float w = kw + 4 + lw;
            x -= w;
            float bh = l.fh + 3;
            boolean hov = inBox(mx, my, x - 2, 6, w + 4, bh + 4);
            CinematicDraw.pill(x, 8, kw, bh, hov ? CREAM : 0x90000000, 1);
            CinematicDraw.text(it[0], x + 3, 10, hov ? INK : 0xFFE0E0E0, 1);
            CinematicDraw.text(it[1], x + kw + 4, 10, 0xC0FFFFFF, 1);
            hintBoxes.add(new float[]{x - 2, 6, w + 4, bh + 4, it[2].equals("H") ? 0 : 1});
            x -= 10;
        }
    }

    private void drawQuestCard(Layout l) {
        float a = anim(optT0, 0, 180);
        float x = l.optX, y = l.cardY, w = l.optW;
        CinematicDraw.pill(x, y, w, l.cardH, 0xD8141420, a);
        CinematicDraw.rect(x + 1, y, w - 2, 1, GOLD_LINE, a);
        DialogIcons.draw(DialogIcons.QUEST, x + 5, y + 4, DialogIcons.COLOR_QUEST, a);
        CinematicDraw.text(StatCollector.translateToLocal("dialog.quest.obtained"), x + 17, y + 4, GOLD_LINE, a);
        float yy = y + 16;
        CinematicDraw.text(CinematicDraw.trim(quest.title, (int) w - 12), x + 6, yy, 0xFFFFFFFF, a);
        yy += l.lineH;
        for (String o : l.objLines) {
            CinematicDraw.text(o, x + 6, yy, 0xFFB8B8C0, a);
            yy += l.lineH;
        }
        if (quest.rewards.isEmpty() && quest.xp == 0)
            return;
        float xx = x + 6;
        yy += 2;
        for (ItemStack stack : quest.rewards) {
            drawItem(stack, xx, yy - 1, 10, a);
            xx += 12;
            if (stack.stackSize > 1)
                xx = CinematicDraw.text("x" + stack.stackSize, xx, yy, 0xFFE8E8E8, a) + 6;
        }
        if (quest.xp > 0)
            CinematicDraw.text("XP " + quest.xp, xx, yy, 0xFF7CFF8A, a);
    }

    private void drawItem(ItemStack stack, float x, float y, float size, float alpha) {
        if (stack == null || stack.getItem() == null || alpha < 0.5f)
            return;
        Minecraft mc = Minecraft.getMinecraft();
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0);
        GL11.glScalef(size / 16f, size / 16f, 1);
        RenderHelper.enableGUIStandardItemLighting();
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        itemRender.renderItemAndEffectIntoGUI(mc.fontRenderer, mc.getTextureManager(), stack, 0, 0);
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glPopMatrix();
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
    }

    private void drawList(Layout l, float mx, float my) {
        if (!textDone() || waiting || options.isEmpty())
            return;
        int maxScroll = Math.max(0, options.size() - l.rows);
        scroll = Math.max(0, Math.min(maxScroll, scroll));
        boolean ready = optionsReady();
        hover = -1;
        for (int r = 0; r < l.rows; r++) {
            float y = l.listY + r * (l.rowH + l.gap);
            if (mouseMoved && ready && inBox(mx, my, l.optX, y, l.optW, l.rowH))
                hover = r + scroll;
        }
        int sel = hover >= 0 ? hover : kb;
        for (int r = 0; r < l.rows; r++) {
            int i = r + scroll;
            Option o = options.get(i);
            float y = l.listY + r * (l.rowH + l.gap);
            float e = anim(optT0, r * 45L, 160);
            boolean hov = i == sel;
            int ai = Math.min(i, hoverAnim.length - 1);
            hoverAnim[ai] += ((hov ? 1 : 0) - hoverAnim[ai]) * 0.35f;
            float x = Math.round(l.optX + (1 - e) * 14 - hoverAnim[ai] * 3);
            drawOption(o, i, x, y, l.optW, l.rowH, hov, e);
            boxes.add(new float[]{l.optX, y, l.optW, l.rowH, i});
        }
        float ax = l.optX + l.optW / 2f;
        if (scroll > 0) {
            float y = l.listY - 6;
            CinematicDraw.rect(ax - 2, y, 5, 1, 0xC0FFFFFF, 1);
            CinematicDraw.rect(ax - 1, y - 1, 3, 1, 0xC0FFFFFF, 1);
            CinematicDraw.rect(ax, y - 2, 1, 1, 0xC0FFFFFF, 1);
        }
        if (scroll < maxScroll) {
            float y = l.listY + l.rows * (l.rowH + l.gap) + 2;
            CinematicDraw.rect(ax - 2, y, 5, 1, 0xC0FFFFFF, 1);
            CinematicDraw.rect(ax - 1, y + 1, 3, 1, 0xC0FFFFFF, 1);
            CinematicDraw.rect(ax, y + 2, 1, 1, 0xC0FFFFFF, 1);
        }
    }

    private void drawOption(Option o, int index, float x, float y, float w, int h, boolean hov, float alpha) {
        if (hov) {
            CinematicDraw.pill(x - 1, y - 1, w + 2, h + 2, 0x40FFFFFF, alpha);
            CinematicDraw.pill(x, y, w, h, CREAM, alpha);
        } else {
            CinematicDraw.pill(x, y, w, h, PANEL, alpha);
            CinematicDraw.rect(x + 1, y, w - 2, 1, 0x18FFFFFF, alpha);
        }
        int fh = CinematicDraw.fontHeight();
        DialogIcons.draw(o.icon, x + 5, y + (h - DialogIcons.SIZE) / 2f, hov ? INK : DialogIcons.colorFor(o.icon), alpha);
        String title = CinematicDraw.trim(o.title, (int) w - 18 - 14);
        // Hovered text goes plain dark: color codes of the title would not read on cream
        CinematicDraw.text(hov ? net.minecraft.util.EnumChatFormatting.getTextWithoutFormattingCodes(title) : title,
            x + 18, y + (h - fh) / 2f, hov ? INK : 0xFF000000 | o.color, alpha);
        if (index < 9)
            CinematicDraw.text(String.valueOf(index + 1), x + w - 9, y + (h - fh) / 2f, hov ? 0x901C1C22 : 0x60FFFFFF, alpha);
    }

    private float[] wheelGeom(Layout l) {
        return new float[]{Math.round(W * (l.compact ? 0.62f : 0.68f)), Math.round((l.top + l.bottom) / 2f), 9, 27};
    }

    private int slotAt(float[] g, float mx, float my) {
        float dx = mx - g[0], dy = my - g[1];
        if (dx * dx + dy * dy < 16)
            return -1;
        double a = Math.toDegrees(Math.atan2(dy, dx));
        if (a < -90)
            a += 360;
        for (int s = 0; s < 6; s++)
            if (a >= SLOT_ANGLES[s][0] && a < SLOT_ANGLES[s][1])
                return s;
        return -1;
    }

    private Option bySlot(int slot) {
        for (Option o : options)
            if (o.slot == slot)
                return o;
        return null;
    }

    private void drawWheel(Layout l, float mx, float my) {
        if (!textDone() || waiting || options.isEmpty())
            return;
        float[] g = wheelGeom(l);
        float cx = g[0], cy = g[1], r0 = g[2], r1 = g[3];
        if (mouseMoved && optionsReady()) {
            int s = slotAt(g, mx, my);
            if (s >= 0 && bySlot(s) != null)
                wheelSel = s;
        }
        float a = anim(optT0, 0, 200);
        CinematicDraw.sector(cx, cy, 0, r1 + 3, 0, 360, 0x59000000, a);
        for (int s = 0; s < 6; s++) {
            Option o = bySlot(s);
            boolean sel = o != null && s == wheelSel;
            float rr = r1 + (sel ? 2 : 0);
            CinematicDraw.sector(cx, cy, r0, rr, SLOT_ANGLES[s][0] + 3, SLOT_ANGLES[s][1] - 3, o == null ? 0x50000000 : sel ? CREAM : 0xC0121218, a);
            if (o != null) {
                double am = Math.toRadians((SLOT_ANGLES[s][0] + SLOT_ANGLES[s][1]) / 2f);
                float ir = (r0 + rr) / 2f;
                DialogIcons.draw(o.icon, Math.round(cx + Math.cos(am) * ir - 4.5f), Math.round(cy + Math.sin(am) * ir - 4.5f),
                    sel ? INK : DialogIcons.colorFor(o.icon), a);
            }
        }
        double am = Math.toRadians((SLOT_ANGLES[wheelSel][0] + SLOT_ANGLES[wheelSel][1]) / 2f);
        CinematicDraw.rect(Math.round(cx + Math.cos(am) * 5) - 1, Math.round(cy + Math.sin(am) * 5) - 1, 3, 3, GOLD, a);

        int fh = CinematicDraw.fontHeight();
        for (int s = 0; s < 6; s++) {
            Option o = bySlot(s);
            if (o == null)
                continue;
            boolean right = s < 3, sel = s == wheelSel;
            float y = cy + WHEEL_LABEL_Y[s % 3];
            int room = (int) Math.min(120, (right ? W - cx : cx) - r1 - 24);
            String label = CinematicDraw.trim(o.title, room);
            int lw = CinematicDraw.width(label);
            float x = right ? cx + r1 + 10 + (s == 1 ? 4 : 0) : cx - r1 - 10 - (s == 4 ? 4 : 0) - lw;
            CinematicDraw.pill(x - 4, y - 3, lw + 8, fh + 5, sel ? CREAM : 0x90121218, a);
            CinematicDraw.text(sel ? net.minecraft.util.EnumChatFormatting.getTextWithoutFormattingCodes(label) : label,
                x, y, sel ? INK : 0xFF000000 | o.color, a);
            String key = String.valueOf(s + 1);
            CinematicDraw.text(key, right ? x + lw + 8 : x - 12, y, 0x70FFFFFF, a);
            boxes.add(new float[]{x - 4, y - 3, lw + 8, fh + 5, s});
        }
    }

    private void drawHistory() {
        int hw = Math.min(240, Math.round(W * 0.55f)), pad = 10;
        int fh = CinematicDraw.fontHeight(), lh = fh + 1;
        CinematicDraw.rect(0, 0, W, H, 0x90000000, 1);
        CinematicDraw.rect(0, 0, hw, H, 0xE00C0C14, 1);
        CinematicDraw.hgradient(hw, 0, 24, H, 0xE00C0C14, 0x000C0C14, 1);
        CinematicDraw.text(StatCollector.translateToLocal("dialog.history"), pad, 10, GOLD_LINE, 1);
        String close = "[" + historyKeyName + "] " + StatCollector.translateToLocal("dialog.history.close");
        CinematicDraw.text(close, hw - pad - CinematicDraw.width(close), 10, 0x80FFFFFF, 1);
        CinematicDraw.rect(pad, 12 + fh, hw - pad * 2, 1, 0x40FFC94A, 1);

        int clipTop = 16 + fh;
        Minecraft mc = Minecraft.getMinecraft();
        // scissor works in real pixels, measured from the bottom
        float scale = mc.displayHeight / (float) H;
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(0, Math.round(8 * scale), Math.round(hw * scale), Math.round((H - clipTop - 8) * scale));
        float y = H - 8 + historyScroll;
        for (int b = history.size() - 1; b >= 0 && y > clipTop - 200; b--) {
            HistoryEntry e = history.get(b);
            List<String> lines = DialogPager.wrap(e.text, hw - pad * 2 - (e.who == null ? 10 : 0));
            float h = (e.who != null ? lh : 0) + lines.size() * lh + 6;
            y -= h;
            float yy = y;
            if (e.who != null) {
                CinematicDraw.text(e.who, pad, yy, GOLD, 1);
                yy += lh;
                for (String line : lines) {
                    CinematicDraw.text(line, pad, yy, 0xFFD8D8D8, 1);
                    yy += lh;
                }
            } else {
                CinematicDraw.text(">", pad, yy, 0xFF8FD8FF, 1);
                for (String line : lines) {
                    CinematicDraw.text(line, pad + 10, yy, 0xFF8FD8FF, 1);
                    yy += lh;
                }
            }
        }
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    private static boolean inBox(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ================================================================ input
    // Each method returns what the screen must do; the chosen slot is in chosenSlot().

    private int chosen = -1;

    public int chosenSlot() {
        return chosen;
    }

    /**
     * @return 0 = history toggle, 1 = exit, or -1 when the click was not on a hint
     */
    public int hintAt(float mx, float my) {
        for (float[] b : hintBoxes)
            if (inBox(mx, my, b[0], b[1], b[2], b[3]))
                return (int) b[4];
        return -1;
    }

    public Action click(float mx, float my, boolean shift) {
        if (historyOpen)
            return Action.NONE;
        if (shift && !textDone()) {
            skipToEnd();
            return Action.NONE;
        }
        if (!full) {
            completePage();
            return Action.NONE;
        }
        if (!lastPage()) {
            nextPage();
            return Action.NONE;
        }
        if (waiting)
            return Action.NONE;
        if (options.isEmpty())
            return Action.FINISH;
        if (!optionsReady())
            return Action.NONE;
        if (wheel)
            return choose(bySlot(wheelSel));
        for (float[] b : boxes)
            if (inBox(mx, my, b[0], b[1], b[2], b[3]))
                return choose(options.get((int) b[4]));
        return Action.NONE;
    }

    private void skipToEnd() {
        if (pages == null)
            return;
        page = pages.size() - 1;
        startPage();
        completePage();
    }

    private Action choose(Option o) {
        if (o == null || !optionsReady())
            return Action.NONE;
        chosen = o.slot;
        return Action.CHOOSE;
    }

    public Action key(int keyCode, boolean shift) {
        if (historyOpen)
            return Action.NONE;
        if (keyCode == Keyboard.KEY_SPACE || keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            if (shift && !textDone()) {
                skipToEnd();
                return Action.NONE;
            }
            if (!full) {
                completePage();
                return Action.NONE;
            }
            if (!lastPage()) {
                nextPage();
                return Action.NONE;
            }
            if (waiting)
                return Action.NONE;
            if (options.isEmpty())
                return Action.FINISH;
            if (wheel)
                return choose(bySlot(wheelSel));
            if (kb >= 0 && kb < options.size())
                return choose(options.get(kb));
            return Action.NONE;
        }
        if (keyCode >= Keyboard.KEY_1 && keyCode <= Keyboard.KEY_9) {
            int n = keyCode - Keyboard.KEY_1;
            if (wheel)
                return choose(bySlot(n));
            if (n < options.size())
                return choose(options.get(n));
            return Action.NONE;
        }
        if (!textDone() || waiting || options.isEmpty())
            return Action.NONE;
        boolean up = keyCode == Keyboard.KEY_UP || keyCode == Keyboard.KEY_W;
        boolean down = keyCode == Keyboard.KEY_DOWN || keyCode == Keyboard.KEY_S;
        boolean side = keyCode == Keyboard.KEY_LEFT || keyCode == Keyboard.KEY_RIGHT || keyCode == Keyboard.KEY_A || keyCode == Keyboard.KEY_D;
        mouseMoved = false;
        if (wheel) {
            int next = wheelSel;
            if (up && wheelSel % 3 > 0)
                next = wheelSel - 1;
            if (down && wheelSel % 3 < 2)
                next = wheelSel + 1;
            if (side)
                next = (wheelSel + 3) % 6;
            if (bySlot(next) != null)
                wheelSel = next;
            return Action.NONE;
        }
        if (up || down) {
            int n = options.size();
            kb = kb < 0 ? (up ? n - 1 : 0) : (kb + (up ? n - 1 : 1)) % n;
            if (L != null) {
                if (kb < scroll)
                    scroll = kb;
                else if (kb >= scroll + L.rows)
                    scroll = kb - L.rows + 1;
            }
        }
        return Action.NONE;
    }

    public void mouseWheel(int dir) {
        if (historyOpen)
            historyScroll = Math.max(0, historyScroll + dir * 20);
        else
            scroll -= dir;
    }
}
