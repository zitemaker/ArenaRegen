package com.zitemaker.commands;

import com.zitemaker.ArenaRegen;
import com.zitemaker.helpers.EntitySerializer;
import com.zitemaker.helpers.RegionData;
import org.bukkit.*;
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
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ArenaRegenCommand implements TabExecutor, Listener {

    private final ArenaRegen plugin;
    private final Map<UUID, Vector[]> selections = new HashMap<>();
    private final Map<UUID, BukkitRunnable> expirationTasks = new HashMap<>();
    private static final long ACTION_COOLDOWN_MS = 500;
    private final Map<UUID, Long> lastActionTimes = new HashMap<>();
    private long lastConfigCheck;
    private static final long CONFIG_CHECK_INTERVAL = 1000;

    public ArenaRegenCommand(ArenaRegen plugin) {
        this.plugin = plugin;
    }

    public long arenaSizeLimit;
    private Material wandMaterial;

    @Override
    public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String @NotNull [] strings) {


        FileConfiguration conf = plugin.getConfig();
        FileConfiguration msg = plugin.getMessagesConfig();

        String pluginPrefix = plugin.prefix;

        String incorrectSyntax = ChatColor.translateAlternateColorCodes('&', msg.getString("messages.incorrect-syntax", "&cUnknown arguments. Type \"/arenaregen help\" to see usages"));
        String noPermission = ChatColor.translateAlternateColorCodes('&', msg.getString("messages.no-permission", "&cYou do not have permission to run this command"));
        String onlyForPlayers = ChatColor.translateAlternateColorCodes('&', msg.getString("messages.only-for-players", "&cOnly players can use this command"));
        String regionExists = ChatColor.translateAlternateColorCodes('&', msg.getString("messages.region-exists", "&cA region with this name already exists."));
        String invalidHeight = ChatColor.translateAlternateColorCodes('&', msg.getString("messages.invalid-height", "&cInvalid arena height! Must be between {minHeight} and {maxHeight} blocks."));
        String regionSizeLimit = ChatColor.translateAlternateColorCodes('&', msg.getString("messages.region-size-limit", "&cArena must be within {arena_size_limit} blocks. You can change the size limit in config.yml"));
        arenaSizeLimit = plugin.getConfig().getLong("general.arena-size-limit", 50000L);
        String regionCreated = ChatColor.translateAlternateColorCodes('&', msg.getString("messages.region-created", "&aRegion '{arena_name}' has been successfully registered!"));
        String regionDeleted = ChatColor.translateAlternateColorCodes('&', msg.getString("messages.region-deleted", "&aRegion '{arena_name}' has been successfully deleted!"));
        String regionResized = ChatColor.translateAlternateColorCodes('&', msg.getString("messages.region-resized", "&aRegion '{arena_name}' has been successfully resized!"));
        String arenaNotFound = ChatColor.translateAlternateColorCodes('&', msg.getString("messages.arena-not-found", "&cArena '{arena_name}' does not exist."));
        String regenComplete = ChatColor.translateAlternateColorCodes('&', msg.getString("messages.regen-complete", "&aArena '{arena_name}' has been successfully regenerated!"));
        String spawnSet = ChatColor.translateAlternateColorCodes('&', msg.getString("messages.spawn-set", "&aSpawn point for arena '{arena_name}' has been set!"));
        String spawnDeleted = ChatColor.translateAlternateColorCodes('&', msg.getString("messages.spawn-deleted", "&aSpawn point for arena '{arena_name}' has been removed!"));
        String teleportSuccess = ChatColor.translateAlternateColorCodes('&', msg.getString("messages.teleport-success", "&aTeleported to arena '{arena_name}'."));
        String reloadSuccess = ChatColor.translateAlternateColorCodes('&', msg.getString("messages.reload-success", "&aConfiguration reloaded successfully!"));
        String maxArenasReached = ChatColor.translateAlternateColorCodes('&', msg.getString("messages.max-arenas-reached", "&cMaximum number of arenas ({max_arenas}) reached!"));

        if (commandSender instanceof Player && !ArenaRegen.hasAnyPermissions((Player) commandSender)) {
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
                    plugin.getLogger().info(onlyForPlayers);
                    return true;
                }
                if (!commandSender.hasPermission("arenaregen.create")) {
                    commandSender.sendMessage(noPermission);
                    return true;
                }
                if (strings.length != 2) {
                    commandSender.sendMessage(pluginPrefix + " " + ChatColor.translateAlternateColorCodes('&', "&cUsage: /arenaregen create <arena>"));
                    return true;
                }

                String regionName = strings[1];
                if (plugin.getRegisteredRegions().containsKey(regionName)) {
                    commandSender.sendMessage(regionExists);
                    return true;
                }
                if (plugin.getRegisteredRegions().size() >= plugin.maxArenas) {
                    commandSender.sendMessage(maxArenasReached.replace("{max_arenas}", String.valueOf(plugin.maxArenas)));
                    return true;
                }

                Vector[] selection = getSelection(player);
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
                    commandSender.sendMessage(invalidHeight.replace("{minHeight}", String.valueOf(world.getMinHeight())).replace("{maxHeight}", String.valueOf(world.getMaxHeight())));
                    return true;
                }

                int width = maxX - minX + 1;
                int height = maxY - minY + 1;
                int depth = maxZ - minZ + 1;
                long volume = (long) width * height * depth;
                if (volume > plugin.arenaSize) {
                    commandSender.sendMessage(regionSizeLimit.replace("{arena_size_limit}", String.valueOf(plugin.arenaSize)));
                    return true;
                }

                RegionData regionData = new RegionData(plugin);
                regionData.setMetadata(player.getName(), System.currentTimeMillis(), world.getName(), Bukkit.getVersion(), minX, minY, minZ, width, height, depth);
                plugin.getRegisteredRegions().put(regionName, regionData);

                File datcFile = new File(new File(plugin.getDataFolder(), "arenas"), regionName + ".datc");
                try {
                    regionData.saveToDatc(datcFile);
                } catch (IOException e) {
                    plugin.getLogger().severe("Failed to save " + regionName + ".datc: " + e.getMessage());
                }

                commandSender.sendMessage(ChatColor.YELLOW + "Analyzing and creating region '" + regionName + "', please wait...");

                int blocksPerTick = plugin.analyzeSpeed / 20;
                AtomicInteger blocksProcessed = new AtomicInteger(0);

                if (plugin.trackEntities) {
                    for (Entity entity : world.getEntities()) {
                        Location loc = entity.getLocation();
                        if (loc.getX() >= minX && loc.getX() <= maxX &&
                                loc.getY() >= minY && loc.getY() <= maxY &&
                                loc.getZ() >= minZ && loc.getZ() <= maxZ) {
                            regionData.addEntity(loc, EntitySerializer.serializeEntity(entity));
                        }
                    }
                }

                Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task -> {
                    int start = blocksProcessed.get();
                    int end = Math.min(start + blocksPerTick, (int) volume);

                    for (int i = start; i < end; i++) {
                        int x = minX + (i % width);
                        int y = minY + ((i / width) % height);
                        int z = minZ + (i / (width * height));
                        BlockData blockData = world.getBlockAt(x, y, z).getBlockData();
                        regionData.addBlockToSection("temp", new Location(world, x, y, z), blockData);
                    }

                    if (end >= volume) {
                        task.cancel();
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
                                            sectionBlocks.put(loc, regionData.getSectionedBlockData().get("temp").get(loc));
                                        }
                                    }
                                }

                                regionData.addSection("chunk_" + sectionId++, sectionBlocks);
                            }
                        }

                        Bukkit.getScheduler().runTask(plugin, () -> {
                            try {
                                regionData.saveToDatc(datcFile);
                                plugin.markRegionDirty(regionName);
                                clearSelection(player);
                                commandSender.sendMessage(regionCreated.replace("{arena_name}", regionName));
                            } catch (IOException e) {
                                plugin.getLogger().severe("Failed to save " + regionName + ".datc: " + e.getMessage());
                            }
                        });
                    }
                    blocksProcessed.set(end);
                }, 0L, 1L);
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

                Vector[] selection = getSelection(player);
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
                        minX,
                        minY,
                        minZ,
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
                        clearSelection(player);
                        commandSender.sendMessage(regionResized.replace("{arena_name}", regionName));
                    });
                });
                return true;
            }

            case "regenerate", "regen" -> {
                String showUsage = ChatColor.translateAlternateColorCodes('&', "&cUsage: /arenaregen regenerate <arena>");
                String confirmPrompt = ChatColor.YELLOW + "Are you sure you want to regenerate the region '{arena_name}'? Type '/arenaregen regen confirm' to proceed.";
                String noPending = ChatColor.RED + "No pending region regeneration found. Use '/arenaregen regen <name>' first.";
                String playersInsideCancel = ChatColor.RED + "Regeneration of '{arena_name}' canceled due to players inside the arena.";

                if (!commandSender.hasPermission("arenaregen.regenerate")) {
                    commandSender.sendMessage(noPermission);
                    return true;
                }

                if (strings.length != 2) {
                    commandSender.sendMessage(pluginPrefix + " " + showUsage);
                    return true;
                }

                String input = strings[1];

                Runnable regenerateArena = new Runnable() {
                    private final String arenaName = input;

                    @Override
                    public void run() {
                        RegionData regionData = plugin.getRegisteredRegions().get(arenaName);
                        if (regionData == null) {
                            commandSender.sendMessage(arenaNotFound.replace("{arena_name}", arenaName));
                            return;
                        }

                        World world = Bukkit.getWorld(regionData.worldName);
                        if (world == null) {
                            commandSender.sendMessage(ChatColor.RED + "World '" + regionData.worldName + "' not found.");
                            return;
                        }

                        if (regionData.sectionedBlockData.isEmpty()) {
                            commandSender.sendMessage(ChatColor.RED + "No sections found for region '" + arenaName + "'.");
                            return;
                        }

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

                        final Location finalMin = min;
                        final Location finalMax = max;

                        List<Player> playersInside = new ArrayList<>();
                        for (Player p : world.getPlayers()) {
                            Location loc = p.getLocation();
                            if (loc.getX() >= finalMin.getX() && loc.getX() <= finalMax.getX() &&
                                    loc.getY() >= finalMin.getY() && loc.getY() <= finalMax.getY() &&
                                    loc.getZ() >= finalMin.getZ() && loc.getZ() <= finalMax.getZ()) {
                                playersInside.add(p);
                            }
                        }

                        if (!playersInside.isEmpty()) {
                            if (plugin.cancelRegen) {
                                commandSender.sendMessage(playersInsideCancel.replace("{arena_name}", arenaName));
                                return;
                            }

                            for (Player p : playersInside) {
                                if (plugin.killPlayers) {
                                    p.setHealth(0.0);
                                }
                                if (plugin.teleportToSpawn) {
                                    p.teleport(world.getSpawnLocation());
                                }
                                if (plugin.executeCommands && !plugin.commands.isEmpty()) {
                                    for (String cmd : plugin.commands) {
                                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("{player}", p.getName()));
                                    }
                                }
                            }
                        }
                        int minX = regionData.getMinX();
                        int minY = regionData.getMinY();
                        int minZ = regionData.getMinZ();
                        int maxX = regionData.getMaxX();
                        int maxY = regionData.getMaxY();
                        int maxZ = regionData.getMaxZ();

                        if (plugin.trackEntities) {
                            world.getEntities().stream()
                                    .filter(e -> {
                                        Location loc = e.getLocation();
                                        return loc.getX() >= minX && loc.getX() <= maxX &&
                                                loc.getY() >= minY && loc.getY() <= maxY &&
                                                loc.getZ() >= minZ && loc.getZ() <= maxZ;
                                    })
                                    .forEach(e -> {
                                        if (!(e instanceof Player)) e.remove();
                                    });
                        }

                        commandSender.sendMessage(ChatColor.YELLOW + "Regenerating region '" + arenaName + "', please wait...");
                        int blocksPerTick = plugin.regenType.equals("PRESET") ? switch (plugin.regenSpeed.toUpperCase()) {
                            case "SLOW" -> 1000;
                            case "NORMAL" -> 10000;
                            case "FAST" -> 40000;
                            case "VERYFAST" -> 100000;
                            case "EXTREME" -> 4000000;
                            default -> 10000;
                        } : plugin.customRegenSpeed;

                        AtomicInteger sectionIndex = new AtomicInteger(0);
                        List<String> sectionNames = new ArrayList<>(regionData.sectionedBlockData.keySet());
                        AtomicInteger totalBlocksReset = new AtomicInteger(0);
                        long startTime = System.currentTimeMillis();

                        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
                            int currentSection = sectionIndex.get();
                            if (currentSection >= sectionNames.size()) {
                                if (plugin.trackEntities) {
                                    Map<Location, Map<String, Object>> entityDataMap = regionData.getEntityDataMap();
                                    for (Map.Entry<Location, Map<String, Object>> entry : entityDataMap.entrySet()) {
                                        Location loc = entry.getKey();
                                        Map<String, Object> serializedEntity = entry.getValue();
                                        try {
                                            EntitySerializer.deserializeEntity(serializedEntity, loc);
                                        } catch (Exception e) {
                                            plugin.getLogger().warning("Failed to restore entity at " + loc + ": " + e.getMessage());
                                        }
                                    }
                                }

                                long timeTaken = System.currentTimeMillis() - startTime;
                                commandSender.sendMessage(regenComplete.replace("{arena_name}", arenaName) +
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

                                if (plugin.regenOnlyModified) {
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
                    }
                };

                if (plugin.confirmationPrompt) {
                    if (input.equalsIgnoreCase("confirm")) {
                        String senderName = commandSender.getName();
                        String targetArenaName = plugin.getPendingRegenerations().remove(senderName);
                        if (targetArenaName == null) {
                            commandSender.sendMessage(noPending);
                            return true;
                        }
                        Runnable confirmedRegen = new Runnable() {
                            private final String arenaName = targetArenaName;

                            @Override
                            public void run() {
                                RegionData regionData = plugin.getRegisteredRegions().get(arenaName);
                                if (regionData == null) {
                                    commandSender.sendMessage(arenaNotFound.replace("{arena_name}", arenaName));
                                    return;
                                }

                                World world = Bukkit.getWorld(regionData.worldName);
                                if (world == null) {
                                    commandSender.sendMessage(ChatColor.RED + "World '" + regionData.worldName + "' not found.");
                                    return;
                                }

                                if (regionData.sectionedBlockData.isEmpty()) {
                                    commandSender.sendMessage(ChatColor.RED + "No sections found for region '" + arenaName + "'.");
                                    return;
                                }

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

                                final Location finalMin = min;
                                final Location finalMax = max;

                                List<Player> playersInside = new ArrayList<>();
                                for (Player p : world.getPlayers()) {
                                    Location loc = p.getLocation();
                                    if (loc.getX() >= finalMin.getX() && loc.getX() <= finalMax.getX() &&
                                            loc.getY() >= finalMin.getY() && loc.getY() <= finalMax.getY() &&
                                            loc.getZ() >= finalMin.getZ() && loc.getZ() <= finalMax.getZ()) {
                                        playersInside.add(p);
                                    }
                                }

                                if (!playersInside.isEmpty()) {
                                    if (plugin.cancelRegen) {
                                        commandSender.sendMessage(playersInsideCancel.replace("{arena_name}", arenaName));
                                        return;
                                    }

                                    for (Player p : playersInside) {
                                        if (plugin.killPlayers) {
                                            p.setHealth(0.0);
                                        }
                                        if (plugin.teleportToSpawn) {
                                            p.teleport(world.getSpawnLocation());
                                        }
                                        if (plugin.executeCommands && !plugin.commands.isEmpty()) {
                                            for (String cmd : plugin.commands) {
                                                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("{player}", p.getName()));
                                            }
                                        }
                                    }
                                }

                                commandSender.sendMessage(ChatColor.YELLOW + "Regenerating region '" + arenaName + "', please wait...");
                                int blocksPerTick = plugin.regenType.equals("PRESET") ? switch (plugin.regenSpeed.toUpperCase()) {
                                    case "SLOW" -> 1000;
                                    case "NORMAL" -> 10000;
                                    case "FAST" -> 40000;
                                    case "VERYFAST" -> 100000;
                                    case "EXTREME" -> 4000000;
                                    default -> 10000;
                                } : plugin.customRegenSpeed;

                                AtomicInteger sectionIndex = new AtomicInteger(0);
                                List<String> sectionNames = new ArrayList<>(regionData.sectionedBlockData.keySet());
                                AtomicInteger totalBlocksReset = new AtomicInteger(0);
                                long startTime = System.currentTimeMillis();

                                Bukkit.getScheduler().runTaskTimer(plugin, task -> {
                                    int currentSection = sectionIndex.get();
                                    if (currentSection >= sectionNames.size()) {
                                        if (finalMin != null && finalMax != null && plugin.trackEntities) {
                                            for (Entity entity : world.getEntities()) {
                                                if (!(entity instanceof Player) && (entity instanceof Item || entity.getType() != EntityType.PLAYER)) {
                                                    Location loc = entity.getLocation();
                                                    if (loc.getX() >= finalMin.getX() && loc.getX() <= finalMax.getX() &&
                                                            loc.getY() >= finalMin.getY() && loc.getY() <= finalMax.getY() &&
                                                            loc.getZ() >= finalMin.getZ() && loc.getZ() <= finalMax.getZ()) {
                                                        entity.remove();
                                                    }
                                                }
                                            }
                                        }

                                        long timeTaken = System.currentTimeMillis() - startTime;
                                        commandSender.sendMessage(regenComplete.replace("{arena_name}", arenaName) +
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

                                        if (plugin.regenOnlyModified) {
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
                            }
                        };
                        confirmedRegen.run();
                        return true;
                    } else {
                        String regionName = input;
                        if (!plugin.getRegisteredRegions().containsKey(regionName)) {
                            commandSender.sendMessage(arenaNotFound.replace("{arena_name}", regionName));
                            return true;
                        }
                        plugin.getPendingRegenerations().put(commandSender.getName(), regionName);
                        commandSender.sendMessage(confirmPrompt.replace("{arena_name}", regionName));
                        Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.getPendingRegenerations().remove(commandSender.getName()), 1200L);
                        return true;
                    }
                } else {
                    regenerateArena.run();
                    return true;
                }
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

                plugin.reloadPluginConfig();
                plugin.loadMessagesFile();
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

                giveSelectionTool(player);
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
                    .append(new Date(regionData.getCreationDate()))
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
                    .append(ChatColor.GRAY);

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

    // selection tool stuff
    public void giveSelectionTool(@NotNull Player player) {
        updateWandMaterial();

        ItemStack wandTool = new ItemStack(wandMaterial);
        ItemMeta meta = wandTool.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "Arena Selection Tool");
            wandTool.setItemMeta(meta);
        }

        player.getInventory().addItem(wandTool);
        player.sendMessage(ChatColor.GREEN + "You have been given the Arena Selection Tool (" +
                ChatColor.YELLOW + wandMaterial.name() + ChatColor.GREEN + ")!");
    }

    @EventHandler
    public void onPlayerInteract(@NotNull PlayerInteractEvent event) {
        if (System.currentTimeMillis() - lastConfigCheck > CONFIG_CHECK_INTERVAL) {
            updateWandMaterial();
            lastConfigCheck = System.currentTimeMillis();
        }

        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item.getType() != wandMaterial || !player.hasPermission("arenaregen.select")) return;
        if (event.getClickedBlock() == null) return;

        if (!item.hasItemMeta() || !item.getItemMeta().getDisplayName().equals(ChatColor.GOLD + "Arena Selection Tool")) {
            return;
        }

        UUID playerId = player.getUniqueId();
        Vector clickedPos = event.getClickedBlock().getLocation().toVector();
        long currentTime = System.currentTimeMillis();

        if (currentTime - lastActionTimes.getOrDefault(playerId, 0L) < ACTION_COOLDOWN_MS) {
            event.setCancelled(true);
            return;
        }

        lastActionTimes.put(playerId, currentTime);

        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            event.setCancelled(true);
            handleCornerSelection(playerId, clickedPos, 0, "First");
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            handleCornerSelection(playerId, clickedPos, 1, "Second");
        }
    }

    private void updateWandMaterial() {
        try {
            Material newMaterial = Material.valueOf(plugin.selectionTool);
            if (!newMaterial.isItem()) {
                throw new IllegalArgumentException();
            }
            wandMaterial = newMaterial;
        } catch (IllegalArgumentException e) {
            wandMaterial = Material.GOLDEN_HOE;
        }
    }

    private void handleCornerSelection(UUID playerId, Vector clickedPos, int index, String cornerName) {
        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null) return;

        Vector[] corners = selections.computeIfAbsent(playerId, k -> new Vector[2]);

        if (corners[index] == null || !corners[index].equals(clickedPos)) {
            corners[index] = clickedPos;
            player.sendMessage(ChatColor.GREEN + cornerName + " corner set at: " + formatVector(clickedPos));
            resetExpiration(playerId);
        }
    }

    public Vector[] getSelection(@NotNull Player player) {
        Vector[] corners = selections.get(player.getUniqueId());
        if (corners == null || corners[0] == null || corners[1] == null) {
            player.sendMessage(ChatColor.RED + "Incomplete selection. Use the selection tool to set both corners.");
            return null;
        }
        return corners;
    }

    public void clearSelection(@NotNull Player player) {
        UUID playerId = player.getUniqueId();
        selections.remove(playerId);
        cancelExpiration(playerId);
        lastActionTimes.remove(playerId);
    }

    private void resetExpiration(UUID playerId) {
        cancelExpiration(playerId);
        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                Player player = plugin.getServer().getPlayer(playerId);
                if (player != null) {
                    clearSelection(player);
                } else {
                    selections.remove(playerId);
                    expirationTasks.remove(playerId);
                    lastActionTimes.remove(playerId);
                }
            }
        };
        task.runTaskLater(plugin, 3 * 60 * 20);
        expirationTasks.put(playerId, task);
    }

    private void cancelExpiration(UUID playerId) {
        BukkitRunnable task = expirationTasks.remove(playerId);
        if (task != null) task.cancel();
    }
    private @NotNull String formatVector(@NotNull Vector vector) {
        return "(" + vector.getBlockX() + ", " + vector.getBlockY() + ", " + vector.getBlockZ() + ")";
    }
}