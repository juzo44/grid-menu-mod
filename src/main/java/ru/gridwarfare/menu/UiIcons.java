package ru.gridwarfare.menu;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Векторные иконки для кнопок меню.
 * Все рисуются прямоугольниками (fill), без текстур.
 * Центр иконки — точка (cx, cy), size — полный размер.
 */
public final class UiIcons {
    private UiIcons() {}

    /* ═══ ТРЕУГОЛЬНИК «PLAY» (SVG: M8 5v14l11-7z в 24×24) ═══ */
    public static void play(GuiGraphics g, int cx, int cy, int size, int color) {
        // SVG вершины: (8,5), (8,19), (19,12) — левая грань + правый пик
        float scale = (float) size / 24f;
        float ox = cx - size / 2f;
        float oy = cy - size / 2f;
        // Вершины в экранных координатах
        float x0 = ox + 8 * scale;   // левая верх
        float y0 = oy + 5 * scale;
        float x1 = ox + 8 * scale;   // левая низ
        float y1 = oy + 19 * scale;
        float x2 = ox + 19 * scale;  // правый пик
        float y2 = oy + 12 * scale;
        int topY = (int) Math.min(y0, Math.min(y1, y2));
        int botY = (int) Math.max(y0, Math.max(y1, y2));
        for (int row = topY; row <= botY; row++) {
            float y = row + 0.5f;
            int leftX = (int) x0;
            int rightX;
            if (y <= y2) {
                rightX = (int) (x0 + (x2 - x0) * (y - y0) / (y2 - y0));
            } else {
                rightX = (int) (x2 + (x1 - x2) * (y - y2) / (y1 - y2));
            }
            if (rightX > leftX) {
                g.fill(leftX, row, rightX, row + 1, color);
            }
        }
    }

    /* ═══ ГАЛОЧКА «CHECK» (для Одиночный мир) ═══ */
    public static void check(GuiGraphics g, int cx, int cy, int size, int color) {
        int h = size / 2;
        // Галочка: (cx-h, cy) → (cx-h/3, cy+h/2.5) → (cx+h, cy-h)
        bresenhamThick(g, cx - h, cy, cx - h / 3, cy + h * 2 / 5, color, 2);
        bresenhamThick(g, cx - h / 3, cy + h * 2 / 5, cx + h, cy - h, color, 2);
    }

    /* ═══ ШЕСТЕРЁНКА «SETTINGS» ═══ */
    public static void sliders(GuiGraphics g, int cx, int cy, int size, int color) {
        int half = size / 2;
        int lineW = size - 4;
        int gap = (size - 6) / 3;
        int knobW = Math.max(3, size * 7 / 22);
        int knobH = Math.max(2, size / 5);
        int lineH = Math.max(1, size / 11);

        int startY = cy - half + 3;
        // Три ползунка с разными позициями бегунков
        for (int i = 0; i < 3; i++) {
            int ly = startY + i * (gap + lineH);
            // Линия
            g.fill(cx - lineW / 2, ly, cx + lineW / 2, ly + lineH, color);
            // Бегунок (разная позиция для каждого)
            int knobOffsets[] = {lineW * 3 / 8, -lineW * 2 / 8, lineW / 8};
            int kx = cx + knobOffsets[i] - knobW / 2;
            g.fill(kx, ly - (knobH - lineH) / 2, kx + knobW, ly + (knobH - lineH) / 2 + lineH, color);
        }
    }

    /* ═══ ИКОНКА «INFO» (буква i в круге) ═══ */
    public static void info(GuiGraphics g, int cx, int cy, int size, int color) {
        int half = size / 2;
        int dotH = Math.max(2, size / 5);
        int dotW = Math.max(3, size / 4);
        // Точка сверху
        g.fill(cx - dotW / 2, cy - half + 1, cx + dotW / 2, cy - half + 1 + dotH, color);
        // Тело снизу (прямоугольник без скруглений — минимализм)
        int bodyTop = cy - half + dotH + 3;
        g.fill(cx - dotW / 2, bodyTop, cx + dotW / 2, cy + half - 1, color);
    }

    /* ═══ СУМКА «SHOP» ═══ */
    public static void bag(GuiGraphics g, int cx, int cy, int size, int color) {
        int half = size / 2;
        int bodyW = size - 4;
        int bodyH = size * 3 / 5;
        int bodyTop = cy - bodyH / 2 + 2;
        // Ручка (маленькая дуга сверху)
        int handleW = bodyW * 5 / 10;
        int handleH = size * 2 / 8;
        g.fill(cx - handleW / 2, bodyTop - handleH, cx - handleW / 2 + 2, bodyTop, color);
        g.fill(cx + handleW / 2 - 2, bodyTop - handleH, cx + handleW / 2, bodyTop, color);
        // Тело сумки
        g.fill(cx - bodyW / 2, bodyTop, cx + bodyW / 2, bodyTop + bodyH, color);
    }

    /* ═══ ВЫХОД «EXIT» (стрелка из двери) ═══ */
    public static void exit(GuiGraphics g, int cx, int cy, int size, int color) {
        int half = size / 2;
        // Дверная рамка (прямоугольник, открытая справа)
        int frameW = 2;
        int frameH = size - 4;
        int fx = cx - half + 2;
        int fy = cy - half + 2;
        // Левая стенка
        g.fill(fx, fy, fx + frameW, fy + frameH, color);
        // Верхняя перекладина
        g.fill(fx, fy, cx + 1, fy + frameW, color);
        // Нижняя перекладина
        g.fill(fx, fy + frameH - frameW, cx + 1, fy + frameH, color);
        // Стрелка → (в правой части)
        int arrowX = cx - 1;
        int arrowLen = half - 3;
        // Горизонтальная черта стрелки
        g.fill(arrowX - arrowLen, cy - 1, arrowX + 3, cy + 2, color);
        // Верхний луч стрелки
        for (int i = 0; i < Math.min(5, arrowLen); i++) {
            g.fill(arrowX - i, cy - 2 - i, arrowX - i + 2, cy - i, color);
        }
        // Нижний луч стрелки
        for (int i = 0; i < Math.min(5, arrowLen); i++) {
            g.fill(arrowX - i, cy + 2 + i, arrowX - i + 2, cy + i + 2, color);
        }
    }

    /* ═══ УТИЛИТА: толстая линия Брезенхема ═══ */
    private static void bresenhamThick(GuiGraphics g, int x0, int y0, int x1, int y1, int color, int thickness) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        int x = x0, y = y0;
        int half = thickness / 2;
        for (int i = 0; i < 200; i++) {
            g.fill(x - half, y - half, x + half + 1, y + half + 1, color);
            if (x == x1 && y == y1) break;
            int e2 = 2 * err;
            if (e2 > -dy) { err -= dy; x += sx; }
            if (e2 < dx) { err += dx; y += sy; }
        }
    }
}
