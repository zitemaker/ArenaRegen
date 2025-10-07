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
import org.bukkit.util.BoundingBox;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerMoveListener implements Listener {

    private final ArenaRegen plugin;
    private final Map<UUID, Long> messageCooldowns = new HashMap<>();
    private static final long MESSAGE_COOLDOWN_MS = 3000;

    private final Map<String, BoundingBox> regionBounds = new HashMap<>();

    public PlayerMoveListener(ArenaRegen plugin) {
        this.plugin = plugin;
        updateRegionBounds();
    }


    public void updateRegionBounds() {
        regionBounds.clear();
        for (Map.Entry<String, RegionData> entry : plugin.getRegisteredRegions().entrySet()) {
            RegionData region = entry.getValue();
            BoundingBox box = new BoundingBox(
                    region.getMinX(), region.getMinY(), region.getMinZ(),
                    region.getMaxX(), region.getMaxY(), region.getMaxZ()
            );
            regionBounds.put(entry.getKey(), box);
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ())) {
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

            BoundingBox box = regionBounds.get(regionName);
            if (box == null || !region.getWorldName().equals(world.getName())) continue;

            if (box.contains(x + 0.5, y + 0.5, z + 0.5)) {
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
        BoundingBox box = regionBounds.get(plugin.getRegisteredRegions().entrySet().stream()
                .filter(e -> e.getValue() == region)
                .findFirst().get().getKey());
        double x = from.getX(), y = from.getY(), z = from.getZ();
        float yaw = from.getYaw(), pitch = from.getPitch();

        double distToMinX = Math.abs(x - box.getMinX());
        double distToMaxX = Math.abs(x - box.getMaxX());
        double distToMinZ = Math.abs(z - box.getMinZ());
        double distToMaxZ = Math.abs(z - box.getMaxZ());
        double minDist = Math.min(Math.min(distToMinX, distToMaxX), Math.min(distToMinZ, distToMaxZ));

        if (minDist == distToMinX) x = box.getMinX() - 1.5;
        else if (minDist == distToMaxX) x = box.getMaxX() + 1.5;
        else if (minDist == distToMinZ) z = box.getMinZ() - 1.5;
        else z = box.getMaxZ() + 1.5;

        y = Math.max(box.getMinY(), Math.min(y, box.getMaxY() + 1));

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