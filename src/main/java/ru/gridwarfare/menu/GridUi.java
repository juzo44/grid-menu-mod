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

    private static ResourceLocation tex(String path) {
        return ResourceLocation.fromNamespaceAndPath(GridMenu.MOD_ID, "textures/gui/" + path);
    }

    public static final ResourceLocation ICON_TG  = tex("ui/circle_tg_n.png");
    public static final ResourceLocation ICON_TG_H = tex("ui/circle_tg_h.png");
    public static final ResourceLocation ICON_DC  = tex("ui/circle_dc_n.png");
    public static final ResourceLocation ICON_DC_H = tex("ui/circle_dc_h.png");
    public static final ResourceLocation ICON_GL  = tex("ui/circle_gl_n.png");
    public static final ResourceLocation ICON_GL_H = tex("ui/circle_gl_h.png");

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
    public static final int BTN_PRIMARY_BG    = ACCENT;
    public static final int BTN_PRIMARY_HOVER = ACCENT_HOVER;
    public static final int BTN_SEC_BG        = 0xBF0C100E; // rgba(12,16,14,0.75)
    public static final int BTN_SEC_HOVER     = 0xD9121814; // rgba(18,24,20,0.85)
    public static final int BTN_SM_BG         = 0xA60C100E; // rgba(12,16,14,0.65)
    public static final int BTN_SM_HOVER      = 0xCC121814; // rgba(18,24,20,0.80)

    private GridUi() {}

    /* ═══ ШРИФТ ═══ */
    public static Component styled(String text) {
        return Component.literal(text).setStyle(Style.EMPTY.withFont(FONT));
    }

    /* ═══════════════════════════
       ФОН (bg-photo + overlay + vignette)
       ═══════════════════════════ */
    public static void background(GuiGraphics g, int w, int h) {
        // 1) Текстура (cover)
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.blit(BG_TEXTURE, 0, 0, 0, 0, w, h, w, h);

        // 2) Градиент-оверлей: 72% → 50% → 68% (из CSS linear-gradient)
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

        // 3) Виньетка — 4 края, по 80 полос каждый (~320 fill вместо 130K)
        int vigMax = (int) (0.50 * 255);
        int bands = 80;
        for (int i = 0; i < bands; i++) {
            float t = 1f - (float) i / bands;
            int a = (int) (t * t * vigMax);
            if (a < 1) continue;
            int c = a << 24;
            g.fill(0, i, w, i + 1, c);                // верх
            g.fill(0, h - 1 - i, w, h - i, c);       // низ
            g.fill(i, 0, i + 1, h, c);                 // лево
            g.fill(w - 1 - i, 0, w - i, h, c);        // право
        }
    }

    /* ═══════════════════════════
       БРЕНД-МАРКА (32x32 с обрезанными углами)
       ═══════════════════════════ */
    public static void brandMark(GuiGraphics g, int x, int y, int size) {
        clippedCorners(g, x, y, size, size, 5, ACCENT);
        var fnt = Minecraft.getInstance().font;
        g.drawCenteredString(fnt, styled("G"), x + size / 2, y + size / 2 - 4, BG_DEEP);
    }

    /** Рисует прямоугольник с обрезанными по диагонали углами (clip-path: polygon(...)). */
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
       ПАНЕЛИ (border + inset fill)
       ═══════════════════════════ */
    public static void panel(GuiGraphics g, int x, int y, int w, int h, int radius) {
        // Бордер (LINE_COLOR)
        roundedRect(g, x, y, w, h, radius, LINE_COLOR);
        // Фон (PANEL_BG, inset 1px)
        roundedRect(g, x + 1, y + 1, w - 2, h - 2, Math.max(1, radius - 1), PANEL_BG);
    }

    /** Панель с радиусом 10px (по умолчанию из мокапа). */
    public static void card(GuiGraphics g, int x, int y, int w, int h) {
        panel(g, x, y, w, h, 10);
    }

    /* ═══════════════════════════
       СКРУГЛЁННЫЙ ПРЯМОУГОЛЬНИК
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
       УТИЛИТЫ ЦВЕТА
       ═══════════════════════════ */
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

    /* ═══════════════════════════
       УТИЛИТЫ ЛЕЙАУТА
       ═══════════════════════════ */

    /** Отступ от краёв экрана, адаптивный (40px @ 1920, минимум 24px). */
    public static int pad(int screenWidth) {
        return Math.max(24, Math.min(120, screenWidth * 8 / 100));
    }

    /** Ширина меню-колонки (макс 440px, адаптивная). */
    public static int menuWidth(int screenWidth, int rightColW, int pad) {
        return Math.min(440, (screenWidth - pad * 2 - rightColW - 40) * 45 / 100);
    }

    /** X-позиция меню-колонки (центрирована в доступном пространстве). */
    public static int menuX(int screenWidth, int menuW, int rightColW, int pad) {
        int available = screenWidth - pad * 2 - rightColW - 40;
        return pad + (available - menuW) / 2;
    }
}
