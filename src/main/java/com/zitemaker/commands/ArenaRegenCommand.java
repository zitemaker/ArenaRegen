package com.zitemaker.commands;

import com.zitemaker.ArenaRegen;
import com.zitemaker.RegionData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class ArenaRegenCommand implements TabExecutor {

    private final ArenaRegen plugin;

    public ArenaRegenCommand(ArenaRegen plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String @NotNull [] strings) {

        String incorrectSyntax = ChatColor.translateAlternateColorCodes('&',
                "&cUnknown arguments. Type \"/arenaregen help\" to see usages");
        String noPermission = ChatColor.translateAlternateColorCodes('&',
                "&cYou do not have permission to run this command");

        if (!commandSender.hasPermission("arenaregen.admin") || !commandSender.hasPermission("arenaregen.reload")
                || !commandSender.hasPermission("arenaregen.register") || !commandSender.hasPermission("arenaregen.regenerate")
                || !commandSender.hasPermission("arenaregen.region")) {
            commandSender.sendMessage(noPermission);
            return true;
        }

        if (strings.length == 0) {
            commandSender.sendMessage(incorrectSyntax);
            return true;
        }

        switch (strings[0]) {
            case "regenerate":
                String targetArenaName = strings[1];

                // Get the registered region data
                RegionData regionData = plugin.getRegisteredRegions().get(targetArenaName);
                if (regionData == null) {
                    commandSender.sendMessage(ChatColor.RED + "Region '" + targetArenaName + "' does not exist.");
                    return true;
                }

                // If the sender is a player, use their world. Otherwise, use the default world.
                Location referenceLocation;
                if (commandSender instanceof Player) {
                    referenceLocation = ((Player) commandSender).getLocation();
                } else {
                    // Use the first block's location in the region as a reference
                    referenceLocation = regionData.getBlockDataMap().keySet().stream().findFirst().orElse(null);
                    if (referenceLocation == null) {
                        commandSender.sendMessage(ChatColor.RED + "Region '" + targetArenaName + "' has no blocks.");
                        return true;
                    }
                }

                // Ensure the world is set
                if (referenceLocation.getWorld() == null) {
                    commandSender.sendMessage(ChatColor.RED + "Invalid world for region '" + targetArenaName + "'.");
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
                if (plugin.getConfig().getBoolean("messages.track-entities")) {
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
                String message = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.arena-regen", "&aArena Regen"));
                commandSender.sendMessage(message);

        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String @NotNull [] strings) {
        return handleTabComplete(strings);
    }

    private List<String> handleTabComplete(String[] args) {
        return switch (args.length) {
            case 1 -> filterSuggestions(List.of("create", "regenerate", "setspawn", "delspawn", "teleport", "tp",
                    "list", "delete", "resize", "reload", "help"), args[0]);

            case 2 -> args[0].equalsIgnoreCase("regenerate") || args[0].equalsIgnoreCase("delete")
                    || args[0].equalsIgnoreCase("resize") || args[0].equalsIgnoreCase("setspawn")
                    || args[0].equalsIgnoreCase("delspawn") || args[0].equalsIgnoreCase("teleport")
                    || args[0].equalsIgnoreCase("tp")
                    ? getAvailableRegions(args[1])
                    : List.of();

            case 3 -> args[0].equalsIgnoreCase("regions") && (args[1].equalsIgnoreCase("setspawn") || args[1].equalsIgnoreCase("tp"))
                    ? getAvailableRegions(args[2])
                    : List.of();

            default -> List.of();
        };
    }


    private List<String> getAvailableRegions(String filter) {
        List<String> playerNames = new ArrayList<>();
        String filterLower = filter.toLowerCase();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().toLowerCase().startsWith(filterLower)) {
                playerNames.add(player.getName());
            }
        }
        return playerNames;
    }

    private List<String> filterSuggestions(List<String> options, String input) {
        if (input == null || input.isEmpty()) {
            return options;
        }
        String inputLower = input.toLowerCase();
        List<String> filtered = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase().startsWith(inputLower)) {
                filtered.add(option);
            }
        }
        return filtered;
    }
}
