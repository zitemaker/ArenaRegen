package com.zitemaker;

import com.zitemaker.commands.ArenaRegenCommand;
import com.zitemaker.helpers.RegionData;
import com.zitemaker.helpers.SelectionToolListener;
import com.zitemaker.utils.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;

public class ArenaRegen extends JavaPlugin {

    private final SelectionToolListener selectionToolListener = new SelectionToolListener(this);
    private final Map<String, RegionData> registeredRegions = new HashMap<>();
    private final Map<String, String> pendingDeletions = new HashMap<>();
    private final Console console = new SpigotConsole();
    private final Logger logger = new Logger(new JavaPlatformLogger(console, getLogger()), true);
    private final Set<String> dirtyRegions = new HashSet<>();

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

        saveDefaultConfig();
        loadRegions();

        Bukkit.getPluginManager().registerEvents(selectionToolListener, this);

        ArenaRegenCommand commandExecutor = new ArenaRegenCommand(this, selectionToolListener);
        Objects.requireNonNull(getCommand("arenaregen")).setExecutor(commandExecutor);
        Objects.requireNonNull(getCommand("arenaregen")).setTabCompleter(commandExecutor);

        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            if (!dirtyRegions.isEmpty()) {
                saveRegions();
                dirtyRegions.clear();
            }
        }, 0L, 6000L);
    }

    @Override
    public void onDisable() {
        saveRegions();
        logger.info(ARChatColor.RED + "ArenaRegen v" + getDescription().getVersion() + " has been disabled.");
    }

    public Map<String, RegionData> getRegisteredRegions() {
        return registeredRegions;
    }

    public Map<String, String> getPendingDeletions() {
        return pendingDeletions;
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