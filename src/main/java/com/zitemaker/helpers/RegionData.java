package com.zitemaker.helpers;

import com.zitemaker.ArenaRegen;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class RegionData {
    private final ArenaRegen plugin;
    public final Map<String, Map<Location, BlockData>> sectionedBlockData = new HashMap<>();
    private final List<String> blockTypes = new ArrayList<>();
    public final Map<Location, EntityType> entityMap = new HashMap<>();

    private String creator;
    private long creationDate;
    public String worldName;
    private String minecraftVersion;
    private int width, height, depth;
    private Location spawnLocation;

    public static final byte SECTION_SPLIT = (byte) '\n';
    public static final byte KEY_SPLIT = (byte) ',';

    public RegionData(ArenaRegen plugin) {
        this.plugin = plugin;
    }

    public void setSpawnLocation(Location location) {
        this.spawnLocation = location.clone();
    }

    public Location getSpawnLocation() {
        return spawnLocation != null ? spawnLocation.clone() : null;
    }

    public void addBlockToSection(String section, Location location, BlockData blockData) {
        sectionedBlockData.computeIfAbsent(section, k -> new HashMap<>()).put(location, blockData);
        String blockType = blockData.getAsString();
        if (!blockTypes.contains(blockType)) blockTypes.add(blockType);
    }

    public void addSection(String sectionName, Map<Location, BlockData> blocks) {
        sectionedBlockData.put(sectionName, blocks);
        for (BlockData blockData : blocks.values()) {
            String blockType = blockData.getAsString();
            if (!blockTypes.contains(blockType)) blockTypes.add(blockType);
        }
    }

    public void setMetadata(String creator, long creationDate, String world, String version, int width, int height, int depth) {
        this.creator = creator;
        this.creationDate = creationDate;
        this.worldName = world;
        this.minecraftVersion = version;
        this.width = width;
        this.height = height;
        this.depth = depth;
    }

    public void saveToConfig(FileConfiguration config, String path) {
        config.set(path + ".creator", creator);
        config.set(path + ".creationDate", creationDate);
        config.set(path + ".world", worldName);
        config.set(path + ".minecraftVersion", minecraftVersion);
        config.set(path + ".dimensions", width + ":" + height + ":" + depth);

        if (spawnLocation != null) {
            config.set(path + ".spawn.x", spawnLocation.getX());
            config.set(path + ".spawn.y", spawnLocation.getY());
            config.set(path + ".spawn.z", spawnLocation.getZ());
            config.set(path + ".spawn.yaw", spawnLocation.getYaw());
            config.set(path + ".spawn.pitch", spawnLocation.getPitch());
        }

        config.set(path + ".blockTypes", blockTypes);

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
        blockTypes.clear();

        plugin.getRegisteredRegions().remove(regionName);
        plugin.markRegionDirty(regionName);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, plugin::saveRegions);
    }

    public void saveToDatc(File datcFile) throws IOException {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        byteStream.write(("1," + creator + "," + creationDate + "," + worldName + "," +
                minecraftVersion + "," + width + "," + height + "," + depth).getBytes(StandardCharsets.US_ASCII));
        if (spawnLocation != null) {
            byteStream.write(("," + spawnLocation.getX() + "," + spawnLocation.getY() + "," + spawnLocation.getZ() +
                    "," + spawnLocation.getYaw() + "," + spawnLocation.getPitch()).getBytes(StandardCharsets.US_ASCII));
        }
        byteStream.write(SECTION_SPLIT);

        ByteArrayOutputStream keyStream = new ByteArrayOutputStream();
        for (String blockType : blockTypes) {
            keyStream.write(blockType.getBytes(StandardCharsets.US_ASCII));
            keyStream.write(KEY_SPLIT);
        }
        byte[] keyBytes = keyStream.toByteArray();
        if (keyBytes.length > 0) {
            keyBytes = Arrays.copyOf(keyBytes, keyBytes.length - 1);
        }
        byteStream.write(keyBytes);
        byteStream.write(SECTION_SPLIT);

        ByteBuffer sectionBuffer = ByteBuffer.allocate(2 + (sectionedBlockData.size() * 4));
        sectionBuffer.putShort((short) sectionedBlockData.size());
        for (String sectionName : sectionedBlockData.keySet()) {
            sectionBuffer.putInt(sectionName.hashCode());
        }
        byteStream.write(sectionBuffer.array());

        for (Map.Entry<String, Map<Location, BlockData>> entry : sectionedBlockData.entrySet()) {
            String sectionName = entry.getKey();
            Map<Location, BlockData> blocks = entry.getValue();
            byteStream.write(sectionName.getBytes(StandardCharsets.UTF_8));
            byteStream.write(SECTION_SPLIT);
            ByteBuffer blockBuffer = ByteBuffer.allocate(4 + (blocks.size() * (12 + 4)));
            blockBuffer.putInt(blocks.size());
            for (Map.Entry<Location, BlockData> blockEntry : blocks.entrySet()) {
                Location loc = blockEntry.getKey();
                blockBuffer.putInt(loc.getBlockX());
                blockBuffer.putInt(loc.getBlockY());
                blockBuffer.putInt(loc.getBlockZ());
                blockBuffer.putInt(blockTypes.indexOf(blockEntry.getValue().getAsString()));
            }
            byteStream.write(blockBuffer.array());
        }

        byte[] totalBytes = byteStream.toByteArray();
        try (FileOutputStream fos = new FileOutputStream(datcFile);
             GZIPOutputStream gzip = new GZIPOutputStream(fos)) {
            gzip.write(totalBytes);
        }
    }

    public void loadFromDatc(File datcFile) throws IOException {
        byte[] readBytes;
        try (FileInputStream fis = new FileInputStream(datcFile);
             GZIPInputStream gzip = new GZIPInputStream(fis)) {
            readBytes = gzip.readAllBytes();
        }

        ByteBuffer buffer = ByteBuffer.wrap(readBytes);
        int firstSplit = -1;
        for (int i = 0; i < readBytes.length; i++) {
            if (readBytes[i] == SECTION_SPLIT) {
                firstSplit = i;
                break;
            }
        }
        String header = new String(Arrays.copyOfRange(readBytes, 0, firstSplit), StandardCharsets.US_ASCII);
        String[] headerParts = header.split(",");
        creator = headerParts[1];
        creationDate = Long.parseLong(headerParts[2]);
        worldName = headerParts[3];
        minecraftVersion = headerParts[4];
        width = Integer.parseInt(headerParts[5]);
        height = Integer.parseInt(headerParts[6]);
        depth = Integer.parseInt(headerParts[7]);
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("World '" + worldName + "' not found!");
            return;
        }
        if (headerParts.length > 8) {
            spawnLocation = new Location(world, Double.parseDouble(headerParts[8]), Double.parseDouble(headerParts[9]),
                    Double.parseDouble(headerParts[10]), Float.parseFloat(headerParts[11]), Float.parseFloat(headerParts[12]));
        }

        buffer.position(firstSplit + 1);
        int keySplit = -1;
        for (int i = firstSplit + 1; i < readBytes.length; i++) {
            if (readBytes[i] == SECTION_SPLIT) {
                keySplit = i;
                break;
            }
        }
        byte[] keyBytes = Arrays.copyOfRange(readBytes, firstSplit + 1, keySplit);
        blockTypes.clear();
        if (keyBytes.length > 0) {
            int start = 0;
            for (int i = 0; i < keyBytes.length; i++) {
                if (keyBytes[i] == KEY_SPLIT) {
                    blockTypes.add(new String(Arrays.copyOfRange(keyBytes, start, i), StandardCharsets.US_ASCII));
                    start = i + 1;
                }
            }
            if (start < keyBytes.length) {
                blockTypes.add(new String(Arrays.copyOfRange(keyBytes, start, keyBytes.length), StandardCharsets.US_ASCII));
            }
        }

        buffer.position(keySplit + 1);
        int sectionCount = buffer.getShort();
        int[] sectionIds = new int[sectionCount];
        for (int i = 0; i < sectionCount; i++) {
            sectionIds[i] = buffer.getInt();
        }

        sectionedBlockData.clear();
        for (int i = 0; i < sectionCount; i++) {
            int nameEnd = -1;
            for (int j = buffer.position(); j < readBytes.length; j++) {
                if (readBytes[j] == SECTION_SPLIT) {
                    nameEnd = j;
                    break;
                }
            }
            String sectionName = new String(Arrays.copyOfRange(readBytes, buffer.position(), nameEnd), StandardCharsets.UTF_8);
            buffer.position(nameEnd + 1);
            int blockCount = buffer.getInt();
            Map<Location, BlockData> blocks = new HashMap<>();
            for (int j = 0; j < blockCount; j++) {
                int x = buffer.getInt();
                int y = buffer.getInt();
                int z = buffer.getInt();
                int typeIndex = buffer.getInt();
                Location loc = new Location(world, x, y, z);
                blocks.put(loc, Bukkit.createBlockData(blockTypes.get(typeIndex)));
            }
            sectionedBlockData.put(sectionName, blocks);
        }
    }

    public Map<String, Map<Location, BlockData>> getSectionedBlockData() {
        return sectionedBlockData;
    }

    public String getCreator() { return creator; }
    public long getCreationDate() { return creationDate; }
    public String getWorldName() { return worldName; }
    public String getMinecraftVersion() { return minecraftVersion; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getDepth() { return depth; }
    public List<String> getBlockTypes() { return blockTypes; }
}