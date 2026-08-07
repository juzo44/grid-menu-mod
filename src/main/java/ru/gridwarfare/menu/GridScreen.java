package ru.gridwarfare.menu;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;

/**
 * GRID Main Menu — renders via Java2D (GridRenderer) to a small BufferedImage (960x540),
 * then uploads as a texture and scales up at blit time. Optimised for weak PCs:
 * <ul>
 *   <li>Only re-renders when hover state or data actually changes</li>
 *   <li>Base render at 960x540 (75% fewer pixels than full-res)</li>
 *   <li>Background layer cached in GridRenderer</li>
 *   <li>GPU does the upscaling via bilinear filtering</li>
 * </ul>
 */
public final class GridScreen extends Screen {

    private static final String MAIN_IP = "grid-server.ru";
    private static final String TG_URL  = "https://t.me/gridwarfare";
    private static final String DC_URL  = "https://discord.gg/gridwarfare";
    private static final String WEB_URL = "https://grid-server.ru";

    private static final long NEWS_REFRESH_MS = 60_000L;
    private static final long AUTH_REFRESH_MS = 30_000L;

    private GridRenderer renderer;
    private int textureId = -1;
    private net.minecraft.client.renderer.texture.DynamicTexture dynTex;
    private boolean dirty = true;
    private int lastHovered = -1;
    private int lastServerState = -1;
    private int lastOnline = -1;
    private int lastWidth = -1;
    private int lastHeight = -1;

    public GridScreen() {
        super(Component.literal("GRID"));
    }

    /* ═══ INIT ═══ */
    @Override
    protected void init() {
        if (renderer == null) {
            renderer = new GridRenderer();
            renderer.init();
        }
        dirty = true;
        loadAuth();
        loadNews();
        ServerStatusManager.start();
    }

    /* ═══ TICK ═══ */
    @Override
    public void tick() {
        ServerStatusManager.tick();
        long now = Util.getMillis();
        if (now - newsFetchedAt > NEWS_REFRESH_MS) loadNews();
        if (now - authFetchedAt > AUTH_REFRESH_MS) loadAuth();

        if (renderer != null) {
            int st = ServerStatusManager.getState();
            int on = ServerStatusManager.getOnline();
            renderer.setServerStatus(st, on, ServerStatusManager.getMax());
            if (st != lastServerState || on != lastOnline) {
                lastServerState = st;
                lastOnline = on;
                dirty = true;
            }
        }
    }

    @Override
    public void removed() {
        ServerStatusManager.stop();
        if (dynTex != null) { dynTex.close(); dynTex = null; textureId = -1; }
    }

    /* ═══ RENDER ═══ */
    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        // Check hover change
        if (renderer != null) {
            int hovered = -1;
            for (int i = 0; i < renderer.buttons.size(); i++) {
                if (renderer.buttons.get(i).contains(mx, my)) { hovered = i; break; }
            }
            if (hovered != lastHovered) {
                lastHovered = hovered;
                dirty = true;
            }
        }

        // Check resize
        if (width != lastWidth || height != lastHeight) {
            lastWidth = width;
            lastHeight = height;
            if (renderer != null) renderer.onResize(width, height);
            dirty = true;
        }

        if (dirty && renderer != null) {
            rebuildTexture(mx, my);
            dirty = false;
        }

        // Draw texture scaled to full screen with bilinear filtering
        if (textureId >= 0) {
            com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0, textureId);
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
            // Enable bilinear filtering for smooth upscale
            com.mojang.blaze3d.systems.RenderSystem.setTextureFilter(
                    com.mojang.blaze3d.systems.RenderSystem.getDefaultMipmap(), true);
            blitScaled(g, 0, 0, width, height);
        }

        super.render(g, mx, my, pt);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {}

    @Override
    public boolean isPauseScreen() { return false; }

    /* ═══ BUILD TEXTURE FROM JAVA2D (at base 960x540) ═══ */
    private void rebuildTexture(int mx, int my) {
        BufferedImage img = renderer.render(width, height, mx, my);
        try {
            int tw = img.getWidth();
            int th = img.getHeight();
            net.minecraft.client.texture.NativeImage ni =
                    new net.minecraft.client.texture.NativeImage(tw, th, false);
            int[] pixels = img.getRGB(0, 0, tw, th, null, 0, tw);
            for (int i = 0; i < pixels.length; i++) {
                int a = (pixels[i] >> 24) & 0xFF;
                int r = (pixels[i] >> 16) & 0xFF;
                int gv = (pixels[i] >> 8) & 0xFF;
                int b = pixels[i] & 0xFF;
                ni.setPixelRGBA(i % tw, i / tw, (a << 24) | (b << 16) | (gv << 8) | r);
            }
            if (dynTex == null) {
                dynTex = new net.minecraft.client.renderer.texture.DynamicTexture(ni);
            } else {
                dynTex.setPixels(ni);
            }
            dynTex.upload();
            textureId = dynTex.getId();
        } catch (Exception ignored) {}
    }

    /** Blit texture stretched to w x h (GPU does bilinear upscale from 960x540). */
    private void blitScaled(GuiGraphics g, int x, int y, int w, int h) {
        com.mojang.blaze3d.systems.RenderSystem.setShader(
                net.minecraft.client.renderer.GameRenderer::getPositionTexColorShader);
        var builder = com.mojang.blaze3d.vertex.Tesselator.getInstance().begin(
                com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,
                com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_COLOR);
        builder.addVertex(x,     y + h, 0).setU(0, 1).setColor(255, 255, 255, 255);
        builder.addVertex(x + w, y + h, 0).setU(1, 1).setColor(255, 255, 255, 255);
        builder.addVertex(x + w, y,     0).setU(1, 0).setColor(255, 255, 255, 255);
        builder.addVertex(x,     y,     0).setU(0, 0).setColor(255, 255, 255, 255);
        com.mojang.blaze3d.vertex.BufferUploader.drawWithGlobalProgram(builder.buildOrThrow());
    }

    /* ═══ CLICK HANDLING ═══ */
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (renderer == null || button != 0) return super.mouseClicked(mx, my, button);
        for (GridRenderer.BtnRect r : renderer.buttons) {
            if (r.contains((int)mx, (int)my)) {
                handleAction(r.id);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private void handleAction(String id) {
        switch (id) {
            case "play"     -> connectToServer();
            case "single"   -> minecraft.setScreen(new SelectWorldScreen(this));
            case "settings" -> minecraft.setScreen(new OptionsScreen(this, minecraft.options));
            case "info"     -> minecraft.setScreen(new ServerInfoScreen(this));
            case "shop"     -> openShop();
            case "exit"     -> minecraft.stop();
            case "social_telegram" -> openLink(TG_URL);
            case "social_discord"  -> openLink(DC_URL);
            case "social_globe"    -> openLink(WEB_URL);
        }
    }

    /* ═══ API ═══ */
    private long newsFetchedAt;
    private long authFetchedAt;

    private void loadAuth() {
        authFetchedAt = Util.getMillis();
        new Thread(() -> {
            JsonObject result = GridApiClient.me();
            Minecraft.getInstance().execute(() -> {
                if (renderer != null) renderer.setAuth(result);
                dirty = true;
            });
        }, "GRID-auth").start();
    }

    private void loadNews() {
        newsFetchedAt = Util.getMillis();
        new Thread(() -> {
            JsonArray result = GridApiClient.news();
            Minecraft.getInstance().execute(() -> {
                if (renderer != null) renderer.setNews(result);
                newsFetchedAt = Util.getMillis();
                dirty = true;
            });
        }, "GRID-news").start();
    }

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
}
