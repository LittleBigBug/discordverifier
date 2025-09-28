/*
 * This file is part of DiscordVerifier, licensed under GNU GPLv3 license.
 * Copyright (C) 2022 yawek9
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package xyz.yawek.discordverifier.command.subcommand;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import org.jetbrains.annotations.NotNull;
import xyz.yawek.discordverifier.DiscordVerifier;
import xyz.yawek.discordverifier.command.PermissibleCommand;
import xyz.yawek.discordverifier.config.Config;
import xyz.yawek.discordverifier.manager.VerifiableUserManager;
import xyz.yawek.discordverifier.user.VerifiableUser;
import xyz.yawek.discordverifier.util.LogUtils;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class UnlinkCommand extends PermissibleCommand {

    public UnlinkCommand(DiscordVerifier verifier) {
        super(verifier, "discordverifier.unlink");
    }

    @Override
    protected void handle(CommandSource source, String[] args) {
        if (!(source instanceof Player)) {
            source.sendMessage(verifier.getConfig().notFromConsole());
            return;
        }

        Config config = verifier.getConfig();
        VerifiableUserManager userManager = verifier.getUserManager();

        UUID uuid = ((Player) source).getUniqueId();

        VerifiableUser user =
                userManager.create(uuid);
        if (user.getDiscordId().isEmpty()) {
            source.sendMessage(config.notVerified());
            return;
        }
        verifier.getVerificationManager().updateGroups(uuid, true);
        verifier.getVerificationManager().updateRoles(uuid, true);
        verifier.getVerificationManager().updatePermissions(uuid, true);
        userManager.updateUser(user.toBuilder()
                .discordId(null)
                .discordName(null)
                .verified(false)
                .build());
        source.sendMessage(config.verificationCanceled());

        boolean kickUnlink = config.kickOnUnlink();

        String sendServer = config.sendToServerOnUnlink();
        boolean sendToServer = !sendServer.isBlank();

        if (sendToServer || kickUnlink) {
            ProxyServer proxy = verifier.getServer();
            Optional<Player> optPly = proxy.getPlayer(uuid);

            if (optPly.isPresent()) {
                Player ply = optPly.get();

                if (sendToServer) {
                    var optServer = proxy.getServer(sendServer);
                    if (optServer.isPresent()) ply.createConnectionRequest(optServer.get()).connect();
                    else {
                        LogUtils.error("No server found by the name " + sendServer);
                        if (kickUnlink) ply.disconnect(config.unlinkKicked());
                    }
                } else ply.disconnect(config.unlinkKicked());
            }
        }
    }

    @Override
    protected @NotNull List<String> handleSuggestion(CommandSource source, String[] args) {
        return Collections.emptyList();
    }

}
