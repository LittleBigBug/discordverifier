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

package xyz.yawek.discordverifier.discordlistener;

import com.velocitypowered.api.proxy.Player;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import xyz.yawek.discordverifier.DiscordVerifier;
import xyz.yawek.discordverifier.config.Config;
import xyz.yawek.discordverifier.manager.DiscordManager;
import xyz.yawek.discordverifier.manager.VerifiableUserManager;
import xyz.yawek.discordverifier.manager.VerificationManager;
import xyz.yawek.discordverifier.user.VerifiableUser;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class MessageReceivedListener extends ListenerAdapter {

    private final DiscordVerifier verifier;

    public MessageReceivedListener(DiscordVerifier verifier) {
        this.verifier = verifier;
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent e) {
        DiscordManager discord = verifier.getDiscordManager();
        Config config = verifier.getConfig();
        VerificationManager verification = verifier.getVerificationManager();

        if (!e.getChannel().getId().equalsIgnoreCase(config.channelId())) {
            return;
        }
        if (discord.isOtherBot(e.getAuthor())) {
            return;
        }

        Message message = e.getMessage();
        if (discord.isBotItself(e.getAuthor())) {
            deleteAfterDelay(message);
            return;
        }
        if (e.getMessage().getContentRaw().length() > 100) {
            message.delete().queue();
            return;
        }

        Member member = e.getMember();
        if (member == null) {
            message.delete().queue();
            return;
        }

        VerifiableUserManager userManager = verifier.getUserManager();
        MessageChannelUnion channel = e.getChannel();

        Optional<VerifiableUser> discordUser =
                userManager.retrieveByMemberId(member.getId());

        boolean unlinking = message.getContentRaw().startsWith("!mcunlink");

        if (discordUser.isPresent() && discordUser.get().isVerified()) {
            if (unlinking) {
                UUID uuid = discordUser.get().getUUID();
                verifier.getVerificationManager().updateGroups(uuid, true);
                verifier.getVerificationManager().updateRoles(uuid, true);
                verifier.getVerificationManager().updatePermissions(uuid, true);
                VerifiableUser user = userManager.create(uuid);
                userManager.updateUser(user.toBuilder()
                        .discordId(null)
                        .discordName(null)
                        .verified(false)
                        .build());

                channel.sendMessageEmbeds(config.verificationUnlinked()).queue();
            } else channel.sendMessageEmbeds(config.discordAlreadyVerified()).queue();

            deleteAfterDelay(message);
            return;
        } else if (unlinking) {
            return;
        }

        if (!message.getContentRaw().startsWith("!mclink ")) {
            // todo; send message telling user of wrong usage
            message.delete().queue();
            return;
        }

        String nickname = message.getContentRaw().replaceFirst("!mclink ", "");
        if (nickname.length() > 40) {
            message.delete().queue();
            return;
        }

        Optional<VerifiableUser> user =
                userManager.retrieveByNickname(nickname);
        Optional<Player> playerOptional = verifier.getServer().getPlayer(nickname);

        if (playerOptional.isEmpty() || user.isEmpty()) {
            channel.sendMessageEmbeds(config.playerNotFound(nickname)).queue();
            deleteAfterDelay(message);
            return;
        }

        if (user.get().isVerified()) {
            channel.sendMessageEmbeds(config.playerAlreadyVerified(nickname)).queue();
            deleteAfterDelay(message);
            return;
        }

        if (verification.startVerification(e.getMember(), playerOptional.get())) {
            channel.sendMessageEmbeds(config.verificationAccepted(nickname)).queue();
            deleteAfterDelay(message);
            return;
        }
        message.delete().queue();
    }

    private void deleteAfterDelay(Message message) {
        message.delete().queueAfter(
                verifier.getConfig().messageDeleteDelay(),
                TimeUnit.SECONDS);
    }

}
