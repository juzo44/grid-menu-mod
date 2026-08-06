package ru.gridwarfare.menu;

import net.minecraft.client.gui.GuiGraphics;

/** Простые векторные иконки для меню (рисуются прямоугольниками, без текстур). */
public final class UiIcons {
    private UiIcons() {
    }

    public static void play(GuiGraphics g, int cx, int cy, int size, int color) {
        int half = size / 2;
        for (int i = 0; i < size; i++) {
            int h2 = Math.max(1, (i + 1) * half / size);
            g.fill(cx - half + i, cy - h2, cx - half + i + 1, cy + h2, color);
        }
    }

    public static void check(GuiGraphics g, int cx, int cy, int size, int color) {
        line(g, cx - size / 2, cy, cx - size / 6, cy + size / 3, color);
        line(g, cx - size / 6, cy + size / 3, cx + size / 2, cy - size / 2, color);
    }

    public static void sliders(GuiGraphics g, int cx, int cy, int size, int color) {
        int half = size / 2;
        int[] knobs = {-half + 3, half - 8, -half / 2};
        for (int row = 0; row < 3; row++) {
            int ly = cy - half + 3 + row * 7;
            g.fill(cx - half, ly, cx + half, ly + 2, color);
            int kx = cx - half + knobs[row];
            g.fill(kx, ly - 2, kx + 7, ly + 4, color);
        }
    }

    public static void info(GuiGraphics g, int cx, int cy, int size, int color) {
        int half = size / 2;
        g.fill(cx - 1, cy - half + 1, cx + 2, cy - half + 5, color);
        g.fill(cx - 1, cy - 2, cx + 2, cy + half - 1, color);
    }

    public static void bag(GuiGraphics g, int cx, int cy, int size, int color) {
        int half = size / 2;
        g.fill(cx - 2, cy - half, cx + 3, cy - half + 4, color);
        g.fill(cx - half, cy - half + 4, cx + half, cy + half, color);
    }

    public static void exit(GuiGraphics g, int cx, int cy, int size, int color) {
        int half = size / 2;
        g.fill(cx - half, cy - 1, cx + half, cy + 2, color);
        for (int i = 0; i < 5; i++) {
            g.fill(cx + half - 5 + i, cy - 2 + i, cx + half - 5 + i + 1, cy + 3 - i, color);
        }
    }

    private static void line(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        int x = x0;
        int y = y0;
        for (int i = 0; i < 80; i++) {
            g.fill(x, y, x + 2, y + 2, color);
            if (x == x1 && y == y1) {
                break;
            }
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x += sx;
            }
            if (e2 < dx) {
                err += dx;
                y += sy;
            }
        }
    }
}
