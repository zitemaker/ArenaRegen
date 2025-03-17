package com.zitemaker.commands;

import com.zitemaker.ArenaRegen;
import com.zitemaker.RegionData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
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
import java.util.List;
import java.util.Map;

public class ArenaRegenCommand implements TabExecutor {

    private final ArenaRegen plugin;

    public ArenaRegenCommand(ArenaRegen plugin) {
        this.plugin = plugin;
    }

    int arenaSizeLimit = 40000;

    @Override
    public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String @NotNull [] strings) {

        String incorrectSyntax = ChatColor.translateAlternateColorCodes('&',
                "&cUnknown arguments. Type \"/arenaregen help\" to see usages");
        String noPermission = ChatColor.translateAlternateColorCodes('&',
                "&cYou do not have permission to run this command");
        String onlyForPlayers = ChatColor.translateAlternateColorCodes('&', "&cOnly players can use this command.");

        if (!ArenaRegen.hasAnyPermissions((Player) commandSender)) {
            commandSender.sendMessage(noPermission);
            return true;
        }

        if (strings.length == 0) {
            commandSender.sendMessage(incorrectSyntax);
            return true;
        }

        switch (strings[0]) {
            case "create" -> {
                if (!(commandSender instanceof Player player)) {
                    commandSender.sendMessage(onlyForPlayers);
                    return true;
                }

                if (!commandSender.hasPermission("arenaregen.create")) {
                    commandSender.sendMessage(noPermission);
                }

                if (strings.length != 8) {
                    commandSender.sendMessage(ChatColor.RED + "Usage: /arenaregen create <name> <x1> <y1> <z1> <x2> <y2> <z2>");
                    return true;
                }

                String regionName = strings[1];

                try {

                    int x1 = Integer.parseInt(strings[2]);
                    int y1 = Integer.parseInt(strings[3]);
                    int z1 = Integer.parseInt(strings[4]);
                    int x2 = Integer.parseInt(strings[5]);
                    int y2 = Integer.parseInt(strings[6]);
                    int z2 = Integer.parseInt(strings[7]);

                    World world = player.getWorld();

                    if (plugin.getRegisteredRegions().containsKey(regionName)) {
                        commandSender.sendMessage(ChatColor.RED + "A region with this name already exists.");
                        return true;
                    }

                    if (!isCoordinateHeightValid(world, y1) || !isCoordinateHeightValid(world, y2)) {
                        commandSender.sendMessage(ChatColor.RED + "Invalid arena height! Must be between "
                                + world.getMinHeight() + " and " + world.getMaxHeight() + ".");
                        return true;
                    }

                    if (!regionSizeLimit(x1, z1) || !regionSizeLimit(x2, z2)) {
                        commandSender.sendMessage(ChatColor.RED + "Arena must be in size limit! Must be within " + arenaSizeLimit + " blocks. " +
                                "You can change the size limit in config.yml");
                        return true;
                    }

                    int minX = Math.min(x1, x2);
                    int minY = Math.min(y1, y2);
                    int minZ = Math.min(z1, z2);
                    int maxX = Math.max(x1, x2);
                    int maxY = Math.max(y1, y2);
                    int maxZ = Math.max(z1, z2);

                    RegionData regionData = new RegionData();

                    for (int x = minX; x <= maxX; x++) {
                        for (int y = minY; y <= maxY; y++) {
                            for (int z = minZ; z <= maxZ; z++) {
                                Location loc = new Location(world, x, y, z);
                                BlockData blockData = loc.getBlock().getBlockData();
                                regionData.addBlock(loc, blockData);
                            }
                        }
                    }

                    plugin.getRegisteredRegions().put(regionName, regionData);
                    commandSender.sendMessage(ChatColor.GREEN + "Region '" + regionName + "' has been successfully registered!");

                } catch (NumberFormatException e) {
                    commandSender.sendMessage(ChatColor.RED + "Invalid coordinates! Please enter valid integers.");
                }

                return true;
            }

            case "delete" -> {
                if (!commandSender.hasPermission("arenaregen.delete")) {
                    commandSender.sendMessage(noPermission);
                    return true;
                }

                if (strings.length < 2) {
                    commandSender.sendMessage(ChatColor.RED + "Usage: /arenaregen delete <name>");
                    return true;
                }

                String regionName = strings[1];

                if (strings.length == 2) {
                    // First step: Ask for confirmation
                    if (!plugin.getRegisteredRegions().containsKey(regionName)) {
                        commandSender.sendMessage(ChatColor.RED + "No region with this name exists.");
                        return true;
                    }

                    plugin.getPendingDeletions().put(commandSender.getName(), regionName);
                    commandSender.sendMessage(ChatColor.YELLOW + "Are you sure you want to delete the region '" + regionName + "'?");
                    commandSender.sendMessage(ChatColor.YELLOW + "Type '/arenaregen delete confirm' to proceed.");
                    return true;
                }

                if (strings.length == 3 && strings[1].equalsIgnoreCase("confirm")) {
                    // Second step: Confirm and delete
                    String senderName = commandSender.getName();

                    if (!plugin.getPendingDeletions().containsKey(senderName)) {
                        commandSender.sendMessage(ChatColor.RED + "No pending region deletion found. Use '/arenaregen delete <name>' first.");
                        return true;
                    }

                    String confirmedRegion = plugin.getPendingDeletions().remove(senderName);

                    if (!plugin.getRegisteredRegions().containsKey(confirmedRegion)) {
                        commandSender.sendMessage(ChatColor.RED + "The region '" + confirmedRegion + "' no longer exists.");
                        return true;
                    }

                    plugin.getRegisteredRegions().remove(confirmedRegion);
                    commandSender.sendMessage(ChatColor.GREEN + "Region '" + confirmedRegion + "' has been successfully deleted!");
                    return true;
                }

                commandSender.sendMessage(ChatColor.RED + "Invalid usage! Type '/arenaregen delete <name>' first, then '/arenaregen delete confirm'.");
                return true;
            }

            case "resize" -> {
                if (!(commandSender instanceof Player player)) {
                    commandSender.sendMessage(onlyForPlayers);
                    return true;
                }

                if (strings.length < 4) {
                    commandSender.sendMessage(ChatColor.RED + "Usage: /rg update <name> <corner1> <corner2>");
                    return true;
                }
                updateRegion(commandSender, strings[1], strings[2], strings[3], player);
            }

            case "regenerate" -> {
                String targetArenaName = strings[1];

                RegionData regionData = plugin.getRegisteredRegions().get(targetArenaName);

                if (targetArenaName == null) {
                    commandSender.sendMessage(ChatColor.RED + "Please select an arena to regenerate.");
                    return true;
                }

                if (regionData == null) {
                    commandSender.sendMessage(ChatColor.RED + "Arena '" + targetArenaName + "' does not exist.");
                    return true;
                }

                Location referenceLocation;
                if (commandSender instanceof Player) {
                    referenceLocation = ((Player) commandSender).getLocation();
                } else {

                    referenceLocation = regionData.getBlockDataMap().keySet().stream().findFirst().orElse(null);
                    if (referenceLocation == null) {
                        commandSender.sendMessage(ChatColor.RED + "Region '" + targetArenaName + "' has no blocks.");
                        return true;
                    }
                }

                if (referenceLocation.getWorld() == null) {
                    commandSender.sendMessage(ChatColor.RED + "Invalid world for region '" + targetArenaName + "'.");
                    return true;
                }

                for (Map.Entry<Location, BlockData> entry : regionData.getBlockDataMap().entrySet()) {
                    try {
                        Location location = entry.getKey();
                        location.setWorld(referenceLocation.getWorld());
                        BlockData blockData = entry.getValue();
                        Block block = location.getWorld().getBlockAt(location);
                        block.setBlockData(blockData, false);
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to restore block at " + entry.getKey() + ": " + e.getMessage());
                    }
                }

                if (plugin.getConfig().getBoolean("messages.track-entities")) {
                    for (Map.Entry<Location, EntityType> entry : regionData.getEntityMap().entrySet()) {
                        try {
                            Location location = entry.getKey();
                            location.setWorld(referenceLocation.getWorld());

                            for (Entity entity : location.getWorld().getNearbyEntities(location, 0.5, 0.5, 0.5)) {
                                entity.remove();
                            }

                            location.getWorld().spawnEntity(location, entry.getValue());
                        } catch (Exception e) {
                            plugin.getLogger().warning("Failed to restore entity at " + entry.getKey() + ": " + e.getMessage());
                        }
                    }
                }

                String message = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.arena-regen", "&aArena Regen"));
                commandSender.sendMessage(message);

                return true;
            }

            case "setspawn" -> {
                if (!(commandSender instanceof Player player)) {
                    commandSender.sendMessage(onlyForPlayers);
                    return true;
                }

                if (strings.length < 2) {
                    commandSender.sendMessage(ChatColor.RED + "Usage: /rg spawn <name>");
                    return true;
                }
                setSpawn(commandSender, strings[1], player.getLocation());
            }

            case "delspawn" -> {
                // work in progress4
            }

            case "teleport", "tp" -> {
                if (!(commandSender instanceof Player player)) {
                    commandSender.sendMessage(onlyForPlayers);
                    return true;
                }

                if (strings.length < 2) {
                    commandSender.sendMessage(ChatColor.RED + "Usage: /rg tp <name>");
                    return true;
                }
                teleportToRegion(commandSender, strings[1], player);
            }

            case "list" -> listRegions(commandSender);

            case "reload" -> {
                // work in progress8
            }

            case "help" -> {
                // work in progress9
            }

            default -> {
                if (commandSender instanceof Player player) {
                    player.sendMessage(incorrectSyntax);
                } else plugin.getLogger().info(incorrectSyntax);
            }
        }

        return true;
    }

    private boolean regionSizeLimit(int x, int z) {
        return (x * z) <= arenaSizeLimit;
    }

    private boolean isCoordinateHeightValid(@NotNull World world, int y) {
        return y > world.getMinHeight() && y < world.getMaxHeight();
    }

    private void listRegions(CommandSender sender) {
        Map<String, RegionData> regions = plugin.getRegisteredRegions();
        if (regions.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "No arenas have been registered.");
            return;
        }

        sender.sendMessage(ChatColor.GREEN + "Registered Arenas:");
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
    }

    private void setSpawn(CommandSender sender, String name, Location location) {
        RegionData regionData = plugin.getRegisteredRegions().get(name);
        if (regionData == null) {
            sender.sendMessage(ChatColor.RED + "Region '" + name + "' does not exist.");
            return;
        }

        regionData.setSpawnLocation(location);
        sender.sendMessage(ChatColor.GREEN + "Spawn point for region '" + name + "' has been set.");
    }

    private void teleportToRegion(CommandSender sender, String name, Player player) {
        RegionData regionData = plugin.getRegisteredRegions().get(name);
        if (regionData == null) {
            sender.sendMessage(ChatColor.RED + "Region '" + name + "' does not exist.");
            return;
        }

        Location spawnLocation = regionData.getSpawnLocation();
        if (spawnLocation == null) {
            sender.sendMessage(ChatColor.RED + "Please set a spawn point for region '" + name + "' first.");
            return;
        }

        player.teleport(spawnLocation);
        sender.sendMessage(ChatColor.GREEN + "Teleported to spawn point of region '" + name + "'.");
    }

    private void updateRegion(CommandSender sender, String name, String corner1, String corner2, Player player) {
        RegionData regionData = plugin.getRegisteredRegions().get(name);
        if (regionData == null) {
            sender.sendMessage(ChatColor.RED + "Region '" + name + "' does not exist.");
            return;
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
                        if (plugin.getConfig().getBoolean("track-entities")) {
                            for (Entity entity : location.getWorld().getNearbyEntities(location, 0.5, 0.5, 0.5)) {
                                regionData.addEntity(location, entity.getType());
                            }
                        }
                    }
                }
            }

            sender.sendMessage(ChatColor.GREEN + "Region '" + name + "' has been updated.");
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid coordinates. Please use integers.");
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "An error occurred while updating the region.");
            plugin.getLogger().severe("Error updating region: " + e.getMessage());
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String @NotNull [] strings) {
        return handleTabComplete(strings);
    }

    private List<String> handleTabComplete(String @NotNull [] args) {
        return switch (args.length) {
            case 1 -> filterSuggestions(List.of("create", "regenerate", "setspawn", "delspawn", "teleport", "tp",
                    "list", "delete", "resize", "reload", "help", "wand", "selection"), args[0]);

            case 2 -> args[0].equalsIgnoreCase("regenerate") || args[0].equalsIgnoreCase("delete")
                    || args[0].equalsIgnoreCase("resize") || args[0].equalsIgnoreCase("setspawn")
                    || args[0].equalsIgnoreCase("delspawn") || args[0].equalsIgnoreCase("teleport")
                    || args[0].equalsIgnoreCase("tp")
                    ? listRegionsForTabComplete(args[1])
                    : args[0].equalsIgnoreCase("create")
                    ? filterSuggestions(List.of("ArenaName"), args[1]) // Suggest Arena Name
                    : args[0].equalsIgnoreCase("wand") || args[0].equalsIgnoreCase("selection")
                    ? getOnlinePlayers(args[1])
                    : List.of();

            default -> List.of();
        };
    }


    private @NotNull List<String> listRegionsForTabComplete(@NotNull String filter) {
        List<String> regionNames = new ArrayList<>();
        String filterLower = filter.toLowerCase();

        for (String regionName : plugin.getRegisteredRegions().keySet()) {
            if (regionName.toLowerCase().startsWith(filterLower)) {
                regionNames.add(regionName);
            }
        }
        return regionNames;
    }

    private @NotNull List<String> getOnlinePlayers(@NotNull String filter) {
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
