package com.zitemaker.helpers;

import com.zitemaker.ArenaRegen;
import org.bukkit.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class RegionData {
    private final ArenaRegen plugin;
    public final Map<String, Map<Location, BlockData>> sectionedBlockData = new HashMap<>();
    private final Map<Location, Map<String, Object>> entityDataMap = new HashMap<>();
    private final Map<Location, BlockData> modifiedBlocks = new HashMap<>();

    private String creator;
    private long creationDate;
    public String worldName;
    private String minecraftVersion;
    private int minX, minY, minZ;
    private int width, height, depth;
    private Location spawnLocation;
    private boolean isBlockDataLoaded = false;
    private File datcFile;
    private boolean loadFailed = false;
    private boolean isLoading = false;

    public RegionData(ArenaRegen plugin) {
        this.plugin = plugin;
    }

    public void setDatcFile(File datcFile) {
        this.datcFile = datcFile;
    }

    public void setSpawnLocation(Location location) {
        this.spawnLocation = location != null ? location.clone() : null;
    }

    public Location getSpawnLocation() {
        return spawnLocation != null ? spawnLocation.clone() : null;
    }

    public void addBlockToSection(String section, Location location, BlockData blockData) {
        sectionedBlockData.computeIfAbsent(section, k -> new HashMap<>()).put(location, blockData);
    }

    public void addSection(String sectionName, Map<Location, BlockData> blocks) {
        sectionedBlockData.put(sectionName, blocks);
        for (Map.Entry<Location, BlockData> entry : blocks.entrySet()) {
            Location location = entry.getKey();
            BlockData blockData = entry.getValue();
            if (blockData == null) {
                plugin.getLogger().info("Block data in section " + sectionName + " at " + location + " is null, replacing with air.");
                blockData = Bukkit.createBlockData(Material.AIR);
                blocks.put(location, blockData);
            }
        }
    }

    public void setMetadata(String creator, long creationDate, String world, String version, int minX, int minY, int minZ, int width, int height, int depth) {
        this.creator = creator;
        this.creationDate = creationDate;
        this.worldName = world;
        this.minecraftVersion = version;
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.width = width;
        this.height = height;
        this.depth = depth;
    }

    public void markBlockModified(Location location, BlockData newBlockData) {
        Map<Location, BlockData> allBlocks = getAllBlocks();

        World arenaWorld = Bukkit.getWorld(worldName);
        if (arenaWorld == null) {
            return;
        }
        Location normalizedLoc = new Location(arenaWorld, location.getBlockX(), location.getBlockY(), location.getBlockZ());

        BlockData original = allBlocks.get(normalizedLoc);
        if (original != null && !original.getMaterial().equals(newBlockData.getMaterial())) {
            modifiedBlocks.put(normalizedLoc, original);
        }
    }

    public void clearModifiedBlocks() {
        modifiedBlocks.clear();
    }

    public Map<Location, BlockData> getModifiedBlocks() {
        try {
            ensureBlockDataLoaded();
        } catch (IOException e) {
            plugin.getLogger().info("[ArenaRegen] Failed to load block data for modified blocks: " + e.getMessage());
        }
        return new HashMap<>(modifiedBlocks);
    }

    public void addEntity(Location location, Map<String, Object> serializedEntity) {
        World arenaWorld = Bukkit.getWorld(worldName);
        if (arenaWorld == null) {
            return;
        }
        Location normalizedLoc = new Location(arenaWorld, location.getX(), location.getY(), location.getZ());
        entityDataMap.put(normalizedLoc, serializedEntity);
    }

    public Map<Location, Map<String, Object>> getEntityDataMap() {
        try {
            ensureBlockDataLoaded();
        } catch (IOException e) {
            plugin.getLogger().info("[ArenaRegen] Failed to load block data for entity map: " + e.getMessage());
        }
        return new HashMap<>(entityDataMap);
    }

    public void clearEntities() {
        entityDataMap.clear();
    }


    private String coordsToString(Location loc) {
        return loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }


    public void clearRegion(String regionName) {
        sectionedBlockData.clear();
        entityDataMap.clear();
        modifiedBlocks.clear();

        plugin.getRegisteredRegions().remove(regionName);
        plugin.getPendingDeletions().remove(regionName);
        plugin.getPendingRegenerations().remove(regionName);
        plugin.getDirtyRegions().remove(regionName);

        if (datcFile != null && datcFile.exists()) {
            if (datcFile.delete()) {
                plugin.getLogger().info("[ArenaRegen] Successfully deleted arena file: " + datcFile.getPath());
            } else {
                plugin.getLogger().info(ChatColor.RED + "[ArenaRegen] Failed to delete arena file: " + datcFile.getPath());
            }
        } else {
            plugin.getLogger().info(ChatColor.YELLOW + "[ArenaRegen] No .datc file found for arena '" + regionName + "' to delete.");
        }

        plugin.getLogger().info("[ArenaRegen] Arena '" + regionName + "' has been fully removed from memory and disk.");
    }

    public void saveToDatc(File datcFile) throws IOException {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();

        byteStream.write(("1," + creator + "," + creationDate + "," + worldName + "," +
                minecraftVersion + "," + minX + "," + minY + "," + minZ + "," +
                width + "," + height + "," + depth).getBytes(StandardCharsets.US_ASCII));
        if (spawnLocation != null) {
            byteStream.write(("," + spawnLocation.getX() + "," + spawnLocation.getY() + "," + spawnLocation.getZ() +
                    "," + spawnLocation.getYaw() + "," + spawnLocation.getPitch()).getBytes(StandardCharsets.US_ASCII));
        }
        byteStream.write('\n');

        ByteBuffer sectionBuffer = ByteBuffer.allocate(4);
        sectionBuffer.putInt(sectionedBlockData.size());
        byteStream.write(sectionBuffer.array());

        for (Map.Entry<String, Map<Location, BlockData>> entry : sectionedBlockData.entrySet()) {
            String sectionName = entry.getKey();
            Map<Location, BlockData> blocks = entry.getValue();

            byte[] sectionNameBytes = sectionName.getBytes(StandardCharsets.UTF_8);
            byteStream.write(ByteBuffer.allocate(4).putInt(sectionNameBytes.length).array());
            byteStream.write(sectionNameBytes);

            byteStream.write(ByteBuffer.allocate(4).putInt(blocks.size()).array());

            for (Map.Entry<Location, BlockData> blockEntry : blocks.entrySet()) {
                Location loc = blockEntry.getKey();
                BlockData blockData = blockEntry.getValue();
                String blockDataStr = blockData != null ? blockData.getAsString() : "minecraft:air";

                byteStream.write(ByteBuffer.allocate(12)
                        .putInt(loc.getBlockX())
                        .putInt(loc.getBlockY())
                        .putInt(loc.getBlockZ())
                        .array());

                byte[] blockDataBytes = blockDataStr.getBytes(StandardCharsets.UTF_8);
                byteStream.write(ByteBuffer.allocate(4).putInt(blockDataBytes.length).array());
                byteStream.write(blockDataBytes);
            }
        }

        byteStream.write(ByteBuffer.allocate(4).putInt(entityDataMap.size()).array());
        for (Map.Entry<Location, Map<String, Object>> entry : entityDataMap.entrySet()) {
            Location loc = entry.getKey();
            Map<String, Object> serializedEntity = entry.getValue();

            byteStream.write(ByteBuffer.allocate(24)
                    .putDouble(loc.getX())
                    .putDouble(loc.getY())
                    .putDouble(loc.getZ())
                    .array());
            ByteArrayOutputStream entityStream = new ByteArrayOutputStream();
            try (ObjectOutputStream oos = new ObjectOutputStream(entityStream)) {
                oos.writeObject(serializedEntity);
            }
            byte[] entityDataBytes = entityStream.toByteArray();
            byteStream.write(ByteBuffer.allocate(4).putInt(entityDataBytes.length).array());
            byteStream.write(entityDataBytes);
        }

        byteStream.write(ByteBuffer.allocate(4).putInt(modifiedBlocks.size()).array());
        for (Map.Entry<Location, BlockData> entry : modifiedBlocks.entrySet()) {
            Location loc = entry.getKey();
            BlockData blockData = entry.getValue();
            String blockDataStr = blockData != null ? blockData.getAsString() : "minecraft:air";

            byteStream.write(ByteBuffer.allocate(12)
                    .putInt(loc.getBlockX())
                    .putInt(loc.getBlockY())
                    .putInt(loc.getBlockZ())
                    .array());

            byte[] blockDataBytes = blockDataStr.getBytes(StandardCharsets.UTF_8);
            byteStream.write(ByteBuffer.allocate(4).putInt(blockDataBytes.length).array());
            byteStream.write(blockDataBytes);
        }

        byte[] rawBytes = byteStream.toByteArray();
        try (FileOutputStream fos = new FileOutputStream(datcFile);
             GZIPOutputStream gzip = new GZIPOutputStream(fos)) {
            gzip.write(rawBytes);
        }

        plugin.getLogger().info("[ArenaRegen] Saved RegionData to " + datcFile.getName() + ": " +
                sectionedBlockData.size() + " sections, " + getAllBlocks().size() + " total blocks, " +
                entityDataMap.size() + " entities, " + modifiedBlocks.size() + " modified blocks.");
    }

    public void loadFromDatc(File datcFile) throws IOException {
        this.datcFile = datcFile;

        byte[] rawBytes;
        try (FileInputStream fis = new FileInputStream(datcFile);
             GZIPInputStream gzip = new GZIPInputStream(fis)) {
            rawBytes = gzip.readAllBytes();
        }

        ByteArrayInputStream byteStream = new ByteArrayInputStream(rawBytes);
        DataInputStream dataStream = new DataInputStream(byteStream);

        StringBuilder headerBuilder = new StringBuilder();
        int b;
        while ((b = dataStream.read()) != -1 && b != '\n') {
            headerBuilder.append((char) b);
        }
        if (b == -1) throw new IOException("Invalid .datc file: No header section found");
        String header = headerBuilder.toString();
        String[] headerParts = header.split(",");
        if (headerParts.length < 11) throw new IOException("Invalid .datc file: Incomplete header");
        creator = headerParts[1];
        creationDate = Long.parseLong(headerParts[2]);
        worldName = headerParts[3];
        minecraftVersion = headerParts[4];
        minX = Integer.parseInt(headerParts[5]);
        minY = Integer.parseInt(headerParts[6]);
        minZ = Integer.parseInt(headerParts[7]);
        width = Integer.parseInt(headerParts[8]);
        height = Integer.parseInt(headerParts[9]);
        depth = Integer.parseInt(headerParts[10]);

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().info("World '" + worldName + "' not found for region in " + datcFile.getName() + ". Deferring block data loading.");
            isBlockDataLoaded = false;
            spawnLocation = null;
            return;
        }

        if (headerParts.length > 11) {
            spawnLocation = new Location(world, Double.parseDouble(headerParts[11]), Double.parseDouble(headerParts[12]),
                    Double.parseDouble(headerParts[13]), Float.parseFloat(headerParts[14]), Float.parseFloat(headerParts[15]));
        }

        int sectionCount = dataStream.readInt();
        sectionedBlockData.clear();

        for (int i = 0; i < sectionCount; i++) {
            int sectionNameLength = dataStream.readInt();
            byte[] sectionNameBytes = new byte[sectionNameLength];
            dataStream.readFully(sectionNameBytes);
            String sectionName = new String(sectionNameBytes, StandardCharsets.UTF_8);

            int blockCount = dataStream.readInt();
            Map<Location, BlockData> blocks = new HashMap<>();

            for (int j = 0; j < blockCount; j++) {
                int x = dataStream.readInt();
                int y = dataStream.readInt();
                int z = dataStream.readInt();
                int blockDataLength = dataStream.readInt();
                byte[] blockDataBytes = new byte[blockDataLength];
                dataStream.readFully(blockDataBytes);
                String blockDataStr = new String(blockDataBytes, StandardCharsets.UTF_8);

                Location loc = new Location(world, x, y, z);
                try {
                    BlockData blockData = Bukkit.createBlockData(blockDataStr);
                    blocks.put(loc, blockData);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().info("Invalid block data '" + blockDataStr + "' at " + loc + " in section " + sectionName + ": " + e.getMessage() + ", replacing with air.");
                    blocks.put(loc, Bukkit.createBlockData(Material.AIR));
                }
            }
            sectionedBlockData.put(sectionName, blocks);
        }
        int entityCount = dataStream.readInt();
        entityDataMap.clear();
        for (int i = 0; i < entityCount; i++) {
            double x = dataStream.readDouble();
            double y = dataStream.readDouble();
            double z = dataStream.readDouble();
            int entityDataLength = dataStream.readInt();
            byte[] entityDataBytes = new byte[entityDataLength];
            dataStream.readFully(entityDataBytes);

            Location loc = new Location(world, x, y, z);
            try {
                ByteArrayInputStream entityStream = new ByteArrayInputStream(entityDataBytes);
                try (ObjectInputStream ois = new ObjectInputStream(entityStream)) {
                    Map<String, Object> serializedEntity = (Map<String, Object>) ois.readObject();
                    entityDataMap.put(loc, serializedEntity);
                }
            } catch (Exception e) {
                plugin.getLogger().info("Failed to deserialize entity data at " + loc + ": " + e.getMessage() + ", skipping.");
            }
        }

        int modifiedCount = dataStream.readInt();
        modifiedBlocks.clear();
        for (int i = 0; i < modifiedCount; i++) {
            int x = dataStream.readInt();
            int y = dataStream.readInt();
            int z = dataStream.readInt();
            int blockDataLength = dataStream.readInt();
            byte[] blockDataBytes = new byte[blockDataLength];
            dataStream.readFully(blockDataBytes);
            String blockDataStr = new String(blockDataBytes, StandardCharsets.UTF_8);

            Location loc = new Location(world, x, y, z);
            try {
                BlockData blockData = Bukkit.createBlockData(blockDataStr);
                modifiedBlocks.put(loc, blockData);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().info("Invalid block data '" + blockDataStr + "' for modified block at " + loc + ": " + e.getMessage() + ", skipping.");
            }
        }

        isBlockDataLoaded = true;
        plugin.getLogger().info("[ArenaRegen] Loaded RegionData for file " + datcFile.getName() + ": " +
                sectionedBlockData.size() + " sections, " + getAllBlocks().size() + " total blocks, " +
                entityDataMap.size() + " entities, " + modifiedBlocks.size() + " modified blocks.");
    }

    public void ensureBlockDataLoaded() throws IOException {
        if (isLoading) {
            throw new IOException("Recursive loading detected for region in " + (datcFile != null ? datcFile.getName() : "unknown file"));
        }

        if (!isBlockDataLoaded && datcFile != null) {
            if (loadFailed) {
                throw new IOException("Block data loading previously failed for region in " + datcFile.getName());
            }

            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                loadFailed = true;
                throw new IOException("World '" + worldName + "' not found for region in " + datcFile.getName());
            }

            try {
                isLoading = true;
                loadFromDatc(datcFile);
            } finally {
                isLoading = false;
            }
        }
    }

    public Map<String, Map<Location, BlockData>> getSectionedBlockData() {
        try {
            ensureBlockDataLoaded();
        } catch (IOException e) {
            plugin.getLogger().info("[ArenaRegen] Failed to load block data for sectionedBlockData: " + e.getMessage());
        }
        return sectionedBlockData;
    }

    public Map<Location, BlockData> getAllBlocks() {
        try {
            ensureBlockDataLoaded();
        } catch (IOException e) {
            plugin.getLogger().info("[ArenaRegen] Failed to load block data for all blocks: " + e.getMessage());
        }
        Map<Location, BlockData> allBlocks = new HashMap<>();
        for (Map.Entry<String, Map<Location, BlockData>> entry : sectionedBlockData.entrySet()) {
            allBlocks.putAll(entry.getValue());
        }
        return allBlocks;
    }

    // metadata getters
    public String getCreator() {
        return creator;
    }

    public long getCreationDate() {
        return creationDate;
    }

    public String getWorldName() {
        return worldName;
    }

    public String getMinecraftVersion() {
        return minecraftVersion;
    }

    public int getMinX() {
        return minX;
    }

    public int getMinY() {
        return minY;
    }

    public int getMinZ() {
        return minZ;
    }

    public int getMaxX() {
        return minX + width - 1;
    }

    public int getMaxY() {
        return minY + height - 1;
    }

    public int getMaxZ() {
        return minZ + depth - 1;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getDepth() {
        return depth;
    }
    public File getDatcFile() {
        return datcFile;
    }
}