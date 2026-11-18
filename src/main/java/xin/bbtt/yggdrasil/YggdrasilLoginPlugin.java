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
 * along with this program. If not, see <https://www.gnu.org/licenses/.
 */

package xin.bbtt.yggdrasil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xin.bbtt.mcbot.Bot;
import xin.bbtt.mcbot.plugin.Plugin;

/**
 * Xinbot third-party plugin: log in through a Yggdrasil-compatible
 * authentication server (外置登录, e.g. LittleSkin).
 *
 * <p>The plugin authenticates with the configured auth server and, on every
 * connection attempt (including reconnects), injects the resulting profile,
 * access token and a matching session service into the underlying
 * mcprotocollib session. No changes to the Xinbot core are required.</p>
 */
public class YggdrasilLoginPlugin implements Plugin {

    private static final Logger log = LoggerFactory.getLogger("YggdrasilLogin");

    private AuthManager authManager;
    private ConnectListener connectListener;

    @Override
    public void onLoad() {
    }

    @Override
    public void onUnload() {
    }

    @Override
    public void onEnable() {
        this.authManager = new AuthManager();
        this.connectListener = new ConnectListener(authManager);

        try {
            authManager.loadConfig();
        } catch (Exception e) {
            log.error("Failed to load yggdrasil.conf, plugin will stay inactive", e);
            return;
        }

        if (!authManager.isConfigured()) {
            log.warn("yggdrasil.conf is not configured yet (authServer/username/password).");
            log.warn("The plugin will stay inactive until the file is filled in.");
            return;
        }

        Bot.INSTANCE.getPluginManager().registerEvents(connectListener, this);
        log.info("YggdrasilLogin enabled: authServer={}", authManager.getAuthServer());
    }

    @Override
    public void onDisable() {
    }
}
