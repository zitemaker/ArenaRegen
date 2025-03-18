package com.zitemaker;

import com.zitemaker.commands.ArenaRegenCommand;
import com.zitemaker.utils.*;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ArenaRegen extends JavaPlugin {


    private Map<String, RegionData> registeredRegions = new HashMap<>();
    private Map<String, String> pendingDeletions = new HashMap<>();
    private Console console = new SpigotConsole();;
    private PlatformLogger platformLogger;
    private Logger logger = new Logger(new JavaPlatformLogger(console, getLogger()), true);
    private final boolean loggerColor = true;
    private final String purchaseLink = "https://zitemaker.tebex.io";

    @Override
    public void onEnable() {

        logger.info("");
        logger.info(ARChatColor.GOLD + "    +===============+");
        logger.info(ARChatColor.GOLD + "    |   ArenaRegen  |");
        logger.info(ARChatColor.GOLD + "    |---------------|");
        logger.info(ARChatColor.GOLD + "    |  Free Version |");
        logger.info(ARChatColor.GOLD + "    +===============+");
        logger.info("");
        logger.info(ARChatColor.AQUA + "    Purchase ArenaRegen+ for more features!");
        logger.info(ARChatColor.GREEN + "    " + getPurchaseLink());
        logger.info("");


        getConfig().options().copyDefaults();
        saveDefaultConfig();


        Bukkit.getPluginManager().registerEvents(new SelectionTool(), this);


        getCommand("arenaregen").setExecutor(new ArenaRegenCommand(this));


        getCommand("arenaregen").setTabCompleter(new ArenaRegenCommand(this));
    }

    @Override
    public void onDisable() {
        logger.info(ARChatColor.GREEN + "ArenaRegen has been disabled.");
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
                return true; // return true if player has atleast ONE permission like broo
            }
        }
        return false;
    }

    public String getPurchaseLink(){
        return purchaseLink;
    }
}