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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pixel-perfect Java2D renderer — port of HTML mockup.
 * Renders at 1920x1080 (native resolution), minimal GPU scaling.
 * Optimised: bg cached, social icons cached as sub-images, reusable BufferedImage.
 */
public final class GridRenderer {

    public static final int BASE_W = 1920;
    public static final int BASE_H = 1080;

    /* ═══════════════════ BUTTON RECT ═══════════════════ */
    public static final class BtnRect {
        public int x, y, w, h;
        public final String id;
        public BtnRect(int x, int y, int w, int h, String id) {
            this.x = x; this.y = y; this.w = w; this.h = h; this.id = id;
        }
        public boolean contains(int mx, int my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    /* ═══════════════════ COLORS (exact HTML values) ═══════════════════ */
    private static final Color ACCENT        = new Color(0x68, 0xC2, 0x84);
    private static final Color ACCENT_HOVER  = new Color(0x7C, 0xD0, 0x90);
    private static final Color ACCENT_DARK   = new Color(0x4A, 0x9C, 0x66);
    private static final Color ACCENT_DARKER = new Color(0x38, 0x7A, 0x50);
    private static final Color BG_DEEP       = new Color(0x0B, 0x0F, 0x0C);
    private static final Color TEXT_MAIN     = new Color(0xF3, 0xF6, 0xF3);
    private static final Color TEXT_MUTED    = new Color(0x8B, 0x97, 0x8F);
    private static final Color TEXT_DIM      = new Color(0x5A, 0x65, 0x5E);
    private static final Color LINE          = new Color(0x34, 0x40, 0x38);
    private static final Color PANEL_BG      = new Color(12, 16, 14, 209);  // 0.82*255≈209
    private static final Color ACCENT_DIM    = new Color(104, 194, 132, 38); // 0.15*255≈38
    private static final Color ACCENT_BORDER = new Color(104, 194, 132, 77); // 0.30*255≈77
    private static final Color BTN_SEC_BG    = new Color(12, 16, 14, 191); // 0.75*255≈191
    private static final Color BTN_SEC_HOVER = new Color(18, 24, 20, 217); // 0.85*255≈217
    private static final Color BTN_SM_BG     = new Color(12, 16, 14, 166); // 0.65*255≈166
    private static final Color BTN_SM_HOVER  = new Color(18, 24, 20, 204); // 0.80*255≈204
    private static final Color NEWS_LINE     = new Color(52, 64, 56, 102); // 0.40*255≈102

    /* ═══════════════════ FONTS ═══════════════════ */
    private Font f400, f600, f700, f900;

    /* ═══════════════════ BACKGROUND ═══════════════════ */
    private BufferedImage bgPhoto;
    private BufferedImage cachedBg;

    /* ═══════════════════ DATA (volatile, written from MC thread, read from render thread) ═══════════════════ */
    private volatile JsonObject authData;
    private volatile JsonArray newsData;
    private volatile int serverState = 2; // 0=checking, 1=online, 2=offline
    private volatile int onlinePlayers;
    private volatile int maxPlayers;

    /* ═══════════════════ OUTPUT ═══════════════════ */
    public final List<BtnRect> buttons = new ArrayList<>();
    private float screenScale = 1f;
    private BufferedImage reusableImg;

    /* ═══════════════════ CACHED SVG PATHS (parsed once) ═══════════════════ */
    private Path2D pathTg, pathDc, pathGlobe;
    private Path2D pathPlay, pathCheck, pathGear, pathInfo, pathBag, pathExit;

    /* ═══════════════════ CACHED ICON IMAGES (24x24 white on transparent) ═══════════════════ */
    private BufferedImage icPlay, icCheck, icGear, icInfo, icBag, icExit;
    private BufferedImage icTg, icDc, icGlobe;

    /* ═══════════════════ GRADIENT CACHE ═══════════════════ */
    private int[] overlayStrip;

    /* ═══════════════════ CONSTRUCTOR ═══════════════════ */
    public GridRenderer() {}

    /* ═══════════════════ INIT (called once, on background thread) ═══════════════════ */
    public void init() {
        System.setProperty("java.awt.headless", "true");
        try {
            Font b400 = loadTtf("font/inter_400.ttf");
            Font b600 = loadTtf("font/inter_600.ttf");
            Font b700 = loadTtf("font/inter_700.ttf");
            f400 = b400.deriveFont(Font.PLAIN, 13f);
            f600 = b600.deriveFont(Font.PLAIN, 13f);
            f700 = b700.deriveFont(Font.PLAIN, 13f);
            f900 = b700.deriveFont(Font.BOLD, 13f);
        } catch (Exception e) {
            f400 = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
            f600 = f700 = f900 = f400.deriveFont(Font.BOLD);
        }
        try (InputStream is = MinecraftHolder.getResource("textures/gui/ui/bg_menu.png")) {
            if (is != null) bgPhoto = ImageIO.read(is);
        } catch (Exception ignored) { bgPhoto = null; }

        // Pre-parse all SVG paths (done once)
        pathPlay = svgPath("M8 5v14l11-7z");
        pathCheck = svgPath("M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z");
        pathGear = svgPath("M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58a.49.49 0 00.12-.61l-1.92-3.32a.488.488 0 00-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54a.484.484 0 00-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96a.49.49 0 00-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.07.62-.07.94s.02.64.07.94l-2.03 1.58a.49.49 0 00-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6A3.6 3.6 0 1115.6 12 3.611 3.611 0 0112 15.6z");
        pathInfo = svgPath("M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z");
        pathBag = svgPath("M7 18c-1.1 0-1.99.9-1.99 2S5.9 22 7 22s2-.9 2-2-.9-2-2-2zM1 2v2h2l3.6 7.59-1.35 2.45c-.16.28-.25.61-.25.96 0 1.1.9 2 2 2h12v-2H7.42c-.14 0-.25-.11-.25-.25l.03-.12.9-1.63h7.45c.75 0 1.41-.41 1.75-1.03l3.58-6.49A1.003 1.003 0 0020 4H5.21l-.94-2H1zm16 16c-1.1 0-1.99.9-1.99 2s.89 2 1.99 2 2-.9 2-2-.9-2-2-2z");
        pathExit = svgPath("M10.09 15.59L11.5 17l5-5-5-5-1.41 1.41L12.67 11H3v2h9.67l-2.58 2.59zM19 3H5c-1.11 0-2 .9-2 2v4h2V5h14v14H5v-4H3v4c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2z");
        // Telegram: full icon with circle
        pathTg = svgPath("M11.944 0A12 12 0 000 12a12 12 0 0012 12 12 12 0 0012-12A12 12 0 0012 0a12 12 0 00-.056 0zm4.962 7.224c.1-.002.321.023.465.14a.506.506 0 01.171.325c.016.093.036.306.02.472-.18 1.898-.962 6.502-1.36 8.627-.168.9-.499 1.201-.82 1.23-.696.065-1.225-.46-1.9-.902-1.056-.693-1.653-1.124-2.678-1.8-1.185-.78-.417-1.21.258-1.91.177-.184 3.247-2.977 3.307-3.23.007-.032.014-.15-.056-.212s-.174-.041-.249-.024c-.106.024-1.793 1.14-5.061 3.345-.479.33-.913.49-1.302.48-.428-.008-1.252-.241-1.865-.44-.752-.245-1.349-.374-1.297-.789.027-.216.325-.437.893-.663 3.498-1.524 5.83-2.529 6.998-3.014 3.332-1.386 4.025-1.627 4.476-1.635z");
        pathDc = svgPath("M20.317 4.37a19.79 19.79 0 00-4.885-1.515.074.074 0 00-.079.037c-.21.375-.445.865-.608 1.25-1.845-.276-3.68-.276-5.487 0-.164-.393-.406-.874-.618-1.25a.077.077 0 00-.078-.037 19.74 19.74 0 00-4.885 1.515.07.07 0 00-.032.028C.533 9.046-.319 13.58.099 18.058a.082.082 0 00.031.056c2.053 1.508 4.041 2.423 5.993 3.029a.078.078 0 00.084-.028c.462-.63.873-1.295 1.226-1.994a.076.076 0 00-.042-.106c-.653-.247-1.274-.549-1.872-.892a.077.077 0 01-.008-.128c.126-.094.252-.192.372-.291a.074.074 0 01.078-.01c3.927 1.793 8.18 1.793 12.061 0a.074.074 0 01.078.009c.12.1.246.198.373.293a.077.077 0 01-.007.127 12.3 12.3 0 01-1.873.892.076.076 0 00-.041.107c.36.698.772 1.362 1.225 1.993a.076.076 0 00.084.028c1.961-.606 3.95-1.522 6.002-3.029a.077.077 0 00.031-.055c.5-5.177-.838-9.674-3.549-13.66a.061.061 0 00-.031-.029zM8.02 15.33c-1.183 0-2.157-1.086-2.157-2.419 0-1.333.956-2.419 2.157-2.419 1.21 0 2.176 1.096 2.157 2.42 0 1.332-.956 2.418-2.157 2.418zm7.975 0c-1.183 0-2.157-1.086-2.157-2.419 0-1.333.955-2.419 2.157-2.419 1.21 0 2.176 1.096 2.157 2.42 0 1.332-.946 2.418-2.157 2.418z");
        pathGlobe = svgPath("M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 17.93c-3.95-.49-7-3.85-7-7.93 0-.62.08-1.21.21-1.79L9 15v1c0 1.1.9 2 2 2v1.93zm6.9-2.54c-.26-.81-1-1.39-1.9-1.39h-1v-3c0-.55-.45-1-1-1H8v-2h2c.55 0 1-.45 1-1V7h2c1.1 0 2-.9 2-2v-.41c2.93 1.19 5 4.06 5 7.41 0 2.08-.8 3.97-2.1 5.39z");

        // Pre-render icon sprites at 24x24 (white on transparent) — avoids per-frame path transforms
        icPlay = renderIcon(pathPlay, 24, Color.WHITE);
        icCheck = renderIcon(pathCheck, 24, Color.WHITE);
        icGear = renderIcon(pathGear, 24, Color.WHITE);
        icInfo = renderIcon(pathInfo, 24, Color.WHITE);
        icBag = renderIcon(pathBag, 24, Color.WHITE);
        icExit = renderIcon(pathExit, 24, Color.WHITE);
        icTg = renderIcon(pathTg, 24, Color.WHITE);
        icDc = renderIcon(pathDc, 24, Color.WHITE);
        icGlobe = renderIcon(pathGlobe, 24, Color.WHITE);

        // Pre-compute gradient overlay strip for background (ARGB int[] for setRGB)
        overlayStrip = new int[BASE_H];
        for (int y = 0; y < BASE_H; y++) {
            float t = (float) y / (float) (BASE_H - 1);
            float a;
            if (t < 0.4f) a = 0.72f + (0.50f - 0.72f) * (t / 0.4f);
            else a = 0.50f + (0.68f - 0.50f) * ((t - 0.4f) / 0.6f);
            overlayStrip[y] = (int)(a * 255) << 24 | 0x0B0F0C;
        }
    }

    private Font loadTtf(String path) throws IOException, FontFormatException {
        try (InputStream is = MinecraftHolder.getResource(path)) {
            return Font.createFont(Font.TRUETYPE_FONT, is);
        }
    }

    /* ═══════════════════ DATA SETTERS (called from MC thread) ═══════════════════ */
    public void setAuth(JsonObject d) { this.authData = d; }
    public void setNews(JsonArray d) { this.newsData = d; }
    public void setServerStatus(int st, int on, int mx) {
        this.serverState = st; this.onlinePlayers = on; this.maxPlayers = mx;
    }

    /* ═══════════════════ MAIN RENDER ═══════════════════ */
    public BufferedImage render(int screenW, int screenH, int mx, int my) {
        screenScale = Math.min((float) screenW / BASE_W, (float) screenH / BASE_H);
        int W = BASE_W, H = BASE_H;

        // Snapshot volatile data once for frame consistency
        final JsonObject aData = authData;
        final JsonArray  nData = newsData;
        final int sState   = serverState;
        final int onl      = onlinePlayers;
        final int mxP      = maxPlayers;

        // Clear reusable image
        if (reusableImg == null) reusableImg = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D cg = reusableImg.createGraphics();
        cg.setComposite(AlphaComposite.Clear);
        cg.fillRect(0, 0, W, H);
        cg.dispose();

        Graphics2D g = reusableImg.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        buttons.clear();

        // Background (cached)
        if (cachedBg == null) {
            cachedBg = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
            Graphics2D bg = cachedBg.createGraphics();
            bg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            bg.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            paintBg(bg, W, H);
            bg.dispose();
        }
        g.drawImage(cachedBg, 0, 0, null);

        // Layout constants (CSS px)
        int pad = 40;
        int topH = 60;

        // Top bar
        paintTopbar(g, W, pad, 20, aData);

        // Content area
        int contentY = topH;
        int contentH = H - contentY;

        // Right column: absolutely positioned, vertically centered in content
        int rightW = 280;
        int rightX = W - pad - rightW;

        // Center menu column
        int menuW = 440;
        int availForMenu = rightX - pad;
        int menuX = pad + (availForMenu - menuW) / 2;

        // Title block measurement
        g.setFont(f900.deriveFont(52f));
        FontMetrics tfm = g.getFontMetrics();
        int titleBoxH = tfm.getAscent() + tfm.getDescent() + 10 + 12;
        g.setFont(f600.deriveFont(11f));
        FontMetrics tagfm = g.getFontMetrics();
        int tagH = 4 + tagfm.getHeight() + 4;
        int titleBlockH = titleBoxH + tagH - 2;
        int titleGap = 48;
        int playH = 72;
        int singleH = 62;
        int smallH = 46;
        int btnGap = 10;
        int totalMenuH = titleBlockH + titleGap + playH + btnGap + singleH + btnGap + smallH;
        int baseY = contentY + (contentH - totalMenuH) / 2;

        // Title
        paintTitle(g, menuX, menuW, baseY);
        // Play button
        paintPlayBtn(g, menuX, baseY + titleBlockH + titleGap, menuW, playH, mx, my);
        // Single button
        paintSingleBtn(g, menuX, baseY + titleBlockH + titleGap + playH + btnGap, menuW, singleH, mx, my);
        // Small buttons
        paintSmallBtns(g, menuX, baseY + titleBlockH + titleGap + playH + btnGap + singleH + btnGap, menuW, smallH, mx, my);

        // Right panels — vertically centered in content area
        boolean isOn = sState == 1;
        int statusH = isOn ? 110 : 70;
        int nc = (nData == null) ? 0 : Math.min(4, nData.size());
        int newsH = 18 + 12 + 9 + (nc == 0 ? 24 : nc * 30 + 8) + 18;
        int panelGap = 10;
        int rightTotalH = statusH + panelGap + newsH;
        int rightY = contentY + (contentH - rightTotalH) / 2;
        paintStatusPanel(g, rightX, rightY, rightW, statusH, sState, onl, mxP);
        paintNewsPanel(g, rightX, rightY + statusH + panelGap, rightW, newsH, nData);

        // Social icons — bottom right, 24px from bottom, 40px from right
        int socSize = 38;
        int socGap = 10;
        int socY = H - 24 - socSize;
        int socStartX = W - 40 - socSize * 3 - socGap * 2;
        paintSocial(g, socStartX, socY, socSize, mx, my, "telegram");
        paintSocial(g, socStartX + socSize + socGap, socY, socSize, mx, my, "discord");
        paintSocial(g, socStartX + (socSize + socGap) * 2, socY, socSize, mx, my, "globe");

        // Version text
        g.setFont(f400.deriveFont(10f));
        g.setColor(TEXT_DIM);
        g.drawString("Minecraft 1.21.1 \u00B7 NeoForge \u00B7 GRID v0.1.0", pad, H - 10 - 3);

        g.dispose();

        // Scale button rects to screen coordinates
        for (int i = 0; i < buttons.size(); i++) {
            BtnRect b = buttons.get(i);
            buttons.set(i, new BtnRect(
                (int) (b.x * screenScale), (int) (b.y * screenScale),
                (int) (b.w * screenScale), (int) (b.h * screenScale), b.id));
        }
        return reusableImg;
    }

    /* ═══════════════════ BACKGROUND (cached, rendered once) ═══════════════════ */
    private void paintBg(Graphics2D g, int W, int H) {
        if (bgPhoto != null) {
            double sc = Math.max((double) W / bgPhoto.getWidth(), (double) H / bgPhoto.getHeight());
            int sw = (int) (bgPhoto.getWidth() * sc);
            int sh = (int) (bgPhoto.getHeight() * sc);
            g.drawImage(bgPhoto, (W - sw) / 2, (H - sh) / 2, sw, sh, null);
        }
        // Gradient overlay (pre-computed strip)
        BufferedImage overlay = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        int[] row = new int[W];
        for (int y = 0; y < H; y++) {
            int argb = overlayStrip[y];
            for (int x = 0; x < W; x++) row[x] = argb;
            overlay.setRGB(0, y, W, 1, row, 0, W);
        }
        g.drawImage(overlay, 0, 0, null);

        // Vignette
        float vigR = Math.max(W, H) * 0.7f;
        RadialGradientPaint vig = new RadialGradientPaint(
            new Point2D.Float(W / 2f, H / 2f), vigR,
            new float[]{0.4f, 1.0f},
            new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 0, 128)}
        );
        g.setPaint(vig);
        g.fillRect(0, 0, W, H);
        g.setPaint(null);
    }

    /* ═══════════════════ TOP BAR ═══════════════════ */
    private void paintTopbar(Graphics2D g, int W, int pad, int topPad, JsonObject auth) {
        // Brand mark — 32x32 octagonal clip
        int bm = 32;
        int cut = 5;
        drawClippedRect(g, pad, topPad, bm, bm, cut, ACCENT);
        g.setFont(f900.deriveFont(17f));
        g.setColor(BG_DEEP);
        FontMetrics fmG = g.getFontMetrics();
        String gL = "G";
        g.drawString(gL, pad + (bm - fmG.stringWidth(gL)) / 2, topPad + (bm + fmG.getAscent()) / 2 - fmG.getDescent());
        // Brand text
        g.setFont(f700.deriveFont(13f));
        int brandTextY = topPad + bm / 2 + (g.getFontMetrics().getAscent() + g.getFontMetrics().getDescent()) / 2 - g.getFontMetrics().getDescent();
        drawSpaced(g, "GRID", pad + bm + 12, brandTextY, 3, TEXT_MAIN);
        // Auth card
        paintAuthCard(g, W, pad, topPad, auth);
    }

    /* ═══════════════════ AUTH CARD ═══════════════════ */
    private void paintAuthCard(Graphics2D g, int W, int pad, int topPad, JsonObject auth) {
        boolean authed = auth != null;
        String nick = "\u0413\u041E\u0421\u0422\u042C";
        String rank = "";
        if (authed) {
            if (auth.has("username")) nick = auth.get("username").getAsString().toUpperCase();
            if (auth.has("donate") && !auth.get("donate").getAsString().isEmpty())
                rank = auth.get("donate").getAsString().toUpperCase();
            if (rank.isEmpty() && auth.has("rank")) rank = auth.get("rank").getAsString().toUpperCase();
        }
        g.setFont(f600.deriveFont(12f));
        int nickW = g.getFontMetrics().stringWidth(nick);
        int rankBadgeW = 0;
        if (!rank.isEmpty()) {
            g.setFont(f600.deriveFont(10f));
            rankBadgeW = 5 + g.getFontMetrics().stringWidth(rank) + 5 + 7;
        }
        int line1W = nickW + rankBadgeW;
        String balLabel = authed ? "\u0411\u0430\u043B\u0430\u043D\u0441: " : "\u0410\u0432\u0442\u043E\u0440\u0438\u0437\u0430\u0446\u0438\u044F";
        String balVal = authed ? fmtBal(balance(auth)) + " \u20BD" : "";
        g.setFont(f400.deriveFont(10f));
        int line2W = g.getFontMetrics().stringWidth(balLabel) + (balVal.isEmpty() ? 0 : g.getFontMetrics().stringWidth(balVal));
        int cw = 14 * 2 + Math.max(line1W, line2W);
        g.setFont(f600.deriveFont(12f));
        int l1h = g.getFontMetrics().getHeight();
        g.setFont(f400.deriveFont(10f));
        int l2h = g.getFontMetrics().getHeight();
        int ch = (authed ? 7 * 2 + l1h + 2 + l2h : 7 * 2 + l2h);
        int cy = (60 - ch) / 2;
        int cx = W - pad - cw;
        fillRR(g, cx, cy, cw, ch, 10, LINE);
        fillRR(g, cx + 1, cy + 1, cw - 2, ch - 2, 9, PANEL_BG);
        int tx = cx + 14;
        int ty = cy + 7;
        // Nick
        g.setFont(f600.deriveFont(12f));
        g.setColor(TEXT_MAIN);
        g.drawString(nick, tx, ty + g.getFontMetrics().getAscent());
        // Rank badge
        if (!rank.isEmpty()) {
            g.setFont(f600.deriveFont(10f));
            FontMetrics fmR = g.getFontMetrics();
            int rx = tx + nickW + 7;
            int rw = 5 + fmR.stringWidth(rank) + 5;
            int rh = fmR.getHeight() + 2;
            int ry = ty + (g.getFontMetrics().getAscent() - fmR.getAscent()) - 1;
            fillRR(g, rx, ry, rw, rh, 3, ACCENT_DIM);
            g.setColor(ACCENT);
            g.drawString(rank, rx + 5, ry + 1 + fmR.getAscent());
        }
        // Balance
        if (authed) {
            int by = ty + l1h + 2;
            g.setFont(f400.deriveFont(10f));
            g.setColor(TEXT_MUTED);
            g.drawString(balLabel, tx, by + g.getFontMetrics().getAscent());
            if (!balVal.isEmpty()) {
                int lw = g.getFontMetrics().stringWidth(balLabel);
                g.setFont(f600.deriveFont(10f));
                g.setColor(ACCENT);
                g.drawString(balVal, tx + lw + 4, by + g.getFontMetrics().getAscent());
            }
        }
    }

    private long balance(JsonObject auth) {
        return (auth != null && auth.has("balance")) ? auth.get("balance").getAsLong() : 0L;
    }

    /* ═══════════════════ 3D TITLE ═══════════════════ */
    private void paintTitle(Graphics2D g, int menuX, int menuW, int baseY) {
        g.setFont(f900.deriveFont(52f));
        FontMetrics fm = g.getFontMetrics();
        int ls = 8;
        int tw = (int) spacedW(g, "GRID", ls);
        int px = 36;
        int bw = tw + px * 2;
        int bh = fm.getAscent() + fm.getDescent() + 10 + 12;
        int bx = menuX + (menuW - bw) / 2;
        int r = 8;
        // 3D shadow layers
        Composite old = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.50f));
        fillRR(g, bx - 4, baseY + 9, bw + 8, bh + 8, r + 2, Color.BLACK);
        g.setComposite(old);
        fillRR(g, bx, baseY + 7, bw, bh, r, ACCENT_DARKER);
        fillRR(g, bx, baseY + 5, bw, bh, r, ACCENT_DARK);
        fillRR(g, bx, baseY, bw, bh, r, ACCENT);
        // Title text
        g.setColor(BG_DEEP);
        float txX = bx + px + (bw - px * 2 - tw) / 2;
        drawSpaced(g, "GRID", txX, baseY + 10 + fm.getAscent(), ls, BG_DEEP);
        // Tag
        g.setFont(f600.deriveFont(11f));
        FontMetrics fmT = g.getFontMetrics();
        String tag = "\u0412\u041E\u0415\u041D\u041D\u041E-\u041F\u041E\u041B\u0418\u0422\u0418\u0427\u0415\u0421\u041A\u0418\u0419 \u0421\u0415\u0420\u0412\u0415\u0420";
        float tLs = 1.5f;
        int ttw = (int) spacedW(g, tag, tLs);
        int tpx = 16;
        int tW = ttw + tpx * 2;
        int tH = 4 + fmT.getHeight() + 4;
        int tY = baseY + bh - 2;
        int tX = menuX + (menuW - tW) / 2;
        fillRR(g, tX, tY, tW, tH, 4, ACCENT_BORDER);
        fillRR(g, tX + 1, tY + 1, tW - 2, tH - 2, 3, ACCENT_DIM);
        g.setColor(ACCENT);
        drawSpaced(g, tag, tX + tpx + (tW - tpx * 2 - ttw) / 2, tY + 4 + fmT.getAscent(), tLs, ACCENT);
    }

    /* ═══════════════════ PLAY BUTTON ═══════════════════ */
    private void paintPlayBtn(Graphics2D g, int x, int y, int w, int h, int mx, int my) {
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + h;
        buttons.add(new BtnRect(x, y, w, h, "play"));
        // Glow
        Composite old = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, hov ? 0.20f : 0.10f));
        fillRR(g, x - 6, y - 6, w + 12, h + 12, 18, ACCENT);
        g.setComposite(old);
        // Button body
        fillRR(g, x, y, w, h, 12, hov ? ACCENT_HOVER : ACCENT);
        // Play icon (22x22)
        drawIcon(g, icPlay, x + 20 + 7, y + (h - 22) / 2, 22, BG_DEEP);
        // Text block
        int txX = x + 20 + 36 + 16;
        g.setFont(f700.deriveFont(15f));
        FontMetrics fmT = g.getFontMetrics();
        g.setFont(f400.deriveFont(11f));
        FontMetrics fmD = g.getFontMetrics();
        int ttH = fmT.getHeight() + 2 + fmD.getHeight();
        int tbY = y + (h - ttH) / 2;
        g.setFont(f700.deriveFont(15f));
        g.setColor(BG_DEEP);
        drawSpaced(g, "\u0418\u0413\u0420\u0410\u0422\u042C", txX, tbY + fmT.getAscent(), 1, BG_DEEP);
        g.setFont(f400.deriveFont(11f));
        g.setColor(new Color(11, 15, 12, 153));
        g.drawString("\u041F\u043E\u0434\u043A\u043B\u044E\u0447\u0435\u043D\u0438\u0435 \u043A \u0441\u0435\u0440\u0432\u0435\u0440\u0443",
            txX, tbY + fmT.getHeight() + 2 + fmD.getAscent());
    }

    /* ═══════════════════ SINGLE BUTTON ═══════════════════ */
    private void paintSingleBtn(Graphics2D g, int x, int y, int w, int h, int mx, int my) {
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + h;
        buttons.add(new BtnRect(x, y, w, h, "single"));
        fillRR(g, x, y, w, h, 10, hov ? ACCENT_BORDER : LINE);
        fillRR(g, x + 1, y + 1, w - 2, h - 2, 9, hov ? BTN_SEC_HOVER : BTN_SEC_BG);
        // Check icon (22x22)
        drawIcon(g, icCheck, x + 20 + 7, y + (h - 22) / 2, 22, ACCENT);
        // Text block
        int txX = x + 20 + 36 + 16;
        g.setFont(f700.deriveFont(15f));
        FontMetrics fmT = g.getFontMetrics();
        g.setFont(f400.deriveFont(11f));
        FontMetrics fmD = g.getFontMetrics();
        int ttH = fmT.getHeight() + 2 + fmD.getHeight();
        int tbY = y + (h - ttH) / 2;
        g.setFont(f700.deriveFont(15f));
        g.setColor(TEXT_MAIN);
        drawSpaced(g, "\u041E\u0414\u0418\u041D\u041E\u0427\u041D\u042B\u0419 \u041C\u0418\u0420", txX, tbY + fmT.getAscent(), 1, TEXT_MAIN);
        g.setFont(f400.deriveFont(11f));
        g.setColor(TEXT_MUTED);
        g.drawString("\u041E\u0434\u0438\u043D\u043E\u0447\u043D\u0430\u044F \u0438\u0433\u0440\u0430",
            txX, tbY + fmT.getHeight() + 2 + fmD.getAscent());
    }

    /* ═══════════════════ SMALL BUTTONS ═══════════════════ */
    private void paintSmallBtns(Graphics2D g, int mx0, int y, int mw, int h, int mx, int my) {
        String[] labels = {"\u041D\u0410\u0421\u0422\u0420\u041E\u0419\u041A\u0418", "\u041E \u0421\u0415\u0420\u0412\u0415\u0420\u0415", "\u041C\u0410\u0413\u0410\u0417\u0418\u041D", "\u0412\u042B\u0425\u041E\u0414"};
        String[] ids = {"settings", "info", "shop", "exit"};
        BufferedImage[] icons = {icGear, icInfo, icBag, icExit};
        int gap = 8;
        int bw = (mw - gap * 3) / 4;
        for (int i = 0; i < 4; i++) {
            int bx = mx0 + i * (bw + gap);
            boolean hov = mx >= bx && mx < bx + bw && my >= y && my < y + h;
            boolean isExit = "exit".equals(ids[i]);
            buttons.add(new BtnRect(bx, y, bw, h, ids[i]));
            Color bc = isExit && hov ? new Color(220, 80, 80, 77) : (hov ? ACCENT_BORDER : LINE);
            fillRR(g, bx, y, bw, h, 10, bc);
            fillRR(g, bx + 1, y + 1, bw - 2, h - 2, 9, hov ? BTN_SM_HOVER : BTN_SM_BG);
            g.setFont(f600.deriveFont(11f));
            FontMetrics fm = g.getFontMetrics();
            int icS = 14;
            int icG = 8;
            float tLs = 0.8f;
            int tW = (int) spacedW(g, labels[i], tLs);
            int total = icS + icG + tW;
            int sx = bx + (bw - total) / 2;
            int sy = y + (h - fm.getHeight()) / 2 + fm.getAscent();
            Color ic = isExit && hov ? new Color(0xE0, 0x55, 0x55) : (hov ? ACCENT : TEXT_MUTED);
            // Draw pre-cached icon
            drawIcon(g, icons[i], sx + (icS - 14) / 2, y + (h - 14) / 2, 14, ic);
            drawSpaced(g, labels[i], sx + icS + icG, sy, tLs, hov ? TEXT_MAIN : TEXT_MUTED);
        }
    }

    /* ═══════════════════ STATUS PANEL ═══════════════════ */
    private void paintStatusPanel(Graphics2D g, int x, int y, int w, int h, int state, int online, int max) {
        fillRR(g, x, y, w, h, 10, LINE);
        fillRR(g, x + 1, y + 1, w - 2, h - 2, 9, PANEL_BG);
        g.setFont(f600.deriveFont(10f));
        g.setColor(TEXT_MUTED);
        int tY = y + 18;
        drawSpaced(g, "\u0421\u0422\u0410\u0422\u0423\u0421 \u0421\u0415\u0420\u0412\u0415\u0420\u0410",
            x + 18, tY + g.getFontMetrics().getAscent(), 1.5f, TEXT_MUTED);
        int rowY = tY + g.getFontMetrics().getHeight() + 12;
        boolean on = state == 1;
        Color dotC = on ? ACCENT : new Color(0x61, 0x6A, 0x64);
        int ds = 6;
        int dcy = rowY + (11 - ds) / 2 + ds / 2;
        g.setColor(dotC);
        g.fillOval(x + 18, dcy - ds / 2, ds, ds);
        g.setFont(f400.deriveFont(11f));
        String lbl;
        Color lblC;
        if (on) { lbl = "\u0421\u0435\u0440\u0432\u0435\u0440 \u0440\u0430\u0431\u043E\u0442\u0430\u0435\u0442"; lblC = ACCENT; }
        else if (state == 0) { lbl = "\u0421\u0435\u0440\u0432\u0435\u0440 \u043D\u0435\u0434\u043E\u0441\u0442\u0443\u043F\u0435\u043D"; lblC = new Color(0xE0, 0x66, 0x66); }
        else { lbl = "\u041F\u0440\u043E\u0432\u0435\u0440\u043A\u0430..."; lblC = TEXT_MUTED; }
        g.setColor(lblC);
        g.drawString(lbl, x + 18 + ds + 7, rowY + g.getFontMetrics().getAscent());
        if (on) {
            int numY = rowY + 11 + 8;
            g.setFont(f700.deriveFont(26f));
            g.setColor(TEXT_MAIN);
            String ns = String.valueOf(online);
            g.drawString(ns, x + 18, numY + g.getFontMetrics().getAscent());
            int nw = g.getFontMetrics().stringWidth(ns);
            g.setFont(f400.deriveFont(12f));
            g.setColor(TEXT_MUTED);
            g.drawString("\u0438\u0433\u0440\u043E\u043A\u0430", x + 18 + nw + 3, numY + g.getFontMetrics().getAscent());
            int barY = numY + g.getFontMetrics().getHeight() + 10;
            int barX = x + 18;
            int barW = w - 36;
            int barH = 3;
            fillRR(g, barX, barY, barW, barH, 2, LINE);
            int fW = max > 0 ? Math.max(barH, (int) (barW * Math.min(1f, (float) online / max))) : barH;
            fillRR(g, barX, barY, fW, barH, 2, ACCENT);
        }
    }

    /* ═══════════════════ NEWS PANEL ═══════════════════ */
    private void paintNewsPanel(Graphics2D g, int x, int y, int w, int h, JsonArray news) {
        fillRR(g, x, y, w, h, 10, LINE);
        fillRR(g, x + 1, y + 1, w - 2, h - 2, 9, PANEL_BG);
        g.setFont(f600.deriveFont(10f));
        g.setColor(TEXT_MUTED);
        int tY = y + 18;
        drawSpaced(g, "\u041D\u041E\u0412\u041E\u0421\u0422\u0418", x + 18, tY + g.getFontMetrics().getAscent(), 1.5f, TEXT_MUTED);
        int listY = tY + g.getFontMetrics().getHeight() + 12;
        if (news == null) {
            g.setFont(f400.deriveFont(11f));
            g.setColor(TEXT_DIM);
            g.drawString("\u0417\u0430\u0433\u0440\u0443\u0437\u043A\u0430...", x + 18, listY + 8 + g.getFontMetrics().getAscent());
            return;
        }
        if (news.isEmpty()) {
            g.setFont(f400.deriveFont(11f));
            g.setColor(TEXT_DIM);
            g.drawString("\u041D\u043E\u0432\u043E\u0441\u0442\u0435\u0439 \u043F\u043E\u043A\u0430 \u043D\u0435\u0442", x + 18, listY + 8 + g.getFontMetrics().getAscent());
            return;
        }
        int maxTW = w - 36;
        int iy = listY;
        int shown = 0;
        for (JsonElement el : news) {
            if (shown >= 4) break;
            JsonObject item = el.getAsJsonObject();
            String title = item.has("title") ? item.get("title").getAsString() : "";
            String date = item.has("publishedAt") ? item.get("publishedAt").getAsString() : "";
            if (date.length() >= 10) date = date.substring(8, 10) + "." + date.substring(5, 7) + "." + date.substring(0, 4);
            int itY = iy + 8;
            g.setFont(f400.deriveFont(9f));
            g.setColor(TEXT_DIM);
            g.drawString(date, x + 18, itY + g.getFontMetrics().getAscent());
            g.setFont(f600.deriveFont(11f));
            FontMetrics fmT = g.getFontMetrics();
            String cl = title;
            if (fmT.stringWidth(cl) > maxTW) {
                while (cl.length() > 3 && fmT.stringWidth(cl + "...") > maxTW) cl = cl.substring(0, cl.length() - 1);
                cl += "...";
            }
            g.setColor(TEXT_MAIN);
            g.drawString(cl, x + 18, itY + 12 + fmT.getAscent());
            if (shown < 3 && shown < news.size() - 1) {
                g.setColor(NEWS_LINE);
                g.fillRect(x + 18, itY + 28, maxTW, 1);
            }
            iy = itY + 30;
            shown++;
        }
    }

    /* ═══════════════════ SOCIAL ICONS ═══════════════════ */
    private void paintSocial(Graphics2D g, int x, int y, int sz, int mx, int my, String type) {
        boolean hov = mx >= x && mx < x + sz && my >= y && my < y + sz;
        buttons.add(new BtnRect(x, y, sz, sz, "social_" + type));
        // Circle background
        g.setColor(hov ? ACCENT_BORDER : LINE);
        g.fillOval(x, y, sz, sz);
        g.setColor(hov ? ACCENT_DIM : new Color(12, 16, 14, 179));
        g.fillOval(x + 1, y + 1, sz - 2, sz - 2);
        // Icon (18x18, centered in circle)
        Color ic = hov ? ACCENT : TEXT_MUTED;
        BufferedImage icon;
        switch (type) {
            case "telegram" -> icon = icTg;
            case "discord"  -> icon = icDc;
            default          -> icon = icGlobe;
        }
        drawIcon(g, icon, x + (sz - 18) / 2, y + (sz - 18) / 2, 18, ic);
    }

    /* ═══════════════════ ICON DRAWING (from pre-cached images) ═══════════════════ */
    /** Draw a pre-cached white icon with a color tint. */
    private static void drawIcon(Graphics2D g, BufferedImage whiteIcon, int x, int y, int sz, Color tint) {
        if (sz == 24 && whiteIcon.getWidth() == 24) {
            // Exact size match — fast path, just tint
            tintIcon(g, whiteIcon, x, y, tint);
        } else if (sz < 24) {
            // Scale down using pre-cached image
            BufferedImage scaled = new BufferedImage(sz, sz, BufferedImage.TYPE_INT_ARGB);
            Graphics2D sg = scaled.createGraphics();
            sg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            sg.drawImage(whiteIcon, 0, 0, sz, sz, null);
            sg.dispose();
            tintIcon(g, scaled, x, y, tint);
        } else {
            tintIcon(g, whiteIcon, x, y, sz, sz, tint);
        }
    }

    /** Tint a white icon to the desired color and draw it at (x,y). */
    private static void tintIcon(Graphics2D g, BufferedImage whiteIcon, int x, int y, Color tint) {
        int w = whiteIcon.getWidth(), h = whiteIcon.getHeight();
        BufferedImage tinted = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int[] px = whiteIcon.getRGB(0, 0, w, h, null, 0, w);
        int tr = tint.getRed(), tg = tint.getGreen(), tb = tint.getBlue(), ta = tint.getAlpha();
        for (int i = 0; i < px.length; i++) {
            int a = (px[i] >> 24) & 0xFF;
            if (a > 0) {
                // Luminance-weighted tinting for better results
                int r = (px[i] >> 16) & 0xFF;
                int gv = (px[i] >> 8) & 0xFF;
                int b = px[i] & 0xFF;
                float lum = (r * 0.299f + gv * 0.587f + b * 0.114f) / 255f;
                int fr = (int)(tr * lum);
                int fg = (int)(tg * lum);
                int fb = (int)(tb * lum);
                int fa = (int)(a * (ta / 255f));
                px[i] = (fa << 24) | (fr << 16) | (fg << 8) | fb;
            }
        }
        tinted.setRGB(0, 0, w, h, px, 0, w);
        g.drawImage(tinted, x, y, null);
    }

    /** Tint and scale in one step. */
    private static void tintIcon(Graphics2D g, BufferedImage whiteIcon, int x, int y, int w, int h, Color tint) {
        BufferedImage tinted = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D tg = tinted.createGraphics();
        tg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        tg.drawImage(whiteIcon, 0, 0, w, h, null);
        tg.dispose();
        tintIcon(g, tinted, x, y, tint);
    }

    /** Render a Path2D into a white-on-transparent BufferedImage at 24x24. */
    private static BufferedImage renderIcon(Path2D path, int size, Color color) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(color);
        g.fill(path);
        g.dispose();
        return img;
    }

    /* ═══════════════════ SVG PATH PARSER (handles M L H V C S Q T A Z) ═══════════════════ */
    private static final Pattern SVG_TOKEN = Pattern.compile(
        "[MmLlHhVvCcSsQqTtAaZz]|[-+]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?"
    );

    private static Path2D svgPath(String d) {
        Path2D p = new Path2D.Float();
        Matcher m = SVG_TOKEN.matcher(d);
        ArrayList<String> tokens = new ArrayList<>();
        while (m.find()) tokens.add(m.group());
        int[] idx = {0};
        float lastX = 0, lastY = 0, startX = 0, startY = 0;
        float lastCx = 0, lastCy = 0;
        char lastCmd = ' ';
        while (idx[0] < tokens.size()) {
            String t = tokens.get(idx[0]++);
            char cmd = t.charAt(0);
            boolean rel = Character.isLowerCase(cmd);
            char upper = Character.toUpperCase(cmd);
            if (Character.isDigit(t.charAt(0)) || t.charAt(0) == '-' || t.charAt(0) == '+') {
                if (lastCmd == 'Z' || lastCmd == 'z') cmd = 'M';
                else cmd = lastCmd;
                rel = Character.isLowerCase(cmd);
                upper = Character.toUpperCase(cmd);
                idx[0]--;
            }
            float x = rel ? lastX : 0, y = rel ? lastY : 0;
            switch (upper) {
                case 'M' -> {
                    float mx = f(tokens, idx[0]++), my = f(tokens, idx[0]++);
                    if (rel) { x = lastX + mx; y = lastY + my; } else { x = mx; y = my; }
                    p.moveTo(x, y); lastX = x; lastY = y; startX = x; startY = y;
                    lastCmd = 'L'; continue;
                }
                case 'L' -> { float dx = f(tokens, idx[0]++), dy = f(tokens, idx[0]++); x = rel ? lastX + dx : dx; y = rel ? lastY + dy : dy; p.lineTo(x, y); break; }
                case 'H' -> { float hv = f(tokens, idx[0]++); x = rel ? lastX + hv : hv; p.lineTo(x, lastY); y = lastY; break; }
                case 'V' -> { float vv = f(tokens, idx[0]++); y = rel ? lastY + vv : vv; p.lineTo(lastX, y); x = lastX; break; }
                case 'C' -> {
                    float x1 = f(tokens, idx[0]++), y1 = f(tokens, idx[0]++);
                    float x2 = f(tokens, idx[0]++), y2 = f(tokens, idx[0]++);
                    float x3 = f(tokens, idx[0]++), y3 = f(tokens, idx[0]++);
                    if (rel) { x1+=lastX; y1+=lastY; x2+=lastX; y2+=lastY; x3+=lastX; y3+=lastY; }
                    p.curveTo(x1, y1, x2, y2, x3, y3); lastCx = x2; lastCy = y2; x = x3; y = y3; break;
                }
                case 'S' -> {
                    float x2 = f(tokens, idx[0]++), y2 = f(tokens, idx[0]++);
                    float x3 = f(tokens, idx[0]++), y3 = f(tokens, idx[0]++);
                    if (rel) { x2+=lastX; y2+=lastY; x3+=lastX; y3+=lastY; }
                    p.curveTo(2*lastX - lastCx, 2*lastY - lastCy, x2, y2, x3, y3);
                    lastCx = x2; lastCy = y2; x = x3; y = y3; break;
                }
                case 'Q' -> {
                    float x1 = f(tokens, idx[0]++), y1 = f(tokens, idx[0]++);
                    float x2 = f(tokens, idx[0]++), y2 = f(tokens, idx[0]++);
                    if (rel) { x1+=lastX; y1+=lastY; x2+=lastX; y2+=lastY; }
                    p.quadTo(x1, y1, x2, y2); lastCx = x1; lastCy = y1; x = x2; y = y2; break;
                }
                case 'T' -> {
                    float x2 = f(tokens, idx[0]++), y2 = f(tokens, idx[0]++);
                    if (rel) { x2+=lastX; y2+=lastY; }
                    float rx = 2*lastX - lastCx, ry = 2*lastY - lastCy;
                    p.quadTo(rx, ry, x2, y2); lastCx = rx; lastCy = ry; x = x2; y = y2; break;
                }
                case 'A' -> {
                    float rx = f(tokens, idx[0]++), ry = f(tokens, idx[0]++);
                    float rot = f(tokens, idx[0]++);
                    float large = arcFlag(tokens, idx);
                    float sweep = arcFlag(tokens, idx);
                    float ax = f(tokens, idx[0]++), ay = f(tokens, idx[0]++);
                    if (rel) { ax+=lastX; ay+=lastY; }
                    arcTo(p, lastX, lastY, rx, ry, rot, large > 0.5f, sweep > 0.5f, ax, ay);
                    x = ax; y = ay; break;
                }
                case 'Z' -> { p.closePath(); x = startX; y = startY; break; }
                default -> { lastX = x; lastY = y; lastCmd = cmd; continue; }
            }
            lastX = x; lastY = y; lastCmd = cmd;
        }
        return p;
    }

    private static float f(List<String> t, int i) {
        return i < t.size() ? Float.parseFloat(t.get(i)) : 0f;
    }

    private static float arcFlag(List<String> t, int[] idx) {
        if (idx[0] >= t.size()) return 0f;
        String tok = t.get(idx[0]);
        if (tok.length() > 1 && tok.charAt(0) >= '0' && tok.charAt(0) <= '1') {
            char flag = tok.charAt(0);
            t.set(idx[0], tok.substring(1));
            return flag - '0';
        }
        return f(t, idx[0]++);
    }

    private static void arcTo(Path2D p, float x0, float y0, float rx, float ry, float phi,
                              boolean largeArc, boolean sweep, float x1, float y1) {
        if (rx == 0 || ry == 0) { p.lineTo(x1, y1); return; }
        float cosPhi = (float) Math.cos(phi), sinPhi = (float) Math.sin(phi);
        float dx2 = (x0 - x1) / 2, dy2 = (y0 - y1) / 2;
        float x1p = cosPhi * dx2 + sinPhi * dy2;
        float y1p = -sinPhi * dx2 + cosPhi * dy2;
        float rx2 = rx * rx, ry2 = ry * ry, x1p2 = x1p * x1p, y1p2 = y1p * y1p;
        float lambda = x1p2 / rx2 + y1p2 / ry2;
        if (lambda > 1) { float s = (float) Math.sqrt(lambda); rx *= s; ry *= s; rx2 = rx*rx; ry2 = ry*ry; }
        float num = Math.max(0, rx2 * ry2 - rx2 * y1p2 - ry2 * x1p2);
        float den = rx2 * y1p2 + ry2 * x1p2;
        float sq = (float) Math.sqrt(num / den);
        if (largeArc == sweep) sq = -sq;
        float cxp = sq * rx * y1p / ry;
        float cyp = -sq * ry * x1p / rx;
        float cx0 = cosPhi * cxp - sinPhi * cyp + (x0 + x1) / 2;
        float cy0 = sinPhi * cxp + cosPhi * cyp + (y0 + y1) / 2;
        float theta1 = angle(1, 0, (x1p - cxp) / rx, (y1p - cyp) / ry);
        float dTheta = angle((x1p - cxp) / rx, (y1p - cyp) / ry, (-x1p - cxp) / rx, (-y1p - cyp) / ry);
        if (!sweep && dTheta > 0) dTheta -= 2 * (float) Math.PI;
        if (sweep && dTheta < 0) dTheta += 2 * (float) Math.PI;
        int n = (int) Math.ceil(Math.abs(dTheta) / (Math.PI / 2));
        float step = dTheta / n;
        float t = step / 2;
        float alpha = (float) Math.sin(t) * (1 + 4f/3f * (1 - Math.cos(t)) / Math.cos(t));
        for (int i = 0; i < n; i++) {
            float th = theta1 + i * step;
            float cosTh = (float) Math.cos(th), sinTh = (float) Math.sin(th);
            float cosTh2 = (float) Math.cos(th + step), sinTh2 = (float) Math.sin(th + step);
            float ep0x = cosTh - alpha * sinTh, ep0y = sinTh + alpha * cosTh;
            float ep1x = cosTh2 + alpha * sinTh2, ep1y = sinTh2 - alpha * cosTh2;
            float cp0x = rx * (cosTh + alpha * sinTh), cp0y = ry * (sinTh - alpha * cosTh);
            float cp1x = rx * (cosTh2 - alpha * sinTh2), cp1y = ry * (sinTh2 + alpha * cosTh2);
            float px0 = cosPhi * cp0x - sinPhi * cp0y + cx0;
            float py0 = sinPhi * cp0x + cosPhi * cp0y + cy0;
            float px1 = cosPhi * cp1x - sinPhi * cp1y + cx0;
            float py1 = sinPhi * cp1x + cosPhi * cp1y + cy0;
            float ex = cosPhi * (rx * ep1x) - sinPhi * (ry * ep1y) + cx0;
            float ey = sinPhi * (rx * ep1x) + cosPhi * (ry * ep1y) + cy0;
            p.curveTo(px0, py0, px1, py1, ex, ey);
        }
    }

    private static float angle(float ux, float uy, float vx, float vy) {
        return (float) Math.atan2(ux * vy - uy * vx, ux * vx + uy * vy);
    }

    /* ═══════════════════ UTILS ═══════════════════ */
    private static void fillRR(Graphics2D g, float x, float y, float w, float h, float r, Color c) {
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

    private void drawSpaced(Graphics2D g, String text, float x, float y, float sp, Color color) {
        g.setColor(color);
        FontMetrics fm = g.getFontMetrics();
        float cx = x;
        boolean lastSp = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == ' ') { cx += fm.stringWidth(" "); lastSp = true; }
            else {
                if (!lastSp && i > 0) cx += sp;
                g.drawString(String.valueOf(ch), cx, y);
                cx += fm.stringWidth(String.valueOf(ch));
                lastSp = false;
            }
        }
    }

    private float spacedW(Graphics2D g, String text, float sp) {
        FontMetrics fm = g.getFontMetrics();
        float w = 0;
        boolean lastSp = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == ' ') { w += fm.stringWidth(" "); lastSp = true; }
            else { if (!lastSp && i > 0) w += sp; w += fm.stringWidth(String.valueOf(ch)); lastSp = false; }
        }
        return w;
    }

    private static String fmtBal(long v) {
        String d = String.valueOf(Math.abs(v));
        StringBuilder sb = new StringBuilder(d);
        for (int i = sb.length() - 3; i > 0; i -= 3) sb.insert(i, ' ');
        if (v < 0) sb.insert(0, '-');
        return sb.toString();
    }
}