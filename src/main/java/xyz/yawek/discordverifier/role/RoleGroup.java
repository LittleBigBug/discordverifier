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

package xyz.yawek.discordverifier.role;

import net.dv8tion.jda.api.entities.Role;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.track.Track;
import net.luckperms.api.track.TrackManager;
import org.jetbrains.annotations.NotNull;

public class RoleGroup implements Comparable<RoleGroup> {

    private final String trackName;
    private final String groupName;
    private final Role role;

    public RoleGroup(String groupOption, Role role) {
        String[] optsArr = groupOption.split(":");

        boolean usesTracks = optsArr.length > 1;
        this.trackName = usesTracks ? optsArr[0] : null;
        this.groupName = (usesTracks ? optsArr[1] : groupOption).toLowerCase();

        this.role = role;
    }

    public boolean usesTrack() {
        return this.trackName != null;
    }
    public Track getTrack(LuckPerms lp) {
        if (!this.usesTrack()) return null;
        TrackManager trackManager = lp.getTrackManager();
        return trackManager.getTrack(this.trackName);
    }
    public String getTrackName() {
        if (!this.usesTrack()) return null;
        return trackName;
    }

    public String getGroupName() {
        return groupName;
    }

    public Role getRole() {
        return role;
    }

    @Override
    public int compareTo(@NotNull RoleGroup r) {
        return Integer.compare(r.getRole().getPosition(), this.getRole().getPosition());
    }

}
