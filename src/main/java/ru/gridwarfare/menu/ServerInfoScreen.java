package ru.gridwarfare.menu;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;

public final class ServerInfoScreen extends Screen {
    private static final String IP = "grid-server.ru";
    private static final String TG_URL = "https://t.me/gridwarfare";
    private static final String DC_URL = "https://discord.gg/gridwarfare";
    private static final String WEB_URL = "https://grid-server.ru";
    private static final String[] DESCRIPTION = {
            "GRID — военно-политический сервер,",
            "мир для совместной игры,",
            "развития и регулярных событий."
    };

    private final Screen parent;

    public ServerInfoScreen(Screen parent) {
        super(Component.literal("О сервере"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ServerStatusManager.requestPing();
        int cx = width / 2;
        int panelW = Math.min(560, width * 50 / 100);
        int panelH = Math.min(420, height * 70 / 100);
        int py = (height - panelH) / 2;

        int btnW = 170;
        int btnGap = 10;
        int totalBtnW = btnW * 3 + btnGap * 2;
        int btnStartX = cx - totalBtnW / 2;
        int btnY = py + panelH - 60;

        addRenderableWidget(styledButton(btnStartX, btnY, btnW, 40, "КОПИРОВАТЬ IP", b -> copyIp()));
        addRenderableWidget(styledButton(btnStartX + btnW + btnGap, btnY, btnW, 40, "ИГРАТЬ", b -> connect()));
        addRenderableWidget(styledButton(btnStartX + (btnW + btnGap) * 2, btnY, btnW, 40, "НАЗАД", b -> minecraft.setScreen(parent)));
    }

    private Button styledButton(int x, int y, int w, int h, String label, Button.OnPress press) {
        return Button.builder(GridUi.styled(label), press).bounds(x, y, w, h).build();
    }

    private void copyIp() {
        minecraft.keyboardHandler.setClipboard(IP);
    }

    private void connect() {
        try {
            ServerData data = new ServerData("GRID", IP, ServerData.Type.OTHER);
            ConnectScreen.startConnecting(this, minecraft, ServerAddress.parseString(IP), data, false, null);
        } catch (Throwable e) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public void tick() {
        ServerStatusManager.tick();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        GridUi.background(g, width, height);

        int panelW = Math.min(560, width * 50 / 100);
        int panelH = Math.min(420, height * 70 / 100);
        int cx = width / 2;
        int py = (height - panelH) / 2;
        int px = cx - panelW / 2;

        GridUi.panel(g, px, py, panelW, panelH, 10);

        g.drawCenteredString(font, GridUi.styled("О СЕРВЕРЕ"), cx, py + 24, GridUi.TEXT_MAIN);
        g.fill(px + 40, py + 42, px + panelW - 40, py + 43, GridUi.LINE_COLOR);

        g.drawCenteredString(font, GridUi.styled("GRID"), cx, py + 60, GridUi.ACCENT);
        g.drawCenteredString(font, GridUi.styled("Адрес: " + IP), cx, py + 86, GridUi.TEXT_MUTED);

        String statusText;
        int statusColor;
        switch (ServerStatusManager.getState()) {
            case ServerStatusManager.ONLINE -> {
                String pingText = ServerStatusManager.getPing() > 0
                        ? " · Пинг: " + ServerStatusManager.getPing() + " мс" : "";
                statusText = "Онлайн: " + ServerStatusManager.getOnline() + "/" + ServerStatusManager.getMax() + pingText;
                statusColor = GridUi.ACCENT;
            }
            case ServerStatusManager.OFFLINE -> {
                statusText = "Сервер недоступен";
                statusColor = 0xFFE06666;
            }
            default -> {
                statusText = "Проверка сервера...";
                statusColor = 0xFF9AA5A0;
            }
        }
        g.drawCenteredString(font, GridUi.styled(statusText), cx, py + 108, statusColor);

        int ty = py + 148;
        for (String line : DESCRIPTION) {
            g.drawCenteredString(font, GridUi.styled(line), cx, ty, GridUi.TEXT_MUTED);
            ty += 16;
        }

        super.render(g, mx, my, pt);
        g.drawCenteredString(font, GridUi.styled("Minecraft 1.21.1 · NeoForge · GRID v0.1.0"),
                cx, height - 14, GridUi.TEXT_DIM);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    @Override
    public void removed() {
        ServerStatusManager.stop();
    }
}