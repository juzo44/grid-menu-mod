package ru.gridwarfare.menu;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

/**
 * Дизайн-система GRID — точный порт CSS-мокапа.
 * Все цвета, шрифты, утилиты рендеринга в одном месте.
 */
public final class GridUi {

    /* ═══ РЕСУРСЫ ═══ */
    public static final ResourceLocation BG_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(GridMenu.MOD_ID, "textures/gui/ui/bg_menu.png");
    public static final ResourceLocation FONT =
            ResourceLocation.fromNamespaceAndPath(GridMenu.MOD_ID, "grid");

    /* ═══ ЦВЕТА (из CSS :root) ═══ */
    public static final int ACCENT        = 0xFF68C284;
    public static final int ACCENT_HOVER  = 0xFF7CD090;
    public static final int ACCENT_DARK   = 0xFF4A9C66;
    public static final int ACCENT_DARKER = 0xFF387A50;
    public static final int BG_DEEP       = 0xFF0B0F0C;
    public static final int TEXT_MAIN     = 0xFFF3F6F3;
    public static final int TEXT_MUTED    = 0xFF8B978F;
    public static final int TEXT_DIM      = 0xFF5A655E;
    public static final int LINE_COLOR    = 0xFF344038;
    public static final int PANEL_BG      = 0xD10C100E;   // rgba(12,16,14,0.82)
    public static final int ACCENT_DIM    = 0x2668C284;   // rgba(104,194,132,0.15)
    public static final int ACCENT_BORDER = 0x4D68C284;   // rgba(104,194,132,0.30)

    /* Готовые составные цвета для кнопок */
    public static final int BTN_SEC_BG        = 0xBF0C100E; // rgba(12,16,14,0.75)
    public static final int BTN_SEC_HOVER     = 0xD9121814; // rgba(18,24,20,0.85)
    public static final int BTN_SM_BG         = 0xA60C100E; // rgba(12,16,14,0.65)
    public static final int BTN_SM_HOVER      = 0xCC121814; // rgba(18,24,20,0.80)

    /* ═══ МАСШТАБ ═══ */
    /** CSS-designed for 1920×1080. This scales all layout values to actual GUI resolution. */
    public static float S = 1f;
    public static int s(int cssPx) { return Math.max(1, (int)(cssPx * S)); }

    private GridUi() {}

    /* ═══ ШРИФТ ═══ */
    public static Component styled(String text) {
        return Component.literal(text).setStyle(Style.EMPTY.withFont(FONT));
    }

    /* ═════════════════════════
       ФОН (bg-photo + overlay + vignette)
       CSS: .bg-overlay linear-gradient(180deg, rgba(11,15,12,0.72) 0%, rgba(11,15,12,0.50) 40%, rgba(11,15,12,0.68) 100%)
           .bg-vignette radial-gradient(ellipse at center, transparent 40%, rgba(0,0,0,0.50) 100%)
       ═══════════════════════ */
    public static void background(GuiGraphics g, int w, int h) {
        // 1) Текстура фона (cover)
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.blit(BG_TEXTURE, 0, 0, 0, 0, w, h, w, h);

        // 2) Градиент-оверлей: 72% → 50% → 68% (CSS linear-gradient 180deg)
        for (int y = 0; y < h; y += 2) {
            float t = (float) y / Math.max(1, h - 1);
            int alpha;
            if (t < 0.4f) {
                alpha = (int)(lerp(0.72f, 0.50f, t / 0.4f) * 255f);
            } else {
                alpha = (int)(lerp(0.50f, 0.68f, (t - 0.4f) / 0.6f) * 255f);
            }
            g.fill(0, y, w, y + 2, (alpha << 24) | 0x0B0F0C);
        }

        // 3) Виньетка — 4 края + усиление углов
        // CSS: radial-gradient(ellipse at center, transparent 40%, rgba(0,0,0,0.50) 100%)
        int vigMax = (int) (0.50 * 255);
        int edgeBands = 100;
        for (int i = 0; i < edgeBands; i++) {
            float t = 1f - (float) i / edgeBands;
            int a = (int) (t * t * vigMax);
            if (a < 1) continue;
            int c = a << 24;
            g.fill(0, i, w, i + 1, c);                // верх
            g.fill(0, h - 1 - i, w, h - i, c);       // низ
            g.fill(i, 0, i + 1, h, c);                 // лево
            g.fill(w - 1 - i, 0, w - i, h, c);        // право
        }

        // Угловое усиление (имитация радиальной виньетки — углы темнее)
        int cornerSize = Math.min(w, h) / 3;
        int cornerBands = 40;
        for (int i = 0; i < cornerBands; i++) {
            float t = (float) i / cornerBands;
            int a = (int) ((1f - t) * (1f - t) * vigMax * 0.35f);
            if (a < 1) continue;
            int c = a << 24;
            int sz = (int) (cornerSize * (1f - t));
            if (sz < 1) continue;
            g.fill(0, 0, sz, sz, c);                      // top-left
            g.fill(w - sz, 0, w, sz, c);                   // top-right
            g.fill(0, h - sz, sz, h, c);                   // bottom-left
            g.fill(w - sz, h - sz, w, h, c);              // bottom-right
        }
    }

    /* ═══════════════════════════
       БРЕНД-МАРКА (32×32, clip-path: polygon(5px 0, ...), font-size 17px weight 900)
       ═════════════════════════ */
    public static void brandMark(GuiGraphics g, int x, int y, int size) {
        clippedCorners(g, x, y, size, size, 5, ACCENT);
        var fnt = Minecraft.getInstance().font;
        g.pose().pushPose();
        g.pose().translate((float) (x + size / 2), (float) (y + size / 2), 0.0F);
        float s = 17f / 9f; // CSS: font-size 17px, MC font ~9px → scale ~1.89
        g.pose().scale(s, s, 1.0F);
        g.drawCenteredString(fnt, styled("G"), 0, -4, BG_DEEP);
        g.pose().popPose();
    }

    /** Рисует прямоугольник с обрезанными по диагонали углами. */
    public static void clippedCorners(GuiGraphics g, int x, int y, int w, int h, int cut, int color) {
        for (int row = 0; row < h; row++) {
            int left = 0, right = w;
            if (row < cut) {
                left = cut - row;
            } else if (row >= h - cut) {
                right = w - (row - (h - cut)) - 1;
            }
            if (left < right) {
                g.fill(x + left, y + row, x + right, y + row + 1, color);
            }
        }
    }

    /* ═══════════════════════════
       ПАНЕЛИ (border 1px LINE_COLOR + inset 1px PANEL_BG)
       ═════════════════════════════ */
    public static void panel(GuiGraphics g, int x, int y, int w, int h, int radius) {
        roundedRect(g, x, y, w, h, radius, LINE_COLOR);
        roundedRect(g, x + 1, y + 1, w - 2, h - 2, Math.max(1, radius - 1), PANEL_BG);
    }

    public static void card(GuiGraphics g, int x, int y, int w, int h) {
        panel(g, x, y, w, h, 10);
    }

    /* ═══════════════════════════
       СКРУГЛЁННЫЙ ПРЯМОУГОЛЬНИК (scanline)
       ═══════════════════════════ */
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

    /* ═══════════════════════════
       УТИЛИТЫ
       ═════════════════════════ */
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

    public static int pad(int screenWidth) {
        return Math.max(24, Math.min(40, screenWidth * 4 / 100));
    }
}
