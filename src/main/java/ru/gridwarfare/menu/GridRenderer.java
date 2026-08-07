package ru.gridwarfare.menu;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Optimised Java2D renderer — pixel-perfect port of HTML mockup.
 * <p>
 * Key optimisations for weak PCs:
 * <ul>
 *   <li>Renders to BASE_W x BASE_H (960x540) — 75% fewer pixels than 1920x1080</li>
 *   <li>Caches the static background layer (photo + overlay + vignette)</li>
 *   <li>Only re-renders UI layer when hover or data changes</li>
 * </ul>
 */
public final class GridRenderer {

    /* == Base render resolution (half of 1920x1080) == */
    public static final int BASE_W = 960;
    public static final int BASE_H = 540;

    /* == BUTTON (for click detection, in screen-space) == */
    public static final class BtnRect {
        public final int x, y, w, h;
        public final String id;
        public BtnRect(int x, int y, int w, int h, String id) {
            this.x = x; this.y = y; this.w = w; this.h = h; this.id = id;
        }
        public boolean contains(int mx, int my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    /* == COLORS (from CSS :root) == */
    private static final Color ACCENT        = new Color(0x68, 0xC2, 0x84);
    private static final Color ACCENT_HOVER  = new Color(0x7C, 0xD0, 0x90);
    private static final Color ACCENT_DARK   = new Color(0x4A, 0x9C, 0x66);
    private static final Color ACCENT_DARKER = new Color(0x38, 0x7A, 0x50);
    private static final Color BG_DEEP       = new Color(0x0B, 0x0F, 0x0C);
    private static final Color TEXT_MAIN     = new Color(0xF3, 0xF6, 0xF3);
    private static final Color TEXT_MUTED    = new Color(0x8B, 0x97, 0x8F);
    private static final Color TEXT_DIM      = new Color(0x5A, 0x65, 0x5E);
    private static final Color LINE          = new Color(0x34, 0x40, 0x38);
    private static final Color PANEL_BG      = new Color(12, 16, 14, 209);
    private static final Color ACCENT_DIM    = new Color(104, 194, 132, 38);
    private static final Color ACCENT_BORDER = new Color(104, 194, 132, 77);
    private static final Color BTN_SEC_BG    = new Color(12, 16, 14, 191);
    private static final Color BTN_SEC_HOVER = new Color(18, 24, 20, 217);
    private static final Color BTN_SM_BG     = new Color(12, 16, 14, 166);
    private static final Color BTN_SM_HOVER  = new Color(18, 24, 20, 204);

    /* == FONTS (at base resolution) == */
    private Font f400, f500, f600, f700, f900;
    private boolean fontsOk;
    private BufferedImage bgPhoto;

    /* Data */
    private JsonObject authData;
    private JsonArray newsData;
    private int serverState = 2;
    private int onlinePlayers;
    private int maxPlayers;

    /* Cached background */
    private BufferedImage cachedBg;

    /* Layout cache (recalculated on render) — in base-space */
    public final List<BtnRect> buttons = new ArrayList<>();

    /* Scale factor base-space -> screen-space */
    private float screenScale = 1f;

    public GridRenderer() {}

    /* =====================
       INIT
       ===================== */
    public void init() {
        System.setProperty("java.awt.headless", "true");
        loadFonts();
        loadBackground();
    }

    private void loadFonts() {
        try {
            f400 = loadTtf("font/inter_400.ttf").deriveFont(Font.PLAIN, 13f);
            f500 = loadTtf("font/inter_600.ttf").deriveFont(Font.PLAIN, 13f);
            f600 = loadTtf("font/inter_600.ttf").deriveFont(Font.PLAIN, 13f);
            f700 = loadTtf("font/inter_700.ttf").deriveFont(Font.PLAIN, 13f);
            f900 = loadTtf("font/inter_700.ttf").deriveFont(Font.BOLD, 13f);
            fontsOk = true;
        } catch (Exception e) {
            fontsOk = false;
            f400 = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
            f500 = f400; f600 = f400.deriveFont(Font.BOLD);
            f700 = f600; f900 = f700.deriveFont(Font.BOLD);
        }
    }

    private Font loadTtf(String path) throws IOException, FontFormatException {
        try (InputStream is = MinecraftHolder.getResource(path)) {
            return Font.createFont(Font.TRUETYPE_FONT, is);
        }
    }

    private void loadBackground() {
        try (InputStream is = MinecraftHolder.getResource("textures/gui/ui/bg_menu.png")) {
            if (is != null) bgPhoto = ImageIO.read(is);
        } catch (Exception ignored) { bgPhoto = null; }
    }

    /* Data setters */
    public void setAuth(JsonObject data) { this.authData = data; }
    public void setNews(JsonArray data) { this.newsData = data; }
    public void setServerStatus(int state, int online, int max) {
        this.serverState = state; this.onlinePlayers = online; this.maxPlayers = max;
    }

    /**
     * Called when the screen resizes. Recalculates scale factor and
     * invalidates the cached background.
     */
    public void onResize(int screenW, int screenH) {
        float newScale = Math.min((float)screenW / BASE_W, (float)screenH / BASE_H);
        if (Math.abs(newScale - screenScale) > 0.01f) {
            screenScale = newScale;
            cachedBg = null;
        }
    }

    /* ==============================
       MAIN RENDER — returns image at BASE_W x BASE_H
       ============================== */
    public BufferedImage render(int screenW, int screenH, int mx, int my) {
        screenScale = Math.min((float)screenW / BASE_W, (float)screenH / BASE_H);
        int W = BASE_W;
        int H = BASE_H;

        // Convert mouse from screen-space to base-space
        int baseMx = (int)(mx / screenScale);
        int baseMy = (int)(my / screenScale);

        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        buttons.clear();

        // Scale = 0.5 (base is half of 1920x1080)
        float sc = 0.5f;
        int pad = Math.max(12, Math.min(20, (int)(W * 4f / 100f)));

        // Background (cached)
        if (cachedBg == null) {
            cachedBg = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
            Graphics2D bg = cachedBg.createGraphics();
            bg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            drawBackground(bg, W, H, sc);
            bg.dispose();
        }
        g.drawImage(cachedBg, 0, 0, null);

        drawTopbar(g, W, H, sc, pad);
        int menuW = s(440, sc);
        int menuX = pad + (W - pad * 2 - menuW) / 2;
        int rightColW = s(280, sc);
        int rightX = W - pad - rightColW;

        // Compute Y positions
        g.setFont(f900.deriveFont(52f * sc));
        FontMetrics titleFm = g.getFontMetrics();
        int titleBoxH = titleFm.getAscent() + titleFm.getDescent() + s(10, sc) + s(12, sc);

        g.setFont(f600.deriveFont((float)s(11, sc)));
        FontMetrics tagFm = g.getFontMetrics();
        int tagH = s(4, sc) + tagFm.getHeight() + s(4, sc);
        int titleBlockH = titleBoxH + tagH - s(2, sc);
        int gapTitle = s(48, sc);
        int playH = s(72, sc);
        int singleH = s(62, sc);
        int smallH = s(46, sc);
        int gapBig = s(10, sc);
        int gapRow = s(10, sc);

        int totalH = titleBlockH + gapTitle + playH + gapBig + singleH + gapRow + smallH;
        int contentH = H - s(60, sc);
        int baseY = s(60, sc) + (contentH - totalH) / 2;

        int titleY = baseY;
        int playY = titleY + titleBlockH + gapTitle;
        int singleY = playY + playH + gapBig;
        int rowY = singleY + singleH + gapRow;

        drawTitle(g, menuX, menuW, titleY, sc);
        drawPlayButton(g, menuX, playY, menuW, playH, sc, baseMx, baseMy);
        drawSingleButton(g, menuX, singleY, menuW, singleH, sc, baseMx, baseMy);
        drawSmallButtons(g, menuX, rowY, menuW, smallH, sc, baseMx, baseMy);

        // Right column
        int newsCount = newsData == null ? 0 : Math.min(4, newsData.size());
        int statusH = s(110, sc);
        int newsH = s(30, sc) + (newsCount == 0 ? s(24, sc) : newsCount * s(40, sc));
        int panelGap = s(10, sc);
        int rightTotal = statusH + panelGap + newsH;
        int rightY = s(60, sc) + (contentH - rightTotal) / 2;
        drawStatusPanel(g, rightX, rightY, rightColW, statusH, sc);
        drawNewsPanel(g, rightX, rightY + statusH + panelGap, rightColW, newsH, sc);

        // Social icons
        int socSize = s(38, sc);
        int socGap = s(10, sc);
        int socY = H - s(24, sc) - socSize;
        int socRight = W - s(40, sc);
        drawSocialIcon(g, socRight - socSize * 3 - socGap * 2, socY, socSize, sc, baseMx, baseMy, "telegram");
        drawSocialIcon(g, socRight - socSize * 2 - socGap, socY, socSize, sc, baseMx, baseMy, "discord");
        drawSocialIcon(g, socRight - socSize, socY, socSize, sc, baseMx, baseMy, "globe");

        // Version
        g.setFont(f400.deriveFont((float)s(10, sc)));
        g.setColor(TEXT_DIM);
        g.drawString("Minecraft 1.21.1 \u00B7 NeoForge \u00B7 GRID v0.1.0", pad, H - s(10, sc) - s(3, sc));

        g.dispose();

        // Scale button rects from base-space to screen-space
        scaleButtonsToScreen();
        return img;
    }

    /**
     * Scale cached button rects from base (960x540) to actual screen coordinates.
     */
    private void scaleButtonsToScreen() {
        for (int i = 0; i < buttons.size(); i++) {
            BtnRect b = buttons.get(i);
            buttons.set(i, new BtnRect(
                (int)(b.x * screenScale),
                (int)(b.y * screenScale),
                (int)(b.w * screenScale),
                (int)(b.h * screenScale),
                b.id
            ));
        }
    }

    /* =====================
       BACKGROUND (cached)
       ===================== */
    private void drawBackground(Graphics2D g, int W, int H, float sc) {
        if (bgPhoto != null) {
            double scale = Math.max((double) W / bgPhoto.getWidth(), (double) H / bgPhoto.getHeight());
            int sw = (int)(bgPhoto.getWidth() * scale);
            int sh = (int)(bgPhoto.getHeight() * scale);
            g.drawImage(bgPhoto, (W - sw) / 2, (H - sh) / 2, sw, sh, null);
        }
        // Gradient overlay
        for (int y = 0; y < H; y++) {
            float t = (float) y / Math.max(1, H - 1);
            float alpha;
            if (t < 0.4f) alpha = 0.72f + (0.50f - 0.72f) * (t / 0.4f);
            else alpha = 0.50f + (0.68f - 0.50f) * ((t - 0.4f) / 0.6f);
            g.setColor(new Color(11, 15, 12, (int)(alpha * 255)));
            g.fillRect(0, y, W, 1);
        }
        // Vignette
        float vigRadius = Math.max(W, H) * 0.7f;
        RadialGradientPaint vig = new RadialGradientPaint(
            new Point2D.Float(W / 2f, H / 2f), vigRadius,
            new float[]{0.4f, 1.0f},
            new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 0, 128)}
        );
        g.setPaint(vig);
        g.fillRect(0, 0, W, H);
        g.setPaint(null);
    }

    /* =====================
       TOP BAR
       CSS: height:60px; padding:20px 40px;
       ===================== */
    private void drawTopbar(Graphics2D g, int W, int H, float sc, int pad) {
        int topPad = s(20, sc);

        // Brand mark 32x32 (clip-path polygon 5px)
        int bmSize = s(32, sc);
        int cut = s(5, sc);
        drawClippedRect(g, pad, topPad, bmSize, bmSize, cut, ACCENT);
        g.setFont(f900.deriveFont((float)s(17, sc)));
        g.setColor(BG_DEEP);
        FontMetrics fm = g.getFontMetrics();
        String gLetter = "G";
        int gw = fm.stringWidth(gLetter);
        g.drawString(gLetter, pad + (bmSize - gw) / 2, topPad + (bmSize + fm.getAscent()) / 2 - fm.getDescent());

        // Brand text "GRID"
        g.setFont(f700.deriveFont((float)s(13, sc)));
        drawSpaced(g, "GRID", pad + bmSize + s(12, sc), topPad + bmSize / 2 + s(4, sc), s(3, sc), TEXT_MAIN);

        drawAuthCard(g, W, pad, sc);
    }

    /* =====================
       AUTH CARD
       ===================== */
    private void drawAuthCard(Graphics2D g, int W, int pad, float sc) {
        boolean authed = authData != null;
        String nick = "\u0413\u041E\u0421\u0422\u042C";
        String rank = "";
        if (authed) {
            if (authData.has("username")) nick = authData.get("username").getAsString().toUpperCase();
            if (authData.has("donate")) rank = authData.get("donate").getAsString().toUpperCase();
            if (rank.isEmpty() && authData.has("rank")) rank = authData.get("rank").getAsString().toUpperCase();
        }

        g.setFont(f600.deriveFont((float)s(12, sc)));
        FontMetrics fmNick = g.getFontMetrics();
        int nickW = fmNick.stringWidth(nick);

        g.setFont(f600.deriveFont((float)s(10, sc)));
        FontMetrics fmRank = g.getFontMetrics();
        int rankW = rank.isEmpty() ? 0 : fmRank.stringWidth(rank);

        int line1W = s(14, sc) + nickW + (rank.isEmpty() ? 0 : s(7, sc) + rankW + s(10, sc)) + s(14, sc);

        String balLabel = authed ? "\u0411\u0430\u043B\u0430\u043D\u0441: " : "\u0410\u0432\u0442\u043E\u0440\u0438\u0437\u0430\u0446\u0438\u044F";
        String balValue = authed ? formatBalance(balance()) + " \u20BD" : "";
        g.setFont(f400.deriveFont((float)s(10, sc)));
        FontMetrics fmBal = g.getFontMetrics();
        int line2W = s(14, sc) + fmBal.stringWidth(balLabel)
                      + (balValue.isEmpty() ? 0 : s(4, sc) + fmBal.stringWidth(balValue)) + s(14, sc);

        int cardW = Math.max(line1W, line2W);
        int cardH = authed ? s(46, sc) : s(32, sc);
        int cx = W - pad - cardW;
        int cy = (s(60, sc) - cardH) / 2;

        fillRoundRect(g, cx, cy, cardW, cardH, s(10, sc), LINE);
        fillRoundRect(g, cx + 1, cy + 1, cardW - 2, cardH - 2, s(9, sc), PANEL_BG);

        int tx = cx + s(14, sc);
        int ty = cy + s(7, sc);

        g.setFont(f600.deriveFont((float)s(12, sc)));
        g.setColor(TEXT_MAIN);
        g.drawString(nick, tx, ty + g.getFontMetrics().getAscent());

        if (!rank.isEmpty()) {
            int rx = tx + nickW + s(7, sc);
            int rw = rankW + s(10, sc);
            int rh = g.getFontMetrics().getHeight() + s(2, sc);
            fillRoundRect(g, rx, ty - 1, rw, rh, s(3, sc), ACCENT_DIM);
            g.setFont(f600.deriveFont((float)s(10, sc)));
            g.setColor(ACCENT);
            g.drawString(rank, rx + s(5, sc), ty + g.getFontMetrics().getAscent() - 1);
        }

        if (authed) {
            int by = ty + s(16, sc);
            g.setFont(f400.deriveFont((float)s(10, sc)));
            g.setColor(TEXT_MUTED);
            g.drawString(balLabel, tx, by + g.getFontMetrics().getAscent());
            int lw = g.getFontMetrics().stringWidth(balLabel);
            g.setColor(ACCENT);
            g.setFont(f600.deriveFont((float)s(10, sc)));
            g.drawString(balValue, tx + lw + s(4, sc), by + g.getFontMetrics().getAscent());
        } else {
            g.setFont(f400.deriveFont((float)s(10, sc)));
            g.setColor(TEXT_MUTED);
            g.drawString(balLabel, tx, ty + g.getFontMetrics().getAscent());
        }
    }

    private long balance() {
        return authData != null && authData.has("balance") ? authData.get("balance").getAsLong() : 0L;
    }

    /* =====================
       3D TITLE + TAG
       ===================== */
    private void drawTitle(Graphics2D g, int menuX, int menuW, int baseY, float sc) {
        g.setFont(f900.deriveFont(52f * sc));
        FontMetrics fm = g.getFontMetrics();
        int ls = s(8, sc);
        int textW = (int)spacedWidth(g, "GRID", ls);
        int padX = s(36, sc);
        int boxW = textW + padX * 2;
        int boxH = fm.getAscent() + fm.getDescent() + s(10, sc) + s(12, sc);
        int boxX = menuX + (menuW - boxW) / 2;
        int r = s(8, sc);

        // Drop shadow
        Composite old = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.50f));
        fillRoundRect(g, boxX - s(4, sc), baseY + s(9, sc), boxW + s(8, sc), boxH + s(8, sc), r, Color.BLACK);
        g.setComposite(old);

        // 3D layers
        fillRoundRect(g, boxX, baseY + s(7, sc), boxW, boxH, r, ACCENT_DARKER);
        fillRoundRect(g, boxX, baseY + s(5, sc), boxW, boxH, r, ACCENT_DARK);
        fillRoundRect(g, boxX, baseY, boxW, boxH, r, ACCENT);

        // Text
        g.setColor(BG_DEEP);
        float textX = boxX + padX + (boxW - padX * 2 - textW) / 2;
        float textY = baseY + s(10, sc) + fm.getAscent();
        drawSpaced(g, "GRID", textX, textY, ls, BG_DEEP);

        // Tag
        g.setFont(f600.deriveFont((float)s(11, sc)));
        FontMetrics fmTag = g.getFontMetrics();
        String tag = "\u0412\u041E\u0415\u041D\u041D\u041E-\u041F\u041E\u041B\u0418\u0422\u0418\u0427\u0415\u0421\u041A\u0418\u0419 \u0421\u0415\u0420\u0412\u0415\u0420";
        int tagLs = s(1.5f, sc);
        int tagTextW = (int)spacedWidth(g, tag, tagLs);
        int tagPadX = s(16, sc);
        int tagW = tagTextW + tagPadX * 2;
        int tagH = s(4, sc) + fmTag.getHeight() + s(4, sc);
        int tagY = baseY + boxH - s(2, sc);
        int tagX = menuX + (menuW - tagW) / 2;

        fillRoundRect(g, tagX, tagY, tagW, tagH, s(4, sc), ACCENT_BORDER);
        fillRoundRect(g, tagX + 1, tagY + 1, tagW - 2, tagH - 2, s(3, sc), ACCENT_DIM);

        g.setColor(ACCENT);
        float tagTextX = tagX + tagPadX + (tagW - tagPadX * 2 - tagTextW) / 2;
        drawSpaced(g, tag, tagTextX, tagY + s(4, sc) + fmTag.getAscent(), tagLs, ACCENT);
    }

    /* =====================
       PLAY BUTTON (PRIMARY)
       ===================== */
    private void drawPlayButton(Graphics2D g, int x, int y, int w, int h, float sc, int mx, int my) {
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + h;
        buttons.add(new BtnRect(x, y, w, h, "play"));

        // Glow
        Composite old = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, hover ? 0.20f : 0.10f));
        fillRoundRect(g, x - s(6, sc), y - s(6, sc), w + s(12, sc), h + s(12, sc), s(18, sc), ACCENT);
        g.setComposite(old);

        fillRoundRect(g, x, y, w, h, s(12, sc), hover ? ACCENT_HOVER : ACCENT);

        // Play icon
        int iconCx = x + s(20, sc) + s(18, sc);
        int iconCy = y + h / 2;
        drawPlayIcon(g, iconCx, iconCy, s(22, sc), BG_DEEP);

        // Text
        int textX = x + s(20, sc) + s(36, sc) + s(16, sc);
        g.setFont(f700.deriveFont((float)s(15, sc)));
        FontMetrics fmT = g.getFontMetrics();
        int titleH = fmT.getHeight();
        g.setFont(f400.deriveFont((float)s(11, sc)));
        int descH = g.getFontMetrics().getHeight();
        int totalTextH = titleH + s(2, sc) + descH;
        int textBlockY = y + (h - totalTextH) / 2;

        g.setFont(f700.deriveFont((float)s(15, sc)));
        g.setColor(BG_DEEP);
        drawSpaced(g, "\u0418\u0413\u0420\u0410\u0422\u042C", textX, textBlockY + fmT.getAscent(), s(1, sc), BG_DEEP);

        g.setFont(f400.deriveFont((float)s(11, sc)));
        FontMetrics fmD = g.getFontMetrics();
        g.setColor(new Color(11, 15, 12, 153));
        g.drawString("\u041F\u043E\u0434\u043A\u043B\u044E\u0447\u0435\u043D\u0438\u0435 \u043A \u0441\u0435\u0440\u0432\u0435\u0440\u0443", textX,
                textBlockY + titleH + s(2, sc) + fmD.getAscent());
    }

    /* =====================
       SINGLE WORLD BUTTON (SECONDARY)
       ===================== */
    private void drawSingleButton(Graphics2D g, int x, int y, int w, int h, float sc, int mx, int my) {
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + h;
        buttons.add(new BtnRect(x, y, w, h, "single"));

        fillRoundRect(g, x, y, w, h, s(10, sc), LINE);
        fillRoundRect(g, x + 1, y + 1, w - 2, h - 2, s(9, sc), hover ? BTN_SEC_HOVER : BTN_SEC_BG);

        int iconCx = x + s(20, sc) + s(18, sc);
        int iconCy = y + h / 2;
        drawCheckIcon(g, iconCx, iconCy, s(22, sc), ACCENT);

        int textX = x + s(20, sc) + s(36, sc) + s(16, sc);
        g.setFont(f700.deriveFont((float)s(15, sc)));
        FontMetrics fmT = g.getFontMetrics();
        int titleH = fmT.getHeight();
        g.setFont(f400.deriveFont((float)s(11, sc)));
        int descH = g.getFontMetrics().getHeight();
        int totalTextH = titleH + s(2, sc) + descH;
        int textBlockY = y + (h - totalTextH) / 2;

        g.setFont(f700.deriveFont((float)s(15, sc)));
        g.setColor(TEXT_MAIN);
        drawSpaced(g, "\u041E\u0414\u0418\u041D\u041E\u0427\u041D\u042B\u0419 \u041C\u0418\u0420", textX, textBlockY + fmT.getAscent(), s(1, sc), TEXT_MAIN);

        g.setFont(f400.deriveFont((float)s(11, sc)));
        FontMetrics fmD = g.getFontMetrics();
        g.setColor(TEXT_MUTED);
        g.drawString("\u041E\u0434\u0438\u043D\u043E\u0447\u043D\u0430\u044F \u0438\u0433\u0440\u0430", textX,
                textBlockY + titleH + s(2, sc) + fmD.getAscent());
    }

    /* =====================
       SMALL BUTTONS ROW
       ===================== */
    private void drawSmallButtons(Graphics2D g, int menuX, int y, int menuW, int h, float sc, int mx, int my) {
        String[] labels = {"\u041D\u0410\u0421\u0422\u0420\u041E\u0419\u041A\u0418", "\u041E \u0421\u0415\u0420\u0412\u0415\u0420\u0415", "\u041C\u0410\u0413\u0410\u0417\u0418\u041D", "\u0412\u042B\u0425\u041E\u0414"};
        String[] ids = {"settings", "info", "shop", "exit"};
        int gap = s(8, sc);
        int bw = (menuW - gap * (labels.length - 1)) / labels.length;

        for (int i = 0; i < labels.length; i++) {
            int bx = menuX + i * (bw + gap);
            boolean hover = mx >= bx && mx < bx + bw && my >= y && my < y + h;
            boolean isExit = ids[i].equals("exit");
            buttons.add(new BtnRect(bx, y, bw, h, ids[i]));

            Color borderC = isExit && hover ? new Color(220, 80, 80, 77) : (hover ? ACCENT_BORDER : LINE);
            fillRoundRect(g, bx, y, bw, h, s(10, sc), borderC);
            fillRoundRect(g, bx + 1, y + 1, bw - 2, h - 2, s(9, sc), hover ? BTN_SM_HOVER : BTN_SM_BG);

            g.setFont(f500.deriveFont((float)s(11, sc)));
            FontMetrics fm = g.getFontMetrics();
            int iconS = s(14, sc);
            int gapIC = s(8, sc);
            float textLS = s(0.8f, sc);
            int textW = (int)spacedWidth(g, labels[i], textLS);
            int total = iconS + gapIC + textW;
            int sx = bx + (bw - total) / 2;
            int sy = y + (h - fm.getHeight()) / 2 + fm.getAscent();

            Color iconColor = isExit && hover ? new Color(0xE0, 0x55, 0x55) : (hover ? ACCENT : TEXT_MUTED);
            int iconCx = sx + iconS / 2;
            int iconCy = y + h / 2;
            switch (i) {
                case 0 -> drawSlidersIcon(g, iconCx, iconCy, iconS, iconColor);
                case 1 -> drawInfoIcon(g, iconCx, iconCy, iconS, iconColor);
                case 2 -> drawBagIcon(g, iconCx, iconCy, iconS, iconColor);
                case 3 -> drawExitIcon(g, iconCx, iconCy, iconS, iconColor);
            }

            Color textC = hover ? TEXT_MAIN : TEXT_MUTED;
            drawSpaced(g, labels[i], sx + iconS + gapIC, sy, textLS, textC);
        }
    }

    /* =====================
       STATUS PANEL
       ===================== */
    private void drawStatusPanel(Graphics2D g, int x, int y, int w, int h, float sc) {
        fillRoundRect(g, x, y, w, h, s(10, sc), LINE);
        fillRoundRect(g, x + 1, y + 1, w - 2, h - 2, s(9, sc), PANEL_BG);

        g.setFont(f600.deriveFont((float)s(10, sc)));
        g.setColor(TEXT_MUTED);
        drawSpaced(g, "\u0421\u0422\u0410\u0422\u0423\u0421 \u0421\u0415\u0420\u0412\u0415\u0420\u0410", x + s(18, sc), y + s(18, sc) + g.getFontMetrics().getAscent(), s(1.5f, sc), TEXT_MUTED);

        int dy = y + s(18, sc) + s(12, sc) + s(8, sc);
        boolean online = serverState == 1;
        Color dotC = online ? ACCENT : new Color(0x61, 0x6A, 0x64);
        String label = online ? "\u0421\u0435\u0440\u0432\u0435\u0440 \u0440\u0430\u0431\u043E\u0442\u0430\u0435\u0442" :
                serverState == 0 ? "\u0421\u0435\u0440\u0432\u0435\u0440 \u043D\u0435\u0434\u043E\u0441\u0442\u0443\u043F\u0435\u043D" : "\u041F\u0440\u043E\u0432\u0435\u0440\u043A\u0430...";
        Color labelC = online ? ACCENT : (serverState == 0 ? new Color(0xE0, 0x66, 0x66) : TEXT_MUTED);

        g.setColor(dotC);
        g.fillOval(x + s(18, sc), dy + (s(11, sc) - s(6, sc)) / 2, s(6, sc), s(6, sc));
        g.setFont(f400.deriveFont((float)s(11, sc)));
        g.setColor(labelC);
        g.drawString(label, x + s(18, sc) + s(6, sc) + s(7, sc), dy + g.getFontMetrics().getAscent());

        if (online) {
            int numY = dy + s(20, sc);
            g.setFont(f700.deriveFont((float)s(26, sc)));
            g.setColor(TEXT_MAIN);
            String numStr = String.valueOf(onlinePlayers);
            g.drawString(numStr, x + s(18, sc), numY + g.getFontMetrics().getAscent());
            int numW = g.getFontMetrics().stringWidth(numStr);

            g.setFont(f400.deriveFont((float)s(12, sc)));
            g.setColor(TEXT_MUTED);
            g.drawString("\u0438\u0433\u0440\u043E\u043A\u0430", x + s(18, sc) + numW + s(3, sc), numY + g.getFontMetrics().getAscent());

            int barY = numY + s(26, sc) + s(10, sc);
            int barX = x + s(18, sc);
            int barW = w - s(36, sc);
            fillRoundRect(g, barX, barY, barW, s(3, sc), s(2, sc), LINE);
            int fillW = maxPlayers > 0 ? Math.max(s(3, sc), (int)(barW * Math.min(1f, (float)onlinePlayers / maxPlayers))) : s(3, sc);
            fillRoundRect(g, barX, barY, fillW, s(3, sc), s(2, sc), ACCENT);
        }
    }

    /* =====================
       NEWS PANEL
       ===================== */
    private void drawNewsPanel(Graphics2D g, int x, int y, int w, int h, float sc) {
        fillRoundRect(g, x, y, w, h, s(10, sc), LINE);
        fillRoundRect(g, x + 1, y + 1, w - 2, h - 2, s(9, sc), PANEL_BG);

        g.setFont(f600.deriveFont((float)s(10, sc)));
        g.setColor(TEXT_MUTED);
        drawSpaced(g, "\u041D\u041E\u0412\u041E\u0421\u0422\u0418", x + s(18, sc), y + s(18, sc) + g.getFontMetrics().getAscent(), s(1.5f, sc), TEXT_MUTED);

        if (newsData == null) {
            g.setFont(f400.deriveFont((float)s(11, sc)));
            g.setColor(TEXT_DIM);
            g.drawString("\u0417\u0430\u0433\u0440\u0443\u0437\u043A\u0430...", x + s(18, sc), y + s(38, sc) + g.getFontMetrics().getAscent());
            return;
        }

        int iy = y + s(18, sc) + s(12, sc);
        int shown = 0;
        for (JsonElement el : newsData) {
            if (shown >= 4) break;
            JsonObject item = el.getAsJsonObject();
            String title = item.has("title") ? item.get("title").getAsString() : "";
            String date = item.has("publishedAt") ? item.get("publishedAt").getAsString() : "";
            if (date.length() >= 10) date = date.substring(8, 10) + "." + date.substring(5, 7) + "." + date.substring(0, 4);

            int itemY = iy + s(8, sc);

            g.setFont(f400.deriveFont((float)s(9, sc)));
            g.setColor(TEXT_DIM);
            g.drawString(date, x + s(18, sc), itemY + g.getFontMetrics().getAscent());

            g.setFont(f500.deriveFont((float)s(11, sc)));
            FontMetrics fmT = g.getFontMetrics();
            int maxW = w - s(36, sc);
            String clipped = title;
            if (fmT.stringWidth(clipped) > maxW) {
                while (clipped.length() > 3 && fmT.stringWidth(clipped + "...") > maxW) clipped = clipped.substring(0, clipped.length() - 1);
                clipped += "...";
            }
            g.setColor(TEXT_MAIN);
            g.drawString(clipped, x + s(18, sc), itemY + s(12, sc) + fmT.getAscent());

            if (shown < 3) {
                g.setColor(new Color(52, 64, 56, 102));
                g.fillRect(x + s(18, sc), itemY + s(28, sc), w - s(36, sc), 1);
            }

            iy = itemY + s(30, sc);
            shown++;
        }
        if (shown == 0) {
            g.setFont(f400.deriveFont((float)s(11, sc)));
            g.setColor(TEXT_DIM);
            g.drawString("\u041D\u043E\u0432\u043E\u0441\u0442\u0435\u0439 \u043F\u043E\u043A\u0430 \u043D\u0435\u0442", x + s(18, sc), iy + s(8, sc) + g.getFontMetrics().getAscent());
        }
    }

    /* =====================
       SOCIAL ICONS
       ===================== */
    private void drawSocialIcon(Graphics2D g, int x, int y, int size, float sc, int mx, int my, String type) {
        boolean hover = mx >= x && mx < x + size && my >= y && my < y + size;
        buttons.add(new BtnRect(x, y, size, size, "social_" + type));

        g.setColor(hover ? ACCENT_BORDER : LINE);
        g.fillOval(x, y, size, size);
        g.setColor(hover ? ACCENT_DIM : new Color(12, 16, 14, 179));
        g.fillOval(x + 1, y + 1, size - 2, size - 2);

        int iconS = s(18, sc);
        int cx = x + size / 2;
        int cy = y + size / 2;
        Color ic = hover ? ACCENT : TEXT_MUTED;
        switch (type) {
            case "telegram" -> drawTelegramIcon(g, cx, cy, iconS, ic);
            case "discord"  -> drawDiscordIcon(g, cx, cy, iconS, ic);
            case "globe"    -> drawGlobeIcon(g, cx, cy, iconS, ic);
        }
    }

    /* ======================================
       ICONS (Java2D Path2D — anti-aliased)
       ====================================== */

    private void drawPlayIcon(Graphics2D g, int cx, int cy, int size, Color color) {
        float sc = size / 24f;
        Path2D p = new Path2D.Float();
        p.moveTo(cx + (8 - 12) * sc, cy + (5 - 12) * sc);
        p.lineTo(cx + (8 - 12) * sc, cy + (19 - 12) * sc);
        p.lineTo(cx + (19 - 12) * sc, cy + (12 - 12) * sc);
        p.closePath();
        g.setColor(color);
        g.fill(p);
    }

    private void drawCheckIcon(Graphics2D g, int cx, int cy, int size, Color color) {
        float sc = size / 24f;
        g.setColor(color);
        g.setStroke(new BasicStroke(Math.max(1.5f, size * 0.12f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D p = new Path2D.Float();
        p.moveTo(cx + (4.83f - 12) * sc, cy + (12 - 12) * sc);
        p.lineTo(cx + (9 - 12) * sc, cy + (16.17f - 12) * sc);
        p.lineTo(cx + (19.59f - 12) * sc, cy + (5.59f - 12) * sc);
        g.draw(p);
        g.setStroke(new BasicStroke(1));
    }

    private void drawSlidersIcon(Graphics2D g, int cx, int cy, int size, Color color) {
        float sc = size / 24f;
        g.setColor(color);
        float sw = Math.max(1f, size * 0.06f);
        g.setStroke(new BasicStroke(sw));
        float or = 9 * sc;
        int orI = (int)or;
        g.drawOval(cx - orI, cy - orI, orI * 2, orI * 2);
        float ir = 3.6f * sc;
        int irI = (int)ir;
        g.drawOval(cx - irI, cy - irI, irI * 2, irI * 2);
        float toothLen = 3 * sc;
        float toothW = 4 * sc;
        for (int i = 0; i < 8; i++) {
            double a = Math.toRadians(i * 45);
            float midR = (9 - 0.5f) * sc;
            float tx = cx + (float)Math.cos(a) * midR;
            float ty = cy + (float)Math.sin(a) * midR;
            g.translate(tx, ty);
            g.rotate(a);
            g.fillRect((int)(-toothW / 2), (int)(-toothLen / 2), (int)toothW, (int)toothLen);
            g.rotate(-a);
            g.translate(-tx, -ty);
        }
        g.setStroke(new BasicStroke(1));
    }

    private void drawInfoIcon(Graphics2D g, int cx, int cy, int size, Color color) {
        float sc = size / 24f;
        float r = 10 * sc;
        int ri = (int)r;
        g.setColor(color);
        g.setStroke(new BasicStroke(Math.max(1f, size * 0.07f)));
        g.drawOval(cx - ri, cy - ri, ri * 2, ri * 2);
        g.setStroke(new BasicStroke(1));
        float dr = 1.5f * sc;
        int dri = (int)dr;
        g.fillOval(cx - dri, cy - (int)(4 * sc) - dri, dri * 2, dri * 2);
        float sw = 2 * sc;
        g.fillRect(cx - (int)(sw / 2), cy - (int)(1 * sc), (int)sw, (int)(6 * sc));
    }

    private void drawBagIcon(Graphics2D g, int cx, int cy, int size, Color color) {
        float sc = size / 24f;
        g.setColor(color);
        float bw = 16 * sc, bh = 12 * sc;
        fillRoundRect(g, cx - bw / 2, cy - bh / 2 + 2 * sc, bw, bh, 2 * sc, color);
        g.setStroke(new BasicStroke(Math.max(1.5f, 1.5f * sc), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Arc2D handle = new Arc2D.Float(cx - 5 * sc, cy - bh / 2 - 2 * sc, 10 * sc, 8 * sc, 180, 180, Arc2D.OPEN);
        g.draw(handle);
        g.setStroke(new BasicStroke(1));
        float wr = 2 * sc;
        int wri = (int)wr;
        g.fillOval(cx - (int)(5 * sc) - wri, cy + (int)(bh / 2) + (int)(2 * sc) - wri, wri * 2, wri * 2);
        g.fillOval(cx + (int)(5 * sc) - wri, cy + (int)(bh / 2) + (int)(2 * sc) - wri, wri * 2, wri * 2);
    }

    private void drawExitIcon(Graphics2D g, int cx, int cy, int size, Color color) {
        float sc = size / 24f;
        g.setColor(color);
        int thick = Math.max(2, (int)(Math.max(1.5f, 1.5f * sc)));
        int dl = cx - (int)(9 * sc), dt = cy - (int)(9 * sc);
        int dw = (int)(18 * sc), dh = (int)(18 * sc);
        g.fillRect(dl, dt, thick, dh);
        g.fillRect(dl, dt, dw, thick);
        g.fillRect(dl, dt + dh - thick, dw, thick);
        g.setStroke(new BasicStroke(thick, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D arrow = new Path2D.Float();
        arrow.moveTo(cx - 4 * sc, cy);
        arrow.lineTo(cx + 3 * sc, cy);
        g.draw(arrow);
        Path2D head = new Path2D.Float();
        head.moveTo(cx + 3 * sc, cy - 4 * sc);
        head.lineTo(cx + 7.5f * sc, cy);
        head.lineTo(cx + 3 * sc, cy + 4 * sc);
        g.draw(head);
        g.setStroke(new BasicStroke(1));
    }

    private void drawTelegramIcon(Graphics2D g, int cx, int cy, int size, Color color) {
        float sc = size / 24f;
        g.setColor(color);
        Path2D p = new Path2D.Float();
        float s = sc * 0.85f;
        p.moveTo(cx + (-9) * s, cy + 0 * s);
        p.lineTo(cx + 9 * s, cy + (-6) * s);
        p.lineTo(cx + 3 * s, cy + 0 * s);
        p.lineTo(cx + 9 * s, cy + 6 * s);
        p.lineTo(cx + (-9) * s, cy + 0 * s);
        p.moveTo(cx + (-3) * s, cy + 0 * s);
        p.lineTo(cx + 1 * s, cy + (-3) * s);
        p.lineTo(cx + 3 * s, cy + 0 * s);
        p.closePath();
        g.fill(p);
    }

    private void drawDiscordIcon(Graphics2D g, int cx, int cy, int size, Color color) {
        float sc = size / 24f;
        g.setColor(color);
        float sw = Math.max(1f, size * 0.06f);
        g.setStroke(new BasicStroke(sw));
        float or2 = 9 * sc;
        int or2i = (int)or2;
        g.drawOval(cx - or2i, cy - or2i, or2i * 2, or2i * 2);
        float ew = 2.5f * sc, eh = 3 * sc;
        int ewi = (int)ew, ehi = (int)eh;
        g.fillOval(cx - (int)(4 * sc) - ewi / 2, cy - (int)(1.5f * sc) - ehi / 2, ewi, ehi);
        g.fillOval(cx + (int)(4 * sc) - ewi / 2, cy - (int)(1.5f * sc) - ehi / 2, ewi, ehi);
        g.setStroke(new BasicStroke(Math.max(1f, 1.2f * sc), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Arc2D.Float(cx - 4 * sc, cy + 1 * sc, 8 * sc, 5 * sc, 200, 140, Arc2D.OPEN));
        g.setStroke(new BasicStroke(1));
    }

    private void drawGlobeIcon(Graphics2D g, int cx, int cy, int size, Color color) {
        float sc = size / 24f;
        float r = 10 * sc;
        int ri = (int)r;
        g.setColor(color);
        g.setStroke(new BasicStroke(Math.max(1f, 1.2f * sc)));
        g.drawOval(cx - ri, cy - ri, ri * 2, ri * 2);
        g.draw(new Line2D.Float(cx - ri, cy, cx + ri, cy));
        g.draw(new Line2D.Float(cx, cy - ri, cx, cy + ri));
        g.draw(new Arc2D.Float(cx - ri, cy - ri, ri * 2, ri * 2, 60, 60, Arc2D.OPEN));
        g.draw(new Arc2D.Float(cx - ri, cy - ri, ri * 2, ri * 2, -120, 60, Arc2D.OPEN));
        g.setStroke(new BasicStroke(1));
    }

    /* ======================================
       RENDERING UTILITIES
       ====================================== */

    private static void fillRoundRect(Graphics2D g, float x, float y, float w, float h, float r, Color c) {
        g.setColor(c);
        g.fill(new RoundRectangle2D.Float(x, y, w, h, r, r));
    }

    private static void drawClippedRect(Graphics2D g, int x, int y, int w, int h, int cut, Color c) {
        g.setColor(c);
        Path2D p = new Path2D.Float();
        p.moveTo(x + cut, y);
        p.lineTo(x + w - cut, y);
        p.lineTo(x + w, y + cut);
        p.lineTo(x + w, y + h - cut);
        p.lineTo(x + w - cut, y + h);
        p.lineTo(x + cut, y + h);
        p.lineTo(x, y + h - cut);
        p.lineTo(x, y + cut);
        p.closePath();
        g.fill(p);
    }

    private void drawSpaced(Graphics2D g, String text, float x, float y, float spacing, Color color) {
        g.setColor(color);
        FontMetrics fm = g.getFontMetrics();
        float cx = x;
        boolean lastWasSpace = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == ' ') {
                cx += fm.stringWidth(" ");
                lastWasSpace = true;
            } else {
                if (!lastWasSpace && i > 0) cx += spacing;
                g.drawString(String.valueOf(ch), cx, y);
                cx += fm.stringWidth(String.valueOf(ch));
                lastWasSpace = false;
            }
        }
    }

    private float spacedWidth(Graphics2D g, String text, float spacing) {
        FontMetrics fm = g.getFontMetrics();
        float w = 0;
        boolean lastWasSpace = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == ' ') {
                w += fm.stringWidth(" ");
                lastWasSpace = true;
            } else {
                if (!lastWasSpace && i > 0) w += spacing;
                w += fm.stringWidth(String.valueOf(ch));
                lastWasSpace = false;
            }
        }
        return w;
    }

    private static int s(float cssPx, float sc) { return Math.max(1, (int)(cssPx * sc)); }

    private static String formatBalance(long value) {
        String d = String.valueOf(Math.abs(value));
        StringBuilder sb = new StringBuilder(d);
        for (int i = sb.length() - 3; i > 0; i -= 3) sb.insert(i, ' ');
        if (value < 0) sb.insert(0, '-');
        return sb.toString();
    }
}
