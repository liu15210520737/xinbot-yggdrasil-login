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
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * Loads {@code yggdrasil.conf} (authServer / username / password) from the
 * xinbot working directory and persists the issued Yggdrasil session
 * (access token + client token + profile) to {@code yggdrasil-session.json}
 * so reconnects can reuse or refresh it instead of re-entering the password.
 */
public class AuthManager {

    private static final Logger log = LoggerFactory.getLogger("YggdrasilLogin");
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private static final Path CONFIG_PATH = Paths.get("yggdrasil.conf");
    private static final Path SESSION_PATH = Paths.get("yggdrasil-session.json");

    private String authServer;
    private String username;
    private String password;
    private YggdrasilAuthenticator.YggdrasilSession session;

    public void loadConfig() throws IOException {
        if (!Files.exists(CONFIG_PATH)) {
            writeDefaultConfig();
            log.warn("Created default {} — please fill in authServer/username/password", CONFIG_PATH);
        }

        Config config = ConfigFactory.parseFile(CONFIG_PATH.toFile());
        this.authServer = config.hasPath("authServer") ? config.getString("authServer") : "";
        this.username = config.hasPath("username") ? config.getString("username") : "";
        this.password = config.hasPath("password") ? config.getString("password") : "";

        loadStoredSession();
    }

    private void writeDefaultConfig() throws IOException {
        String defaults = "# YggdrasilLogin plugin configuration\n"
                + "# Base URL of the third-party authentication server (外置登录), e.g.:\n"
                + "#   https://littleskin.cn/api/yggdrasil  (LittleSkin)\n"
                + "#   https://authserver.example.com      (generic authlib-injector)\n"
                + "authServer = \"\"\n"
                + "\n"
                + "# Account on that authentication server\n"
                + "username = \"\"\n"
                + "password = \"\"\n";
        Files.write(CONFIG_PATH, defaults.getBytes(StandardCharsets.UTF_8));
    }

    public boolean isConfigured() {
        return authServer != null && !authServer.isBlank()
                && username != null && !username.isBlank()
                && password != null && !password.isBlank();
    }

    private void loadStoredSession() {
        this.session = null;
        if (!Files.exists(SESSION_PATH)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(SESSION_PATH, StandardCharsets.UTF_8)) {
            JsonObject node = gson.fromJson(reader, JsonObject.class);
            if (node == null || !node.has("accessToken") || !node.has("clientToken")) {
                return;
            }
            UUID uuid = node.has("uuid") ? UUID.fromString(node.get("uuid").getAsString()) : null;
            String name = node.has("name") ? node.get("name").getAsString() : null;
            this.session = new YggdrasilAuthenticator.YggdrasilSession(
                    node.get("accessToken").getAsString(),
                    node.get("clientToken").getAsString(),
                    uuid,
                    name
            );
        } catch (Exception e) {
            log.warn("Ignoring unreadable stored session at {}", SESSION_PATH, e);
            this.session = null;
        }
    }

    public synchronized void saveSession(YggdrasilAuthenticator.YggdrasilSession session) {
        this.session = session;
        JsonObject node = new JsonObject();
        node.addProperty("accessToken", session.accessToken());
        node.addProperty("clientToken", session.clientToken());
        node.addProperty("uuid", session.uuid().toString());
        node.addProperty("name", session.name());
        try (Writer writer = Files.newBufferedWriter(SESSION_PATH, StandardCharsets.UTF_8)) {
            gson.toJson(node, writer);
        } catch (IOException e) {
            log.warn("Failed to persist session to {}", SESSION_PATH, e);
        }
    }

    public YggdrasilAuthenticator.YggdrasilSession getStoredSession() {
        return session;
    }

    public String getAuthServer() {
        return authServer;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }
}
