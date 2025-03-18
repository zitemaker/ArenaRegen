package com.zitemaker.commands;

import com.zitemaker.ArenaRegen;
import com.zitemaker.helpers.RegionData;
import com.zitemaker.helpers.SelectionToolListener;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ArenaRegenCommand implements TabExecutor {

    private final ArenaRegen plugin;
    private final SelectionToolListener selectionListener;

    public ArenaRegenCommand(ArenaRegen plugin, SelectionToolListener selectionListener) {
        this.plugin = plugin;
        this.selectionListener = selectionListener;
    }

    int arenaSizeLimit = 40000; // 40,000 blocks size limit for arena creating

    @Override
    public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String @NotNull [] strings) {

        FileConfiguration conf = plugin.getConfig();

        // Plugin prefix
        String pluginPrefix = ChatColor.translateAlternateColorCodes('&', conf.getString("prefix", "&e[&2ArenaRegen&e]"));

        // Messages with placeholders from the config
        String incorrectSyntax = ChatColor.translateAlternateColorCodes('&', conf.getString("messages.incorrect-syntax", "&cUnknown arguments. Type \"/arenaregen help\" to see usages"));
        String noPermission = ChatColor.translateAlternateColorCodes('&', conf.getString("messages.no-permission", "&cYou do not have permission to run this command"));
        String onlyForPlayers = ChatColor.translateAlternateColorCodes('&', conf.getString("messages.only-for-players", "&cOnly players can use this command"));
        String regionExists = ChatColor.translateAlternateColorCodes('&', conf.getString("messages.region-exists", "&cA region with this name already exists."));
        String invalidHeight = ChatColor.translateAlternateColorCodes('&', conf.getString("messages.invalid-height", "&cInvalid arena height! Must be between {minHeight} and {maxHeight}."));
        String regionSizeLimit = ChatColor.translateAlternateColorCodes('&', conf.getString("messages.region-size-limit", "&cArena must be within {arenaSizeLimit} blocks. You can change the size limit in config.yml"));
        String regionCreated = ChatColor.translateAlternateColorCodes('&', conf.getString("messages.region-created", "&aRegion '{arena_name}' has been successfully registered!"));
        String regionDeleted = ChatColor.translateAlternateColorCodes('&', conf.getString("messages.region-deleted", "&aRegion '{arena_name}' has been successfully deleted!"));
        String regionResized = ChatColor.translateAlternateColorCodes('&', conf.getString("messages.region-resized", "&aRegion '{arena_name}' has been successfully resized!"));
        String arenaNotFound = ChatColor.translateAlternateColorCodes('&', conf.getString("messages.arena-not-found", "&cArena '{arena_name}' does not exist."));
        String regenComplete = ChatColor.translateAlternateColorCodes('&', conf.getString("messages.regen-complete", "&aArena '{arena_name}' has been successfully regenerated!"));
        String spawnSet = ChatColor.translateAlternateColorCodes('&', conf.getString("messages.spawn-set", "&aSpawn point for arena '{arena_name}' has been set!"));
        String teleportSuccess = ChatColor.translateAlternateColorCodes('&', conf.getString("messages.teleport-success", "&aTeleported to arena '{arena_name}'."));

        if (!ArenaRegen.hasAnyPermissions((Player) commandSender)) {
            commandSender.sendMessage(noPermission);
            return true;
        }

        if (strings.length == 0) {
            commandSender.sendMessage(incorrectSyntax);
            return true;
        }


        // ALL THE SUB COMMANDS!!

        switch (strings[0]) {

            // CREATE SUBCOMMAND
            case "create" -> {
                String showUsage = ChatColor.translateAlternateColorCodes('&', "&cUsage: /arenaregen delete <arena>");

                if (!(commandSender instanceof Player player)) {
                    plugin.getLogger().info(onlyForPlayers);
                    return true;
                }

                if (!commandSender.hasPermission("arenaregen.create")) {
                    commandSender.sendMessage(noPermission);
                    return true;
                }

                if (strings.length != 2) {
                    commandSender.sendMessage(pluginPrefix + " " + showUsage);
                    return true;
                }

                String regionName = strings[1];
                regionCreated = regionCreated.replace("{arena_name}", regionName);

                if (plugin.getRegisteredRegions().containsKey(regionName)) {
                    commandSender.sendMessage(ChatColor.RED + "A region with this name already exists.");
                    return true;
                }

                Vector[] selection = selectionListener.getSelection(player);
                if (selection == null) {
                    commandSender.sendMessage(ChatColor.RED + "You must select both corners using the selection tool first!");
                    return true;
                }

                World world = player.getWorld();
                int minX = Math.min(selection[0].getBlockX(), selection[1].getBlockX());
                int minY = Math.min(selection[0].getBlockY(), selection[1].getBlockY());
                int minZ = Math.min(selection[0].getBlockZ(), selection[1].getBlockZ());
                int maxX = Math.max(selection[0].getBlockX(), selection[1].getBlockX());
                int maxY = Math.max(selection[0].getBlockY(), selection[1].getBlockY());
                int maxZ = Math.max(selection[0].getBlockZ(), selection[1].getBlockZ());

                if (!isCoordinateHeightValid(world, minY) || !isCoordinateHeightValid(world, maxY)) {
                    commandSender.sendMessage(ChatColor.RED + "Invalid arena height! Must be between " +
                            world.getMinHeight() + " and " + world.getMaxHeight() + ".");
                    return true;
                }

                if (!regionSizeLimit(minX, minZ) || !regionSizeLimit(maxX, maxZ)) {
                    commandSender.sendMessage(ChatColor.RED + "Arena must be within " + arenaSizeLimit + " blocks. " +
                            "You can change the size limit in config.yml");
                    return true;
                }

                RegionData regionData = new RegionData();

                for (int x = minX; x <= maxX; x++) {
                    for (int y = minY; y <= maxY; y++) {
                        for (int z = minZ; z <= maxZ; z++) {
                            Location loc = new Location(world, x, y, z);
                            regionData.addBlock(loc, loc.getBlock().getBlockData());
                        }
                    }
                }

                plugin.getRegisteredRegions().put(regionName, regionData);
                selectionListener.clearSelection(player);
                commandSender.sendMessage(regionCreated);

                return true;
            }


            // ------------------------- = -------------------------

            // DELETE SUB COMMAND
            case "delete" -> {
                String showUsage = ChatColor.translateAlternateColorCodes('&', "&cUsage: /arenaregen delete <arena>");

                if (!commandSender.hasPermission("arenaregen.delete")) {
                    commandSender.sendMessage(noPermission);
                    return true;
                }

                // Check for correct arguments
                if (strings.length != 2) {
                    commandSender.sendMessage(pluginPrefix + " " + showUsage);
                    return true;
                }

                String regionName = strings[1];
                regionDeleted = regionDeleted.replace("{arena_name}", regionName);

                // Check if the region exists
                if (!plugin.getRegisteredRegions().containsKey(regionName)) {
                    commandSender.sendMessage(ChatColor.RED + "No region with this name exists.");
                    return true;
                }

                // First step: Ask for confirmation
                if (strings.length == 2) {
                    plugin.getPendingDeletions().put(commandSender.getName(), regionName);
                    commandSender.sendMessage(ChatColor.YELLOW + "Are you sure you want to delete the region '" + regionName + "'?");
                    commandSender.sendMessage(ChatColor.YELLOW + "Type '/arenaregen delete confirm' to proceed.");

                    // Expire the confirmation after 30 seconds (you can adjust the time)
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (plugin.getPendingDeletions().containsKey(commandSender.getName())) {
                            plugin.getPendingDeletions().remove(commandSender.getName());
                            commandSender.sendMessage(ChatColor.RED + "Arena deletion confirmation has expired.");
                        }
                    }, 600L); // 600L = 30 seconds
                    return true;
                }

                // Second step: Confirm and delete
                if (strings.length == 3 && strings[2].equalsIgnoreCase("confirm")) {
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
                    commandSender.sendMessage(regionDeleted);
                    return true;
                }

                commandSender.sendMessage(ChatColor.RED + "Invalid usage! Type '/arenaregen delete <name>' first, then '/arenaregen delete confirm' to delete.");
                return true;
            }

            // ------------------------- = -------------------------

            // RESIZE SUB COMMAND
            case "resize" -> {
                String showUsage = ChatColor.RED + "Usage: /arenaregen resize <arena>";

                if (!(commandSender instanceof Player player)) {
                    commandSender.sendMessage(onlyForPlayers);
                    return true;
                }

                if (!commandSender.hasPermission("arenaregen.resize")) {
                    commandSender.sendMessage(noPermission);
                    return true;
                }

                if (strings.length != 2) {
                    commandSender.sendMessage(pluginPrefix + " " + showUsage);
                    return true;
                }

                String regionName = strings[1];
                regionResized = regionResized.replace("{arena_name}", regionName);

                if (!plugin.getRegisteredRegions().containsKey(regionName)) {
                    commandSender.sendMessage(ChatColor.RED + "No arena with this name exists.");
                    return true;
                }

                Vector[] selection = selectionListener.getSelection(player);
                if (selection == null || selection[0] == null || selection[1] == null) {
                    commandSender.sendMessage(ChatColor.RED + "You must select both corners using the selection tool first!");
                    return true;
                }

                World world = player.getWorld();
                int minX = Math.min(selection[0].getBlockX(), selection[1].getBlockX());
                int minY = Math.min(selection[0].getBlockY(), selection[1].getBlockY());
                int minZ = Math.min(selection[0].getBlockZ(), selection[1].getBlockZ());
                int maxX = Math.max(selection[0].getBlockX(), selection[1].getBlockX());
                int maxY = Math.max(selection[0].getBlockY(), selection[1].getBlockY());
                int maxZ = Math.max(selection[0].getBlockZ(), selection[1].getBlockZ());

                RegionData regionData = new RegionData();

                // Store new block data
                for (int x = minX; x <= maxX; x++) {
                    for (int y = minY; y <= maxY; y++) {
                        for (int z = minZ; z <= maxZ; z++) {
                            Location loc = new Location(world, x, y, z);
                            regionData.addBlock(loc, loc.getBlock().getBlockData());
                        }
                    }
                }

                // Update the existing region
                plugin.getRegisteredRegions().put(regionName, regionData);
                selectionListener.clearSelection(player);
                commandSender.sendMessage(regionResized);

                return true;
            }


            // ------------------------- = -------------------------

            // REGENERATE SUB COMMAND
            case "regenerate", "regen" -> {
                String showUsage = ChatColor.translateAlternateColorCodes('&', "&cUsage: /arenaregen regenerate <arena>");

                String targetArenaName = strings[1];
                regenComplete = regenComplete.replace("{arena_name}", targetArenaName);
                arenaNotFound = arenaNotFound.replace("{arena_name}", targetArenaName);

                RegionData regionData = plugin.getRegisteredRegions().get(targetArenaName);

                if (!commandSender.hasPermission("arenaregen.regenerate")) {
                    commandSender.sendMessage(noPermission);
                    return true;
                }

                if (strings.length != 2) {
                    commandSender.sendMessage(pluginPrefix + " " + showUsage);
                    return true;
                }

                if (targetArenaName == null) {
                    commandSender.sendMessage(ChatColor.RED + "Please select an arena to regenerate.");
                    return true;
                }

                if (regionData == null) {
                    commandSender.sendMessage(arenaNotFound);
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
                        Block block = Objects.requireNonNull(location.getWorld()).getBlockAt(location);
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

                            for (Entity entity : Objects.requireNonNull(location.getWorld()).getNearbyEntities(location, 0.5, 0.5, 0.5)) {
                                entity.remove();
                            }

                            location.getWorld().spawnEntity(location, entry.getValue());
                        } catch (Exception e) {
                            plugin.getLogger().warning("Failed to restore entity at " + entry.getKey() + ": " + e.getMessage());
                        }
                    }
                }
                commandSender.sendMessage(regenComplete);

                return true;
            }

            // ------------------------- = -------------------------

            // SETSPAWN SUB COMMAND
            case "setspawn" -> {
                String showUsage = ChatColor.translateAlternateColorCodes('&', "&cUsage: /arenaregen setspawn <arena>");
                String targetArenaName = strings[1];
                spawnSet = spawnSet.replace("{arena_name}", targetArenaName);

                if (!(commandSender instanceof Player player)) {
                    commandSender.sendMessage(onlyForPlayers);
                    return true;
                }

                if (strings.length != 2) {
                    commandSender.sendMessage(pluginPrefix + " " + showUsage);
                    return true;
                }

                Location location = player.getLocation();

                RegionData regionData = plugin.getRegisteredRegions().get(targetArenaName);
                if (regionData == null) {
                    commandSender.sendMessage(ChatColor.RED + "Region '" + targetArenaName + "' does not exist.");
                    return true;
                }

                regionData.setSpawnLocation(location);
                commandSender.sendMessage(spawnSet);
            }

            // ------------------------- = -------------------------

            // DELSPAWN SUB COMMAND
            case "delspawn" -> {
                // work in progress4
            }

            // ------------------------- = -------------------------

            // TELEPORT, TP SUB COMMAND
            case "teleport", "tp" -> {
                String showUsage = ChatColor.translateAlternateColorCodes('&', "&cUsage: /arenaregen teleport <arena>");
                String targetArenaName = strings[1];
                teleportSuccess = teleportSuccess.replace("{arena_name}", targetArenaName);

                if (!(commandSender instanceof Player player)) {
                    commandSender.sendMessage(onlyForPlayers);
                    return true;
                }

                if (strings.length != 2) {
                    commandSender.sendMessage(pluginPrefix + " " + showUsage);
                    return true;
                }

                RegionData regionData = plugin.getRegisteredRegions().get(targetArenaName);
                if (regionData == null) {
                    commandSender.sendMessage(ChatColor.RED + "Region '" + targetArenaName + "' does not exist.");
                    return true;
                }

                Location spawnLocation = regionData.getSpawnLocation();
                if (spawnLocation == null) {
                    commandSender.sendMessage(ChatColor.RED + "Please set a spawn point for region '" + targetArenaName + "' first.");
                    return true;
                }

                player.teleport(spawnLocation);
                commandSender.sendMessage(teleportSuccess);
            }

            // ------------------------- = -------------------------

            // LIST SUB COMMAND
            case "list" -> listRegions(commandSender);

            // ------------------------- = -------------------------

            // RELOAD SUB COMMAND
            case "reload" -> {
                // work in progress8
            }

            // ------------------------- = -------------------------

            // HELP SUB COMMAND
            case "help" -> {
                // work in progress9
            }

            // ------------------------- = -------------------------

            // WAND, SELECTION SUB COMMAND
            case "wand", "selection" -> {
                String showUsage = ChatColor.translateAlternateColorCodes('&', "&cUsage: /arenaregen selection");

                if (!(commandSender instanceof Player player)) {
                    commandSender.sendMessage(onlyForPlayers);
                    return true;
                }

                if (strings.length != 1) {
                    commandSender.sendMessage(pluginPrefix + " " + showUsage);
                    return true;
                }

                selectionListener.giveSelectionTool(player);
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

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String @NotNull [] strings) {
        return handleTabComplete(strings);
    }

    private List<String> handleTabComplete(String @NotNull [] args) {
        return switch (args.length) {
            case 1 -> filterSuggestions(List.of("create", "regenerate", "regen", "setspawn", "delspawn", "teleport", "tp",
                    "list", "delete", "resize", "reload", "help", "wand", "selection"), args[0]);

            case 2 -> args[0].equalsIgnoreCase("regenerate") || args[0].equalsIgnoreCase("regen")
                    || args[0].equalsIgnoreCase("delete") || args[0].equalsIgnoreCase("resize")
                    || args[0].equalsIgnoreCase("setspawn") || args[0].equalsIgnoreCase("delspawn")
                    || args[0].equalsIgnoreCase("teleport") || args[0].equalsIgnoreCase("tp")
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
