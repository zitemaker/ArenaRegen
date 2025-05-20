package com.zitemaker.listeners;

import com.zitemaker.ArenaRegen;
import com.zitemaker.helpers.RegionData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerMoveListener implements Listener {

    private final ArenaRegen plugin;
    private final Map<UUID, Long> messageCooldowns = new HashMap<>();
    private static final long MESSAGE_COOLDOWN_MS = 3000;

    private final Map<String, int[]> regionBounds = new HashMap<>();

    public PlayerMoveListener(ArenaRegen plugin) {
        this.plugin = plugin;
        updateRegionBounds();
    }

    private void updateRegionBounds() {
        regionBounds.clear();
        for (Map.Entry<String, RegionData> entry : plugin.getRegisteredRegions().entrySet()) {
            RegionData region = entry.getValue();
            int[] bounds = new int[6];
            bounds[0] = region.getMinX();
            bounds[1] = region.getMinY();
            bounds[2] = region.getMinZ();
            bounds[3] = region.getMaxX();
            bounds[4] = region.getMaxY();
            bounds[5] = region.getMaxZ();
            regionBounds.put(entry.getKey(), bounds);
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        World world = player.getWorld();
        int x = to.getBlockX(), y = to.getBlockY(), z = to.getBlockZ();

        Location safeLoc = null;
        boolean shouldCancel = false;

        for (Map.Entry<String, RegionData> entry : plugin.getRegisteredRegions().entrySet()) {
            String regionName = entry.getKey();
            RegionData region = entry.getValue();
            if (!region.isLocked()) continue;

            int[] bounds = regionBounds.get(regionName);
            if (bounds == null || !region.getWorldName().equals(world.getName())) continue;

            if (x >= bounds[0] && x <= bounds[3] && y >= bounds[1] && y <= bounds[4] && z >= bounds[2] && z <= bounds[5]) {
                long currentTime = System.currentTimeMillis();
                UUID playerId = player.getUniqueId();
                Long lastMessageTime = messageCooldowns.get(playerId);
                if (lastMessageTime == null || currentTime - lastMessageTime > MESSAGE_COOLDOWN_MS) {
                    player.sendMessage(plugin.prefix + ChatColor.RED + " This arena is currently locked!");
                    messageCooldowns.put(playerId, currentTime);
                }

                safeLoc = findSafeLocationOutsideArena(player, region, from);
                shouldCancel = true;
                break;
            }
        }

        if (shouldCancel) {
            player.teleport(safeLoc);
            event.setCancelled(true);
        }
    }

    private Location findSafeLocationOutsideArena(Player player, RegionData region, Location from) {
        int[] bounds = regionBounds.get(plugin.getRegisteredRegions().entrySet().stream()
                .filter(e -> e.getValue() == region)
                .findFirst().get().getKey());
        double x = from.getX(), y = from.getY(), z = from.getZ();
        float yaw = from.getYaw(), pitch = from.getPitch();

        double distToMinX = Math.abs(x - bounds[0]);
        double distToMaxX = Math.abs(x - bounds[3]);
        double distToMinZ = Math.abs(z - bounds[2]);
        double distToMaxZ = Math.abs(z - bounds[5]);
        double minDist = Math.min(Math.min(distToMinX, distToMaxX), Math.min(distToMinZ, distToMaxZ));

        if (minDist == distToMinX) x = bounds[0] - 1.5;
        else if (minDist == distToMaxX) x = bounds[3] + 1.5;
        else if (minDist == distToMinZ) z = bounds[2] - 1.5;
        else z = bounds[5] + 1.5;

        y = Math.max(bounds[1], Math.min(y, bounds[4] + 1));

        Location safeLocation = new Location(player.getWorld(), x, y, z, yaw, pitch);

        if (!(player.getWorld().getBlockAt(safeLocation.getBlockX(), safeLocation.getBlockY(), safeLocation.getBlockZ()).isPassable() &&
                player.getWorld().getBlockAt(safeLocation.getBlockX(), safeLocation.getBlockY() + 1, safeLocation.getBlockZ()).isPassable())) {
            safeLocation = findNearestSafeLocation(safeLocation);
        }

        return safeLocation;
    }

    private Location findNearestSafeLocation(Location start) {
        World world = start.getWorld();
        double x = start.getX(), z = start.getZ();
        float yaw = start.getYaw(), pitch = start.getPitch();
        int y = (int) start.getY();

        for (int i = 0; i < 5; i++) {
            int testY = y + i;
            Location testLoc = new Location(world, x, testY, z, yaw, pitch);
            if (world.getBlockAt(testLoc.getBlockX(), testY, testLoc.getBlockZ()).isPassable() &&
                    world.getBlockAt(testLoc.getBlockX(), testY + 1, testLoc.getBlockZ()).isPassable()) {
                return testLoc;
            }
        }

        return world.getSpawnLocation();
    }
}