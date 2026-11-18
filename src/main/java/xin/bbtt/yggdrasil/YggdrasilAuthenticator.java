/*
 * Copyright (C) 2026 liu15210520737
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package xin.bbtt.yggdrasil;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

/**
 * Authenticates against a third-party Yggdrasil authentication server
 * (外置登录, e.g. LittleSkin), implementing the standard Yggdrasil API:
 * <ul>
 *     <li>{@code POST {authServer}/authserver/authenticate}</li>
 *     <li>{@code POST {authServer}/authserver/refresh}</li>
 *     <li>{@code POST {authServer}/authserver/validate}</li>
 * </ul>
 *
 * <p>Tokens are kept in the {@link AuthManager}: a still-valid stored token is
 * reused, an expired one is refreshed, and only otherwise is the password used
 * to log in again.</p>
 */
public class YggdrasilAuthenticator {

    public record YggdrasilSession(String accessToken, String clientToken, UUID uuid, String name) {
    }

    private static final Logger log = LoggerFactory.getLogger("YggdrasilLogin");
    private static final Gson gson = new Gson();
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private YggdrasilAuthenticator() {
    }

    /**
     * Returns a usable session, reusing / refreshing the stored token when
     * possible and falling back to a fresh password login.
     */
    public static YggdrasilSession ensureSession(AuthManager authManager) throws IOException, InterruptedException {
        String base = normalizeBaseUrl(authManager.getAuthServer());

        YggdrasilSession stored = authManager.getStoredSession();
        if (stored != null) {
            if (stored.uuid() != null && validate(base, stored)) {
                return stored;
            }
            log.info("Stored Yggdrasil token expired, refreshing...");
            YggdrasilSession refreshed = refresh(base, stored);
            authManager.saveSession(refreshed);
            return refreshed;
        }

        log.info("Logging in to Yggdrasil auth server: {}", base);

        JsonObject agent = new JsonObject();
        agent.addProperty("name", "Minecraft");
        agent.addProperty("version", 1);

        JsonObject payload = new JsonObject();
        payload.add("agent", agent);
        payload.addProperty("username", authManager.getUsername());
        payload.addProperty("password", authManager.getPassword());
        payload.addProperty("clientToken", UUID.randomUUID().toString());
        payload.addProperty("requestUser", false);

        JsonObject response = post(base + "/authserver/authenticate", payload);

        JsonObject selected = response.has("selectedProfile") && !response.get("selectedProfile").isJsonNull()
                ? response.getAsJsonObject("selectedProfile")
                : null;
        if (selected == null && response.has("availableProfiles")
                && response.getAsJsonArray("availableProfiles").size() > 0) {
            selected = response.getAsJsonArray("availableProfiles").get(0).getAsJsonObject();
        }
        if (selected == null) {
            throw new IOException("Yggdrasil account has no Minecraft profile");
        }

        YggdrasilSession session = new YggdrasilSession(
                response.get("accessToken").getAsString(),
                response.get("clientToken").getAsString(),
                parseUuid(selected.get("id").getAsString()),
                selected.get("name").getAsString()
        );
        authManager.saveSession(session);
        log.info("Yggdrasil login successful: {}", session.name());
        return session;
    }

    private static boolean validate(String base, YggdrasilSession session) {
        JsonObject payload = new JsonObject();
        payload.addProperty("accessToken", session.accessToken());
        payload.addProperty("clientToken", session.clientToken());
        try {
            post(base + "/authserver/validate", payload);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (IOException e) {
            return false;
        }
    }

    private static YggdrasilSession refresh(String base, YggdrasilSession session)
            throws IOException, InterruptedException {
        JsonObject payload = new JsonObject();
        payload.addProperty("accessToken", session.accessToken());
        payload.addProperty("clientToken", session.clientToken());

        JsonObject response = post(base + "/authserver/refresh", payload);

        JsonObject selected = response.has("selectedProfile") && !response.get("selectedProfile").isJsonNull()
                ? response.getAsJsonObject("selectedProfile")
                : null;
        UUID uuid = selected != null ? parseUuid(selected.get("id").getAsString()) : session.uuid();
        String name = selected != null ? selected.get("name").getAsString() : session.name();

        return new YggdrasilSession(
                response.get("accessToken").getAsString(),
                response.get("clientToken").getAsString(),
                uuid,
                name
        );
    }

    private static JsonObject post(String url, JsonObject payload) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(payload), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String error = "HTTP " + response.statusCode();
            try {
                JsonObject body = gson.fromJson(response.body(), JsonObject.class);
                if (body != null && body.has("errorMessage")) {
                    error = body.get("errorMessage").getAsString();
                } else if (body != null && body.has("error")) {
                    error = body.get("error").getAsString() + ": " + body.get("errorMessage").getAsString();
                }
            } catch (Exception ignored) {
            }
            throw new IOException(error);
        }

        if (response.body() == null || response.body().isBlank()) {
            return new JsonObject();
        }
        return gson.fromJson(response.body(), JsonObject.class);
    }

    private static UUID parseUuid(String id) {
        String undashed = id.replace("-", "");
        return new UUID(
                Long.parseUnsignedLong(undashed.substring(0, 16), 16),
                Long.parseUnsignedLong(undashed.substring(16), 16)
        );
    }

    private static String normalizeBaseUrl(String authServer) {
        String base = authServer.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }
}
