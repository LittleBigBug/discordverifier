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

import com.velocitypowered.api.proxy.Player;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.node.Node;
import net.luckperms.api.track.Track;
import xyz.yawek.discordverifier.DiscordVerifier;
import xyz.yawek.discordverifier.config.Config;
import xyz.yawek.discordverifier.role.GroupRole;
import xyz.yawek.discordverifier.role.RoleGroup;
import xyz.yawek.discordverifier.user.VerifiableUser;
import xyz.yawek.discordverifier.util.LogUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class VerificationManager {

    private final DiscordVerifier verifier;
    private final ConcurrentHashMap<Player, Member> verifyingPlayers = new ConcurrentHashMap<>();

    public VerificationManager(DiscordVerifier verifier) {
        this.verifier = verifier;
    }

    public boolean startVerification(Member member, Player player) {
        Config config = verifier.getConfig();

        if (verifyingPlayers.containsKey(player)) return false;

        verifyingPlayers.put(player, member);
        player.sendMessage(config.verificationRequest(member.getUser().getAsTag()));
        verifier.getServer().getScheduler()
                .buildTask(verifier, () -> cancelVerification(member, player))
                .delay(config.verificationExpireTime(), TimeUnit.SECONDS)
                .schedule();
        return true;
    }

    public void cancelVerification(Member member, Player player) {
        if (player.isActive() && verifyingPlayers.containsKey(player)) {
            player.sendMessage(verifier.getConfig()
                    .verificationExpired(member.getUser().getAsTag()));
            verifyingPlayers.remove(player);
        }
    }

    public void completeVerification(Player player, boolean accepted) {
        Config config = verifier.getConfig();

        if (!verifyingPlayers.containsKey(player)) {
            player.sendMessage(config.noRequests());
            return;
        }

        if (!player.isActive()) return;

        if (!accepted) {
            player.sendMessage(config.verificationDenied());
            verifier.getDiscordManager().sendInVerification(
                    config.verificationDenied(player.getUsername()));
            verifyingPlayers.remove(player);
            return;
        }

        VerifiableUser user = verifier.getUserManager().create(player.getUniqueId());
        Member member = verifyingPlayers.get(player);
        verifier.getDataProvider().updateUser(user.toBuilder()
                .verified(true)
                .discordId(member.getId())
                .discordName(member.getUser().getAsTag())
                .build());

        this.updateGroups(player);
        this.updateRoles(player);
        this.updatePermissions(player);
        this.updateNickname(player);

        player.sendMessage(config.verifiedSuccessfully(verifyingPlayers.get(player).getUser().getAsTag()));
        verifier.getDiscordManager().sendInVerification(config.verificationSuccess());
        verifyingPlayers.remove(player);
    }

    private boolean hasPermission(User lpUser, Node node) { return this.hasPermission(lpUser, node.getKey()); }
    private boolean hasPermission(User lpUser, String permission) {
        return lpUser.getNodes().stream().anyMatch(node -> node.getKey().equals(permission) && node.getValue());
    }

    public void updateRoles(Player player) {
        this.updateRoles(player, false);
    }
    public void updateRoles(Player player, boolean remove) { this.updateRoles(player.getUniqueId(), remove); }
    public void updateRoles(UUID uuid) { this.updateRoles(uuid, false); }
    public void updateRoles(UUID uuid, boolean remove) {
        Config config = verifier.getConfig();
        DiscordManager discord = verifier.getDiscordManager();

        VerifiableUser user = verifier.getUserManager().create(uuid);
        if (user.getDiscordId().isEmpty()) return;
        Optional<Member> memberOptional = discord.getMemberById(user.getDiscordId().get());
        if (memberOptional.isEmpty()) return;

        Set<GroupRole> roleSet = config.groupsRolesSet();

        if (remove) {
            DiscordManager discordManager = verifier.getDiscordManager();
            roleSet.forEach(groupRole -> discordManager.removeRole(
                    memberOptional.get(), groupRole.getRole()));
        } else {
            this.verifier.getLuckPerms().getUserManager().loadUser(uuid).thenAccept(lpUser -> {
                Member member = memberOptional.get();
                boolean roleAssigned = false;
                for (GroupRole groupRole : roleSet) {
                    Role role = groupRole.getRole();
                    boolean hasNode = this.hasPermission(lpUser, "group." + groupRole.getGroupName());

                    if (hasNode && (!roleAssigned || !config.oneRoleLimit())) {
                        discord.addRole(member, role);
                        roleAssigned = true;
                    } else
                        discord.removeRole(member, role);
                }
            });
        }
    }

    public void updateGroups(Player player) {
        updateGroups(player, false);
    }
    public void updateGroups(Player player, boolean remove) { updateGroups(player.getUniqueId(), remove); }
    public void updateGroups(UUID uuid) { updateGroups(uuid, false); }
    public void updateGroups(UUID uuid, boolean remove) {
        Config config = verifier.getConfig();
        DiscordManager discord = verifier.getDiscordManager();

        VerifiableUser user = verifier.getUserManager().create(uuid);
        if (user.getDiscordId().isEmpty()) return;
        Optional<Member> memberOptional = discord.getMemberById(user.getDiscordId().get());
        if (memberOptional.isEmpty()) return;

        Member member = memberOptional.get();
        List<Role> roles = member.getRoles();
        Set<RoleGroup> groupSet = config.rolesGroupsSet();

        UserManager userManager = this.verifier.getLuckPerms().getUserManager();

        userManager.loadUser(uuid).thenAccept(lpUser -> {
            boolean modified = false;

            for (RoleGroup roleGroup : groupSet) {
                Node pNode = Node.builder("group." + roleGroup.getGroupName()).build();
                boolean hasPerm = this.hasPermission(lpUser, pNode);
                boolean hasRole = roles.contains(roleGroup.getRole());

                if (hasPerm && (remove || !hasRole)) {
                    modified = true;
                    lpUser.data().remove(pNode);
                    if (roleGroup.usesTrack()) {
                        String defaultGroup = config.trackDefaultGroup(roleGroup.getTrackName());
                        if (defaultGroup != null) lpUser.data().add(Node.builder("group." + defaultGroup).build());
                    }
                } else if (!remove && (!hasPerm && hasRole)) {
                    modified = true;

                    if (roleGroup.usesTrack()) {
                        Track track = roleGroup.getTrack(this.verifier.getLuckPerms());

                        if (track == null) {
                            LogUtils.error("Track {} not found, still adding group {}, without removing existing groups in track.", roleGroup.getTrackName(), roleGroup.getGroupName());
                            continue;
                        }

                        List<String> trackGroups = track.getGroups();
                        for (String trackGroup : trackGroups) {
                            Node node = Node.builder("group." + trackGroup).build();
                            if (!this.hasPermission(lpUser, node)) continue;
                            lpUser.data().remove(node);
                        }
                    }

                    lpUser.data().add(pNode);
                }
            }

            if (modified)
                userManager.saveUser(lpUser);
        });
    }

    public void updatePermissions(Player player) {
        updatePermissions(player, false);
    }
    public void updatePermissions(Player player, boolean remove) { updatePermissions(player.getUniqueId(), remove); }
    public void updatePermissions(UUID uuid) { updatePermissions(uuid, false); }
    public void updatePermissions(UUID uuid, boolean remove) {
        Config config = verifier.getConfig();
        List<String> perms = config.verificationPermissions();

        UserManager userManager = this.verifier.getLuckPerms().getUserManager();

        userManager.loadUser(uuid).thenAccept(lpUser -> {
            boolean modified = false;

            for (String perm : perms) {
                boolean hasPerm = this.hasPermission(lpUser, perm);
                if (remove != hasPerm) continue;

                modified = true;

                if (remove) lpUser.data().remove(Node.builder(perm).build());
                else lpUser.data().add(Node.builder(perm).build());
            }

            if (modified)
                userManager.saveUser(lpUser);
        });
    }

    public void updateNickname(Player player) { updateNickname(player, false); }
    public void updateNickname(Player player, boolean login) {
        Config config = verifier.getConfig();

        if (!config.nicknameSyncEnabled() || (login && !config.forceNicknames())) return;

        VerifiableUser user = verifier.getUserManager().create(player.getUniqueId());
        if (!user.isVerified() || user.getDiscordId().isEmpty()) return;

        DiscordManager discordManager = verifier.getDiscordManager();
        Optional<Member> memberOptional =
                discordManager.getMemberById(user.getDiscordId().get());
        if (memberOptional.isEmpty()) return;

        Member member = memberOptional.get();

        boolean discordToMinecraft = config.nicknameSyncDiscordToMinecraft();

        if (discordToMinecraft) {
          // todo; not implemented (probably requires some other plugin, maybe later)
        } else discordManager.setNickname(member, player.getUsername());

//        if (!member.getPermissions().contains(Permission.NICKNAME_CHANGE)) {
//            discordManager.setNickname(memberOptional.get(), player.getUsername());
//        }
    }

}