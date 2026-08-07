package ru.gridwarfare.menu;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;

public class GridRendererTest {

    static Font f400, f500, f600, f700, f900;
    static BufferedImage bgPhoto;

    public static void main(String[] args) throws Exception {
        String fontDir = "/home/z/my-project/grid-menu-mod/src/main/resources/assets/gridmenu/font/";
        String bgPath = "/home/z/my-project/grid-menu-mod/src/main/resources/assets/gridmenu/textures/gui/ui/bg_menu.png";
        String outPath = "/home/z/my-project/download/grid_menu_render_test.png";

        f400 = Font.createFont(Font.TRUETYPE_FONT, new FileInputStream(fontDir + "inter_400.ttf"));
        f600 = Font.createFont(Font.TRUETYPE_FONT, new FileInputStream(fontDir + "inter_600.ttf"));
        f700 = Font.createFont(Font.TRUETYPE_FONT, new FileInputStream(fontDir + "inter_700.ttf"));
        f500 = f400.deriveFont(Font.PLAIN, 13f);
        f900 = f700.deriveFont(Font.BOLD, 13f);

        File bgFile = new File(bgPath);
        if (bgFile.exists()) bgPhoto = ImageIO.read(bgFile);

        int W = 1920, H = 1080;
        BufferedImage img = renderFull(W, H);
        Files.createDirectories(Paths.get(outPath).getParent());
        ImageIO.write(img, "PNG", new File(outPath));
        System.out.println("Saved: " + outPath);
    }

    // Colors
    static final Color ACCENT        = new Color(0x68, 0xC2, 0x84);
    static final Color ACCENT_HOVER  = new Color(0x7C, 0xD0, 0x90);
    static final Color ACCENT_DARK   = new Color(0x4A, 0x9C, 0x66);
    static final Color ACCENT_DARKER = new Color(0x38, 0x7A, 0x50);
    static final Color BG_DEEP       = new Color(0x0B, 0x0F, 0x0C);
    static final Color TEXT_MAIN     = new Color(0xF3, 0xF6, 0xF3);
    static final Color TEXT_MUTED    = new Color(0x8B, 0x97, 0x8F);
    static final Color TEXT_DIM      = new Color(0x5A, 0x65, 0x5E);
    static final Color LINE          = new Color(0x34, 0x40, 0x38);
    static final Color PANEL_BG      = new Color(12, 16, 14, 209);
    static final Color ACCENT_DIM    = new Color(104, 194, 132, 38);
    static final Color ACCENT_BORDER = new Color(104, 194, 132, 77);
    static final Color BTN_SEC_BG    = new Color(12, 16, 14, 191);
    static final Color BTN_SEC_HOVER = new Color(18, 24, 20, 217);
    static final Color BTN_SM_BG     = new Color(12, 16, 14, 166);
    static final Color BTN_SM_HOVER  = new Color(18, 24, 20, 204);

    public static BufferedImage renderFull(int W, int H) {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        float sc = Math.min(W / 1920f, H / 1080f);
        int pad = Math.max(24, Math.min(40, (int)(W * 4f / 100f)));

        // drawBackground(g, W, H);  // SKIP background entirely
        g.setColor(new Color(11, 15, 12)); g.fillRect(0, 0, W, H);
        // TEST: draw text directly here
        g.setFont(f700.deriveFont(13f));
        g.setColor(Color.WHITE);
        g.drawString("BEFORE_TOPBAR_TEST", 84, 40);
        drawTopbar(g, W, H, sc, pad);
        g.setColor(Color.RED);
        g.drawString("AFTER_TOPBAR_TEST", 84, 60);

        int menuW = s(440, sc);
        int menuX = pad + (W - pad * 2 - menuW) / 2;
        int rightColW = s(280, sc);
        int rightX = W - pad - rightColW;

        // Compute layout
        g.setFont(f900.deriveFont(52f * sc));
        FontMetrics fmT = g.getFontMetrics();
        int titleTextH = fmT.getAscent();
        int titleDescent = fmT.getDescent();
        int ls = s(8, sc);
        int textW = (int) spacedWidth(g, "GRID", ls);
        int padX = s(36, sc);
        int boxW = textW + padX * 2;
        int boxH = titleTextH + titleDescent + s(10, sc) + s(12, sc);

        g.setFont(f600.deriveFont((float)s(11, sc)));
        FontMetrics fmTag = g.getFontMetrics();
        int tagH = s(4, sc) + fmTag.getHeight() + s(4, sc);
        int titleBlockH = boxH + tagH - s(2, sc);
        int gapTitle = s(48, sc);
        int playH = s(72, sc);
        int singleH = s(62, sc);
        int smallH = s(46, sc);
        int gapBig = s(10, sc);
        int gapRow = s(10, sc);

        int totalH = titleBlockH + gapTitle + playH + gapBig + singleH + gapRow + smallH;
        int contentH = H - s(60, sc);
        int baseY = s(60, sc) + (contentH - totalH) / 2;

        int playY = baseY + titleBlockH + gapTitle;
        int singleY = playY + playH + gapBig;
        int rowY = singleY + singleH + gapRow;

        drawTitle(g, menuX, menuW, baseY, sc);
        drawPlayButton(g, menuX, playY, menuW, playH, sc);
        drawSingleButton(g, menuX, singleY, menuW, singleH, sc);
        drawSmallButtons(g, menuX, rowY, menuW, smallH, sc);

        int statusH = s(110, sc);
        int newsH = s(30, sc) + 4 * s(40, sc);
        int panelGap = s(10, sc);
        int rightTotal = statusH + panelGap + newsH;
        int rightY = s(60, sc) + (contentH - rightTotal) / 2;
        drawStatusPanel(g, rightX, rightY, rightColW, statusH, sc);
        drawNewsPanel(g, rightX, rightY + statusH + panelGap, rightColW, newsH, sc);

        int socSize = s(38, sc);
        int socGap = s(10, sc);
        int socY = H - s(24, sc) - socSize;
        int socRight = W - s(40, sc);
        drawSocialIcon(g, socRight - socSize * 3 - socGap * 2, socY, socSize, sc, "telegram");
        drawSocialIcon(g, socRight - socSize * 2 - socGap, socY, socSize, sc, "discord");
        drawSocialIcon(g, socRight - socSize, socY, socSize, sc, "globe");

        g.setFont(f400.deriveFont((float)s(10, sc)));
        g.setColor(TEXT_DIM);
        g.drawString("Minecraft 1.21.1 \u00B7 NeoForge \u00B7 GRID v0.1.0", pad, H - s(10, sc) - s(3, sc));

        g.dispose();
        return img;
    }

    static void drawBackground(Graphics2D g, int W, int H) {
        if (bgPhoto != null) {
            double scale = Math.max((double) W / bgPhoto.getWidth(), (double) H / bgPhoto.getHeight());
            int sw = (int)(bgPhoto.getWidth() * scale);
            int sh = (int)(bgPhoto.getHeight() * scale);
            g.drawImage(bgPhoto, (W - sw) / 2, (H - sh) / 2, sw, sh, null);
        } else {
            g.setColor(BG_DEEP);
            g.fillRect(0, 0, W, H);
        }
        for (int y = 0; y < H; y++) {
            float t = (float) y / Math.max(1, H - 1);
            float alpha;
            if (t < 0.4f) alpha = 0.72f + (0.50f - 0.72f) * (t / 0.4f);
            else alpha = 0.50f + (0.68f - 0.50f) * ((t - 0.4f) / 0.6f);
            g.setColor(new Color(11, 15, 12, (int)(alpha * 255)));
            g.fillRect(0, y, W, 1);
        }
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

    static void drawTopbar(Graphics2D g, int W, int H, float sc, int pad) {
        int topPad = s(20, sc);
        int bmSize = s(32, sc);
        int cut = s(5, sc);
        drawClippedRect(g, pad, topPad, bmSize, bmSize, cut, ACCENT);
        g.setFont(f900.deriveFont((float)s(17, sc)));
        g.setColor(BG_DEEP);
        FontMetrics fm = g.getFontMetrics();
        String gLetter = "G";
        int gw = fm.stringWidth(gLetter);
        g.drawString(gLetter, pad + (bmSize - gw) / 2, topPad + (bmSize + fm.getAscent()) / 2 - fm.getDescent());

        g.setFont(f700.deriveFont((float)s(13, sc)));
        drawSpaced(g, "GRID", pad + bmSize + s(12, sc), topPad + bmSize / 2 + s(4, sc), s(3, sc), TEXT_MAIN);
        drawAuthCard(g, W, pad, sc);
        // VERY LAST: draw white X
        g.setColor(Color.WHITE);
        g.drawString("X", 84, 40);
    }

    static void drawAuthCard(Graphics2D g, int W, int pad, float sc) {
        String nick = "JUZO44";
        String rank = "VIP";
        String balLabel = "\u0411\u0430\u043B\u0430\u043D\u0441: ";
        String balValue = "5 000 \u20BD";

        g.setFont(f600.deriveFont((float)s(12, sc)));
        FontMetrics fmNick = g.getFontMetrics();
        int nickW = fmNick.stringWidth(nick);
        System.out.println("nickW=" + nickW + " font=" + g.getFont().getFontName() + " size=" + g.getFont().getSize());
        g.setFont(f600.deriveFont((float)s(10, sc)));
        FontMetrics fmRank = g.getFontMetrics();
        int rankW = fmRank.stringWidth(rank);
        System.out.println("rankW=" + rankW + " font=" + g.getFont().getFontName() + " size=" + g.getFont().getSize());

        int line1W = s(14, sc) + nickW + s(7, sc) + rankW + s(5, sc) + s(14, sc);
        g.setFont(f400.deriveFont((float)s(10, sc)));
        FontMetrics fmBal = g.getFontMetrics();
        int line2W = s(14, sc) + fmBal.stringWidth(balLabel) + s(4, sc) + fmBal.stringWidth(balValue) + s(14, sc);

        int cardW = Math.max(line1W, line2W);
        int cardH = s(46, sc);
        int cx = W - pad - cardW;
        int cy = (s(60, sc) - cardH) / 2;
        System.out.println("AUTH CARD: cx=" + cx + " cy=" + cy + " cardW=" + cardW + " cardH=" + cardH);

        fillRR(g, cx, cy, cardW, cardH, s(10, sc), LINE);
        fillRR(g, cx + 1, cy + 1, cardW - 2, cardH - 2, s(9, sc), PANEL_BG);

        int tx = cx + s(14, sc);
        int ty = cy + s(7, sc);

        g.setFont(f600.deriveFont((float)s(12, sc)));
        g.setColor(TEXT_MAIN);
        g.drawString(nick, tx, ty + g.getFontMetrics().getAscent());

        int rx = tx + nickW + s(7, sc);
        int rw = rankW + s(10, sc);
        int rh = g.getFontMetrics().getHeight() + s(2, sc);
        fillRR(g, rx, ty - 1, rw, rh, s(3, sc), ACCENT_DIM);
        g.setFont(f600.deriveFont((float)s(10, sc)));
        g.setColor(ACCENT);
        g.drawString(rank, rx + s(5, sc), ty + g.getFontMetrics().getAscent() - 1);

        int by = ty + s(16, sc);
        g.setFont(f400.deriveFont((float)s(10, sc)));
        g.setColor(TEXT_MUTED);
        g.drawString(balLabel, tx, by + g.getFontMetrics().getAscent());
        int lw = g.getFontMetrics().stringWidth(balLabel);
        g.setColor(ACCENT);
        g.setFont(f600.deriveFont((float)s(10, sc)));
        g.drawString(balValue, tx + lw + s(4, sc), by + g.getFontMetrics().getAscent());
    }

    static void drawTitle(Graphics2D g, int menuX, int menuW, int baseY, float sc) {
        g.setFont(f900.deriveFont(52f * sc));
        FontMetrics fm = g.getFontMetrics();
        int titleTextH = fm.getAscent();
        int ls = s(8, sc);
        int textW = (int) spacedWidth(g, "GRID", ls);
        int padX = s(36, sc);
        int boxW = textW + padX * 2;
        int boxH = titleTextH + fm.getDescent() + s(10, sc) + s(12, sc);
        int boxX = menuX + (menuW - boxW) / 2;

        Composite old = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.15f));
        fillRR(g, boxX - s(6, sc), baseY + s(13, sc), boxW + s(12, sc), boxH + s(4, sc), s(12, sc), Color.BLACK);
        g.setComposite(old);

        fillRR(g, boxX, baseY + s(7, sc), boxW, boxH, s(8, sc), ACCENT_DARKER);
        fillRR(g, boxX, baseY + s(5, sc), boxW, boxH, s(8, sc), ACCENT_DARK);
        fillRR(g, boxX, baseY, boxW, boxH, s(8, sc), ACCENT);

        g.setColor(BG_DEEP);
        float textX = boxX + padX + (boxW - padX * 2 - textW) / 2f;
        float ty = baseY + s(10, sc) + titleTextH;
        drawSpaced(g, "GRID", textX, ty, ls, BG_DEEP);

        g.setFont(f600.deriveFont((float)s(11, sc)));
        FontMetrics fmTag = g.getFontMetrics();
        String tag = "\u0412\u041E\u0415\u041D\u041D\u041E-\u041F\u041E\u041B\u0418\u0422\u0418\u0427\u0415\u0421\u041A\u0418\u0419 \u0421\u0415\u0420\u0412\u0415\u0420";
        int tagLs = (int)(1.5f * sc);
        int tagTextW = (int) spacedWidth(g, tag, tagLs);
        int tagPadX = s(16, sc);
        int tagW = tagTextW + tagPadX * 2;
        int tagH = s(4, sc) + fmTag.getHeight() + s(4, sc);
        int tagY = baseY + boxH - s(2, sc);
        int tagX = menuX + (menuW - tagW) / 2;

        fillRR(g, tagX, tagY, tagW, tagH, s(4, sc), ACCENT_BORDER);
        fillRR(g, tagX + 1, tagY + 1, tagW - 2, tagH - 2, s(3, sc), ACCENT_DIM);
        g.setColor(ACCENT);
        float tagTextX = tagX + tagPadX + (tagW - tagPadX * 2 - tagTextW) / 2f;
        drawSpaced(g, tag, tagTextX, tagY + s(4, sc) + fmTag.getAscent(), tagLs, ACCENT);
    }

    static void drawPlayButton(Graphics2D g, int x, int y, int w, int h, float sc) {
        Composite old = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.10f));
        fillRR(g, x - s(6, sc), y - s(6, sc), w + s(12, sc), h + s(12, sc), s(18, sc), ACCENT);
        g.setComposite(old);

        fillRR(g, x, y, w, h, s(12, sc), ACCENT);

        int iconCx = x + s(20, sc) + s(18, sc);
        int iconCy = y + h / 2;
        drawPlayIcon(g, iconCx, iconCy, s(22, sc), BG_DEEP);

        int textX = x + s(20, sc) + s(36, sc) + s(16, sc);
        g.setFont(f700.deriveFont((float)s(15, sc)));
        FontMetrics fmT = g.getFontMetrics();
        g.setFont(f400.deriveFont((float)s(11, sc)));
        FontMetrics fmD = g.getFontMetrics();
        int blockH = fmT.getHeight() + s(2, sc) + fmD.getHeight();
        int textBlockY = y + (h - blockH) / 2;

        g.setFont(f700.deriveFont((float)s(15, sc)));
        g.setColor(BG_DEEP);
        drawSpaced(g, "\u0418\u0413\u0420\u0410\u0422\u042C", textX, textBlockY + fmT.getAscent(), s(1, sc), BG_DEEP);

        g.setFont(f400.deriveFont((float)s(11, sc)));
        g.setColor(new Color(11, 15, 12, 153));
        g.drawString("\u041F\u043E\u0434\u043A\u043B\u044E\u0447\u0435\u043D\u0438\u0435 \u043A \u0441\u0435\u0440\u0432\u0435\u0440\u0443", textX,
                textBlockY + fmT.getHeight() + s(2, sc) + fmD.getAscent());
    }

    static void drawSingleButton(Graphics2D g, int x, int y, int w, int h, float sc) {
        fillRR(g, x, y, w, h, s(10, sc), LINE);
        fillRR(g, x + 1, y + 1, w - 2, h - 2, s(9, sc), BTN_SEC_BG);

        int iconCx = x + s(20, sc) + s(18, sc);
        int iconCy = y + h / 2;
        drawCheckIcon(g, iconCx, iconCy, s(22, sc), ACCENT);

        int textX = x + s(20, sc) + s(36, sc) + s(16, sc);
        g.setFont(f700.deriveFont((float)s(15, sc)));
        FontMetrics fmT = g.getFontMetrics();
        g.setFont(f400.deriveFont((float)s(11, sc)));
        FontMetrics fmD = g.getFontMetrics();
        int blockH = fmT.getHeight() + s(2, sc) + fmD.getHeight();
        int textBlockY = y + (h - blockH) / 2;

        g.setFont(f700.deriveFont((float)s(15, sc)));
        g.setColor(TEXT_MAIN);
        drawSpaced(g, "\u041E\u0414\u0418\u041D\u041E\u0427\u041D\u042B\u0419 \u041C\u0418\u0420", textX, textBlockY + fmT.getAscent(), s(1, sc), TEXT_MAIN);

        g.setFont(f400.deriveFont((float)s(11, sc)));
        g.setColor(TEXT_MUTED);
        g.drawString("\u041E\u0434\u0438\u043D\u043E\u0447\u043D\u0430\u044F \u0438\u0433\u0440\u0430", textX,
                textBlockY + fmT.getHeight() + s(2, sc) + fmD.getAscent());
    }

    static void drawSmallButtons(Graphics2D g, int menuX, int y, int menuW, int h, float sc) {
        String[] labels = {"\u041D\u0410\u0421\u0422\u0420\u041E\u0419\u041A\u0418", "\u041E \u0421\u0415\u0420\u0412\u0415\u0420\u0415", "\u041C\u0410\u0413\u0410\u0417\u0418\u041D", "\u0412\u042B\u0425\u041E\u0414"};
        int gap = s(8, sc);
        int bw = (menuW - gap * (labels.length - 1)) / labels.length;

        for (int i = 0; i < labels.length; i++) {
            int bx = menuX + i * (bw + gap);

            fillRR(g, bx, y, bw, h, s(10, sc), LINE);
            fillRR(g, bx + 1, y + 1, bw - 2, h - 2, s(9, sc), BTN_SM_BG);

            g.setFont(f500.deriveFont((float)s(11, sc)));
            FontMetrics fm = g.getFontMetrics();
            int iconS = s(14, sc);
            int gapIC = s(8, sc);
            int textW = (int) spacedWidth(g, labels[i], (int)(0.8f * sc));
            int total = iconS + gapIC + textW;
            int sx = bx + (bw - total) / 2;
            int sy = y + (h - fm.getHeight()) / 2 + fm.getAscent();

            int iconCx = sx + iconS / 2;
            int iconCy = y + h / 2;
            switch (i) {
                case 0 -> drawSlidersIcon(g, iconCx, iconCy, iconS, TEXT_MUTED);
                case 1 -> drawInfoIcon(g, iconCx, iconCy, iconS, TEXT_MUTED);
                case 2 -> drawBagIcon(g, iconCx, iconCy, iconS, TEXT_MUTED);
                case 3 -> drawExitIcon(g, iconCx, iconCy, iconS, TEXT_MUTED);
            }

            drawSpaced(g, labels[i], sx + iconS + gapIC, sy, (int)(0.8f * sc), TEXT_MUTED);
        }
    }

    static void drawStatusPanel(Graphics2D g, int x, int y, int w, int h, float sc) {
        fillRR(g, x, y, w, h, s(10, sc), LINE);
        fillRR(g, x + 1, y + 1, w - 2, h - 2, s(9, sc), PANEL_BG);

        g.setFont(f600.deriveFont((float)s(10, sc)));
        g.setColor(TEXT_MUTED);
        drawSpaced(g, "\u0421\u0422\u0410\u0422\u0423\u0421 \u0421\u0415\u0420\u0412\u0415\u0420\u0410", x + s(18, sc), y + s(18, sc) + g.getFontMetrics().getAscent(), (int)(1.5f * sc), TEXT_MUTED);

        int dy = y + s(18, sc) + s(12, sc) + s(8, sc);
        g.setColor(ACCENT);
        g.fillOval(x + s(18, sc), dy + (s(11, sc) - s(6, sc)) / 2, s(6, sc), s(6, sc));
        g.setFont(f400.deriveFont((float)s(11, sc)));
        g.setColor(ACCENT);
        g.drawString("\u0421\u0435\u0440\u0432\u0435\u0440 \u0440\u0430\u0431\u043E\u0442\u0430\u0435\u0442", x + s(18, sc) + s(6, sc) + s(7, sc), dy + g.getFontMetrics().getAscent());

        int numY = dy + s(20, sc);
        g.setFont(f700.deriveFont((float)s(26, sc)));
        g.setColor(TEXT_MAIN);
        String numStr = "24";
        g.drawString(numStr, x + s(18, sc), numY + g.getFontMetrics().getAscent());
        int numW = g.getFontMetrics().stringWidth(numStr);

        g.setFont(f400.deriveFont((float)s(12, sc)));
        g.setColor(TEXT_MUTED);
        g.drawString("\u0438\u0433\u0440\u043E\u043A\u0430", x + s(18, sc) + numW + s(3, sc), numY + g.getFontMetrics().getAscent());

        int barY = numY + s(26, sc) + s(10, sc);
        int barX = x + s(18, sc);
        int barW = w - s(36, sc);
        fillRR(g, barX, barY, barW, s(3, sc), s(2, sc), LINE);
        fillRR(g, barX, barY, (int)(barW * 0.65f), s(3, sc), s(2, sc), ACCENT);
    }

    static void drawNewsPanel(Graphics2D g, int x, int y, int w, int h, float sc) {
        fillRR(g, x, y, w, h, s(10, sc), LINE);
        fillRR(g, x + 1, y + 1, w - 2, h - 2, s(9, sc), PANEL_BG);

        g.setFont(f600.deriveFont((float)s(10, sc)));
        g.setColor(TEXT_MUTED);
        drawSpaced(g, "\u041D\u041E\u0412\u041E\u0421\u0422\u0418", x + s(18, sc), y + s(18, sc) + g.getFontMetrics().getAscent(), (int)(1.5f * sc), TEXT_MUTED);

        String[][] news = {
            {"04.08.2026", "\u041E\u0431\u043D\u043E\u0432\u043B\u0435\u043D\u0438\u0435 1.2: \u043D\u043E\u0432\u044B\u0435 \u043C\u0435\u0445\u0430\u043D\u0438\u043A\u0438 \u0432\u043E\u0439\u043D\u044B"},
            {"01.08.2026", "\u041E\u043F\u0435\u0440\u0430\u0446\u0438\u044F \u00AB\u041D\u043E\u0432\u044B\u0439 \u0440\u0443\u0431\u0435\u0436\u00BB \u2014 \u0441\u0442\u0430\u0440\u0442 \u0447\u0435\u0440\u0435\u0437 3 \u0434\u043D\u044F"},
            {"28.07.2026", "\u0414\u043E\u043D\u0430\u0442-\u043C\u0430\u0433\u0430\u0437\u0438\u043D \u043F\u0435\u0440\u0435\u0448\u0451\u043B \u043D\u0430 \u043D\u043E\u0432\u0443\u044E \u0441\u0438\u0441\u0442\u0435\u043C\u0443"},
            {"25.07.2026", "\u0421\u0435\u0437\u043E\u043D 01 \u043E\u0444\u0438\u0446\u0438\u0430\u043B\u044C\u043D\u043E \u043E\u0442\u043A\u0440\u044B\u0442"}
        };

        int iy = y + s(18, sc) + s(12, sc);
        for (int i = 0; i < news.length; i++) {
            int itemY = iy + s(8, sc);
            g.setFont(f400.deriveFont((float)s(9, sc)));
            g.setColor(TEXT_DIM);
            g.drawString(news[i][0], x + s(18, sc), itemY + g.getFontMetrics().getAscent());

            g.setFont(f500.deriveFont((float)s(11, sc)));
            FontMetrics fmT = g.getFontMetrics();
            int maxW = w - s(36, sc);
            String title = news[i][1];
            if (fmT.stringWidth(title) > maxW) {
                while (title.length() > 3 && fmT.stringWidth(title + "...") > maxW) title = title.substring(0, title.length() - 1);
                title += "...";
            }
            g.setColor(TEXT_MAIN);
            g.drawString(title, x + s(18, sc), itemY + s(12, sc) + fmT.getAscent());

            if (i < 3) {
                g.setColor(new Color(52, 64, 56, 102));
                g.fillRect(x + s(18, sc), itemY + s(28, sc), w - s(36, sc), 1);
            }
            iy = itemY + s(30, sc);
        }
    }

    static void drawSocialIcon(Graphics2D g, int x, int y, int size, float sc, String type) {
        g.setColor(LINE);
        g.fillOval(x, y, size, size);
        g.setColor(new Color(12, 16, 14, 179));
        g.fillOval(x + 1, y + 1, size - 2, size - 2);

        int iconS = s(18, sc);
        int cx = x + size / 2;
        int cy = y + size / 2;
        switch (type) {
            case "telegram" -> drawTelegramIcon(g, cx, cy, iconS, TEXT_MUTED);
            case "discord"  -> drawDiscordIcon(g, cx, cy, iconS, TEXT_MUTED);
            case "globe"    -> drawGlobeIcon(g, cx, cy, iconS, TEXT_MUTED);
        }
    }

    // Icons
    static void drawPlayIcon(Graphics2D g, int cx, int cy, int size, Color color) {
        float f = size / 24f;
        Path2D p = new Path2D.Float();
        p.moveTo(cx + (8 - 12) * f, cy + (5 - 12) * f);
        p.lineTo(cx + (8 - 12) * f, cy + (19 - 12) * f);
        p.lineTo(cx + (19 - 12) * f, cy + (12 - 12) * f);
        p.closePath();
        g.setColor(color); g.fill(p);
    }

    static void drawCheckIcon(Graphics2D g, int cx, int cy, int size, Color color) {
        float f = size / 24f;
        g.setColor(color);
        g.setStroke(new BasicStroke(Math.max(1.5f, size * 0.12f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D p = new Path2D.Float();
        p.moveTo(cx + (4.83f - 12) * f, cy + (12 - 12) * f);
        p.lineTo(cx + (9 - 12) * f, cy + (16.17f - 12) * f);
        p.lineTo(cx + (19.59f - 12) * f, cy + (5.59f - 12) * f);
        g.draw(p); g.setStroke(new BasicStroke(1));
    }

    static void drawSlidersIcon(Graphics2D g, int cx, int cy, int size, Color color) {
        float f = size / 24f;
        float r = 9 * f;
        g.setColor(color);
        // Teeth
        float toothLen = 3 * f, toothW = 3 * f;
        for (int i = 0; i < 8; i++) {
            double a = Math.toRadians(i * 45);
            float ir = 8 * f;
            int tx = (int)(cx + Math.cos(a) * ir - toothW / 2);
            int ty = (int)(cy + Math.sin(a) * ir - toothLen / 2);
            fillRR(g, tx, ty, toothW, toothLen, 1, color);
        }
        g.fillOval((int)(cx - r), (int)(cy - r), (int)(r * 2), (int)(r * 2));
        float hr = 3.6f * f;
        g.setColor(new Color(12, 16, 14, 166));
        g.fillOval((int)(cx - hr), (int)(cy - hr), (int)(hr * 2), (int)(hr * 2));
    }

    static void drawInfoIcon(Graphics2D g, int cx, int cy, int size, Color color) {
        float f = size / 24f;
        float r = 10 * f;
        g.setColor(color);
        g.setStroke(new BasicStroke(Math.max(1f, size * 0.07f)));
        g.drawOval((int)(cx - r), (int)(cy - r), (int)(r * 2), (int)(r * 2));
        g.setStroke(new BasicStroke(1));
        float dr = 1.5f * f;
        g.fillOval((int)(cx - dr), (int)(cy - 4 * f - dr), (int)(dr * 2), (int)(dr * 2));
        float sw = 2 * f;
        g.fillRect((int)(cx - sw / 2), (int)(cy - 1 * f), (int)sw, (int)(6 * f));
    }

    static void drawBagIcon(Graphics2D g, int cx, int cy, int size, Color color) {
        float f = size / 24f;
        g.setColor(color);
        float bw = 16 * f, bh = 12 * f;
        fillRR(g, cx - bw / 2, cy - bh / 2 + 2 * f, bw, bh, 2 * f, color);
        g.setStroke(new BasicStroke(Math.max(1.5f, 1.5f * f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Arc2D handle = new Arc2D.Float(cx - 5 * f, cy - bh / 2 - 2 * f, 10 * f, 8 * f, 180, 180, Arc2D.OPEN);
        g.draw(handle); g.setStroke(new BasicStroke(1));
        float wr = 2 * f;
        g.fillOval((int)(cx - 5 * f - wr), (int)(cy + bh / 2 + 2 * f - wr), (int)(wr * 2), (int)(wr * 2));
        g.fillOval((int)(cx + 5 * f - wr), (int)(cy + bh / 2 + 2 * f - wr), (int)(wr * 2), (int)(wr * 2));
    }

    static void drawExitIcon(Graphics2D g, int cx, int cy, int size, Color color) {
        float f = size / 24f;
        g.setColor(color);
        float thick = Math.max(1.5f, 1.5f * f);
        int dl = cx - (int)(9 * f), dt = cy - (int)(9 * f);
        int dw = (int)(18 * f), dh = (int)(18 * f);
        g.fillRect(dl, dt, (int)thick, dh);
        g.fillRect(dl, dt, dw, (int)thick);
        g.fillRect(dl, dt + dh - (int)thick, dw, (int)thick);
        g.setStroke(new BasicStroke(thick, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D arrow = new Path2D.Float();
        arrow.moveTo(cx - 4 * f, cy); arrow.lineTo(cx + 3 * f, cy); g.draw(arrow);
        Path2D head = new Path2D.Float();
        head.moveTo(cx + 3 * f, cy - 4 * f); head.lineTo(cx + 7.5f * f, cy); head.lineTo(cx + 3 * f, cy + 4 * f); g.draw(head);
        g.setStroke(new BasicStroke(1));
    }

    static void drawTelegramIcon(Graphics2D g, int cx, int cy, int size, Color color) {
        float f = size / 24f;
        g.setColor(color);
        Path2D p = new Path2D.Float();
        p.moveTo(cx + (4 - 12) * f, cy + (10 - 12) * f);
        p.lineTo(cx + (20 - 12) * f, cy + (4 - 12) * f);
        p.lineTo(cx + (4 - 12) * f, cy + (14 - 12) * f);
        p.closePath(); g.fill(p);
    }

    static void drawDiscordIcon(Graphics2D g, int cx, int cy, int size, Color color) {
        float f = size / 24f;
        g.setColor(color);
        fillRR(g, cx - 9 * f, cy - 7 * f, 18 * f, 14 * f, 6 * f, color);
    }

    static void drawGlobeIcon(Graphics2D g, int cx, int cy, int size, Color color) {
        float f = size / 24f;
        float r = 10 * f;
        g.setColor(color);
        g.setStroke(new BasicStroke(Math.max(1f, 1.2f * f)));
        g.drawOval((int)(cx - r), (int)(cy - r), (int)(r * 2), (int)(r * 2));
        g.draw(new Line2D.Float(cx - r, cy, cx + r, cy));
        g.draw(new Line2D.Float(cx, cy - r, cx, cy + r));
        g.setStroke(new BasicStroke(1));
    }

    // Utils
    static void fillRR(Graphics2D g, float x, float y, float w, float h, float r, Color c) {
        g.setColor(c);
        g.fill(new RoundRectangle2D.Float(x, y, w, h, r, r));
    }

    static void drawClippedRect(Graphics2D g, int x, int y, int w, int h, int cut, Color c) {
        g.setColor(c);
        Path2D p = new Path2D.Float();
        p.moveTo(x + cut, y); p.lineTo(x + w - cut, y); p.lineTo(x + w, y + cut);
        p.lineTo(x + w, y + h - cut); p.lineTo(x + w - cut, y + h); p.lineTo(x + cut, y + h);
        p.lineTo(x, y + h - cut); p.lineTo(x, y + cut); p.closePath(); g.fill(p);
    }

    static void drawSpaced(Graphics2D g, String text, float x, float y, int spacing, Color color) {
        g.setColor(color);
        FontMetrics fm = g.getFontMetrics();
        float cx = x;
        for (int i = 0; i < text.length(); i++) {
            String ch = String.valueOf(text.charAt(i));
            g.drawString(ch, cx, y);
            cx += fm.stringWidth(ch) + spacing;
        }
    }

    static float spacedWidth(Graphics2D g, String text, int spacing) {
        FontMetrics fm = g.getFontMetrics();
        float w = 0;
        for (int i = 0; i < text.length(); i++) {
            if (i > 0) w += spacing;
            w += fm.stringWidth(String.valueOf(text.charAt(i)));
        }
        return w;
    }

    static int s(float cssPx, float sc) { return Math.max(1, Math.round(cssPx * sc)); }
}