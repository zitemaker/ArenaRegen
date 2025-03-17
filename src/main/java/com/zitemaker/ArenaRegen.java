package com.zitemaker;

import com.zitemaker.commands.ArenaRegenCommand;
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

    // ANSI color code for green
    private static final String ANSI_GREEN = "\u001B[92m";
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_RED = "\u001B[31m";

    private Map<String, RegionData> registeredRegions = new HashMap<>();
    private Map<String, String> pendingDeletions = new HashMap<>();

    @Override
    public void onEnable() {
        // Plugin startup success message
        getLogger().info(ANSI_GREEN + "ArenaRegen.jar v" + getDescription().getVersion() + " has been enabled successfully" + ANSI_RESET);

        // Save default config
        getConfig().options().copyDefaults();
        saveDefaultConfig();

        // Register the event listeners
        Bukkit.getPluginManager().registerEvents(new SelectionTool(), this);

        // Register commands
        getCommand("arenaregen").setExecutor(new ArenaRegenCommand(this));

        // Register tab completer
        getCommand("arenaregen").setTabCompleter(new ArenaRegenCommand(this));
    }

    @Override
    public void onDisable() {

        // Plugin disable success message
        getLogger().info(ANSI_RED + "ArenaRegen.jar v" + getDescription().getVersion() + " has been disabled successfully" + ANSI_RESET);
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
}