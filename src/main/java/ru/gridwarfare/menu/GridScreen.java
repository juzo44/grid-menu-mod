package ru.gridwarfare.menu;

import com.google.gson.JsonArray;
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
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * GRID Custom Main Menu.
 *
 * <p>All Java2D rendering happens on a <b>background thread</b> so that AWT never
 * runs on Minecraft’s render thread — this prevents the AWT / LWJGL-OpenGL
 * deadlock that made the game freeze on the previous version.</p>
 *
 * <p>Flow per frame:
 * <ol>
 *   <li>Consume any pending {@link Frame} from the background thread.</li>
 *   <li>Check hover changes → request re-render if needed.</li>
 *   <li>If a re-render is needed and none is in progress, fire a daemon thread.</li>
 *   <li>Draw the latest texture stretched to the full window (GPU bilinear upscale).</li>
 * </ol>
 */
public final class GridScreen extends Screen {

    private static final String MAIN_IP = "grid-server.ru";
    private static final String TG_URL  = "https://t.me/gridwarfare";
    private static final String DC_URL  = "https://discord.gg/gridwarfare";
    private static final String WEB_URL = "https://grid-server.ru";

    private static final long NEWS_REFRESH_MS = 60_000L;
    private static final long AUTH_REFRESH_MS = 30_000L;

    /* ════ renderer ════ */
    private GridRenderer renderer;
    private volatile boolean rendererReady;

    /* ════ texture ════ */
    private DynamicTexture dynTex;
    private int textureId = -1;

    /* ════ background-thread communication ════ */
    private volatile boolean needsRender = true;
    private volatile boolean isRendering  = false;
    private volatile boolean closed       = false;

    /** Immutable snapshot produced by the background thread, consumed by the render thread. */
    private static final class Frame {
        final NativeImage image;
        final List<GridRenderer.BtnRect> buttons;
        Frame(NativeImage image, List<GridRenderer.BtnRect> buttons) {
            this.image   = image;
            this.buttons = Collections.unmodifiableList(new ArrayList<>(buttons));
        }
    }

    private volatile Frame pendingFrame;

    /* ════ active button state (read-only from render/click threads) ════ */
    private List<GridRenderer.BtnRect> activeButtons = Collections.emptyList();
    private int lastHovered = -1;

    /* ════ server data change tracking ════ */
    private int lastServerState = -1;
    private int lastOnline     = -1;

    /* ════ API refresh timestamps ════ */
    private long newsFetchedAt;
    private long authFetchedAt;

    public GridScreen() {
        super(Component.literal("GRID"));
    }

    /* ════ INIT ════ */
    @Override
    protected void init() {
        newsFetchedAt = Util.getMillis();
        authFetchedAt = Util.getMillis();

        if (renderer == null) {
            renderer = new GridRenderer();
            /* Initialise renderer on a background thread — AWT class-loading
               can deadlock with LWJGL if it happens on the render thread. */
            Thread t = new Thread(() -> {
                try {
                    renderer.init();
                    rendererReady = true;
                    needsRender   = true;
                } catch (Throwable e) {
                    System.err.println("[GRID] Renderer init failed: " + e);
                }
            }, "GRID-init");
            t.setDaemon(true);
            t.start();
        }

        loadAuth();
        loadNews();
        ServerStatusManager.start();
    }

    /* ════ TICK ════ */
    @Override
    public void tick() {
        ServerStatusManager.tick();

        long now = Util.getMillis();
        if (now - newsFetchedAt > NEWS_REFRESH_MS) loadNews();
        if (now - authFetchedAt > AUTH_REFRESH_MS) loadAuth();

        if (rendererReady) {
            int st = ServerStatusManager.getState();
            int on = ServerStatusManager.getOnline();
            renderer.setServerStatus(st, on, ServerStatusManager.getMax());
            if (st != lastServerState || on != lastOnline) {
                lastServerState = st;
                lastOnline     = on;
                needsRender    = true;
            }
        }
    }

    /* ════ CLEANUP ════ */
    @Override
    public void removed() {
        closed = true;
        ServerStatusManager.stop();
        if (dynTex != null) { dynTex.close(); dynTex = null; textureId = -1; }
        /* Close any frame that the render thread hasn’t consumed yet. */
        Frame f = pendingFrame;
        if (f != null) { if (f.image != null) f.image.close(); pendingFrame = null; }
    }

    /* ════ RENDER (runs every frame on the render thread) ════ */
    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        /* 1. Consume the latest completed frame from the background thread. */
        Frame frame = pendingFrame;
        if (frame != null) {
            pendingFrame = null;
            uploadTexture(frame.image);
            activeButtons = frame.buttons;
        }

        /* 2. Hover detection → request re-render when hover changes. */
        int hovered = -1;
        for (int i = 0; i < activeButtons.size(); i++) {
            if (activeButtons.get(i).contains(mx, my)) { hovered = i; break; }
        }
        if (hovered != lastHovered) {
            lastHovered = hovered;
            needsRender = true;
        }

        /* 3. Kick off a background render if needed. */
        if (needsRender && !isRendering && rendererReady && !closed) {
            needsRender = false;
            isRendering  = true;
            final int fmx = mx, fmy = my, fw = width, fh = height;
            Thread rt = new Thread(() -> {
                try {
                    if (closed) return;
                    BufferedImage img  = renderer.render(fw, fh, fmx, fmy);
                    NativeImage   ni   = toNativeImage(img);
                    List<GridRenderer.BtnRect> btns = new ArrayList<>(renderer.buttons);
                    if (!closed) pendingFrame = new Frame(ni, btns);
                } catch (Throwable e) {
                    System.err.println("[GRID] Render error: " + e);
                } finally {
                    isRendering = false;
                }
            }, "GRID-render");
            rt.setDaemon(true);
            rt.start();
        }

        /* 4. Blit the texture stretched to the full window (GPU does bilinear upscale). */
        if (textureId >= 0) {
            blitFullscreen(g, 0, 0, width, height);
        }

        super.render(g, mx, my, pt);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {}

    @Override
    public boolean isPauseScreen() { return false; }

    /* ════ TEXTURE UPLOAD (render thread only) ════ */
    private void uploadTexture(NativeImage ni) {
        try {
            if (dynTex == null) {
                dynTex = new DynamicTexture(ni);
            } else {
                dynTex.setPixels(ni);
            }
            dynTex.upload();
            textureId = dynTex.getId();
            /* Ensure linear filtering so the 960×540 texture upscales smoothly. */
            setLinearFilter();
        } catch (Throwable e) {
            System.err.println("[GRID] Texture upload error: " + e);
        }
    }

    private void setLinearFilter() {
        try {
            RenderSystem.setShaderTexture(0, textureId);
            /* GL_LINEAR = 0x2601 */
            org.lwjgl.opengl.GL11.glTexParameteri(
                    org.lwjgl.opengl.GL11.GL_TEXTURE_2D,
                    org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER,
                    org.lwjgl.opengl.GL11.GL_LINEAR);
            org.lwjgl.opengl.GL11.glTexParameteri(
                    org.lwjgl.opengl.GL11.GL_TEXTURE_2D,
                    org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER,
                    org.lwjgl.opengl.GL11.GL_LINEAR);
        } catch (Throwable ignored) {}
    }

    /* ════ FULLSCREEN BLIT ════ */
    private void blitFullscreen(GuiGraphics g, int x, int y, int w, int h) {
        RenderSystem.setShaderTexture(0, textureId);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);

        var builder = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_TEX_COLOR);
        builder.addVertex(x,     y + h, 0).setU(0, 1).setColor(255, 255, 255, 255);
        builder.addVertex(x + w, y + h, 0).setU(1, 1).setColor(255, 255, 255, 255);
        builder.addVertex(x + w, y,     0).setU(1, 0).setColor(255, 255, 255, 255);
        builder.addVertex(x,     y,     0).setU(0, 0).setColor(255, 255, 255, 255);
        BufferUploader.drawWithGlobalProgram(builder.buildOrThrow());
    }

    /* ════ PIXEL CONVERSION (background thread) ════ */
    private static NativeImage toNativeImage(BufferedImage img) throws Exception {
        int tw = img.getWidth(), th = img.getHeight();
        NativeImage ni = new NativeImage(tw, th, false);
        int[] px = img.getRGB(0, 0, tw, th, null, 0, tw);
        for (int i = 0; i < px.length; i++) {
            int a  = (px[i] >> 24) & 0xFF;
            int r  = (px[i] >> 16) & 0xFF;
            int gv = (px[i] >>  8) & 0xFF;
            int b  =  px[i]        & 0xFF;
            ni.setPixelRGBA(i % tw, i / tw, (a << 24) | (b << 16) | (gv << 8) | r);
        }
        return ni;
    }

    /* ════ CLICK HANDLING ════ */
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);
        for (GridRenderer.BtnRect r : activeButtons) {
            if (r.contains((int) mx, (int) my)) {
                handleAction(r.id);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private void handleAction(String id) {
        switch (id) {
            case "play"              -> connectToServer();
            case "single"            -> minecraft.setScreen(new SelectWorldScreen(this));
            case "settings"          -> minecraft.setScreen(new OptionsScreen(this, minecraft.options));
            case "info"              -> minecraft.setScreen(new ServerInfoScreen(this));
            case "shop"              -> openShop();
            case "exit"              -> minecraft.stop();
            case "social_telegram"   -> openLink(TG_URL);
            case "social_discord"    -> openLink(DC_URL);
            case "social_globe"      -> openLink(WEB_URL);
        }
    }

    /* ════ API CALLS ════ */
    private void loadAuth() {
        authFetchedAt = Util.getMillis();
        Thread t = new Thread(() -> {
            JsonObject result = GridApiClient.me();
            Minecraft.getInstance().execute(() -> {
                if (renderer != null) renderer.setAuth(result);
                needsRender = true;
            });
        }, "GRID-auth");
        t.setDaemon(true);
        t.start();
    }

    private void loadNews() {
        newsFetchedAt = Util.getMillis();
        Thread t = new Thread(() -> {
            JsonArray result = GridApiClient.news();
            Minecraft.getInstance().execute(() -> {
                if (renderer != null) renderer.setNews(result);
                newsFetchedAt = Util.getMillis();
                needsRender = true;
            });
        }, "GRID-news");
        t.setDaemon(true);
        t.start();
    }

    /* ════ NAVIGATION HELPERS ════ */
    private void connectToServer() {
        try {
            ServerData data = new ServerData("GRID", MAIN_IP, ServerData.Type.OTHER);
            ConnectScreen.startConnecting(this, minecraft,
                    ServerAddress.parseString(MAIN_IP), data, false, null);
        } catch (Throwable e) { minecraft.setScreen(new TitleScreen()); }
    }

    private static void openLink(String url) {
        try { Util.getPlatform().openUri(url); } catch (Throwable ignored) {}
    }

    private void openShop() {
        try {
            Class<?> cls  = Class.forName("ru.gridwarfare.shop.GridShopScreen");
            Object   inst = cls.getDeclaredConstructor().newInstance();
            if (inst instanceof Screen s) minecraft.setScreen(s);
        } catch (Throwable e) { minecraft.setScreen(new ServerInfoScreen(this)); }
    }
}
