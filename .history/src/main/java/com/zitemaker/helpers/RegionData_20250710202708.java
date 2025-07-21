package com.zitemaker.helpers;

import com.zitemaker.ArenaRegen;
import org.bukkit.*;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.Banner;
import org.bukkit.block.Sign;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class RegionData {
    private static final Logger LOGGER = JavaPlugin.getPlugin(ArenaRegen.class).getLogger();
    private static final String FILE_FORMAT_VERSION = "4";
    private static final int GZIP_COMPRESSION_LEVEL = 6;
    private static final byte[] GZIP_MAGIC = new byte[] { (byte) 0x1F, (byte) 0x8B };

    private static final List<String> KNOWN_PATTERN_IDENTIFIERS = Arrays.asList(
            "base", "square_bottom_left", "square_bottom_right", "square_top_left", "square_top_right",
            "stripe_bottom", "stripe_top", "stripe_left", "stripe_right", "stripe_center", "stripe_middle",
            "stripe_downright", "stripe_downleft", "small_stripes", "cross", "straight_cross",
            "triangle_bottom", "triangle_top", "triangles_bottom", "triangles_top",
            "diagonal_left", "diagonal_right", "diagonal_up_left", "diagonal_up_right",
            "circle", "rhombus", "half_vertical", "half_horizontal", "half_vertical_right", "half_horizontal_bottom",
            "border", "curly_border", "gradient", "gradient_up", "bricks", "globe", "creeper", "skull",
            "flower", "mojang", "piglin", "flow", "guster"
    );

    private final ArenaRegen plugin = new ArenaRegen();
    public final Map<String, Map<Location, BlockData>> sectionedBlockData = new ConcurrentHashMap<>();
    private final Map<Location, Map<String, Object>> entityDataMap = new ConcurrentHashMap<>();
    private final Map<Location, BlockData> modifiedBlocks = new ConcurrentHashMap<>();
    private final Map<Location, Map<String, Object>> bannerStates = new ConcurrentHashMap<>();
    private final Map<Location, Map<String, Object>> signStates = new ConcurrentHashMap<>();

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
    private CompletableFuture<Void> blockDataLoadFuture = null;


    public static boolean forceStreaming = plugin.getConfig().getBoolean("general.force-streaming", false);

    private DataOutputStream streamingDos = null;
    private int streamingSectionCount = 0;
    private File streamingDatcFile = null;


    public CompletableFuture<Void> startStreamingDatc(File datcFile) {
        return CompletableFuture.runAsync(() -> {
            try {
                if (streamingDos != null) throw new IOException("Streaming already in progress");
                this.streamingDatcFile = datcFile;
                FileOutputStream fos = new FileOutputStream(datcFile);
                BufferedOutputStream bos = new BufferedOutputStream(fos, calculateBufferSize());
                GZIPOutputStream gzip = new GZIPOutputStream(bos) {{ def.setLevel(GZIP_COMPRESSION_LEVEL); }};
                streamingDos = new DataOutputStream(gzip);
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
                streamingDos.writeBytes(header);
                streamingDos.writeByte('\n');
                streamingDos.writeInt(0);
                streamingSectionCount = 0;
            } catch (IOException e) {
                throw new RuntimeException("Failed to start streaming datc file", e);
            }
        });
    }


    public CompletableFuture<Void> writeChunkToDatc(String sectionName, Map<Location, BlockData> blocks, File datcFile, boolean append) {
        return CompletableFuture.runAsync(() -> {
            try {
                if (streamingDos == null || !datcFile.equals(streamingDatcFile)) throw new IOException("Streaming not started or file mismatch");
                streamingDos.writeUTF(sectionName);
                streamingDos.writeInt(blocks.size());
                for (Map.Entry<Location, BlockData> blockEntry : blocks.entrySet()) {
                    Location loc = blockEntry.getKey();
                    BlockData blockData = blockEntry.getValue();
                    String blockDataStr = blockData != null ? blockData.getAsString() : "minecraft:air";
                    streamingDos.writeInt(loc.getBlockX());
                    streamingDos.writeInt(loc.getBlockY());
                    streamingDos.writeInt(loc.getBlockZ());
                    streamingDos.writeUTF(blockDataStr);
                }
                streamingSectionCount++;
                streamingDos.flush();
            } catch (IOException e) {
                throw new RuntimeException("Failed to write chunk to datc file", e);
            }
        });
    }


    public CompletableFuture<Void> finalizeStreamingDatc(File datcFile, Map<Location, Map<String, Object>> entities, Map<Location, Map<String, Object>> banners, Map<Location, Map<String, Object>> signs, Map<Location, BlockData> modifiedBlocks) {
        return CompletableFuture.runAsync(() -> {
            try {
                if (streamingDos == null || !datcFile.equals(streamingDatcFile)) throw new IOException("Streaming not started or file mismatch");

                writeEntities(streamingDos, entities);
                writeBanners(streamingDos, banners);
                writeSigns(streamingDos, signs);
                writeModifiedBlocks(streamingDos, modifiedBlocks);
                streamingDos.flush();
                streamingDos.close();

                streamingDos = null;
                streamingDatcFile = null;
                streamingSectionCount = 0;
            } catch (IOException e) {
                throw new RuntimeException("Failed to finalize streaming datc file", e);
            }
        });
    }

    public RegionData(ArenaRegen plugin) {
        this.plugin = plugin;
    }

    public void setDatcFile(File datcFile) {
        this.datcFile = datcFile;
    }

    public void setSpawnLocation(Location location) {
        this.spawnLocation = location != null ? location.clone() : null;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
        if (datcFile != null) {
            String regionName = datcFile.getName().replace(".datc", "");
            plugin.markRegionDirty(regionName);
        }
    }

    public Location getSpawnLocation() {
        return spawnLocation != null ? spawnLocation.clone() : null;
    }

    public void addBlockToSection(String section, Location location, BlockData blockData) {
        sectionedBlockData.computeIfAbsent(section, k -> new ConcurrentHashMap<>()).put(location, blockData);
        World world = location.getWorld();
        if (world != null) {
            BlockState state = world.getBlockAt(location).getState();
            if (state instanceof Banner) {
                Banner banner = (Banner) state;
                Map<String, Object> bannerData = new HashMap<>();
                DyeColor baseColor = banner.getBaseColor();
                bannerData.put("baseColor", baseColor != null ? baseColor.name() : "NONE");
                List<Map<String, String>> patternDataList = new ArrayList<>();
                for (Pattern pattern : banner.getPatterns()) {
                    Map<String, String> patternData = new HashMap<>();
                    DyeColor color = pattern.getColor();
                    patternData.put("color", color.name());
                    String patternIdentifier = resolvePatternIdentifier(pattern);
                    patternData.put("type", patternIdentifier);
                    patternDataList.add(patternData);
                }
                bannerData.put("patterns", patternDataList);
                PersistentDataContainer pdc = banner.getPersistentDataContainer();
                if (!pdc.isEmpty()) {
                    Map<String, Object> pdcData = serializePdc(pdc);
                    bannerData.put("persistentData", pdcData);
                }
                bannerStates.put(location.clone(), bannerData);
            }
            if (state instanceof Sign) {
                Sign sign = (Sign) state;
                Map<String, Object> signData = new HashMap<>();
                List<String> lines = new ArrayList<>();
                for (int i = 0; i < 4; i++) {
                    lines.add(sign.getLine(i));
                }
                signData.put("lines", lines);
                DyeColor color = sign.getColor();
                signData.put("color", color != null ? color.name() : "BLACK");
                signData.put("glowing", sign.isGlowingText());
                PersistentDataContainer pdc = sign.getPersistentDataContainer();
                if (!pdc.isEmpty()) {
                    Map<String, Object> pdcData = serializePdc(pdc);
                    signData.put("persistentData", pdcData);
                }
                signStates.put(location.clone(), signData);
            }
        }
    }

    public void addSection(String sectionName, Map<Location, BlockData> blocks) {
        Map<Location, BlockData> sectionBlocks = new ConcurrentHashMap<>();
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            LOGGER.warning("[ArenaRegen] World '" + worldName + "' not found, cannot check for banners or signs in section " + sectionName);
        }

        final int CHUNK_SIZE = plugin.getConfig().getInt("general.large-region-settings.chunk-size", 10000);
        List<Map.Entry<Location, BlockData>> blockEntries = new ArrayList<>(blocks.entrySet());
        
        for (int i = 0; i < blockEntries.size(); i += CHUNK_SIZE) {
            int endIndex = Math.min(i + CHUNK_SIZE, blockEntries.size());
            List<Map.Entry<Location, BlockData>> chunk = blockEntries.subList(i, endIndex);
            
            for (Map.Entry<Location, BlockData> entry : chunk) {
                Location location = entry.getKey();
                BlockData blockData = entry.getValue();
                if (blockData == null) {
                    LOGGER.warning("[ArenaRegen] Block data in section " + sectionName + " at " + location + " is null, replacing with air.");
                    blockData = Bukkit.createBlockData(Material.AIR);
                }
                sectionBlocks.put(location, blockData);
                
                if (world != null && blocks.size() < 500000) {
                    BlockState state = world.getBlockAt(location).getState();
                    if (state instanceof Banner) {
                        Banner banner = (Banner) state;
                        Map<String, Object> bannerData = new HashMap<>();
                        DyeColor baseColor = banner.getBaseColor();
                        bannerData.put("baseColor", baseColor != null ? baseColor.name() : "NONE");
                        List<Map<String, String>> patternDataList = new ArrayList<>();
                        for (Pattern pattern : banner.getPatterns()) {
                            Map<String, String> patternData = new HashMap<>();
                            DyeColor color = pattern.getColor();
                            patternData.put("color", color.name());
                            String patternIdentifier = resolvePatternIdentifier(pattern);
                            patternData.put("type", patternIdentifier);
                            patternDataList.add(patternData);
                        }
                        bannerData.put("patterns", patternDataList);
                        PersistentDataContainer pdc = banner.getPersistentDataContainer();
                        if (!pdc.isEmpty()) {
                            Map<String, Object> pdcData = serializePdc(pdc);
                            bannerData.put("persistentData", pdcData);
                        }
                        bannerStates.put(location.clone(), bannerData);
                    }
                    if (state instanceof Sign) {
                        Sign sign = (Sign) state;
                        Map<String, Object> signData = new HashMap<>();
                        List<String> lines = new ArrayList<>();
                        for (int j = 0; j < 4; j++) {
                            lines.add(sign.getLine(j));
                        }
                        signData.put("lines", lines);
                        DyeColor color = sign.getColor();
                        signData.put("color", color != null ? color.name() : "BLACK");
                        signData.put("glowing", sign.isGlowingText());
                        PersistentDataContainer pdc = sign.getPersistentDataContainer();
                        if (!pdc.isEmpty()) {
                            Map<String, Object> pdcData = serializePdc(pdc);
                            signData.put("persistentData", pdcData);
                        }
                        signStates.put(location.clone(), signData);
                    }
                }
            }
            
            if (blocks.size() > 100000 && i % (CHUNK_SIZE * 2) == 0) {
                System.gc();
            }
        }
        
        sectionedBlockData.put(sectionName, sectionBlocks);
    }

    private String resolvePatternIdentifier(Pattern pattern) {
        DyeColor color = pattern.getColor();
        Registry<PatternType> patternRegistry = Bukkit.getRegistry(PatternType.class);
        for (String identifier : KNOWN_PATTERN_IDENTIFIERS) {
            NamespacedKey key = new NamespacedKey("minecraft", identifier);
            PatternType patternType = patternRegistry.get(key);
            if (patternType != null) {
                Pattern testPattern = new Pattern(color, patternType);
                if (testPattern.toString().equals(pattern.toString())) {
                    return identifier;
                }
            }
        }
        LOGGER.warning("[ArenaRegen] Unknown pattern type for pattern: " + pattern.toString() + ", defaulting to 'base'.");
        return "base";
    }

    private Map<String, Object> serializePdc(PersistentDataContainer pdc) {
        Map<String, Object> pdcData = new HashMap<>();
        for (NamespacedKey key : pdc.getKeys()) {
            if (pdc.has(key, PersistentDataType.STRING)) {
                pdcData.put(key.toString(), pdc.get(key, PersistentDataType.STRING));
            } else if (pdc.has(key, PersistentDataType.INTEGER)) {
                pdcData.put(key.toString(), pdc.get(key, PersistentDataType.INTEGER));
            } else if (pdc.has(key, PersistentDataType.DOUBLE)) {
                pdcData.put(key.toString(), pdc.get(key, PersistentDataType.DOUBLE));
            } else if (pdc.has(key, PersistentDataType.BYTE)) {
                pdcData.put(key.toString(), pdc.get(key, PersistentDataType.BYTE));
            } else if (pdc.has(key, PersistentDataType.LONG)) {
                pdcData.put(key.toString(), pdc.get(key, PersistentDataType.LONG));
            } else {
                LOGGER.warning("[ArenaRegen] Unsupported PDC data type for key " + key + ", skipping.");
            }
        }
        return pdcData;
    }

    private void deserializePdc(PersistentDataContainer pdc, Map<String, Object> pdcData) {
        for (Map.Entry<String, Object> entry : pdcData.entrySet()) {
            try {
                NamespacedKey key = NamespacedKey.fromString(entry.getKey());
                if (key == null) {
                    LOGGER.warning("[ArenaRegen] Invalid NamespacedKey '" + entry.getKey() + "', skipping PDC entry.");
                    continue;
                }
                Object value = entry.getValue();
                if (value instanceof String) {
                    pdc.set(key, PersistentDataType.STRING, (String) value);
                } else if (value instanceof Integer) {
                    pdc.set(key, PersistentDataType.INTEGER, (Integer) value);
                } else if (value instanceof Double) {
                    pdc.set(key, PersistentDataType.DOUBLE, (Double) value);
                } else if (value instanceof Byte) {
                    pdc.set(key, PersistentDataType.BYTE, (Byte) value);
                } else if (value instanceof Long) {
                    pdc.set(key, PersistentDataType.LONG, (Long) value);
                } else {
                    LOGGER.warning("[ArenaRegen] Unsupported PDC value type for key " + key + ", skipping.");
                }
            } catch (Exception e) {
                LOGGER.warning("[ArenaRegen] Failed to deserialize PDC entry for key " + entry.getKey() + ": " + e.getMessage());
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
        if (datcFile != null) {
            String regionName = datcFile.getName().replace(".datc", "");
            plugin.markRegionDirty(regionName);
        }
    }

    public void markBlockModified(Location location, BlockData newBlockData) {
        modifiedBlocks.put(location, newBlockData);
        if (datcFile != null) {
            String regionName = datcFile.getName().replace(".datc", "");
            plugin.markRegionDirty(regionName);
        }
    }

    public void clearModifiedBlocks() {
        modifiedBlocks.clear();
    }

    public Map<Location, BlockData> getModifiedBlocks() {
        return new HashMap<>(modifiedBlocks);
    }

    public void addEntity(Location location, Map<String, Object> serializedEntity) {
        entityDataMap.put(location, serializedEntity);
    }

    public Map<Location, Map<String, Object>> getEntityDataMap() {
        return new HashMap<>(entityDataMap);
    }

    public void clearEntities() {
        entityDataMap.clear();
    }

    public Map<Location, Map<String, Object>> getBannerStates() {
        return new HashMap<>(bannerStates);
    }

    public void clearBanners() {
        bannerStates.clear();
    }

    public Map<Location, Map<String, Object>> getSignStates() {
        return new HashMap<>(signStates);
    }

    public void clearSigns() {
        signStates.clear();
    }

    public void clearRegion(String regionName) {
        sectionedBlockData.clear();
        entityDataMap.clear();
        modifiedBlocks.clear();
        bannerStates.clear();
        signStates.clear();
        plugin.getPendingDeletions().remove(regionName);
        plugin.getRegisteredRegions().remove(regionName);
        plugin.regeneratingArenas.remove(regionName);
    }

    private int calculateBufferSize() {
        long totalBlocks = sectionedBlockData.values().stream().mapToLong(Map::size).sum();
        long totalEntities = entityDataMap.size();
        long totalModifiedBlocks = modifiedBlocks.size();
        long totalBanners = bannerStates.size();
        long totalSigns = signStates.size();

        long estimatedSize = (totalBlocks * 42) + (totalEntities * 124) + (totalModifiedBlocks * 42) + (totalBanners * 128) + (totalSigns * 64) + 1024;

        if (estimatedSize < 500_000) {
            return 4096;
        } else if (estimatedSize < 5_000_000) {
            return 16384;
        } else {
            return 32768;
        }
    }

    public CompletableFuture<Void> saveToDatc(File datcFile) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        long startTime = System.currentTimeMillis();


        Map<String, Map<Location, BlockData>> sectionedBlockDataCopy = createChunkedCopy(sectionedBlockData);
        Map<Location, Map<String, Object>> entityDataMapCopy = new ConcurrentHashMap<>(entityDataMap);
        Map<Location, BlockData> modifiedBlocksCopy = new ConcurrentHashMap<>(modifiedBlocks);
        Map<Location, Map<String, Object>> bannerStatesCopy = new ConcurrentHashMap<>(bannerStates);
        Map<Location, Map<String, Object>> signStatesCopy = new ConcurrentHashMap<>(signStates);

        int bufferSize = calculateBufferSize();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
            File backupFile = new File(datcFile.getParent(), datcFile.getName() + ".bak");
                if (datcFile.exists()) {
                    if (!datcFile.renameTo(backupFile)) {
                //LOGGER.warning("[ArenaRegen] Failed to create backup of " + datcFile.getName());
                    }
            }

            try (FileOutputStream fos = new FileOutputStream(datcFile);
                 BufferedOutputStream bos = new BufferedOutputStream(fos, bufferSize);
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

                writeSections(dos, sectionedBlockDataCopy);
                writeEntities(dos, entityDataMapCopy);
                writeBanners(dos, bannerStatesCopy);
                writeSigns(dos, signStatesCopy);
                writeModifiedBlocks(dos, modifiedBlocksCopy);
                dos.flush();

            } catch (IOException e) {
                if (backupFile.exists()) {
                    if (datcFile.exists()) datcFile.delete();
                    backupFile.renameTo(datcFile);
                }
                future.completeExceptionally(e);
                return;
            }

            long timeTaken = System.currentTimeMillis() - startTime;
            long fileSize = datcFile.length();
            LOGGER.info("[ArenaRegen] Saved RegionData to " + datcFile.getName() + ": " +
                        sectionedBlockDataCopy.size() + " sections, " + sectionedBlockDataCopy.values().stream().mapToLong(Map::size).sum() + " total blocks, " +
                        entityDataMapCopy.size() + " entities, " + bannerStatesCopy.size() + " banners, " + signStatesCopy.size() + " signs, " +
                        modifiedBlocksCopy.size() + " modified blocks. " +
                    "File size: " + (fileSize / 1024) + " KB, Time: " + timeTaken + "ms");

            future.complete(null);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });

        return future;
    }


    private Map<String, Map<Location, BlockData>> createChunkedCopy(Map<String, Map<Location, BlockData>> source) {
        Map<String, Map<Location, BlockData>> copy = new ConcurrentHashMap<>();
        final int CHUNK_SIZE = plugin.getConfig().getInt("general.large-region-settings.chunk-size", 10000);
        
        for (Map.Entry<String, Map<Location, BlockData>> sectionEntry : source.entrySet()) {
            String sectionName = sectionEntry.getKey();
            Map<Location, BlockData> sourceBlocks = sectionEntry.getValue();

            Map<Location, BlockData> sectionCopy = new ConcurrentHashMap<>();
            copy.put(sectionName, sectionCopy);

            List<Map.Entry<Location, BlockData>> blockEntries = new ArrayList<>(sourceBlocks.entrySet());
            for (int i = 0; i < blockEntries.size(); i += CHUNK_SIZE) {
                int endIndex = Math.min(i + CHUNK_SIZE, blockEntries.size());
                List<Map.Entry<Location, BlockData>> chunk = blockEntries.subList(i, endIndex);
                
                for (Map.Entry<Location, BlockData> blockEntry : chunk) {
                    sectionCopy.put(blockEntry.getKey(), blockEntry.getValue());
                }
                
                if (i % (CHUNK_SIZE * 10) == 0) {
                    System.gc();
                }
            }
        }
        
        return copy;
    }

    private void writeSections(DataOutputStream dos, Map<String, Map<Location, BlockData>> sectionedBlockDataCopy) throws IOException {
        dos.writeInt(sectionedBlockDataCopy.size());
        for (Map.Entry<String, Map<Location, BlockData>> entry : sectionedBlockDataCopy.entrySet()) {
            String sectionName = entry.getKey();
            Map<Location, BlockData> blocks = entry.getValue();

            dos.writeUTF(sectionName);
            dos.writeInt(blocks.size());
            
            int batchSize = blocks.size() > 1000000 ? plugin.getConfig().getInt("general.large-region-settings.write-batch-size", 500) : 1000;
            int count = 0;

            List<Map.Entry<Location, BlockData>> blockEntries = new ArrayList<>(blocks.entrySet());
            final int CHUNK_SIZE = plugin.getConfig().getInt("general.large-region-settings.chunk-size", 10000);
            
            for (int i = 0; i < blockEntries.size(); i += CHUNK_SIZE) {
                int endIndex = Math.min(i + CHUNK_SIZE, blockEntries.size());
                List<Map.Entry<Location, BlockData>> chunk = blockEntries.subList(i, endIndex);
                
                for (Map.Entry<Location, BlockData> blockEntry : chunk) {
                    Location loc = blockEntry.getKey();
                    BlockData blockData = blockEntry.getValue();
                    String blockDataStr = blockData != null ? blockData.getAsString() : "minecraft:air";
                    dos.writeInt(loc.getBlockX());
                    dos.writeInt(loc.getBlockY());
                    dos.writeInt(loc.getBlockZ());
                    dos.writeUTF(blockDataStr);
                    count++;
                    if (count % batchSize == 0) {
                        dos.flush();
                    }
                }
                

                if (blocks.size() > 100000 && i % (CHUNK_SIZE * 5) == 0) {
                    System.gc();
                }
            }
            
            if (count % batchSize != 0) {
                dos.flush();
            }
        }
    }

    private static Object makeSerializable(Object obj) {
        if (obj instanceof org.bukkit.util.Vector vector) {
            return vector.serialize();
        } else if (obj instanceof Map<?, ?> map) {
            Map<Object, Object> newMap = new HashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                newMap.put(entry.getKey(), makeSerializable(entry.getValue()));
            }
            return newMap;
        } else if (obj instanceof List<?> list) {
            List<Object> newList = new ArrayList<>();
            for (Object item : list) {
                newList.add(makeSerializable(item));
            }
            return newList;
        } else if (obj == null || obj instanceof String || obj instanceof Number || obj instanceof Boolean) {
            return obj;
        } else if (obj instanceof java.io.Serializable) {
            return obj;
        } else {
            return null;
        }
    }

    private void writeEntities(DataOutputStream dos, Map<Location, Map<String, Object>> entityDataMapCopy) throws IOException {
        dos.writeInt(entityDataMapCopy.size());
        int batchSize = 100;
        int count = 0;
        for (Map.Entry<Location, Map<String, Object>> entry : entityDataMapCopy.entrySet()) {
            Location loc = entry.getKey();
            Map<String, Object> serializedEntity = entry.getValue();

            dos.writeDouble(loc.getX());
            dos.writeDouble(loc.getY());
            dos.writeDouble(loc.getZ());

            ByteArrayOutputStream entityStream = new ByteArrayOutputStream();
            byte[] entityDataBytes;
            Object safeEntity = makeSerializable(serializedEntity);
            try (ObjectOutputStream oos = new ObjectOutputStream(entityStream)) {
                if (safeEntity instanceof java.io.Serializable) {
                    oos.writeObject(safeEntity);
                } else {
                    oos.writeObject(new HashMap<>());
                }
                entityDataBytes = entityStream.toByteArray();
            } catch (IOException e) {
                LOGGER.warning("[ArenaRegen] Skipping non-serializable entity at " + loc + ": " + e.getMessage());
                entityStream = new ByteArrayOutputStream();
                try (ObjectOutputStream oos2 = new ObjectOutputStream(entityStream)) {
                    oos2.writeObject(new HashMap<>());
                    entityDataBytes = entityStream.toByteArray();
                } catch (IOException ex) {
                    entityDataBytes = new byte[0];
                }
            }
            dos.writeInt(entityDataBytes.length);
            dos.write(entityDataBytes);

            count++;
            if (count % batchSize == 0) {
                dos.flush();
            }
        }
        if (count % batchSize != 0) {
            dos.flush();
        }
    }

    private void writeBanners(DataOutputStream dos, Map<Location, Map<String, Object>> bannerStatesCopy) throws IOException {
        dos.writeInt(bannerStatesCopy.size());
        int batchSize = 100;
        int count = 0;
        for (Map.Entry<Location, Map<String, Object>> entry : bannerStatesCopy.entrySet()) {
            Location loc = entry.getKey();
            Map<String, Object> bannerData = entry.getValue();

            dos.writeDouble(loc.getX());
            dos.writeDouble(loc.getY());
            dos.writeDouble(loc.getZ());

            String baseColorStr = (String) bannerData.get("baseColor");
            DyeColor baseColor = baseColorStr.equals("NONE") ? null : DyeColor.valueOf(baseColorStr);
            dos.writeByte(baseColor != null ? (byte) baseColor.ordinal() : (byte) -1);

            List<Map<String, String>> patternDataList = (List<Map<String, String>>) bannerData.get("patterns");
            if (patternDataList != null && !patternDataList.isEmpty()) {
                dos.writeByte((byte) patternDataList.size());
                for (Map<String, String> patternData : patternDataList) {
                    String colorStr = patternData.get("color");
                    String typeStr = patternData.get("type");
                    try {
                        DyeColor color = DyeColor.valueOf(colorStr);
                        dos.writeByte((byte) color.ordinal());
                        dos.writeUTF(typeStr);
                    } catch (IllegalArgumentException e) {
                        LOGGER.warning("[ArenaRegen] Invalid pattern color " + colorStr + " at " + loc + ", skipping.");
                        dos.writeByte((byte) -1);
                        dos.writeUTF("");
                    }
                }
            } else {
                dos.writeByte((byte) 0);
            }

            Map<String, Object> pdcData = (Map<String, Object>) bannerData.get("persistentData");
            if (pdcData != null && !pdcData.isEmpty()) {
                dos.writeInt(pdcData.size());
                for (Map.Entry<String, Object> pdcEntry : pdcData.entrySet()) {
                    dos.writeUTF(pdcEntry.getKey());
                    Object value = pdcEntry.getValue();
                    if (value instanceof String) {
                        dos.writeByte(1);
                        dos.writeUTF((String) value);
                    } else if (value instanceof Integer) {
                        dos.writeByte(2);
                        dos.writeInt((Integer) value);
                    } else if (value instanceof Double) {
                        dos.writeByte(3);
                        dos.writeDouble((Double) value);
                    } else if (value instanceof Byte) {
                        dos.writeByte(4);
                        dos.writeByte((Byte) value);
                    } else if (value instanceof Long) {
                        dos.writeByte(5);
                        dos.writeLong((Long) value);
                    } else {
                        dos.writeByte(0);
                    }
                }
            } else {
                dos.writeInt(0);
            }

            count++;
            if (count % batchSize == 0) {
                dos.flush();
            }
        }
        if (count % batchSize != 0) {
            dos.flush();
        }
    }

    private void writeSigns(DataOutputStream dos, Map<Location, Map<String, Object>> signStatesCopy) throws IOException {
        dos.writeInt(signStatesCopy.size());
        int batchSize = 100;
        int count = 0;
        for (Map.Entry<Location, Map<String, Object>> entry : signStatesCopy.entrySet()) {
            Location loc = entry.getKey();
            Map<String, Object> signData = entry.getValue();

            dos.writeDouble(loc.getX());
            dos.writeDouble(loc.getY());
            dos.writeDouble(loc.getZ());

            List<String> lines = (List<String>) signData.get("lines");
            if (lines != null && !lines.isEmpty()) {
                dos.writeByte((byte) lines.size());
            for (String line : lines) {
                dos.writeUTF(line != null ? line : "");
            }
            } else {
                dos.writeByte((byte) 0);
            }

            String colorStr = (String) signData.get("color");
            DyeColor color = colorStr != null ? DyeColor.valueOf(colorStr) : DyeColor.BLACK;
            dos.writeByte((byte) color.ordinal());

            boolean glowing = (boolean) signData.getOrDefault("glowing", false);
            dos.writeBoolean(glowing);

            Map<String, Object> pdcData = (Map<String, Object>) signData.get("persistentData");
            if (pdcData != null && !pdcData.isEmpty()) {
                dos.writeInt(pdcData.size());
                for (Map.Entry<String, Object> pdcEntry : pdcData.entrySet()) {
                    dos.writeUTF(pdcEntry.getKey());
                    Object value = pdcEntry.getValue();
                    if (value instanceof String) {
                        dos.writeByte(1);
                        dos.writeUTF((String) value);
                    } else if (value instanceof Integer) {
                        dos.writeByte(2);
                        dos.writeInt((Integer) value);
                    } else if (value instanceof Double) {
                        dos.writeByte(3);
                        dos.writeDouble((Double) value);
                    } else if (value instanceof Byte) {
                        dos.writeByte(4);
                        dos.writeByte((Byte) value);
                    } else if (value instanceof Long) {
                        dos.writeByte(5);
                        dos.writeLong((Long) value);
                    } else {
                        dos.writeByte(0);
                    }
                }
            } else {
                dos.writeInt(0);
            }

            count++;
            if (count % batchSize == 0) {
                dos.flush();
            }
        }
        if (count % batchSize != 0) {
            dos.flush();
        }
    }

    private void writeModifiedBlocks(DataOutputStream dos, Map<Location, BlockData> modifiedBlocksCopy) throws IOException {
        dos.writeInt(modifiedBlocksCopy.size());
        int batchSize = 1000;
        int count = 0;
        for (Map.Entry<Location, BlockData> entry : modifiedBlocksCopy.entrySet()) {
            Location loc = entry.getKey();
            BlockData blockData = entry.getValue();
            String blockDataStr = blockData != null ? blockData.getAsString() : "minecraft:air";

            dos.writeInt(loc.getBlockX());
            dos.writeInt(loc.getBlockY());
            dos.writeInt(loc.getBlockZ());
            dos.writeUTF(blockDataStr);

            count++;
            if (count % batchSize == 0) {
                dos.flush();
            }
        }
        if (count % batchSize != 0) {
            dos.flush();
        }
    }

    public CompletableFuture<Void> loadFromDatc(File datcFile) {
        this.datcFile = datcFile;
        CompletableFuture<Void> future = new CompletableFuture<>();
        long startTime = System.currentTimeMillis();
        int bufferSize = datcFile.length() < 500_000 ? 4096 : (datcFile.length() < 5_000_000 ? 16384 : 32768);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                try (FileInputStream fis = new FileInputStream(datcFile);
                     BufferedInputStream bis = new BufferedInputStream(fis, bufferSize);
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
                        LOGGER.warning("[ArenaRegen] World '" + worldName + "' not found for region in " + datcFile.getName() + ". Deferring block data loading.");
                        isBlockDataLoaded = false;
                        spawnLocation = null;
                        locked = false;
                        future.complete(null);
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
                    if (fileVersion.equals("4")) {
                        readBanners(dis, world, fileVersion);
                        readSigns(dis, world, fileVersion);
                    } else {
                        bannerStates.clear();
                        signStates.clear();
                    }
                    readModifiedBlocks(dis, world);

                    isBlockDataLoaded = true;

                    long timeTaken = System.currentTimeMillis() - startTime;
                    long fileSize = datcFile.length();
                    LOGGER.info("[ArenaRegen] Loaded RegionData for file " + datcFile.getName() + ": " +
                            sectionedBlockData.size() + " sections, " + getAllBlocks().join().size() + " total blocks, " +
                            entityDataMap.size() + " entities, " + bannerStates.size() + " banners, " + signStates.size() + " signs, " +
                            modifiedBlocks.size() + " modified blocks. " +
                            "Locked: " + locked + ", File size: " + (fileSize / 1024) + " KB, Time: " + timeTaken + "ms");

                    future.complete(null);
                }
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });

        return future;
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
        if (fileVersion.equals("1") || fileVersion.equals("2") || fileVersion.equals("3")) {
            LOGGER.info("[ArenaRegen] Migrating file format version '" + fileVersion + "' to version " + FILE_FORMAT_VERSION + ".");
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

    private void readBanners(DataInputStream dis, World world, String fileVersion) throws IOException {
        bannerStates.clear();
        int bannerCount = dis.readInt();
        for (int i = 0; i < bannerCount; i++) {
            double x = dis.readDouble();
            double y = dis.readDouble();
            double z = dis.readDouble();
            Location loc = new Location(world, x, y, z);
            Map<String, Object> bannerData = new HashMap<>();

            if (!fileVersion.equals(FILE_FORMAT_VERSION)) {
                if (bannerCount > 0) {
                    dis.readByte();
                    byte patternCount = dis.readByte();
                    for (int j = 0; j < patternCount; j++) {
                        dis.readByte();
                        dis.readUTF();
                    }
                    int pdcSize = dis.readInt();
                    for (int j = 0; j < pdcSize; j++) {
                        dis.readUTF();
                        byte type = dis.readByte();
                        if (type == 1) dis.readUTF();
                        else if (type == 2) dis.readInt();
                        else if (type == 3) dis.readDouble();
                        else if (type == 4) dis.readByte();
                        else if (type == 5) dis.readLong();
                    }
                }
                continue;
            }

            byte baseColorOrdinal = dis.readByte();
            DyeColor baseColor = null;
            try {
                baseColor = baseColorOrdinal != -1 ? DyeColor.values()[baseColorOrdinal] : null;
            } catch (ArrayIndexOutOfBoundsException e) {
                LOGGER.warning("[ArenaRegen] Invalid base color ordinal " + baseColorOrdinal + " at " + loc + ", defaulting to null.");
            }
            bannerData.put("baseColor", baseColor != null ? baseColor.name() : "NONE");

            byte patternCount = dis.readByte();
            List<Map<String, String>> patternDataList = new ArrayList<>();
            for (int j = 0; j < patternCount; j++) {
                byte colorOrdinal = dis.readByte();
                String typeStr = dis.readUTF();
                try {
                    DyeColor color = DyeColor.values()[colorOrdinal];
                    String validTypeStr = KNOWN_PATTERN_IDENTIFIERS.contains(typeStr) ? typeStr : "base";
                    patternDataList.add(Map.of("color", color.name(), "type", validTypeStr));
                } catch (ArrayIndexOutOfBoundsException e) {
                    LOGGER.warning("[ArenaRegen] Invalid color ordinal " + colorOrdinal + " for pattern at " + loc + ", skipping.");
                }
            }
            bannerData.put("patterns", patternDataList);

            int pdcSize = dis.readInt();
            if (pdcSize > 0) {
                Map<String, Object> pdcData = new HashMap<>();
                for (int j = 0; j < pdcSize; j++) {
                    String key = dis.readUTF();
                    byte type = dis.readByte();
                    try {
                        switch (type) {
                            case 1:
                                pdcData.put(key, dis.readUTF());
                                break;
                            case 2:
                                pdcData.put(key, dis.readInt());
                                break;
                            case 3:
                                pdcData.put(key, dis.readDouble());
                                break;
                            case 4:
                                pdcData.put(key, dis.readByte());
                                break;
                            case 5:
                                pdcData.put(key, dis.readLong());
                                break;
                            default:
                                LOGGER.warning("[ArenaRegen] Unknown PDC type " + type + " for key " + key + " at " + loc + ", skipping.");
                        }
                    } catch (Exception e) {
                        LOGGER.warning("[ArenaRegen] Failed to read PDC entry for key " + key + " at " + loc + ": " + e.getMessage());
                    }
                }
                bannerData.put("persistentData", pdcData);
                try {
                    BlockState state = world.getBlockAt(loc).getState();
                    if (state instanceof Banner) {
                        Banner banner = (Banner) state;
                        deserializePdc(banner.getPersistentDataContainer(), pdcData);
                    } else {
                        LOGGER.warning("[ArenaRegen] Block at " + loc + " is not a banner, cannot apply PDC.");
                    }
                } catch (Exception e) {
                    LOGGER.warning("[ArenaRegen] Failed to apply PDC for banner at " + loc + ": " + e.getMessage());
                }
            }

            bannerStates.put(loc, bannerData);
        }
    }

    private void readSigns(DataInputStream dis, World world, String fileVersion) throws IOException {
        signStates.clear();
        int signCount = dis.readInt();
        for (int i = 0; i < signCount; i++) {
            double x = dis.readDouble();
            double y = dis.readDouble();
            double z = dis.readDouble();
            Location loc = new Location(world, x, y, z);
            Map<String, Object> signData = new HashMap<>();

            if (!fileVersion.equals(FILE_FORMAT_VERSION)) {
                if (signCount > 0) {
                    byte lineCount = dis.readByte();
                    for (int j = 0; j < lineCount; j++) {
                        dis.readUTF();
                    }
                    dis.readByte();
                    dis.readBoolean();
                    int pdcSize = dis.readInt();
                    for (int j = 0; j < pdcSize; j++) {
                        dis.readUTF();
                        byte type = dis.readByte();
                        if (type == 1) dis.readUTF();
                        else if (type == 2) dis.readInt();
                        else if (type == 3) dis.readDouble();
                        else if (type == 4) dis.readByte();
                        else if (type == 5) dis.readLong();
                    }
                }
                continue;
            }

            byte lineCount = dis.readByte();
            List<String> lines = new ArrayList<>();
            for (int j = 0; j < lineCount && j < 4; j++) {
                lines.add(dis.readUTF());
            }
            while (lines.size() < 4) {
                lines.add("");
            }
            signData.put("lines", lines);

            byte colorOrdinal = dis.readByte();
            DyeColor color = null;
            try {
                color = DyeColor.values()[colorOrdinal];
            } catch (ArrayIndexOutOfBoundsException e) {
                LOGGER.warning("[ArenaRegen] Invalid color ordinal " + colorOrdinal + " at " + loc + ", defaulting to BLACK.");
                color = DyeColor.BLACK;
            }
            signData.put("color", color.name());

            boolean glowing = dis.readBoolean();
            signData.put("glowing", glowing);

            int pdcSize = dis.readInt();
            if (pdcSize > 0) {
                Map<String, Object> pdcData = new HashMap<>();
                for (int j = 0; j < pdcSize; j++) {
                    String key = dis.readUTF();
                    byte type = dis.readByte();
                    try {
                        switch (type) {
                            case 1:
                                pdcData.put(key, dis.readUTF());
                                break;
                            case 2:
                                pdcData.put(key, dis.readInt());
                                break;
                            case 3:
                                pdcData.put(key, dis.readDouble());
                                break;
                            case 4:
                                pdcData.put(key, dis.readByte());
                                break;
                            case 5:
                                pdcData.put(key, dis.readLong());
                                break;
                            default:
                                LOGGER.warning("[ArenaRegen] Unknown PDC type " + type + " for key " + key + " at " + loc + ", skipping.");
                        }
                    } catch (Exception e) {
                        LOGGER.warning("[ArenaRegen] Failed to read PDC entry for key " + key + " at " + loc + ": " + e.getMessage());
                    }
                }
                signData.put("persistentData", pdcData);
                try {
                    BlockState state = world.getBlockAt(loc).getState();
                    if (state instanceof Sign) {
                        Sign sign = (Sign) state;
                        deserializePdc(sign.getPersistentDataContainer(), pdcData);
                    } else {
                        LOGGER.warning("[ArenaRegen] Block at " + loc + " is not a sign, cannot apply PDC.");
                    }
                } catch (Exception e) {
                    LOGGER.warning("[ArenaRegen] Failed to apply PDC for sign at " + loc + ": " + e.getMessage());
                }
            }
            signStates.put(loc, signData);
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

    public synchronized CompletableFuture<Void> ensureBlockDataLoaded() {
        if (isBlockDataLoaded) {
            return CompletableFuture.completedFuture(null);
        }
        if (blockDataLoadFuture != null && !blockDataLoadFuture.isDone()) {
            return blockDataLoadFuture;
        }
        
        blockDataLoadFuture = CompletableFuture.runAsync(() -> {
            if (isLoading) {
                throw new RuntimeException("Recursive loading detected for region in " + (datcFile != null ? datcFile.getName() : "unknown file"));
            }

            if (!sectionedBlockData.isEmpty()) {
                isBlockDataLoaded = true;
                return;
            }

            if (!isBlockDataLoaded && datcFile != null) {
                if (!datcFile.exists()) {
                    throw new RuntimeException("Datc file does not exist: " + datcFile.getAbsolutePath());
                }
                
                if (!datcFile.canRead()) {
                    throw new RuntimeException("Cannot read datc file: " + datcFile.getAbsolutePath());
                }
                
                if (loadFailed) {
                    throw new RuntimeException("Block data loading previously failed for region in " + datcFile.getName());
                }

                World world = Bukkit.getWorld(worldName);
                if (world == null) {
                    loadFailed = true;
                    throw new RuntimeException("World '" + worldName + "' not found for region in " + datcFile.getName());
                }
            }
        }).thenCompose(v -> {

            if (!isBlockDataLoaded && datcFile != null) {
                try {
                    isLoading = true;
                    return loadFromDatc(datcFile).whenComplete((result, ex) -> {
                        isLoading = false;
                        if (ex != null) {
                            LOGGER.severe("[ArenaRegen] Exception during loadFromDatc for " + datcFile.getName() + ": " + ex.getMessage());
                            ex.printStackTrace();
                        }
                    });
                } catch (Exception e) {
                    isLoading = false;
                    LOGGER.severe("[ArenaRegen] Exception during loadFromDatc for " + datcFile.getName() + ": " + e.getMessage());
                    e.printStackTrace();
                    throw new RuntimeException("Failed to load region data", e);
                }
            } else {
                return CompletableFuture.completedFuture(null);
            }
        }).whenComplete((v, ex) -> {
            if (ex != null) {
                blockDataLoadFuture = null;
            }
        });
        return blockDataLoadFuture;
    }

    public CompletableFuture<Map<String, Map<Location, BlockData>>> getSectionedBlockData() {
        return ensureBlockDataLoaded().thenApply(v -> sectionedBlockData);
    }

    public CompletableFuture<Map<Location, BlockData>> getAllBlocks() {
        return ensureBlockDataLoaded().thenApply(v -> {
            Map<Location, BlockData> allBlocks = new ConcurrentHashMap<>();
            for (Map.Entry<String, Map<Location, BlockData>> entry : sectionedBlockData.entrySet()) {
                allBlocks.putAll(entry.getValue());
            }
            return allBlocks;
        });
    }

    public boolean isLocked() {
        return locked;
    }

    public String getCreator() { return creator; }
    public long getCreationDate() { return creationDate; }
    public String getWorldName() { return worldName; }
    public String getMinecraftVersion() { return minecraftVersion; }
    public String getFileFormatVersion() { return fileFormatVersion; }
    public int getMinX() { return minX; }
    public int getMinY() { return minY; }
    public int getMinZ() { return minZ; }
    public int getMaxX() { return minX + width - 1; }
    public int getMaxY() { return minY + height - 1; }
    public int getMaxZ() { return minZ + depth - 1; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getDepth() { return depth; }
    public File getDatcFile() { return datcFile; }
    public long getArea() {
        return (long) width * height * depth;
    }

    public boolean isBlockDataLoaded() {
        return isBlockDataLoaded;
    }
    
    public void setBlockDataLoaded(boolean loaded) {
        this.isBlockDataLoaded = loaded;
    }
}