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
 * Главное меню GRID — точный порт HTML-мокапа.
 * Лейаут: топбар → центральная колонка (заголовок + кнопки) + правая колонка (статус + новости).
 */
public final class GridScreen extends Screen {

    private static final String MAIN_IP = "grid-server.ru";
    private static final String TG_URL  = "https://t.me/gridwarfare";
    private static final String DC_URL  = "https://discord.gg/gridwarfare";
    private static final String WEB_URL = "https://grid-server.ru";

    private static final long NEWS_REFRESH_MS = 60_000L;
    private static final long AUTH_REFRESH_MS = 30_000L;

    /* Иконки */
    private static final int ICON_PLAY    = 1;
    private static final int ICON_CHECK   = 2;
    private static final int ICON_SLIDERS = 3;
    private static final int ICON_INFO    = 4;
    private static final int ICON_BAG     = 5;
    private static final int ICON_EXIT    = 6;

    /* API-данные */
    private JsonObject me;
    private JsonArray  news;
    private long newsFetchedAt;
    private long authFetchedAt;

    /* Кэш лейаута (пересчитывается в init()) */
    private int p;          // pad (отступ от краёв)
    private int rightColW;  // ширина правой колонки
    private int menuW;      // ширина меню-колонки
    private int menuX;      // X меню-колонки

    public GridScreen() {
        super(Component.literal("GRID"));
    }

    /* ═══════════════════════════
       INIT
       ═══════════════════════════ */
    @Override
    protected void init() {
        ServerStatusManager.start();

        // ── Рассчитываем лейаут (как в мокапе) ──
        p = GridUi.pad(width);
        rightColW = Math.min(280, width * 21 / 100);
        menuW = GridUi.menuWidth(width, rightColW, p);
        menuX = GridUi.menuX(width, menuW, rightColW, p);

        // ── Высоты элементов (из мокапа) ──
        int titleBoxH = 56;  // title-main padding 10+12 = 22 + text ~34
        int tagH       = 16;  // padding 4+4 + text ~8
        int titleGap   = 48;  // margin-bottom: 48px
        int playH      = 72;
        int singleH    = 62;
        int smallH     = 46;
        int bigGap     = 10;  // gap: 10px
        int smallGap   = 8;   // gap: 8px

        // Общая высота контента (без мелких кнопок, они ниже)
        int totalContentH = titleBoxH + 8 + tagH + titleGap + playH + bigGap + singleH + bigGap + smallH;
        int startY = (height - totalContentH) / 2;

        // ── Y-позиции ──
        int playY   = startY + titleBoxH + 8 + tagH + titleGap;
        int singleY = playY + playH + bigGap;
        int rowY    = singleY + singleH + bigGap;

        // ═══ PRIMARY: ИГРАТЬ ═══
        addRenderableWidget(new MenuButton(
                menuX, playY, menuW, playH,
                ICON_PLAY, "ИГРАТЬ", "Подключение к серверу",
                MenuButton.Type.PRIMARY,
                b -> connectToServer()
        ));

        // ═══ SECONDARY: ОДИНОЧНЫЙ МИР ═══
        addRenderableWidget(new MenuButton(
                menuX, singleY, menuW, singleH,
                ICON_CHECK, "ОДИНОЧНЫЙ МИР", "Одиночная игра",
                MenuButton.Type.SECONDARY,
                b -> minecraft.setScreen(new SelectWorldScreen(this))
        ));

        // ═══ BOTTOM ROW: мелкие кнопки ═══
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

        // ═══ SOCIAL ICONS (bottom-right) ═══
        int socialY = height - 62;
        int socialRight = width - p;
        int socialGap = 10;
        int socialSize = 38;
        addRenderableWidget(new SocialIconButton(
                socialRight - socialSize * 3 - socialGap * 2, socialY, socialSize,
                GridUi.ICON_TG, GridUi.ICON_TG_H, b -> openLink(TG_URL)));
        addRenderableWidget(new SocialIconButton(
                socialRight - socialSize * 2 - socialGap, socialY, socialSize,
                GridUi.ICON_DC, GridUi.ICON_DC_H, b -> openLink(DC_URL)));
        addRenderableWidget(new SocialIconButton(
                socialRight - socialSize, socialY, socialSize,
                GridUi.ICON_GL, GridUi.ICON_GL_H, b -> openLink(WEB_URL)));

        // API
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
    public void removed() {
        ServerStatusManager.stop();
    }

    /* ═══════════════════════════
       API ЗАГРУЗКА
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
       РЕНДЕР
       ═══════════════════════════ */
    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        // 1) Фон (текстура + оверлей + виньетка)
        GridUi.background(g, width, height);

        // 2) Топбар (brand + auth card)
        renderTopbar(g);

        // 3) 3D-заголовок «GRID» + тег
        renderTitle(g);

        // 4) Правая колонка (статус + новости)
        renderRightColumn(g);

        // 5) Версия (левый нижний угол)
        renderVersion(g);

        // 6) Виджеты (кнопки, соц.иконки)
        super.render(g, mx, my, pt);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Переопределено: фон рисуем сами в render()
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /* ═══════════════════════════
       TOP BAR
       ═══════════════════════════ */
    private void renderTopbar(GuiGraphics g) {
        int topPad = Math.max(20, p / 2);
        // Brand mark 32x32
        GridUi.brandMark(g, p, topPad, 32);
        // Brand text «GRID» — gap 12px от иконки
        g.drawString(font, GridUi.styled("GRID"), p + 32 + 12, topPad + 3, GridUi.TEXT_MAIN, false);
        // Auth card
        renderAuthCard(g, topPad);
    }

    /* ═══════════════════════════
       AUTH CARD (top-right)
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

        // Ширина строки 1: padding 14 + nick + (gap 7 + rank + pad 5) + padding 14
        int line1W = 14 + nickW + (rank.isEmpty() ? 0 : 7 + rankW + 5 + 10) + 14;

        String balLabel = authed ? "Баланс: " : "Авторизация через лаунчер";
        String balValue = authed ? formatBalance(balance()) + " \u20BD" : "";
        int line2W = 14 + fnt.width(GridUi.styled(balLabel)) + (balValue.isEmpty() ? 0 : 4 + fnt.width(GridUi.styled(balValue))) + 14;

        int cardW = Math.max(line1W, line2W);
        int cardH = authed ? 46 : 34;
        int cx = right - cardW;
        int cy = (60 - cardH) / 2 + 4;

        GridUi.panel(g, cx, cy, cardW, cardH, 10);

        int tx = cx + 14;
        int ty = cy + (authed ? 12 : 10);
        // Ник
        g.drawString(fnt, GridUi.styled(nick), tx, ty, GridUi.TEXT_MAIN, false);
        // Ранк (с фоном и бордером как в мокапе)
        if (!rank.isEmpty()) {
            int rankBgX = tx + nickW + 7;
            int rankBgW = rankW + 10;
            // Фон ранга
            g.fill(rankBgX, ty - 1, rankBgX + rankBgW, ty + 10, GridUi.ACCENT_DIM);
            // Текст ранка
            g.drawString(fnt, GridUi.styled(rank), rankBgX + 5, ty, GridUi.ACCENT, false);
        }
        // Баланс
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
       ═══════════════════════════ */
    private void renderTitle(GuiGraphics g) {
        // Используем кэшированный menuX и menuW
        int cx = menuX + menuW / 2;

        int boxW = Math.min(280, menuW);
        int boxH = 56;  // padding 10+12 + текст
        int tagH = 16;
        int titleGap = 48;
        int playH = 72;
        int singleH = 62;
        int smallH = 46;
        int bigGap = 10;

        int totalContentH = boxH + 8 + tagH + titleGap + playH + bigGap + singleH + bigGap + smallH;
        int startY = (height - totalContentH) / 2;
        int baseY = startY;

        // 3D-эффект: 3 слоя (darker → dark → accent) со смещением по Y
        // box-shadow: 0 5px 0 dark, 0 7px 0 darker, 0 9px 20px black
        GridUi.roundedRect(g, cx - boxW / 2, baseY + 9, boxW, boxH, 8, 0x80000000); // тень
        GridUi.roundedRect(g, cx - boxW / 2, baseY + 7, boxW, boxH, 8, GridUi.ACCENT_DARKER);
        GridUi.roundedRect(g, cx - boxW / 2, baseY + 5, boxW, boxH, 8, GridUi.ACCENT_DARK);
        GridUi.roundedRect(g, cx - boxW / 2, baseY, boxW, boxH, 8, GridUi.ACCENT);

        // Текст «GRID» (52px в мокапе ≈ масштаб 2.8x от 12px шрифта)
        var fnt = Minecraft.getInstance().font;
        g.pose().pushPose();
        g.pose().translate(cx, baseY + boxH / 2, 0.0F);
        g.pose().scale(2.8F, 2.8F, 1.0F);
        g.drawCenteredString(fnt, GridUi.styled("GRID"), 0, -4, GridUi.BG_DEEP);
        g.pose().popPose();

        // Тег «Военно-политический сервер»
        String tag = "Военно-политический сервер";
        int tw = fnt.width(GridUi.styled(tag));
        int tagPadX = 16;
        int tagY = baseY + boxH + 4;
        int tagX = cx - tw / 2 - tagPadX;
        int tagW = tw + tagPadX * 2;
        int tagInnerH = 11; // padding 4+4 + текст ~3px

        // Фон тега (ACCENT_DIM) + бордер (ACCENT_BORDER)
        g.fill(tagX, tagY, tagX + tagW, tagY + tagInnerH, GridUi.ACCENT_DIM);
        // Верхняя и нижняя линия бордера
        g.fill(tagX, tagY, tagX + tagW, tagY + 1, GridUi.ACCENT_BORDER);
        g.fill(tagX, tagY + tagInnerH - 1, tagX + tagW, tagY + tagInnerH, GridUi.ACCENT_BORDER);
        // Левая и правая линия бордера
        g.fill(tagX, tagY, tagX + 1, tagY + tagInnerH, GridUi.ACCENT_BORDER);
        g.fill(tagX + tagW - 1, tagY, tagX + tagW, tagY + tagInnerH, GridUi.ACCENT_BORDER);

        // Текст тега
        g.drawString(fnt, GridUi.styled(tag), cx - tw / 2, tagY + 3, GridUi.ACCENT, false);
    }

    /* ═══════════════════════════
       ПРАВАЯ КОЛОНКА
       ═══════════════════════════ */
    private void renderRightColumn(GuiGraphics g) {
        int rw = rightColW;
        int rx = width - p - rw;

        // Высоты панелей
        int newsCount = news == null ? 0 : Math.min(4, news.size());
        int statusH = 100;
        int newsH = 30 + (newsCount == 0 ? 24 : newsCount * 38);
        int total = statusH + 10 + newsH;
        int ry = Math.max(74, (height - total) / 2);
        if (ry + total > height - 90) ry = height - 90 - total;

        // Панель статуса
        renderStatusPanel(g, rx, ry, rw, statusH);
        // Панель новостей
        renderNewsPanel(g, rx, ry + statusH + 10, rw, newsH);
    }

    /**
     * Панель «Статус сервера».
     * Из мокапа: точка 6x6, «Сервер работает», число 26px жирное, полоска 3px.
     */
    private void renderStatusPanel(GuiGraphics g, int x, int y, int w, int h) {
        GridUi.card(g, x, y, w, h);
        var fnt = Minecraft.getInstance().font;

        // Заголовок
        g.drawString(fnt, GridUi.styled("СТАТУС СЕРВЕРА"), x + 18, y + 18, GridUi.TEXT_MUTED, false);

        int state = ServerStatusManager.getState();
        boolean online = state == ServerStatusManager.ONLINE;
        int dotColor = online ? GridUi.ACCENT : 0xFF616A64;
        String label = online ? "Сервер работает" :
                state == ServerStatusManager.OFFLINE ? "Сервер недоступен" : "Проверка...";
        int labelColor = online ? GridUi.ACCENT :
                state == ServerStatusManager.OFFLINE ? 0xFFE06666 : 0xFF9AA5A0;

        // Строка: точка + текст
        int dx = x + 18;
        int dy = y + 34;
        g.fill(dx, dy + 1, dx + 6, dy + 7, dotColor); // 6x6 точка, центрирована по тексту
        g.drawString(fnt, GridUi.styled(label), dx + 12, dy, labelColor, false);

        if (online) {
            int value = ServerStatusManager.getOnline();
            // Число (26px в мокапе ≈ масштаб 2.0x)
            g.pose().pushPose();
            g.pose().translate(dx, dy + 20, 0.0F);
            g.pose().scale(2.0F, 2.0F, 1.0F);
            g.drawString(fnt, GridUi.styled(String.valueOf(value)), 0, 0, GridUi.TEXT_MAIN, false);
            g.pose().popPose();
            // Подпись «игрока» (12px, 1px правее числа)
            g.drawString(fnt, GridUi.styled("игрока"), dx + 18, dy + 24, GridUi.TEXT_MUTED, false);

            // Прогресс-бар (3px высота, скруглённая через roundedRect)
            int barY = dy + 40;
            int barX = dx;
            int barW = x + w - 18 - barX;
            if (barW > 4) {
                GridUi.roundedRect(g, barX, barY, barW, 3, 2, GridUi.LINE_COLOR);
                int max = ServerStatusManager.getMax();
                if (max > 0) {
                    int fillW = Math.max(3, (int) (barW * Math.min(1f, (float) value / max)));
                    GridUi.roundedRect(g, barX, barY, fillW, 3, 2, GridUi.ACCENT);
                }
            }
        } else {
            g.drawString(fnt, GridUi.styled("—"), dx, dy + 20, GridUi.TEXT_MAIN, false);
        }
    }

    /**
     * Панель «Новости».
     * Из мокапа: дата (9px dim), заголовок (11px main), разделитель 1px.
     */
    private void renderNewsPanel(GuiGraphics g, int x, int y, int w, int h) {
        GridUi.card(g, x, y, w, h);
        var fnt = Minecraft.getInstance().font;

        // Заголовок
        g.drawString(fnt, GridUi.styled("НОВОСТИ"), x + 18, y + 18, GridUi.TEXT_MUTED, false);

        if (news == null) {
            g.drawString(fnt, GridUi.styled("Загрузка..."), x + 18, y + 38, GridUi.TEXT_DIM, false);
            return;
        }

        int iy = y + 38; // 18px заголовок + 20px отступ (padding 18 + gap)
        int shown = 0;
        for (JsonElement element : news) {
            if (shown >= 4) break;
            JsonObject item = element.getAsJsonObject();
            String title = item.has("title") ? item.get("title").getAsString() : "";
            String date = item.has("publishedAt") ? item.get("publishedAt").getAsString() : "";
            if (date.length() > 10) date = date.substring(0, 10);

            // Дата (dim)
            g.drawString(fnt, GridUi.styled(date), x + 18, iy, GridUi.TEXT_DIM, false);
            // Заголовок новости (main, обрезанный по ширине)
            String clipped = fnt.plainSubstrByWidth(title, w - 36);
            g.drawString(fnt, GridUi.styled(clipped), x + 18, iy + 12, GridUi.TEXT_MAIN, false);
            // Разделитель (rgba(52,64,56,0.4))
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
       VERSION TEXT (bottom-left)
       ═══════════════════════════ */
    private void renderVersion(GuiGraphics g) {
        g.drawString(font, GridUi.styled("Minecraft 1.21.1 \u00B7 NeoForge \u00B7 GRID v0.1.0"),
                p, height - 14, GridUi.TEXT_DIM, false);
    }

    /* ═══════════════════════════
       ДЕЙСТВИЯ
       ═══════════════════════════ */
    private void connectToServer() {
        try {
            ServerData data = new ServerData("GRID", MAIN_IP, ServerData.Type.OTHER);
            ConnectScreen.startConnecting(this, minecraft, ServerAddress.parseString(MAIN_IP), data, false, null);
        } catch (Throwable e) {
            minecraft.setScreen(new TitleScreen());
        }
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
        } catch (Throwable e) {
            minecraft.setScreen(new ServerInfoScreen(this));
        }
    }

    /* ═══════════════════════════
       СОЦ. КНОПКА (текстурная)
       ═══════════════════════════ */
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
            boolean hover = isHovered() && active;
            g.blit(hover ? hovered : normal, getX(), getY(), 0.0F, 0.0F, width, height, width, height);
        }
    }

    /* ═══════════════════════════
       КНОПКА МЕНЮ
       Типы: PRIMARY (зелёная), SECONDARY (тёмная с бордером),
       SMALL / SMALL_EXIT (мелкие в нижнем ряду).
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

            // Выбираем цвета в зависимости от типа
            int bg, border, text, sub, iconColor;
            switch (type) {
                case PRIMARY -> {
                    bg = hover ? GridUi.ACCENT_HOVER : GridUi.ACCENT;
                    border = GridUi.ACCENT; // нет видимого бордера — фон = бордер
                    text = GridUi.BG_DEEP;
                    sub = 0x990B0F0C; // rgba(11,15,12,0.6) — описание
                    iconColor = GridUi.BG_DEEP;
                }
                case SECONDARY -> {
                    bg = hover ? GridUi.BTN_SEC_HOVER : GridUi.BTN_SEC_BG;
                    border = hover ? 0x4068C284 : GridUi.LINE_COLOR; // hover: accent border
                    text = GridUi.TEXT_MAIN;
                    sub = GridUi.TEXT_MUTED;
                    iconColor = GridUi.ACCENT;
                }
                case SMALL_EXIT -> {
                    bg = hover ? GridUi.BTN_SM_HOVER : GridUi.BTN_SM_BG;
                    border = hover ? 0x4DDC5050 : GridUi.LINE_COLOR; // hover: красный
                    text = hover ? GridUi.TEXT_MAIN : GridUi.TEXT_MUTED;
                    sub = GridUi.TEXT_MUTED;
                    iconColor = hover ? 0xFFE05555 : GridUi.TEXT_MUTED;
                }
                default -> { // SMALL
                    bg = hover ? GridUi.BTN_SM_HOVER : GridUi.BTN_SM_BG;
                    border = hover ? 0x4068C284 : GridUi.LINE_COLOR; // hover: accent border
                    text = hover ? GridUi.TEXT_MAIN : GridUi.TEXT_MUTED;
                    sub = GridUi.TEXT_MUTED;
                    iconColor = hover ? GridUi.ACCENT : GridUi.TEXT_MUTED;
                }
            }

            int radius = switch (type) {
                case PRIMARY -> 12;
                default -> 10;
            };

            // PRIMARY без бордера (фон заполняет всё)
            if (type == Type.PRIMARY) {
                GridUi.roundedRect(g, x, y, w, h, radius, bg);
                // box-shadow: 0 0 20px rgba(104,194,132,0.10) — на ховере увеличивается
                if (hover) {
                    // Свечение вокруг кнопки (только ховер)
                    int glow = 0x1A68C284; // rgba(104,194,132,0.10)
                    GridUi.roundedRect(g, x - 4, y - 4, w + 8, h + 8, radius + 4, glow);
                }
            } else {
                // Остальные: бордер + inset фон
                GridUi.roundedRect(g, x, y, w, h, radius, border);
                GridUi.roundedRect(g, x + 1, y + 1, w - 2, h - 2, Math.max(1, radius - 1), bg);
            }

            // Содержимое
            if (type == Type.SMALL || type == Type.SMALL_EXIT) {
                // Мелкая кнопка: иконка + текст по центру
                int iconSize = 14;
                int textW = fnt.width(getMessage());
                int total = iconSize + 8 + textW;
                int start = x + (w - total) / 2;
                drawIcon(g, icon, start + iconSize / 2, y + h / 2, iconSize, iconColor);
                g.drawString(fnt, getMessage(), start + iconSize + 8, y + (h - 8) / 2, text, false);
            } else {
                // Крупная кнопка: иконка слева + заголовок + описание
                int padX = 20;
                int iconSize = 22;
                int iconCx = x + padX + iconSize / 2;
                drawIcon(g, icon, iconCx, y + h / 2, iconSize, iconColor);
                int textX = x + padX + iconSize + 16; // margin-right 16px от иконки
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
