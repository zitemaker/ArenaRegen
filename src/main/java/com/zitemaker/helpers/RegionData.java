package com.zitemaker.helpers;

import com.zitemaker.ArenaRegen;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;

import java.util.*;

public class RegionData {

    private final ArenaRegen plugin;
    public final Map<String, Map<Location, BlockData>> sectionedBlockData = new HashMap<>();
    private final Set<String> blockTypes = new HashSet<>();
    public final Map<Location, EntityType> entityMap = new HashMap<>();

    private String creator;
    private long creationDate;
    public String worldName;
    private String minecraftVersion;
    private int width, height, depth;
    private int sectionCount;
    private final Map<String, Map<Location, BlockData>> sectionedBlocks = new HashMap<>();
    private Location spawnLocation;

    public RegionData(ArenaRegen plugin) {
        this.plugin = plugin;
    }

    public void setSpawnLocation(Location location) {
        this.spawnLocation = location.clone();
    }

    public Location getSpawnLocation(){
        return spawnLocation != null ? spawnLocation.clone() : null;
    }

    public void addBlockToSection(String section, Location location, BlockData blockData) {
        sectionedBlockData.computeIfAbsent(section, k -> new HashMap<>()).put(location, blockData);
        blockTypes.add(blockData.getAsString());
    }

    public void setMetadata(String creator, long creationDate, String world, String version, int width, int height, int depth, int sectionCount) {
        this.creator = creator;
        this.creationDate = creationDate;
        this.worldName = world;
        this.minecraftVersion = version;
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.sectionCount = sectionCount;
    }

    public void saveToConfig(FileConfiguration config, String path) {
        config.set(path + ".creator", creator);
        config.set(path + ".creationDate", creationDate);
        config.set(path + ".world", worldName);
        config.set(path + ".minecraftVersion", minecraftVersion);
        config.set(path + ".dimensions", width + ":" + height + ":" + depth);
        config.set(path + ".sectionCount", sectionCount);


        if (spawnLocation != null) {
            config.set(path + ".spawn.x", spawnLocation.getX());
            config.set(path + ".spawn.y", spawnLocation.getY());
            config.set(path + ".spawn.z", spawnLocation.getZ());
            config.set(path + ".spawn.yaw", spawnLocation.getYaw());
            config.set(path + ".spawn.pitch", spawnLocation.getPitch());
        }

        List<String> blockTypeList = new ArrayList<>(blockTypes);
        config.set(path + ".blockTypes", blockTypeList);

        for (Map.Entry<String, Map<Location, BlockData>> sectionEntry : sectionedBlockData.entrySet()) {
            String section = sectionEntry.getKey();
            for (Map.Entry<Location, BlockData> blockEntry : sectionEntry.getValue().entrySet()) {
                String key = coordsToString(blockEntry.getKey());
                config.set(path + ".sections." + section + "." + key, blockEntry.getValue().getAsString());
            }
        }
    }

    public void loadFromConfig(FileConfiguration config, String path) {
        this.creator = config.getString(path + ".creator", "Unknown");
        this.creationDate = config.getLong(path + ".creationDate", System.currentTimeMillis());
        this.worldName = config.getString(path + ".world", "Unknown");
        this.minecraftVersion = config.getString(path + ".minecraftVersion", "Unknown");

        String dimensions = config.getString(path + ".dimensions", "0:0:0");
        String[] dimParts = dimensions.split(":");
        if (dimParts.length == 3) {
            this.width = Integer.parseInt(dimParts[0]);
            this.height = Integer.parseInt(dimParts[1]);
            this.depth = Integer.parseInt(dimParts[2]);
        }

        this.sectionCount = config.getInt(path + ".sectionCount", 0);
        blockTypes.addAll(config.getStringList(path + ".blockTypes"));

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("World '" + worldName + "' does not exist! Skipping region loading.");
            return;
        }

        if (config.contains(path + ".spawn")) {
            double x = config.getDouble(path + ".spawn.x");
            double y = config.getDouble(path + ".spawn.y");
            double z = config.getDouble(path + ".spawn.z");
            float yaw = (float) config.getDouble(path + ".spawn.yaw");
            float pitch = (float) config.getDouble(path + ".spawn.pitch");
            this.spawnLocation = new Location(world, x, y, z, yaw, pitch);
        }

        if (config.contains(path + ".sections")) {
            for (String section : config.getConfigurationSection(path + ".sections").getKeys(false)) {
                Map<Location, BlockData> sectionData = new HashMap<>();
                for (String key : config.getConfigurationSection(path + ".sections." + section).getKeys(false)) {
                    Location loc = stringToCoords(key, world);
                    if (loc == null) continue;
                    String blockDataStr = config.getString(path + ".sections." + section + "." + key);
                    if (blockDataStr == null) continue;
                    BlockData blockData = Bukkit.createBlockData(blockDataStr);
                    sectionData.put(loc, blockData);
                }
                sectionedBlockData.put(section, sectionData);
            }
        }
    }

    private String coordsToString(Location loc) {
        return loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }

    private Location stringToCoords(String s, World world) {
        if (s == null || s.isEmpty()) return null;
        String[] parts = s.split(":");
        if (parts.length != 3) return null;
        try {
            return new Location(
                    world,
                    Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2])
            );
        } catch (NumberFormatException e) {
            return null;
        }
    }
    public void clearRegion(String regionName) {
        sectionedBlockData.clear();
        entityMap.clear();
        sectionedBlocks.clear();
        sectionCount = 0;

        plugin.getRegisteredRegions().remove(regionName);
        plugin.markRegionDirty(regionName);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, plugin::saveRegions);
    }




    public Map<String, Map<Location, BlockData>> getSectionedBlockData() {
        return sectionedBlocks;
    }


    public void setSectionCount(int count) {
        this.sectionCount = count;
    }

    // getters
    public String getCreator() { return creator; }
    public long getCreationDate() { return creationDate; }
    public String getWorldName() { return worldName; }
    public String getMinecraftVersion() { return minecraftVersion; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getDepth() { return depth; }
    public int getSectionCount() { return sectionCount > 0 ? sectionCount : sectionedBlockData.size(); }
    public Set<String> getBlockTypes() { return blockTypes; }
}
