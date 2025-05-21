package com.zitemaker.placeholders;

import com.zitemaker.ArenaRegen;
import com.zitemaker.helpers.RegionData;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class ArenaRegenExpansion extends PlaceholderExpansion {

    private final ArenaRegen plugin;

    public ArenaRegenExpansion(ArenaRegen plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "arenaregen";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Zitemaker";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    public void onScheduleUpdate() {
        //plugin.getLogger().info("Placeholder expansion received schedule update");
    }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String identifier) {
        //plugin.getLogger().info("Processing placeholder: " + identifier + " for player: " + (player != null ? player.getName() : "null"));

        String[] parts = identifier.toLowerCase().split("_", 2);
        String baseIdentifier = parts[0];
        String arenaName = parts.length > 1 ? parts[1] : null;

        String currentRegionName = null;
        RegionData currentRegion = null;

        if (arenaName == null && player != null) {
            Location loc = player.getLocation();
            String worldName = loc.getWorld().getName();
            int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();

            for (Map.Entry<String, RegionData> entry : plugin.getRegisteredRegions().entrySet()) {
                RegionData r = entry.getValue();
                int minX = r.getMinX(), minY = r.getMinY(), minZ = r.getMinZ();
                int maxX = r.getMaxX(), maxY = r.getMaxY(), maxZ = r.getMaxZ();

                if (worldName.equals(r.getWorldName()) &&
                        x >= minX && x <= maxX &&
                        y >= minY && y <= maxY &&
                        z >= minZ && z <= maxZ) {
                    currentRegionName = entry.getKey();
                    currentRegion = r;
                    //plugin.getLogger().info("Found region: " + currentRegionName + " for player at " + x + "," + y + "," + z);
                    break;
                }
            }
        } else if (arenaName != null) {
            currentRegionName = arenaName;
            currentRegion = plugin.getRegisteredRegions().get(arenaName);
            //plugin.getLogger().info("Using specified arena: " + arenaName + " (exists: " + (currentRegion != null) + ")");
        }

        if (currentRegionName == null && player == null) {
            switch (baseIdentifier) {
                case "region":
                    return "Player Required";
                case "locked":
                case "status":
                case "nextregen":
                case "size":
                case "creator":
                case "world":
                    return "Arena or Player Required";
                default:
                    return null;
            }
        }

        switch (baseIdentifier) {
            case "region":
                return currentRegionName != null ? currentRegionName : "None";

            case "locked":
                return currentRegion != null ? String.valueOf(currentRegion.isLocked()) : "false";

            case "status":
                if (currentRegion == null) return arenaName != null ? "Invalid Arena" : "None";
                if (plugin.isArenaRegenerating(currentRegionName)) return "Regenerating";
                return currentRegion.isLocked() ? "Locked" : "Available";

            case "nextregen":
                if (currentRegion == null) return arenaName != null ? "Invalid Arena" : "None";
                if (plugin.isArenaRegenerating(currentRegionName)) return "Regenerating";
                Long intervalTicks = plugin.getScheduledIntervals().get(currentRegionName);
                //plugin.getLogger().info("IntervalTicks for " + currentRegionName + ": " + intervalTicks);
                if (intervalTicks == null) return "Not Scheduled";
                Long startTime = plugin.getTaskStartTimes().get(currentRegionName);
                //plugin.getLogger().info("StartTime for " + currentRegionName + ": " + startTime);
                if (startTime == null) return "Not Scheduled";
                long currentTime = System.currentTimeMillis();
                long intervalMs = intervalTicks * 50;
                long totalElapsedMs = currentTime - startTime;
                long remainingMs = intervalMs - (totalElapsedMs % intervalMs);
                //plugin.getLogger().info("Calculated remainingMs: " + remainingMs);
                return formatTime(remainingMs);

            case "size":
                if (currentRegion == null) return arenaName != null ? "Invalid Arena" : "0";
                return String.valueOf(currentRegion.getArea());

            case "creator":
                if (currentRegion == null) return arenaName != null ? "Invalid Arena" : "None";
                return currentRegion.getCreator() != null ? currentRegion.getCreator() : "Unknown";

            case "world":
                return currentRegion != null ? currentRegion.getWorldName() : (arenaName != null ? "Invalid Arena" : "None");

            default:
                return null;
        }
    }

    private String formatTime(long milliseconds) {
        long seconds = milliseconds / 1000;
        if (seconds < 60) {
            return seconds + "s";
        } else if (seconds < 3600) {
            long minutes = seconds / 60;
            seconds %= 60;
            return minutes + "m" + (seconds > 0 ? " " + seconds + "s" : "");
        } else if (seconds < 86400) {
            long hours = seconds / 3600;
            seconds %= 3600;
            long minutes = seconds / 60;
            seconds %= 60;
            return hours + "h" + (minutes > 0 ? " " + minutes + "m" : "") + (seconds > 0 ? " " + seconds + "s" : "");
        } else if (seconds < 604800) {
            long days = seconds / 86400;
            seconds %= 86400;
            long hours = seconds / 3600;
            seconds %= 3600;
            long minutes = seconds / 60;
            seconds %= 60;
            return days + "d" + (hours > 0 ? " " + hours + "h" : "") + (minutes > 0 ? " " + minutes + "m" : "") + (seconds > 0 ? " " + seconds + "s" : "");
        } else {
            long weeks = seconds / 604800;
            seconds %= 604800;
            long days = seconds / 86400;
            seconds %= 86400;
            long hours = seconds / 3600;
            seconds %= 3600;
            long minutes = seconds / 60;
            seconds %= 60;
            return weeks + "w" + (days > 0 ? " " + days + "d" : "") + (hours > 0 ? " " + hours + "h" : "") + (minutes > 0 ? " " + minutes + "m" : "") + (seconds > 0 ? " " + seconds + "s" : "");
        }
    }
}