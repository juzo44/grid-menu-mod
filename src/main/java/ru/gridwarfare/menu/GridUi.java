package ru.gridwarfare.menu;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

public final class GridUi {
    public static final ResourceLocation BG_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(GridMenu.MOD_ID, "textures/gui/ui/bg_menu.png");

    public static final ResourceLocation FONT = ResourceLocation.fromNamespaceAndPath(GridMenu.MOD_ID, "grid");

    private static ResourceLocation tex(String path) {
        return ResourceLocation.fromNamespaceAndPath(GridMenu.MOD_ID, "textures/gui/" + path);
    }

    public static final ResourceLocation ICON_TG = tex("ui/circle_tg_n.png");
    public static final ResourceLocation ICON_TG_H = tex("ui/circle_tg_h.png");
    public static final ResourceLocation ICON_DC = tex("ui/circle_dc_n.png");
    public static final ResourceLocation ICON_DC_H = tex("ui/circle_dc_h.png");
    public static final ResourceLocation ICON_GL = tex("ui/circle_gl_n.png");
    public static final ResourceLocation ICON_GL_H = tex("ui/circle_gl_h.png");

    public static final int ACCENT        = 0xFF68C284;
    public static final int ACCENT_HOVER  = 0xFF7CD090;
    public static final int ACCENT_DARK   = 0xFF4A9C66;
    public static final int ACCENT_DARKER = 0xFF387A50;
    public static final int BG_DEEP       = 0xFF0B0F0C;
    public static final int TEXT_MAIN     = 0xFFF3F6F3;
    public static final int TEXT_MUTED    = 0xFF8B978F;
    public static final int TEXT_DIM      = 0xFF5A655E;
    public static final int LINE_COLOR    = 0xFF344038;
    public static final int PANEL_BG      = 0xD10C100E;
    public static final int ACCENT_DIM    = 0x2668C284;
    public static final int ACCENT_BORDER = 0x4D68C284;
    public static final int GREEN         = 0xFF68C284;

    private GridUi() {}

    public static Component styled(String text) {
        return Component.literal(text).setStyle(Style.EMPTY.withFont(FONT));
    }

    /* ═══ ФОН ═══ */
    public static void background(GuiGraphics g, int w, int h) {
        // 1) Картинка
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.blit(BG_TEXTURE, 0, 0, 0, 0, w, h, w, h);

        // 2) Градиент-оверлей (полосами по 2px)
        for (int y = 0; y < h; y += 2) {
            float t = (float) y / Math.max(1, h - 1);
            int alpha;
            if (t < 0.4f) {
                alpha = (int) lerp(0.72f, 0.50f, t / 0.4f);
            } else {
                alpha = (int) lerp(0.50f, 0.68f, (t - 0.4f) / 0.6f);
            }
            g.fill(0, y, w, y + 2, (alpha << 24) | 0x0B0F0C);
        }

        // 3) Виньетка — 4 полосы по краям
        int vigMax = (int)(0.50 * 255);
        int vigBands = 80;
        for (int i = 0; i < vigBands; i++) {
            float t = 1f - (float) i / vigBands;
            int a = (int)(t * t * vigMax);
            if (a < 1) continue;
            int color = (a << 24);
            g.fill(0, i, w, i + 1, color);           // верх
            g.fill(0, h - 1 - i, w, h - i, color);   // низ
            g.fill(i, 0, i + 1, h, color);             // лево
            g.fill(w - 1 - i, 0, w - i, h, color);     // право
        }
    }

    /* ═══ БРЕНД-МАРКА ═══ */
    public static void brandMark(GuiGraphics g, int x, int y, int size) {
        clippedCorners(g, x, y, size, size, 5, ACCENT);
        var font = Minecraft.getInstance().font;
        g.drawCenteredString(font, styled("G"), x + size / 2, y + size / 2 - 4, BG_DEEP);
    }

    public static void clippedCorners(GuiGraphics g, int x, int y, int w, int h, int cut, int color) {
        for (int row = 0; row < h; row++) {
            int left = 0, right = w;
            if (row < cut) left = cut - row;
            else if (row >= h - cut) right = w - (row - (h - cut)) - 1;
            if (left < right) g.fill(x + left, y + row, x + right, y + row + 1, color);
        }
    }

    /* ═══ ПАНЕЛИ ═══ */
    public static void panel(GuiGraphics g, int x, int y, int w, int h, int radius) {
        roundedRect(g, x, y, w, h, radius, LINE_COLOR);
        roundedRect(g, x + 1, y + 1, w - 2, h - 2, Math.max(1, radius - 1), PANEL_BG);
    }

    public static void card(GuiGraphics g, int x, int y, int w, int h) {
        panel(g, x, y, w, h, 10);
    }

    /* ═══ РАУНДРЕКТЫ ═══ */
    public static void roundedRect(GuiGraphics g, int x, int y, int w, int h, int radius, int color) {
        int r = Math.min(radius, Math.min(w, h) / 2);
        for (int row = 0; row < h; row++) {
            int edge = Math.min(row, h - 1 - row);
            int inset = 0;
            if (edge < r) {
                double dy = r - edge - 0.5;
                inset = r - (int) Math.floor(Math.sqrt(Math.max(0, r * r - dy * dy)));
            }
            g.fill(x + inset, y + row, x + w - inset, y + row + 1, color);
        }
    }

    /* ═══ ЦВЕТА ═══ */
    public static int lerpColor(int from, int to, float t) {
        int a = lerpI((from >> 24) & 0xFF, (to >> 24) & 0xFF, t);
        int r = lerpI((from >> 16) & 0xFF, (to >> 16) & 0xFF, t);
        int gv = lerpI((from >> 8) & 0xFF, (to >> 8) & 0xFF, t);
        int b = lerpI(from & 0xFF, to & 0xFF, t);
        return (a << 24) | (r << 16) | (gv << 8) | b;
    }

    private static int lerpI(int from, int to, float t) {
        return (int) (from + (to - from) * Math.max(0, Math.min(1, t)));
    }

    private static float lerp(float from, float to, float t) {
        return from + (to - from) * Math.max(0, Math.min(1, t));
    }
}
