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

import org.geysermc.mcprotocollib.auth.GameProfile;
import org.geysermc.mcprotocollib.network.ClientSession;
import org.geysermc.mcprotocollib.protocol.MinecraftConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xin.bbtt.mcbot.Bot;
import xin.bbtt.mcbot.event.EventHandler;
import xin.bbtt.mcbot.event.Listener;
import xin.bbtt.mcbot.events.ConnectEvent;

/**
 * Injects the Yggdrasil session into the mcprotocollib session right before it
 * connects. mcprotocollib reads these flags when the server sends its encryption
 * request, so setting them here covers both the initial connection and every
 * automatic reconnect.
 *
 * <ul>
 *     <li>{@code PROFILE_KEY} — the GameProfile (UUID + name) from the auth server</li>
 *     <li>{@code ACCESS_TOKEN_KEY} — the Yggdrasil access token</li>
 *     <li>{@code SESSION_SERVICE_KEY} — a {@link YggdrasilSessionService} that
 *     reports the join to the third-party session server instead of Mojang's</li>
 * </ul>
 */
public class ConnectListener implements Listener {

    private static final Logger log = LoggerFactory.getLogger("YggdrasilLogin");

    private final AuthManager authManager;

    public ConnectListener(AuthManager authManager) {
        this.authManager = authManager;
    }

    @EventHandler
    public void onConnect(ConnectEvent event) {
        if (!authManager.isConfigured()) {
            return;
        }

        YggdrasilAuthenticator.YggdrasilSession session;
        try {
            session = YggdrasilAuthenticator.ensureSession(authManager);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Yggdrasil authentication interrupted, aborting connection injection", e);
            return;
        } catch (Exception e) {
            log.error("Yggdrasil authentication failed, aborting connection injection", e);
            return;
        }

        ClientSession clientSession = Bot.INSTANCE.getSession();
        if (clientSession == null) {
            log.error("No client session available at connect time, cannot inject Yggdrasil session");
            return;
        }

        GameProfile profile = new GameProfile(session.uuid(), session.name());
        clientSession.setFlag(MinecraftConstants.PROFILE_KEY, profile);
        clientSession.setFlag(MinecraftConstants.ACCESS_TOKEN_KEY, session.accessToken());
        clientSession.setFlag(MinecraftConstants.SESSION_SERVICE_KEY,
                new YggdrasilSessionService(authManager.getAuthServer()));
        log.info("Injected Yggdrasil session for {} into connection to {}",
                session.name(), clientSession.getRemoteAddress());
    }
}
