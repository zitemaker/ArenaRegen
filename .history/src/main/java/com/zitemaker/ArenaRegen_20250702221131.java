package com.zitemaker;

import com.zitemaker.commands.ArenaRegenCommand;
import com.zitemaker.helpers.EntitySerializer;
import com.zitemaker.helpers.RegionData;
import com.zitemaker.listeners.PlayerMoveListener;
import com.zitemaker.nms.BlockUpdate;
import com.zitemaker.nms.NMSHandlerFactoryProvider;
import com.zitemaker.placeholders.ArenaRegenExpansion;
import com.zitemaker.utils.*;
import org.bukkit.*;
import org.bukkit.block.Banner;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

public class ArenaRegen extends JavaPlugin{

    private File messagesFile;
    private FileConfiguration messagesConfig;

    private final Map<String, RegionData> registeredRegions = new ConcurrentHashMap<>();
    private final Map<String, String> pendingDeletions = new ConcurrentHashMap<>();
    private final Map<String, String> pendingRegenerations = new ConcurrentHashMap<>();
    public final Console console = new SpigotConsole();
    public final Logger logger = new Logger(new JavaPlatformLogger(console, getLogger()), true);
    public final Set<String> dirtyRegions = new HashSet<>();
    private final Set<String> regeneratingArenas = new HashSet<>();

    // config stuff
    public String prefix;
    public String regenType;
    public String regenSpeed;
    public int customRegenSpeed;
    public int analyzeSpeed;
    public int arenaSize;
    public int maxArenas = 8;
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
    private boolean lockDuringRegeneration;

    private int saveTaskId = -1;
    private final Map<String, Integer> scheduledTasks = new ConcurrentHashMap<>();
    private final Map<String, Long> scheduledIntervals = new ConcurrentHashMap<>();
    private final Map<String, Long> taskStartTimes = new ConcurrentHashMap<>();
    private File schedulesFile;
    private FileConfiguration schedulesConfig;
    private ArenaRegenExpansion placeholderExpansion;

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
        } catch (ClassNotFoundException ignored) {
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
        loadRegionsAsync().thenRun(() -> {
            loadSchedules();
            ArenaRegenCommand commandExecutor = new ArenaRegenCommand(this);
            Objects.requireNonNull(getCommand("arenaregen")).setExecutor(commandExecutor);
            Objects.requireNonNull(getCommand("arenaregen")).setTabCompleter(commandExecutor);
            Bukkit.getPluginManager().registerEvents(commandExecutor, this);
            Bukkit.getPluginManager().registerEvents(new PlayerMoveListener(this), this);

            File arenasDir = new File(getDataFolder(), "arenas");
            if (!arenasDir.exists()) {
                boolean mkdirs = arenasDir.mkdirs();
            }
            if (!arenasDir.canRead() || !arenasDir.canWrite()) {
                logger.info(ARChatColor.RED + "ERROR: The arenas directory (" + arenasDir.getPath() + ") is not readable or writable!");
                logger.info(ARChatColor.RED + "Please check file permissions to ensure the server process has read/write access.");
            }

            saveTaskId = Bukkit.getScheduler().runTaskTimerAsynchronously(this, this::saveRegionsAsync, 0L, 4000L).getTaskId();
            rescheduleTasks();
            logger.info("Plugin fully enabled.");
        }).exceptionally(e -> {
            logger.info("Failed to load regions during enable: " + e.getMessage());
            return null;
        });
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            placeholderExpansion = new ArenaRegenExpansion(this);
            placeholderExpansion.register();
            logger.info(ARChatColor.GREEN + "PlaceholderAPI integration enabled!");
        } else {
            logger.info(ARChatColor.YELLOW + "PlaceholderAPI not found. Placeholder support disabled.");
        }
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
        logger.info(ARChatColor.RED + "ArenaRegen v" + getDescription().getVersion() + " has been disabled.");
    }


    private CompletableFuture<Void> loadRegionsAsync() {
        File arenasDir = new File(getDataFolder(), "arenas");
        if (!arenasDir.exists()) {
            boolean mkdirs = arenasDir.mkdirs();
            return CompletableFuture.completedFuture(null);
        }

        if (!arenasDir.canRead()) {
            logger.info(ARChatColor.RED + "ERROR: Cannot read from arenas directory (" + arenasDir.getPath() + ")!");
            logger.info(ARChatColor.RED + "Please check file permissions to ensure the server process has read access.");
            return CompletableFuture.completedFuture(null);
        }

        File[] files = arenasDir.listFiles((dir, name) -> name.endsWith(".datc"));
        if (files == null || files.length == 0) {
            logger.info(ARChatColor.YELLOW + "No arenas found in " + arenasDir.getPath() + ".");
            return CompletableFuture.completedFuture(null);
        }

        List<CompletableFuture<Void>> loadFutures = new ArrayList<>();
        AtomicInteger loadedRegions = new AtomicInteger(0);
        StringBuilder errorSummary = new StringBuilder();

        for (File file : files) {
            String regionName = file.getName().replace(".datc", "");
            RegionData regionData = new RegionData(this);
            regionData.setDatcFile(file);

            CompletableFuture<Void> loadFuture = regionData.loadFromDatc(file)
                    .thenRun(() -> {
                        registeredRegions.put(regionName, regionData);
                        loadedRegions.incrementAndGet();
                        logger.info(ARChatColor.GREEN + "Loaded arena '" + regionName + "' successfully.");
                    })
                    .exceptionally(e -> {
                        logger.info(ARChatColor.RED + "Failed to load arena '" + regionName + "' from " + file.getName() + ": " + e.getMessage());
                        logger.info(ARChatColor.YELLOW + "Skipping '" + regionName + "'. You may need to delete or fix the file.");
                        errorSummary.append(ARChatColor.RED)
                                .append(" - Arena '").append(regionName).append("': ").append(e.getMessage()).append("\n");
                        return null;
                    });

            loadFutures.add(loadFuture);
        }

        return CompletableFuture.allOf(loadFutures.toArray(new CompletableFuture[0]))
                .thenRun(() -> {
                    int totalLoaded = loadedRegions.get();
                    logger.info("Successfully loaded " + totalLoaded + " out of " + files.length + " arenas.");

                    if (totalLoaded < files.length) {
                        logger.info(ARChatColor.RED + "Errors occurred while loading the following arenas:");
                        logger.info(errorSummary.toString());
                        for (Player player : Bukkit.getOnlinePlayers()) {
                            if (player.isOp()) {
                                player.sendMessage(prefix + " " + ARChatColor.RED + "Failed to load some arenas on startup! Check the server logs for details.");
                            }
                        }
                    }
                });
    }

    public void saveRegionsAsync() {
        File arenasDir = new File(getDataFolder(), "arenas");
        boolean mkdirs = arenasDir.mkdirs();

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
        AtomicInteger savedRegions = new AtomicInteger(0);
        StringBuilder errorSummary = new StringBuilder();

        List<CompletableFuture<Void>> saveFutures = new ArrayList<>();
        for (String regionName : regionsToProcess) {
            if (!regionsToSave.containsKey(regionName)) {
                continue;
            }
            File datcFile = new File(arenasDir, regionName + ".datc");
            CompletableFuture<Void> saveFuture = regionsToSave.get(regionName).saveToDatc(datcFile)
                    .thenRun(() -> {
                        savedRegions.incrementAndGet();
                        logger.info("Saved region '" + regionName + "' to " + datcFile.getPath());
                    })
                    .exceptionally(e -> {
                        logger.info("Failed to save region '" + regionName + "' to " + datcFile.getPath() + ": " + e.getMessage());
                        errorSummary.append(ARChatColor.RED)
                                .append(" - Region '").append(regionName).append("': ").append(e.getMessage()).append("\n");
                        return null;
                    });
            saveFutures.add(saveFuture);
        }

        CompletableFuture.allOf(saveFutures.toArray(new CompletableFuture[0]))
                .thenRun(() -> {
                    int totalSaved = savedRegions.get();
                    console.sendMessage("Successfully saved " + totalSaved + " out of " + regionsToProcess.size() + " dirty regions.");

                    if (totalSaved < regionsToProcess.size()) {
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
                });
    }

    public void loadConfigValues() {
        this.prefix = ChatColor.translateAlternateColorCodes('&', getConfig().getString("prefix", "&e[&2ArenaRegen&e]"));
        this.regenType = getConfig().getString("regen.regen-speed-type", "PRESET").toUpperCase();
        this.regenSpeed = getConfig().getString("regen.regen-speed", "FAST").toUpperCase();
        this.customRegenSpeed = getConfig().getInt("regen.custom-regen-speed", 10000);
        this.analyzeSpeed = getConfig().getInt("general.analyze-speed", 40000);
        this.arenaSize = getConfig().getInt("general.arena-size-limit", 40000);
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
        this.lockDuringRegeneration = getConfig().getBoolean("regen.lock-arenas", true);
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
        return "https://www.spigotmc.org/resources/124624/";
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
        taskStartTimes.put(arenaName, System.currentTimeMillis());
        //getLogger().info("Scheduled regeneration for " + arenaName + " with interval " + intervalTicks + " ticks at " + taskStartTimes.get(arenaName));

        saveSchedules();

        if (placeholderExpansion != null) {
            placeholderExpansion.onScheduleUpdate();
        }
    }

    public void cancelScheduledRegeneration(String arenaName) {
        Integer taskId = scheduledTasks.remove(arenaName);
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
            getLogger().info("Canceled scheduled regeneration for " + arenaName);
        }
        scheduledIntervals.remove(arenaName);
        taskStartTimes.remove(arenaName);
        saveSchedules();

        if (placeholderExpansion != null) {
            placeholderExpansion.onScheduleUpdate();
        }
    }

    public boolean isScheduled(String arenaName) {
        return scheduledTasks.containsKey(arenaName);
    }

    public Long getScheduledInterval(String arenaName) {
        return scheduledIntervals.get(arenaName);
    }

    public Map<String, Long> getTaskStartTimes() {
        return taskStartTimes;
    }

    private void loadSchedules() {
        schedulesFile = new File(getDataFolder(), "schedules.yml");
        if (!schedulesFile.exists()) {
            saveResource("schedules.yml", false);
        }
        schedulesConfig = YamlConfiguration.loadConfiguration(schedulesFile);

        if (schedulesConfig.contains("schedules")) {
            for (String arenaName : Objects.requireNonNull(schedulesConfig.getConfigurationSection("schedules")).getKeys(false)) {
                long intervalTicks = schedulesConfig.getLong("schedules." + arenaName + ".interval");
                if (intervalTicks >= 200) {
                    scheduledIntervals.put(arenaName, intervalTicks);
                    long startTime = schedulesConfig.getLong("schedules." + arenaName + ".startTime", System.currentTimeMillis());
                    taskStartTimes.put(arenaName, startTime);
                    getLogger().info("Loaded schedule for " + arenaName + ": interval=" + intervalTicks + ", startTime=" + startTime);
                }
            }
        }
    }

    private void saveSchedules() {
        schedulesConfig.set("schedules", null);
        for (Map.Entry<String, Long> entry : scheduledIntervals.entrySet()) {
            String arenaName = entry.getKey();
            schedulesConfig.set("schedules." + arenaName + ".interval", entry.getValue());
            schedulesConfig.set("schedules." + arenaName + ".startTime", taskStartTimes.get(arenaName));
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
                if (!taskStartTimes.containsKey(arenaName)) {
                    taskStartTimes.put(arenaName, System.currentTimeMillis());
                    getLogger().info("Set start time for " + arenaName + " to " + taskStartTimes.get(arenaName) + " during reschedule");
                }
            } else {
                scheduledIntervals.remove(arenaName);
                taskStartTimes.remove(arenaName);
            }
        }
        saveSchedules();
    }



    public void regenerateArena(String arenaName, CommandSender sender) {
        synchronized (regeneratingArenas) {
            if (regeneratingArenas.contains(arenaName)) {
                if (sender != null) {
                    sender.sendMessage(prefix + ChatColor.RED + " Arena '" + arenaName + "' is already being regenerated. Please wait until the current regeneration is complete.");
                }
                return;
            }
            regeneratingArenas.add(arenaName);
        }

        RegionData regionData = registeredRegions.get(arenaName);
        if (regionData == null) {
            synchronized (regeneratingArenas) {
                regeneratingArenas.remove(arenaName);
            }
            if (sender != null) {
                sender.sendMessage(prefix + ChatColor.RED + " Arena '" + arenaName + "' not found.");
            }
            return;
        }

        regionData.ensureBlockDataLoadedAsync().thenRun(() -> {
            World world = Bukkit.getWorld(regionData.getWorldName());
            if (world == null) {
                synchronized (regeneratingArenas) {
                    regeneratingArenas.remove(arenaName);
                }
                if (sender != null) {
                    sender.sendMessage(prefix + ChatColor.RED + " World '" + regionData.getWorldName() + "' not found.");
                }
                return;
            }

            if (regionData.getSectionedBlockData().isEmpty()) {
                synchronized (regeneratingArenas) {
                    regeneratingArenas.remove(arenaName);
                }
                if (sender != null) {
                    sender.sendMessage(prefix + ChatColor.RED + " No sections found for region '" + arenaName + "'.");
                }
                return;
            }

            boolean wasLocked = regionData.isLocked();
            if (lockDuringRegeneration && !wasLocked) {
                regionData.setLocked(true);
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

            if (sender != null) {
                sender.sendMessage(prefix + ChatColor.YELLOW + " Regenerating region '" + arenaName + "', please wait...");
            }
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

                    Map<Location, Map<String, Object>> bannerStates = regionData.getBannerStates();
                    for (Map.Entry<Location, Map<String, Object>> entry : bannerStates.entrySet()) {
                        Location loc = entry.getKey();
                        Map<String, Object> bannerData = entry.getValue();

                        try {
                            BlockState state = world.getBlockAt(loc).getState();
                            if (state instanceof Banner banner) {
                                String baseColorStr = (String) bannerData.get("baseColor");
                                DyeColor baseColor = baseColorStr.equals("NONE") ? null : DyeColor.valueOf(baseColorStr);
                                banner.setBaseColor(Objects.requireNonNull(baseColor));

                                List<Map<String, String>> patternDataList = (List<Map<String, String>>) bannerData.get("patterns");
                                if (patternDataList != null && !patternDataList.isEmpty()) {
                                    List<Pattern> patterns = new ArrayList<>();
                                    for (Map<String, String> patternData : patternDataList) {
                                        DyeColor color = DyeColor.valueOf(patternData.get("color"));
                                        String typeStr = patternData.get("type");
                                        NamespacedKey key = new NamespacedKey("minecraft", typeStr);
                                        PatternType patternType = Bukkit.getRegistry(PatternType.class).get(key);
                                        if (patternType == null) {
                                            getLogger().warning("Invalid pattern type '" + typeStr + "' for banner at " + loc + ", defaulting to base.");
                                            patternType = PatternType.BASE;
                                        }
                                        patterns.add(new Pattern(color, patternType));
                                    }
                                    banner.setPatterns(patterns);
                                }

                                Map<String, Object> pdcData = (Map<String, Object>) bannerData.get("persistentData");
                                if (pdcData != null && !pdcData.isEmpty()) {
                                    PersistentDataContainer pdc = banner.getPersistentDataContainer();
                                    for (Map.Entry<String, Object> pdcEntry : pdcData.entrySet()) {
                                        NamespacedKey key = NamespacedKey.fromString(pdcEntry.getKey());
                                        if (key == null) {
                                            getLogger().warning("Invalid NamespacedKey '" + pdcEntry.getKey() + "' for banner at " + loc + ", skipping PDC entry.");
                                            continue;
                                        }
                                        Object value = pdcEntry.getValue();
                                        if (value instanceof String s) {
                                            pdc.set(key, PersistentDataType.STRING, s);
                                        } else if (value instanceof Integer i) {
                                            pdc.set(key, PersistentDataType.INTEGER, i);
                                        } else if (value instanceof Double d) {
                                            pdc.set(key, PersistentDataType.DOUBLE, d);
                                        } else if (value instanceof Byte b) {
                                            pdc.set(key, PersistentDataType.BYTE, b);
                                        } else if (value instanceof Long l) {
                                            pdc.set(key, PersistentDataType.LONG, l);
                                        } else {
                                            getLogger().warning("Unsupported PDC value type for key " + key + " at " + loc + ", skipping.");
                                        }
                                    }
                                }

                                banner.update();
                            } else {
                                getLogger().warning("Block at " + loc + " is not a banner, cannot restore banner state.");
                            }
                        } catch (Exception e) {
                            getLogger().warning("Failed to restore banner at " + loc + ": " + e.getMessage());
                        }
                    }

                    Map<Location, Map<String, Object>> signStates = regionData.getSignStates();
                    for (Map.Entry<Location, Map<String, Object>> entry : signStates.entrySet()) {
                        Location loc = entry.getKey();
                        Map<String, Object> signData = entry.getValue();

                        try {
                            BlockState state = world.getBlockAt(loc).getState();
                            if (state instanceof Sign sign) {
                                List<String> lines = (List<String>) signData.get("lines");
                                if (lines != null) {
                                    for (int i = 0; i < Math.min(lines.size(), 4); i++) {
                                        sign.setLine(i, lines.get(i));
                                    }
                                }

                                String colorStr = (String) signData.get("color");
                                DyeColor color = DyeColor.BLACK;
                                try {
                                    color = DyeColor.valueOf(colorStr);
                                } catch (IllegalArgumentException e) {
                                    getLogger().warning("Invalid color '" + colorStr + "' for sign at " + loc + ", defaulting to BLACK.");
                                }
                                sign.setColor(color);

                                boolean glowing = (boolean) signData.getOrDefault("glowing", false);
                                sign.setGlowingText(glowing);

                                Map<String, Object> pdcData = (Map<String, Object>) signData.get("persistentData");
                                if (pdcData != null && !pdcData.isEmpty()) {
                                    PersistentDataContainer pdc = sign.getPersistentDataContainer();
                                    for (Map.Entry<String, Object> pdcEntry : pdcData.entrySet()) {
                                        NamespacedKey key = NamespacedKey.fromString(pdcEntry.getKey());
                                        if (key == null) {
                                            getLogger().warning("Invalid NamespacedKey '" + pdcEntry.getKey() + "' for sign at " + loc + ", skipping PDC entry.");
                                            continue;
                                        }
                                        Object value = pdcEntry.getValue();
                                        if (value instanceof String s) {
                                            pdc.set(key, PersistentDataType.STRING, s);
                                        } else if (value instanceof Integer i) {
                                            pdc.set(key, PersistentDataType.INTEGER, i);
                                        } else if (value instanceof Double d) {
                                            pdc.set(key, PersistentDataType.DOUBLE, d);
                                        } else if (value instanceof Byte b) {
                                            pdc.set(key, PersistentDataType.BYTE, b);
                                        } else if (value instanceof Long l) {
                                            pdc.set(key, PersistentDataType.LONG, l);
                                        } else {
                                            getLogger().warning("Unsupported PDC value type for key " + key + " at " + loc + ", skipping.");
                                        }

                                    }
                                }

                                sign.update();
                            } else {
                                getLogger().warning("Block at " + loc + " is not a sign, cannot restore sign state.");
                            }
                        } catch (Exception e) {
                            getLogger().warning("Failed to restore sign at " + loc + ": " + e.getMessage());
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
                    if (sender != null) {
                        sender.sendMessage(prefix + ChatColor.GREEN + " Regeneration of '" + arenaName + "' complete! " +
                                ChatColor.GRAY + " (" + totalBlocksReset.get() + " blocks reset in " + (timeTaken / 1000.0) + "s)");
                    }
                    synchronized (regeneratingArenas) {
                        regeneratingArenas.remove(arenaName);
                    }
                    task.cancel();

                    if (regionData.isLocked()) {
                        regionData.setLocked(false);
                    }
                    return;
                }

                String sectionName = sectionNames.get(currentSection);
                List<Map.Entry<Location, BlockData>> blockList = sectionBlockLists.get(sectionName);

                int blockIndex = sectionProgress.getOrDefault(sectionName, 0);
                List<BlockUpdate> updates = new ArrayList<>();

                if (blockIndex == 0) {
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
                    sectionProgress.remove(sectionName);
                    sectionIndex.incrementAndGet();
                }
            }, 0L, 1L);
        }).exceptionally(e -> {
            synchronized (regeneratingArenas) {
                regeneratingArenas.remove(arenaName);
            }
            if (sender != null) {
                sender.sendMessage(prefix + ChatColor.RED + " Regeneration failed for '" + arenaName + "': " + e.getMessage());
            }
            return null;
        });
    }

    private Runnable createRegenerateTask(String arenaName) {
        return () -> {
            if (!registeredRegions.containsKey(arenaName)) {
                logger.info(ChatColor.RED + "Scheduled regeneration failed: Arena '" + arenaName + "' no longer exists.");
                cancelScheduledRegeneration(arenaName);
                return;
            }
            regenerateArena(arenaName, null);
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
                previewParticle = Particle.valueOf(previewParticleString.toUpperCase());
            } else {
                previewParticle = Particle.FLAME;
            }
        } catch (IllegalArgumentException e) {
            logger.info(ARChatColor.YELLOW + "Invalid particle type '" + previewParticleString + "' in config. Falling back to FLAME.");
            previewParticle = Particle.FLAME;
        }
    }

    public boolean isArenaRegenerating(String arenaName) {
        return regeneratingArenas.contains(arenaName);
    }

    public boolean isOverlapping(String excludeRegionName, String worldName, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        for (Map.Entry<String, RegionData> entry : registeredRegions.entrySet()) {
            String regionName = entry.getKey();
            if (regionName.equals(excludeRegionName)) {
                continue;
            }

            RegionData region = entry.getValue();
            if (!region.getWorldName().equals(worldName)) {
                continue;
            }

            if (minX <= region.getMaxX() && maxX >= region.getMinX() &&
                minY <= region.getMaxY() && maxY >= region.getMinY() &&
                minZ <= region.getMaxZ() && maxZ >= region.getMinZ()) {
                return true;
            }
        }
        return false;
    }

}