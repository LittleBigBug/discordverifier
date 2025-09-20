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

package xyz.yawek.discordverifier.manager;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.node.Node;
import net.luckperms.api.track.Track;
import net.luckperms.api.track.TrackManager;
import xyz.yawek.discordverifier.DiscordVerifier;
import xyz.yawek.discordverifier.config.Config;
import xyz.yawek.discordverifier.util.LogUtils;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class LuckPermsManager {

    private final DiscordVerifier verifier;
    private LuckPerms luckPerms;

    public LuckPermsManager(DiscordVerifier verifier) {
        this.verifier = verifier;
        try {
            luckPerms = LuckPermsProvider.get();
        } catch (IllegalStateException e) {
            LogUtils.info("LuckPerms not found, some functions will be limited.");
        }
    }

    public void syncDiscordRoleToLPGroup() {
        Config cfg = verifier.getConfig();
        DiscordManager discord = verifier.getDiscordManager();

        LinkedHashMap<String, String> groupMap = cfg.rolesGroups();
        groupMap.forEach((roleId, groupOption) -> {
            Optional<Role> roleOptional = discord.getRole(roleId);
            if (roleOptional.isEmpty()) return;

            String[] optsArr = groupOption.split(":");

            TrackManager trackManager = luckPerms.getTrackManager();

            boolean usesTracks = optsArr.length > 1;
            String trackName = usesTracks ? optsArr[0] : null;
            String groupName = (usesTracks ? optsArr[1] : groupOption).toLowerCase();
            Track track = usesTracks ? trackManager.getTrack(trackName) : null;

            if (usesTracks) {
                if (track == null) {
                    LogUtils.error("Track {} not found, skipping role {} with mapping to minecraft track/group {}.", trackName, roleId, groupOption);
                    return;
                } else if (!track.containsGroup(groupName)) {
                    LogUtils.error("Track {} doesn't contain group {}. Skipping role {} with mapping to minecraft track/group {}.", trackName, groupName, roleId, groupOption);
                    return;
                }
            }

            discord.getPlayersWithRole(roleId).forEach(user -> {
                if (user.getDiscordId().isEmpty()) return;
                Optional<Member> memberOptional =
                        discord.getMemberById(user.getDiscordId().get());
                if (memberOptional.isEmpty()) return;

                luckPerms.getUserManager().loadUser(user.getUUID()).thenAccept(lpUser -> {
                    if (usesTracks) {
                        AtomicBoolean changed = new AtomicBoolean(false);
                        AtomicBoolean ignoreRest = new AtomicBoolean(false);
                        AtomicBoolean hasGroupAlready = new AtomicBoolean(false);

                        Collection<Node> nodes = lpUser.getNodes();

                        track.getGroups().forEach(group -> {
                            if (ignoreRest.get()) return;

                            boolean hasGroup = nodes.stream().anyMatch(node -> node.getKey().equals("group." + group));

                            if (group.equalsIgnoreCase(groupName)) {
                                if (hasGroup) hasGroupAlready.set(true);
                                ignoreRest.set(true);
                                return;
                            }

                            if (hasGroup) {
                                changed.set(true);
                                lpUser.data().remove(Node.builder("group." + group).build());
                            }
                        });

                        if (!hasGroupAlready.get()) {
                            changed.set(true);
                            lpUser.data().add(Node.builder("group." + groupName).build());
                        }

                        if (changed.get())
                            luckPerms.getUserManager().saveUser(lpUser);
                    } else lpUser.setPrimaryGroup(groupName);
                });
            });
        });
    }

    public void reloadPerms() {
//        Config cfg = verifier.getConfig();
//        DiscordManager discord = verifier.getDiscordManager();
//
//        // MC Groups -> Discord Roles
//        LinkedHashMap<String, String> roleMap = cfg.groupsRoles();
//        roleMap.forEach((groupName, roleId) -> {
//            Optional<Role> roleOptional = discord.getRole(roleId);
//            if (roleOptional.isEmpty()) return;
//
//            discord.getPlayersWithRole(roleId).forEach(user -> {
//                if (user.getDiscordId().isEmpty()) return;
//                Optional<Member> memberOptional =
//                        discord.getMemberById(user.getDiscordId().get());
//                if (memberOptional.isEmpty()) return;
//
//                luckPerms.getUserManager().loadUser(user.getUUID()).thenAccept(lpUser -> {
//                    boolean hasPermission = lpUser.getNodes()
//                            .stream()
//                            .anyMatch(node -> {
//                                if (node.getKey().equals("group." + groupName)) {
//                                    Set<String> values = node.getContexts().getValues("server");
//                                    return values.isEmpty() || values.contains("bungee");
//                                }
//                                return false;
//                            });
//                    if (!hasPermission) {
//                        discord.removeRole(memberOptional.get(), roleOptional.get());
//                    }
//                });
//            });
//        });
    }

}
