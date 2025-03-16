package com.zitemaker;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ArenaRegen extends JavaPlugin {

    private Map<String, RegionData> registeredRegions = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // Load saved regions from config
        loadRegions();

        // Register commands
        getCommand("register").setExecutor(new RegisterCommand(this));
        getCommand("regen").setExecutor(new RegenCommand(this));
        getCommand("rg").setExecutor(new RegionCommand(this));

        getLogger().info("Plugin enabled!");
    }

    @Override
    public void onDisable() {
        // Save regions to config
        saveRegions();

        getLogger().info("Plugin disabled!");
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