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

    private ArenaRegen plugin;
    private final Map<UUID, Long> messageCooldowns = new HashMap<>();
    private static final long MESSAGE_COOLDOWN_MS = 3000;

    public PlayerMoveListener(ArenaRegen plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || (from.getBlockX() == to.getBlockX() &&
                from.getBlockY() == to.getBlockY() &&
                from.getBlockZ() == to.getBlockZ())) {
            return;
        }

        Player player = event.getPlayer();
        Location loc = to;

        for (Map.Entry<String, RegionData> entry : plugin.getRegisteredRegions().entrySet()) {
            RegionData region = entry.getValue();

            if (!region.isLocked()) {
                continue;
            }

            int minX = region.getMinX();
            int minY = region.getMinY();
            int minZ = region.getMinZ();
            int maxX = region.getMaxX();
            int maxY = region.getMaxY();
            int maxZ = region.getMaxZ();

            World world = Bukkit.getWorld(region.getWorldName());
            if (world == null || !player.getWorld().equals(world)) {
                continue;
            }

            int x = loc.getBlockX();
            int y = loc.getBlockY();
            int z = loc.getBlockZ();

            if (x >= minX && x <= maxX &&
                    y >= minY && y <= maxY &&
                    z >= minZ && z <= maxZ) {

                long currentTime = System.currentTimeMillis();
                UUID playerId = player.getUniqueId();
                Long lastMessageTime = messageCooldowns.get(playerId);
                if (lastMessageTime == null || currentTime - lastMessageTime > MESSAGE_COOLDOWN_MS) {
                    player.sendMessage(plugin.prefix + ChatColor.RED + " This arena is currently locked!");
                    messageCooldowns.put(playerId, currentTime);
                }

                Location safeLocation = findSafeLocationOutsideArena(player, region, from);
                player.teleport(safeLocation);
                event.setCancelled(true);
                return;
            }
        }
    }

    private Location findSafeLocationOutsideArena(Player player, RegionData region, Location from) {
        World world = player.getWorld();
        int minX = region.getMinX();
        int minY = region.getMinY();
        int minZ = region.getMinZ();
        int maxX = region.getMaxX();
        int maxY = region.getMaxY();
        int maxZ = region.getMaxZ();

        double x = from.getX();
        double y = from.getY();
        double z = from.getZ();
        float yaw = from.getYaw();
        float pitch = from.getPitch();

        double distToMinX = Math.abs(x - minX);
        double distToMaxX = Math.abs(x - maxX);
        double distToMinZ = Math.abs(z - minZ);
        double distToMaxZ = Math.abs(z - maxZ);

        double minDist = Math.min(Math.min(distToMinX, distToMaxX), Math.min(distToMinZ, distToMaxZ));

        if (minDist == distToMinX) {
            x = minX - 1;
        } else if (minDist == distToMaxX) {
            x = maxX + 1;
        } else if (minDist == distToMinZ) {
            z = minZ - 1;
        } else {
            z = maxZ + 1;
        }

        if (y < minY) {
            y = minY;
        } else if (y > maxY) {
            y = maxY + 1;
        }

        Location safeLocation = new Location(world, x + 0.5, y, z + 0.5, yaw, pitch);

        if (!isSafeLocation(safeLocation)) {

            safeLocation = findNearestSafeLocation(safeLocation);
        }

        return safeLocation;
    }

    private boolean isSafeLocation(Location location) {
        World world = location.getWorld();
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();

        return world.getBlockAt(x, y, z).isPassable() && world.getBlockAt(x, y + 1, z).isPassable();
    }

    private Location findNearestSafeLocation(Location start) {
        World world = start.getWorld();
        double x = start.getX();
        double y = start.getY();
        double z = start.getZ();
        float yaw = start.getYaw();
        float pitch = start.getPitch();

        for (int i = 0; i < 10; i++) {
            Location testLoc = new Location(world, x, y + i, z, yaw, pitch);
            if (isSafeLocation(testLoc)) {
                return testLoc;
            }
        }

        return world.getSpawnLocation();
    }
}