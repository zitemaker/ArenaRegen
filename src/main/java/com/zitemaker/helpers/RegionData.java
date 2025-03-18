package com.zitemaker.helpers;

import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.EntityType;

import java.util.HashMap;
import java.util.Map;

public class RegionData {

    private final Map<Location, BlockData> blockDataMap = new HashMap<>();
    private final Map<Location, EntityType> entityMap = new HashMap<>();
    private Location spawnLocation;

    public void addBlock(Location location, BlockData blockData) {
        blockDataMap.put(location, blockData);
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
}