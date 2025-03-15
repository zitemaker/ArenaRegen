package DiamondPlugin.untitled2;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;

import java.util.HashMap;
import java.util.Map;

public class RegionData {

    private Map<Location, BlockData> blockDataMap = new HashMap<>();
    private Map<Location, EntityType> entityMap = new HashMap<>();
    private Location spawnLocation;

    public void addBlock(Location location, BlockData blockData) {
        blockDataMap.put(location, blockData);
    }

    public void addEntity(Location location, EntityType entityType) {
        entityMap.put(location, entityType);
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

    public void setSpawnLocation(Location spawnLocation) {
        this.spawnLocation = spawnLocation;
    }

    // Serialize RegionData to a ConfigurationSection
    public void serialize(ConfigurationSection section) {
        // Save spawn location
        if (spawnLocation != null) {
            section.set("spawn", spawnLocation.serialize());
        }

        // Save block data
        ConfigurationSection blocksSection = section.createSection("blocks");
        for (Map.Entry<Location, BlockData> entry : blockDataMap.entrySet()) {
            String key = entry.getKey().getBlockX() + "," + entry.getKey().getBlockY() + "," + entry.getKey().getBlockZ();
            blocksSection.set(key, entry.getValue().getAsString());
        }

        // Save entity data (if needed)
        if (!entityMap.isEmpty()) {
            ConfigurationSection entitiesSection = section.createSection("entities");
            for (Map.Entry<Location, EntityType> entry : entityMap.entrySet()) {
                String key = entry.getKey().getBlockX() + "," + entry.getKey().getBlockY() + "," + entry.getKey().getBlockZ();
                entitiesSection.set(key, entry.getValue().name());
            }
        }
    }

    // Deserialize RegionData from a ConfigurationSection
    public static RegionData deserialize(ConfigurationSection section, World defaultWorld) {
        RegionData regionData = new RegionData();

        // Load spawn location
        if (section.contains("spawn")) {
            Map<String, Object> spawnData = section.getConfigurationSection("spawn").getValues(false);
            // Ensure the world is set correctly
            if (!spawnData.containsKey("world")) {
                spawnData.put("world", defaultWorld.getName()); // Use the default world name
            }
            regionData.setSpawnLocation(Location.deserialize(spawnData));
        }

        // Load block data
        ConfigurationSection blocksSection = section.getConfigurationSection("blocks");
        if (blocksSection != null) {
            for (String key : blocksSection.getKeys(false)) {
                try {
                    String[] coords = key.split(",");
                    int x = Integer.parseInt(coords[0]);
                    int y = Integer.parseInt(coords[1]);
                    int z = Integer.parseInt(coords[2]);
                    Location location = new Location(defaultWorld, x, y, z); // Use the default world
                    BlockData blockData = Bukkit.createBlockData(blocksSection.getString(key));
                    regionData.addBlock(location, blockData);
                } catch (Exception e) {
                    Bukkit.getLogger().warning("Failed to load block data at " + key + ": " + e.getMessage());
                }
            }
        }

        // Load entity data (if needed)
        ConfigurationSection entitiesSection = section.getConfigurationSection("entities");
        if (entitiesSection != null) {
            for (String key : entitiesSection.getKeys(false)) {
                try {
                    String[] coords = key.split(",");
                    int x = Integer.parseInt(coords[0]);
                    int y = Integer.parseInt(coords[1]);
                    int z = Integer.parseInt(coords[2]);
                    Location location = new Location(defaultWorld, x, y, z); // Use the default world
                    EntityType entityType = EntityType.valueOf(entitiesSection.getString(key));
                    regionData.addEntity(location, entityType);
                } catch (Exception e) {
                    Bukkit.getLogger().warning("Failed to load entity data at " + key + ": " + e.getMessage());
                }
            }
        }

        return regionData;
    }
}