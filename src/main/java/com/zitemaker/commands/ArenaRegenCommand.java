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
import java.util.concurrent.atomic.AtomicInteger;

public class ArenaRegenCommand implements TabExecutor {

    private final ArenaRegen plugin;
    private final SelectionToolListener selectionListener;

    public ArenaRegenCommand(ArenaRegen plugin, SelectionToolListener selectionListener) {
        this.plugin = plugin;
        this.selectionListener = selectionListener;
    }

    int arenaSizeLimit = 40000;

    @Override
    public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String @NotNull [] strings) {

        FileConfiguration conf = plugin.getConfig();

        String pluginPrefix = ChatColor.translateAlternateColorCodes('&', conf.getString("prefix", "&e[&2ArenaRegen&e]"));

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
        String spawnDeleted = ChatColor.translateAlternateColorCodes('&', conf.getString("messages.spawn-deleted", "&aSpawn point for arena '{arena_name}' has been removed!"));
        String teleportSuccess = ChatColor.translateAlternateColorCodes('&', conf.getString("messages.teleport-success", "&aTeleported to arena '{arena_name}'."));
        String reloadSuccess = ChatColor.translateAlternateColorCodes('&', conf.getString("messages.reload-success", "&aConfiguration reloaded successfully!"));

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
                String showUsage = ChatColor.translateAlternateColorCodes('&', "&cUsage: /arenaregen create <arena>");

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

                RegionData regionData = new RegionData(plugin);
                commandSender.sendMessage(ChatColor.YELLOW + "Creating region '" + regionName + "', please wait...");
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    for (int x = minX; x <= maxX; x++) {
                        for (int y = minY; y <= maxY; y++) {
                            for (int z = minZ; z <= maxZ; z++) {
                                Location loc = new Location(world, x, y, z);
                                regionData.addBlock(loc, loc.getBlock().getBlockData());
                            }
                        }
                    }
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        plugin.getRegisteredRegions().put(regionName, regionData);
                        plugin.markRegionDirty(regionName);
                        Bukkit.getScheduler().runTaskAsynchronously(plugin, plugin::saveRegions);
                        selectionListener.clearSelection(player);
                        commandSender.sendMessage(regionCreated.replace("{arena_name}", regionName));
                    });
                });
                return true;
            }

            case "delete" -> {
                String showUsage = ChatColor.translateAlternateColorCodes('&', "&cUsage: /arenaregen delete <arena>");

                // Check permission
                if (!commandSender.hasPermission("arenaregen.delete")) {
                    commandSender.sendMessage(noPermission);
                    return true;
                }

                // Check if the command has the correct number of arguments
                if (strings.length != 2) {
                    commandSender.sendMessage(pluginPrefix + " " + showUsage);
                    return true;
                }

                String regionName = strings[1];

                // Check if the region exists
                if (!plugin.getRegisteredRegions().containsKey(regionName)) {
                    commandSender.sendMessage(ChatColor.RED + "No region with this name exists.");
                    return true;
                }

                // Add the region to pending deletions
                plugin.getPendingDeletions().put(commandSender.getName(), regionName);
                commandSender.sendMessage(ChatColor.YELLOW + "Are you sure you want to delete the region '" + regionName + "'?");
                commandSender.sendMessage(ChatColor.YELLOW + "Type '/arenaregen confirm' to proceed.");

                // Schedule removal of the pending deletion after 60 seconds (1200 ticks)
                Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.getPendingDeletions().remove(commandSender.getName()), 1200L);

                return true;
            }

            case "confirm" -> {
                // Check if the sender has a pending deletion
                String senderName = commandSender.getName();

                if (!plugin.getPendingDeletions().containsKey(senderName)) {
                    commandSender.sendMessage(ChatColor.RED + "No pending region deletion found. Use '/arenaregen delete <name>' first.");
                    return true;
                }

                // Get the region name from pending deletions
                String confirmedRegion = plugin.getPendingDeletions().remove(senderName);

                // Check if the region still exists
                if (!plugin.getRegisteredRegions().containsKey(confirmedRegion)) {
                    commandSender.sendMessage(ChatColor.RED + "The region '" + confirmedRegion + "' no longer exists.");
                    return true;
                }

                // Delete the region
                RegionData regionData = plugin.getRegisteredRegions().get(confirmedRegion);
                regionData.clearRegion(confirmedRegion);
                plugin.getRegisteredRegions().remove(confirmedRegion);

                // Save the changes asynchronously
                Bukkit.getScheduler().runTaskAsynchronously(plugin, plugin::saveRegions);

                // Notify the sender
                regionDeleted = regionDeleted.replace("{arena_name}", confirmedRegion); // Replace placeholder with the actual region name
                commandSender.sendMessage(regionDeleted);

                return true;
            }

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

                RegionData regionData = new RegionData(plugin);
                commandSender.sendMessage(ChatColor.YELLOW + "Resizing region '" + regionName + "', please wait...");
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    regionData.clearRegion(regionName);
                    for (int x = minX; x <= maxX; x++) {
                        for (int y = minY; y <= maxY; y++) {
                            for (int z = minZ; z <= maxZ; z++) {
                                Location loc = new Location(world, x, y, z);
                                regionData.addBlock(loc, loc.getBlock().getBlockData());
                            }
                        }
                    }
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        plugin.getRegisteredRegions().put(regionName, regionData);
                        plugin.markRegionDirty(regionName);
                        Bukkit.getScheduler().runTaskAsynchronously(plugin, plugin::saveRegions);
                        selectionListener.clearSelection(player);
                        commandSender.sendMessage(regionResized.replace("{arena_name}", regionName));
                    });
                });
                return true;
            }

            case "regenerate", "regen" -> {
                String showUsage = ChatColor.translateAlternateColorCodes('&', "&cUsage: /arenaregen regenerate <arena>");

                if (!commandSender.hasPermission("arenaregen.regenerate")) {
                    commandSender.sendMessage(noPermission);
                    return true;
                }

                if (strings.length != 2) {
                    commandSender.sendMessage(pluginPrefix + " " + showUsage);
                    return true;
                }

                String targetArenaName = strings[1];

                RegionData regionData = plugin.getRegisteredRegions().get(targetArenaName);
                if (regionData == null) {
                    commandSender.sendMessage(arenaNotFound.replace("{arena_name}", targetArenaName));
                    return true;
                }

                Location referenceLocation = commandSender instanceof Player ?
                        ((Player) commandSender).getLocation() :
                        regionData.getBlockDataMap().keySet().stream().findFirst().orElse(null);

                if (referenceLocation == null || referenceLocation.getWorld() == null) {
                    commandSender.sendMessage(ChatColor.RED + "Invalid region data.");
                    return true;
                }


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

                if (min == null || max == null) {
                    commandSender.sendMessage(ChatColor.RED + "Region bounds could not be determined.");
                    return true;
                }

                min.setWorld(referenceLocation.getWorld());
                max.setWorld(referenceLocation.getWorld());

                List<Map.Entry<Location, BlockData>> blockList = new ArrayList<>(regionData.getBlockDataMap().entrySet());
                int blocksPerTick = 1000;
                AtomicInteger index = new AtomicInteger(0);

                commandSender.sendMessage(ChatColor.YELLOW + "Regenerating region '" + targetArenaName + "', please wait...");
                Bukkit.getScheduler().runTaskTimer(plugin, task -> {
                    int start = index.get();
                    int end = Math.min(start + blocksPerTick, blockList.size());
                    for (int i = start; i < end; i++) {
                        Map.Entry<Location, BlockData> entry = blockList.get(i);
                        Location loc = entry.getKey();
                        loc.setWorld(referenceLocation.getWorld());
                        Block block = Objects.requireNonNull(loc.getWorld()).getBlockAt(loc);
                        block.setBlockData(entry.getValue(), false);
                    }
                    index.set(end);
                    if (index.get() >= blockList.size()) {

                        for (Entity entity : referenceLocation.getWorld().getEntities()) {
                             if (!(entity instanceof Player)) {
                                Location loc = entity.getLocation();
                                if (loc.getX() >= min.getX() && loc.getX() <= max.getX() &&
                                        loc.getY() >= min.getY() && loc.getY() <= max.getY() &&
                                        loc.getZ() >= min.getZ() && loc.getZ() <= max.getZ()) {
                                    entity.remove();
                                }
                            }
                        }

                        if (plugin.getConfig().getBoolean("track-entities", true)) {
                            for (Map.Entry<Location, EntityType> entry : regionData.getEntityMap().entrySet()) {
                                Location loc = entry.getKey();
                                loc.setWorld(referenceLocation.getWorld());
                                Objects.requireNonNull(loc.getWorld()).spawnEntity(loc, entry.getValue());
                            }
                        }
                        commandSender.sendMessage(regenComplete.replace("{arena_name}", targetArenaName));
                        task.cancel();
                    }
                }, 0L, 1L);
                return true;
            }

            case "setspawn" -> {
                String showUsage = ChatColor.translateAlternateColorCodes('&', "&cUsage: /arenaregen setspawn <arena>");
                String targetArenaName = strings[1];
                spawnSet = spawnSet.replace("{arena_name}", targetArenaName);

                if (!(commandSender instanceof Player player)) {
                    commandSender.sendMessage(onlyForPlayers);
                    return true;
                }

                if (!commandSender.hasPermission("arenaregen.setspawn")) {
                    commandSender.sendMessage(noPermission);
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
                plugin.markRegionDirty(targetArenaName);
                Bukkit.getScheduler().runTaskAsynchronously(plugin, plugin::saveRegions);
                commandSender.sendMessage(spawnSet);
                return true;
            }

            case "delspawn" -> {
                String showUsage = ChatColor.translateAlternateColorCodes('&', "&cUsage: /arenaregen delspawn <arena>");
                String targetArenaName = strings[1];
                spawnDeleted = spawnDeleted.replace("{arena_name}", targetArenaName);

                if (!commandSender.hasPermission("arenaregen.delspawn")) {
                    commandSender.sendMessage(noPermission);
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

                regionData.setSpawnLocation(null);
                plugin.markRegionDirty(targetArenaName);
                Bukkit.getScheduler().runTaskAsynchronously(plugin, plugin::saveRegions);
                commandSender.sendMessage(spawnDeleted);
                return true;
            }

            case "teleport", "tp" -> {
                String showUsage = ChatColor.translateAlternateColorCodes('&', "&cUsage: /arenaregen teleport <arena>");
                String targetArenaName = strings[1];
                teleportSuccess = teleportSuccess.replace("{arena_name}", targetArenaName);

                if (!(commandSender instanceof Player player)) {
                    commandSender.sendMessage(onlyForPlayers);
                    return true;
                }

                if (!commandSender.hasPermission("arenaregen.teleport")) {
                    commandSender.sendMessage(noPermission);
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
                return true;
            }

            case "list" -> {
                if (!commandSender.hasPermission("arenaregen.list")) {
                    commandSender.sendMessage(noPermission);
                    return true;
                }
                listRegions(commandSender);
                return true;
            }

            case "reload" -> {
                if (!commandSender.hasPermission("arenaregen.reload")) {
                    commandSender.sendMessage(noPermission);
                    return true;
                }

                plugin.reloadConfig();
                plugin.getRegisteredRegions().clear();
                plugin.loadRegions();
                commandSender.sendMessage(reloadSuccess);
                return true;
            }

            case "help" -> {
                if (!commandSender.hasPermission("arenaregen.help")) {
                    commandSender.sendMessage(noPermission);
                    return true;
                }

                commandSender.sendMessage(ChatColor.GOLD + "=== ArenaRegen Commands ===");
                commandSender.sendMessage(ChatColor.GREEN + "/arenaregen create <arena> - Create a new arena");
                commandSender.sendMessage(ChatColor.GREEN + "/arenaregen delete <arena> - Delete an arena");
                commandSender.sendMessage(ChatColor.GREEN + "/arenaregen resize <arena> - Resize an arena");
                commandSender.sendMessage(ChatColor.GREEN + "/arenaregen regenerate <arena> - Regenerate an arena");
                commandSender.sendMessage(ChatColor.GREEN + "/arenaregen setspawn <arena> - Set spawn for an arena");
                commandSender.sendMessage(ChatColor.GREEN + "/arenaregen delspawn <arena> - Remove spawn from an arena");
                commandSender.sendMessage(ChatColor.GREEN + "/arenaregen teleport <arena> - Teleport to an arena");
                commandSender.sendMessage(ChatColor.GREEN + "/arenaregen list - List all arenas");
                commandSender.sendMessage(ChatColor.GREEN + "/arenaregen reload - Reload configuration");
                commandSender.sendMessage(ChatColor.GREEN + "/arenaregen help - Show this help");
                commandSender.sendMessage(ChatColor.GREEN + "/arenaregen wand - Get the selection tool");
                return true;
            }

            case "wand", "selection" -> {
                String showUsage = ChatColor.translateAlternateColorCodes('&', "&cUsage: /arenaregen selection");

                if (!(commandSender instanceof Player player)) {
                    commandSender.sendMessage(onlyForPlayers);
                    return true;
                }

                if (!commandSender.hasPermission("arenaregen.select")) {
                    commandSender.sendMessage(noPermission);
                    return true;
                }

                if (strings.length != 1) {
                    commandSender.sendMessage(pluginPrefix + " " + showUsage);
                    return true;
                }

                selectionListener.giveSelectionTool(player);
                return true;
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
                    ? filterSuggestions(List.of("ArenaName"), args[1])
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