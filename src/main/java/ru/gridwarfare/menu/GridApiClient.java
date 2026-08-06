package ru.gridwarfare.menu;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Клиент API GRID. Адрес берётся из системного свойства {@code grid.apiBase}
 * (лаунчер передаёт -Dgrid.apiBase=...), по умолчанию — локальный dev-сервер.
 */
public final class GridApiClient {
    private static final String API_BASE = System.getProperty("grid.apiBase", "http://127.0.0.1:3000");
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private GridApiClient() {
    }

    /** Публичный профиль игрока по позывному. Возвращает null при любой ошибке. */
    public static JsonObject profile(String username) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(API_BASE + "/api/profile/" + URLEncoder.encode(username, StandardCharsets.UTF_8)))
                    .timeout(Duration.ofSeconds(6))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                return null;
            }
            JsonObject body = JsonParser.parseString(response.body()).getAsJsonObject();
            return body.has("profile") ? body.getAsJsonObject("profile") : null;
        } catch (Throwable error) {
            return null;
        }
    }

    /** Текущий профиль игрока по игровому токену лаунчера. Возвращает null при любой ошибке. */
    public static JsonObject me() {
        String token = gameToken();
        if (token == null) {
            return null;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(API_BASE + "/api/game/me"))
                    .timeout(Duration.ofSeconds(6))
                    .header("Accept", "application/json")
                    .header("X-Game-Token", token)
                    .GET()
                    .build();
            HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                return null;
            }
            JsonObject body = JsonParser.parseString(response.body()).getAsJsonObject();
            return body.has("user") ? body.getAsJsonObject("user") : null;
        } catch (Throwable error) {
            return null;
        }
    }

    /** Список новостей сервера. Возвращает null при ошибке. */
    public static JsonArray news() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(API_BASE + "/api/news"))
                    .timeout(Duration.ofSeconds(6))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                return null;
            }
            JsonObject body = JsonParser.parseString(response.body()).getAsJsonObject();
            return body.has("news") ? body.getAsJsonArray("news") : new JsonArray();
        } catch (Throwable error) {
            return null;
        }
    }

    private static String gameToken() {
        try {
            Path path = net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath().resolve("grid_token.txt");
            if (!Files.exists(path)) {
                return null;
            }
            String token = Files.readString(path).trim();
            return token.isEmpty() ? null : token;
        } catch (Throwable error) {
            return null;
        }
    }
}
