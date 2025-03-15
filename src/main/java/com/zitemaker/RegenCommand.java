package com.zitemaker;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.ChatColor;

import java.util.Map;

public class RegenCommand implements CommandExecutor {

    private final ArenaRegen plugin;

    public RegenCommand(ArenaRegen plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "Usage: /regen <name>");
            return true;
        }

        String name = args[0];

        // Get the registered region data
        RegionData regionData = plugin.getRegisteredRegions().get(name);
        if (regionData == null) {
            sender.sendMessage(ChatColor.RED + "Region '" + name + "' does not exist.");
            return true;
        }

        // If the sender is a player, use their world. Otherwise, use the default world.
        Location referenceLocation;
        if (sender instanceof Player) {
            referenceLocation = ((Player) sender).getLocation();
        } else {
            // Use the first block's location in the region as a reference
            referenceLocation = regionData.getBlockDataMap().keySet().stream().findFirst().orElse(null);
            if (referenceLocation == null) {
                sender.sendMessage(ChatColor.RED + "Region '" + name + "' has no blocks.");
                return true;
            }
        }

        // Ensure the world is set
        if (referenceLocation.getWorld() == null) {
            sender.sendMessage(ChatColor.RED + "Invalid world for region '" + name + "'.");
            return true;
        }

        // Restore all blocks in the region
        for (Map.Entry<Location, BlockData> entry : regionData.getBlockDataMap().entrySet()) {
            try {
                Location location = entry.getKey();
                location.setWorld(referenceLocation.getWorld()); // Set the world
                BlockData blockData = entry.getValue();
                Block block = location.getWorld().getBlockAt(location);
                block.setBlockData(blockData, false); // Avoid physics updates for better performance
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to restore block at " + entry.getKey() + ": " + e.getMessage());
            }
        }

        // Restore all entities in the region
        if (plugin.isTrackEntities()) {
            for (Map.Entry<Location, EntityType> entry : regionData.getEntityMap().entrySet()) {
                try {
                    Location location = entry.getKey();
                    location.setWorld(referenceLocation.getWorld()); // Set the world

                    // Clear existing entities in the region
                    for (Entity entity : location.getWorld().getNearbyEntities(location, 0.5, 0.5, 0.5)) {
                        entity.remove();
                    }

                    // Spawn the saved entity
                    location.getWorld().spawnEntity(location, entry.getValue());
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to restore entity at " + entry.getKey() + ": " + e.getMessage());
                }
            }
        }

        // Send the "Arena Regen" message
        String message = ChatColor.translateAlternateColorCodes('&', plugin.getArenaRegenMessage());
        sender.sendMessage(message);

        return true;
    }
}