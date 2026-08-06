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

public final class GridScreen extends Screen {
    private static final String MAIN_IP = "grid-server.ru";
    private static final String TG_URL = "https://t.me/gridwarfare";
    private static final String DC_URL = "https://discord.gg/gridwarfare";
    private static final String WEB_URL = "https://grid-server.ru";

    private static final long NEWS_REFRESH_MS = 60000L;
    private static final long AUTH_REFRESH_MS = 30000L;

    private static final int ICON_PLAY = 1;
    private static final int ICON_CHECK = 2;
    private static final int ICON_SLIDERS = 3;
    private static final int ICON_INFO = 4;
    private static final int ICON_BAG = 5;
    private static final int ICON_EXIT = 6;

    private JsonObject me;
    private JsonArray news;
    private long newsFetchedAt;
    private long authFetchedAt;

    public GridScreen() {
        super(Component.literal("GRID"));
    }

    /** Отступ от краёв, масштабируемый. */
    private static int pad() {
        return Math.max(24, Math.min(120, (int) (Minecraft.getInstance().getWindow().getGuiScaledWidth() * 0.08)));
    }

    @Override
    protected void init() {
        ServerStatusManager.start();
        int p = pad();
        int menuW = Math.min(440, width * 30 / 100);

        // Кнопки располагаются по центру (смещены чуть влево от центра экрана)
        // чтобы правая колонка с панелями помещалась справа
        int rightColW = Math.min(280, width * 21 / 100);
        int availW = width - p * 2 - rightColW - 20;
        int menuX = p + (availW - menuW) / 2;

        // Title block над кнопками
        int titleBlockH = 110;
        int centerY = height / 2;
        int contentTop = centerY - titleBlockH / 2 - 72 - 20; // titleH/2 + buttonH/2 + tag + gap
        int playY = contentTop + titleBlockH;

        // Primary: ИГРАТЬ (72px высота)
        addRenderableWidget(new MenuButton(menuX, playY, menuW, 72, ICON_PLAY, "ИГРАТЬ", "Подключение к серверу",
                MenuButton.Type.PRIMARY, b -> connectToServer()));

        // Secondary: ОДИНОЧНЫЙ МИР (62px)
        int singleY = playY + 72 + 10;
        addRenderableWidget(new MenuButton(menuX, singleY, menuW, 62, ICON_CHECK, "ОДИНОЧНЫЙ МИР", "Одиночная игра",
                MenuButton.Type.SECONDARY, b -> minecraft.setScreen(new SelectWorldScreen(this))));

        // Bottom row: 4 мелкие кнопки (46px, 10px border-radius)
        List<MenuButton> small = new ArrayList<>();
        int rowY = singleY + 62 + 10;
        small.add(new MenuButton(0, rowY, 0, 46, ICON_SLIDERS, "НАСТРОЙКИ", null,
                MenuButton.Type.SMALL, b -> minecraft.setScreen(new OptionsScreen(this, minecraft.options))));
        small.add(new MenuButton(0, rowY, 0, 46, ICON_INFO, "О СЕРВЕРЕ", null,
                MenuButton.Type.SMALL, b -> minecraft.setScreen(new ServerInfoScreen(this))));
        if (isShopAvailable()) {
            small.add(new MenuButton(0, rowY, 0, 46, ICON_BAG, "МАГАЗИН", null,
                    MenuButton.Type.SMALL, b -> openShop()));
        }
        small.add(new MenuButton(0, rowY, 0, 46, ICON_EXIT, "ВЫХОД", null,
                MenuButton.Type.SMALL_EXIT, b -> minecraft.stop()));

        int gap = 8;
        int sw = (menuW - (small.size() - 1) * gap) / small.size();
        int sx = menuX;
        for (MenuButton button : small) {
            button.setX(sx);
            button.setWidth(sw);
            addRenderableWidget(button);
            sx += sw + gap;
        }

        // Соцсети (правый нижний угол)
        int socialY = height - 62;
        int socialRight = width - p;
        addRenderableWidget(new SocialIconButton(socialRight - 38 * 3 - 10 * 2, socialY, 38,
                GridUi.ICON_TG, GridUi.ICON_TG_H, b -> openLink(TG_URL)));
        addRenderableWidget(new SocialIconButton(socialRight - 38 * 2 - 10, socialY, 38,
                GridUi.ICON_DC, GridUi.ICON_DC_H, b -> openLink(DC_URL)));
        addRenderableWidget(new SocialIconButton(socialRight - 38, socialY, 38,
                GridUi.ICON_GL, GridUi.ICON_GL_H, b -> openLink(WEB_URL)));

        loadAuth();
        loadNews();
    }

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

    /* ── API ── */
    private void loadAuth() {
        authFetchedAt = Util.getMillis();
        new Thread(() -> {
            JsonObject result = GridApiClient.me();
            Minecraft.getInstance().execute(() -> me = result);
        }, "GRID-menu-auth").start();
    }

    private void loadNews() {
        newsFetchedAt = Util.getMillis();
        new Thread(() -> {
            JsonArray result = GridApiClient.news();
            Minecraft.getInstance().execute(() -> {
                news = result;
                newsFetchedAt = Util.getMillis();
            });
        }, "GRID-menu-news").start();
    }

    private static boolean isShopAvailable() {
        try {
            Class.forName("ru.gridwarfare.shop.GridShopScreen");
            return true;
        } catch (Throwable e) { return false; }
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

    /* ════════════════════════════
       РЕНДЕР
       ════════════════════════════ */
    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        GridUi.background(g, width, height);
        renderTopbar(g);
        renderTitle(g);
        renderRightColumn(g);
        renderVersion(g);
        super.render(g, mx, my, pt);
    }

    /* ── TOP BAR ── */
    private void renderTopbar(GuiGraphics g) {
        int p = pad();
        // Brand mark (32x32, 5px clipped corners)
        GridUi.brandMark(g, p, 14, 32);
        // Brand text "GRID"
        g.drawString(font, GridUi.styled("GRID"), p + 44, 17, GridUi.TEXT_MAIN, false);
        // Auth card (правый верхний угол)
        renderAuthCard(g);
    }

    /* ── AUTH CARD ── */
    private void renderAuthCard(GuiGraphics g) {
        int p = pad();
        int right = width - p;
        String nick = "ГОСТЬ";
        String rank = "";
        boolean authed = me != null;
        if (authed) {
            if (me.has("username")) nick = me.get("username").getAsString().toUpperCase();
            if (me.has("donate")) rank = me.get("donate").getAsString().toUpperCase();
            if (rank.isEmpty() && me.has("rank")) rank = me.get("rank").getAsString().toUpperCase();
        }

        int nickW = font.width(GridUi.styled(nick));
        int rankW = rank.isEmpty() ? 0 : font.width(GridUi.styled(rank));
        int line1 = 14 + nickW + (rank.isEmpty() ? 0 : 7 + rankW + 10) + 14;

        String balLabel = authed ? "Баланс: " : "Авторизация через лаунчер";
        String balValue = authed ? fmt(balance()) + " \u20BD" : "";
        int line2 = 14 + font.width(GridUi.styled(balLabel)) + (balValue.isEmpty() ? 0 : 4 + font.width(GridUi.styled(balValue))) + 14;

        int cardW = Math.max(line1, line2);
        int cardH = authed ? 44 : 32;
        int cx = right - cardW;
        int cy = (60 - cardH) / 2 + 4;
        GridUi.panel(g, cx, cy, cardW, cardH, 10);

        int tx = cx + 14;
        int ty = cy + 11;
        g.drawString(font, GridUi.styled(nick), tx, ty, GridUi.TEXT_MAIN, false);
        if (!rank.isEmpty()) {
            g.fill(tx + nickW + 7, ty - 1, tx + nickW + 7 + rankW + 10, ty + 9, GridUi.ACCENT_DIM);
            g.drawString(font, GridUi.styled(rank), tx + nickW + 12, ty, GridUi.ACCENT, false);
        }
        if (authed) {
            int lblW = font.width(GridUi.styled(balLabel));
            g.drawString(font, GridUi.styled(balLabel), tx, ty + 13, GridUi.TEXT_MUTED, false);
            g.drawString(font, GridUi.styled(balValue), tx + lblW + 4, ty + 13, GridUi.ACCENT, false);
        } else {
            g.drawString(font, GridUi.styled(balLabel), tx, ty + 7, GridUi.TEXT_MUTED, false);
        }
    }

    private long balance() {
        return me != null && me.has("balance") ? me.get("balance").getAsLong() : 0L;
    }

    private static String fmt(long value) {
        String digits = String.valueOf(Math.abs(value));
        StringBuilder sb = new StringBuilder(digits);
        for (int i = sb.length() - 3; i > 0; i -= 3) sb.insert(i, ' ');
        if (value < 0) sb.insert(0, '-');
        return sb.toString();
    }

    /* ── TITLE «GRID» с 3D-эффектом ── */
    private void renderTitle(GuiGraphics g) {
        int p = pad();
        int rightColW = Math.min(280, width * 21 / 100);
        int menuW = Math.min(440, width * 30 / 100);
        int availW = width - p * 2 - rightColW - 20;
        int menuX = p + (availW - menuW) / 2;
        int cx = menuX + menuW / 2;

        int boxW = Math.min(280, menuW);
        int boxH = 54;
        int baseY = height / 2 - boxH / 2 - 72 - 16; // над кнопками

        // 3D блок-тень: darker → dark → main
        GridUi.roundedRect(g, cx - boxW / 2, baseY + 7, boxW, boxH, 8, GridUi.ACCENT_DARKER);
        GridUi.roundedRect(g, cx - boxW / 2, baseY + 4, boxW, boxH, 8, GridUi.ACCENT_DARK);
        GridUi.roundedRect(g, cx - boxW / 2, baseY, boxW, boxH, 8, GridUi.ACCENT);

        // Текст «GRID»
        g.pose().pushPose();
        g.pose().translate(cx, baseY + boxH / 2, 0.0F);
        g.pose().scale(2.8F, 2.8F, 1.0F);
        g.drawCenteredString(font, GridUi.styled("GRID"), 0, -4, GridUi.BG_DEEP);
        g.pose().popPose();

        // Тег «Военно-политический сервер»
        String tag = "Военно-политический сервер";
        int tw = font.width(GridUi.styled(tag));
        int ty = baseY + boxH + 4;
        int tagX = cx - tw / 2 - 16;
        int tagW = tw + 32;
        g.fill(tagX, ty - 2, tagX + tagW, ty + 9, GridUi.ACCENT_DIM);
        // border top & bottom
        g.fill(tagX, ty - 2, tagX + tagW, ty - 1, GridUi.ACCENT_BORDER);
        g.fill(tagX, ty + 9, tagX + tagW, ty + 10, GridUi.ACCENT_BORDER);
        g.drawString(font, GridUi.styled(tag), cx - tw / 2, ty, GridUi.ACCENT, false);
    }

    /* ── ПРАВАЯ КОЛОНКА ── */
    private void renderRightColumn(GuiGraphics g) {
        int p = pad();
        int rw = Math.min(280, width * 21 / 100);
        int rx = width - p - rw;

        int shown = news == null ? 0 : Math.min(4, news.size());
        int statusH = 100;
        int newsH = 30 + shown * 38;
        if (shown == 0) newsH = 30 + 24;
        int total = statusH + 10 + newsH;
        int ry = Math.max(74, (height - total) / 2);
        if (ry + total > height - 90) ry = height - 90 - total;

        renderStatusPanel(g, rx, ry, rw, statusH);
        renderNewsPanel(g, rx, ry + statusH + 10, rw, newsH);
    }

    private void renderStatusPanel(GuiGraphics g, int x, int y, int w, int h) {
        GridUi.card(g, x, y, w, h);
        g.drawString(font, GridUi.styled("СТАТУС СЕРВЕРА"), x + 18, y + 18, GridUi.TEXT_MUTED, false);

        int state = ServerStatusManager.getState();
        boolean online = state == ServerStatusManager.ONLINE;
        int dot = online ? GridUi.ACCENT : 0xFF616A64;
        String label = online ? "Сервер работает" : state == ServerStatusManager.OFFLINE ? "Сервер недоступен" : "Проверка...";
        int labelColor = online ? GridUi.ACCENT : state == ServerStatusManager.OFFLINE ? 0xFFE06666 : 0xFF9AA5A0;

        int dx = x + 18;
        int dy = y + 32;
        g.fill(dx, dy, dx + 6, dy + 6, dot);
        g.drawString(font, GridUi.styled(label), dx + 12, dy - 2, labelColor, false);

        if (online) {
            int value = ServerStatusManager.getOnline();
            g.pose().pushPose();
            g.pose().translate(dx, dy + 18, 0.0F);
            g.pose().scale(2.0F, 2.0F, 1.0F);
            g.drawString(font, GridUi.styled(String.valueOf(value)), 0, 0, GridUi.TEXT_MAIN, false);
            g.pose().popPose();
            g.drawString(font, GridUi.styled("игрока"), dx + 18, dy + 24, GridUi.TEXT_MUTED, false);

            int barY = dy + 38;
            g.fill(dx, barY, x + w - 18, barY + 3, GridUi.LINE_COLOR);
            int max = ServerStatusManager.getMax();
            if (max > 0) {
                int fillW = (int) ((x + w - 18 - dx) * Math.min(1f, (float) value / max));
                g.fill(dx, barY, dx + fillW, barY + 3, GridUi.ACCENT);
            }
        } else {
            g.drawString(font, GridUi.styled("—"), dx, dy + 16, GridUi.TEXT_MAIN, false);
        }
    }

    private void renderNewsPanel(GuiGraphics g, int x, int y, int w, int h) {
        GridUi.card(g, x, y, w, h);
        g.drawString(font, GridUi.styled("НОВОСТИ"), x + 18, y + 18, GridUi.TEXT_MUTED, false);

        if (news == null) {
            g.drawString(font, GridUi.styled("Загрузка..."), x + 18, y + 34, GridUi.TEXT_DIM, false);
            return;
        }

        int iy = y + 34;
        int shown = 0;
        for (JsonElement element : news) {
            if (shown >= 4) break;
            JsonObject item = element.getAsJsonObject();
            String title = item.has("title") ? item.get("title").getAsString() : "";
            String date = item.has("publishedAt") ? item.get("publishedAt").getAsString() : "";
            if (date.length() > 10) date = date.substring(0, 10);
            g.drawString(font, GridUi.styled(date), x + 18, iy, GridUi.TEXT_DIM, false);
            String clipped = font.plainSubstrByWidth(title, w - 36);
            g.drawString(font, GridUi.styled(clipped), x + 18, iy + 10, GridUi.TEXT_MAIN, false);
            g.fill(x + 18, iy + 28, x + w - 18, iy + 29, 0x1A344038);
            iy += 38;
            shown++;
        }
        if (shown == 0) {
            g.drawString(font, GridUi.styled("Новостей пока нет"), x + 18, y + 34, GridUi.TEXT_DIM, false);
        }
    }

    private void renderVersion(GuiGraphics g) {
        g.drawString(font, GridUi.styled("Minecraft 1.21.1 · NeoForge · GRID v0.1.0"),
                pad(), height - 14, GridUi.TEXT_DIM, false);
    }

    /* ── Действия ── */
    private void connectToServer() {
        try {
            ServerData data = new ServerData("GRID", MAIN_IP, ServerData.Type.OTHER);
            ConnectScreen.startConnecting(this, minecraft, ServerAddress.parseString(MAIN_IP), data, false, null);
        } catch (Throwable e) {
            minecraft.setScreen(new TitleScreen());
        }
    }

    private void openLink(String url) {
        try { Util.getPlatform().openUri(url); } catch (Throwable e) { /* ignore */ }
    }

    /* ── Утилиты ── */
    private static void roundedFill(GuiGraphics g, int x, int y, int w, int h, int radius, int color) {
        GridUi.roundedRect(g, x, y, w, h, radius, color);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    @Override
    public boolean isPauseScreen() { return false; }

    /* ════════════════════════════
       СОЦИАЛЬНАЯ КНОПКА (круглая)
       ════════════════════════════ */
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
            int x = getX(), y = getY(), s = width;
            g.blit(hover ? hovered : normal, x, y, 0.0F, 0.0F, s, s, s, s);
        }
    }

    /* ════════════════════════════
       КНОПКА МЕНЮ
       ════════════════════════════ */
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

            int bg, line, text, sub, iconColor;
            switch (type) {
                case PRIMARY -> {
                    bg = hover ? GridUi.ACCENT_HOVER : GridUi.ACCENT;
                    line = GridUi.ACCENT;
                    text = GridUi.BG_DEEP;
                    sub = 0x990B0F0C;
                    iconColor = GridUi.BG_DEEP;
                }
                case SECONDARY -> {
                    bg = hover ? 0xD9121814 : 0xBF0C100E;
                    line = hover ? 0x4068C284 : GridUi.LINE_COLOR;
                    text = GridUi.TEXT_MAIN;
                    sub = GridUi.TEXT_MUTED;
                    iconColor = GridUi.ACCENT;
                }
                case SMALL_EXIT -> {
                    bg = hover ? 0xCC121814 : 0xA60C100E;
                    line = hover ? 0x4DDC5050 : GridUi.LINE_COLOR;
                    text = hover ? GridUi.TEXT_MAIN : GridUi.TEXT_MUTED;
                    sub = GridUi.TEXT_MUTED;
                    iconColor = hover ? 0xFFE05555 : GridUi.TEXT_MUTED;
                }
                default -> { // SMALL
                    bg = hover ? 0xCC121814 : 0xA60C100E;
                    line = hover ? 0x4068C284 : GridUi.LINE_COLOR;
                    text = hover ? GridUi.TEXT_MAIN : GridUi.TEXT_MUTED;
                    sub = GridUi.TEXT_MUTED;
                    iconColor = hover ? GridUi.ACCENT : GridUi.TEXT_MUTED;
                }
            }

            int radius = switch (type) {
                case PRIMARY -> 12;
                case SECONDARY -> 10;
                default -> 10;
            };

            GridUi.roundedRect(g, x, y, w, h, radius, line);
            GridUi.roundedRect(g, x + 1, y + 1, w - 2, h - 2, Math.max(1, radius - 1), bg);

            if (type == Type.SMALL || type == Type.SMALL_EXIT) {
                int iconSize = 14;
                String title = getMessage().getString();
                int textW = fnt.width(getMessage());
                int total = iconSize + 8 + textW;
                int start = x + (w - total) / 2;
                drawIcon(g, icon, start + iconSize / 2, y + h / 2, iconSize, iconColor);
                g.drawString(fnt, getMessage(), start + iconSize + 8, y + (h - 8) / 2, text, false);
            } else {
                int pd = Math.max(16, w * 5 / 100);
                int iconSize = 22;
                int iconY = y + h / 2;
                drawIcon(g, icon, x + pd + iconSize / 2, iconY, iconSize, iconColor);
                int textX = x + pd + iconSize + 16;
                g.drawString(fnt, getMessage(), textX, y + h / 2 - 9, text, false);
                if (desc != null) {
                    g.drawString(fnt, GridUi.styled(desc), textX, y + h / 2 + 3, sub, false);
                }
            }
        }

        private static void drawIcon(GuiGraphics g, int icon, int cx, int cy, int size, int color) {
            switch (icon) {
                case ICON_PLAY -> UiIcons.play(g, cx, cy, size, color);
                case ICON_CHECK -> UiIcons.check(g, cx, cy, size, color);
                case ICON_SLIDERS -> UiIcons.sliders(g, cx, cy, size, color);
                case ICON_INFO -> UiIcons.info(g, cx, cy, size, color);
                case ICON_BAG -> UiIcons.bag(g, cx, cy, size, color);
                case ICON_EXIT -> UiIcons.exit(g, cx, cy, size, color);
                default -> {}
            }
        }
    }
}
