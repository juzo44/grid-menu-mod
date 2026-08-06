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
 * CSS reference values (1920×1080):
 *   padding: 40px   topbar: 60px   right-col: 280px
 *   menu-w: 440px   title-scale: 52px   tag: 11px
 *   PRIMARY: 440×72 r12   SECONDARY: 440×62 r10   SMALL: flex×46 r10
 *   gaps: title→btn 48px, big 10px, small 8px, small-row mt 10px
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

        // Лейаут из CSS: padding 40px, right-col 280px, menu 440px
        p = GridUi.pad(width);
        rightColW = Math.min(280, width * 280 / 1920);
        menuW = Math.min(440, width - p * 2 - rightColW - 40);
        if (menuW < 200) menuW = width - p * 2 - 40; // fallback if no right col
        menuX = p + (width - p * 2 - rightColW - 40 - menuW) / 2;

        // CSS heights & gaps (exact from mockup)
        int titleBoxH = 66;  // 52px text + padding 10+12 = ~74, but MC font is smaller, so 66
        int tagH       = 13;  // padding 4+4 + ~5px text
        int tagGap     = 4;   // visual gap between box and tag (CSS margin-top: -2px → overlap)
        int titleGap   = 48;  // CSS: .title-block margin-bottom 48px
        int playH      = 72;  // CSS: .btn-primary-lg height 72px
        int singleH    = 62;  // CSS: .btn-secondary-lg height 62px
        int smallH     = 46;  // CSS: .btn-sm height 46px
        int bigGap     = 10;  // CSS: .menu-buttons gap 10px
        int smallRowGap = 10; // CSS: .bottom-row margin-top 10px
        int smallGap   = 8;   // CSS: .bottom-row gap 8px

        // Total content height (centered vertically)
        int totalH = titleBoxH + tagGap + tagH + titleGap
                   + playH + bigGap + singleH + smallRowGap + smallH;
        int startY = (height - totalH) / 2;

        int playY   = startY + titleBoxH + tagGap + tagH + titleGap;
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

        // ═══ BOTTOM ROW: мелкие кнопки (flex:1, 46px, r10) ═══
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

        int sw = (menuW - (small.size() - 1) * smallGap) / small.size();
        int sx = menuX;
        for (MenuButton btn : small) {
            btn.setX(sx);
            btn.setWidth(sw);
            addRenderableWidget(btn);
            sx += sw + smallGap;
        }

        // ═══ SOCIAL ICONS (bottom:24px, right:40px, 38×38, gap 10px) ═══
        int socialSize = 38;
        int socialGap  = 10;
        int socialY    = height - 24 - socialSize;
        int socialRight = width - p;
        addRenderableWidget(new SocialIconButton(
                socialRight - socialSize * 3 - socialGap * 2, socialY, socialSize,
                GridUi.ICON_TG, GridUi.ICON_TG_H, b -> openLink(TG_URL)));
        addRenderableWidget(new SocialIconButton(
                socialRight - socialSize * 2 - socialGap, socialY, socialSize,
                GridUi.ICON_DC, GridUi.ICON_DC_H, b -> openLink(DC_URL)));
        addRenderableWidget(new SocialIconButton(
                socialRight - socialSize, socialY, socialSize,
                GridUi.ICON_GL, GridUi.ICON_GL_H, b -> openLink(WEB_URL)));

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
       РЕНДЕР (порядок: фон → топбар → заголовок → правая колонка → версия → виджеты)
       ═══════════════════════════ */
    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        GridUi.background(g, width, height);
        renderTopbar(g);
        renderTitle(g);
        renderRightColumn(g);
        renderVersion(g);
        super.render(g, mx, my, pt); // кнопки + соц.иконки
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {}

    @Override
    public boolean isPauseScreen() { return false; }

    /* ═══════════════════════════
       TOP BAR (CSS: height 60px, padding 20px 40px)
       ═══════════════════════════ */
    private void renderTopbar(GuiGraphics g) {
        int topPad = 20; // CSS: padding-top 20px

        // Brand mark 32×32 (CSS: .brand-mark width 32px height 32px)
        GridUi.brandMark(g, p, topPad, 32);
        // Brand text «GRID» (CSS: gap 12px, font-size 13px, weight 700, spacing 3px)
        g.drawString(font, GridUi.styled("GRID"), p + 32 + 12, topPad + 3, GridUi.TEXT_MAIN, false);

        // Auth card (top-right)
        renderAuthCard(g, topPad);
    }

    /* ═══════════════════════════
       AUTH CARD (CSS: .auth-card padding 7px 14px, radius 10px)
       ═══════════════════════════ */
    private void renderAuthCard(GuiGraphics g, int topPad) {
        int right = width - p;
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

        // Ширина строки 1: 14 + nick + (7 + rank + 10) + 14
        int line1W = 14 + nickW + (rank.isEmpty() ? 0 : 7 + rankW + 10) + 14;

        String balLabel = authed ? "Баланс: " : "Авторизация через лаунчер";
        String balValue = authed ? formatBalance(balance()) + " \u20BD" : "";
        int line2W = 14 + fnt.width(GridUi.styled(balLabel))
                      + (balValue.isEmpty() ? 0 : 4 + fnt.width(GridUi.styled(balValue))) + 14;

        int cardW = Math.max(line1W, line2W);
        int cardH = authed ? 46 : 34;
        int cx = right - cardW;
        int cy = (60 - cardH) / 2; // центрируем в topbar (60px)

        GridUi.panel(g, cx, cy, cardW, cardH, 10);

        int tx = cx + 14;
        int ty = cy + (authed ? 12 : 10);
        g.drawString(fnt, GridUi.styled(nick), tx, ty, GridUi.TEXT_MAIN, false);

        if (!rank.isEmpty()) {
            int rankBgX = tx + nickW + 7;
            int rankBgW = rankW + 10;
            g.fill(rankBgX, ty - 1, rankBgX + rankBgW, ty + 10, GridUi.ACCENT_DIM);
            g.drawString(fnt, GridUi.styled(rank), rankBgX + 5, ty, GridUi.ACCENT, false);
        }

        if (authed) {
            int balLabelW = fnt.width(GridUi.styled(balLabel));
            g.drawString(fnt, GridUi.styled(balLabel), tx, ty + 13, GridUi.TEXT_MUTED, false);
            g.drawString(fnt, GridUi.styled(balValue), tx + balLabelW + 4, ty + 13, GridUi.ACCENT, false);
        } else {
            g.drawString(fnt, GridUi.styled(balLabel), tx, ty + 7, GridUi.TEXT_MUTED, false);
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
       CSS: .title-main padding 10px 36px 12px, font-size 52px, weight 900, spacing 8px
             box-shadow: 0 5px 0 dark, 0 7px 0 darker, 0 9px 20px rgba(0,0,0,0.5)
             .title-tag padding 4px 16px, margin-top -2px, radius 4px
       ═══════════════════════════ */
    private void renderTitle(GuiGraphics g) {
        var fnt = Minecraft.getInstance().font;
        int cx = menuX + menuW / 2;

        // Пересчитаем startY как в init() для консистентности
        int titleBoxH = 66;
        int tagH = 13;
        int tagGap = 4;
        int titleGap = 48;
        int playH = 72;
        int singleH = 62;
        int smallH = 46;
        int bigGap = 10;
        int smallRowGap = 10;

        int totalH = titleBoxH + tagGap + tagH + titleGap
                   + playH + bigGap + singleH + smallRowGap + smallH;
        int startY = (height - totalH) / 2;

        int boxW = menuW; // ширина заголовка = ширине кнопок
        int baseY = startY;

        // 3D тени (darker → dark → main) — смещение по Y как в CSS box-shadow
        GridUi.roundedRect(g, cx - boxW/2, baseY + 9, boxW, titleBoxH, 8, 0x80000000);
        GridUi.roundedRect(g, cx - boxW/2, baseY + 7, boxW, titleBoxH, 8, GridUi.ACCENT_DARKER);
        GridUi.roundedRect(g, cx - boxW/2, baseY + 5, boxW, titleBoxH, 8, GridUi.ACCENT_DARK);
        GridUi.roundedRect(g, cx - boxW/2, baseY,     boxW, titleBoxH, 8, GridUi.ACCENT);

        // Текст «GRID» (CSS 52px → MC scale 4.0x от ~12px base)
        g.pose().pushPose();
        g.pose().translate(cx, baseY + titleBoxH / 2, 0.0F);
        g.pose().scale(4.0F, 4.0F, 1.0F);
        g.drawCenteredString(fnt, GridUi.styled("GRID"), 0, -4, GridUi.BG_DEEP);
        g.pose().popPose();

        // Тег «Военно-политический сервер» (CSS: .title-tag)
        String tag = "Военно-политический сервер";
        int tw = fnt.width(GridUi.styled(tag));
        int tagPadX = 16; // CSS: padding 4px 16px
        int tagPadY = 4;  // CSS: padding-top/bottom 4px
        int tagY = baseY + titleBoxH + tagGap;
        int tagX = cx - tw / 2 - tagPadX;
        int tagW = tw + tagPadX * 2;
        int tagInnerH = tagPadY * 2 + 8; // 4+4+~8px text

        // Фон + полный бордер (1px все 4 стороны)
        g.fill(tagX, tagY, tagX + tagW, tagY + tagInnerH, GridUi.ACCENT_DIM);
        g.fill(tagX, tagY, tagX + tagW, tagY + 1, GridUi.ACCENT_BORDER);
        g.fill(tagX, tagY + tagInnerH - 1, tagX + tagW, tagY + tagInnerH, GridUi.ACCENT_BORDER);
        g.fill(tagX, tagY, tagX + 1, tagY + tagInnerH, GridUi.ACCENT_BORDER);
        g.fill(tagX + tagW - 1, tagY, tagX + tagW, tagY + tagInnerH, GridUi.ACCENT_BORDER);

        g.drawString(fnt, GridUi.styled(tag), cx - tw / 2, tagY + tagPadY, GridUi.ACCENT, false);
    }

    /* ═══════════════════════════
       ПРАВАЯ КОЛОНКА (CSS: position absolute, right 40px, top 50%, width 280px)
       ═══════════════════════════ */
    private void renderRightColumn(GuiGraphics g) {
        int rw = rightColW;
        int rx = width - p - rw;

        int newsCount = news == null ? 0 : Math.min(4, news.size());
        int statusH = 100;
        int newsH = 30 + (newsCount == 0 ? 24 : newsCount * 38);
        int total = statusH + 10 + newsH;
        int ry = Math.max(74, (height - total) / 2);
        if (ry + total > height - 90) ry = height - 90 - total;

        renderStatusPanel(g, rx, ry, rw, statusH);
        renderNewsPanel(g, rx, ry + statusH + 10, rw, newsH);
    }

    /**
     * Статус сервера (CSS: .panel padding 18px, .status-dot 6×6, .status-value 26px 800)
     */
    private void renderStatusPanel(GuiGraphics g, int x, int y, int w, int h) {
        GridUi.card(g, x, y, w, h);
        var fnt = Minecraft.getInstance().font;

        // Заголовок (CSS: .panel-title font-size 10px, weight 600, uppercase, spacing 1.5px, margin-bottom 12px)
        g.drawString(fnt, GridUi.styled("СТАТУС СЕРВЕРА"), x + 18, y + 18, GridUi.TEXT_MUTED, false);

        int state = ServerStatusManager.getState();
        boolean online = state == ServerStatusManager.ONLINE;
        int dotColor = online ? GridUi.ACCENT : 0xFF616A64;
        String label = online ? "Сервер работает" :
                state == ServerStatusManager.OFFLINE ? "Сервер недоступен" : "Проверка...";
        int labelColor = online ? GridUi.ACCENT :
                state == ServerStatusManager.OFFLINE ? 0xFFE06666 : 0xFF9AA5A0;

        // Dot 6×6 + label (CSS: .status-row gap 7px, margin-bottom 8px)
        int dx = x + 18;
        int dy = y + 34;
        g.fill(dx, dy + 1, dx + 6, dy + 7, dotColor);
        g.drawString(fnt, GridUi.styled(label), dx + 13, dy, labelColor, false);

        if (online) {
            int value = ServerStatusManager.getOnline();
            // Число 26px (CSS: .status-value font-size 26px, weight 800)
            g.pose().pushPose();
            g.pose().translate(dx, dy + 20, 0.0F);
            g.pose().scale(2.0F, 2.0F, 1.0F);
            g.drawString(fnt, GridUi.styled(String.valueOf(value)), 0, 0, GridUi.TEXT_MAIN, false);
            g.pose().popPose();
            // «игрока» (CSS: span font-size 12px, margin-left 3px)
            g.drawString(fnt, GridUi.styled("игрока"), dx + 18, dy + 24, GridUi.TEXT_MUTED, false);

            // Прогресс-бар (CSS: .status-bar-track height 3px, margin-top 10px)
            int barY = dy + 40;
            int barX = dx;
            int barW = x + w - 18 - barX;
            if (barW > 4) {
                g.fill(barX, barY, barX + barW, barY + 3, GridUi.LINE_COLOR);
                int max = ServerStatusManager.getMax();
                if (max > 0) {
                    int fillW = Math.max(3, (int)(barW * Math.min(1f, (float)value / max)));
                    g.fill(barX, barY, barX + fillW, barY + 3, GridUi.ACCENT);
                }
            }
        } else {
            g.drawString(fnt, GridUi.styled("—"), dx, dy + 20, GridUi.TEXT_MAIN, false);
        }
    }

    /**
     * Новости (CSS: .news-item padding 8px 0, border-bottom 1px rgba(52,64,56,0.4))
     */
    private void renderNewsPanel(GuiGraphics g, int x, int y, int w, int h) {
        GridUi.card(g, x, y, w, h);
        var fnt = Minecraft.getInstance().font;

        g.drawString(fnt, GridUi.styled("НОВОСТИ"), x + 18, y + 18, GridUi.TEXT_MUTED, false);

        if (news == null) {
            g.drawString(fnt, GridUi.styled("Загрузка..."), x + 18, y + 38, GridUi.TEXT_DIM, false);
            return;
        }

        int iy = y + 38;
        int shown = 0;
        for (JsonElement element : news) {
            if (shown >= 4) break;
            JsonObject item = element.getAsJsonObject();
            String title = item.has("title") ? item.get("title").getAsString() : "";
            String date = item.has("publishedAt") ? item.get("publishedAt").getAsString() : "";
            if (date.length() > 10) date = date.substring(0, 10);

            // Дата (CSS: .news-date font-size 9px, dim)
            g.drawString(fnt, GridUi.styled(date), x + 18, iy, GridUi.TEXT_DIM, false);
            // Заголовок (CSS: .news-title font-size 11px, weight 500, margin-top 2px)
            String clipped = fnt.plainSubstrByWidth(title, w - 36);
            g.drawString(fnt, GridUi.styled(clipped), x + 18, iy + 12, GridUi.TEXT_MAIN, false);
            // Разделитель (CSS: border-bottom 1px rgba(52,64,56,0.4))
            if (shown < 3) {
                g.fill(x + 18, iy + 30, x + w - 18, iy + 31, 0x66344038);
            }
            iy += 38;
            shown++;
        }
        if (shown == 0) {
            g.drawString(fnt, GridUi.styled("Новостей пока нет"), x + 18, y + 38, GridUi.TEXT_DIM, false);
        }
    }

    /* ═══════════════════════════
       VERSION (CSS: bottom 10px, left 40px, font-size 10px)
       ═══════════════════════════ */
    private void renderVersion(GuiGraphics g) {
        g.drawString(font, GridUi.styled("Minecraft 1.21.1 \u00B7 NeoForge \u00B7 GRID v0.1.0"),
                p, height - 10, GridUi.TEXT_DIM, false);
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

    /* ═════════════════════════
       СОЦ. КНОПКА
       ═════════════════════════ */
    private static final class SocialIconButton extends Button {
        private final ResourceLocation normal;
        private final ResourceLocation hovered;

        SocialIconButton(int x, int y, int size, ResourceLocation normal, ResourceLocation hovered, OnPress press) {
            super(x, y, size, size, Component.empty(), press, DEFAULT_NARRATION);
            this.normal = normal;
            this.hovered = hovered;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            g.blit(isHovered() && active ? hovered : normal,
                    getX(), getY(), 0.0F, 0.0F, width, height, width, height);
        }
    }

    /* ═══════════════════════════
       КНОПКА МЕНЮ
       CSS: btn-primary-lg: 440×72, r12, accent bg, shadow 0 0 20px rgba(104,194,132,0.10)
           btn-secondary-lg: 440×62, r10, dark bg, border 1px line
           btn-sm: flex:1 × 46, r10, dark bg, border 1px line
       ═════════════════════════ */
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
                    sub = 0x990B0F0C;
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

            if (type == Type.PRIMARY) {
                // CSS: box-shadow всегда 0 0 20px rgba(104,194,132,0.10), на ховере 0 0 28px rgba(0.20)
                int glow = hover ? 0x3368C284 : 0x1A68C284;
                GridUi.roundedRect(g, x - 6, y - 6, w + 12, h + 12, radius + 6, glow);
                GridUi.roundedRect(g, x, y, w, h, radius, bg);
            } else {
                GridUi.roundedRect(g, x, y, w, h, radius, border);
                GridUi.roundedRect(g, x + 1, y + 1, w - 2, h - 2, Math.max(1, radius - 1), bg);
            }

            // Содержимое
            if (type == Type.SMALL || type == Type.SMALL_EXIT) {
                int iconSize = 14;
                int gap = 8;
                int availW = w - iconSize - gap - 16;
                String rawTitle = getMessage().getString();
                String title = availW > 0 ? fnt.plainSubstrByWidth(rawTitle, availW) : "";
                int textW = fnt.width(GridUi.styled(title));
                int total = iconSize + gap + textW;
                int start = x + (w - total) / 2;
                drawIcon(g, icon, start + iconSize / 2, y + h / 2, iconSize, iconColor);
                g.drawString(fnt, GridUi.styled(title), start + iconSize + gap, y + (h - 8) / 2, text, false);
            } else {
                // CSS: .btn-icon 36×36 margin-right 16px, padding 0 20px
                int iconSize = 22;
                int iconCx = x + 20 + iconSize / 2;
                drawIcon(g, icon, iconCx, y + h / 2, iconSize, iconColor);
                int textX = x + 20 + 36 + 16;
                g.drawString(fnt, getMessage(), textX, y + h / 2 - 9, text, false);
                if (desc != null) {
                    g.drawString(fnt, GridUi.styled(desc), textX, y + h / 2 + 3, sub, false);
                }
            }
        }

        private static void drawIcon(GuiGraphics g, int icon, int cx, int cy, int size, int color) {
            switch (icon) {
                case ICON_PLAY    -> UiIcons.play(g, cx, cy, size, color);
                case ICON_CHECK   -> UiIcons.check(g, cx, cy, size, color);
                case ICON_SLIDERS -> UiIcons.sliders(g, cx, cy, size, color);
                case ICON_INFO    -> UiIcons.info(g, cx, cy, size, color);
                case ICON_BAG     -> UiIcons.bag(g, cx, cy, size, color);
                case ICON_EXIT    -> UiIcons.exit(g, cx, cy, size, color);
                default -> {}
            }
        }
    }
}
