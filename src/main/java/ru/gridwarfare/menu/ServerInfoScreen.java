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
import net.minecraft.resources.ResourceLocation;

public final class ServerInfoScreen extends Screen {
    private static final ResourceLocation T_BG = ResourceLocation.fromNamespaceAndPath("gridmenu", "textures/gui/ui/bg_menu.png");
    private static final ResourceLocation T_PANEL = ResourceLocation.fromNamespaceAndPath("gridmenu", "textures/gui/ui/panel_info.png");

    private static final String IP = "grid-server.ru";
    private static final String TG_URL = "https://t.me/gridwarfare";
    private static final String DC_URL = "https://discord.gg/gridwarfare";
    private static final String WEB_URL = "https://grid-server.ru";
    private static final String[] DESCRIPTION = {
            "GRID — мир для совместной игры,",
            "развития, исследований",
            "и регулярных событий."
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
        int py = Math.max(height / 2 - 210, 30);

        addRenderableWidget(textButton(cx - 261, py + 356, 170, "КОПИРОВАТЬ IP", b -> copyIp()));
        addRenderableWidget(textButton(cx - 85, py + 356, 170, "ИГРАТЬ", b -> connect()));
        addRenderableWidget(textButton(cx + 91, py + 356, 170, "НАЗАД", b -> minecraft.setScreen(parent)));

        int sy = Math.max(height / 2 + 30, 270);
        addRenderableWidget(textButton(cx - 172, sy, 110, "Telegram", b -> openLink(TG_URL)));
        addRenderableWidget(textButton(cx - 55, sy, 110, "Discord", b -> openLink(DC_URL)));
        addRenderableWidget(textButton(cx + 62, sy, 110, "Сайт", b -> openLink(WEB_URL)));
    }

    private static Button textButton(int x, int y, int w, String label, Button.OnPress press) {
        return Button.builder(Component.literal(label), press).bounds(x, y, w, 20).build();
    }

    private void copyIp() {
        minecraft.keyboardHandler.setClipboard(IP);
    }

    private void connect() {
        try {
            ServerData data = new ServerData("GRID", IP, ServerData.Type.OTHER);
            ConnectScreen.startConnecting(this, minecraft, ServerAddress.parseString(IP), data, false, null);
        } catch (Throwable error) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public void tick() {
        ServerStatusManager.tick();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int w = width, h = height;
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        graphics.blit(T_BG, 0, 0, w, h, 0, 0, 1920, 1080, 1920, 1080);
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
        graphics.fill(0, 0, w, h, 0xC80B140A);

        int cx = width / 2;
        int py = Math.max(height / 2 - 210, 30);
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        graphics.blit(T_PANEL, cx - 280, py, 560, 420, 0.0F, 0.0F, 1120, 840, 1120, 840);
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();

        graphics.drawCenteredString(font, "О СЕРВЕРЕ", cx, py + 28, 0xFFE8EEE6);
        graphics.fill(cx - 200, py + 48, cx + 200, py + 49, 0x605C7A4A);

        graphics.drawCenteredString(font, "GRID", cx, py + 66, 0xFF93D8CB);
        graphics.drawCenteredString(font, "Адрес: " + IP, cx, py + 96, 0xFFC9D4C6);

        String statusText;
        int statusColor;
        switch (ServerStatusManager.getState()) {
            case ServerStatusManager.ONLINE -> {
                String pingText = ServerStatusManager.getPing() > 0 ? " • Пинг: " + ServerStatusManager.getPing() + " мс" : "";
                statusText = "Онлайн: " + ServerStatusManager.getOnline() + "/" + ServerStatusManager.getMax() + pingText;
                statusColor = 0xFF66CC66;
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
        graphics.drawCenteredString(font, statusText, cx, py + 116, statusColor);

        int ty = py + 160;
        for (String line : DESCRIPTION) {
            graphics.drawCenteredString(font, line, cx, ty, 0xFFB9C4B6);
            ty += 14;
        }

        graphics.drawCenteredString(font, "Соцсети", cx, py + 220, 0xFF93A080);

        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, "GRID CLIENT • 1.21.1", cx, height - 26, 0x80647B5A);
    }

    private void openLink(String url) {
        try {
            Util.getPlatform().openUri(url);
        } catch (Throwable error) {
            System.out.println("[GRID] Не удалось открыть ссылку: " + url);
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void removed() {
        ServerStatusManager.stop();
    }
}
