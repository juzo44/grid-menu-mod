package ru.gridwarfare.menu;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * GRID Main Menu — pixel-perfect port of the CSS mockup.
 *
 * CSS reference (1920×1080):
 *   padding: 40px   topbar: 60px   right-col: 280px (right:40px, top:50%, translateY(-50%))
 *   menu-w: 440px   PRIMARY: 440×72 r12   SECONDARY: 440×62 r10   SMALL: flex×46 r10
 *   title: 52px/900/8px ls, box-shadow 0 5/7/9px, pad 10px 36px 12px, r8
 *   tag: 11px/600/1.5px ls, pad 4px 16px, r4, margin-top -2px
 *   btn-primary: icon 36×36 mr16px, svg 22×22, title 15px/700/1px ls, desc 11px/400/mt2px
 *   btn-sm: icon 14×14 gap8px, text 11px/500/0.8px ls
 *   social: 38×38 circle, gap 10px, bottom 24px right 40px
 */
public final class GridScreen extends Screen {

    private static final String MAIN_IP = "grid-server.ru";
    private static final String TG_URL  = "https://t.me/gridwarfare";
    private static final String DC_URL  = "https://discord.gg/gridwarfare";
    private static final String WEB_URL = "https://grid-server.ru";

    private static final long NEWS_REFRESH_MS = 60_000L;
    private static final long AUTH_REFRESH_MS = 30_000L;

    private static final int ICON_PLAY    = 1;
    private static final int ICON_CHECK   = 2;
    private static final int ICON_SLIDERS = 3;
    private static final int ICON_INFO    = 4;
    private static final int ICON_BAG     = 5;
    private static final int ICON_EXIT    = 6;

    private JsonObject me;
    private JsonArray  news;
    private long newsFetchedAt;
    private long authFetchedAt;

    /* Кэш лейаута */
    private int p;          // pad (40px @ 1920)
    private int rightColW;  // 280px
    private int menuW;      // 440px
    private int menuX;      // X menu column

    public GridScreen() {
        super(Component.literal("GRID"));
    }

    /* ═══════════════════════════
       INIT
       ═══════════════════════════ */
    @Override
    protected void init() {
        ServerStatusManager.start();

        GridUi.S = Math.min((float)width / 1920f, (float)height / 1080f);
        p = GridUi.pad(width);
        rightColW = GridUi.s(280);
        menuW = GridUi.s(440);
        if (menuW < 100) menuW = width - p * 2 - 20;
        // CSS: .menu-col { flex:1; align-items:center; } — центрируется во ВСЕЙ ширине .main-content
        // .right-col — position:absolute, НЕ влияет на flex-flow
        int contentW = width - p * 2;
        menuX = p + (contentW - menuW) / 2;

        // CSS heights & gaps (exact from mockup)
        var fnt = Minecraft.getInstance().font;
        float titleScale = (52f / 9f) * GridUi.S; // CSS: 52px font, MC ~9px baseline, scales with resolution
        int titleTextH = (int)(fnt.lineHeight * titleScale);  // ~48px
        int titleBoxH = titleTextH + GridUi.s(10) + GridUi.s(12);  // CSS: padding 10px top + 12px bottom = +22px
        int tagInnerH = GridUi.s(4) + fnt.lineHeight + GridUi.s(4);  // CSS: padding 4px, text 11px(~12px MC), = ~20px
        int tagGap = -2;  // CSS: margin-top -2px (overlap!)
        int titleGap = GridUi.s(48);  // CSS: .title-block margin-bottom 48px
        int playH = GridUi.s(72);  // CSS: .btn-primary-lg height 72px
        int singleH = GridUi.s(62);  // CSS: .btn-secondary-lg height 62px
        int smallH = GridUi.s(46);  // CSS: .btn-sm height 46px
        int bigGap = GridUi.s(10);  // CSS: .menu-buttons gap 10px
        int smallRowGap = GridUi.s(10); // CSS: .bottom-row margin-top 10px

        // Total content height (centered vertically in .main-content which is below topbar)
        // CSS: .main-content starts at 60px (topbar height)
        // tagGap = -2 means tag overlaps titleBox by 2px, so effective height = titleBoxH + tagInnerH - 2
        int totalH = (titleBoxH + tagInnerH - 2) + titleGap
                   + playH + bigGap + singleH + smallRowGap + smallH;
        // startY: centered in the area below the 60px topbar
        int contentAreaH = height - GridUi.s(60);
        int startY = GridUi.s(60) + (contentAreaH - totalH) / 2;

        int playY   = startY + titleBoxH + tagInnerH - 2 + titleGap;
        int singleY = playY + playH + bigGap;
        int rowY    = singleY + singleH + smallRowGap;

        // ═══ PRIMARY: ИГРАТЬ (440×72, r12, accent) ═══
        addRenderableWidget(new MenuButton(
                menuX, playY, menuW, playH,
                ICON_PLAY, "ИГРАТЬ", "Подключение к серверу",
                MenuButton.Type.PRIMARY, b -> connectToServer()));

        // ═══ SECONDARY: ОДИНОЧНЫЙ МИР (440×62, r10, dark+border) ═══
        addRenderableWidget(new MenuButton(
                menuX, singleY, menuW, singleH,
                ICON_CHECK, "ОДИНОЧНЫЙ МИР", "Одиночная игра",
                MenuButton.Type.SECONDARY, b -> minecraft.setScreen(new SelectWorldScreen(this))));

        // ═══ BOTTOM ROW: мелкие кнопки (flex:1, 46px, r10, gap 8px) ═══
        List<MenuButton> small = new ArrayList<>();
        small.add(new MenuButton(0, rowY, 0, smallH, ICON_SLIDERS, "НАСТРОЙКИ", null,
                MenuButton.Type.SMALL, b -> minecraft.setScreen(new OptionsScreen(this, minecraft.options))));
        small.add(new MenuButton(0, rowY, 0, smallH, ICON_INFO, "О СЕРВЕРЕ", null,
                MenuButton.Type.SMALL, b -> minecraft.setScreen(new ServerInfoScreen(this))));
        if (isShopAvailable()) {
            small.add(new MenuButton(0, rowY, 0, smallH, ICON_BAG, "МАГАЗИН", null,
                    MenuButton.Type.SMALL, b -> openShop()));
        }
        small.add(new MenuButton(0, rowY, 0, smallH, ICON_EXIT, "ВЫХОД", null,
                MenuButton.Type.SMALL_EXIT, b -> minecraft.stop()));

        int smallGap = GridUi.s(8);
        int sw = (menuW - (small.size() - 1) * smallGap) / small.size();
        int sx = menuX;
        for (MenuButton btn : small) {
            btn.setX(sx);
            btn.setWidth(sw);
            addRenderableWidget(btn);
            sx += sw + smallGap;
        }

        // ═══ SOCIAL ICONS (CSS: bottom:24px, right:40px, 38×38, gap 10px) ═══
        // CSS: .social-icon { r50%; bg rgba(12,16,14,0.7); border 1px line; }
        // CSS: .social-icon svg { 18×18; fill text-muted; } hover: fill accent
        int socialSize = GridUi.s(38);
        int socialGap  = GridUi.s(10);
        int socialY    = height - GridUi.s(24) - socialSize;
        int socialRight = width - GridUi.s(40); // CSS: right: 40px
        addRenderableWidget(new SocialIconButton(
                socialRight - socialSize * 3 - socialGap * 2, socialY, socialSize,
                1, b -> openLink(TG_URL)));
        addRenderableWidget(new SocialIconButton(
                socialRight - socialSize * 2 - socialGap, socialY, socialSize,
                2, b -> openLink(DC_URL)));
        addRenderableWidget(new SocialIconButton(
                socialRight - socialSize, socialY, socialSize,
                3, b -> openLink(WEB_URL)));

        loadAuth();
        loadNews();
    }

    /* ═══════════════════════════
       TICK / LIFECYCLE
       ═══════════════════════════ */
    @Override
    public void tick() {
        ServerStatusManager.tick();
        long now = Util.getMillis();
        if (now - newsFetchedAt > NEWS_REFRESH_MS) loadNews();
        if (now - authFetchedAt > AUTH_REFRESH_MS) loadAuth();
    }

    @Override
    public void removed() { ServerStatusManager.stop(); }

    /* ═══════════════════════════
       API
       ═══════════════════════════ */
    private void loadAuth() {
        authFetchedAt = Util.getMillis();
        new Thread(() -> {
            JsonObject result = GridApiClient.me();
            Minecraft.getInstance().execute(() -> me = result);
        }, "GRID-auth").start();
    }

    private void loadNews() {
        newsFetchedAt = Util.getMillis();
        new Thread(() -> {
            JsonArray result = GridApiClient.news();
            Minecraft.getInstance().execute(() -> {
                news = result;
                newsFetchedAt = Util.getMillis();
            });
        }, "GRID-news").start();
    }

    /* ═══════════════════════════
       РЕНДЕР (фон → топбар → заголовок → правая колонка → версия → виджеты)
       ═══════════════════════════ */
    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        GridUi.S = Math.min((float)width / 1920f, (float)height / 1080f);
        GridUi.background(g, width, height);
        renderTopbar(g);
        renderTitle(g);
        renderRightColumn(g);
        renderVersion(g);
        // debug overlay removed

        super.render(g, mx, my, pt);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {}

    @Override
    public boolean isPauseScreen() { return false; }

    /* ═══════════════════════════
       TOP BAR (CSS: height 60px, padding 20px 40px, flex, space-between, align-items center)
       ═══════════════════════════ */
    private void renderTopbar(GuiGraphics g) {
        // CSS: .top-bar padding: 20px 40px → brand at (40, 20), auth card right-aligned
        int topPad = GridUi.s(20);

        // Brand mark 32×32 (CSS: .brand-mark width 32px height 32px)
        GridUi.brandMark(g, p, topPad, GridUi.s(32));
        // Brand text «GRID» (CSS: gap 12px, font-size 13px, weight 700, letter-spacing 3px, uppercase)
        drawSpaced(g, font, GridUi.styled("GRID"), p + GridUi.s(32) + GridUi.s(12), topPad + GridUi.s(3), GridUi.s(3), GridUi.TEXT_MAIN);

        // Auth card (top-right)
        renderAuthCard(g);
    }

    /* ═══════════════════════════
       AUTH CARD
       CSS: .auth-card { gap:14px; bg-panel; border:1px solid line; r10; padding:7px 14px; }
            .auth-nick { 12px/600; color text; gap:7px; }
            .auth-rank { 10px/600; accent color; accent-dim bg; padding:1px 5px; r3; }
            .auth-balance { 10px; color text-muted; } .auth-balance span { accent; 600; }
       ═══════════════════════════ */
    private void renderAuthCard(GuiGraphics g) {
        int right = width - p; // CSS: right: 40px (inside .top-bar padding 40px)
        boolean authed = me != null;
        String nick = "ГОСТЬ";
        String rank = "";
        if (authed) {
            if (me.has("username")) nick = me.get("username").getAsString().toUpperCase();
            if (me.has("donate")) rank = me.get("donate").getAsString().toUpperCase();
            if (rank.isEmpty() && me.has("rank")) rank = me.get("rank").getAsString().toUpperCase();
        }

        var fnt = Minecraft.getInstance().font;
        int nickW = fnt.width(GridUi.styled(nick));
        int rankW = rank.isEmpty() ? 0 : fnt.width(GridUi.styled(rank));

        // Line 1: nick + [rank badge]  (padding 14px each side)
        int line1W = 14 + nickW + (rank.isEmpty() ? 0 : 7 + rankW + 10) + 14;

        String balLabel = authed ? "Баланс: " : "Авторизация через лаунчер";
        String balValue = authed ? formatBalance(balance()) + " \u20BD" : "";
        int line2W = 14 + fnt.width(GridUi.styled(balLabel))
                      + (balValue.isEmpty() ? 0 : 4 + fnt.width(GridUi.styled(balValue))) + 14;

        int cardW = Math.max(line1W, line2W);
        int cardH = authed ? GridUi.s(46) : GridUi.s(32);
        // CSS: centered vertically in 60px topbar
        int cx = right - cardW;
        int cy = (GridUi.s(60) - cardH) / 2;

        // CSS: .auth-card bg-panel border:1px solid line r10
        GridUi.panel(g, cx, cy, cardW, cardH, 10);

        int tx = cx + 14;
        // CSS: .auth-info gap:2px → line1 at cy+7+lineHeight/2, line2 at +14
        int ty = cy + 7;

        // Nick (CSS: .auth-nick font-size 12px font-weight 600 color text)
        g.drawString(fnt, GridUi.styled(nick), tx, ty, GridUi.TEXT_MAIN, false);

        // Rank badge (CSS: .auth-rank 10px/600 accent color, bg accent-dim, padding 1px 5px, r3)
        if (!rank.isEmpty()) {
            int rankBgX = tx + nickW + 7;
            int rankBgW = rankW + 10; // padding 5px × 2
            int rankBgH = fnt.lineHeight + 2; // padding 1px × 2
            // Фон бейджа (accent-dim = rgba(104,194,132,0.15))
            GridUi.roundedRect(g, rankBgX, ty - 1, rankBgW, rankBgH, 3, GridUi.ACCENT_DIM);
            // Текст ранга (accent color)
            g.drawString(fnt, GridUi.styled(rank), rankBgX + 5, ty, GridUi.ACCENT, false);
        }

        if (authed) {
            // Balance line (CSS: .auth-balance 10px, text-muted, then accent span)
            int balLabelW = fnt.width(GridUi.styled(balLabel));
            g.drawString(fnt, GridUi.styled(balLabel), tx, ty + 14, GridUi.TEXT_MUTED, false);
            g.drawString(fnt, GridUi.styled(balValue), tx + balLabelW + 4, ty + 14, GridUi.ACCENT, false);
        } else {
            g.drawString(fnt, GridUi.styled(balLabel), tx, ty, GridUi.TEXT_MUTED, false);
        }
    }

    private long balance() {
        return me != null && me.has("balance") ? me.get("balance").getAsLong() : 0L;
    }

    private static String formatBalance(long value) {
        String digits = String.valueOf(Math.abs(value));
        StringBuilder sb = new StringBuilder(digits);
        for (int i = sb.length() - 3; i > 0; i -= 3) sb.insert(i, ' ');
        if (value < 0) sb.insert(0, '-');
        return sb.toString();
    }

    /* ═══════════════════════════
       3D ЗАГОЛОВОК «GRID» + ТЕГ
       CSS: .title-main { bg accent; box-shadow: 0 5px 0 accent-dark, 0 7px 0 accent-darker, 0 9px 20px rgba(0,0,0,0.5);
             padding: 10px 36px 12px 36px; border-radius: 8px; }
            .title-main-text { 52px; 900; bg-deep; uppercase; letter-spacing 8px; line-height 1; }
            .title-tag { bg accent-dim; border:1px solid accent-border; padding:4px 16px; r4; margin-top:-2px; }
            .title-tag-text { 11px; 600; accent; uppercase; letter-spacing 1.5px; }
       ═══════════════════════════ */
    private void renderTitle(GuiGraphics g) {
        var fnt = Minecraft.getInstance().font;
        int cx = menuX + menuW / 2;
        float titleScale = (52f / 9f) * GridUi.S; // CSS: 52px font, MC ~9px baseline, scales with resolution
        int letterSpacing = GridUi.s(8); // CSS: letter-spacing 8px

        // Измеряем «GRID» по буквам для ширины бокса
        String titleText = "GRID";
        int[] charW = new int[titleText.length()];
        int totalTextW = 0;
        for (int i = 0; i < titleText.length(); i++) {
            charW[i] = (int) (fnt.width(GridUi.styled(String.valueOf(titleText.charAt(i)))) * titleScale);
            totalTextW += charW[i];
            if (i > 0) totalTextW += letterSpacing;
        }

        // CSS: padding 0 36px, box height = textH + 10(top) + 12(bottom)
        int padX = GridUi.s(36);
        int titleTextH = (int)(fnt.lineHeight * titleScale);
        int titleBoxH = titleTextH + GridUi.s(10) + GridUi.s(12);
        int boxW = totalTextW + padX * 2;

        // Пересчитаем startY (как в init)
        int tagInnerH = GridUi.s(4) + fnt.lineHeight + GridUi.s(4);
        int titleGap = GridUi.s(48);
        int playH = GridUi.s(72), singleH = GridUi.s(62), smallH = GridUi.s(46);
        int bigGap = GridUi.s(10), smallRowGap = GridUi.s(10);
        int totalH = (titleBoxH + tagInnerH - 2) + titleGap
                   + playH + bigGap + singleH + smallRowGap + smallH;
        int contentAreaH = height - GridUi.s(60);
        int startY = GridUi.s(60) + (contentAreaH - totalH) / 2;
        int baseY = startY;
        int boxX = cx - boxW / 2;

        // CSS box-shadow: 0 9px 20px rgba(0,0,0,0.5) — размытая тень (3 слоя)
        GridUi.roundedRect(g, boxX - GridUi.s(4), baseY + GridUi.s(13), boxW + GridUi.s(8), titleBoxH + GridUi.s(4), 12, 0x26000000);
        GridUi.roundedRect(g, boxX - GridUi.s(2), baseY + GridUi.s(10), boxW + GridUi.s(4), titleBoxH + GridUi.s(2), 10, 0x40000000);
        GridUi.roundedRect(g, boxX, baseY + GridUi.s(9), boxW, titleBoxH, 8, 0x80000000);

        // CSS box-shadow: 0 7px 0 accent-darker, 0 5px 0 accent-dark
        GridUi.roundedRect(g, boxX, baseY + GridUi.s(7), boxW, titleBoxH, 8, GridUi.ACCENT_DARKER);
        GridUi.roundedRect(g, boxX, baseY + GridUi.s(5), boxW, titleBoxH, 8, GridUi.ACCENT_DARK);
        // Основной бокс
        GridUi.roundedRect(g, boxX, baseY, boxW, titleBoxH, 8, GridUi.ACCENT);

        // Текст «GRID» по буквам с letter-spacing 8px
        int charX = boxX + padX;
        int charCY = baseY + GridUi.s(10) + titleTextH / 2; // 10px = CSS padding-top
        for (int i = 0; i < titleText.length(); i++) {
            g.pose().pushPose();
            g.pose().translate((float) (charX + charW[i] / 2), (float) (charCY), 0.0F);
            g.pose().scale(titleScale, titleScale, 1.0F);
            g.drawCenteredString(fnt, GridUi.styled(String.valueOf(titleText.charAt(i))), 0, -4, GridUi.BG_DEEP);
            g.pose().popPose();
            charX += charW[i] + letterSpacing;
        }

        // CSS: .title-tag-text { 11px; 600; accent; UPPERCASE; letter-spacing 1.5px; }
        String tag = "ВОЕННО-ПОЛИТИЧЕСКИЙ СЕРВЕР";
        int tagSpacing = GridUi.s(1); // ~1.5px CSS
        int tagTextW = spacedWidth(fnt, GridUi.styled(tag), tagSpacing);
        int tagPadX = GridUi.s(16);
        int tagPadY = GridUi.s(4);
        int tagW = tagTextW + tagPadX * 2;
        int tagH = tagInnerH;
        // CSS: margin-top -2px → overlap with title box
        int tagY = baseY + titleBoxH - GridUi.s(2);
        int tagX = cx - tagW / 2;

        // CSS: border 1px solid accent-border, bg accent-dim, r4
        GridUi.roundedRect(g, tagX, tagY, tagW, tagH, 4, GridUi.ACCENT_BORDER);
        GridUi.roundedRect(g, tagX + 1, tagY + 1, tagW - 2, tagH - 2, 3, GridUi.ACCENT_DIM);

        // Текст тега с letter-spacing (по центру)
        int tagTextX = tagX + tagPadX + (tagW - tagPadX * 2 - tagTextW) / 2;
        int tagTextY = tagY + tagPadY;
        drawSpaced(g, fnt, GridUi.styled(tag), tagTextX, tagTextY, tagSpacing, GridUi.ACCENT);
    }

    /* ═══════════════════════════
       ПРАВАЯ КОЛОНКА
       CSS: .right-col { position: absolute; right: 40px; top: 50%; transform: translateY(-50%); width: 280px; gap: 10px; }
       Важно: top: 50% и translateY(-50%) означают вертикальный центр
       родителя (.main-content), который начинается после topbar (60px).
       ═══════════════════════════ */
    private void renderRightColumn(GuiGraphics g) {
        int rw = rightColW;
        int rx = width - p - rw; // CSS: right: 40px

        int newsCount = news == null ? 0 : Math.min(4, news.size());
        int statusH = GridUi.s(100);
        int newsH = GridUi.s(30) + (newsCount == 0 ? GridUi.s(24) : newsCount * GridUi.s(38));
        int gap = GridUi.s(10);
        int total = statusH + gap + newsH;

        // CSS: top: 50%; transform: translateY(-50%) — центр .main-content
        // .main-content = весь экран минус topbar(60px)
        int contentAreaH = height - GridUi.s(60);
        int ry = GridUi.s(60) + (contentAreaH - total) / 2;

        renderStatusPanel(g, rx, ry, rw, statusH);
        renderNewsPanel(g, rx, ry + statusH + gap, rw, newsH);
    }

    /**
     * Статус сервера
     * CSS: .panel { padding: 18px; } .panel-title { 10px; 600; uppercase; ls 1.5px; mb 12px; }
     *      .status-row { gap: 7px; mb 8px; } .status-dot { 6×6; r50%; accent; }
     *      .status-label { 11px; text-muted; }
     *      .status-value { 26px; 800; text; } span { 12px; 400; text-muted; ml 3px; }
     *      .status-bar-track { h 3px; line bg; r2; mt 10px; }
     *      .status-bar-fill { 65%; h 100%; accent; r2; }
     */
    private void renderStatusPanel(GuiGraphics g, int x, int y, int w, int h) {
        GridUi.card(g, x, y, w, h);
        var fnt = Minecraft.getInstance().font;

        // CSS: .panel-title font-size 10px, weight 600, uppercase, spacing 1.5px, margin-bottom 12px
        drawSpaced(g, fnt, GridUi.styled("СТАТУС СЕРВЕРА"), x + GridUi.s(18), y + GridUi.s(18), 1, GridUi.TEXT_MUTED);

        int state = ServerStatusManager.getState();
        boolean online = state == ServerStatusManager.ONLINE;
        int dotColor = online ? GridUi.ACCENT : 0xFF616A64;
        String label = online ? "Сервер работает" :
                state == ServerStatusManager.OFFLINE ? "Сервер недоступен" : "Проверка...";
        int labelColor = online ? GridUi.ACCENT :
                state == ServerStatusManager.OFFLINE ? 0xFFE06666 : 0xFF9AA5A0;

        // CSS: .status-row gap 7px, margin-bottom 8px
        int dx = x + GridUi.s(18);
        int dy = y + GridUi.s(18) + GridUi.s(12) + GridUi.s(8); // panel-title(18) + mb(12) + some alignment
        // Dot 6×6 (CSS: .status-dot width 6px height 6px border-radius 50%)
        // В MC рисуем квадрат 6×6 — на таком маленьком размере круг и квадрат неразличимы
        int dotS = GridUi.s(6);
        g.fill(dx, dy + 1, dx + dotS, dy + 1 + dotS, dotColor);
        // Label (CSS: .status-label font-size 11px, gap 7px after dot)
        g.drawString(fnt, GridUi.styled(label), dx + GridUi.s(6) + GridUi.s(7), dy, labelColor, false);

        if (online) {
            int value = ServerStatusManager.getOnline();
            // CSS: .status-value font-size 26px, weight 800
            String numStr = String.valueOf(value);
            int numBaseW = fnt.width(GridUi.styled(numStr));
            float numScale = GridUi.S * 2.0F;
            int numScaledW = (int) (numBaseW * numScale);
            int numY = dy + 8 + 12; // after status-row mb 8px
            g.pose().pushPose();
            g.pose().translate((float) dx, (float) numY, 0.0F);
            g.pose().scale(numScale, numScale, 1.0F);
            g.drawString(fnt, GridUi.styled(numStr), 0, 0, GridUi.TEXT_MAIN, false);
            g.pose().popPose();
            // CSS: span { font-size 12px; margin-left 3px; }
            g.drawString(fnt, GridUi.styled("игрока"), dx + numScaledW + GridUi.s(3), numY + GridUi.s(4), GridUi.TEXT_MUTED, false);

            // CSS: .status-bar-track height 3px, margin-top 10px
            int barY = numY + (int)(fnt.lineHeight * numScale) + GridUi.s(10);
            int barX = dx;
            int barW = x + w - GridUi.s(18) - barX;
            if (barW > 4) {
                g.fill(barX, barY, barX + barW, barY + GridUi.s(3), GridUi.LINE_COLOR);
                int max = ServerStatusManager.getMax();
                if (max > 0) {
                    int fillW = Math.max(3, (int)(barW * Math.min(1f, (float)value / max)));
                    g.fill(barX, barY, barX + fillW, barY + GridUi.s(3), GridUi.ACCENT);
                }
            }
        } else {
            g.drawString(fnt, GridUi.styled("—"), dx, dy + 20, GridUi.TEXT_MAIN, false);
        }
    }

    /**
     * Новости
     * CSS: .panel { padding: 18px; }
     *      .panel-title { 10px; 600; uppercase; ls 1.5px; mb 12px; }
     *      .news-item { padding: 8px 0; border-bottom: 1px rgba(52,64,56,0.4); }
     *      .news-date { 9px; text-dim; uppercase; ls 0.5px; }
     *      .news-title { 11px; 500; text; mt 2px; lh 1.35; }
     */
    private void renderNewsPanel(GuiGraphics g, int x, int y, int w, int h) {
        GridUi.card(g, x, y, w, h);
        var fnt = Minecraft.getInstance().font;

        // CSS: .panel-title
        drawSpaced(g, fnt, GridUi.styled("НОВОСТИ"), x + GridUi.s(18), y + GridUi.s(18), 1, GridUi.TEXT_MUTED);

        if (news == null) {
            g.drawString(fnt, GridUi.styled("Загрузка..."), x + GridUi.s(18), y + GridUi.s(38), GridUi.TEXT_DIM, false);
            return;
        }

        int iy = y + GridUi.s(18) + GridUi.s(12); // after panel title + margin-bottom 12px
        int shown = 0;
        for (JsonElement element : news) {
            if (shown >= 4) break;
            JsonObject item = element.getAsJsonObject();
            String title = item.has("title") ? item.get("title").getAsString() : "";
            String date = item.has("publishedAt") ? item.get("publishedAt").getAsString() : "";
            // CSS: .news-date format DD.MM.YYYY
            if (date.length() >= 10) {
                String dd = date.substring(8, 10);
                String mm = date.substring(5, 7);
                String yyyy = date.substring(0, 4);
                date = dd + "." + mm + "." + yyyy;
            }

            // CSS: .news-item padding 8px 0
            int itemY = iy + GridUi.s(8);
            // Дата (CSS: .news-date font-size 9px, text-dim, uppercase, ls 0.5px)
            g.drawString(fnt, GridUi.styled(date), x + GridUi.s(18), itemY, GridUi.TEXT_DIM, false);
            // Заголовок (CSS: .news-title font-size 11px, weight 500, text, margin-top 2px)
            String clipped = fnt.plainSubstrByWidth(title, w - 36);
            g.drawString(fnt, GridUi.styled(clipped), x + GridUi.s(18), itemY + GridUi.s(12), GridUi.TEXT_MAIN, false);
            // Разделитель (CSS: border-bottom 1px rgba(52,64,56,0.4))
            if (shown < 3) {
                g.fill(x + GridUi.s(18), itemY + GridUi.s(28), x + w - GridUi.s(18), itemY + GridUi.s(29), 0x66344038);
            }
            iy = itemY + GridUi.s(30);
            shown++;
        }
        if (shown == 0) {
            g.drawString(fnt, GridUi.styled("Новостей пока нет"), x + GridUi.s(18), y + GridUi.s(38), GridUi.TEXT_DIM, false);
        }
    }

    /* ═══════════════════════════
       VERSION (CSS: bottom 10px, left 40px, font-size 10px, color text-dim, ls 0.5px)
       ═══════════════════════════ */
    private void renderVersion(GuiGraphics g) {
        // CSS: bottom 10px → baseline ~10px от нижнего края
        g.drawString(font, GridUi.styled("Minecraft 1.21.1 \u00B7 NeoForge \u00B7 GRID v0.1.0"),
                p, height - GridUi.s(12), GridUi.TEXT_DIM, false);
    }

    /* ═══════════════════════════
       LETTER-SPACING УТИЛИТЫ
       ═══════════════════════════ */
    private static void drawSpaced(GuiGraphics g, net.minecraft.client.gui.Font fnt,
                                   Component text, int x, int y, int spacing, int color) {
        String str = text.getString();
        for (int i = 0; i < str.length(); i++) {
            if (i > 0) x += spacing;
            g.drawString(fnt, Component.literal(String.valueOf(str.charAt(i))).setStyle(text.getStyle()),
                    x, y, color, false);
            x += fnt.width(Component.literal(String.valueOf(str.charAt(i))).setStyle(text.getStyle()));
        }
    }

    private static int spacedWidth(net.minecraft.client.gui.Font fnt, Component text, int spacing) {
        String str = text.getString();
        int w = 0;
        for (int i = 0; i < str.length(); i++) {
            if (i > 0) w += spacing;
            w += fnt.width(Component.literal(String.valueOf(str.charAt(i))).setStyle(text.getStyle()));
        }
        return w;
    }

    /* ═══════════════════════════
       ДЕЙСТВИЯ
       ═══════════════════════════ */
    private void connectToServer() {
        try {
            ServerData data = new ServerData("GRID", MAIN_IP, ServerData.Type.OTHER);
            ConnectScreen.startConnecting(this, minecraft, ServerAddress.parseString(MAIN_IP), data, false, null);
        } catch (Throwable e) { minecraft.setScreen(new TitleScreen()); }
    }

    private void openLink(String url) {
        try { Util.getPlatform().openUri(url); } catch (Throwable ignored) {}
    }

    private static boolean isShopAvailable() {
        try { Class.forName("ru.gridwarfare.shop.GridShopScreen"); return true; }
        catch (Throwable e) { return false; }
    }

    private void openShop() {
        try {
            Class<?> cls = Class.forName("ru.gridwarfare.shop.GridShopScreen");
            Object inst = cls.getDeclaredConstructor().newInstance();
            if (inst instanceof Screen s) minecraft.setScreen(s);
        } catch (Throwable e) { minecraft.setScreen(new ServerInfoScreen(this)); }
    }

    /* ═══════════════════════════
       СОЦ. КНОПКА (текстурная, 38×38, монохромная)
       CSS: .social-icon { 38×38; r50%; bg rgba(12,16,14,0.7); border 1px line; }
            .social-icon svg { 18×18; fill text-muted; } hover: fill accent
       ═══════════════════════════ */
    private static final class SocialIconButton extends Button {
        private final int iconType; // 1=Telegram, 2=Discord, 3=Globe

        SocialIconButton(int x, int y, int size, int iconType, OnPress press) {
            super(x, y, size, size, Component.empty(), press, DEFAULT_NARRATION);
            this.iconType = iconType;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            boolean hover = isHovered() && active;
            int x = getX(), y = getY(), s = width;
            int r = s / 2;
            int bg = hover ? GridUi.ACCENT_DIM : 0xB30C100E;
            int border = hover ? GridUi.ACCENT_BORDER : GridUi.LINE_COLOR;
            GridUi.roundedRect(g, x, y, s, s, r, border);
            GridUi.roundedRect(g, x + 1, y + 1, s - 2, s - 2, Math.max(1, r - 1), bg);

            // Иконка из текстуры (36px для 18px display, 2x)
            ResourceLocation tex = switch (iconType) {
                case 1 -> hover ? GridUi.SOCIAL_TG_H : GridUi.SOCIAL_TG;
                case 2 -> hover ? GridUi.SOCIAL_DC_H : GridUi.SOCIAL_DC;
                default -> hover ? GridUi.SOCIAL_GL_H : GridUi.SOCIAL_GL;
            };
            int iconDisplaySize = GridUi.s(18);
            int ix = x + (s - iconDisplaySize) / 2;
            int iy = y + (s - iconDisplaySize) / 2;
            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
            g.blit(tex, ix, iy, 0, 0, iconDisplaySize, iconDisplaySize, 36, 36);
        }
    }

        /* ═══════════════════════════
       КНОПКА МЕНЮ
       CSS: btn-primary-lg: 440×72, r12, accent bg, no border, glow 0 0 20px rgba(104,194,132,0.10)
           padding: 0 20px; .btn-icon 36×36 mr16px svg22×22
           .btn-title 15px 700 uppercase ls1px, .btn-desc 11px 400 mt2px
       CSS: btn-secondary-lg: 440×62, r10, rgba(12,16,14,0.75), border 1px line
           padding: 0 20px; .btn-icon 36×36 mr16px svg22×22
       CSS: btn-sm: flex:1 × 46, r10, rgba(12,16,14,0.65), border 1px line
           icon 14×14 gap8px, text 11px 500 uppercase ls0.8px, padding 0 8px
       ═══════════════════════════ */
    private static final class MenuButton extends Button {

        enum Type { PRIMARY, SECONDARY, SMALL, SMALL_EXIT }

        private final Type type;
        private final int icon;
        private final String desc;

        MenuButton(int x, int y, int w, int h, int icon, String title, String desc, Type type, OnPress press) {
            super(x, y, w, h, GridUi.styled(title), press, DEFAULT_NARRATION);
            this.icon = icon;
            this.desc = desc;
            this.type = type;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            boolean hover = isHovered() && active;
            int x = getX(), y = getY(), w = width, h = height;
            var fnt = Minecraft.getInstance().font;

            int bg, border, text, sub, iconColor;
            switch (type) {
                case PRIMARY -> {
                    bg = hover ? GridUi.ACCENT_HOVER : GridUi.ACCENT;
                    border = GridUi.ACCENT;
                    text = GridUi.BG_DEEP;
                    sub = 0x990B0F0C; // rgba(11,15,12,0.6)
                    iconColor = GridUi.BG_DEEP;
                }
                case SECONDARY -> {
                    bg = hover ? GridUi.BTN_SEC_HOVER : GridUi.BTN_SEC_BG;
                    border = hover ? 0x4068C284 : GridUi.LINE_COLOR;
                    text = GridUi.TEXT_MAIN;
                    sub = GridUi.TEXT_MUTED;
                    iconColor = GridUi.ACCENT;
                }
                case SMALL_EXIT -> {
                    bg = hover ? GridUi.BTN_SM_HOVER : GridUi.BTN_SM_BG;
                    border = hover ? 0x4DDC5050 : GridUi.LINE_COLOR;
                    text = hover ? GridUi.TEXT_MAIN : GridUi.TEXT_MUTED;
                    sub = GridUi.TEXT_MUTED;
                    iconColor = hover ? 0xFFE05555 : GridUi.TEXT_MUTED;
                }
                default -> {
                    bg = hover ? GridUi.BTN_SM_HOVER : GridUi.BTN_SM_BG;
                    border = hover ? 0x4068C284 : GridUi.LINE_COLOR;
                    text = hover ? GridUi.TEXT_MAIN : GridUi.TEXT_MUTED;
                    sub = GridUi.TEXT_MUTED;
                    iconColor = hover ? GridUi.ACCENT : GridUi.TEXT_MUTED;
                }
            }

            int radius = type == Type.PRIMARY ? 12 : 10;

            // Фон + бордер
            if (type == Type.PRIMARY) {
                // CSS: box-shadow 0 0 20px rgba(104,194,132,0.10), hover 0 0 28px rgba(0.20)
                int glow = hover ? 0x3368C284 : 0x1A68C284;
                GridUi.roundedRect(g, x - GridUi.s(6), y - GridUi.s(6), w + GridUi.s(12), h + GridUi.s(12), radius + GridUi.s(6), glow);
                // CSS: no border on primary
                GridUi.roundedRect(g, x, y, w, h, radius, bg);
            } else {
                // CSS: border 1px solid line
                GridUi.roundedRect(g, x, y, w, h, radius, border);
                GridUi.roundedRect(g, x + 1, y + 1, w - 2, h - 2, Math.max(1, radius - 1), bg);
            }

            // Содержимое
            if (type == Type.SMALL || type == Type.SMALL_EXIT) {
                // CSS: .btn-sm { icon 14×14; gap 8px; text 11px/500/uppercase/ls0.8px; padding 0 8px; justify-content center; }
                int iconSize = GridUi.s(14);
                int gap = GridUi.s(8);
                int padding = GridUi.s(8);
                String rawTitle = getMessage().getString();
                int ls = 1; // CSS: letter-spacing 0.8px → 1px
                int textW = spacedWidth(fnt, GridUi.styled(rawTitle), ls);
                int total = iconSize + gap + textW;
                int start = x + (w - total) / 2;
                int iconCx = start + iconSize / 2;
                int iconCy = y + h / 2;
                drawIcon(g, icon, iconCx, iconCy, iconSize, hover);
                // Текст с letter-spacing 1px (CSS: ls 0.8px, округляем до 1)
                drawSpaced(g, fnt, GridUi.styled(rawTitle), start + iconSize + gap, y + (h - fnt.lineHeight) / 2, 1, text);
            } else {
                // CSS: .btn-primary-lg / .btn-secondary-lg
                // padding: 0 20px; .btn-icon 36×36 margin-right 16px; svg 22×22
                // .btn-title 15px/700/uppercase/ls 1px, .btn-desc 11px/400/mt 2px
                int iconContainerSize = GridUi.s(36);
                int iconSvgSize = GridUi.s(22);
                int iconMarginRight = GridUi.s(16);
                int padLeft = GridUi.s(20);

                // Иконка по центру контейнера 36×36
                int iconCx = x + padLeft + iconContainerSize / 2;
                int iconCy = y + h / 2;
                drawIcon(g, icon, iconCx, iconCy, iconSvgSize, hover);

                // Текстовый блок
                int textX = x + padLeft + iconContainerSize + iconMarginRight;
                String rawTitle = getMessage().getString();
                int titleH = fnt.lineHeight;
                int descH = fnt.lineHeight;
                int gap2px = GridUi.s(2);
                int blockH = titleH + gap2px + descH;
                int blockStartY = y + (h - blockH) / 2;

                // Заголовок с letter-spacing 1px (CSS: ls 1px)
                drawSpaced(g, fnt, GridUi.styled(rawTitle), textX, blockStartY, 1, text);

                // Описание (CSS: font-size 11px, margin-top 2px)
                if (desc != null) {
                    g.drawString(fnt, GridUi.styled(desc), textX, blockStartY + titleH + gap2px, sub, false);
                }
            }
        }

        private static void drawIcon(GuiGraphics g, int icon, int cx, int cy, int displaySize, boolean hover) {
            // Текстурные иконки: 44px для 22px display (большие), 28px для 14px (маленькие)
            ResourceLocation tex;
            int texSize;
            switch (icon) {
                case ICON_PLAY    -> { tex = GridUi.ICO_PLAY;  texSize = 44; }
                case ICON_CHECK   -> { tex = GridUi.ICO_CHECK; texSize = 44; }
                case ICON_SLIDERS -> { tex = hover ? GridUi.ICO_SETTINGS_H : GridUi.ICO_SETTINGS; texSize = 28; }
                case ICON_INFO    -> { tex = hover ? GridUi.ICO_INFO_H : GridUi.ICO_INFO; texSize = 28; }
                case ICON_BAG     -> { tex = hover ? GridUi.ICO_BAG_H : GridUi.ICO_BAG; texSize = 28; }
                case ICON_EXIT    -> { tex = hover ? GridUi.ICO_EXIT_H : GridUi.ICO_EXIT; texSize = 28; }
                default -> return;
            }
            int ix = cx - displaySize / 2;
            int iy = cy - displaySize / 2;
            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
            g.blit(tex, ix, iy, 0, 0, displaySize, displaySize, texSize, texSize);
        }
    }
}
