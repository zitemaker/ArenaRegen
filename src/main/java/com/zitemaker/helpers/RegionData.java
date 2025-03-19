package com.zitemaker.helpers;

import com.zitemaker.ArenaRegen;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;

import java.util.HashMap;
import java.util.Map;

public class RegionData {

    private final ArenaRegen plugin;
    private final Map<Location, BlockData> blockDataMap = new HashMap<>();
    private final Map<Location, EntityType> entityMap = new HashMap<>();
    private Location spawnLocation;

    public RegionData(ArenaRegen plugin) {
        this.plugin = plugin;
    }

    public void addBlock(Location location, BlockData blockData) {
        blockDataMap.put(location, blockData);
    }

    public void clearRegion(String regionName) {
        blockDataMap.clear();
        entityMap.clear();
        spawnLocation = null;
    }

    public Map<Location, BlockData> getBlockDataMap() {
        return blockDataMap;
    }

    public Map<Location, EntityType> getEntityMap() {
        return entityMap;
    }

    public Location getSpawnLocation() {
        return spawnLocation;
    }

    public void setSpawnLocation(Location location) {
        this.spawnLocation = location;
    }

    public void saveToConfig(FileConfiguration config, String path) {
        if (!blockDataMap.isEmpty()) {
            World world = blockDataMap.keySet().iterator().next().getWorld();
            config.set(path + ".world", world.getName());
            for (Map.Entry<Location, BlockData> entry : blockDataMap.entrySet()) {
                String key = coordsToString(entry.getKey());
                config.set(path + ".blocks." + key, entry.getValue().getAsString());
            }
        }
        if (spawnLocation != null) {
            config.set(path + ".spawn", coordsToString(spawnLocation));
        }
    }

    public void loadFromConfig(FileConfiguration config, String path) {
        String worldName = config.getString(path + ".world");
        World world = worldName != null ? Bukkit.getWorld(worldName) : null;
        if (world == null) {
            plugin.getLogger().warning("Invalid or missing world for region '" + path + "'");
            return;
        }

        if (config.contains(path + ".blocks")) {
            for (String key : config.getConfigurationSection(path + ".blocks").getKeys(false)) {
                try {
                    Location loc = stringToCoords(key, world);
                    if (loc == null) continue;
                    String blockDataStr = config.getString(path + ".blocks." + key);
                    if (blockDataStr == null) continue;
                    BlockData blockData = Bukkit.createBlockData(blockDataStr);
                    blockDataMap.put(loc, blockData);
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to load block at '" + key + "' in region '" + path + "': " + e.getMessage());
                }
            }
        }
        if (config.contains(path + ".spawn")) {
            String spawnStr = config.getString(path + ".spawn");
            if (spawnStr != null) {
                spawnLocation = stringToCoords(spawnStr, world);
                if (spawnLocation == null) {
                    plugin.getLogger().warning("Failed to load spawn location for region '" + path + "'");
                }
            }
        }
    }

    private String coordsToString(Location loc) {
        if (loc == null) return "";
        return loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }

    private Location stringToCoords(String s, World world) {
        if (s == null || s.trim().isEmpty()) return null;
        String[] parts = s.split(":");
        if (parts.length != 3) {
            plugin.getLogger().warning("Invalid coordinate string: '" + s + "' (expected 'x:y:z')");
            return null;
        }
        try {
            return new Location(
                    world,
                    Integer.parseInt(parts[0].trim()),
                    Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim())
            );
        } catch (NumberFormatException e) {
            plugin.getLogger().warning("Invalid coordinates in string: '" + s + "'");
            return null;
        }
    }
}