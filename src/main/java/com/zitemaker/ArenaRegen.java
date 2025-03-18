package com.zitemaker;

import com.zitemaker.commands.ArenaRegenCommand;
import com.zitemaker.util.RegionData;
import com.zitemaker.util.SelectionToolListener;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ArenaRegen extends JavaPlugin {

    SelectionToolListener selectionToolListener = new SelectionToolListener();

    // ANSI color code for green
    private static final String ANSI_GREEN = "\u001B[92m";
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_RED = "\u001B[31m";

    private final Map<String, RegionData> registeredRegions = new HashMap<>();
    private final Map<String, String> pendingDeletions = new HashMap<>();

    @Override
    public void onEnable() {
        // Plugin startup success message
        getLogger().info(ANSI_GREEN + "ArenaRegen.jar v" + getDescription().getVersion() + " has been enabled successfully" + ANSI_RESET);

        // Save default config
        getConfig().options().copyDefaults();
        saveDefaultConfig();

        // Register the event listeners
        Bukkit.getPluginManager().registerEvents(new SelectionToolListener(), this);

        // Register commands
        Objects.requireNonNull(getCommand("arenaregen")).setExecutor(new ArenaRegenCommand(this, selectionToolListener));

        // Register tab completer
        Objects.requireNonNull(getCommand("arenaregen")).setTabCompleter(new ArenaRegenCommand(this, selectionToolListener));
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