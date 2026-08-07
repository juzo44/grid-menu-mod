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
 * Pixel-perfect Java2D renderer — port of HTML mockup.
 * Renders at 960x540 (half of 1920x1080), GPU upscales.
 * Optimised: bg cached, reusable BufferedImage, no per-frame alloc.
 */
public final class GridRenderer {

    public static final int BASE_W = 960;
    public static final int BASE_H = 540;
    private static final float SC = 0.5f;

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
    private static final Color NEWS_LINE     = new Color(52, 64, 56, 102);

    private Font f400, f500, f600, f700, f900;
    private BufferedImage bgPhoto;
    private JsonObject authData;
    private JsonArray newsData;
    private int serverState = 2;
    private int onlinePlayers;
    private int maxPlayers;
    private BufferedImage cachedBg;
    private float cachedBgScale = -1;
    public final List<BtnRect> buttons = new ArrayList<>();
    private float screenScale = 1f;
    private BufferedImage reusableImg;

    public GridRenderer() {}

    public void init() {
        System.setProperty("java.awt.headless", "true");
        try {
            Font b400 = loadTtf("font/inter_400.ttf");
            Font b600 = loadTtf("font/inter_600.ttf");
            Font b700 = loadTtf("font/inter_700.ttf");
            f400 = b400.deriveFont(Font.PLAIN, 13f);
            f500 = b600.deriveFont(Font.PLAIN, 13f);
            f600 = b600.deriveFont(Font.PLAIN, 13f);
            f700 = b700.deriveFont(Font.PLAIN, 13f);
            f900 = b700.deriveFont(Font.BOLD, 13f);
        } catch (Exception e) {
            f400 = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
            f500 = f600 = f400.deriveFont(Font.BOLD);
            f700 = f900 = f600;
        }
        try (InputStream is = MinecraftHolder.getResource("textures/gui/ui/bg_menu.png")) {
            if (is != null) bgPhoto = ImageIO.read(is);
        } catch (Exception ignored) { bgPhoto = null; }
    }

    private Font loadTtf(String path) throws IOException, FontFormatException {
        try (InputStream is = MinecraftHolder.getResource(path)) {
            return Font.createFont(Font.TRUETYPE_FONT, is);
        }
    }

    public void setAuth(JsonObject d) { this.authData = d; }
    public void setNews(JsonArray d) { this.newsData = d; }
    public void setServerStatus(int st, int on, int mx) {
        this.serverState = st; this.onlinePlayers = on; this.maxPlayers = mx;
    }

    public void onResize(int sw, int sh) {
        float ns = Math.min((float) sw / BASE_W, (float) sh / BASE_H);
        if (Math.abs(ns - cachedBgScale) > 0.01f) cachedBg = null;
    }

    public BufferedImage render(int screenW, int screenH, int mx, int my) {
        screenScale = Math.min((float) screenW / BASE_W, (float) screenH / BASE_H);
        int W = BASE_W, H = BASE_H;
        int bmx = (int) (mx / screenScale);
        int bmy = (int) (my / screenScale);

        if (reusableImg == null) reusableImg = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D cg = reusableImg.createGraphics();
        cg.setComposite(AlphaComposite.Clear);
        cg.fillRect(0, 0, W, H);
        cg.dispose();

        Graphics2D g = reusableImg.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        buttons.clear();

        if (cachedBg == null) {
            cachedBg = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
            Graphics2D bg = cachedBg.createGraphics();
            bg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            bg.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            paintBg(bg, W, H);
            bg.dispose();
            cachedBgScale = screenScale;
        }
        g.drawImage(cachedBg, 0, 0, null);

        int pad = s(40);
        int topH = s(60);
        paintTopbar(g, W, pad, s(20));

        int contentY = topH;
        int contentH = H - contentY;
        int rightW = s(280);
        int rightX = W - pad - rightW;
        int menuW = s(440);
        int availForMenu = rightX - pad;
        int menuX = pad + (availForMenu - menuW) / 2;

        g.setFont(f900.deriveFont(52f * SC));
        FontMetrics tfm = g.getFontMetrics();
        int titleBoxH = tfm.getAscent() + tfm.getDescent() + s(10) + s(12);
        g.setFont(f600.deriveFont(11f * SC));
        FontMetrics tagfm = g.getFontMetrics();
        int tagH = s(4) + tagfm.getHeight() + s(4);
        int titleBlockH = titleBoxH + tagH - s(2);
        int titleGap = s(48);
        int playH = s(72);
        int singleH = s(62);
        int smallH = s(46);
        int btnGap = s(10);
        int totalMenuH = titleBlockH + titleGap + playH + btnGap + singleH + btnGap + smallH;
        int baseY = contentY + (contentH - totalMenuH) / 2;

        paintTitle(g, menuX, menuW, baseY);
        paintPlayBtn(g, menuX, baseY + titleBlockH + titleGap, menuW, playH, bmx, bmy);
        paintSingleBtn(g, menuX, baseY + titleBlockH + titleGap + playH + btnGap, menuW, singleH, bmx, bmy);
        paintSmallBtns(g, menuX, baseY + titleBlockH + titleGap + playH + btnGap + singleH + btnGap, menuW, smallH, bmx, bmy);

        boolean isOn = serverState == 1;
        int statusH = isOn ? s(110) : s(70);
        int nc = (newsData == null) ? 0 : Math.min(4, newsData.size());
        int newsH = s(18) + s(12) + s(9) + (nc == 0 ? s(24) : nc * s(30) + s(8)) + s(18);
        int panelGap = s(10);
        int rightTotalH = statusH + panelGap + newsH;
        int rightY = contentY + (contentH - rightTotalH) / 2;
        paintStatusPanel(g, rightX, rightY, rightW, statusH);
        paintNewsPanel(g, rightX, rightY + statusH + panelGap, rightW, newsH);

        int socSize = s(38);
        int socGap = s(10);
        int socY = H - s(24) - socSize;
        int socR = W - s(40);
        paintSocial(g, socR - socSize * 3 - socGap * 2, socY, socSize, bmx, bmy, "telegram");
        paintSocial(g, socR - socSize * 2 - socGap, socY, socSize, bmx, bmy, "discord");
        paintSocial(g, socR - socSize, socY, socSize, bmx, bmy, "globe");

        g.setFont(f400.deriveFont(10f * SC));
        g.setColor(TEXT_DIM);
        g.drawString("Minecraft 1.21.1 \u00B7 NeoForge \u00B7 GRID v0.1.0", pad, H - s(10) - s(3));

        g.dispose();

        for (int i = 0; i < buttons.size(); i++) {
            BtnRect b = buttons.get(i);
            buttons.set(i, new BtnRect(
                (int) (b.x * screenScale), (int) (b.y * screenScale),
                (int) (b.w * screenScale), (int) (b.h * screenScale), b.id));
        }
        return reusableImg;
    }

    /* ===== BACKGROUND (cached) ===== */
    private void paintBg(Graphics2D g, int W, int H) {
        if (bgPhoto != null) {
            double sc = Math.max((double) W / bgPhoto.getWidth(), (double) H / bgPhoto.getHeight());
            int sw = (int) (bgPhoto.getWidth() * sc);
            int sh = (int) (bgPhoto.getHeight() * sc);
            g.drawImage(bgPhoto, (W - sw) / 2, (H - sh) / 2, sw, sh, null);
        }
        for (int y = 0; y < H; y++) {
            float t = (float) y / Math.max(1, H - 1);
            float a;
            if (t < 0.4f) a = 0.72f + (0.50f - 0.72f) * (t / 0.4f);
            else a = 0.50f + (0.68f - 0.50f) * ((t - 0.4f) / 0.6f);
            g.setColor(new Color(11, 15, 12, (int) (a * 255)));
            g.fillRect(0, y, W, 1);
        }
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

    /* ===== TOP BAR ===== */
    private void paintTopbar(Graphics2D g, int W, int pad, int topPad) {
        int bm = s(32);
        drawClippedRect(g, pad, topPad, bm, bm, s(5), ACCENT);
        g.setFont(f900.deriveFont(17f * SC));
        g.setColor(BG_DEEP);
        FontMetrics fmG = g.getFontMetrics();
        String gL = "G";
        g.drawString(gL, pad + (bm - fmG.stringWidth(gL)) / 2, topPad + (bm + fmG.getAscent()) / 2 - fmG.getDescent());
        g.setFont(f700.deriveFont(13f * SC));
        drawSpaced(g, "GRID", pad + bm + s(12), topPad + bm / 2 + (g.getFontMetrics().getAscent() + g.getFontMetrics().getDescent()) / 2 - g.getFontMetrics().getDescent(), s(3), TEXT_MAIN);
        paintAuthCard(g, W, pad, topPad);
    }

    /* ===== AUTH CARD ===== */
    private void paintAuthCard(Graphics2D g, int W, int pad, int topPad) {
        boolean authed = authData != null;
        String nick = "\u0413\u041E\u0421\u0422\u042C";
        String rank = "";
        if (authed) {
            if (authData.has("username")) nick = authData.get("username").getAsString().toUpperCase();
            if (authData.has("donate") && !authData.get("donate").getAsString().isEmpty())
                rank = authData.get("donate").getAsString().toUpperCase();
            if (rank.isEmpty() && authData.has("rank")) rank = authData.get("rank").getAsString().toUpperCase();
        }
        g.setFont(f600.deriveFont(12f * SC));
        int nickW = g.getFontMetrics().stringWidth(nick);
        int rankBadgeW = 0;
        if (!rank.isEmpty()) {
            g.setFont(f600.deriveFont(10f * SC));
            rankBadgeW = s(5) + g.getFontMetrics().stringWidth(rank) + s(5) + s(7);
        }
        int line1W = nickW + rankBadgeW;
        String balLabel = authed ? "\u0411\u0430\u043B\u0430\u043D\u0441: " : "\u0410\u0432\u0442\u043E\u0440\u0438\u0437\u0430\u0446\u0438\u044F";
        String balVal = authed ? fmtBal(balance()) + " \u20BD" : "";
        g.setFont(f400.deriveFont(10f * SC));
        int line2W = g.getFontMetrics().stringWidth(balLabel) + (balVal.isEmpty() ? 0 : g.getFontMetrics().stringWidth(balVal));
        int cw = s(14) * 2 + Math.max(line1W, line2W);
        g.setFont(f600.deriveFont(12f * SC));
        int l1h = g.getFontMetrics().getHeight();
        g.setFont(f400.deriveFont(10f * SC));
        int l2h = g.getFontMetrics().getHeight();
        int ch = (authed ? s(7) * 2 + l1h + s(2) + l2h : s(7) * 2 + l2h);
        int cy = (s(60) - ch) / 2;
        int cx = W - pad - cw;
        fillRR(g, cx, cy, cw, ch, s(10), LINE);
        fillRR(g, cx + 1, cy + 1, cw - 2, ch - 2, s(9), PANEL_BG);
        int tx = cx + s(14);
        int ty = cy + s(7);
        g.setFont(f600.deriveFont(12f * SC));
        g.setColor(TEXT_MAIN);
        g.drawString(nick, tx, ty + g.getFontMetrics().getAscent());
        if (!rank.isEmpty()) {
            g.setFont(f600.deriveFont(10f * SC));
            FontMetrics fmR = g.getFontMetrics();
            int rx = tx + nickW + s(7);
            int rw = s(5) + fmR.stringWidth(rank) + s(5);
            int rh = fmR.getHeight() + s(2);
            int ry = ty + (g.getFontMetrics().getAscent() - fmR.getAscent()) - s(1);
            fillRR(g, rx, ry, rw, rh, s(3), ACCENT_DIM);
            g.setColor(ACCENT);
            g.drawString(rank, rx + s(5), ry + s(1) + fmR.getAscent());
        }
        if (authed) {
            int by = ty + l1h + s(2);
            g.setFont(f400.deriveFont(10f * SC));
            g.setColor(TEXT_MUTED);
            g.drawString(balLabel, tx, by + g.getFontMetrics().getAscent());
            if (!balVal.isEmpty()) {
                int lw = g.getFontMetrics().stringWidth(balLabel);
                g.setFont(f600.deriveFont(10f * SC));
                g.setColor(ACCENT);
                g.drawString(balVal, tx + lw + s(4), by + g.getFontMetrics().getAscent());
            }
        }
    }

    private long balance() {
        return (authData != null && authData.has("balance")) ? authData.get("balance").getAsLong() : 0L;
    }

    /* ===== 3D TITLE ===== */
    private void paintTitle(Graphics2D g, int menuX, int menuW, int baseY) {
        g.setFont(f900.deriveFont(52f * SC));
        FontMetrics fm = g.getFontMetrics();
        int ls = s(8);
        int tw = (int) spacedW(g, "GRID", ls);
        int px = s(36);
        int bw = tw + px * 2;
        int bh = fm.getAscent() + fm.getDescent() + s(10) + s(12);
        int bx = menuX + (menuW - bw) / 2;
        int r = s(8);
        Composite old = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.50f));
        fillRR(g, bx - s(4), baseY + s(9), bw + s(8), bh + s(8), r + s(2), Color.BLACK);
        g.setComposite(old);
        fillRR(g, bx, baseY + s(7), bw, bh, r, ACCENT_DARKER);
        fillRR(g, bx, baseY + s(5), bw, bh, r, ACCENT_DARK);
        fillRR(g, bx, baseY, bw, bh, r, ACCENT);
        g.setColor(BG_DEEP);
        float txX = bx + px + (bw - px * 2 - tw) / 2;
        drawSpaced(g, "GRID", txX, baseY + s(10) + fm.getAscent(), ls, BG_DEEP);
        g.setFont(f600.deriveFont(11f * SC));
        FontMetrics fmT = g.getFontMetrics();
        String tag = "\u0412\u041E\u0415\u041D\u041D\u041E-\u041F\u041E\u041B\u0418\u0422\u0418\u0427\u0415\u0421\u041A\u0418\u0419 \u0421\u0415\u0420\u0412\u0415\u0420";
        float tLs = 1.5f * SC;
        int ttw = (int) spacedW(g, tag, tLs);
        int tpx = s(16);
        int tW = ttw + tpx * 2;
        int tH = s(4) + fmT.getHeight() + s(4);
        int tY = baseY + bh - s(2);
        int tX = menuX + (menuW - tW) / 2;
        fillRR(g, tX, tY, tW, tH, s(4), ACCENT_BORDER);
        fillRR(g, tX + 1, tY + 1, tW - 2, tH - 2, s(3), ACCENT_DIM);
        g.setColor(ACCENT);
        drawSpaced(g, tag, tX + tpx + (tW - tpx * 2 - ttw) / 2, tY + s(4) + fmT.getAscent(), tLs, ACCENT);
    }

    /* ===== PLAY BUTTON ===== */
    private void paintPlayBtn(Graphics2D g, int x, int y, int w, int h, int mx, int my) {
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + h;
        buttons.add(new BtnRect(x, y, w, h, "play"));
        Composite old = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, hov ? 0.20f : 0.10f));
        fillRR(g, x - s(6), y - s(6), w + s(12), h + s(12), s(18), ACCENT);
        g.setComposite(old);
        fillRR(g, x, y, w, h, s(12), hov ? ACCENT_HOVER : ACCENT);
        int icx = x + s(20) + s(18);
        int icy = y + h / 2;
        drawPlayIc(g, icx, icy, s(22), BG_DEEP);
        int txX = x + s(20) + s(36) + s(16);
        g.setFont(f700.deriveFont(15f * SC));
        FontMetrics fmT = g.getFontMetrics();
        g.setFont(f400.deriveFont(11f * SC));
        FontMetrics fmD = g.getFontMetrics();
        int ttH = fmT.getHeight() + s(2) + fmD.getHeight();
        int tbY = y + (h - ttH) / 2;
        g.setFont(f700.deriveFont(15f * SC));
        g.setColor(BG_DEEP);
        drawSpaced(g, "\u0418\u0413\u0420\u0410\u0422\u042C", txX, tbY + fmT.getAscent(), s(1), BG_DEEP);
        g.setFont(f400.deriveFont(11f * SC));
        g.setColor(new Color(11, 15, 12, 153));
        g.drawString("\u041F\u043E\u0434\u043A\u043B\u044E\u0447\u0435\u043D\u0438\u0435 \u043A \u0441\u0435\u0440\u0432\u0435\u0440\u0443",
            txX, tbY + fmT.getHeight() + s(2) + fmD.getAscent());
    }

    /* ===== SINGLE BUTTON ===== */
    private void paintSingleBtn(Graphics2D g, int x, int y, int w, int h, int mx, int my) {
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + h;
        buttons.add(new BtnRect(x, y, w, h, "single"));
        fillRR(g, x, y, w, h, s(10), hov ? ACCENT_BORDER : LINE);
        fillRR(g, x + 1, y + 1, w - 2, h - 2, s(9), hov ? BTN_SEC_HOVER : BTN_SEC_BG);
        drawCheckIc(g, x + s(20) + s(18), y + h / 2, s(22), ACCENT);
        int txX = x + s(20) + s(36) + s(16);
        g.setFont(f700.deriveFont(15f * SC));
        FontMetrics fmT = g.getFontMetrics();
        g.setFont(f400.deriveFont(11f * SC));
        FontMetrics fmD = g.getFontMetrics();
        int ttH = fmT.getHeight() + s(2) + fmD.getHeight();
        int tbY = y + (h - ttH) / 2;
        g.setFont(f700.deriveFont(15f * SC));
        g.setColor(TEXT_MAIN);
        drawSpaced(g, "\u041E\u0414\u0418\u041D\u041E\u0427\u041D\u042B\u0419 \u041C\u0418\u0420", txX, tbY + fmT.getAscent(), s(1), TEXT_MAIN);
        g.setFont(f400.deriveFont(11f * SC));
        g.setColor(TEXT_MUTED);
        g.drawString("\u041E\u0434\u0438\u043D\u043E\u0447\u043D\u0430\u044F \u0438\u0433\u0440\u0430",
            txX, tbY + fmT.getHeight() + s(2) + fmD.getAscent());
    }

    /* ===== SMALL BUTTONS ===== */
    private void paintSmallBtns(Graphics2D g, int mx0, int y, int mw, int h, int mx, int my) {
        String[] labels = {"\u041D\u0410\u0421\u0422\u0420\u041E\u0419\u041A\u0418", "\u041E \u0421\u0415\u0420\u0412\u0415\u0420\u0415", "\u041C\u0410\u0413\u0410\u0417\u0418\u041D", "\u0412\u042B\u0425\u041E\u0414"};
        String[] ids = {"settings", "info", "shop", "exit"};
        int gap = s(8);
        int bw = (mw - gap * 3) / 4;
        for (int i = 0; i < 4; i++) {
            int bx = mx0 + i * (bw + gap);
            boolean hov = mx >= bx && mx < bx + bw && my >= y && my < y + h;
            boolean isExit = "exit".equals(ids[i]);
            buttons.add(new BtnRect(bx, y, bw, h, ids[i]));
            Color bc = isExit && hov ? new Color(220, 80, 80, 77) : (hov ? ACCENT_BORDER : LINE);
            fillRR(g, bx, y, bw, h, s(10), bc);
            fillRR(g, bx + 1, y + 1, bw - 2, h - 2, s(9), hov ? BTN_SM_HOVER : BTN_SM_BG);
            g.setFont(f500.deriveFont(11f * SC));
            FontMetrics fm = g.getFontMetrics();
            int icS = s(14);
            int icG = s(8);
            float tLs = 0.8f * SC;
            int tW = (int) spacedW(g, labels[i], tLs);
            int total = icS + icG + tW;
            int sx = bx + (bw - total) / 2;
            int sy = y + (h - fm.getHeight()) / 2 + fm.getAscent();
            Color ic = isExit && hov ? new Color(0xE0, 0x55, 0x55) : (hov ? ACCENT : TEXT_MUTED);
            int icx = sx + icS / 2;
            int icy = y + h / 2;
            switch (i) {
                case 0 -> drawGearIc(g, icx, icy, icS, ic);
                case 1 -> drawInfoIc(g, icx, icy, icS, ic);
                case 2 -> drawBagIc(g, icx, icy, icS, ic);
                case 3 -> drawExitIc(g, icx, icy, icS, ic);
            }
            drawSpaced(g, labels[i], sx + icS + icG, sy, tLs, hov ? TEXT_MAIN : TEXT_MUTED);
        }
    }

    /* ===== STATUS PANEL ===== */
    private void paintStatusPanel(Graphics2D g, int x, int y, int w, int h) {
        fillRR(g, x, y, w, h, s(10), LINE);
        fillRR(g, x + 1, y + 1, w - 2, h - 2, s(9), PANEL_BG);
        g.setFont(f600.deriveFont(10f * SC));
        g.setColor(TEXT_MUTED);
        int tY = y + s(18);
        drawSpaced(g, "\u0421\u0422\u0410\u0422\u0423\u0421 \u0421\u0415\u0420\u0412\u0415\u0420\u0410",
            x + s(18), tY + g.getFontMetrics().getAscent(), 1.5f * SC, TEXT_MUTED);
        int rowY = tY + g.getFontMetrics().getHeight() + s(12);
        boolean on = serverState == 1;
        Color dotC = on ? ACCENT : new Color(0x61, 0x6A, 0x64);
        int ds = s(6);
        int dcy = rowY + (s(11) - ds) / 2 + ds / 2;
        g.setColor(dotC);
        g.fillOval(x + s(18), dcy - ds / 2, ds, ds);
        g.setFont(f400.deriveFont(11f * SC));
        String lbl;
        Color lblC;
        if (on) { lbl = "\u0421\u0435\u0440\u0432\u0435\u0440 \u0440\u0430\u0431\u043E\u0442\u0430\u0435\u0442"; lblC = ACCENT; }
        else if (serverState == 0) { lbl = "\u0421\u0435\u0440\u0432\u0435\u0440 \u043D\u0435\u0434\u043E\u0441\u0442\u0443\u043F\u0435\u043D"; lblC = new Color(0xE0, 0x66, 0x66); }
        else { lbl = "\u041F\u0440\u043E\u0432\u0435\u0440\u043A\u0430..."; lblC = TEXT_MUTED; }
        g.setColor(lblC);
        g.drawString(lbl, x + s(18) + ds + s(7), rowY + g.getFontMetrics().getAscent());
        if (on) {
            int numY = rowY + s(11) + s(8);
            g.setFont(f700.deriveFont(26f * SC));
            g.setColor(TEXT_MAIN);
            String ns = String.valueOf(onlinePlayers);
            g.drawString(ns, x + s(18), numY + g.getFontMetrics().getAscent());
            int nw = g.getFontMetrics().stringWidth(ns);
            g.setFont(f400.deriveFont(12f * SC));
            g.setColor(TEXT_MUTED);
            g.drawString("\u0438\u0433\u0440\u043E\u043A\u0430", x + s(18) + nw + s(3), numY + g.getFontMetrics().getAscent());
            int barY = numY + g.getFontMetrics().getHeight() + s(10);
            int barX = x + s(18);
            int barW = w - s(36);
            int barH = s(3);
            fillRR(g, barX, barY, barW, barH, s(2), LINE);
            int fW = maxPlayers > 0 ? Math.max(barH, (int) (barW * Math.min(1f, (float) onlinePlayers / maxPlayers))) : barH;
            fillRR(g, barX, barY, fW, barH, s(2), ACCENT);
        }
    }

    /* ===== NEWS PANEL ===== */
    private void paintNewsPanel(Graphics2D g, int x, int y, int w, int h) {
        fillRR(g, x, y, w, h, s(10), LINE);
        fillRR(g, x + 1, y + 1, w - 2, h - 2, s(9), PANEL_BG);
        g.setFont(f600.deriveFont(10f * SC));
        g.setColor(TEXT_MUTED);
        int tY = y + s(18);
        drawSpaced(g, "\u041D\u041E\u0412\u041E\u0421\u0422\u0418", x + s(18), tY + g.getFontMetrics().getAscent(), 1.5f * SC, TEXT_MUTED);
        int listY = tY + g.getFontMetrics().getHeight() + s(12);
        if (newsData == null) {
            g.setFont(f400.deriveFont(11f * SC));
            g.setColor(TEXT_DIM);
            g.drawString("\u0417\u0430\u0433\u0440\u0443\u0437\u043A\u0430...", x + s(18), listY + s(8) + g.getFontMetrics().getAscent());
            return;
        }
        if (newsData.size() == 0) {
            g.setFont(f400.deriveFont(11f * SC));
            g.setColor(TEXT_DIM);
            g.drawString("\u041D\u043E\u0432\u043E\u0441\u0442\u0435\u0439 \u043F\u043E\u043A\u0430 \u043D\u0435\u0442", x + s(18), listY + s(8) + g.getFontMetrics().getAscent());
            return;
        }
        int maxTW = w - s(36);
        int iy = listY;
        int shown = 0;
        for (JsonElement el : newsData) {
            if (shown >= 4) break;
            JsonObject item = el.getAsJsonObject();
            String title = item.has("title") ? item.get("title").getAsString() : "";
            String date = item.has("publishedAt") ? item.get("publishedAt").getAsString() : "";
            if (date.length() >= 10) date = date.substring(8, 10) + "." + date.substring(5, 7) + "." + date.substring(0, 4);
            int itY = iy + s(8);
            g.setFont(f400.deriveFont(9f * SC));
            g.setColor(TEXT_DIM);
            g.drawString(date, x + s(18), itY + g.getFontMetrics().getAscent());
            g.setFont(f500.deriveFont(11f * SC));
            FontMetrics fmT = g.getFontMetrics();
            String cl = title;
            if (fmT.stringWidth(cl) > maxTW) {
                while (cl.length() > 3 && fmT.stringWidth(cl + "...") > maxTW) cl = cl.substring(0, cl.length() - 1);
                cl += "...";
            }
            g.setColor(TEXT_MAIN);
            g.drawString(cl, x + s(18), itY + s(12) + fmT.getAscent());
            if (shown < 3 && shown < newsData.size() - 1) {
                g.setColor(NEWS_LINE);
                g.fillRect(x + s(18), itY + s(28), maxTW, 1);
            }
            iy = itY + s(30);
            shown++;
        }
    }

    /* ===== SOCIAL ===== */
    private void paintSocial(Graphics2D g, int x, int y, int sz, int mx, int my, String type) {
        boolean hov = mx >= x && mx < x + sz && my >= y && my < y + sz;
        buttons.add(new BtnRect(x, y, sz, sz, "social_" + type));
        g.setColor(hov ? ACCENT_BORDER : LINE);
        g.fillOval(x, y, sz, sz);
        g.setColor(hov ? ACCENT_DIM : new Color(12, 16, 14, 179));
        g.fillOval(x + 1, y + 1, sz - 2, sz - 2);
        int icS = s(18);
        int cx = x + sz / 2;
        int cy = y + sz / 2;
        Color ic = hov ? ACCENT : TEXT_MUTED;
        switch (type) {
            case "telegram" -> drawTgIc(g, cx, cy, icS, ic);
            case "discord"  -> drawDcIc(g, cx, cy, icS, ic);
            case "globe"    -> drawGlobeIc(g, cx, cy, icS, ic);
        }
    }

    /* ===== ICONS ===== */
    private void drawPlayIc(Graphics2D g, int cx, int cy, int sz, Color c) {
        float sc = sz / 24f;
        Path2D p = new Path2D.Float();
        p.moveTo(cx - 4 * sc, cy - 7 * sc);
        p.lineTo(cx - 4 * sc, cy + 7 * sc);
        p.lineTo(cx + 7 * sc, cy);
        p.closePath();
        g.setColor(c); g.fill(p);
    }

    private void drawCheckIc(Graphics2D g, int cx, int cy, int sz, Color c) {
        float sc = sz / 24f;
        g.setColor(c);
        g.setStroke(new BasicStroke(Math.max(1.5f, sz * 0.12f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D p = new Path2D.Float();
        p.moveTo(cx + (4.83f - 12) * sc, cy);
        p.lineTo(cx + (9 - 12) * sc, cy + 4.17f * sc);
        p.lineTo(cx + (19.59f - 12) * sc, cy - 6.41f * sc);
        g.draw(p);
        g.setStroke(new BasicStroke(1));
    }

    private void drawGearIc(Graphics2D g, int cx, int cy, int sz, Color c) {
        float sc = sz / 24f;
        g.setColor(c);
        g.setStroke(new BasicStroke(Math.max(1f, sz * 0.06f)));
        int orI = (int) (9 * sc);
        g.drawOval(cx - orI, cy - orI, orI * 2, orI * 2);
        int irI = (int) (3.6f * sc);
        g.drawOval(cx - irI, cy - irI, irI * 2, irI * 2);
        float tl = 3 * sc, tw = 4 * sc;
        for (int i = 0; i < 8; i++) {
            double a = Math.toRadians(i * 45);
            float mR = 8.5f * sc;
            float tx = cx + (float) Math.cos(a) * mR;
            float ty = cy + (float) Math.sin(a) * mR;
            g.translate(tx, ty); g.rotate(a);
            g.fillRect((int) (-tw / 2), (int) (-tl / 2), (int) tw, (int) tl);
            g.rotate(-a); g.translate(-tx, -ty);
        }
        g.setStroke(new BasicStroke(1));
    }

    private void drawInfoIc(Graphics2D g, int cx, int cy, int sz, Color c) {
        float sc = sz / 24f;
        int ri = (int) (10 * sc);
        g.setColor(c);
        g.setStroke(new BasicStroke(Math.max(1f, sz * 0.07f)));
        g.drawOval(cx - ri, cy - ri, ri * 2, ri * 2);
        g.setStroke(new BasicStroke(1));
        int dri = (int) (1.5f * sc);
        g.fillOval(cx - dri, cy - (int) (4 * sc) - dri, dri * 2, dri * 2);
        int sw = (int) (2 * sc);
        g.fillRect(cx - sw / 2, cy - (int) sc, sw, (int) (6 * sc));
    }

    private void drawBagIc(Graphics2D g, int cx, int cy, int sz, Color c) {
        float sc = sz / 24f;
        g.setColor(c);
        float bw = 16 * sc, bh = 12 * sc;
        fillRR(g, cx - bw / 2, cy - bh / 2 + 2 * sc, bw, bh, 2 * sc, c);
        g.setStroke(new BasicStroke(Math.max(1.5f, 1.5f * sc), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Arc2D.Float(cx - 5 * sc, cy - bh / 2 - 2 * sc, 10 * sc, 8 * sc, 180, 180, Arc2D.OPEN));
        g.setStroke(new BasicStroke(1));
        int wri = (int) (2 * sc);
        g.fillOval(cx - (int) (5 * sc) - wri, cy + (int) (bh / 2) + (int) (2 * sc) - wri, wri * 2, wri * 2);
        g.fillOval(cx + (int) (5 * sc) - wri, cy + (int) (bh / 2) + (int) (2 * sc) - wri, wri * 2, wri * 2);
    }

    private void drawExitIc(Graphics2D g, int cx, int cy, int sz, Color c) {
        float sc = sz / 24f;
        g.setColor(c);
        int th = Math.max(2, (int) (1.5f * sc));
        int dl = cx - (int) (9 * sc), dt = cy - (int) (9 * sc);
        int dw = (int) (18 * sc), dh = (int) (18 * sc);
        g.fillRect(dl, dt, th, dh);
        g.fillRect(dl, dt, dw, th);
        g.fillRect(dl, dt + dh - th, dw, th);
        g.setStroke(new BasicStroke(th, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D ar = new Path2D.Float();
        ar.moveTo(cx - 4 * sc, cy); ar.lineTo(cx + 3 * sc, cy);
        g.draw(ar);
        Path2D hd = new Path2D.Float();
        hd.moveTo(cx + 3 * sc, cy - 4 * sc); hd.lineTo(cx + 7.5f * sc, cy); hd.lineTo(cx + 3 * sc, cy + 4 * sc);
        g.draw(hd);
        g.setStroke(new BasicStroke(1));
    }

    /** Telegram — exact SVG path from mockup, offset to center in 24x24 viewBox */
    private void drawTgIc(Graphics2D g, int cx, int cy, int sz, Color c) {
        float sc = sz / 24f;
        g.setColor(c);
        Path2D p = svgPath(
            "M11.944 0A12 12 0 000 12a12 12 0 0012 12 12 12 0 0012-12A12 12 0 0012 0" +
            "a12 12 0 00-.056 0zm4.962 7.224c.1-.002.321.023.465.14a.506.506 0 01.171.325" +
            "c.016.093.036.306.02.472-.18 1.898-.962 6.502-1.36 8.627-.168.9-.499 1.201-.82 1.23" +
            "-.696.065-1.225-.46-1.9-.902-1.056-.693-1.653-1.124-2.678-1.8-1.185-.78-.417-1.21.258-1.91" +
            ".177-.184 3.247-2.977 3.307-3.23.007-.032.014-.15-.056-.212s-.174-.041-.249-.024" +
            "c-.106.024-1.793 1.14-5.061 3.345-.479.33-.913.49-1.302.48-.428-.008-1.252-.241-1.865-.44" +
            "-.752-.245-1.349-.374-1.297-.789.027-.216.325-.437.893-.663 3.498-1.524 5.83-2.529 6.998-3.014" +
            " 3.332-1.386 4.025-1.627 4.476-1.635z"
        );
        // Center the 24x24 path around (cx, cy)
        g.translate(cx - 12 * sc, cy - 12 * sc);
        g.scale(sc, sc);
        g.fill(p);
        g.scale(1 / sc, 1 / sc);
        g.translate(-(cx - 12 * sc), -(cy - 12 * sc));
    }

    /** Discord — exact SVG path from mockup */
    private void drawDcIc(Graphics2D g, int cx, int cy, int sz, Color c) {
        float sc = sz / 24f;
        g.setColor(c);
        Path2D p = svgPath(
            "M20.317 4.37a19.79 19.79 0 00-4.885-1.515.074.074 0 00-.079.037c-.21.375-.445.865-.608 1.25" +
            "-1.845-.276-3.68-.276-5.487 0-.164-.393-.406-.874-.618-1.25a.077.077 0 00-.078-.037" +
            " 19.74 19.74 0 00-4.885 1.515.07.07 0 00-.032.028C.533 9.046-.319 13.58.099 18.058" +
            "a.082.082 0 00.031.056c2.053 1.508 4.041 2.423 5.993 3.029a.078.078 0 00.084-.028" +
            "c.462-.63.873-1.295 1.226-1.994a.076.076 0 00-.042-.106c-.653-.247-1.274-.549-1.872-.892" +
            "a.077.077 0 01-.008-.128c.126-.094.252-.192.372-.291a.074.074 0 01.078-.01" +
            "c3.927 1.793 8.18 1.793 12.061 0a.074.074 0 01.078.009c.12.1.246.198.373.293" +
            "a.077.077 0 01-.007.127 12.3 12.3 0 01-1.873.892.076.076 0 00-.041.107" +
            "c.36.698.772 1.362 1.225 1.993a.076.076 0 00.084.028c1.961-.606 3.95-1.522 6.002-3.029" +
            "a.077.077 0 00.031-.055c.5-5.177-.838-9.674-3.549-13.66a.061.061 0 00-.031-.029z" +
            "M8.02 15.33c-1.183 0-2.157-1.086-2.157-2.419 0-1.333.956-2.419 2.157-2.419" +
            " 1.21 0 2.176 1.096 2.157 2.42 0 1.332-.956 2.418-2.157 2.418zm7.975 0" +
            "c-1.183 0-2.157-1.086-2.157-2.419 0-1.333.955-2.419 2.157-2.419" +
            " 1.21 0 2.176 1.096 2.157 2.42 0 1.332-.946 2.418-2.157 2.418z"
        );
        g.translate(cx - 12 * sc, cy - 12 * sc);
        g.scale(sc, sc);
        g.fill(p);
        g.scale(1 / sc, 1 / sc);
        g.translate(-(cx - 12 * sc), -(cy - 12 * sc));
    }

    /** Globe — exact SVG path from mockup */
    private void drawGlobeIc(Graphics2D g, int cx, int cy, int sz, Color c) {
        float sc = sz / 24f;
        g.setColor(c);
        Path2D p = svgPath(
            "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2z" +
            "m-1 17.93c-3.95-.49-7-3.85-7-7.93 0-.62.08-1.21.21-1.79L9 15v1c0 1.1.9 2 2 2z" +
            "m6.9-2.54c-.26-.81-1-1.39-1.9-1.39h-1v-3c0-.55-.45-1-1-1H8v-2h2c.55 0 1-.45 1-1V7" +
            "h2c1.1 0 2-.9 2-2v-.41c2.93 1.19 5 4.06 5 7.41 0 2.08-.8 3.97-2.1 5.39z"
        );
        g.translate(cx - 12 * sc, cy - 12 * sc);
        g.scale(sc, sc);
        g.fill(p);
        g.scale(1 / sc, 1 / sc);
        g.translate(-(cx - 12 * sc), -(cy - 12 * sc));
    }

    /* ===== SVG PATH PARSER ===== */
    /** Minimal SVG path parser — handles M, L, H, V, C, S, Q, T, A, Z commands. */
    private static Path2D svgPath(String d) {
        Path2D p = new Path2D.Float();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
            "[MmLlHhVvCcSsQqTtAaZz]|[-+]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?"
        ).matcher(d);
        java.util.ArrayList<String> tokens = new java.util.ArrayList<>();
        while (m.find()) tokens.add(m.group());
        int i = 0;
        float lastX = 0, lastY = 0, startX = 0, startY = 0;
        float lastCx = 0, lastCy = 0; // for S/T smooth curves
        char lastCmd = ' ';
        while (i < tokens.size()) {
            String t = tokens.get(i++);
            char cmd = t.charAt(0);
            boolean rel = Character.isLowerCase(cmd);
            char upper = Character.toUpperCase(cmd);
            // Implicit command repetition for coordinate-only tokens
            if (Character.isDigit(t.charAt(0)) || t.charAt(0) == '-' || t.charAt(0) == '+') {
                if (lastCmd == 'Z' || lastCmd == 'z') cmd = 'M';
                else cmd = lastCmd;
                rel = Character.isLowerCase(cmd);
                upper = Character.toUpperCase(cmd);
                i--; // re-read this token as a number
            }
            float x = rel ? lastX : 0, y = rel ? lastY : 0;
            switch (upper) {
                case 'M' -> {
                    x += f(tokens, i); i++; y += f(tokens, i); i++;
                    if (rel) { x = lastX + f(tokens, i-2); y = lastY + f(tokens, i-1); }
                    else { x = f(tokens, i-2); y = f(tokens, i-1); }
                    p.moveTo(x, y); lastX = x; lastY = y; startX = x; startY = y;
                    // Subsequent coordinate pairs become L
                    lastCmd = 'L'; continue;
                }
                case 'L' -> {
                    float dx = f(tokens, i++); float dy = f(tokens, i++);
                    x = rel ? lastX + dx : dx; y = rel ? lastY + dy : dy;
                    p.lineTo(x, y); break;
                }
                case 'H' -> { float hv = f(tokens, i++); x = rel ? lastX + hv : hv; p.lineTo(x, lastY); y = lastY; break; }
                case 'V' -> { float vv = f(tokens, i++); y = rel ? lastY + vv : vv; p.lineTo(lastX, y); x = lastX; break; }
                case 'C' -> {
                    float x1 = f(tokens, i++), y1 = f(tokens, i++);
                    float x2 = f(tokens, i++), y2 = f(tokens, i++);
                    float x3 = f(tokens, i++), y3 = f(tokens, i++);
                    if (rel) { x1+=lastX; y1+=lastY; x2+=lastX; y2+=lastY; x3+=lastX; y3+=lastY; }
                    p.curveTo(x1, y1, x2, y2, x3, y3);
                    lastCx = x2; lastCy = y2; x = x3; y = y3; break;
                }
                case 'S' -> {
                    float sx = rel ? lastX : 0, sy = rel ? lastY : 0;
                    float x2 = f(tokens, i++), y2 = f(tokens, i++);
                    float x3 = f(tokens, i++), y3 = f(tokens, i++);
                    if (rel) { x2+=lastX; y2+=lastY; x3+=lastX; y3+=lastY; }
                    float rx = 2*lastX - lastCx, ry = 2*lastY - lastCy;
                    p.curveTo(rx, ry, x2, y2, x3, y3);
                    lastCx = x2; lastCy = y2; x = x3; y = y3; break;
                }
                case 'Q' -> {
                    float x1 = f(tokens, i++), y1 = f(tokens, i++);
                    float x2 = f(tokens, i++), y2 = f(tokens, i++);
                    if (rel) { x1+=lastX; y1+=lastY; x2+=lastX; y2+=lastY; }
                    p.quadTo(x1, y1, x2, y2);
                    lastCx = x1; lastCy = y1; x = x2; y = y2; break;
                }
                case 'T' -> {
                    float x2 = f(tokens, i++), y2 = f(tokens, i++);
                    if (rel) { x2+=lastX; y2+=lastY; }
                    float rx = 2*lastX - lastCx, ry = 2*lastY - lastCy;
                    p.quadTo(rx, ry, x2, y2);
                    lastCx = rx; lastCy = ry; x = x2; y = y2; break;
                }
                case 'A' -> {
                    float rx = f(tokens, i++), ry = f(tokens, i++);
                    float rot = f(tokens, i++);
                    float large = f(tokens, i++), sweep = f(tokens, i++);
                    float ax = f(tokens, i++), ay = f(tokens, i++);
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

    private static float f(java.util.List<String> t, int i) {
        return i < t.size() ? Float.parseFloat(t.get(i)) : 0f;
    }

    /** Convert arc (A/a) to cubic beziers and append to path. */
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

    /* ===== UTILS ===== */
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

    private static int s(float cssPx) {
        return Math.max(1, (int) (cssPx * SC));
    }

    private static String fmtBal(long v) {
        String d = String.valueOf(Math.abs(v));
        StringBuilder sb = new StringBuilder(d);
        for (int i = sb.length() - 3; i > 0; i -= 3) sb.insert(i, ' ');
        if (v < 0) sb.insert(0, '-');
        return sb.toString();
    }
}
