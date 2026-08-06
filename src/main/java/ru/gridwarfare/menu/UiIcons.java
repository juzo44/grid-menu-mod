package ru.gridwarfare.menu;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Векторные иконки для кнопок и соц. сетей.
 * Все рисуются прямоугольниками (fill), без текстур.
 * Центр иконки — точка (cx, cy), size — полный размер.
 */
public final class UiIcons {
    private UiIcons() {}

    /* ═══ ТРЕУГОЛЬНИК «PLAY» (SVG: M8 5v14l11-7z в 24×24) ═══ */
    public static void play(GuiGraphics g, int cx, int cy, int size, int color) {
        float scale = (float) size / 24f;
        float ox = cx - size / 2f;
        float oy = cy - size / 2f;
        float x0 = ox + 8 * scale;
        float y0 = oy + 5 * scale;
        float x1 = ox + 8 * scale;
        float y1 = oy + 19 * scale;
        float x2 = ox + 19 * scale;
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

    /* ═══ ГАЛОЧКА «CHECK» (SVG: M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z в 24×24) ═══ */
    public static void check(GuiGraphics g, int cx, int cy, int size, int color) {
        float scale = (float) size / 24f;
        float ox = cx - size / 2f;
        float oy = cy - size / 2f;
        // SVG path segments: (9,16.17) → (4.83,12) → (3.41,13.41) → (9,19) → (21,7) → (19.59,5.59)
        // Simplified as two line segments forming the checkmark:
        // Segment 1: (4.83, 12) → (9, 16.17) [short arm going down-right]
        // Segment 2: (9, 16.17) → (19.59, 5.59) [long arm going up-right]
        float ax = ox + 4.83f * scale, ay = oy + 12f * scale;     // start of short arm
        float bx = ox + 9f * scale,    by = oy + 16.17f * scale;  // corner (bottom of check)
        float dx = ox + 19.59f * scale, dy = oy + 5.59f * scale;   // end of long arm
        int thick = Math.max(1, (int)(size * 0.12f)); // line thickness proportional to size
        bresenhamThick(g, (int)ax, (int)ay, (int)bx, (int)by, color, thick);
        bresenhamThick(g, (int)bx, (int)by, (int)dx, (int)dy, color, thick);
    }

    /* ═══ ШЕСТЕРЁНКА «SETTINGS» (SVG gear icon в 24×24) ═══ */
    public static void sliders(GuiGraphics g, int cx, int cy, int size, int color) {
        float scale = (float) size / 24f;
        float ox = cx - size / 2f;
        float oy = cy - size / 2f;
        // Outer gear body: 8 points around a circle at radius 9, with teeth extending to 12
        // Centered at (12, 12) in SVG coords
        int centerSx = cx;
        int centerSy = cy;
        int outerR = (int)(12 * scale);
        int innerR = (int)(7.5 * scale);
        int toothH = (int)(2.5 * scale);
        int holeR = (int)(3.5 * scale);

        // Draw gear body (filled circle with radius ~outerR-toothH)
        int bodyR = outerR - toothH / 2;
        fillCircle(g, centerSx, centerSy, bodyR + toothH / 2, color);

        // Cut out teeth notches (draw background-colored rectangles at 45° intervals)
        // Actually for simplicity, draw the gear as a circle + teeth rectangles
        // Teeth: 8 rectangles around the circle
        for (int i = 0; i < 8; i++) {
            double angle = Math.toRadians(i * 45);
            int toothW = (int)(4 * scale);
            int toothLen = outerR;
            int tx = centerSx + (int)(Math.cos(angle) * (innerR));
            int ty = centerSy + (int)(Math.sin(angle) * (innerR));
            // Each tooth is a small rectangle extending outward
            int ex = centerSx + (int)(Math.cos(angle) * toothLen);
            int ey = centerSy + (int)(Math.sin(angle) * toothLen);
            // Draw thick line from inner to outer
            bresenhamThick(g, tx, ty, ex, ey, color, toothW);
        }

        // Center hole (draw a slightly darker circle to suggest the hole)
        // Since we can't easily cut, we skip the hole for simplicity at small sizes
    }

    /* ═══ ИКОНКА «INFO» (i в круге — SVG: circle + rect) ═══ */
    public static void info(GuiGraphics g, int cx, int cy, int size, int color) {
        float scale = (float) size / 24f;
        float ox = cx - size / 2f;
        float oy = cy - size / 2f;
        // SVG: M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2z
        // Circle outline
        int r = (int)(10 * scale);
        int thick = Math.max(1, (int)(size * 0.08f));
        drawCircleOutline(g, cx, cy, r, thick, color);
        // Dot at top (i dot): center at (12, 8) in SVG → scaled
        int dotR = Math.max(1, (int)(1.5 * scale));
        fillCircle(g, cx, cy - (int)(4 * scale), dotR, color);
        // Body (i stem): rect from (11, 11) to (13, 17) in SVG
        int stemW = Math.max(1, (int)(2 * scale));
        int stemTop = cy - (int)(1 * scale);
        int stemBot = cy + (int)(5 * scale);
        g.fill(cx - stemW / 2, stemTop, cx + stemW / 2 + (stemW % 2), stemBot, color);
    }

    /* ═══ СУМКА «SHOP» (SVG shopping bag path) ═══ */
    public static void bag(GuiGraphics g, int cx, int cy, int size, int color) {
        float scale = (float) size / 24f;
        float ox = cx - size / 2f;
        float oy = cy - size / 2f;
        // SVG: M7 18c-1.1 0-1.99.9-1.99 2S5.9 22 7 22s2-.9 2-2-.9-2-2-2z
        // Plus the bag body and handle
        // Simplified: draw bag body + handle
        int bodyL = cx - (int)(8 * scale);
        int bodyR = cx + (int)(8 * scale);
        int bodyT = cy - (int)(2 * scale);
        int bodyB = cy + (int)(8 * scale);
        // Main bag body
        g.fill(bodyL, bodyT, bodyR, bodyB, color);
        // Handle arc (two vertical lines + horizontal line)
        int handleW = Math.max(1, (int)(1.5 * scale));
        int handleH = (int)(5 * scale);
        int handleL = cx - (int)(4 * scale);
        int handleR = cx + (int)(4 * scale);
        int handleT = bodyT - handleH;
        // Left side of handle
        g.fill(handleL, handleT, handleL + handleW, bodyT, color);
        // Right side of handle
        g.fill(handleR - handleW, handleT, handleR, bodyT, color);
        // Bottom wheels (two small squares)
        int wheelR = Math.max(1, (int)(2 * scale));
        fillCircle(g, cx - (int)(5 * scale), bodyB, wheelR, color);
        fillCircle(g, cx + (int)(5 * scale), bodyB, wheelR, color);
    }

    /* ═══ ВЫХОД «EXIT» (SVG: door + arrow) ═══ */
    public static void exit(GuiGraphics g, int cx, int cy, int size, int color) {
        float scale = (float) size / 24f;
        float ox = cx - size / 2f;
        float oy = cy - size / 2f;
        // SVG: M10.09 15.59L11.5 17l5-5-5-5-1.41 1.41L12.67 11H3v2h9.67l-2.58 2.59z
        // Plus: M19 3H5c-1.11 0-2 .9-2 2v4h2V5h14v14H5v-4H3v4c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2z
        int thick = Math.max(1, (int)(1.5 * scale));
        // Door frame
        int doorL = cx - (int)(8 * scale);
        int doorR = cx + (int)(1 * scale);
        int doorT = cy - (int)(8 * scale);
        int doorB = cy + (int)(8 * scale);
        // Left wall
        g.fill(doorL, doorT, doorL + thick, doorB, color);
        // Top wall
        g.fill(doorL, doorT, doorR, doorT + thick, color);
        // Bottom wall
        g.fill(doorL, doorB - thick, doorR, doorB, color);
        // Arrow (from left-center to right)
        int arrowY = cy;
        int arrowStartX = cx - (int)(4.5 * scale);
        int arrowEndX = cx + (int)(8 * scale);
        // Arrow shaft
        int shaftH = thick;
        g.fill(arrowStartX, arrowY - shaftH / 2, arrowEndX - (int)(4 * scale), arrowY + shaftH / 2 + (shaftH % 2), color);
        // Arrow head (triangle pointing right)
        int headTipX = arrowEndX;
        int headBaseX = arrowEndX - (int)(5 * scale);
        int headHalfH = (int)(4 * scale);
        for (int row = arrowY - headHalfH; row <= arrowY + headHalfH; row++) {
            float t = (float)(row - (arrowY - headHalfH)) / (float)(headHalfH * 2);
            int inset = (int)((1f - Math.abs(t - 0.5f) * 2f) * (headTipX - headBaseX));
            int lx = headBaseX + (headTipX - headBaseX) - inset;
            g.fill(lx, row, headTipX, row + 1, color);
        }
    }

    /* ═══ TELEGRAM (paper airplane) ═══ */
    public static void telegram(GuiGraphics g, int cx, int cy, int size, int color) {
        // Simplified paper airplane centered at (cx, cy)
        int half = size / 2;
        // Main triangle body (pointing upper-right)
        int tipX = cx + half - 1;
        int tipY = cy - half + 2;
        int leftX = cx - half + 2;
        int leftY = cy + 1;
        int rightX = cx - 1;
        int rightY = cy + half - 2;
        fillTriangle(g, leftX, leftY, tipX, tipY, rightX, rightY, color);
        // Tail fold (small triangle at bottom-left)
        int tailX = cx - half + 2;
        int tailY = cy + half - 2;
        int midX = cx - 1;
        int midY = cy + 1;
        fillTriangle(g, tailX, tailY, midX, midY, rightX, rightY, color);
    }

    /* ═══ DISCORD (game controller shape) ═══ */
    public static void discord(GuiGraphics g, int cx, int cy, int size, int color) {
        int half = size / 2;
        // Simplified Discord icon: circle with inner details
        // Main circle outline
        int r = half - 1;
        drawCircleOutline(g, cx, cy, r, Math.max(1, size / 8), color);
        // Inner face: two dots for eyes and a curved mouth
        int eyeR = Math.max(1, size / 10);
        int eyeSpacing = size / 5;
        fillCircle(g, cx - eyeSpacing, cy - size / 8, eyeR, color);
        fillCircle(g, cx + eyeSpacing, cy - size / 8, eyeR, color);
        // Simple mouth line
        int mouthW = size / 3;
        int mouthY = cy + size / 6;
        g.fill(cx - mouthW / 2, mouthY, cx + mouthW / 2, mouthY + Math.max(1, size / 12), color);
    }

    /* ═══ GLOBE (world icon) ═══ */
    public static void globe(GuiGraphics g, int cx, int cy, int size, int color) {
        int r = size / 2 - 1;
        int thick = Math.max(1, size / 10);
        // Outer circle
        drawCircleOutline(g, cx, cy, r, thick, color);
        // Horizontal meridian (equator)
        g.fill(cx - r + 1, cy - thick / 2, cx + r - 1, cy + thick / 2 + (thick % 2), color);
        // Vertical meridian
        g.fill(cx - thick / 2, cy - r + 1, cx + thick / 2 + (thick % 2), cy + r - 1, color);
    }

    /* ═══════════════════════════
       УТИЛИТЫ РЕНДЕРИНГА
       ═══════════════════════════ */

    /** Заполненный треугольник (scanline). */
    private static void fillTriangle(GuiGraphics g, int x0, int y0, int x1, int y1, int x2, int y2, int color) {
        int topY = Math.min(y0, Math.min(y1, y2));
        int botY = Math.max(y0, Math.max(y1, y2));
        for (int row = topY; row <= botY; row++) {
            float y = row + 0.5f;
            int leftX = Integer.MAX_VALUE, rightX = Integer.MIN_VALUE;
            // Edge 0→1
            if ((y0 <= y && y1 > y0) || (y1 <= y && y0 > y1)) {
                int ex = (int) (x0 + (x1 - x0) * (y - y0) / (float)(y1 - y0));
                leftX = Math.min(leftX, ex); rightX = Math.max(rightX, ex);
            }
            // Edge 1→2
            if ((y1 <= y && y2 > y1) || (y2 <= y && y1 > y2)) {
                int ex = (int) (x1 + (x2 - x1) * (y - y1) / (float)(y2 - y1));
                leftX = Math.min(leftX, ex); rightX = Math.max(rightX, ex);
            }
            // Edge 2→0
            if ((y2 <= y && y0 > y2) || (y0 <= y && y2 > y0)) {
                int ex = (int) (x2 + (x0 - x2) * (y - y2) / (float)(y0 - y2));
                leftX = Math.min(leftX, ex); rightX = Math.max(rightX, ex);
            }
            if (rightX > leftX) {
                g.fill(leftX, row, rightX, row + 1, color);
            }
        }
    }

    /** Заполненный круг. */
    private static void fillCircle(GuiGraphics g, int cx, int cy, int r, int color) {
        if (r < 1) { g.fill(cx, cy, cx + 1, cy + 1, color); return; }
        for (int dy = -r; dy <= r; dy++) {
            int dx = (int) Math.sqrt(r * r - dy * dy);
            g.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
        }
    }

    /** Контур круга. */
    private static void drawCircleOutline(GuiGraphics g, int cx, int cy, int r, int thick, int color) {
        if (r < 1 || thick < 1) return;
        int outerR = r + thick / 2;
        int innerR = r - thick / 2;
        if (innerR < 0) innerR = 0;
        for (int dy = -outerR; dy <= outerR; dy++) {
            int outerDx = (int) Math.sqrt(Math.max(0, outerR * outerR - dy * dy));
            int innerDx = innerR > 0 ? (int) Math.sqrt(Math.max(0, innerR * innerR - dy * dy)) : 0;
            // Left arc
            if (outerDx > innerDx) {
                g.fill(cx - outerDx, cy + dy, cx - innerDx, cy + dy + 1, color);
            }
            // Right arc
            if (outerDx > innerDx) {
                g.fill(cx + innerDx, cy + dy, cx + outerDx + 1, cy + dy + 1, color);
            }
        }
    }

    /** Толстая линия Брезенхема. */
    private static void bresenhamThick(GuiGraphics g, int x0, int y0, int x1, int y1, int color, int thickness) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        int x = x0, y = y0;
        int half = thickness / 2;
        for (int i = 0; i < 500; i++) {
            g.fill(x - half, y - half, x + half + 1, y + half + 1, color);
            if (x == x1 && y == y1) break;
            int e2 = 2 * err;
            if (e2 > -dy) { err -= dy; x += sx; }
            if (e2 < dx) { err += dx; y += sy; }
        }
    }
}