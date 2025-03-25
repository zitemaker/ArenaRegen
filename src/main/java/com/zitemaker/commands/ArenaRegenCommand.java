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
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ArenaRegenCommand implements TabExecutor {

    private final ArenaRegen plugin;
    private final SelectionToolListener selectionListener;

    public ArenaRegenCommand(ArenaRegen plugin, SelectionToolListener selectionListener) {
        this.plugin = plugin;
        this.selectionListener = selectionListener;
    }

    public long arenaSizeLimit;


    @Override
    public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String @NotNull [] strings) {

        if (!(commandSender instanceof Player)){
            commandSender.sendMessage(ChatColor.RED + "You must be a player to do that.");
            return false;
        }


        FileConfiguration conf = plugin.getConfig();

        String pluginPrefix = ChatColor.translateAlternateColorCodes('&', conf.getString("prefix", "&e[&2ArenaRegen&e]"));

        String incorrectSyntax = ChatColor.translateAlternateColorCodes('&', conf.getString("messages.incorrect-syntax", "&cUnknown arguments. Type \"/arenaregen help\" to see usages"));
        String noPermission = ChatColor.translateAlternateColorCodes('&', conf.getString("messages.no-permission", "&cYou do not have permission to run this command"));
        String onlyForPlayers = ChatColor.translateAlternateColorCodes('&', conf.getString("messages.only-for-players", "&cOnly players can use this command"));
        String regionExists = ChatColor.translateAlternateColorCodes('&', conf.getString("messages.region-exists", "&cA region with this name already exists."));
        String invalidHeight = ChatColor.translateAlternateColorCodes('&', conf.getString("messages.invalid-height", "&cInvalid arena height! Must be between {minHeight} and {maxHeight}."));
        String regionSizeLimit = ChatColor.translateAlternateColorCodes('&', conf.getString("messages.region-size-limit", "&cArena must be within {arenaSizeLimit} blocks. You can change the size limit in config.yml"));
        arenaSizeLimit = plugin.getConfig().getLong("general.arena-size-limit", 1000000L);
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

                if (minY < world.getMinHeight() || maxY > world.getMaxHeight()) {
                    commandSender.sendMessage(ChatColor.RED + "Invalid arena height! Must be between " +
                            world.getMinHeight() + " and " + world.getMaxHeight() + ".");
                    return true;
                }

                int width = maxX - minX + 1;
                int height = maxY - minY + 1;
                int depth = maxZ - minZ + 1;
                long volume = (long) width * height * depth;

                if (volume > arenaSizeLimit) {
                    commandSender.sendMessage(regionSizeLimit.replace("{arenaSizeLimit}", String.valueOf(arenaSizeLimit)));
                    return true;
                }

                RegionData regionData = new RegionData(plugin);
                regionData.setMetadata(
                        player.getName(),
                        System.currentTimeMillis(),
                        world.getName(),
                        Bukkit.getVersion(),
                        width,
                        height,
                        depth
                );

                plugin.getRegisteredRegions().put(regionName, regionData);
                File arenasDir = new File(plugin.getDataFolder(), "arenas");
                arenasDir.mkdirs();
                File datcFile = new File(arenasDir, regionName + ".datc");
                try {
                    regionData.saveToDatc(datcFile);
                } catch (IOException e) {
                    plugin.getLogger().severe("Failed to save " + regionName + ".datc: " + e.getMessage());
                }

                commandSender.sendMessage(ChatColor.YELLOW + "Analyzing and creating region '" + regionName + "', please wait...");

                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    Map<Location, BlockData> allBlocks = new HashMap<>();
                    for (int x = minX; x <= maxX; x++) {
                        for (int y = minY; y <= maxY; y++) {
                            for (int z = minZ; z <= maxZ; z++) {
                                Location loc = new Location(world, x, y, z);
                                BlockData blockData = loc.getBlock().getBlockData();
                                allBlocks.put(loc, blockData);
                            }
                        }
                    }

                    int chunkMinX = minX >> 4;
                    int chunkMinZ = minZ >> 4;
                    int chunkMaxX = maxX >> 4;
                    int chunkMaxZ = maxZ >> 4;
                    int sectionId = 0;

                    for (int chunkX = chunkMinX; chunkX <= chunkMaxX; chunkX++) {
                        for (int chunkZ = chunkMinZ; chunkZ <= chunkMaxZ; chunkZ++) {
                            int xStart = Math.max(chunkX << 4, minX);
                            int zStart = Math.max(chunkZ << 4, minZ);
                            int xEnd = Math.min((chunkX << 4) + 15, maxX);
                            int zEnd = Math.min((chunkZ << 4) + 15, maxZ);
                            Map<Location, BlockData> sectionBlocks = new HashMap<>();

                            for (int x = xStart; x <= xEnd; x++) {
                                for (int y = minY; y <= maxY; y++) {
                                    for (int z = zStart; z <= zEnd; z++) {
                                        Location loc = new Location(world, x, y, z);
                                        sectionBlocks.put(loc, allBlocks.get(loc));
                                    }
                                }
                            }

                            regionData.addSection("chunk_" + sectionId++, sectionBlocks);
                        }
                    }

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        try {
                            regionData.saveToDatc(datcFile);
                        } catch (IOException e) {
                            plugin.getLogger().severe("Failed to save " + regionName + ".datc: " + e.getMessage());
                        }
                        plugin.markRegionDirty(regionName);
                        selectionListener.clearSelection(player);
                        commandSender.sendMessage(regionCreated.replace("{arena_name}", regionName));
                    });
                });

                return true;
            }

            case "delete" -> {
                String showUsage = ChatColor.translateAlternateColorCodes('&', "&cUsage: /arenaregen delete <arena>");


                if (!commandSender.hasPermission("arenaregen.delete")) {
                    commandSender.sendMessage(noPermission);
                    return true;
                }


                if (strings.length != 2) {
                    commandSender.sendMessage(pluginPrefix + " " + showUsage);
                    return true;
                }

                String regionName = strings[1];


                if (!plugin.getRegisteredRegions().containsKey(regionName)) {
                    commandSender.sendMessage(ChatColor.RED + "No region with this name exists.");
                    return true;
                }


                plugin.getPendingDeletions().put(commandSender.getName(), regionName);
                commandSender.sendMessage(ChatColor.YELLOW + "Are you sure you want to delete the region '" + regionName + "'?");
                commandSender.sendMessage(ChatColor.YELLOW + "Type '/arenaregen confirm' to proceed.");


                Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.getPendingDeletions().remove(commandSender.getName()), 1200L);

                return true;
            }

            case "confirm" -> {

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


                RegionData regionData = plugin.getRegisteredRegions().get(confirmedRegion);
                regionData.clearRegion(confirmedRegion);
                plugin.getRegisteredRegions().remove(confirmedRegion);


                Bukkit.getScheduler().runTaskAsynchronously(plugin, plugin::saveRegions);


                regionDeleted = regionDeleted.replace("{arena_name}", confirmedRegion);
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


                RegionData oldRegionData = plugin.getRegisteredRegions().get(regionName);
                RegionData regionData = new RegionData(plugin);


                regionData.setMetadata(
                        oldRegionData.getCreator(),
                        oldRegionData.getCreationDate(),
                        world.getName(),
                        oldRegionData.getMinecraftVersion(),
                        maxX - minX + 1,
                        maxY - minY + 1,
                        maxZ - minZ + 1
                );

                commandSender.sendMessage(ChatColor.YELLOW + "Resizing region '" + regionName + "', please wait...");
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {

                    regionData.clearRegion(regionName);


                    String sectionName = "default";
                    for (int x = minX; x <= maxX; x++) {
                        for (int y = minY; y <= maxY; y++) {
                            for (int z = minZ; z <= maxZ; z++) {
                                Location loc = new Location(world, x, y, z);
                                BlockData blockData = world.getBlockAt(loc).getBlockData();
                                regionData.addBlockToSection(sectionName, loc, blockData);
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

                World world = Bukkit.getWorld(regionData.worldName);
                if (world == null) {
                    commandSender.sendMessage(ChatColor.RED + "World '" + regionData.worldName + "' not found.");
                    return true;
                }

                if (regionData.sectionedBlockData.isEmpty()) {
                    commandSender.sendMessage(ChatColor.RED + "No sections found for region '" + targetArenaName + "'.");
                    return true;
                }

                commandSender.sendMessage(ChatColor.YELLOW + "Regenerating region '" + targetArenaName + "', please wait...");
                int blocksPerTick = plugin.getConfig().getInt("regen.blocks-per-tick", 1000);
                boolean regenOnlyModified = plugin.getConfig().getBoolean("regen.regen-only-modified", false);
                AtomicInteger sectionIndex = new AtomicInteger(0);
                List<String> sectionNames = new ArrayList<>(regionData.sectionedBlockData.keySet());
                AtomicInteger totalBlocksReset = new AtomicInteger(0);
                long startTime = System.currentTimeMillis();

                Bukkit.getScheduler().runTaskTimer(plugin, task -> {
                    int currentSection = sectionIndex.get();
                    if (currentSection >= sectionNames.size()) {
                        Location min = null;
                        Location max = null;
                        for (Map<Location, BlockData> section : regionData.sectionedBlockData.values()) {
                            for (Location loc : section.keySet()) {
                                if (min == null) {
                                    min = new Location(world, loc.getX(), loc.getY(), loc.getZ());
                                    max = new Location(world, loc.getX(), loc.getY(), loc.getZ());
                                } else {
                                    min.setX(Math.min(min.getX(), loc.getX()));
                                    min.setY(Math.min(min.getY(), loc.getY()));
                                    min.setZ(Math.min(min.getZ(), loc.getZ()));
                                    max.setX(Math.max(max.getX(), loc.getX()));
                                    max.setY(Math.max(max.getY(), loc.getY()));
                                    max.setZ(Math.max(max.getZ(), loc.getZ()));
                                }
                            }
                        }

                        if (min != null && max != null) {
                            for (Entity entity : world.getEntities()) {
                                if (!(entity instanceof Player) && (entity instanceof Item || entity.getType() != EntityType.PLAYER)) {
                                    Location loc = entity.getLocation();
                                    if (loc.getX() >= min.getX() && loc.getX() <= max.getX() &&
                                            loc.getY() >= min.getY() && loc.getY() <= max.getY() &&
                                            loc.getZ() >= min.getZ() && loc.getZ() <= max.getZ()) {
                                        entity.remove();
                                    }
                                }
                            }
                        }

                        long timeTaken = System.currentTimeMillis() - startTime;
                        commandSender.sendMessage(regenComplete.replace("{arena_name}", targetArenaName) +
                                ChatColor.GRAY + " (" + totalBlocksReset.get() + " blocks reset in " + (timeTaken / 1000.0) + "s)");
                        task.cancel();
                        return;
                    }

                    String sectionName = sectionNames.get(currentSection);
                    Map<Location, BlockData> section = regionData.sectionedBlockData.get(sectionName);
                    List<Map.Entry<Location, BlockData>> blockList = new ArrayList<>(section.entrySet());
                    int blockIndex = 0;

                    while (blockIndex < blockList.size() && totalBlocksReset.get() < blocksPerTick * (currentSection + 1)) {
                        Map.Entry<Location, BlockData> entry = blockList.get(blockIndex);
                        Location loc = entry.getKey();
                        loc.setWorld(world);
                        Block block = world.getBlockAt(loc);
                        BlockData originalData = entry.getValue();
                        BlockData currentData = block.getBlockData();

                        if (regenOnlyModified) {
                            if (!currentData.equals(originalData)) {
                                block.setBlockData(originalData, false);
                                totalBlocksReset.incrementAndGet();
                            }
                        } else {
                            block.setBlockData(originalData, false);
                            totalBlocksReset.incrementAndGet();
                        }
                        blockIndex++;
                    }

                    if (blockIndex >= blockList.size()) {
                        sectionIndex.incrementAndGet();
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


    private boolean isRegionSizeValid(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        arenaSizeLimit = plugin.getConfig().getLong("general.arena-size-limit", 1000000L);
        int width = maxX - minX + 1;
        int height = maxY - minY + 1;
        int depth = maxZ - minZ + 1;
        long volume = (long) width * height * depth;
        return volume <= arenaSizeLimit;
    }

    /* stupid method
    private boolean regionSizeLimit(int x, int z) {
        return (x * z) <= arenaSizeLimit;
    }

     */

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

            Location min = null;
            Location max = null;
            World world = Bukkit.getWorld(regionData.worldName);

            if (world == null) {
                sender.sendMessage(ChatColor.RED + "- " + name + ": World '" + regionData.worldName + "' not found");
                continue;
            }

            for (Map<Location, BlockData> section : regionData.sectionedBlockData.values()) {
                for (Location loc : section.keySet()) {
                    if (min == null) {
                        min = new Location(world, loc.getX(), loc.getY(), loc.getZ());
                        max = new Location(world, loc.getX(), loc.getY(), loc.getZ());
                    } else {
                        min.setX(Math.min(min.getX(), loc.getX()));
                        min.setY(Math.min(min.getY(), loc.getY()));
                        min.setZ(Math.min(min.getZ(), loc.getZ()));
                        max.setX(Math.max(max.getX(), loc.getX()));
                        max.setY(Math.max(max.getY(), loc.getY()));
                        max.setZ(Math.max(max.getZ(), loc.getZ()));
                    }
                }
            }

            StringBuilder message = new StringBuilder();
            message.append(ChatColor.GREEN).append("- ").append(name);

            if (min != null && max != null) {
                message.append(String.format(": (%d, %d, %d) to (%d, %d, %d)",
                        (int) min.getX(), (int) min.getY(), (int) min.getZ(),
                        (int) max.getX(), (int) max.getY(), (int) max.getZ()));
            } else {
                message.append(": No block data");
            }

            message.append(ChatColor.GRAY)
                    .append("\n  Creator: ").append(ChatColor.WHITE).append(regionData.getCreator())
                    .append(ChatColor.GRAY)
                    .append("\n  Created: ").append(ChatColor.WHITE)
                    .append(new Date(regionData.getCreationDate()).toString())
                    .append(ChatColor.GRAY)
                    .append("\n  World: ").append(ChatColor.WHITE).append(regionData.getWorldName())
                    .append(ChatColor.GRAY)
                    .append("\n  Version: ").append(ChatColor.WHITE).append(regionData.getMinecraftVersion())
                    .append(ChatColor.GRAY)
                    .append("\n  Dimensions: ").append(ChatColor.WHITE)
                    .append(regionData.getWidth()).append("x")
                    .append(regionData.getHeight()).append("x")
                    .append(regionData.getDepth())
                    .append(ChatColor.GRAY)
                    .append("\n  Sections: ").append(ChatColor.WHITE)
                    .append(regionData.getSectionedBlockData().size())
                    .append(ChatColor.GRAY)
                    .append("\n  Block Types: ").append(ChatColor.WHITE)
                    .append(regionData.getBlockTypes().size());

            sender.sendMessage(message.toString());
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