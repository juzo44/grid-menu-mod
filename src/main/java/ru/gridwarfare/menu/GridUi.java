package ru.gridwarfare.menu;

import com.mojang.blaze3d.platform.DynamicTexture;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

import java.util.IdentityHashMap;

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
       ФОН (bg-photo + overlay + radial vignette)
       CSS: .bg-overlay linear-gradient(180deg, rgba(11,15,12,0.72) 0%, rgba(11,15,12,0.50) 40%, rgba(11,15,12,0.68) 100%)
           .bg-vignette radial-gradient(ellipse at center, transparent 40%, rgba(0,0,0,0.50) 100%)
       ═══════════════════════════ */

    // Кэш виньеточной текстуры (пересоздаётся при изменении размера)
    private static DynamicTexture vignetteTex;
    private static ResourceLocation vignetteLoc;
    private static int vigW = 0, vigH = 0;

    public static void background(GuiGraphics g, int w, int h) {
        // 1) Текстура фона (cover)
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.blit(BG_TEXTURE, 0, 0, 0, 0, w, h, w, h);

        // 2) Градиент-оверлей: 72% → 50% → 68% (из CSS linear-gradient 180deg)
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

        // 3) Радиальная виньетка — динамическая текстура (CSS: radial-gradient ellipse, transparent 40% → rgba(0,0,0,0.50) 100%)
        ensureVignette(w, h);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.blit(vignetteLoc, 0, 0, 0, 0, w, h, w, h);
    }

    /** Создаёт (или переиспользует) текстуру радиальной виньетки. */
    private static void ensureVignette(int w, int h) {
        if (vignetteTex != null && vigW == w && vigH == h) return;

        // Удаляем старую
        if (vignetteTex != null) {
            vignetteTex.close();
            try { Minecraft.getInstance().getTextureManager().release(vignetteLoc); } catch (Throwable ignored) {}
        }

        var img = new com.mojang.blaze3d.platform.NativeImage(w, h, false);
        int cx = w / 2, cy = h / 2;
        float rx = w / 2f, ry = h / 2f;

        for (int y = 0; y < h; y++) {
            float dy = (y - cy) / ry;
            float dy2 = dy * dy;
            for (int x = 0; x < w; x++) {
                float dx = (x - cx) / rx;
                float dist = (float) Math.sqrt(dx * dx + dy2);
                int alpha = 0;
                if (dist > 0.4f) {
                    // Линейная интерполяция от 0 до 128 (50% из 255)
                    alpha = Math.min(128, (int) ((dist - 0.4f) / 0.6f * 128f));
                }
                // ARGB формат: alpha в старшем байте, R=G=B=0 (чёрный)
                img.setPixelRGBA(x, y, alpha << 24);
            }
        }

        vignetteTex = new DynamicTexture(img);
        vigW = w;
        vigH = h;
        vignetteLoc = ResourceLocation.fromNamespaceAndPath(GridMenu.MOD_ID, "dynamic/vignette");
        Minecraft.getInstance().getTextureManager().register(vignetteLoc, vignetteTex);
    }

    /* ═══════════════════════════
       БРЕНД-МАРКА (32×32, clip-path: polygon(5px 0, ...), font-size 17px weight 900)
       ═══════════════════════════ */
    public static void brandMark(GuiGraphics g, int x, int y, int size) {
        // CSS: clip-path: polygon(5px 0, calc(100% - 5px) 0, 100% 5px, 100% calc(100% - 5px), calc(100% - 5px) 100%, 5px 100%, 0 calc(100% - 5px), 0 5px)
        clippedCorners(g, x, y, size, size, 5, ACCENT);
        // CSS: .brand-mark-letter font-size 17px font-weight 900
        var fnt = Minecraft.getInstance().font;
        g.pose().pushPose();
        g.pose().translate((float) (x + size / 2), (float) (y + size / 2), 0.0F);
        float s = 1.4F; // масштабируем базовый MC-шрифт (12px) чтобы примерно попасть в 17px
        g.pose().scale(s, s, 1.0F);
        g.drawCenteredString(fnt, styled("G"), 0, -4, BG_DEEP);
        g.pose().popPose();
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
       ПАНЕЛИ (border 1px LINE_COLOR + inset 1px PANEL_BG)
       CSS: .panel { background: var(--bg-panel); border: 1px solid var(--line); border-radius: 10px; padding: 18px; }
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

    /** Отступ от краёв экрана (CSS: padding 40px, минимум 24px). */
    public static int pad(int screenWidth) {
        return Math.max(24, Math.min(40, screenWidth * 4 / 100));
    }
}
