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

package xyz.yawek.discordverifier.listener;

import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.proxy.Player;
import net.dv8tion.jda.api.entities.Member;
import xyz.yawek.discordverifier.DiscordVerifier;
import xyz.yawek.discordverifier.config.Config;
import xyz.yawek.discordverifier.manager.DiscordManager;
import xyz.yawek.discordverifier.manager.VerificationManager;
import xyz.yawek.discordverifier.user.VerifiableUser;

import java.util.List;
import java.util.Optional;

public class LoginListener {

    private final DiscordVerifier verifier;

    public LoginListener(DiscordVerifier verifier) {
        this.verifier = verifier;
    }

    @Subscribe
    public EventTask onPlayerPostLogin(PostLoginEvent e) {
        return EventTask.async(() -> {
            Player player = e.getPlayer();
            VerifiableUser user =
                    verifier.getUserManager().create(player.getUniqueId());
            if (!user.isVerified())
                player.sendMessage(verifier.getConfig().notVerifiedYet());
        });
    }

    @Subscribe
    public EventTask onPlayerLogin(LoginEvent e) {
        return EventTask.async(() -> {
            Player player = e.getPlayer();

            verifier.getDataProvider().updateUserIdentity(
                            player.getUniqueId(), player.getUsername());

            VerificationManager verificationManager = verifier.getVerificationManager();
            verificationManager.updateGroups(player);
            verificationManager.updateRoles(player);
            verificationManager.updatePermissions(player);
            verificationManager.updateNickname(player, true);

            VerifiableUser user =
                    verifier.getUserManager().create(player.getUniqueId());

            if (user.isVerified()) {
                Config config = verifier.getConfig();

                List<String> bannedRoles = config.bannedDiscordRoles();
                if (bannedRoles.isEmpty()) return;

                DiscordManager discordManager = verifier.getDiscordManager();

                Optional<String> optId = user.getDiscordId();
                if (optId.isEmpty()) return;

                Optional<Member> optMember = discordManager.getMemberById(optId.get());
                if (optMember.isEmpty()) return;

                boolean hasBannedRole = optMember.get().getRoles().stream()
                        .anyMatch(role -> bannedRoles.contains(role.getId()));

                if (hasBannedRole) player.disconnect(config.discordBannedJoin());
            }
        });
    }

}
