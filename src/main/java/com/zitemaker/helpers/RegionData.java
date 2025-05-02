package com.zitemaker.helpers;

import com.zitemaker.ArenaRegen;
import org.bukkit.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class RegionData {
    private static final Logger LOGGER = Bukkit.getLogger();
    private static final String FILE_FORMAT_VERSION = "3";
    private static final int BUFFER_SIZE = 8192;
    private static final int GZIP_COMPRESSION_LEVEL = 6;
    private static final byte[] GZIP_MAGIC = new byte[] { (byte) 0x1F, (byte) 0x8B };

    private final ArenaRegen plugin;
    public final Map<String, Map<Location, BlockData>> sectionedBlockData = new ConcurrentHashMap<>();
    private final Map<Location, Map<String, Object>> entityDataMap = new ConcurrentHashMap<>();
    private final Map<Location, BlockData> modifiedBlocks = new ConcurrentHashMap<>();

    private String creator;
    private long creationDate;
    public String worldName;
    private String minecraftVersion;
    private String fileFormatVersion = FILE_FORMAT_VERSION;
    private int minX, minY, minZ;
    private int width, height, depth;
    private Location spawnLocation;
    private boolean locked = false;
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
        sectionedBlockData.computeIfAbsent(section, k -> new ConcurrentHashMap<>()).put(location, blockData);
    }

    public void addSection(String sectionName, Map<Location, BlockData> blocks) {
        Map<Location, BlockData> sectionBlocks = new ConcurrentHashMap<>();
        for (Map.Entry<Location, BlockData> entry : blocks.entrySet()) {
            Location location = entry.getKey();
            BlockData blockData = entry.getValue();
            if (blockData == null) {
                LOGGER.warning("[ArenaRegen] Block data in section " + sectionName + " at " + location + " is null, replacing with air.");
                blockData = Bukkit.createBlockData(Material.AIR);
            }
            sectionBlocks.put(location, blockData);
        }
        sectionedBlockData.put(sectionName, sectionBlocks);
    }

    public void setMetadata(String creator, long creationDate, String world, String version, int minX, int minY, int minZ, int width, int height, int depth) {
        if (world == null || world.trim().isEmpty()) {
            throw new IllegalArgumentException("World name cannot be null or empty");
        }
        if (width <= 0 || height <= 0 || depth <= 0) {
            throw new IllegalArgumentException("Region dimensions must be positive: width=" + width + ", height=" + height + ", depth=" + depth);
        }
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
            LOGGER.warning("[ArenaRegen] Failed to load block data for modified blocks: " + e.getMessage());
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
            LOGGER.warning("[ArenaRegen] Failed to load block data for entity map: " + e.getMessage());
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

        if (datcFile != null) {
            if (datcFile.exists()) {
                if (datcFile.delete()) {
                    LOGGER.info("[ArenaRegen] Successfully deleted arena file: " + datcFile.getPath());
                } else {
                    LOGGER.warning("[ArenaRegen] Failed to delete arena file: " + datcFile.getPath());
                }
            } else {
                LOGGER.warning("[ArenaRegen] No .datc file found for arena '" + regionName + "' to delete.");
            }

            File backupFile = new File(datcFile.getParent(), datcFile.getName() + ".bak");
            if (backupFile.exists()) {
                if (backupFile.delete()) {
                    LOGGER.info("[ArenaRegen] Successfully deleted backup file: " + backupFile.getPath());
                } else {
                    LOGGER.warning("[ArenaRegen] Failed to delete backup file: " + backupFile.getPath());
                }
            }
        } else {
            LOGGER.warning("[ArenaRegen] No .datc file found for arena '" + regionName + "' to delete.");
        }

        File schematicFile = new File(plugin.getDataFolder(), "arenas/" + regionName + ".schem");
        if (schematicFile.exists()) {
            if (schematicFile.delete()) {
                LOGGER.info("[ArenaRegen] Successfully deleted schematic file: " + schematicFile.getPath());
            } else {
                LOGGER.warning("[ArenaRegen] Failed to delete schematic file: " + schematicFile.getPath());
            }
        } else {
            LOGGER.warning("[ArenaRegen] No schematic file found for arena '" + regionName + "' to delete.");
        }

        LOGGER.info("[ArenaRegen] Arena '" + regionName + "' has been fully removed from memory and disk.");
    }

    public void saveToDatc(File datcFile) throws IOException {
        long startTime = System.currentTimeMillis();

        File backupFile = new File(datcFile.getParent(), datcFile.getName() + ".bak");
        if (datcFile.exists()) {
            if (!datcFile.renameTo(backupFile)) {
                LOGGER.warning("[ArenaRegen] Failed to create backup of " + datcFile.getName());
            }
        }

        try (FileOutputStream fos = new FileOutputStream(datcFile);
             BufferedOutputStream bos = new BufferedOutputStream(fos, BUFFER_SIZE);
             GZIPOutputStream gzip = new GZIPOutputStream(bos) {{ def.setLevel(GZIP_COMPRESSION_LEVEL); }};
             DataOutputStream dos = new DataOutputStream(gzip)) {

            String header = fileFormatVersion + "," + creator + "," + creationDate + "," + worldName + "," +
                    minecraftVersion + "," + minX + "," + minY + "," + minZ + "," +
                    width + "," + height + "," + depth;
            if (spawnLocation != null) {
                header += "," + spawnLocation.getX() + "," + spawnLocation.getY() + "," + spawnLocation.getZ() +
                        "," + spawnLocation.getYaw() + "," + spawnLocation.getPitch();
            } else {
                header += ",0,0,0,0,0";
            }
            header += "," + locked;
            dos.writeBytes(header);
            dos.writeByte('\n');

            writeSections(dos);
            writeEntities(dos);
            writeModifiedBlocks(dos);

        } catch (IOException e) {
            if (backupFile.exists()) {
                if (datcFile.exists()) datcFile.delete();
                backupFile.renameTo(datcFile);
            }
            throw e;
        }

        long timeTaken = System.currentTimeMillis() - startTime;
        long fileSize = datcFile.length();
        LOGGER.info("[ArenaRegen] Saved RegionData to " + datcFile.getName() + ": " +
                sectionedBlockData.size() + " sections, " + getAllBlocks().size() + " total blocks, " +
                entityDataMap.size() + " entities, " + modifiedBlocks.size() + " modified blocks. " +
                "File size: " + (fileSize / 1024) + " KB, Time: " + timeTaken + "ms");
    }

    private void writeSections(DataOutputStream dos) throws IOException {
        dos.writeInt(sectionedBlockData.size());
        for (Map.Entry<String, Map<Location, BlockData>> entry : sectionedBlockData.entrySet()) {
            String sectionName = entry.getKey();
            Map<Location, BlockData> blocks = entry.getValue();

            dos.writeUTF(sectionName);
            dos.writeInt(blocks.size());

            for (Map.Entry<Location, BlockData> blockEntry : blocks.entrySet()) {
                Location loc = blockEntry.getKey();
                BlockData blockData = blockEntry.getValue();
                String blockDataStr = blockData != null ? blockData.getAsString() : "minecraft:air";

                dos.writeInt(loc.getBlockX());
                dos.writeInt(loc.getBlockY());
                dos.writeInt(loc.getBlockZ());
                dos.writeUTF(blockDataStr);
            }
        }
    }

    private void writeEntities(DataOutputStream dos) throws IOException {
        dos.writeInt(entityDataMap.size());
        for (Map.Entry<Location, Map<String, Object>> entry : entityDataMap.entrySet()) {
            Location loc = entry.getKey();
            Map<String, Object> serializedEntity = entry.getValue();

            dos.writeDouble(loc.getX());
            dos.writeDouble(loc.getY());
            dos.writeDouble(loc.getZ());

            ByteArrayOutputStream entityStream = new ByteArrayOutputStream();
            try (ObjectOutputStream oos = new ObjectOutputStream(entityStream)) {
                oos.writeObject(serializedEntity);
            }
            byte[] entityDataBytes = entityStream.toByteArray();
            dos.writeInt(entityDataBytes.length);
            dos.write(entityDataBytes);
        }
    }

    private void writeModifiedBlocks(DataOutputStream dos) throws IOException {
        dos.writeInt(modifiedBlocks.size());
        for (Map.Entry<Location, BlockData> entry : modifiedBlocks.entrySet()) {
            Location loc = entry.getKey();
            BlockData blockData = entry.getValue();
            String blockDataStr = blockData != null ? blockData.getAsString() : "minecraft:air";

            dos.writeInt(loc.getBlockX());
            dos.writeInt(loc.getBlockY());
            dos.writeInt(loc.getBlockZ());
            dos.writeUTF(blockDataStr);
        }
    }

    public void loadFromDatc(File datCities) throws IOException {
        this.datcFile = datCities;
        long startTime = System.currentTimeMillis();

        try (FileInputStream fis = new FileInputStream(datCities);
             BufferedInputStream bis = new BufferedInputStream(fis, BUFFER_SIZE);
             GZIPInputStream gzip = new GZIPInputStream(bis);
             DataInputStream dis = new DataInputStream(gzip)) {

            String header = readHeader(dis);
            String[] headerParts = header.split(",");
            if (headerParts.length < 11) {
                throw new IOException("Invalid .datc file: Incomplete header");
            }

            String fileVersion = headerParts[0];
            this.fileFormatVersion = fileVersion;
            if (!fileVersion.equals(FILE_FORMAT_VERSION)) {
                headerParts = migrateHeader(fileVersion, headerParts);
            }

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
                LOGGER.warning("[ArenaRegen] World '" + worldName + "' not found for region in " + datCities.getName() + ". Deferring block data loading.");
                isBlockDataLoaded = false;
                spawnLocation = null;
                locked = false;
                return;
            }

            if (headerParts.length >= 16) {

                double spawnX = Double.parseDouble(headerParts[11]);
                double spawnY = Double.parseDouble(headerParts[12]);
                double spawnZ = Double.parseDouble(headerParts[13]);
                float spawnYaw = Float.parseFloat(headerParts[14]);
                float spawnPitch = Float.parseFloat(headerParts[15]);

                if (spawnX == 0 && spawnY == 0 && spawnZ == 0 && spawnYaw == 0 && spawnPitch == 0) {
                    spawnLocation = null;
                } else {
                    spawnLocation = new Location(world, spawnX, spawnY, spawnZ, spawnYaw, spawnPitch);
                }
                locked = Boolean.parseBoolean(headerParts[16]);
            } else if (headerParts.length >= 11) {

                double spawnX = headerParts.length > 11 ? Double.parseDouble(headerParts[11]) : 0;
                double spawnY = headerParts.length > 12 ? Double.parseDouble(headerParts[12]) : 0;
                double spawnZ = headerParts.length > 13 ? Double.parseDouble(headerParts[13]) : 0;
                float spawnYaw = headerParts.length > 14 ? Float.parseFloat(headerParts[14]) : 0;
                float spawnPitch = headerParts.length > 15 ? Float.parseFloat(headerParts[15]) : 0;
                if (spawnX == 0 && spawnY == 0 && spawnZ == 0 && spawnYaw == 0 && spawnPitch == 0) {
                    spawnLocation = null;
                } else {
                    spawnLocation = new Location(world, spawnX, spawnY, spawnZ, spawnYaw, spawnPitch);
                }
                locked = false;
            } else {
                spawnLocation = null;
                locked = false;
            }

            readSections(dis, world);
            readEntities(dis, world);
            readModifiedBlocks(dis, world);

            isBlockDataLoaded = true;
        } catch (Exception e) {
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
                locked = false;
                return;
            }

            if (headerParts.length >= 16) {
                double spawnX = Double.parseDouble(headerParts[11]);
                double spawnY = Double.parseDouble(headerParts[12]);
                double spawnZ = Double.parseDouble(headerParts[13]);
                float spawnYaw = Float.parseFloat(headerParts[14]);
                float spawnPitch = Float.parseFloat(headerParts[15]);
                if (spawnX == 0 && spawnY == 0 && spawnZ == 0 && spawnYaw == 0 && spawnPitch == 0) {
                    spawnLocation = null;
                } else {
                    spawnLocation = new Location(world, spawnX, spawnY, spawnZ, spawnYaw, spawnPitch);
                }
                locked = Boolean.parseBoolean(headerParts[16]);
            } else if (headerParts.length >= 11) {

                double spawnX = headerParts.length > 11 ? Double.parseDouble(headerParts[11]) : 0;
                double spawnY = headerParts.length > 12 ? Double.parseDouble(headerParts[12]) : 0;
                double spawnZ = headerParts.length > 13 ? Double.parseDouble(headerParts[13]) : 0;
                float spawnYaw = headerParts.length > 14 ? Float.parseFloat(headerParts[14]) : 0;
                float spawnPitch = headerParts.length > 15 ? Float.parseFloat(headerParts[15]) : 0;
                if (spawnX == 0 && spawnY == 0 && spawnZ == 0 && spawnYaw == 0 && spawnPitch == 0) {
                    spawnLocation = null;
                } else {
                    spawnLocation = new Location(world, spawnX, spawnY, spawnZ, spawnYaw, spawnPitch);
                }
                locked = false;
            } else {
                spawnLocation = null;
                locked = false;
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
                    } catch (IllegalArgumentException f) {
                        plugin.getLogger().info("Invalid block data '" + blockDataStr + "' at " + loc + " in section " + sectionName + ": " + f.getMessage() + ", replacing with air.");
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
                } catch (Exception g) {
                    plugin.getLogger().info("Failed to deserialize entity data at " + loc + ": " + g.getMessage() + ", skipping.");
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
                } catch (IllegalArgumentException h) {
                    plugin.getLogger().info("Invalid block data '" + blockDataStr + "' for modified block at " + loc + ": " + h.getMessage() + ", skipping.");
                }
            }

            isBlockDataLoaded = true;
        }

        long timeTaken = System.currentTimeMillis() - startTime;
        long fileSize = datCities.length();
        LOGGER.info("[ArenaRegen] Loaded RegionData for file " + datCities.getName() + ": " +
                sectionedBlockData.size() + " sections, " + getAllBlocks().size() + " total blocks, " +
                entityDataMap.size() + " entities, " + modifiedBlocks.size() + " modified blocks. " +
                "Locked: " + locked + ", File size: " + (fileSize / 1024) + " KB, Time: " + timeTaken + "ms");
    }

    private String readHeader(DataInputStream dis) throws IOException {
        StringBuilder headerBuilder = new StringBuilder();
        int b;
        while ((b = dis.read()) != -1 && b != '\n') {
            headerBuilder.append((char) b);
        }
        if (b == -1) throw new IOException("Invalid .datc file: No header section found");
        return headerBuilder.toString();
    }

    private String[] migrateHeader(String fileVersion, String[] headerParts) throws IOException {
        if (fileVersion.equals("1") || fileVersion.equals("2")) {

            String[] newHeader = new String[17];
            System.arraycopy(headerParts, 0, newHeader, 0, Math.min(headerParts.length, 11));

            for (int i = headerParts.length; i < 11; i++) {
                newHeader[i] = "0";
            }

            if (headerParts.length < 16) {
                newHeader[11] = "0";
                newHeader[12] = "0";
                newHeader[13] = "0";
                newHeader[14] = "0";
                newHeader[15] = "0";
            } else {
                System.arraycopy(headerParts, 11, newHeader, 11, 5);
            }

            newHeader[16] = "false";

            newHeader[0] = FILE_FORMAT_VERSION;
            return newHeader;
        }
        throw new IOException("Unsupported .datc file version: " + fileVersion);
    }

    private void readSections(DataInputStream dis, World world) throws IOException {
        int sectionCount = dis.readInt();
        sectionedBlockData.clear();

        for (int i = 0; i < sectionCount; i++) {
            String sectionName = dis.readUTF();
            int blockCount = dis.readInt();
            Map<Location, BlockData> blocks = new ConcurrentHashMap<>();

            for (int j = 0; j < blockCount; j++) {
                int x = dis.readInt();
                int y = dis.readInt();
                int z = dis.readInt();
                String blockDataStr = dis.readUTF();

                Location loc = new Location(world, x, y, z);
                try {
                    BlockData blockData = Bukkit.createBlockData(blockDataStr);
                    blocks.put(loc, blockData);
                } catch (IllegalArgumentException e) {
                    LOGGER.warning("[ArenaRegen] Invalid block data '" + blockDataStr + "' at " + loc + " in section " + sectionName + ": " + e.getMessage() + ", replacing with air.");
                    blocks.put(loc, Bukkit.createBlockData(Material.AIR));
                }
            }
            sectionedBlockData.put(sectionName, blocks);
        }
    }

    private void readEntities(DataInputStream dis, World world) throws IOException {
        int entityCount = dis.readInt();
        entityDataMap.clear();
        for (int i = 0; i < entityCount; i++) {
            double x = dis.readDouble();
            double y = dis.readDouble();
            double z = dis.readDouble();
            int entityDataLength = dis.readInt();
            byte[] entityDataBytes = new byte[entityDataLength];
            dis.readFully(entityDataBytes);

            Location loc = new Location(world, x, y, z);
            try {
                ByteArrayInputStream entityStream = new ByteArrayInputStream(entityDataBytes);
                try (ObjectInputStream ois = new ObjectInputStream(entityStream)) {
                    Map<String, Object> serializedEntity = (Map<String, Object>) ois.readObject();
                    entityDataMap.put(loc, serializedEntity);
                }
            } catch (Exception e) {
                LOGGER.warning("[ArenaRegen] Failed to deserialize entity data at " + loc + ": " + e.getMessage() + ", skipping.");
            }
        }
    }

    private void readModifiedBlocks(DataInputStream dis, World world) throws IOException {
        int modifiedCount = dis.readInt();
        modifiedBlocks.clear();
        for (int i = 0; i < modifiedCount; i++) {
            int x = dis.readInt();
            int y = dis.readInt();
            int z = dis.readInt();
            String blockDataStr = dis.readUTF();

            Location loc = new Location(world, x, y, z);
            try {
                BlockData blockData = Bukkit.createBlockData(blockDataStr);
                modifiedBlocks.put(loc, blockData);
            } catch (IllegalArgumentException e) {
                LOGGER.warning("[ArenaRegen] Invalid block data '" + blockDataStr + "' for modified block at " + loc + ": " + e.getMessage() + ", skipping.");
            }
        }
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
            LOGGER.warning("[ArenaRegen] Failed to load block data for sectionedBlockData: " + e.getMessage());
        }
        return sectionedBlockData;
    }

    public Map<Location, BlockData> getAllBlocks() {
        try {
            ensureBlockDataLoaded();
        } catch (IOException e) {
            LOGGER.warning("[ArenaRegen] Failed to load block data for all blocks: " + e.getMessage());
        }
        Map<Location, BlockData> allBlocks = new ConcurrentHashMap<>();
        for (Map.Entry<String, Map<Location, BlockData>> entry : sectionedBlockData.entrySet()) {
            allBlocks.putAll(entry.getValue());
        }
        return allBlocks;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;

        if (datcFile != null) {
            String regionName = datcFile.getName().replace(".datc", "");
            plugin.markRegionDirty(regionName);
        }
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

    public String getFileFormatVersion() {
        return fileFormatVersion;
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