package ru.gridwarfare.menu;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class MyPurchasesScreen extends Screen {
    private static final int LOADING = 0;
    private static final int READY = 1;
    private static final int ERROR = 2;
    private static final int MAX_PURCHASES = 6;

    private final Screen parent;
    private volatile int state = LOADING;
    private volatile JsonObject profile;

    public MyPurchasesScreen(Screen parent) {
        super(Component.literal("Мои покупки"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int py = Math.max(height / 2 - 210, 30);

        addRenderableWidget(textButton(cx - 172, py + 356, 170, "ОБНОВИТЬ", b -> reload()));
        addRenderableWidget(textButton(cx + 2, py + 356, 170, "НАЗАД", b -> minecraft.setScreen(parent)));
    }

    private void reload() {
        String username = Minecraft.getInstance().getUser().getName();
        state = LOADING;
        new Thread(() -> {
            JsonObject loaded = GridApiClient.profile(username);
            profile = loaded;
            state = loaded != null ? READY : ERROR;
        }, "GRID-profile").start();
    }

    private static Button textButton(int x, int y, int w, String label, Button.OnPress press) {
        return Button.builder(Component.literal(label), press).bounds(x, y, w, 20).build();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int w = width, h = height;
        GridUi.background(graphics, w, h);

        int cx = width / 2;
        int py = Math.max(height / 2 - 210, 30);
        GridUi.panel(graphics, cx - 280, py, 560, 420, 8);

        graphics.drawCenteredString(font, "МОИ ПОКУПКИ", cx, py + 28, 0xFFE8EEE6);
        graphics.fill(cx - 200, py + 48, cx + 200, py + 49, 0x605C7A4A);

        String username = Minecraft.getInstance().getUser().getName();
        graphics.drawCenteredString(font, "ИГРОК: " + username, cx, py + 66, 0xFFC9D4C6);

        switch (state) {
            case LOADING -> graphics.drawCenteredString(font, "Загрузка данных...", cx, py + 110, 0xFF9AA5A0);
            case ERROR -> {
                graphics.drawCenteredString(font, "Сервер недоступен", cx, py + 110, 0xFFE06666);
                graphics.drawCenteredString(font, "Не удалось получить профиль", cx, py + 128, 0xFF9AA5A0);
            }
            case READY -> renderProfile(graphics, cx, py);
            default -> {
            }
        }

        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, "GRID CLIENT · 1.21.1", cx, height - 20, 0x505A6B52);
    }

    private void renderProfile(GuiGraphics graphics, int cx, int py) {
        String rank = stringField(profile, "rank");
        String donate = stringField(profile, "donate");
        int balance = intField(profile, "balance");

        String status = "НЕТ".equalsIgnoreCase(donate) ? "─" : donate;
        graphics.drawCenteredString(font, "Звание: " + rank, cx, py + 100, 0xFFB9C4B6);
        graphics.drawCenteredString(font, "Статус: " + status, cx, py + 118, "─".equals(status) ? 0xFF9AA5A0 : 0xFF66CC66);
        graphics.drawCenteredString(font, "Баланс G-COINS: " + balance, cx, py + 136, 0xFFB9C4B6);
        graphics.fill(cx - 200, py + 156, cx + 200, py + 157, 0x30FFFFFF);

        JsonArray purchases = profile.has("purchases") ? profile.getAsJsonArray("purchases") : new JsonArray();
        if (purchases.isEmpty()) {
            graphics.drawCenteredString(font, "Покупок пока нет", cx, py + 190, 0xFF9AA5A0);
            graphics.drawCenteredString(font, "Магазин доступен на сайте", cx, py + 208, 0xFF7B877C);
            return;
        }

        int ty = py + 176;
        int limit = Math.min(purchases.size(), MAX_PURCHASES);
        for (int i = 0; i < limit; i++) {
            JsonObject item = purchases.get(i).getAsJsonObject();
            String name = stringField(item, "name");
            int price = intField(item, "priceRub");
            String category = stringField(item, "category");
            String mark = "status".equals(category) ? "◆ " : "currency".equals(category) ? "● " : "✦ ";
            graphics.drawString(font, " " + mark + name, cx - 220, ty, 0xFFC6D2C5, false);
            graphics.drawString(font, price + " ₽", cx + 180 - font.width(price + " ₽") + 10, ty, 0xFF85A88E, false);
            ty += 14;
        }
        if (purchases.size() > limit) {
            graphics.drawCenteredString(font, "и ещё " + (purchases.size() - limit) + " позиция(и)", cx, ty, 0xFF7B877C);
        }
    }

    private static String stringField(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "─";
    }

    private static int intField(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsInt() : 0;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }
}
