package com.zitemaker;

import com.zitemaker.commands.ArenaRegenCommand;
import com.zitemaker.commands.RegenCommand;
import com.zitemaker.commands.RegionCommand;
import com.zitemaker.commands.RegisterCommand;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.util.HashMap;
import java.util.Map;

public class ArenaRegen extends JavaPlugin {

    // ANSI color code for green
    private static final String ANSI_GREEN = "\u001B[92m";
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_RED = "\u001B[31m";

    private Map<String, RegionData> registeredRegions = new HashMap<>();

    @Override
    public void onEnable() {
        // Plugin startup success message
        getLogger().info(ANSI_GREEN + "ArenaRegen.jar v" + getDescription().getVersion() + " has been enabled successfully" + ANSI_RESET);

        // Save default config
        getConfig().options().copyDefaults();
        saveDefaultConfig();

        // Register the event listeners

        // Register commands
        getCommand("arenaregen").setExecutor(new ArenaRegenCommand(this));

        // Register tab completer
        getCommand("arenaregen").setTabCompleter(new ArenaRegenCommand(this));
    }

    @Override
    public void onDisable() {
        // Save regions to config
        saveRegions();

        // Plugin disable success message
        getLogger().info(ANSI_RED + "ArenaRegen.jar v" + getDescription().getVersion() + " has been disabled successfully" + ANSI_RESET);
    }

    public Map<String, RegionData> getRegisteredRegions() {
        return registeredRegions;
    }

    // Save all regions to config
    public void saveRegions() {
        File configFile = new File(getDataFolder(), "regions.yml");
        YamlConfiguration config = new YamlConfiguration();

        // Save each region
        for (Map.Entry<String, RegionData> entry : registeredRegions.entrySet()) {
            ConfigurationSection regionSection = config.createSection(entry.getKey());
            entry.getValue().serialize(regionSection);
        }

        // Save to file
        try {
            config.save(configFile);
        } catch (IOException e) {
            getLogger().severe("Failed to save regions to config: " + e.getMessage());
        }
    }

    // Load all regions from config
    private void loadRegions() {
        File configFile = new File(getDataFolder(), "regions.yml");
        if (!configFile.exists()) {
            return; // No config file, nothing to load
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);

        // Load each region
        for (String key : config.getKeys(false)) {
            ConfigurationSection regionSection = config.getConfigurationSection(key);
            if (regionSection != null) {
                registeredRegions.put(key, RegionData.deserialize(regionSection, Bukkit.getWorlds().get(0))); // Use the first world as default
            }
        }
    }
}