package com.zitemaker;

import com.zitemaker.commands.ArenaRegenCommand;
import com.zitemaker.helpers.EntitySerializer;
import com.zitemaker.helpers.RegionData;
import com.zitemaker.nms.BlockUpdate;
import com.zitemaker.nms.NMSHandlerFactoryProvider;
import com.zitemaker.utils.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

public class ArenaRegen extends JavaPlugin {

    private File messagesFile;
    private FileConfiguration messagesConfig;

    private final Map<String, RegionData> registeredRegions = new ConcurrentHashMap<>();
    private final Map<String, String> pendingDeletions = new ConcurrentHashMap<>();
    private final Map<String, String> pendingRegenerations = new ConcurrentHashMap<>();
    public final Console console = new SpigotConsole();
    private final Logger logger = new Logger(new JavaPlatformLogger(console, getLogger()), true);
    public final Set<String> dirtyRegions = new HashSet<>();
    private final Set<String> regeneratingArenas = new HashSet<>();

    // config stuff
    public String prefix;
    public String regenType;
    public String regenSpeed;
    public int customRegenSpeed;
    public int analyzeSpeed;
    public int arenaSize;
    public int maxArenas;
    public boolean confirmationPrompt;
    public boolean trackEntities;
    public boolean regenOnlyModified;
    public boolean cancelRegen;
    public boolean killPlayers;
    public boolean executeCommands;
    public List<String> commands;
    public boolean teleportToSpawn;
    public String selectionTool;
    public String previewParticleString;
    public Particle previewParticle;

    private int saveTaskId = -1;
    private final Map<String, Integer> scheduledTasks = new ConcurrentHashMap<>();
    private final Map<String, Long> scheduledIntervals = new ConcurrentHashMap<>();
    private File schedulesFile;
    private FileConfiguration schedulesConfig;

    @Override
    public void onLoad() {
        logger.info(ARChatColor.GREEN + "ArenaRegen.jar v" + getDescription().getVersion() + " has been loaded successfully");
    }

    @Override
    public void onEnable() {
        logger.info("");
        logger.info(ARChatColor.GOLD + "    +===============+");
        logger.info(ARChatColor.GOLD + "    |   ArenaRegen  |");
        logger.info(ARChatColor.GOLD + "    |---------------|");
        logger.info(ARChatColor.GOLD + "    |  Free Version |");
        logger.info(ARChatColor.GOLD + "    +===============+");
        logger.info("");
        logger.info(ARChatColor.GREEN + "    ArenaRegen v" + getDescription().getVersion() + " has been enabled.");
        logger.info("");
        logger.info(ARChatColor.AQUA + "    Purchase ArenaRegen+ for more features!");
        logger.info(ARChatColor.GREEN + "    " + getPurchaseLink());
        logger.info("");

        if (!Bukkit.getServer().getName().equalsIgnoreCase("Paper")) {
            logger.info(ARChatColor.YELLOW + "    [Warning] ArenaRegen detected that this server is not running Paper.");
            logger.info(ARChatColor.YELLOW + "    Paper is recommended for better performance and compatibility.");
            logger.info(ARChatColor.YELLOW + "    Download Paper at https://papermc.io/downloads");
        }

        String serverVersion = Bukkit.getBukkitVersion().split("-")[0];
        boolean isModernServer = serverVersion.startsWith("1.20.5") || serverVersion.startsWith("1.20.6") || serverVersion.startsWith("1.21");
        boolean hasNMSHandler = false;

        try {
            Class.forName("com.zitemaker.nms.NMSHandler_1_21");
            hasNMSHandler = true;
        } catch (ClassNotFoundException e) {
            hasNMSHandler = false;
        }

        if (isModernServer && !hasNMSHandler) {
            logger.info(ARChatColor.RED + "    [Warning] This server (" + serverVersion + ") supports optimized NMS block updates.");
            logger.info(ARChatColor.RED + "    However, this JAR does not include NMS support (likely the legacy build).");
            logger.info(ARChatColor.YELLOW + "    For better performance, please use the modern JAR (built for 1.20.5–1.21.5).");
        } else if (!isModernServer && hasNMSHandler) {
            logger.info(ARChatColor.RED + "    [Warning] This server (" + serverVersion + ") is better suited for the legacy JAR.");
            logger.info(ARChatColor.RED + "    This JAR includes NMS support (likely the modern build), which may cause compatibility issues.");
            logger.info(ARChatColor.YELLOW + "    For better compatibility, please use the legacy JAR (built for 1.18–1.20.4).");
        }

        reloadPluginConfig();
        loadMessagesFile();
        saveMessagesFile();
        loadRegions();
        loadSchedules();

        ArenaRegenCommand commandExecutor = new ArenaRegenCommand(this);
        Objects.requireNonNull(getCommand("arenaregen")).setExecutor(commandExecutor);
        Objects.requireNonNull(getCommand("arenaregen")).setTabCompleter(commandExecutor);
        Bukkit.getPluginManager().registerEvents(commandExecutor, this);

        File arenasDir = new File(getDataFolder(), "arenas");
        if (!arenasDir.exists()) {
            arenasDir.mkdirs();
        }
        if (!arenasDir.canRead() || !arenasDir.canWrite()) {
            logger.info(ARChatColor.RED + "ERROR: The arenas directory (" + arenasDir.getPath() + ") is not readable or writable!");
            logger.info(ARChatColor.RED + "Please check file permissions to ensure the server process has read/write access.");
        }

        saveTaskId = Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            if (!dirtyRegions.isEmpty()) {
                saveRegions();
            }
        }, 0L, 6000L).getTaskId();
        rescheduleTasks();
    }

    @Override
    public void onDisable() {
        if (saveTaskId != -1) {
            Bukkit.getScheduler().cancelTask(saveTaskId);
            saveTaskId = -1;
        }

        for (Integer taskId : scheduledTasks.values()) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
        scheduledTasks.clear();
        saveSchedules();

        logger.info(ARChatColor.GOLD + "Saving all arenas before shutdown...");
        saveRegionsSynchronously();
        logger.info(ARChatColor.RED + "ArenaRegen v" + getDescription().getVersion() + " has been disabled.");
    }

    private void saveRegionsSynchronously() {
        File arenasDir = new File(getDataFolder(), "arenas");
        if (!arenasDir.exists()) {
            arenasDir.mkdirs();
            console.sendMessage(" Created arenas directory: " + arenasDir.getPath());
        }

        if (!arenasDir.canWrite()) {
            logger.info(ARChatColor.RED + "ERROR: Cannot write to arenas directory (" + arenasDir.getPath() + ")!");
            logger.info(ARChatColor.RED + "Please check file permissions to ensure the server process has write access.");
            return;
        }

        Map<String, RegionData> regionsToSave;
        synchronized (registeredRegions) {
            regionsToSave = new HashMap<>(registeredRegions);
        }

        if (regionsToSave.isEmpty()) {
            console.sendMessage("No regions to save.");
            dirtyRegions.clear();
            return;
        }

        console.sendMessage("Saving " + regionsToSave.size() + " regions synchronously...");

        int savedRegions = 0;
        StringBuilder errorSummary = new StringBuilder();

        for (String regionName : regionsToSave.keySet()) {
            File datcFile = new File(arenasDir, regionName + ".datc");
            try {
                RegionData regionData = regionsToSave.get(regionName);
                regionData.saveToDatc(datcFile);
                savedRegions++;
                getLogger().info("Saved region '" + regionName + "' to " + datcFile.getPath());
            } catch (IOException e) {
                getLogger().severe("Failed to save region '" + regionName + "' to " + datcFile.getPath() + ": " + e.getMessage());
                errorSummary.append(ARChatColor.RED)
                        .append(" - Region '").append(regionName).append("': ").append(e.getMessage()).append("\n");
            }
        }

        console.sendMessage("Successfully saved " + savedRegions + " out of " + regionsToSave.size() + " regions.");

        if (savedRegions < regionsToSave.size()) {
            console.sendMessage(ARChatColor.RED + "Errors occurred while saving the following regions:");
            console.sendMessage(errorSummary.toString());
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.isOp()) {
                    player.sendMessage(prefix + " " + ARChatColor.RED + "Failed to save some arenas during shutdown! Check the server logs for details.");
                }
            }
        }

        if (savedRegions == regionsToSave.size()) {
            dirtyRegions.clear();
            console.sendMessage("Cleared dirty regions list.");
        } else {
            console.sendMessage("Preserving dirty regions list due to save errors.");
        }
    }

    public void saveRegions() {
        File arenasDir = new File(getDataFolder(), "arenas");
        arenasDir.mkdirs();

        if (!arenasDir.canWrite()) {
            logger.info(ARChatColor.RED + "ERROR: Cannot write to arenas directory (" + arenasDir.getPath() + ")!");
            logger.info(ARChatColor.RED + "Please check file permissions to ensure the server process has write access.");
            return;
        }

        Map<String, RegionData> regionsToSave;
        Set<String> regionsToProcess;
        synchronized (registeredRegions) {
            regionsToSave = new HashMap<>(registeredRegions);
            synchronized (dirtyRegions) {
                regionsToProcess = new HashSet<>(dirtyRegions);
            }
        }

        if (regionsToProcess.isEmpty()) {
            return;
        }

        console.sendMessage("Saving " + regionsToProcess.size() + " dirty regions asynchronously...");

        int savedRegions = 0;
        StringBuilder errorSummary = new StringBuilder();

        for (String regionName : regionsToProcess) {
            if (!regionsToSave.containsKey(regionName)) {
                continue;
            }
            File datcFile = new File(arenasDir, regionName + ".datc");
            try {
                regionsToSave.get(regionName).saveToDatc(datcFile);
                savedRegions++;
                getLogger().info("Saved region '" + regionName + "' to " + datcFile.getPath());
            } catch (IOException e) {
                getLogger().severe("Failed to save region '" + regionName + "' to " + datcFile.getPath() + ": " + e.getMessage());
                errorSummary.append(ARChatColor.RED)
                        .append(" - Region '").append(regionName).append("': ").append(e.getMessage()).append("\n");
            }
        }

        console.sendMessage("Successfully saved " + savedRegions + " out of " + regionsToProcess.size() + " dirty regions.");

        if (savedRegions < regionsToProcess.size()) {
            console.sendMessage(ARChatColor.RED + "Errors occurred while saving the following regions:");
            console.sendMessage(errorSummary.toString());
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.isOp()) {
                    player.sendMessage(prefix + " " + ARChatColor.RED + "Failed to save some arenas! Check the server logs for details.");
                }
            }
        } else {
            synchronized (dirtyRegions) {
                dirtyRegions.removeAll(regionsToProcess);
            }
            console.sendMessage("Cleared saved regions from dirty regions list.");
        }
    }

    public void loadConfigValues() {
        this.prefix = ChatColor.translateAlternateColorCodes('&', getConfig().getString("prefix", "&e[&2ArenaRegen&e]"));
        this.regenType = getConfig().getString("regen.regen-speed-type", "PRESET").toUpperCase();
        this.regenSpeed = getConfig().getString("regen.regen-speed", "FAST").toUpperCase();
        this.customRegenSpeed = getConfig().getInt("regen.custom-regen-speed", 10000);
        this.analyzeSpeed = getConfig().getInt("general.analyze-speed", 40000);
        this.arenaSize = getConfig().getInt("general.arena-size-limit", 40000);
        this.maxArenas = getConfig().getInt("general.max-arenas", 100);
        this.confirmationPrompt = getConfig().getBoolean("regen.confirmation-prompt", false);
        this.trackEntities = getConfig().getBoolean("regen.track-entities", true);
        this.regenOnlyModified = getConfig().getBoolean("regen.regen-only-modified", false);
        this.cancelRegen = getConfig().getBoolean("regen.players-inside-arena.cancel-regen", false);
        this.killPlayers = getConfig().getBoolean("regen.players-inside-arena.kill", false);
        this.executeCommands = getConfig().getBoolean("regen.players-inside-arena.execute-commands", true);
        this.commands = getConfig().getStringList("regen.players-inside-arena.commands");
        this.teleportToSpawn = getConfig().getBoolean("regen.players-inside-arena.teleport-to-spawn", true);
        this.selectionTool = getConfig().getString("general.selection-tool", "GOLDEN_HOE").toUpperCase();
        this.previewParticleString = getConfig().getString("general.preview-particle", "FLAME").toUpperCase();
    }

    public void reloadPluginConfig() {
        saveDefaultConfig();
        reloadConfig();
        loadConfigValues();
    }

    public Map<String, RegionData> getRegisteredRegions() {
        return registeredRegions;
    }

    public Map<String, String> getPendingDeletions() {
        return pendingDeletions;
    }

    public Map<String, String> getPendingRegenerations() {
        return pendingRegenerations;
    }

    public Set<String> getDirtyRegions() {
        return dirtyRegions;
    }

    private static final List<String> ARENAREGEN_PERMISSIONS = List.of(
            "arenaregen.create",
            "arenaregen.delete",
            "arenaregen.resize",
            "arenaregen.list",
            "arenaregen.setspawn",
            "arenaregen.delspawn",
            "arenaregen.teleport",
            "arenaregen.reload",
            "arenaregen.help",
            "arenaregen.select"
    );

    public static boolean hasAnyPermissions(Player player) {
        for (String permission : ARENAREGEN_PERMISSIONS) {
            if (player.hasPermission(permission)) {
                return true;
            }
        }
        return false;
    }

    public String getPurchaseLink() {
        return "https://zitemaker.tebex.io";
    }

    public void loadMessagesFile() {
        messagesFile = new File(getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            saveResource("messages.yml", false);
        }
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
    }

    public FileConfiguration getMessagesConfig() {
        return messagesConfig;
    }

    public void saveMessagesFile() {
        try {
            messagesConfig.save(messagesFile);
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Could not save messages.yml!", e);
        }
    }

    public void loadRegions() {
        File arenasDir = new File(getDataFolder(), "arenas");
        if (!arenasDir.exists()) {
            arenasDir.mkdirs();
            return;
        }

        if (!arenasDir.canRead()) {
            logger.info(ARChatColor.RED + "ERROR: Cannot read from arenas directory (" + arenasDir.getPath() + ")!");
            logger.info(ARChatColor.RED + "Please check file permissions to ensure the server process has read access.");
            return;
        }

        File[] files = arenasDir.listFiles((dir, name) -> name.endsWith(".datc"));
        if (files == null || files.length == 0) {
            logger.info(ARChatColor.YELLOW + "No arenas found in " + arenasDir.getPath() + ".");
            return;
        }

        int loadedRegions = 0;
        StringBuilder errorSummary = new StringBuilder();

        for (File file : files) {
            String regionName = file.getName().replace(".datc", "");
            RegionData regionData = new RegionData(this);
            try {
                regionData.loadFromDatc(file);
                registeredRegions.put(regionName, regionData);
                loadedRegions++;
                logger.info(ARChatColor.GREEN + "Loaded arena '" + regionName + "' successfully.");
            } catch (Exception e) {
                logger.info(ARChatColor.RED + "Failed to load arena '" + regionName + "' from " + file.getName() + ": " + e.getMessage());
                logger.info(ARChatColor.YELLOW + "Skipping '" + regionName + "'. You may need to delete or fix the file.");
                errorSummary.append(ARChatColor.RED)
                        .append(" - Arena '").append(regionName).append("': ").append(e.getMessage()).append("\n");
            }
        }

        logger.info("Successfully loaded " + loadedRegions + " out of " + files.length + " arenas.");

        if (loadedRegions < files.length) {
            logger.info(ARChatColor.RED + "Errors occurred while loading the following arenas:");
            logger.info(errorSummary.toString());
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.isOp()) {
                    player.sendMessage(prefix + " " + ARChatColor.RED + "Failed to load some arenas on startup! Check the server logs for details.");
                }
            }
        }
    }

    public void markRegionDirty(String regionName) {
        synchronized (dirtyRegions) {
            dirtyRegions.add(regionName);
        }
    }

    public void scheduleRegeneration(String arenaName, long intervalTicks, CommandSender sender) {
        cancelScheduledRegeneration(arenaName);

        Runnable regenerateTask = createRegenerateTask(arenaName);
        int taskId = Bukkit.getScheduler().runTaskTimer(this, regenerateTask, intervalTicks, intervalTicks).getTaskId();

        scheduledTasks.put(arenaName, taskId);
        scheduledIntervals.put(arenaName, intervalTicks);

        saveSchedules();
    }

    public void cancelScheduledRegeneration(String arenaName) {
        Integer taskId = scheduledTasks.remove(arenaName);
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
        scheduledIntervals.remove(arenaName);
        saveSchedules();
    }

    public boolean isScheduled(String arenaName) {
        return scheduledTasks.containsKey(arenaName);
    }

    public Long getScheduledInterval(String arenaName) {
        return scheduledIntervals.get(arenaName);
    }

    private void loadSchedules() {
        schedulesFile = new File(getDataFolder(), "schedules.yml");
        if (!schedulesFile.exists()) {
            saveResource("schedules.yml", false);
        }
        schedulesConfig = YamlConfiguration.loadConfiguration(schedulesFile);

        if (schedulesConfig.contains("schedules")) {
            for (String arenaName : schedulesConfig.getConfigurationSection("schedules").getKeys(false)) {
                long intervalTicks = schedulesConfig.getLong("schedules." + arenaName);
                if (intervalTicks >= 200) {
                    scheduledIntervals.put(arenaName, intervalTicks);
                }
            }
        }
    }

    private void saveSchedules() {
        schedulesConfig.set("schedules", null);
        for (Map.Entry<String, Long> entry : scheduledIntervals.entrySet()) {
            schedulesConfig.set("schedules." + entry.getKey(), entry.getValue());
        }
        try {
            schedulesConfig.save(schedulesFile);
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Could not save schedules.yml!", e);
        }
    }

    private void rescheduleTasks() {
        for (Map.Entry<String, Long> entry : scheduledIntervals.entrySet()) {
            String arenaName = entry.getKey();
            long intervalTicks = entry.getValue();
            if (registeredRegions.containsKey(arenaName)) {
                Runnable regenerateTask = createRegenerateTask(arenaName);
                int taskId = Bukkit.getScheduler().runTaskTimer(this, regenerateTask, intervalTicks, intervalTicks).getTaskId();
                scheduledTasks.put(arenaName, taskId);
            } else {
                scheduledIntervals.remove(arenaName);
            }
        }
        saveSchedules();
    }

    public void regenerateArenaSchedule(String arenaName) {
        synchronized (regeneratingArenas) {
            if (regeneratingArenas.contains(arenaName)) {
                return;
            }
            regeneratingArenas.add(arenaName);
        }

        RegionData regionData = registeredRegions.get(arenaName);
        if (regionData == null) {
            synchronized (regeneratingArenas) {
                regeneratingArenas.remove(arenaName);
            }
            logger.info(ChatColor.RED + " Arena '" + arenaName + "' not found.");
            return;
        }

        World world = Bukkit.getWorld(regionData.getWorldName());
        if (world == null) {
            synchronized (regeneratingArenas) {
                regeneratingArenas.remove(arenaName);
            }
            logger.info(ChatColor.RED + " World '" + regionData.getWorldName() + "' not found.");
            return;
        }

        if (regionData.getSectionedBlockData().isEmpty()) {
            synchronized (regeneratingArenas) {
                regeneratingArenas.remove(arenaName);
            }
            logger.info(ChatColor.RED + " No sections found for region '" + arenaName + "'.");
            return;
        }

        Location min = null;
        Location max = null;
        for (Map<Location, BlockData> section : regionData.getSectionedBlockData().values()) {
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
            if (cancelRegen) {
                synchronized (regeneratingArenas) {
                    regeneratingArenas.remove(arenaName);
                }
                logger.info(ChatColor.RED + "Regeneration of '" + arenaName + "' canceled due to players inside the arena.");
                return;
            }

            for (Player p : playersInside) {
                if (killPlayers) {
                    p.setHealth(0.0);
                }
                if (teleportToSpawn) {
                    p.teleport(world.getSpawnLocation());
                }
                if (executeCommands && !commands.isEmpty()) {
                    for (String cmd : commands) {
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

        if (trackEntities) {
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

        logger.info(ChatColor.YELLOW + " Regenerating region '" + arenaName + "', please wait...");
        int blocksPerTick = regenType.equals("PRESET") ? switch (regenSpeed.toUpperCase()) {
            case "SLOW" -> 1000;
            case "NORMAL" -> 10000;
            case "FAST" -> 40000;
            case "VERYFAST" -> 100000;
            case "EXTREME" -> 4000000;
            default -> 10000;
        } : customRegenSpeed;

        AtomicInteger sectionIndex = new AtomicInteger(0);
        List<String> sectionNames = new ArrayList<>(regionData.getSectionedBlockData().keySet());
        AtomicInteger totalBlocksReset = new AtomicInteger(0);
        long startTime = System.currentTimeMillis();
        Set<Chunk> chunksToRefresh = new HashSet<>();
        Map<String, Integer> sectionProgress = new HashMap<>();

        Map<String, List<Map.Entry<Location, BlockData>>> sectionBlockLists = new HashMap<>();
        for (String sectionName : sectionNames) {
            Map<Location, BlockData> section = regionData.getSectionedBlockData().get(sectionName);
            sectionBlockLists.put(sectionName, new ArrayList<>(section.entrySet()));
        }

        Bukkit.getScheduler().runTaskTimer(this, task -> {
            int currentSection = sectionIndex.get();
            if (currentSection >= sectionNames.size()) {
                if (trackEntities) {
                    Map<Location, Map<String, Object>> entityDataMap = regionData.getEntityDataMap();
                    for (Map.Entry<Location, Map<String, Object>> entry : entityDataMap.entrySet()) {
                        Location loc = entry.getKey();
                        Map<String, Object> serializedEntity = entry.getValue();
                        try {
                            EntitySerializer.deserializeEntity(serializedEntity, loc);
                        } catch (Exception e) {
                            getLogger().warning("Failed to restore entity at " + loc + ": " + e.getMessage());
                        }
                    }
                }

                for (Chunk chunk : chunksToRefresh) {
                    try {
                        world.refreshChunk(chunk.getX(), chunk.getZ());
                    } catch (Exception e) {
                        logger.info(ChatColor.RED + "Failed to refresh chunk at " + chunk.getX() + "," + chunk.getZ() + ": " + e.getMessage());
                    }
                }

                long timeTaken = System.currentTimeMillis() - startTime;
                logger.info(ChatColor.GREEN + " Regeneration of '" + arenaName + "' complete! " +
                        ChatColor.GRAY + " (" + totalBlocksReset.get() + " blocks reset in " + (timeTaken / 1000.0) + "s)");
                synchronized (regeneratingArenas) {
                    regeneratingArenas.remove(arenaName);
                }
                task.cancel();
                return;
            }

            String sectionName = sectionNames.get(currentSection);
            List<Map.Entry<Location, BlockData>> blockList = sectionBlockLists.get(sectionName);

            int blockIndex = sectionProgress.getOrDefault(sectionName, 0);
            List<BlockUpdate> updates = new ArrayList<>();

            if (blockIndex == 0) {
                //logger.info("Starting regeneration of section " + sectionName + " in arena " + arenaName + " (" + blockList.size() + " total blocks)");
            }

            while (blockIndex < blockList.size() && updates.size() < blocksPerTick) {
                Map.Entry<Location, BlockData> entry = blockList.get(blockIndex);
                Location loc = entry.getKey();
                loc.setWorld(world);
                BlockData originalData = entry.getValue();

                boolean shouldUpdate;
                if (regenOnlyModified) {
                    Block block = world.getBlockAt(loc);
                    BlockData currentData = block.getBlockData();
                    shouldUpdate = !currentData.equals(originalData);
                    if (shouldUpdate) {
                        updates.add(new BlockUpdate(block.getX(), block.getY(), block.getZ(), originalData));
                        chunksToRefresh.add(block.getChunk());
                        totalBlocksReset.incrementAndGet();
                    }
                } else {
                    shouldUpdate = true;
                    updates.add(new BlockUpdate((int) loc.getX(), (int) loc.getY(), (int) loc.getZ(), originalData));
                    int chunkX = ((int) loc.getX()) >> 4;
                    int chunkZ = ((int) loc.getZ()) >> 4;
                    chunksToRefresh.add(world.getChunkAt(chunkX, chunkZ));
                    totalBlocksReset.incrementAndGet();
                }
                blockIndex++;
            }

            if (!updates.isEmpty()) {
                try {
                    NMSHandlerFactoryProvider.getNMSHandler().setBlocks(world, updates);
                } catch (Exception e) {
                    logger.info(ChatColor.RED + "Failed to set blocks in section " + sectionName + ": " + e.getMessage());
                }
            }

            if (blockIndex < blockList.size()) {
                sectionProgress.put(sectionName, blockIndex);
            } else {
                //logger.info("Finished regeneration of section " + sectionName + " in arena " + arenaName);
                sectionProgress.remove(sectionName);
                sectionIndex.incrementAndGet();
            }
        }, 0L, 1L);
    }


    public void regenerateArena(String arenaName, CommandSender sender) {
        synchronized (regeneratingArenas) {
            if (regeneratingArenas.contains(arenaName)) {
                sender.sendMessage(prefix + ChatColor.RED + " Arena '" + arenaName + "' is already being regenerated. Please wait until the current regeneration is complete.");
                return;
            }
            regeneratingArenas.add(arenaName);
        }

        RegionData regionData = registeredRegions.get(arenaName);
        if (regionData == null) {
            synchronized (regeneratingArenas) {
                regeneratingArenas.remove(arenaName);
            }
            sender.sendMessage(prefix + ChatColor.RED + " Arena '" + arenaName + "' not found.");
            return;
        }

        World world = Bukkit.getWorld(regionData.getWorldName());
        if (world == null) {
            synchronized (regeneratingArenas) {
                regeneratingArenas.remove(arenaName);
            }
            sender.sendMessage(prefix + ChatColor.RED + " World '" + regionData.getWorldName() + "' not found.");
            return;
        }

        if (regionData.getSectionedBlockData().isEmpty()) {
            synchronized (regeneratingArenas) {
                regeneratingArenas.remove(arenaName);
            }
            sender.sendMessage(prefix + ChatColor.RED + " No sections found for region '" + arenaName + "'.");
            return;
        }

        Location min = null;
        Location max = null;
        for (Map<Location, BlockData> section : regionData.getSectionedBlockData().values()) {
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
            if (cancelRegen) {
                synchronized (regeneratingArenas) {
                    regeneratingArenas.remove(arenaName);
                }
                logger.info(ChatColor.RED + "Regeneration of '" + arenaName + "' canceled due to players inside the arena.");
                return;
            }

            for (Player p : playersInside) {
                if (killPlayers) {
                    p.setHealth(0.0);
                }
                if (teleportToSpawn) {
                    p.teleport(world.getSpawnLocation());
                }
                if (executeCommands && !commands.isEmpty()) {
                    for (String cmd : commands) {
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

        if (trackEntities) {
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

        sender.sendMessage(prefix + ChatColor.YELLOW + " Regenerating region '" + arenaName + "', please wait...");
        int blocksPerTick = regenType.equals("PRESET") ? switch (regenSpeed.toUpperCase()) {
            case "SLOW" -> 1000;
            case "NORMAL" -> 10000;
            case "FAST" -> 40000;
            case "VERYFAST" -> 100000;
            case "EXTREME" -> 4000000;
            default -> 10000;
        } : customRegenSpeed;

        AtomicInteger sectionIndex = new AtomicInteger(0);
        List<String> sectionNames = new ArrayList<>(regionData.getSectionedBlockData().keySet());
        AtomicInteger totalBlocksReset = new AtomicInteger(0);
        long startTime = System.currentTimeMillis();
        Set<Chunk> chunksToRefresh = new HashSet<>();
        Map<String, Integer> sectionProgress = new HashMap<>();

        Map<String, List<Map.Entry<Location, BlockData>>> sectionBlockLists = new HashMap<>();
        for (String sectionName : sectionNames) {
            Map<Location, BlockData> section = regionData.getSectionedBlockData().get(sectionName);
            sectionBlockLists.put(sectionName, new ArrayList<>(section.entrySet()));
        }

        Bukkit.getScheduler().runTaskTimer(this, task -> {
            int currentSection = sectionIndex.get();
            if (currentSection >= sectionNames.size()) {
                if (trackEntities) {
                    Map<Location, Map<String, Object>> entityDataMap = regionData.getEntityDataMap();
                    for (Map.Entry<Location, Map<String, Object>> entry : entityDataMap.entrySet()) {
                        Location loc = entry.getKey();
                        Map<String, Object> serializedEntity = entry.getValue();
                        try {
                            EntitySerializer.deserializeEntity(serializedEntity, loc);
                        } catch (Exception e) {
                            getLogger().warning("Failed to restore entity at " + loc + ": " + e.getMessage());
                        }
                    }
                }

                for (Chunk chunk : chunksToRefresh) {
                    try {
                        world.refreshChunk(chunk.getX(), chunk.getZ());
                    } catch (Exception e) {
                        logger.info(ChatColor.RED + "Failed to refresh chunk at " + chunk.getX() + "," + chunk.getZ() + ": " + e.getMessage());
                    }
                }

                long timeTaken = System.currentTimeMillis() - startTime;
                sender.sendMessage(prefix + ChatColor.GREEN + " Regeneration of '" + arenaName + "' complete! " +
                        ChatColor.GRAY + " (" + totalBlocksReset.get() + " blocks reset in " + (timeTaken / 1000.0) + "s)");
                synchronized (regeneratingArenas) {
                    regeneratingArenas.remove(arenaName);
                }
                task.cancel();
                return;
            }

            String sectionName = sectionNames.get(currentSection);
            List<Map.Entry<Location, BlockData>> blockList = sectionBlockLists.get(sectionName);

            int blockIndex = sectionProgress.getOrDefault(sectionName, 0);
            List<BlockUpdate> updates = new ArrayList<>();

            if (blockIndex == 0) {
                //logger.info("Starting regeneration of section " + sectionName + " in arena " + arenaName + " (" + blockList.size() + " total blocks)");
            }

            while (blockIndex < blockList.size() && updates.size() < blocksPerTick) {
                Map.Entry<Location, BlockData> entry = blockList.get(blockIndex);
                Location loc = entry.getKey();
                loc.setWorld(world);
                BlockData originalData = entry.getValue();

                boolean shouldUpdate;
                if (regenOnlyModified) {
                    Block block = world.getBlockAt(loc);
                    BlockData currentData = block.getBlockData();
                    shouldUpdate = !currentData.equals(originalData);
                    if (shouldUpdate) {
                        updates.add(new BlockUpdate(block.getX(), block.getY(), block.getZ(), originalData));
                        chunksToRefresh.add(block.getChunk());
                        totalBlocksReset.incrementAndGet();
                    }
                } else {
                    shouldUpdate = true;
                    updates.add(new BlockUpdate((int) loc.getX(), (int) loc.getY(), (int) loc.getZ(), originalData));
                    int chunkX = ((int) loc.getX()) >> 4;
                    int chunkZ = ((int) loc.getZ()) >> 4;
                    chunksToRefresh.add(world.getChunkAt(chunkX, chunkZ));
                    totalBlocksReset.incrementAndGet();
                }
                blockIndex++;
            }

            if (!updates.isEmpty()) {
                try {
                    NMSHandlerFactoryProvider.getNMSHandler().setBlocks(world, updates);
                } catch (Exception e) {
                    logger.info(ChatColor.RED + "Failed to set blocks in section " + sectionName + ": " + e.getMessage());
                }
            }

            if (blockIndex < blockList.size()) {
                sectionProgress.put(sectionName, blockIndex);
            } else {
                //logger.info("Finished regeneration of section " + sectionName + " in arena " + arenaName);
                sectionProgress.remove(sectionName);
                sectionIndex.incrementAndGet();
            }
        }, 0L, 1L);
    }

    private Runnable createRegenerateTask(String arenaName) {
        return new Runnable() {
            @Override
            public void run() {
                if (!registeredRegions.containsKey(arenaName)) {
                    logger.info(ChatColor.RED + "Scheduled regeneration failed: Arena '" + arenaName + "' no longer exists.");
                    cancelScheduledRegeneration(arenaName);
                    return;
                }
                regenerateArenaSchedule(arenaName);
            }
        };
    }

    public Map<String, Integer> getScheduledTasks() {
        return scheduledTasks;
    }

    public Map<String, Long> getScheduledIntervals() {
        return scheduledIntervals;
    }

    public void previewArena(String arenaName, CommandSender sender) {
        updateParticle();
        RegionData regionData = registeredRegions.get(arenaName);
        if (regionData == null) {
            sender.sendMessage(prefix + ChatColor.RED + " Arena '" + arenaName + "' not found.");
            return;
        }

        World world = Bukkit.getWorld(regionData.getWorldName());
        if (world == null) {
            sender.sendMessage(prefix + ChatColor.RED + " World '" + regionData.getWorldName() + "' not found.");
            return;
        }

        int minX = regionData.getMinX();
        int minY = regionData.getMinY();
        int minZ = regionData.getMinZ();
        int maxX = regionData.getMaxX();
        int maxY = regionData.getMaxY();
        int maxZ = regionData.getMaxZ();

        logger.info("[ArenaRegen] Previewing arena '" + arenaName + "' with boundaries: (" +
                minX + ", " + minY + ", " + minZ + ") to (" + maxX + ", " + maxY + ", " + maxZ + ")");

        Location spawn = regionData.getSpawnLocation();
        if (spawn == null) {
            sender.sendMessage(prefix + ChatColor.YELLOW + " No spawn location set for '" + arenaName + "'. Showing boundaries only.");
        }


        sender.sendMessage(prefix + ChatColor.YELLOW + " Previewing arena '" + arenaName + "' for 15 seconds...");
        sender.sendMessage(prefix + ChatColor.GRAY + " Tip: Ensure your particle settings are set to 'All' or 'Decreased' in video settings to see particles.");

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= 300) {
                    sender.sendMessage(prefix + ChatColor.GREEN + " Preview of '" + arenaName + "' ended.");
                    cancel();
                    return;
                }

                Object particleData = null;
                if (previewParticle == Particle.DUST) {
                    particleData = new Particle.DustOptions(Color.RED, 1.5f);
                } else if (previewParticle == Particle.DUST_COLOR_TRANSITION) {

                    logger.info(ChatColor.YELLOW + "[ArenaRegen] Particle DUST_COLOR_TRANSITION is not supported in this version. Falling back to FLAME.");
                    previewParticle = Particle.FLAME;
                }

                for (int x = minX - 1; x <= maxX + 1; x += 1) {
                    world.spawnParticle(previewParticle, x + 0.5, (minY - 1) + 0.5, (minZ - 1) + 0.5, 3, 0, 0, 0, 0, particleData);
                    world.spawnParticle(previewParticle, x + 0.5, (minY - 1) + 0.5, (maxZ + 1) + 0.5, 3, 0, 0, 0, 0, particleData);
                    world.spawnParticle(previewParticle, x + 0.5, (maxY + 1) + 0.5, (minZ - 1) + 0.5, 3, 0, 0, 0, 0, particleData);
                    world.spawnParticle(previewParticle, x + 0.5, (maxY + 1) + 0.5, (maxZ + 1) + 0.5, 3, 0, 0, 0, 0, particleData);
                }
                for (int z = minZ - 1; z <= maxZ + 1; z += 1) {
                    world.spawnParticle(previewParticle, (minX - 1) + 0.5, (minY - 1) + 0.5, z + 0.5, 3, 0, 0, 0, 0, particleData);
                    world.spawnParticle(previewParticle, (maxX + 1) + 0.5, (minY - 1) + 0.5, z + 0.5, 3, 0, 0, 0, 0, particleData);
                    world.spawnParticle(previewParticle, (minX - 1) + 0.5, (maxY + 1) + 0.5, z + 0.5, 3, 0, 0, 0, 0, particleData);
                    world.spawnParticle(previewParticle, (maxX + 1) + 0.5, (maxY + 1) + 0.5, z + 0.5, 3, 0, 0, 0, 0, particleData);
                }
                for (int y = minY - 1; y <= maxY + 1; y += 1) {
                    world.spawnParticle(previewParticle, (minX - 1) + 0.5, y + 0.5, (minZ - 1) + 0.5, 3, 0, 0, 0, 0, particleData);
                    world.spawnParticle(previewParticle, (maxX + 1) + 0.5, y + 0.5, (minZ - 1) + 0.5, 3, 0, 0, 0, 0, particleData);
                    world.spawnParticle(previewParticle, (minX - 1) + 0.5, y + 0.5, (maxZ + 1) + 0.5, 3, 0, 0, 0, 0, particleData);
                    world.spawnParticle(previewParticle, (maxX + 1) + 0.5, y + 0.5, (maxZ + 1) + 0.5, 3, 0, 0, 0, 0, particleData);
                }

                if (spawn != null) {
                    world.spawnParticle(Particle.HAPPY_VILLAGER, spawn.getX(), spawn.getY() + 1, spawn.getZ(), 10, 0.5, 0.5, 0.5, 0, null);
                }

                ticks += 2;
            }
        }.runTaskTimer(this, 0L, 2L);
    }

    private void updateParticle() {
        try {
            if (previewParticleString != null) {
                Particle newParticle = Particle.valueOf(previewParticleString.toUpperCase());
                previewParticle = newParticle;
            } else {
                previewParticle = Particle.FLAME;
            }
        } catch (IllegalArgumentException e) {
            logger.info(ARChatColor.YELLOW + "Invalid particle type '" + previewParticleString + "' in config. Falling back to FLAME.");
            previewParticle = Particle.FLAME;
        }
    }
}