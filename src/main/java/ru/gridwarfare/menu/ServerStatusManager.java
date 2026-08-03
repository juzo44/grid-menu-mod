package ru.gridwarfare.menu;

import net.minecraft.Util;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerStatusPinger;

public final class ServerStatusManager {
    public static final int CHECKING = 0;
    public static final int ONLINE = 1;
    public static final int OFFLINE = 2;

    private static final String ADDRESS = "grid-server.ru";
    private static final long PING_TIMEOUT_MS = 6000L;
    private static final long REFRESH_INTERVAL_MS = 30000L;

    private static final ServerStatusPinger PINGER = new ServerStatusPinger();

    private static volatile int state = CHECKING;
    private static volatile int online = 0;
    private static volatile int max = 0;
    private static volatile long ping = -1L;
    private static volatile long lastPingStart = 0L;
    private static volatile long lastPingEnd = -REFRESH_INTERVAL_MS;
    private static boolean started = false;

    private ServerStatusManager() {
    }

    public static int getState() {
        return state;
    }

    public static int getOnline() {
        return online;
    }

    public static int getMax() {
        return max;
    }

    public static long getPing() {
        return ping;
    }

    public static void start() {
        if (started) {
            return;
        }
        started = true;
        requestPing();
    }

    public static void requestPing() {
        state = CHECKING;
        lastPingStart = Util.getMillis();
        new Thread(ServerStatusManager::doPing, "GRID-status-ping").start();
    }

    private static void doPing() {
        try {
            ServerData data = new ServerData("GRID", ADDRESS, ServerData.Type.OTHER);
            PINGER.pingServer(data, () -> {
            }, () -> onPong(data));
        } catch (Throwable error) {
            markOffline();
        }
    }

    private static void onPong(ServerData data) {
        state = ONLINE;
        online = data.players != null ? data.players.online() : 0;
        max = data.players != null ? data.players.max() : 0;
        ping = data.ping;
        lastPingEnd = Util.getMillis();
    }

    private static void markOffline() {
        state = OFFLINE;
        online = 0;
        max = 0;
        ping = -1L;
        lastPingEnd = Util.getMillis();
    }

    public static void tick() {
        PINGER.tick();
        long now = Util.getMillis();
        if (state == CHECKING && now - lastPingStart > PING_TIMEOUT_MS) {
            PINGER.removeAll();
            markOffline();
        }
        if (state != CHECKING && now - lastPingEnd > REFRESH_INTERVAL_MS) {
            requestPing();
        }
    }

    public static void stop() {
        PINGER.removeAll();
        started = false;
        state = CHECKING;
    }
}
