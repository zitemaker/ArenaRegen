package com.zitemaker;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.ChatColor;

import java.util.Map;

public class RegionCommand implements CommandExecutor {

    private final ArenaRegen plugin;

    public RegionCommand(ArenaRegen plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "Usage: /rg <list|spawn|tp|update> [name]");
            return true;
        }

        Player player = (Player) sender;

        switch (args[0].toLowerCase()) {
            case "list":
                return listRegions(sender);
            case "spawn":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /rg spawn <name>");
                    return true;
                }
                return setSpawn(sender, args[1], player.getLocation());
            case "tp":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /rg tp <name>");
                    return true;
                }
                return teleportToRegion(sender, args[1], player);
            case "update":
                if (args.length < 4) {
                    sender.sendMessage(ChatColor.RED + "Usage: /rg update <name> <corner1> <corner2>");
                    return true;
                }
                return updateRegion(sender, args[1], args[2], args[3], player);
            default:
                sender.sendMessage(ChatColor.RED + "Usage: /rg <list|spawn|tp|update> [name]");
                return true;
        }
    }

    private boolean listRegions(CommandSender sender) {
        Map<String, RegionData> regions = plugin.getRegisteredRegions();
        if (regions.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "No regions have been registered.");
            return true;
        }

        sender.sendMessage(ChatColor.GREEN + "Registered Regions:");
        for (Map.Entry<String, RegionData> entry : regions.entrySet()) {
            String name = entry.getKey();
            RegionData regionData = entry.getValue();

            // Calculate min and max coordinates
            Location min = regionData.getBlockDataMap().keySet().stream()
                    .reduce((loc1, loc2) -> new Location(
                            loc1.getWorld(),
                            Math.min(loc1.getX(), loc2.getX()),
                            Math.min(loc1.getY(), loc2.getY()),
                            Math.min(loc1.getZ(), loc2.getZ())
                    ))
                    .orElse(null);

            Location max = regionData.getBlockDataMap().keySet().stream()
                    .reduce((loc1, loc2) -> new Location(
                            loc1.getWorld(),
                            Math.max(loc1.getX(), loc2.getX()),
                            Math.max(loc1.getY(), loc2.getY()),
                            Math.max(loc1.getZ(), loc2.getZ())
                    ))
                    .orElse(null);

            if (min != null && max != null) {
                sender.sendMessage(String.format(
                        ChatColor.GREEN + "- %s: (%d, %d, %d) to (%d, %d, %d)",
                        name,
                        (int) min.getX(), (int) min.getY(), (int) min.getZ(),
                        (int) max.getX(), (int) max.getY(), (int) max.getZ()
                ));
            }
        }
        return true;
    }

    private boolean setSpawn(CommandSender sender, String name, Location location) {
        RegionData regionData = plugin.getRegisteredRegions().get(name);
        if (regionData == null) {
            sender.sendMessage(ChatColor.RED + "Region '" + name + "' does not exist.");
            return true;
        }

        regionData.setSpawnLocation(location);
        sender.sendMessage(ChatColor.GREEN + "Spawn point for region '" + name + "' has been set.");

        // Save regions to config
        plugin.saveRegions();
        return true;
    }

    private boolean teleportToRegion(CommandSender sender, String name, Player player) {
        RegionData regionData = plugin.getRegisteredRegions().get(name);
        if (regionData == null) {
            sender.sendMessage(ChatColor.RED + "Region '" + name + "' does not exist.");
            return true;
        }

        Location spawnLocation = regionData.getSpawnLocation();
        if (spawnLocation == null) {
            sender.sendMessage(ChatColor.RED + "Please set a spawn point for region '" + name + "' first.");
            return true;
        }

        player.teleport(spawnLocation);
        sender.sendMessage(ChatColor.GREEN + "Teleported to spawn point of region '" + name + "'.");
        return true;
    }

    private boolean updateRegion(CommandSender sender, String name, String corner1, String corner2, Player player) {
        RegionData regionData = plugin.getRegisteredRegions().get(name);
        if (regionData == null) {
            sender.sendMessage(ChatColor.RED + "Region '" + name + "' does not exist.");
            return true;
        }

        try {
            // Parse corner1 coordinates
            String[] corner1Coords = corner1.split(" ");
            int x1 = Integer.parseInt(corner1Coords[0]);
            int y1 = Integer.parseInt(corner1Coords[1]);
            int z1 = Integer.parseInt(corner1Coords[2]);

            // Parse corner2 coordinates
            String[] corner2Coords = corner2.split(" ");
            int x2 = Integer.parseInt(corner2Coords[0]);
            int y2 = Integer.parseInt(corner2Coords[1]);
            int z2 = Integer.parseInt(corner2Coords[2]);

            // Clear existing blocks and entities
            regionData.getBlockDataMap().clear();
            regionData.getEntityMap().clear();

            // Iterate through all blocks in the new region
            for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
                for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                    for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                        Location location = new Location(player.getWorld(), x, y, z);
                        Block block = location.getBlock();
                        regionData.addBlock(location, block.getBlockData());

                        // Track entities if enabled
                        if (plugin.isTrackEntities()) {
                            for (Entity entity : location.getWorld().getNearbyEntities(location, 0.5, 0.5, 0.5)) {
                                regionData.addEntity(location, entity.getType());
                            }
                        }
                    }
                }
            }

            sender.sendMessage(ChatColor.GREEN + "Region '" + name + "' has been updated.");
            plugin.saveRegions(); // Save changes to regions.yml
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid coordinates. Please use integers.");
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "An error occurred while updating the region.");
            plugin.getLogger().severe("Error updating region: " + e.getMessage());
        }

        return true;
    }
}