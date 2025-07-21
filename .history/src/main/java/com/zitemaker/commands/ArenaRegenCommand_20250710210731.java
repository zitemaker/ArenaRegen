package com.zitemaker.commands;

import com.zitemaker.ArenaRegen;
import com.zitemaker.helpers.EntitySerializer;
import com.zitemaker.helpers.RegionData;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
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

import java.awt.*;
import java.io.File;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
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
        String maxArenasReached = ChatColor.GOLD + "You have reached the maximum number of arenas that can be created in " + ChatColor.YELLOW + "ArenaRegen.";
        String purchaseMessage = "Click here to purchase ArenaRegen+!";

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
                    commandSender.sendMessage(ChatColor.BOLD + maxArenasReached);
                    TextComponent message = new TextComponent(purchaseMessage);
                    message.setColor(net.md_5.bungee.api.ChatColor.GREEN);
                    message.setBold(true);
                    message.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://www.spigotmc.org/resources/arenaregen-automatically-regenerate-your-pvp-arenas.124624/"));
                    commandSender.spigot().sendMessage(message);
                    return true;
                }

                Vector[] selection = getSelection(player);
                if (selection == null || selection[0] == null || selection[1] == null) {
                    commandSender.sendMessage(pluginPrefix + ChatColor.RED + " You must select both corners using the selection tool first!");
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
                    commandSender.sendMessage(pluginPrefix + " " + invalidHeight.replace("{minHeight}", String.valueOf(world.getMinHeight())).replace("{maxHeight}", String.valueOf(world.getMaxHeight())));
                    return true;
                }

                int width = maxX - minX + 1;
                int height = maxY - minY + 1;
                int depth = maxZ - minZ + 1;
                long volume = (long) width * height * depth;

                File datcFile = new File(plugin.getDataFolder(), "regions/" + regionName + ".datc");
                RegionData regionData = new RegionData(plugin);
                plugin.getRegisteredRegions().put(regionName, regionData);
                AtomicBoolean creationCompleted = new AtomicBoolean(false);
                AtomicBoolean timeoutOccurred = new AtomicBoolean(false);
                final int[] processedBlocks = {0};
                final long[] lastProgressTime = {System.currentTimeMillis()};

                boolean useStreaming = RegionData.forceStreaming || volume > 1000000;
                if (useStreaming) {
                    if (volume > 1000000) {
                        commandSender.sendMessage(pluginPrefix + ChatColor.GOLD + " Warning: This is a very large region (" + volume + " blocks). Processing may take a while and use significant memory.");
                    }

                    regionData.startStreamingDatc(datcFile)
                            .thenRun(() -> {
                                Bukkit.getScheduler().runTask(plugin, () -> commandSender.sendMessage(pluginPrefix + ChatColor.YELLOW + " [DEBUG] Starting chunk processing for region '" + regionName + "'..."));
                                int chunkMinX = minX >> 4;
                                int chunkMinZ = minZ >> 4;
                                int chunkMaxX = maxX >> 4;
                                int chunkMaxZ = maxZ >> 4;
                                int totalChunks = (chunkMaxX - chunkMinX + 1) * (chunkMaxZ - chunkMinZ + 1);
                                final int[] processedChunks = {0};
                                final long[] lastChunkProgressTime = {System.currentTimeMillis()};
                                final int progressInterval = plugin.getConfig().getInt("general.large-region-settings.progress-update-interval-seconds", 5) * 1000;
                                final int[] sectionId = {0};

                                CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
                                for (int chunkX = chunkMinX; chunkX <= chunkMaxX; chunkX++) {
                                    for (int chunkZ = chunkMinZ; chunkZ <= chunkMaxZ; chunkZ++) {
                                        final int cx = chunkX;
                                        final int cz = chunkZ;
                                        final int thisSectionId = sectionId[0]++;
                                        chain = chain.thenCompose(v -> CompletableFuture.supplyAsync(() -> {
                                            Map<Location, BlockData> sectionBlocks = new HashMap<>();
                                            for (int x = Math.max(cx << 4, minX); x <= Math.min((cx << 4) + 15, maxX); x++) {
                                                for (int y = minY; y <= maxY; y++) {
                                                    for (int z = Math.max(cz << 4, minZ); z <= Math.min((cz << 4) + 15, maxZ); z++) {
                                                        Location loc = new Location(world, x, y, z);
                                                        BlockData blockData = world.getBlockAt(x, y, z).getBlockData();
                                                        sectionBlocks.put(loc, blockData);
                                                    }
                                                }
                                            }
                                            return sectionBlocks;
                                        })).thenCompose(sectionBlocks -> regionData.writeChunkToDatc("chunk_" + thisSectionId, sectionBlocks, datcFile, true)
                                                .thenRun(() -> {
                                                    processedChunks[0]++;
                                                    long currentTime = System.currentTimeMillis();
                                                    if ((currentTime - lastChunkProgressTime[0]) >= progressInterval) {
                                                        int progress = (processedChunks[0] * 100) / totalChunks;
                                                        Bukkit.getScheduler().runTask(plugin, () -> commandSender.sendMessage(pluginPrefix + ChatColor.GRAY + " Chunk Processing: " + progress + "% (" + processedChunks[0] + "/" + totalChunks + " chunks)"));
                                                        lastChunkProgressTime[0] = currentTime;
                                                    }
                                                    if (processedChunks[0] % 10 == 0) {
                                                        try { Thread.sleep(1); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                                                    }
                                                    if (processedChunks[0] % 50 == 0) {
                                                        System.gc();
                                                    }
                                                }));
                                    }
                                }

                                chain.thenCompose(v -> regionData.finalizeStreamingDatc(datcFile, regionData.getEntityDataMap(), regionData.getBannerStates(), regionData.getSignStates(), regionData.getModifiedBlocks()))
                                        .thenRun(() -> {
                                            creationCompleted.set(true);
                                            Bukkit.getScheduler().runTask(plugin, () -> {
                                                plugin.markRegionDirty(regionName);
                                                clearSelection(player);
                                                commandSender.sendMessage(regionCreated.replace("{arena_name}", regionName));
                                            });
                                        })
                                        .exceptionally(e -> {
                                            creationCompleted.set(true);
                                            Bukkit.getScheduler().runTask(plugin, () -> {
                                                commandSender.sendMessage(pluginPrefix + ChatColor.RED + " [ERROR] Region creation failed: " + e.getMessage());
                                                plugin.getRegisteredRegions().remove(regionName);
                                                e.printStackTrace();
                                            });
                                            return null;
                                        });
                            })
                            .exceptionally(e -> {
                                Bukkit.getScheduler().runTask(plugin, () -> commandSender.sendMessage(pluginPrefix + ChatColor.RED + " Failed to start streaming region file: " + e.getMessage()));
                                plugin.getRegisteredRegions().remove(regionName);
                                e.printStackTrace();
                                return null;
                            });
                    return true;
                }
                else {
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                        Bukkit.getScheduler().runTask(plugin, () -> commandSender.sendMessage(pluginPrefix + ChatColor.YELLOW + " Analyzing and creating region '" + regionName + "', please wait..."));

                        List<Entity> entitiesInWorld = new ArrayList<>();
                        if (plugin.trackEntities) {
                            entitiesInWorld.addAll(world.getEntities());
                        }

                        if (plugin.trackEntities) {
                            for (Entity entity : entitiesInWorld) {
                                if (timeoutOccurred.get()) {
                                    return;
                                }
                                Location loc = entity.getLocation();
                                if (loc.getX() >= minX && loc.getX() <= maxX &&
                                        loc.getY() >= minY && loc.getY() <= maxY &&
                                        loc.getZ() >= minZ && loc.getZ() <= maxZ) {
                                    Map<String, Object> serialized = EntitySerializer.serializeEntity(entity);
                                    if (serialized != null) {
                                        regionData.addEntity(loc, serialized);
                                    }
                                }
                            }
                        }

                        final int CHUNK_SIZE = volume > 1000000 ? 5000 : 10000;
                        final int progressInterval = plugin.getConfig().getInt("general.large-region-settings.progress-update-interval-seconds", 5) * 1000;

                        for (int x = minX; x <= maxX; x++) {
                            if (timeoutOccurred.get()) {
                                return;
                            }
                            for (int y = minY; y <= maxY; y++) {
                                for (int z = minZ; z <= maxZ; z++) {
                                    Location loc = new Location(world, x, y, z);
                                    BlockData blockData = world.getBlockAt(x, y, z).getBlockData();
                                    regionData.addBlockToSection("temp", loc, blockData);
                                    processedBlocks[0]++;

                                    long currentTime = System.currentTimeMillis();
                                    if (volume > 1000000 && (currentTime - lastProgressTime[0]) >= progressInterval) {
                                        int progress = (processedBlocks[0] * 100) / (int) volume;
                                        Bukkit.getScheduler().runTask(plugin, () -> commandSender.sendMessage(pluginPrefix + ChatColor.GRAY + " Block Analysis: " + progress + "% (" + processedBlocks[0] + "/" + volume + " blocks)"));
                                        lastProgressTime[0] = currentTime;
                                    }

                                    int gcFreq = plugin.getConfig().getInt("general.large-region-settings.gc-frequency", 100000);
                                    if (volume > 1000000 && processedBlocks[0] % gcFreq == 0) {
                                        System.gc();
                                    }
                                }
                            }
                        }

                        if (timeoutOccurred.get()) {
                            return;
                        }

                        regionData.setBlockDataLoaded(true);

                        Map<Location, BlockData> tempSection = regionData.sectionedBlockData.get("temp");
                        if (tempSection == null || tempSection.isEmpty()) {
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                commandSender.sendMessage(pluginPrefix + ChatColor.RED + " Failed to create arena: No blocks were analyzed.");
                                plugin.getRegisteredRegions().remove(regionName);
                            });
                            return;
                        }

                        Bukkit.getScheduler().runTask(plugin, () -> commandSender.sendMessage(pluginPrefix + ChatColor.YELLOW + " Processing chunks and organizing data..."));
                        if (volume > 1000000) {
                            Bukkit.getScheduler().runTask(plugin, () -> commandSender.sendMessage(pluginPrefix + ChatColor.GOLD + " Note: Chunk processing for huge regions can be CPU intensive. This is normal."));
                        }

                        int chunkMinX = minX >> 4;
                        int chunkMinZ = minZ >> 4;
                        int chunkMaxX = maxX >> 4;
                        int chunkMaxZ = maxZ >> 4;
                        int sectionId = 0;
                        final int[] processedChunks = {0};
                        int totalChunks = (chunkMaxX - chunkMinX + 1) * (chunkMaxZ - chunkMinZ + 1);
                        final long[] lastChunkProgressTime = {System.currentTimeMillis()};

                        for (int chunkX = chunkMinX; chunkX <= chunkMaxX; chunkX++) {
                            if (timeoutOccurred.get()) {
                                return;
                            }
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
                                            BlockData blockData = tempSection.get(loc);
                                            if (blockData != null) {
                                                sectionBlocks.put(loc, blockData);
                                            }
                                        }
                                    }
                                }
                                if (!sectionBlocks.isEmpty()) {
                                    regionData.addSection("chunk_" + sectionId++, sectionBlocks);
                                }
                                processedChunks[0]++;

                                long currentTime = System.currentTimeMillis();
                                if (volume > 1000000 && (currentTime - lastChunkProgressTime[0]) >= progressInterval) {
                                    int progress = (processedChunks[0] * 100) / totalChunks;
                                    Bukkit.getScheduler().runTask(plugin, () -> commandSender.sendMessage(pluginPrefix + ChatColor.GRAY + " Chunk Processing: " + progress + "% (" + processedChunks[0] + "/" + totalChunks + " chunks)"));
                                    lastChunkProgressTime[0] = currentTime;
                                }
                                
                                if (volume > 1000000 && processedChunks[0] % 10 == 0) {
                                    try {
                                        Thread.sleep(1);
                                    } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                        return;
                                    }
                                }

                                if (volume > 1000000 && processedChunks[0] % 50 == 0) {
                                    System.gc();
                                }
                            }
                        }

                        if (timeoutOccurred.get()) {
                            return;
                        }

                        regionData.saveToDatc(datcFile).thenRun(() -> {
                            creationCompleted.set(true);
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                plugin.markRegionDirty(regionName);
                                clearSelection(player);
                                commandSender.sendMessage(regionCreated.replace("{arena_name}", regionName));
                            });
                        }).exceptionally(e -> {
                            creationCompleted.set(true);
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                commandSender.sendMessage(pluginPrefix + ChatColor.RED + " Failed to save arena: " + e.getMessage());
                                plugin.getRegisteredRegions().remove(regionName);
                            });
                            return null;
                        });
                    });
                }

                int arenaCreationTimeout = plugin.arenaCreationTimeout;
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!creationCompleted.get()) {
                        timeoutOccurred.set(true);
                        commandSender.sendMessage(pluginPrefix + ChatColor.RED + " Arena creation timed out after " + arenaCreationTimeout + " seconds. Try a smaller region or increase the timeout in config.yml.");
                        plugin.getRegisteredRegions().remove(regionName);
                    }
                }, arenaCreationTimeout * 20L);

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
                    commandSender.sendMessage(pluginPrefix + " " + ChatColor.RED + "No region with this name exists.");
                    return true;
                }


                plugin.getPendingDeletions().put(commandSender.getName(), regionName);
                commandSender.sendMessage(pluginPrefix + ChatColor.YELLOW + " Are you sure you want to delete the region '" + regionName + "'?");
                commandSender.sendMessage(pluginPrefix + ChatColor.YELLOW + " Type '/arenaregen confirm' to proceed.");
                Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.getPendingDeletions().remove(commandSender.getName()), 1200L);

                return true;
            }

            case "confirm" -> {

                String senderName = commandSender.getName();

                if (!plugin.getPendingDeletions().containsKey(senderName)) {
                    commandSender.sendMessage(pluginPrefix + ChatColor.RED + " No pending region deletion found. Use '/arenaregen delete <name>' first.");
                    return true;
                }


                String confirmedRegion = plugin.getPendingDeletions().remove(senderName);


                if (!plugin.getRegisteredRegions().containsKey(confirmedRegion)) {
                    commandSender.sendMessage(pluginPrefix + ChatColor.RED + " The region '" + confirmedRegion + "' no longer exists.");
                    return true;
                }


                RegionData regionData = plugin.getRegisteredRegions().get(confirmedRegion);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    regionData.clearRegion(confirmedRegion);
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, plugin::saveRegionsAsync);
                    String regionDeletedMsg = regionDeleted.replace("{arena_name}", confirmedRegion);
                    commandSender.sendMessage(pluginPrefix + " " + regionDeletedMsg);
                });

                return true;
            }

            case "resize" -> {
                String showUsage = ChatColor.RED + "Usage: /arenaregen resize <arena>";

                if (!(commandSender instanceof Player player)) {
                    commandSender.sendMessage(onlyForPlayers);
                    return true;
                }

                if (!commandSender.hasPermission("arenaregen.resize")) {
                    commandSender.sendMessage(pluginPrefix + " " + noPermission);
                    return true;
                }

                if (strings.length != 2) {
                    commandSender.sendMessage(pluginPrefix + " " + showUsage);
                    return true;
                }

                String regionName = strings[1];
                if (!plugin.getRegisteredRegions().containsKey(regionName)) {
                    commandSender.sendMessage(pluginPrefix + ChatColor.RED + " No arena with this name exists.");
                    return true;
                }

                Vector[] selection = getSelection(player);
                if (selection == null || selection[0] == null || selection[1] == null) {
                    commandSender.sendMessage(pluginPrefix + ChatColor.RED + " You must select both corners using the selection tool first!");
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

                File datcFile = new File(new File(plugin.getDataFolder(), "arenas"), regionName + ".datc");
                regionData.setDatcFile(datcFile);

                int arenaCreationTimeout = plugin.arenaCreationTimeout;
                java.util.concurrent.atomic.AtomicBoolean resizeCompleted = new java.util.concurrent.atomic.AtomicBoolean(false);
                java.util.concurrent.atomic.AtomicBoolean timeoutOccurred = new java.util.concurrent.atomic.AtomicBoolean(false);

                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!resizeCompleted.get()) {
                        timeoutOccurred.set(true);
                        commandSender.sendMessage(pluginPrefix + ChatColor.RED + " Arena resize timed out after " + arenaCreationTimeout + " seconds. Try a smaller region or increase the timeout in config.yml.");
                    }
                }, arenaCreationTimeout * 20L);

                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    Bukkit.getScheduler().runTask(plugin, () -> commandSender.sendMessage(pluginPrefix + ChatColor.YELLOW + " Resizing region '" + regionName + "', please wait..."));
                    regionData.clearRegion(regionName);
                    long volume = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
                    final long startTime = System.currentTimeMillis();
                    final long[] lastProgressTime = {startTime};
                    final int progressInterval = plugin.getConfig().getInt("general.large-region-settings.progress-update-interval-seconds", 5) * 1000;

                    boolean useStreaming = RegionData.forceStreaming || volume > 1000000;
                    if (useStreaming) {
                        regionData.startStreamingDatc(datcFile)
                            .exceptionally(e -> {
                                Bukkit.getScheduler().runTask(plugin, () -> commandSender.sendMessage(pluginPrefix + ChatColor.RED + " Failed to start streaming region file: " + e.getMessage()));
                                return null;
                            });
                        if (timeoutOccurred.get()) return;

                        int chunkMinX = minX >> 4;
                        int chunkMinZ = minZ >> 4;
                        int chunkMaxX = maxX >> 4;
                        int chunkMaxZ = maxZ >> 4;
                        int sectionId = 0;
                        final int[] processedChunks = {0};
                        int totalChunks = (chunkMaxX - chunkMinX + 1) * (chunkMaxZ - chunkMinZ + 1);
                        final long[] lastChunkProgressTime = {System.currentTimeMillis()};

                        for (int chunkX = chunkMinX; chunkX <= chunkMaxX; chunkX++) {
                            if (timeoutOccurred.get()) {
                                return;
                            }
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
                                            BlockData blockData = world.getBlockAt(x, y, z).getBlockData();
                                            sectionBlocks.put(loc, blockData);
                                            processedChunks[0]++;
                                        }
                                    }
                                }
                                if (!sectionBlocks.isEmpty()) {
                                    regionData.writeChunkToDatc("chunk_" + sectionId++, sectionBlocks, datcFile, true)
                                        .exceptionally(e -> {
                                            Bukkit.getScheduler().runTask(plugin, () -> commandSender.sendMessage(pluginPrefix + ChatColor.RED + " Failed to write chunk: " + e.getMessage()));
                                            return null;
                                        });
                                    if (timeoutOccurred.get()) return;
                                }
                                processedChunks[0]++;

                                long currentTime = System.currentTimeMillis();
                                if ((currentTime - lastChunkProgressTime[0]) >= progressInterval) {
                                    int progress = (processedChunks[0] * 100) / totalChunks;
                                    Bukkit.getScheduler().runTask(plugin, () -> commandSender.sendMessage(pluginPrefix + ChatColor.GRAY + " Chunk Processing: " + progress + "% (" + processedChunks[0] + "/" + totalChunks + " chunks)"));
                                    lastChunkProgressTime[0] = currentTime;
                                }
                                if (processedChunks[0] % 10 == 0) {
                                    try { Thread.sleep(1); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
                                }
                                if (processedChunks[0] % 50 == 0) {
                                    System.gc();
                                }
                            }
                        }
                        if (timeoutOccurred.get()) {
                            return;
                        }

                        regionData.finalizeStreamingDatc(datcFile, regionData.getEntityDataMap(), regionData.getBannerStates(), regionData.getSignStates(), regionData.getModifiedBlocks())
                            .thenRun(() -> {
                                resizeCompleted.set(true);
                                Bukkit.getScheduler().runTask(plugin, () -> {
                                    plugin.getRegisteredRegions().put(regionName, regionData);
                                    plugin.markRegionDirty(regionName);
                                    Bukkit.getScheduler().runTaskAsynchronously(plugin, plugin::saveRegionsAsync);
                                    clearSelection(player);
                                    commandSender.sendMessage(pluginPrefix + " " + regionResized.replace("{arena_name}", regionName));
                                });
                            })
                            .exceptionally(e -> {
                                resizeCompleted.set(true);
                                Bukkit.getScheduler().runTask(plugin, () -> {
                                    commandSender.sendMessage(pluginPrefix + ChatColor.RED + " Failed to finalize region file: " + e.getMessage());
                                });
                                return null;
                            });
                        return;
                    }


                    String sectionName = "default";
                    final int[] processedBlocks = {0};
                    for (int x = minX; x <= maxX; x++) {
                        if (timeoutOccurred.get()) {
                            return;
                        }
                        for (int y = minY; y <= maxY; y++) {
                            for (int z = minZ; z <= maxZ; z++) {
                                Location loc = new Location(world, x, y, z);
                                BlockData blockData = world.getBlockAt(loc).getBlockData();
                                regionData.addBlockToSection(sectionName, loc, blockData);
                                processedBlocks[0]++;
                                long currentTime = System.currentTimeMillis();
                                if (volume > 1000000 && (currentTime - lastProgressTime[0]) >= progressInterval) {
                                    int progress = (int) ((processedBlocks[0] * 100) / volume);
                                    Bukkit.getScheduler().runTask(plugin, () -> commandSender.sendMessage(pluginPrefix + ChatColor.GRAY + " Resize Progress: " + progress + "% (" + processedBlocks[0] + "/" + volume + " blocks)"));
                                    lastProgressTime[0] = currentTime;
                                }
                                int gcFreq = plugin.getConfig().getInt("general.large-region-settings.gc-frequency", 100000);
                                if (volume > 1000000 && processedBlocks[0] % gcFreq == 0) {
                                    System.gc();
                                }
                            }
                        }
                    }

                    if (timeoutOccurred.get()) {
                        return;
                    }
                    resizeCompleted.set(true);
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        plugin.getRegisteredRegions().put(regionName, regionData);
                        plugin.markRegionDirty(regionName);
                        Bukkit.getScheduler().runTaskAsynchronously(plugin, plugin::saveRegionsAsync);
                        clearSelection(player);
                        commandSender.sendMessage(pluginPrefix + " " + regionResized.replace("{arena_name}", regionName));
                    });
                });
                return true;
            }

            case "regenerate", "regen" -> {
                String showUsage = ChatColor.translateAlternateColorCodes('&', "&cUsage: /arenaregen regenerate <arena> [cancel]");
                String confirmPrompt = ChatColor.YELLOW + "Are you sure you want to regenerate the region '{arena_name}'? Type '/arenaregen regen confirm' to proceed.";
                String noPending = ChatColor.RED + "No pending region regeneration found. Use '/arenaregen regen <name>' first.";
                String scheduledWarning = ChatColor.YELLOW + "Note: Arena '{arena_name}' is scheduled to regenerate every {interval}. Manual regeneration will not affect the schedule.";
                String scheduleCanceled = ChatColor.GREEN + "Canceled scheduled regeneration for '{arena_name}'.";

                if (!commandSender.hasPermission("arenaregen.regenerate")) {
                    commandSender.sendMessage(pluginPrefix + " " + noPermission);
                    return true;
                }

                if (strings.length < 2 || strings.length > 3) {
                    commandSender.sendMessage(pluginPrefix + " " + showUsage);
                    return true;
                }

                String input = strings[1];

                if (strings.length == 3 && strings[2].equalsIgnoreCase("cancel")) {
                    if (!plugin.getRegisteredRegions().containsKey(input)) {
                        commandSender.sendMessage(pluginPrefix + " " + arenaNotFound.replace("{arena_name}", input));
                        return true;
                    }
                    if (!plugin.isScheduled(input)) {
                        commandSender.sendMessage(pluginPrefix + ChatColor.YELLOW + " No scheduled regeneration found for '" + input + "'.");
                        return true;
                    }
                    plugin.cancelScheduledRegeneration(input);
                    commandSender.sendMessage(pluginPrefix + " " + scheduleCanceled.replace("{arena_name}", input));
                    return true;
                }

                if (plugin.isScheduled(input)) {
                    long intervalTicks = plugin.getScheduledInterval(input);
                    String intervalString = formatTicksToTime(intervalTicks);
                    commandSender.sendMessage(scheduledWarning
                            .replace("{arena_name}", input)
                            .replace("{interval}", intervalString));
                }

                Runnable regenerateArena = () -> plugin.regenerateArena(input, commandSender);

                if (plugin.confirmationPrompt) {
                    if (input.equalsIgnoreCase("confirm")) {
                        String senderName = commandSender.getName();
                        String targetArenaName = plugin.getPendingRegenerations().remove(senderName);
                        if (targetArenaName == null) {
                            commandSender.sendMessage(noPending);
                            return true;
                        }

                        plugin.regenerateArena(targetArenaName, commandSender);

                        return true;
                    } else {
                        if (!plugin.getRegisteredRegions().containsKey(input)) {
                            commandSender.sendMessage(arenaNotFound.replace("{arena_name}", input));
                            return true;
                        }
                        plugin.getPendingRegenerations().put(commandSender.getName(), input);
                        commandSender.sendMessage(confirmPrompt.replace("{arena_name}", input));
                        Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.getPendingRegenerations().remove(commandSender.getName()), 1200L);
                        return true;
                    }
                } else {
                    regenerateArena.run();
                    return true;
                }
            }

            case "schedule" -> {
                String showUsage = ChatColor.translateAlternateColorCodes('&', "&cUsage: /arenaregen schedule <arena> [time (e.g., 50m, 1d, 1w)]");
                String invalidTime = pluginPrefix + ChatColor.RED + " Invalid time format! Use a number followed by s (seconds), m (minutes), d (days), or w (weeks). Example: 50m";
                String timeTooShort = pluginPrefix + ChatColor.RED + " The interval must be at least 10 seconds!";
                String scheduledMessage = pluginPrefix + ChatColor.GREEN + " Scheduled regeneration for '{arena_name}' every {interval}.";
                String canceledMessage = pluginPrefix + ChatColor.YELLOW + " Canceled scheduled regeneration for '{arena_name}'.";
                String alreadyScheduled = pluginPrefix + ChatColor.YELLOW + " Arena '{arena_name}' is already scheduled to regenerate every {interval}. Use '/ar schedule {arena_name}' to cancel.";

                if (!commandSender.hasPermission("arenaregen.regenerate")) {
                    commandSender.sendMessage(noPermission);
                    return true;
                }

                if (strings.length < 2 || strings.length > 3) {
                    commandSender.sendMessage(pluginPrefix + " " + showUsage);
                    return true;
                }

                String arenaName = strings[1];
                if (!plugin.getRegisteredRegions().containsKey(arenaName)) {
                    commandSender.sendMessage(pluginPrefix + " " +arenaNotFound.replace("{arena_name}", arenaName));
                    return true;
                }

                if (strings.length == 2) {
                    if (plugin.isScheduled(arenaName)) {
                        plugin.cancelScheduledRegeneration(arenaName);
                        commandSender.sendMessage(canceledMessage.replace("{arena_name}", arenaName));
                    } else {
                        commandSender.sendMessage(pluginPrefix + ChatColor.YELLOW + " No scheduled regeneration found for '" + arenaName + "'.");
                    }
                    return true;
                }

                String timeInput = strings[2];
                long intervalTicks;
                try {
                    intervalTicks = parseTimeToTicks(timeInput);
                } catch (IllegalArgumentException e) {
                    commandSender.sendMessage(invalidTime);
                    return true;
                }

                if (intervalTicks < 200) {
                    commandSender.sendMessage(timeTooShort);
                    return true;
                }

                if (plugin.isScheduled(arenaName)) {
                    long existingInterval = plugin.getScheduledInterval(arenaName);
                    String intervalString = formatTicksToTime(existingInterval);
                    commandSender.sendMessage(alreadyScheduled
                            .replace("{arena_name}", arenaName)
                            .replace("{interval}", intervalString));
                    return true;
                }

                plugin.scheduleRegeneration(arenaName, intervalTicks, commandSender);
                String intervalString = formatTicksToTime(intervalTicks);
                commandSender.sendMessage(scheduledMessage
                        .replace("{arena_name}", arenaName)
                        .replace("{interval}", intervalString));
                return true;
            }

            case "setspawn" -> {
                String showUsage = ChatColor.translateAlternateColorCodes('&', "&cUsage: /arenaregen setspawn <arena>");

                if (!(commandSender instanceof Player player)) {
                    commandSender.sendMessage(onlyForPlayers);
                    return true;
                }

                if (!commandSender.hasPermission("arenaregen.setspawn")) {
                    commandSender.sendMessage(pluginPrefix + " " + noPermission);
                    return true;
                }

                if (strings.length != 2) {
                    commandSender.sendMessage(pluginPrefix + " " + showUsage);
                    return true;
                }
                String targetArenaName = strings[1];
                spawnSet = spawnSet.replace("{arena_name}", targetArenaName);

                Location location = player.getLocation();

                RegionData regionData = plugin.getRegisteredRegions().get(targetArenaName);
                if (regionData == null) {
                    commandSender.sendMessage(pluginPrefix + ChatColor.RED + " Region '" + targetArenaName + "' does not exist.");
                    return true;
                }

                Bukkit.getScheduler().runTask(plugin, () -> regionData.setSpawnLocation(location));
                plugin.markRegionDirty(targetArenaName);
                Bukkit.getScheduler().runTaskAsynchronously(plugin, plugin::saveRegionsAsync);
                commandSender.sendMessage(spawnSet);
                return true;
            }

            case "delspawn" -> {
                String showUsage = ChatColor.translateAlternateColorCodes('&', "&cUsage: /arenaregen delspawn <arena>");

                if (!commandSender.hasPermission("arenaregen.delspawn")) {
                    commandSender.sendMessage(noPermission);
                    return true;
                }

                if (strings.length != 2) {
                    commandSender.sendMessage(pluginPrefix + " " + showUsage);
                    return true;
                }

                String targetArenaName = strings[1];
                spawnDeleted = spawnDeleted.replace("{arena_name}", targetArenaName);

                RegionData regionData = plugin.getRegisteredRegions().get(targetArenaName);
                if (regionData == null) {
                    commandSender.sendMessage(pluginPrefix + ChatColor.RED + " Region '" + targetArenaName + "' does not exist.");
                    return true;
                }

                Bukkit.getScheduler().runTask(plugin, () -> regionData.setSpawnLocation(null));
                plugin.markRegionDirty(targetArenaName);
                Bukkit.getScheduler().runTaskAsynchronously(plugin, plugin::saveRegionsAsync);
                commandSender.sendMessage(spawnDeleted);
                return true;
            }

            case "teleport", "tp" -> {
                String showUsage = ChatColor.translateAlternateColorCodes('&', "&cUsage: /arenaregen teleport <arena>");

                if (!(commandSender instanceof Player player)) {
                    commandSender.sendMessage(onlyForPlayers);
                    return true;
                }

                if (!commandSender.hasPermission("arenaregen.teleport")) {
                    commandSender.sendMessage(pluginPrefix + " " + noPermission);
                    return true;
                }

                if (strings.length != 2) {
                    commandSender.sendMessage(pluginPrefix + " " + showUsage);
                    return true;
                }

                String targetArenaName = strings[1];
                teleportSuccess = teleportSuccess.replace("{arena_name}", targetArenaName);

                RegionData regionData = plugin.getRegisteredRegions().get(targetArenaName);
                if (regionData == null) {
                    commandSender.sendMessage(pluginPrefix + ChatColor.RED + " Region '" + targetArenaName + "' does not exist.");
                    return true;
                }

                Location spawnLocation = regionData.getSpawnLocation();
                if (spawnLocation == null) {
                    commandSender.sendMessage(pluginPrefix + ChatColor.RED + " Please set a spawn point for region '" + targetArenaName + "' first.");
                    return true;
                }

                player.teleport(spawnLocation);
                commandSender.sendMessage(pluginPrefix + " " + teleportSuccess);
                return true;
            }

            case "list" -> {
                if (!commandSender.hasPermission("arenaregen.list")) {
                    commandSender.sendMessage(pluginPrefix + " " + noPermission);
                    return true;
                }
                listRegions(commandSender);
                return true;
            }

            case "info", "information" -> {
                String showUsage = ChatColor.translateAlternateColorCodes('&', "&cUsage: /arenaregen info <arena>");

                if (!commandSender.hasPermission("arenaregen.info")) {
                    commandSender.sendMessage(pluginPrefix + " " + noPermission);
                    return true;
                }

                if (strings.length != 2) {
                    commandSender.sendMessage(pluginPrefix + " " + showUsage);
                    return true;
                }

                String targetArenaName = strings[1];
                showRegionDetails(commandSender, targetArenaName);
                return true;
            }

            case "reload" -> {
                if (!commandSender.hasPermission("arenaregen.reload")) {
                    commandSender.sendMessage(pluginPrefix + " " + noPermission);
                    return true;
                }

                plugin.reloadPluginConfig();
                plugin.loadMessagesFile();
                commandSender.sendMessage(pluginPrefix + " " + reloadSuccess);
                return true;
            }

            case "preview" -> {
                if (!commandSender.hasPermission("arenaregen.preview")) {
                    commandSender.sendMessage(pluginPrefix + " " + noPermission);
                    return true;
                }

                String targetArenaName = strings[1];

                plugin.previewArena(targetArenaName, commandSender);
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
                commandSender.sendMessage(ChatColor.GREEN + "/arenaregen help - Show this command.");
                commandSender.sendMessage(ChatColor.GREEN + "/arenaregen wand - Get the selection tool");
                commandSender.sendMessage(ChatColor.GREEN + "/arenaregen schedule - Schedule a regeneration timer");
                commandSender.sendMessage(ChatColor.GREEN + "/arenaregen preview - Preview the borders of an arena using particles");
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

        for (Map.Entry<String, RegionData> entry : regions.entrySet()) {
            String name = entry.getKey();
            RegionData regionData = entry.getValue();

            World world = Bukkit.getWorld(regionData.getWorldName());

            if (world == null) {
                sender.sendMessage(ChatColor.YELLOW + "- " + name + ": " + ChatColor.RED + "World " + regionData.getWorldName() + " not found.");
                continue;
            }

            regionData.getSectionedBlockData().thenAccept(sectionedData -> {
                Location min = null;
                Location max = null;

                for (Map<Location, BlockData> section : sectionedData.values()) {
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

                if (min != null) {
                    message.append(String.format(": (%d, %d, %d) to (%d, %d, %d)",
                            (int) min.getX(), (int) min.getY(), (int) min.getZ(),
                            (int) max.getX(), (int) max.getY(), (int) max.getZ()));
                } else {
                    message.append(": No block data");
                }

                sender.sendMessage(message.toString());
            });
        }
    }

    private void showRegionDetails(CommandSender sender, String regionName) {
        Map<String, RegionData> regions = plugin.getRegisteredRegions();
        RegionData regionData = regions.get(regionName);

        if (regionData == null) {
            sender.sendMessage(ChatColor.RED + "Arena '" + regionName + "' not found.");
            return;
        }

        World world = Bukkit.getWorld(regionData.getWorldName());

        if (world == null) {
            sender.sendMessage(ChatColor.RED + "World '" + regionData.getWorldName() + "' not found");
            return;
        }

        regionData.getSectionedBlockData().thenAccept(sectionedBlockData -> {
            Location min = null;
            Location max = null;
            for (Map<Location, BlockData> section : sectionedBlockData.values()) {
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
            long size = (regionData.getArea());
            StringBuilder message = new StringBuilder();
            message.append(ChatColor.GREEN).append("Arena Details for ").append(regionName).append(":");

            if (min != null) {
                message.append(String.format("\n" + ChatColor.GREEN + "  Coordinates: (%d, %d, %d) to (%d, %d, %d)",
                        (int) min.getX(), (int) min.getY(), (int) min.getZ(),
                        (int) max.getX(), (int) max.getY(), (int) max.getZ()));
            } else {
                message.append("\n" + ChatColor.GREEN + "  Coordinates: No block data");
            }

            message.append(ChatColor.GRAY)
                    .append("\n  Volume: ").append(ChatColor.WHITE).append(size).append(" blocks");
            message.append(ChatColor.GRAY)
                    .append("\n  Creator: ").append(ChatColor.WHITE).append(regionData.getCreator())
                    .append(ChatColor.GRAY)
                    .append("\n  Created: ").append(ChatColor.WHITE)
                    .append(new Date(regionData.getCreationDate()))
                    .append(ChatColor.GRAY)
                    .append("\n  World: ").append(ChatColor.WHITE).append(regionData.getWorldName())
                    .append(ChatColor.GRAY)
                    .append("\n  Minecraft Version: ").append(ChatColor.WHITE).append(regionData.getMinecraftVersion())
                    .append(ChatColor.GRAY)
                    .append("\n  File Format Version: ").append(ChatColor.WHITE).append(regionData.getFileFormatVersion())
                    .append(ChatColor.GRAY)
                    .append("\n  Dimensions: ").append(ChatColor.WHITE)
                    .append(regionData.getWidth()).append("x")
                    .append(regionData.getHeight()).append("x")
                    .append(regionData.getDepth())
                    .append(ChatColor.GRAY)
                    .append("\n  Sections: ").append(ChatColor.WHITE)
                    .append(size);

            if (plugin.isScheduled(regionName)) {
                long intervalTicks = plugin.getScheduledInterval(regionName);
                String intervalString = formatTicksToTime(intervalTicks);
                message.append(ChatColor.GRAY)
                        .append("\n  Regeneration Schedule: ").append(ChatColor.WHITE)
                        .append("Every ").append(intervalString);
            }

            sender.sendMessage(message.toString());
        });
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String @NotNull [] strings) {
        return handleTabComplete(strings);
    }

    private List<String> handleTabComplete(String @NotNull [] args) {
        return switch (args.length) {
            case 1 -> filterSuggestions(List.of("create", "regenerate", "regen", "setspawn", "delspawn", "teleport", "tp",
                    "list", "delete", "resize", "reload", "help", "wand", "selection", "schedule", "preview", "info"), args[0]);

            case 2 -> {
                if (args[0].equalsIgnoreCase("regenerate") || args[0].equalsIgnoreCase("regen")
                        || args[0].equalsIgnoreCase("delete") || args[0].equalsIgnoreCase("resize")
                        || args[0].equalsIgnoreCase("setspawn") || args[0].equalsIgnoreCase("delspawn")
                        || args[0].equalsIgnoreCase("teleport") || args[0].equalsIgnoreCase("tp")
                        || args[0].equalsIgnoreCase("preview")
                        || args[0].equalsIgnoreCase("schedule")
                        || args[0].equalsIgnoreCase("info") || args[0].equalsIgnoreCase("information")){
                    yield listRegionsForTabComplete(args[1]);
                } else if (args[0].equalsIgnoreCase("create")) {
                    yield filterSuggestions(List.of("ArenaName"), args[1]);
                } else if (args[0].equalsIgnoreCase("wand") || args[0].equalsIgnoreCase("selection")) {
                    yield getOnlinePlayers(args[1]);
                }
                yield List.of();
            }

            case 3 -> {
                if (args[0].equalsIgnoreCase("schedule")) {
                    yield filterSuggestions(List.of("10s", "1m", "1d", "1w"), args[2]);
                }
                yield List.of();
            }

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
    
    private String formatTicksToTime(long ticks) {
        if (ticks < 20 * 60) {
            return (ticks / 20) + " seconds";
        } else if (ticks < 20 * 60 * 60) {
            return (ticks / (20 * 60)) + " minutes";
        } else if (ticks < 20 * 60 * 60 * 24) {
            return (ticks / (20 * 60 * 60)) + " hours";
        } else if (ticks < 20 * 60 * 60 * 24 * 7) {
            return (ticks / (20 * 60 * 60 * 24)) + " days";
        } else {
            return (ticks / (20 * 60 * 60 * 24 * 7)) + " weeks";
        }
    }
    
    private long parseTimeToTicks(String timeInput) {
        if (timeInput == null || timeInput.isEmpty()) {
            throw new IllegalArgumentException("Time input cannot be empty");
        }
        String numberPart = timeInput.replaceAll("[^0-9]", "");
        String unitPart = timeInput.replaceAll("[0-9]", "").toLowerCase();
        if (numberPart.isEmpty() || unitPart.isEmpty()) {
            throw new IllegalArgumentException("Invalid time format");
        }
        long number;
        try {
            number = Long.parseLong(numberPart);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid number in time format");
        }
        if (number <= 0) {
            throw new IllegalArgumentException("Time must be a positive number");
        }
        return switch (unitPart) {
            case "s" -> number * 20;
            case "m" -> number * 20 * 60;
            case "d" -> number * 20 * 60 * 60 * 24;
            case "w" -> number * 20 * 60 * 60 * 24 * 7;
            default -> throw new IllegalArgumentException("Invalid time unit");
        };
    }
}
