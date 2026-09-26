/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  me.clip.placeholderapi.expansion.PlaceholderExpansion
 *  org.bukkit.OfflinePlayer
 */
package com.dbteku.telecomstationid;

import com.dbteku.telecomstationid.TelecomStationIdPlugin;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

final class TelecomStationIdPlugin$StationPlaceholder
extends PlaceholderExpansion {
    private final TelecomStationIdPlugin plugin;

    TelecomStationIdPlugin$StationPlaceholder(TelecomStationIdPlugin telecomStationIdPlugin) {
        this.plugin = telecomStationIdPlugin;
    }

    public String getIdentifier() {
        return "telecomstation";
    }

    public String getAuthor() {
        return "OpenAI";
    }

    public String getVersion() {
        return "1.2.0";
    }

    public String onRequest(OfflinePlayer offlinePlayer, String string) {
        if (!"station_id".equalsIgnoreCase(string) && !"second_station_id".equalsIgnoreCase(string)) {
            return null;
        }
        if (offlinePlayer == null || offlinePlayer.getPlayer() == null) {
            return "-";
        }
        return this.plugin.currentStationId(offlinePlayer.getPlayer());
    }
}

