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

import java.io.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class RegionData {
    private static final Logger LOGGER = Bukkit.getLogger();
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

    private final ArenaRegen plugin;
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

            World world = location.getWorld();
            if (world != null) {
                Bukkit.getScheduler().runTask(plugin, () -> {
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
                });
            }
    }

    public void addSection(String sectionName, Map<Location, BlockData> blocks) {
            Map<Location, BlockData> sectionBlocks = new ConcurrentHashMap<>();
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                LOGGER.warning("[ArenaRegen] World '" + worldName + "' not found, cannot check for banners or signs in section " + sectionName);
            }

            for (Map.Entry<Location, BlockData> entry : blocks.entrySet()) {
                Location location = entry.getKey();
                BlockData blockData = entry.getValue();
                if (blockData == null) {
                    LOGGER.warning("[ArenaRegen] Block data in section " + sectionName + " at " + location + " is null, replacing with air.");
                    blockData = Bukkit.createBlockData(Material.AIR);
                }
                sectionBlocks.put(location, blockData);

                if (world != null) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
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
                    });
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
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(serializedEntity, "serializedEntity");

            World arenaWorld = Bukkit.getWorld(worldName);
            if (arenaWorld == null) return;

            Location normalizedLoc = new Location(arenaWorld, location.getX(), location.getY(), location.getZ());
            Map<String, Object> serializableEntity = new HashMap<>();

            for (Map.Entry<String, Object> entry : serializedEntity.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();

                if (value instanceof ItemStack) {
                    ItemStack item = (ItemStack) value;
                    serializableEntity.put(key, item.getType().name() + ":" + item.getAmount());

            } else if (value instanceof org.bukkit.util.Vector) {
                org.bukkit.util.Vector vec = (org.bukkit.util.Vector) value;
                    serializableEntity.put(key, vec.getX() + "," + vec.getY() + "," + vec.getZ());

            } else if (value instanceof java.util.Vector<?>) {
                java.util.Vector<?> vec = (java.util.Vector<?>) value;
                String joined = vec.stream()
                        .map(Object::toString)
                        .collect(java.util.stream.Collectors.joining(","));
                serializableEntity.put(key, joined);

                } else if (value instanceof Serializable) {
                    serializableEntity.put(key, value);

                } else {
                    LOGGER.warning("[ArenaRegen] Non-serializable value at " + normalizedLoc + " for key " + key + ", replacing with null.");
                    serializableEntity.put(key, null);
                }
            }

            entityDataMap.put(normalizedLoc, serializableEntity);
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

    public Map<Location, Map<String, Object>> getBannerStates() {
        try {
            ensureBlockDataLoaded();
        } catch (IOException e) {
            LOGGER.warning("[ArenaRegen] Failed to load block data for banner states: " + e.getMessage());
        }
        return new HashMap<>(bannerStates);
    }

    public void clearBanners() {
        bannerStates.clear();
    }

    public Map<Location, Map<String, Object>> getSignStates() {
        try {
            ensureBlockDataLoaded();
        } catch (IOException e) {
            LOGGER.warning("[ArenaRegen] Failed to load block data for sign states: " + e.getMessage());
        }
        return new HashMap<>(signStates);
    }

    public void clearSigns() {
        signStates.clear();
    }

    private String coordsToString(Location loc) {
        return loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }

    public CompletableFuture<Void> clearRegion(String regionName) {
        return CompletableFuture.runAsync(() -> {
            sectionedBlockData.clear();
            entityDataMap.clear();
            modifiedBlocks.clear();
            bannerStates.clear();
            signStates.clear();

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
        });
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

        Map<String, Map<Location, BlockData>> sectionedBlockDataCopy = new ConcurrentHashMap<>();
        for (Map.Entry<String, Map<Location, BlockData>> entry : sectionedBlockData.entrySet()) {
            sectionedBlockDataCopy.put(entry.getKey(), new ConcurrentHashMap<>(entry.getValue()));
        }
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
                LOGGER.warning("[ArenaRegen] Failed to create backup of " + datcFile.getName());
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


    private void writeSections(DataOutputStream dos, Map<String, Map<Location, BlockData>> sectionedBlockDataCopy) throws IOException {
        dos.writeInt(sectionedBlockDataCopy.size());
        for (Map.Entry<String, Map<Location, BlockData>> entry : sectionedBlockDataCopy.entrySet()) {
            String sectionName = entry.getKey();
            Map<Location, BlockData> blocks = entry.getValue();

            dos.writeUTF(sectionName);
            dos.writeInt(blocks.size());
            int batchSize = 1000;
            int count = 0;
            for (Map.Entry<Location, BlockData> blockEntry : blocks.entrySet()) {
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
            if (count % batchSize != 0) {
                dos.flush();
            }
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
            try (ObjectOutputStream oos = new ObjectOutputStream(entityStream)) {
                oos.writeObject(serializedEntity);
            }
            byte[] entityDataBytes = entityStream.toByteArray();
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
                            sectionedBlockData.size() + " sections, " + getAllBlocks().size() + " total blocks, " +
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
                loadFromDatc(datcFile).join();
            } catch (Exception e) {
                throw new IOException("Failed to load region data", e);
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
}