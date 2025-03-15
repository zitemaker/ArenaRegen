package DiamondPlugin.untitled2;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class Untitled2 extends JavaPlugin {

    private Map<String, RegionData> registeredRegions = new HashMap<>();
    private boolean trackEntities;
    private String arenaRegenMessage;

    @Override
    public void onEnable() {
        // Load config
        loadConfig();

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

    public boolean isTrackEntities() {
        return trackEntities;
    }

    public String getArenaRegenMessage() {
        return arenaRegenMessage;
    }

    // Load config
    private void loadConfig() {
        File configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            saveDefaultConfig(); // Create default config if it doesn't exist
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);

        // Set defaults
        config.addDefault("track-entities", true);
        config.addDefault("messages.arena-regen", "&aArena Regen");
        config.options().copyDefaults(true);

        // Save updated config
        try {
            config.save(configFile);
        } catch (IOException e) {
            getLogger().severe("Failed to save config: " + e.getMessage());
        }

        // Load settings
        trackEntities = config.getBoolean("track-entities", true);
        arenaRegenMessage = config.getString("messages.arena-regen", "&aArena Regen");
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