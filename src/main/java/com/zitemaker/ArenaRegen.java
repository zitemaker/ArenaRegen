package com.zitemaker;

import com.zitemaker.commands.ArenaRegenCommand;
import com.zitemaker.helpers.RegionData;
import com.zitemaker.utils.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;
import java.util.logging.Level;

public class ArenaRegen extends JavaPlugin {

    private File messagesFile;
    private FileConfiguration messagesConfig;

    private final Map<String, RegionData> registeredRegions = new ConcurrentHashMap<>();
    private final Map<String, String> pendingDeletions = new ConcurrentHashMap<>();
    private final Map<String, String> pendingRegenerations = new ConcurrentHashMap<>();
    public final Console console = new SpigotConsole();
    private final Logger logger = new Logger(new JavaPlatformLogger(console, getLogger()), true);
    private final Set<String> dirtyRegions = new HashSet<>();

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
    public Material selectionTool;

    private int saveTaskId = -1;

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

        reloadPluginConfig();
        loadMessagesFile();
        saveMessagesFile();
        loadRegions();


        ArenaRegenCommand commandExecutor = new ArenaRegenCommand(this);
        Objects.requireNonNull(getCommand("arenaregen")).setExecutor(commandExecutor);
        Objects.requireNonNull(getCommand("arenaregen")).setTabCompleter(commandExecutor);
        Bukkit.getPluginManager().registerEvents(commandExecutor, this);

        saveTaskId = Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            if (!dirtyRegions.isEmpty()) {
                saveRegions();
                dirtyRegions.clear();
            }
        }, 0L, 6000L).getTaskId();
    }

    @Override
    public void onDisable() {
        if (saveTaskId != -1) {
            Bukkit.getScheduler().cancelTask(saveTaskId);
            saveTaskId = -1;
        }
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
        }

        if (savedRegions == regionsToSave.size()) {
            dirtyRegions.clear();
            console.sendMessage("Cleared dirty regions list.");
        } else {
            console.sendMessage("Preserving dirty regions list due to save errors.");
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
        this.selectionTool = Material.valueOf(getConfig().getString("general.selection-tool", "GOLDEN_HOE").toUpperCase());
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
        File[] files = arenasDir.listFiles((dir, name) -> name.endsWith(".datc"));
        if (files == null) return;

        for (File file : files) {
            String regionName = file.getName().replace(".datc", "");
            RegionData regionData = new RegionData(this);
            try {
                regionData.loadFromDatc(file);
                registeredRegions.put(regionName, regionData);
                logger.info(ARChatColor.GREEN + "Loaded arena '" + regionName + "' successfully.");
            } catch (Exception e) {
                logger.info(ARChatColor.RED + "Failed to load arena '" + regionName + "' from " + file.getName() + ": " + e.getMessage());
                logger.info(ARChatColor.YELLOW + "Skipping '" + regionName + "'. You may need to delete or fix the file.");
            }
        }
    }

    public void saveRegions() {
        File arenasDir = new File(getDataFolder(), "arenas");
        arenasDir.mkdirs();
        for (String regionName : registeredRegions.keySet()) {
            if (dirtyRegions.contains(regionName)) {
                File datcFile = new File(arenasDir, regionName + ".datc");
                try {
                    registeredRegions.get(regionName).saveToDatc(datcFile);
                } catch (IOException e) {
                    getLogger().severe("Failed to save " + regionName + ".datc: " + e.getMessage());
                }
            }
        }
    }

    public void markRegionDirty(String regionName) {
        dirtyRegions.add(regionName);
    }
}
