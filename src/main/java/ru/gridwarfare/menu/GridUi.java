package ru.gridwarfare.menu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

/** Отрисовка в стиле сайта GRID: hero-фон с картинкой, тёмные градиенты, зелёный акцент, минимализм. */
public final class GridUi {
    public static final ResourceLocation BG_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(GridMenu.MOD_ID, "textures/gui/ui/bg_menu.png");

    /** Кастомный шрифт Inter (font/grid.json). */
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

    private static final int BG_W = 1280;
    private static final int BG_H = 720;

    /** Текст с кастомным шрифтом GRID. */
    public static Component styled(String text) {
        return Component.literal(text).setStyle(Style.EMPTY.withFont(FONT));
    }

    public static final int GREEN = 0xFF78EE75;
    public static final int TEXT_MAIN = 0xFFF3F6F3;
    public static final int TEXT_MUTED = 0xFF8B978F;
    public static final int PANEL_BG = 0xDB0C100E;
    public static final int PANEL_LINE = 0xFF344038;
    public static final int BUTTON_LINE = 0xFF49544D;

    private GridUi() {
    }

    /** Фон главного экрана: тёплый тёмный градиент, заметные мягкие пятна и тонкая сетка. */
    public static void background(GuiGraphics g, int w, int h) {
        for (int y = 0; y < h; y++) {
            float t = (float) y / Math.max(1, h - 1);
            g.fill(0, y, w, y + 1, lerpColor(0xFF0B0F0C, 0xFF1E2620, t));
        }

        spotlight(g, w / 2, (int) (h * 0.34), (int) (w * 0.65), 55, 0xFF354C3C);
        spotlight(g, (int) (w * 0.88), (int) (h * 0.18), (int) (w * 0.4), 32, 0xFF2A3B2E);
        spotlight(g, (int) (w * 0.08), (int) (h * 0.9), (int) (w * 0.45), 36, 0xFF24322A);

        drawGrid(g, w, h);
    }

    /** Мягкое световое пятно (несколько полупрозрачных слоёв от центра до края). */
    private static void spotlight(GuiGraphics g, int cx, int cy, int radius, int maxAlpha, int color) {
        int layers = 32;
        for (int i = layers; i >= 1; i--) {
            float t = (float) i / layers;
            int size = (int) (radius * t);
            int alpha = (int) (maxAlpha * (1 - t) * (1 - t));
            int rgb = color & 0xFFFFFF;
            g.fill(cx - size, cy - size, cx + size, cy + size, (alpha << 24) | rgb);
        }
    }

    private static void drawGrid(GuiGraphics g, int w, int h) {
        int cell = 96;
        int color = 0x1A2B3A2E;
        for (int x = 0; x < w; x += cell) {
            g.fill(x, 0, x + 1, h, color);
        }
        for (int y = 0; y < h; y += cell) {
            g.fill(0, y, w, y + 1, color);
        }
    }

    /** Марка бренда как на сайте: зелёная рамка со срезанными углами и буквой. */
    public static void brandMark(GuiGraphics g, int x, int y, int size) {
        clippedSquare(g, x, y, size, GREEN);
        clippedSquare(g, x + 3, y + 3, size - 6, 0xFF0A0D0C);
        var font = net.minecraft.client.Minecraft.getInstance().font;
        g.drawCenteredString(font, styled("G"), x + size / 2, y + size / 2 - 4, GREEN);
    }

    /** Тёмная марка с зелёной буквой (для тёмных панелей). */
    public static void brandMarkDark(GuiGraphics g, int x, int y, int size) {
        clippedSquare(g, x, y, size, PANEL_LINE);
        clippedSquare(g, x + 2, y + 2, size - 4, 0xFF101613);
        var font = net.minecraft.client.Minecraft.getInstance().font;
        g.drawCenteredString(font, styled("G"), x + size / 2, y + size / 2 - 4, GREEN);
    }

    /**
     * Угловатая марка как у логотипа сайта (срезанные углы).
     * clip-path: polygon(15% 0, 100% 0, 100% 85%, 85% 100%, 0 100%, 0 15%).
     */
    public static void clippedSquare(GuiGraphics g, int x, int y, int size, int color) {
        int cut = Math.max(1, size * 15 / 100);
        for (int row = 0; row < size; row++) {
            int left = 0;
            int right = size;
            if (row < cut) {
                left = cut - row;
                right = size;
            } else if (row >= size - cut) {
                left = 0;
                right = size - (row - (size - cut)) - 1;
            }
            if (left < right) g.fill(x + left, y + row, x + right, y + row + 1, color);
        }
    }

    /** Сглаженный прямоугольник с рамкой (панель). */
    public static void panel(GuiGraphics g, int x, int y, int w, int h, int radius) {
        roundedRect(g, x, y, w, h, radius, PANEL_LINE);
        roundedRect(g, x + 1, y + 1, w - 2, h - 2, Math.max(1, radius - 1), PANEL_BG);
    }

    /** Панель с заданным радиусом и цветами (карточка сервера). */
    public static void card(GuiGraphics g, int x, int y, int w, int h) {
        roundedRect(g, x, y, w, h, 0, PANEL_LINE);
        roundedRect(g, x + 1, y + 1, w - 2, h - 2, 0, PANEL_BG);
    }

    private static void roundedRect(GuiGraphics g, int x, int y, int w, int h, int radius, int color) {
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

    public static int lerpColor(int from, int to, float t) {
        int a = lerp((from >> 24) & 0xFF, (to >> 24) & 0xFF, t);
        int r = lerp((from >> 16) & 0xFF, (to >> 16) & 0xFF, t);
        int g = lerp((from >> 8) & 0xFF, (to >> 8) & 0xFF, t);
        int b = lerp(from & 0xFF, to & 0xFF, t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int rgba(float alpha, int r, int g, int b) {
        int a = (int) (Math.max(0, Math.min(1, alpha)) * 255);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int lerp(int from, int to, float t) {
        return (int) (from + (to - from) * Math.max(0, Math.min(1, t)));
    }

    private static float lerp(float from, float to, float t) {
        return from + (to - from) * Math.max(0, Math.min(1, t));
    }
}
