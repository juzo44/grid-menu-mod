package ru.gridwarfare.menu;

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

public final class GridScreen extends Screen {
    private static final ResourceLocation T_BG = tex("ui/bg_menu");
    private static final ResourceLocation T_EMBLEM = tex("ui/logo_grid_v2");
    private static final ResourceLocation T_TG_N = tex("ui/circle_tg_n");
    private static final ResourceLocation T_TG_H = tex("ui/circle_tg_h");
    private static final ResourceLocation T_DC_N = tex("ui/circle_dc_n");
    private static final ResourceLocation T_DC_H = tex("ui/circle_dc_h");
    private static final ResourceLocation T_GL_N = tex("ui/circle_gl_n");
    private static final ResourceLocation T_GL_H = tex("ui/circle_gl_h");

    private static final String MAIN_IP = "grid-server.ru";
    private static final String TG_URL = "https://t.me/gridwarfare";
    private static final String DC_URL = "https://discord.gg/gridwarfare";
    private static final String WEB_URL = "https://grid-server.ru";

    private long openMillis = 0L;

    public GridScreen() {
        super(Component.literal("GRID"));
    }

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath("gridmenu", "textures/gui/" + name + ".png");
    }

    private static int playYFor(int height) {
        int emblemBottom = (int) (height * 0.07) + 96;
        int desired = (int) (height * 0.40);
        int low = emblemBottom + 12;
        int high = height - 220;
        return Math.max(low, Math.min(desired, high));
    }

    @Override
    protected void init() {
        openMillis = Util.getMillis();
        ServerStatusManager.start();
        int cx = width / 2;
        int playY = playYFor(height);

        int menuWidth = Math.min(460, width - 48);
        int menuX = cx - menuWidth / 2;
        addRenderableWidget(new MenuButton(menuX, playY, menuWidth, 58, "▶", "ОСНОВНОЙ СЕРВЕР", true, b -> connectToServer()));
        addRenderableWidget(new MenuButton(menuX, playY + 66, menuWidth, 58, "◆", "ОДИНОЧНЫЙ МИР", false, b -> minecraft.setScreen(new SelectWorldScreen(this))));
        int third = (menuWidth - 16) / 3;
        addRenderableWidget(new MenuButton(menuX, playY + 132, third, 40, "⚙", "НАСТРОЙКИ", false, b -> minecraft.setScreen(new OptionsScreen(this, minecraft.options))));
        addRenderableWidget(new MenuButton(menuX + third + 8, playY + 132, third, 40, "ⓘ", "О СЕРВЕРЕ", false, b -> minecraft.setScreen(new ServerInfoScreen(this))));
        addRenderableWidget(new MenuButton(menuX + (third + 8) * 2, playY + 132, third, 40, "×", "ВЫХОД", false, b -> minecraft.stop()));

        int cy = height - 54;
        addRenderableWidget(new UiButton(width - 152, cy, 42, 42, T_TG_N, T_TG_H, b -> openLink(TG_URL)));
        addRenderableWidget(new UiButton(width - 104, cy, 42, 42, T_DC_N, T_DC_H, b -> openLink(DC_URL)));
        addRenderableWidget(new UiButton(width - 56, cy, 42, 42, T_GL_N, T_GL_H, b -> openLink(WEB_URL)));
    }

    @Override
    public void tick() {
        ServerStatusManager.tick();
    }

    @Override
    public void removed() {
        ServerStatusManager.stop();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        blitBackground(graphics, T_BG, width, height);

        float time = (float) (Util.getMillis() - openMillis) / 1000.0F;
        int vh = height / 3;
        for (int i = 0; i < vh; i++) {
            float t = (float) i / vh;
            int alpha = Math.min((int) (t * 150), 150);
            graphics.fill(0, height - vh + i, width, height - vh + i + 1, (alpha << 24) | 0x000B140A);
        }

        int glow = (int) ((Math.sin(time * 1.6F) * 0.5F + 0.5F) * 16);
        for (int i = 0; i < 3; i++) {
            graphics.fill(0, i, width, i + 1, ((18 + glow) << 24) | 0x00000000);
        }
        for (int i = 0; i < 2; i++) {
            graphics.fill(0, height - 1 - i, width, height - i, ((14 + glow / 2) << 24) | 0x00000000);
        }

        int cx = width / 2;
        int logoY = (int) (height * 0.07);
        blitTex(graphics, T_EMBLEM, cx - 170, logoY, 340, 96);

        int playY = playYFor(height);
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(font, "GRID  CLIENT  •  1.21.1", cx, height - 26, 0x80647B5A);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    private static void blitBackground(GuiGraphics g, ResourceLocation tex, int w, int h) {
        int tw = 1920, th = 1080;
        float scale = Math.max((float) w / tw, (float) h / th);
        int srcW = Math.min(tw, (int) Math.ceil(w / scale));
        int srcH = Math.min(th, (int) Math.ceil(h / scale));
        int ox = (tw - srcW) / 2;
        int oy = (th - srcH) / 2;
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        g.blit(tex, 0, 0, w, h, ox, oy, srcW, srcH, tw, th);
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
    }

    private static void blitTex(GuiGraphics g, ResourceLocation tex, int x, int y, int w, int h) {
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        g.blit(tex, x, y, w, h, 0.0F, 0.0F, w * 2, h * 2, w * 2, h * 2);
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
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

    private static final class UiButton extends Button {
        private final ResourceLocation normal;
        private final ResourceLocation hover;

        UiButton(int x, int y, int w, int h, ResourceLocation normal, ResourceLocation hover, OnPress press) {
            super(x, y, w, h, Component.literal(""), press, DEFAULT_NARRATION);
            this.normal = normal;
            this.hover = hover;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            blitTex(g, isHovered() && active ? hover : normal, getX(), getY(), width, height);
        }
    }

    private static final class MenuButton extends Button {
        private final String icon;
        private final String title;
        private final boolean primary;

        MenuButton(int x, int y, int w, int h, String icon, String title, boolean primary, OnPress press) {
            super(x, y, w, h, Component.literal(title), press, DEFAULT_NARRATION);
            this.icon = icon;
            this.title = title;
            this.primary = primary;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            boolean hover = isHovered() && active;
            int background = primary
                ? (hover ? 0xC0476750 : 0xA936523F)
                : (hover ? 0xBC28352D : 0x98101915);
            int border = primary ? 0xE89BBCA0 : (hover ? 0xD67E9C83 : 0x90617365);

            if (hover && primary) {
                roundedRect(g, getX() - 3, getY() + 1, width + 6, height + 6, 12, 0x38364D3B);
            }

            roundedRect(g, getX() + 2, getY() + 4, width, height, 9, 0x70000000);
            roundedRect(g, getX(), getY(), width, height, 9, border);
            roundedRect(g, getX() + 1, getY() + 1, width - 2, height - 2, 8, background);

            var font = Minecraft.getInstance().font;
            int textColor = 0xFFF1F4EF;
            int iconColor = primary ? 0xFFB3D6B8 : 0xFF88AA8F;
            int iconX = getX() + 34;
            int titleY = getY() + 14;
            roundedRect(g, getX() + 17, getY() + 12, 34, 34, 17, primary ? 0x60344D3B : 0x60415B48);
            g.drawCenteredString(font, icon, iconX, getY() + (height - 8) / 2, iconColor);

            int titleWidth = font.width(title);
            g.drawString(font, title, getX() + 64, titleY, textColor, false);

            if (primary) {
                String subtitle = serverSubtitle();
                int subtitleColor = serverSubtitleColor();
                g.drawString(font, subtitle, getX() + 64, getY() + 31, subtitleColor, false);
            } else {
                g.drawString(font, title, getX() + 64 + titleWidth + 8, getY() + (height - 8) / 2, 0x7F9EAEA2, false);
            }
        }

        private static String serverSubtitle() {
            switch (ServerStatusManager.getState()) {
                case ServerStatusManager.ONLINE:
                    String pingText = ServerStatusManager.getPing() > 0 ? " · " + ServerStatusManager.getPing() + " мс" : "";
                    return "● Онлайн " + ServerStatusManager.getOnline() + "/" + ServerStatusManager.getMax() + pingText;
                case ServerStatusManager.OFFLINE:
                    return "○ Сервер недоступен";
                default:
                    return "◌ Проверка сервера...";
            }
        }

        private static int serverSubtitleColor() {
            switch (ServerStatusManager.getState()) {
                case ServerStatusManager.ONLINE:
                    return 0xFF7BD389;
                case ServerStatusManager.OFFLINE:
                    return 0xFFE06666;
                default:
                    return 0xFF9AA5A0;
            }
        }

        private static void roundedRect(GuiGraphics g, int x, int y, int w, int h, int radius, int color) {
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
    }
}
