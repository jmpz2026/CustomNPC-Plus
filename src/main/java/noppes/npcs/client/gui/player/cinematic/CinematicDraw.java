package noppes.npcs.client.gui.player.cinematic;

import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import noppes.npcs.client.ClientProxy;
import noppes.npcs.config.ConfigClient;
import org.lwjgl.opengl.GL11;

import java.util.HashMap;
import java.util.Map;

/**
 * Flat 2D primitives of the cinematic dialog style. Coordinates are floats in the
 * logical units of the current matrix, colors are ARGB.
 */
public final class CinematicDraw {
    /**
     * Real pixels per logical unit of what is being drawn now. The dialog font (StringCache)
     * rasterizes its glyphs for 2 pixels per unit, so text drawn at any other density gets
     * stretched or squeezed: pixelated at 1440p with the auto scale, letters missing in the
     * small preview. The screens set this before drawing so the text uses a copy of the
     * font rasterized for the real density.
     */
    public static float pixelsPerUnit = 2;
    // StringCache.BASELINE_OFFSET, private there
    private static final int BASELINE_OFFSET = 7;

    private static final Map<Integer, ClientProxy.FontContainer> sizedFonts = new HashMap<Integer, ClientProxy.FontContainer>();

    private CinematicDraw() {
    }

    private static float fontRatio() {
        return pixelsPerUnit / 2f;
    }

    private static ClientProxy.FontContainer font() {
        ClientProxy.FontContainer base = ClientProxy.Font;
        float r = fontRatio();
        // The Minecraft font is pixel art: scaling it is how it is meant to look
        if (!base.useCustomFont || Math.abs(r - 1) < 0.05f)
            return base;
        int size = Math.max(4, Math.round(ConfigClient.FontSize * r));
        ClientProxy.FontContainer f = sizedFonts.get(size);
        if (f == null) {
            f = new ClientProxy.FontContainer(ConfigClient.FontType, size);
            sizedFonts.put(size, f);
        }
        return f;
    }

    private static void begin() {
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GL11.glShadeModel(GL11.GL_SMOOTH);
    }

    private static void end() {
        GL11.glShadeModel(GL11.GL_FLAT);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1, 1, 1, 1);
    }

    private static void color(Tessellator t, int c, float alpha) {
        int a = (int) (((c >>> 24) & 255) * alpha);
        t.setColorRGBA((c >> 16) & 255, (c >> 8) & 255, c & 255, a);
    }

    public static void rect(float x, float y, float w, float h, int c, float alpha) {
        gradient(x, y, w, h, c, c, c, c, alpha);
    }

    /**
     * Rectangle with 1 unit cut corners, the soft rounding used by every panel of the style.
     */
    public static void pill(float x, float y, float w, float h, int c, float alpha) {
        rect(x + 1, y, w - 2, h, c, alpha);
        rect(x, y + 1, 1, h - 2, c, alpha);
        rect(x + w - 1, y + 1, 1, h - 2, c, alpha);
    }

    public static void vgradient(float x, float y, float w, float h, int top, int bottom, float alpha) {
        gradient(x, y, w, h, top, top, bottom, bottom, alpha);
    }

    public static void hgradient(float x, float y, float w, float h, int left, int right, float alpha) {
        gradient(x, y, w, h, left, right, right, left, alpha);
    }

    // Corner colors: top left, top right, bottom right, bottom left
    private static void gradient(float x, float y, float w, float h, int tl, int tr, int br, int bl, float alpha) {
        begin();
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        color(t, tl, alpha);
        t.addVertex(x, y, 0);
        color(t, bl, alpha);
        t.addVertex(x, y + h, 0);
        color(t, br, alpha);
        t.addVertex(x + w, y + h, 0);
        color(t, tr, alpha);
        t.addVertex(x + w, y, 0);
        t.draw();
        end();
    }

    /**
     * Ring sector between two angles in degrees (0 = right, clockwise because y grows down).
     */
    public static void sector(float cx, float cy, float r0, float r1, float a0, float a1, int c, float alpha) {
        begin();
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        color(t, c, alpha);
        int steps = Math.max(2, (int) Math.ceil(Math.abs(a1 - a0) / 6f));
        for (int i = 0; i < steps; i++) {
            double s = Math.toRadians(a0 + (a1 - a0) * i / steps);
            double e = Math.toRadians(a0 + (a1 - a0) * (i + 1) / steps);
            t.addVertex(cx + Math.cos(s) * r0, cy + Math.sin(s) * r0, 0);
            t.addVertex(cx + Math.cos(e) * r0, cy + Math.sin(e) * r0, 0);
            t.addVertex(cx + Math.cos(e) * r1, cy + Math.sin(e) * r1, 0);
            t.addVertex(cx + Math.cos(s) * r1, cy + Math.sin(s) * r1, 0);
        }
        t.draw();
        end();
    }

    /**
     * Text with the dialog font (ClientProxy.Font, the same the classic style uses).
     * The alpha is folded into the color because the font ignores glColor.
     */
    public static int text(String s, float x, float y, int color, float alpha) {
        if (s == null || s.isEmpty())
            return Math.round(x);
        int a = (int) (((color >>> 24) == 0 ? 255 : (color >>> 24)) * alpha);
        if (a < 5)
            return Math.round(x) + width(s);
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        ClientProxy.FontContainer f = font();
        if (f == ClientProxy.Font) {
            f.drawString(s, Math.round(x), Math.round(y), (a << 24) | (color & 0xFFFFFF));
        } else {
            float r = fontRatio();
            GL11.glPushMatrix();
            GL11.glTranslatef(Math.round(x), Math.round(y), 0);
            GL11.glScalef(1 / r, 1 / r, 1);
            // StringCache moves the text down by a fixed BASELINE_OFFSET (7) tuned for the
            // default size: a bigger copy needs it scaled, or the text rides high
            f.drawString(s, 0, Math.round(BASELINE_OFFSET * (r - 1)), (a << 24) | (color & 0xFFFFFF));
            GL11.glPopMatrix();
        }
        GL11.glColor4f(1, 1, 1, 1);
        return Math.round(x) + width(s);
    }

    public static int width(String s) {
        if (s == null)
            return 0;
        ClientProxy.FontContainer f = font();
        return f == ClientProxy.Font ? f.width(s) : Math.round(f.width(s) / fontRatio());
    }

    public static int fontHeight() {
        ClientProxy.FontContainer f = font();
        return f == ClientProxy.Font ? f.height() : Math.round(f.height() / fontRatio());
    }

    /**
     * Cuts the string with "..." so it fits in maxW.
     */
    public static String trim(String s, int maxW) {
        if (width(s) <= maxW)
            return s;
        String out = s;
        while (!out.isEmpty() && width(out + "...") > maxW)
            out = out.substring(0, out.length() - 1);
        if (out.endsWith("§"))
            out = out.substring(0, out.length() - 1);
        return out + "...";
    }
}
