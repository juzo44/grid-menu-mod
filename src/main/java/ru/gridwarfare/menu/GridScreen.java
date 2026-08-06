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

    private static int pad() {
        return Math.max(24, Math.min(120, (int) (Minecraft.getInstance().getWindow().getGuiScaledWidth() * 0.08)));
    }

    @Override
    protected void init() {
        ServerStatusManager.start();
        int bigW = Math.min(380, width * 32 / 100);
        int bigX = (width - bigW) / 2;
        int playY = Math.max(150, (int) (height * 0.30));
        int singleY = playY + 56 + 8;
        int rowY = singleY + 48 + 12;

        addRenderableWidget(new MenuButton(bigX, playY, bigW, 56, ICON_PLAY, "ИГРАТЬ", "Подключение к серверу",
                MenuButton.Type.PRIMARY, b -> connectToServer()));
        addRenderableWidget(new MenuButton(bigX, singleY, bigW, 48, ICON_CHECK, "ОДИНОЧНЫЙ МИР", "Одиночная игра",
                MenuButton.Type.SECONDARY, b -> minecraft.setScreen(new SelectWorldScreen(this))));

        List<MenuButton> small = new ArrayList<>();
        small.add(new MenuButton(0, rowY, 0, 40, ICON_SLIDERS, "НАСТРОЙКИ", null,
                MenuButton.Type.SMALL, b -> minecraft.setScreen(new OptionsScreen(this, minecraft.options))));
        small.add(new MenuButton(0, rowY, 0, 40, ICON_INFO, "О СЕРВЕРЕ", null,
                MenuButton.Type.SMALL, b -> minecraft.setScreen(new ServerInfoScreen(this))));
        if (isShopAvailable()) {
            small.add(new MenuButton(0, rowY, 0, 40, ICON_BAG, "МАГАЗИН", null,
                    MenuButton.Type.SMALL, b -> openShop()));
        }
        small.add(new MenuButton(0, rowY, 0, 40, ICON_EXIT, "ВЫХОД", null,
                MenuButton.Type.SMALL, b -> minecraft.stop()));

        int gap = 8;
        int sw = (bigW - (small.size() - 1) * gap) / small.size();
        int sx = bigX;
        for (MenuButton button : small) {
            button.setX(sx);
            button.setWidth(sw);
            addRenderableWidget(button);
            sx += sw + gap;
        }

        int sy = height - 74;
        int right = width - pad();
        int base = right - 130;
        addRenderableWidget(new SocialIconButton(base, sy, 40, GridUi.ICON_TG, GridUi.ICON_TG_H, b -> openLink(TG_URL)));
        addRenderableWidget(new SocialIconButton(base + 50, sy, 40, GridUi.ICON_DC, GridUi.ICON_DC_H, b -> openLink(DC_URL)));
        addRenderableWidget(new SocialIconButton(base + 100, sy, 40, GridUi.ICON_GL, GridUi.ICON_GL_H, b -> openLink(WEB_URL)));

        loadAuth();
        loadNews();
    }

    @Override
    public void tick() {
        ServerStatusManager.tick();
        long now = Util.getMillis();
        if (now - newsFetchedAt > NEWS_REFRESH_MS) {
            loadNews();
        }
        if (now - authFetchedAt > AUTH_REFRESH_MS) {
            loadAuth();
        }
    }

    @Override
    public void removed() {
        ServerStatusManager.stop();
    }

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
        } catch (Throwable error) {
            return false;
        }
    }

    private void openShop() {
        try {
            Class<?> screenClass = Class.forName("ru.gridwarfare.shop.GridShopScreen");
            Object instance = screenClass.getDeclaredConstructor().newInstance();
            if (instance instanceof Screen screen) {
                minecraft.setScreen(screen);
            }
        } catch (Throwable error) {
            minecraft.setScreen(new ServerInfoScreen(this));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        GridUi.background(graphics, width, height);
        renderTopbar(graphics);
        renderTitle(graphics);
        renderRightColumn(graphics);
        renderVersion(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderTopbar(GuiGraphics g) {
        int h = 70;
        for (int y = 0; y < h; y++) {
            float t = (float) y / (h - 1);
            g.fill(0, y, width, y + 1, GridUi.lerpColor(0xEF080B0A, 0xC5000000, t));
        }
        g.fill(0, h - 1, width, h, 0xFF273129);

        int p = pad();
        GridUi.brandMark(g, p + 6, 19, 32);
        g.drawString(font, GridUi.styled("GRID"), p + 50, 28, 0xFFFFFFFF, false);
        renderAuthCard(g);
    }

    private void renderAuthCard(GuiGraphics g) {
        int right = width - pad();
        String nick = "ГОСТЬ";
        String rank = "";
        boolean authed = me != null;
        if (authed) {
            if (me.has("username")) {
                nick = me.get("username").getAsString().toUpperCase();
            }
            if (me.has("donate")) {
                rank = me.get("donate").getAsString().toUpperCase();
            }
            if (rank.isEmpty() && me.has("rank")) {
                rank = me.get("rank").getAsString().toUpperCase();
            }
        }

        int nickW = font.width(GridUi.styled(nick));
        int rankW = rank.isEmpty() ? 0 : font.width(GridUi.styled(rank));
        int line1 = 18 + nickW + (rank.isEmpty() ? 0 : 8 + rankW + 12) + 18;

        String balLabel = authed ? "Баланс: " : "Авторизация через лаунчер";
        String balValue = authed ? fmt(balance()) + " ₽" : "";
        int line2 = 18 + font.width(GridUi.styled(balLabel)) + (balValue.isEmpty() ? 0 : 4 + font.width(GridUi.styled(balValue))) + 18;

        int cardW = Math.max(line1, line2);
        int cardH = authed ? 46 : 34;
        int x = right - cardW;
        int y = (70 - cardH) / 2;
        GridUi.panel(g, x, y, cardW, cardH, 10);

        int tx = x + 18;
        int ty = y + 12;
        g.drawString(font, GridUi.styled(nick), tx, ty, 0xFFF3F6F3, false);
        if (!rank.isEmpty()) {
            g.fill(tx + nickW + 8, ty - 1, tx + nickW + 8 + rankW + 12, ty + 9, 0x2668C284);
            g.drawString(font, GridUi.styled(rank), tx + nickW + 14, ty, 0xFF68C284, false);
        }
        if (authed) {
            int lblW = font.width(GridUi.styled(balLabel));
            g.drawString(font, GridUi.styled(balLabel), tx, ty + 13, 0xFF8B978F, false);
            g.drawString(font, GridUi.styled(balValue), tx + lblW + 4, ty + 13, 0xFF68C284, false);
        } else {
            g.drawString(font, GridUi.styled(balLabel), tx, ty + 8, 0xFF8B978F, false);
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

    private void renderTitle(GuiGraphics g) {
        int bigW = Math.min(380, width * 32 / 100);
        int cx = width / 2;
        int playY = Math.max(150, (int) (height * 0.30));
        int baseY = Math.max(80, playY - 96);

        int boxW = 280;
        int boxH = 56;
        int x = cx - boxW / 2;
        roundedFill(g, x, baseY + 7, boxW, boxH, 10, 0xFF387A50);
        roundedFill(g, x, baseY + 4, boxW, boxH, 10, 0xFF4A9C66);
        roundedFill(g, x, baseY, boxW, boxH, 10, 0xFF68C284);

        g.pose().pushPose();
        g.pose().translate(cx, baseY + boxH / 2, 0.0F);
        g.pose().scale(3.0F, 3.0F, 1.0F);
        g.drawCenteredString(font, GridUi.styled("GRID"), 0, -4, 0xFF0B0F0C);
        g.pose().popPose();

        String tag = "Военно-политический сервер";
        int tw = font.width(GridUi.styled(tag));
        int ty = baseY + boxH + 14;
        g.fill(cx - tw / 2 - 14, ty - 2, cx + tw / 2 + 14, ty + 9, 0x2668C284);
        g.drawString(font, GridUi.styled(tag), cx - tw / 2, ty, 0xFF68C284, false);
    }

    private void renderRightColumn(GuiGraphics g) {
        int rw = Math.min(252, width * 21 / 100);
        int rx = width - pad() - rw;

        int shown = news == null ? 0 : Math.min(4, news.size());
        int statusH = 108;
        int newsH = 40 + shown * 38;
        if (shown == 0) {
            newsH = 40 + 34;
        }
        int total = statusH + 10 + newsH;
        int ry = Math.max(74, (height - total) / 2);
        if (ry + total > height - 90) {
            ry = height - 90 - total;
        }

        renderStatusPanel(g, rx, ry, rw, statusH);
        renderNewsPanel(g, rx, ry + statusH + 10, rw, newsH);
    }

    private void renderStatusPanel(GuiGraphics g, int x, int y, int w, int h) {
        GridUi.card(g, x, y, w, h);
        g.drawString(font, GridUi.styled("СТАТУС СЕРВЕРА"), x + 16, y + 12, 0xFF8B978F, false);

        int state = ServerStatusManager.getState();
        boolean online = state == ServerStatusManager.ONLINE;
        int dot = online ? 0xFF68C284 : 0xFF616A64;
        String label = online ? "Сервер работает" : state == ServerStatusManager.OFFLINE ? "Сервер недоступен" : "Проверка…";
        int labelColor = online ? 0xFF68C284 : state == ServerStatusManager.OFFLINE ? 0xFFE06666 : 0xFF9AA5A0;

        int dx = x + 16;
        int dy = y + 26;
        g.fill(dx, dy, dx + 6, dy + 6, dot);
        g.drawString(font, GridUi.styled(label), dx + 12, dy - 2, labelColor, false);

        if (online) {
            int value = ServerStatusManager.getOnline();
            g.pose().pushPose();
            g.pose().translate(dx, dy + 18, 0.0F);
            g.pose().scale(2.0F, 2.0F, 1.0F);
            g.drawString(font, GridUi.styled(String.valueOf(value)), 0, 0, 0xFFF3F6F3, false);
            g.pose().popPose();
            String players = "игрока";
            g.drawString(font, GridUi.styled(players), dx + 18, dy + 24, 0xFF8B978F, false);

            int barY = dy + 38;
            g.fill(dx, barY, x + w - 16, barY + 3, 0xFF344038);
            int max = ServerStatusManager.getMax();
            if (max > 0) {
                int fillW = (int) ((x + w - 16 - dx) * value / max);
                g.fill(dx, barY, dx + fillW, barY + 3, 0xFF68C284);
            }
        } else {
            g.drawString(font, GridUi.styled("—"), dx, dy + 16, 0xFFF3F6F3, false);
        }
    }

    private void renderNewsPanel(GuiGraphics g, int x, int y, int w, int h) {
        GridUi.card(g, x, y, w, h);
        g.drawString(font, GridUi.styled("НОВОСТИ"), x + 16, y + 12, 0xFF8B978F, false);

        if (news == null) {
            g.drawString(font, GridUi.styled("Загрузка…"), x + 16, y + 30, 0xFF5A655E, false);
            return;
        }

        int iy = y + 30;
        int shown = 0;
        for (JsonElement element : news) {
            if (shown >= 4) {
                break;
            }
            JsonObject item = element.getAsJsonObject();
            String title = item.has("title") ? item.get("title").getAsString() : "";
            String date = item.has("publishedAt") ? item.get("publishedAt").getAsString() : "";
            if (date.length() > 10) {
                date = date.substring(0, 10);
            }
            g.drawString(font, GridUi.styled(date), x + 16, iy, 0xFF5A655E, false);
            String clipped = font.plainSubstrByWidth(title, w - 32);
            g.drawString(font, GridUi.styled(clipped), x + 16, iy + 10, 0xFFF3F6F3, false);
            g.fill(x + 16, iy + 28, x + w - 16, iy + 29, 0x1A344038);
            iy += 38;
            shown++;
        }
        if (shown == 0) {
            g.drawString(font, GridUi.styled("Новостей пока нет"), x + 16, y + 30, 0xFF5A655E, false);
        }
    }

    private void renderVersion(GuiGraphics g) {
        g.drawString(font, GridUi.styled("Minecraft 1.21.1 · NeoForge · GRID v0.1.0"), pad(), height - 20, 0xFF5A655E, false);
    }

    private void connectToServer() {
        try {
            ServerData data = new ServerData("GRID", MAIN_IP, ServerData.Type.OTHER);
            ConnectScreen.startConnecting(this, minecraft, ServerAddress.parseString(MAIN_IP), data, false, null);
        } catch (Throwable error) {
            minecraft.setScreen(new TitleScreen());
        }
    }

    private void openLink(String url) {
        try {
            Util.getPlatform().openUri(url);
        } catch (Throwable error) {
            System.out.println("[GRID] Не удалось открыть ссылку: " + url);
        }
    }

    private static void roundedFill(GuiGraphics g, int x, int y, int w, int h, int radius, int color) {
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

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Кнопка-иконка соцсети (круглая, с вариантом наведения). */
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

    /** Кнопка в стиле макета меню: primary (зелёная), secondary или small с иконкой. */
    private static final class MenuButton extends Button {
        enum Type { PRIMARY, SECONDARY, SMALL }

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
            var font = Minecraft.getInstance().font;

            int bg;
            int line;
            int text;
            int sub;
            int iconColor;
            switch (type) {
                case PRIMARY -> {
                    bg = hover ? 0xFF7CD090 : 0xFF68C284;
                    line = 0xFF68C284;
                    text = 0xFF0B0F0C;
                    sub = 0x990B0F0C;
                    iconColor = 0xFF0B0F0C;
                }
                case SECONDARY -> {
                    bg = hover ? 0xFF121814 : 0xFF0C100E;
                    line = hover ? 0x5968C284 : 0xFF344038;
                    text = 0xFFF3F6F3;
                    sub = 0xFF8B978F;
                    iconColor = 0xFF68C284;
                }
                default -> {
                    bg = hover ? 0xFF121814 : 0xFF0C100E;
                    line = hover ? 0x5968C284 : 0xFF344038;
                    text = hover ? 0xFFF3F6F3 : 0xFF8B978F;
                    sub = 0xFF8B978F;
                    iconColor = hover ? 0xFF68C284 : 0xFF8B978F;
                }
            }

            int radius = Math.min(10, Math.min(w, h) / 2);
            roundedFill(g, x, y, w, h, radius, line);
            roundedFill(g, x + 1, y + 1, w - 2, h - 2, Math.max(4, radius - 1), bg);

            if (type == Type.SMALL) {
                int iconSize = 15;
                String title = getMessage().getString();
                int textW = font.width(getMessage());
                int total = iconSize + 6 + textW;
                int start = x + (w - total) / 2;
                drawIcon(g, icon, start + iconSize / 2, y + h / 2, iconSize, iconColor);
                g.drawString(font, getMessage(), start + iconSize + 6, y + (h - 8) / 2, text, false);
            } else {
                int pad = Math.max(14, w * 5 / 100);
                int iconSize = 22;
                int iconY = y + h / 2;
                drawIcon(g, icon, x + pad + iconSize / 2, iconY, iconSize, iconColor);
                int textX = x + pad + iconSize + 12;
                g.drawString(font, getMessage(), textX, y + h / 2 - 9, text, false);
                g.drawString(font, GridUi.styled(desc), textX, y + h / 2 + 3, sub, false);
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
                default -> {
                }
            }
        }
    }
}
