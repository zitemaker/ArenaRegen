package com.zitemaker.helpers;

import com.zitemaker.ArenaRegen;
import org.bukkit.*;
import org.bukkit.block.BlockState;
import org.bukkit.block.Banner;
import org.bukkit.block.Sign;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.util.Vector;

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
    private static final byte[] GZIP_MAGIC = new byte[]{(byte) 0x1F, (byte) 0x8B};

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
    private volatile Location spawnLocation;
    private volatile boolean locked = false;
    private volatile boolean isBlockDataLoaded = false;
    private volatile File datcFile;
    private volatile boolean loadFailed = false;
    private final Object loadLock = new Object();
    private volatile CompletableFuture<Void> loadingFuture = null;

    public RegionData(ArenaRegen plugin) {
        this.plugin = plugin;
    }

    public CompletableFuture<Void> setDatcFile(File datcFile) {
        return CompletableFuture.runAsync(() -> this.datcFile = datcFile,
                runnable -> Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    public CompletableFuture<Void> setSpawnLocation(Location location) {
        return CompletableFuture.runAsync(() -> this.spawnLocation = location != null ? location.clone() : null,
                runnable -> Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    public Location getSpawnLocation() {
        return spawnLocation != null ? spawnLocation.clone() : null;
    }

    public CompletableFuture<Void> addBlockToSection(String section, Location location, BlockData blockData) {
        Objects.requireNonNull(section, "section");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(blockData, "blockData");
        return CompletableFuture.runAsync(() -> {
            sectionedBlockData.computeIfAbsent(section, k -> new ConcurrentHashMap<>()).put(location.clone(), blockData);
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
        }, runnable -> Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    public CompletableFuture<Void> addSection(String sectionName, Map<Location, BlockData> blocks) {
        Objects.requireNonNull(sectionName, "sectionName");
        Objects.requireNonNull(blocks, "blocks");
        return CompletableFuture.runAsync(() -> {
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
        }, runnable -> Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable));
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

    public CompletableFuture<Void> setMetadata(String creator, long creationDate, String world, String version, int minX, int minY, int minZ, int width, int height, int depth) {
        return CompletableFuture.runAsync(() -> {
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
        }, runnable -> Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    public CompletableFuture<Void> markBlockModified(Location location, BlockData newBlockData) {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(newBlockData, "newBlockData");
        return ensureBlockDataLoaded().thenCompose(v -> getAllBlocks())
                .thenAccept(allBlocks -> {
                    World arenaWorld = Bukkit.getWorld(worldName);
                    if (arenaWorld == null) return;
                    Location normalizedLoc = new Location(arenaWorld, location.getBlockX(), location.getBlockY(), location.getBlockZ());
                    BlockData original = allBlocks.get(normalizedLoc);
                    if (original != null && !original.getMaterial().equals(newBlockData.getMaterial())) {
                        modifiedBlocks.put(normalizedLoc, original);
                    }
                });
    }

    public CompletableFuture<Void> clearModifiedBlocks() {
        return CompletableFuture.runAsync(() -> modifiedBlocks.clear(),
                runnable -> Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    public CompletableFuture<Map<Location, BlockData>> getModifiedBlocks() {
        return ensureBlockDataLoaded().thenApply(v -> Collections.unmodifiableMap(new HashMap<>(modifiedBlocks)));
    }

    public CompletableFuture<Void> addEntity(Location location, Map<String, Object> serializedEntity) {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(serializedEntity, "serializedEntity");
        return CompletableFuture.runAsync(() -> {
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
                } else if (value instanceof Vector) {
                    Vector vec = (Vector) value;
                    serializableEntity.put(key, vec.getX() + "," + vec.getY() + "," + vec.getZ());
                } else if (value instanceof Serializable) {
                    serializableEntity.put(key, value);
                } else {
                    LOGGER.warning("[ArenaRegen] Non-serializable value at " + normalizedLoc + " for key " + key + ", replacing with null.");
                    serializableEntity.put(key, null);
                }
            }
            entityDataMap.put(normalizedLoc, serializableEntity);
        }, runnable -> Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    public CompletableFuture<Map<Location, Map<String, Object>>> getEntityDataMap() {
        return ensureBlockDataLoaded().thenApply(v -> Collections.unmodifiableMap(new HashMap<>(entityDataMap)));
    }

    public CompletableFuture<Void> clearEntities() {
        return CompletableFuture.runAsync(() -> entityDataMap.clear(),
                runnable -> Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    public CompletableFuture<Map<Location, Map<String, Object>>> getBannerStates() {
        return ensureBlockDataLoaded().thenApply(v -> Collections.unmodifiableMap(new HashMap<>(bannerStates)));
    }

    public CompletableFuture<Void> clearBanners() {
        return CompletableFuture.runAsync(() -> bannerStates.clear(),
                runnable -> Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    public CompletableFuture<Map<Location, Map<String, Object>>> getSignStates() {
        return ensureBlockDataLoaded().thenApply(v -> Collections.unmodifiableMap(new HashMap<>(signStates)));
    }

    public CompletableFuture<Void> clearSigns() {
        return CompletableFuture.runAsync(() -> signStates.clear(),
                runnable -> Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable));
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
            plugin.dirtyRegions.remove(regionName);

            if (datcFile != null) {
                if (datcFile.exists()) {
                    if (datcFile.delete()) {
                        LOGGER.info("[ArenaRegen] Successfully deleted arena file: " + datcFile.getPath());
                    } else {
                        LOGGER.warning("[ArenaRegen] Failed to delete arena file: " + datcFile.getPath());
                    }
                }

                File backupFile = new File(datcFile.getParent(), datcFile.getName() + ".bak");
                if (backupFile.exists()) {
                    if (backupFile.delete()) {
                        LOGGER.info("[ArenaRegen] Successfully deleted backup file: " + backupFile.getPath());
                    } else {
                        LOGGER.warning("[ArenaRegen] Failed to delete backup file: " + backupFile.getPath());
                    }
                }
            }

            File schematicFile = new File(plugin.getDataFolder(), "arenas/" + regionName + ".schem");
            if (schematicFile.exists()) {
                if (schematicFile.delete()) {
                    LOGGER.info("[ArenaRegen] Successfully deleted schematic file: " + schematicFile.getPath());
                } else {
                    LOGGER.warning("[ArenaRegen] Failed to delete schematic file: " + schematicFile.getPath());
                }
            }

            LOGGER.info("[ArenaRegen] Arena '" + regionName + "' has been fully removed from memory and disk.");
        }, runnable -> Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    private int calculateBufferSize() {
        long totalBlocks = sectionedBlockData.values().stream().mapToLong(Map::size).sum();
        long totalEntities = entityDataMap.size();
        long totalModifiedBlocks = modifiedBlocks.size();
        long totalBanners = bannerStates.size();
        long totalSigns = signStates.size();

        long estimatedSize = (totalBlocks * 42) + (totalEntities * 124) + (totalModifiedBlocks * 42) + (totalBanners * 128) + (totalSigns * 64) + 1024;

        return estimatedSize < 500_000 ? 4096 : (estimatedSize < 5_000_000 ? 16384 : 32768);
    }

    public CompletableFuture<Void> saveToDatc(File datcFile) {
        return ensureBlockDataLoaded().thenCompose(v -> CompletableFuture.runAsync(() -> {
            long startTime = System.currentTimeMillis();
            int bufferSize = calculateBufferSize();

            File backupFile = new File(datcFile.getParent(), datcFile.getName() + ".bak");
            if (datcFile.exists() && !datcFile.renameTo(backupFile)) {
                LOGGER.warning("[ArenaRegen] Failed to create backup of " + datcFile.getName());
                return;
            }

            try (FileOutputStream fos = new FileOutputStream(datcFile);
                 BufferedOutputStream bos = new BufferedOutputStream(fos, bufferSize);
                 GZIPOutputStream gzip = new GZIPOutputStream(bos) {{
                     def.setLevel(GZIP_COMPRESSION_LEVEL);
                 }};
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

                writeSections(dos, sectionedBlockData);
                writeEntities(dos, entityDataMap);
                writeBanners(dos, bannerStates);
                writeSigns(dos, signStates);
                writeModifiedBlocks(dos, modifiedBlocks);
            } catch (IOException e) {
                if (backupFile.exists()) {
                    if (datcFile.exists()) datcFile.delete();
                    backupFile.renameTo(datcFile);
                }
                LOGGER.severe("[ArenaRegen] Save failed: " + e.getMessage());
                throw new RuntimeException(e);
            }

            long timeTaken = System.currentTimeMillis() - startTime;
            long fileSize = datcFile.length();
            long totalBlocks = sectionedBlockData.values().stream().mapToLong(Map::size).sum();
            LOGGER.info("[ArenaRegen] Saved RegionData to " + datcFile.getName() + ": " +
                    sectionedBlockData.size() + " sections, " + totalBlocks + " total blocks, " +
                    entityDataMap.size() + " entities, " + bannerStates.size() + " banners, " + signStates.size() + " signs, " +
                    modifiedBlocks.size() + " modified blocks. " +
                    "File size: " + (fileSize / 1024) + " KB, Time: " + timeTaken + "ms");
        }, runnable -> Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable)));
    }

    private void writeSections(DataOutputStream dos, Map<String, Map<Location, BlockData>> sectionedBlockData) throws IOException {
        dos.writeInt(sectionedBlockData.size());
        for (Map.Entry<String, Map<Location, BlockData>> entry : sectionedBlockData.entrySet()) {
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

    private void writeEntities(DataOutputStream dos, Map<Location, Map<String, Object>> entityDataMap) throws IOException {
        dos.writeInt(entityDataMap.size());
        int batchSize = 100;
        int count = 0;
        for (Map.Entry<Location, Map<String, Object>> entry : entityDataMap.entrySet()) {
            Location loc = entry.getKey();
            Map<String, Object> serializedEntity = entry.getValue();

            dos.writeDouble(loc.getX());
            dos.writeDouble(loc.getY());
            dos.writeDouble(loc.getZ());

            ByteArrayOutputStream entityStream = new ByteArrayOutputStream();
            try (ObjectOutputStream oos = new ObjectOutputStream(entityStream)) {
                Map<String, Object> serializableEntity = new HashMap<>();
                for (Map.Entry<String, Object> dataEntry : serializedEntity.entrySet()) {
                    String key = dataEntry.getKey();
                    Object value = dataEntry.getValue();
                    if (value instanceof ItemStack) {
                        ItemStack item = (ItemStack) value;
                        serializableEntity.put(key, item.getType().name() + ":" + item.getAmount());
                    } else if (value instanceof Vector) {
                        Vector vec = (Vector) value;
                        serializableEntity.put(key, vec.getX() + "," + vec.getY() + "," + vec.getZ());
                    } else if (value instanceof Serializable) {
                        serializableEntity.put(key, value);
                    } else {
                        LOGGER.warning("[ArenaRegen] Non-serializable value at " + loc + " for key " + key + ", replacing with null.");
                        serializableEntity.put(key, null);
                    }
                }
                oos.writeObject(serializableEntity);
            } catch (IOException e) {
                LOGGER.warning("[ArenaRegen] IO error serializing entity at " + loc + ": " + e.getMessage() + ", replacing with empty map.");
                Map<String, Object> emptyEntity = new HashMap<>();
                ByteArrayOutputStream fallbackStream = new ByteArrayOutputStream();
                try (ObjectOutputStream fallbackOos = new ObjectOutputStream(fallbackStream)) {
                    fallbackOos.writeObject(emptyEntity);
                }
                entityStream = fallbackStream;
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

    private void writeBanners(DataOutputStream dos, Map<Location, Map<String, Object>> bannerStates) throws IOException {
        dos.writeInt(bannerStates.size());
        int batchSize = 100;
        int count = 0;
        for (Map.Entry<Location, Map<String, Object>> entry : bannerStates.entrySet()) {
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
                dos.writeInt(patternDataList.size());
                for (Map<String, String> patternData : patternDataList) {
                    dos.writeUTF(patternData.get("color"));
                    dos.writeUTF(patternData.get("type"));
                }
            } else {
                dos.writeInt(0);
            }

            if (bannerData.containsKey("persistentData")) {
                dos.writeBoolean(true);
                Map<String, Object> pdcData = (Map<String, Object>) bannerData.get("persistentData");
                ByteArrayOutputStream pdcStream = new ByteArrayOutputStream();
                try (ObjectOutputStream oos = new ObjectOutputStream(pdcStream)) {
                    oos.writeObject(pdcData);
                }
                byte[] pdcBytes = pdcStream.toByteArray();
                dos.writeInt(pdcBytes.length);
                dos.write(pdcBytes);
            } else {
                dos.writeBoolean(false);
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

    private void writeSigns(DataOutputStream dos, Map<Location, Map<String, Object>> signStates) throws IOException {
        dos.writeInt(signStates.size());
        int batchSize = 100;
        int count = 0;
        for (Map.Entry<Location, Map<String, Object>> entry : signStates.entrySet()) {
            Location loc = entry.getKey();
            Map<String, Object> signData = entry.getValue();

            dos.writeDouble(loc.getX());
            dos.writeDouble(loc.getY());
            dos.writeDouble(loc.getZ());

            List<String> lines = (List<String>) signData.get("lines");
            for (String line : lines) {
                dos.writeUTF(line);
            }
            dos.writeUTF((String) signData.get("color"));
            dos.writeBoolean((Boolean) signData.get("glowing"));

            if (signData.containsKey("persistentData")) {
                dos.writeBoolean(true);
                Map<String, Object> pdcData = (Map<String, Object>) signData.get("persistentData");
                ByteArrayOutputStream pdcStream = new ByteArrayOutputStream();
                try (ObjectOutputStream oos = new ObjectOutputStream(pdcStream)) {
                    oos.writeObject(pdcData);
                }
                byte[] pdcBytes = pdcStream.toByteArray();
                dos.writeInt(pdcBytes.length);
                dos.write(pdcBytes);
            } else {
                dos.writeBoolean(false);
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

    private void writeModifiedBlocks(DataOutputStream dos, Map<Location, BlockData> modifiedBlocks) throws IOException {
        dos.writeInt(modifiedBlocks.size());
        int batchSize = 1000;
        int count = 0;
        for (Map.Entry<Location, BlockData> entry : modifiedBlocks.entrySet()) {
            Location loc = entry.getKey();
            BlockData blockData = entry.getValue();
            dos.writeInt(loc.getBlockX());
            dos.writeInt(loc.getBlockY());
            dos.writeInt(loc.getBlockZ());
            dos.writeUTF(blockData.getAsString());
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
        synchronized (loadLock) {
            if (loadingFuture != null && !loadingFuture.isDone()) {
                return loadingFuture;
            }
            if (isBlockDataLoaded) {
                return CompletableFuture.completedFuture(null);
            }
            this.datcFile = datcFile;
            loadFailed = false;

            loadingFuture = CompletableFuture.runAsync(() -> {
                if (!datcFile.exists()) {
                    LOGGER.warning("[ArenaRegen] .datc file not found: " + datcFile.getPath());
                    loadFailed = true;
                    return;
                }
                long startTime = System.currentTimeMillis();
                try (FileInputStream fis = new FileInputStream(datcFile);
                     BufferedInputStream bis = new BufferedInputStream(fis, 16384);
                     GZIPInputStream gzip = new GZIPInputStream(bis);
                     DataInputStream dis = new DataInputStream(gzip)) {

                    String header = dis.readLine();
                    if (header == null) {
                        throw new IOException("Empty or invalid header in .datc file.");
                    }
                    String[] parts = header.split(",");
                    if (parts.length < 12) {
                        throw new IOException("Invalid header format. Expected at least 12 parts, got " + parts.length);
                    }

                    fileFormatVersion = parts[0];
                    creator = parts[1];
                    creationDate = Long.parseLong(parts[2]);
                    worldName = parts[3];
                    minecraftVersion = parts[4];
                    minX = Integer.parseInt(parts[5]);
                    minY = Integer.parseInt(parts[6]);
                    minZ = Integer.parseInt(parts[7]);
                    width = Integer.parseInt(parts[8]);
                    height = Integer.parseInt(parts[9]);
                    depth = Integer.parseInt(parts[10]);

                    if (parts.length >= 16) {
                        double spawnX = Double.parseDouble(parts[11]);
                        double spawnY = Double.parseDouble(parts[12]);
                        double spawnZ = Double.parseDouble(parts[13]);
                        float spawnYaw = Float.parseFloat(parts[14]);
                        float spawnPitch = Float.parseFloat(parts[15]);
                        spawnLocation = new Location(Bukkit.getWorld(worldName), spawnX, spawnY, spawnZ, spawnYaw, spawnPitch);
                    }
                    if (parts.length >= 17) {
                        locked = Boolean.parseBoolean(parts[16]);
                    }

                    readSections(dis, sectionedBlockData);
                    readEntities(dis, entityDataMap);
                    readBanners(dis, bannerStates);
                    readSigns(dis, signStates);
                    readModifiedBlocks(dis, modifiedBlocks);

                    isBlockDataLoaded = true;
                } catch (EOFException e) {
                    LOGGER.severe("[ArenaRegen] Unexpected EOF in .datc file, likely corrupted or incomplete: " + datcFile.getPath());
                    loadFailed = true;
                    throw new RuntimeException("Incomplete .datc file.", e);
                } catch (IOException | NumberFormatException e) {
                    LOGGER.severe("[ArenaRegen] Failed to load RegionData from " + datcFile.getPath() + ": " + e.getMessage());
                    loadFailed = true;
                    throw new RuntimeException(e);
                } finally {
                    long timeTaken = System.currentTimeMillis() - startTime;
                    long totalBlocks = sectionedBlockData.values().stream().mapToLong(Map::size).sum();
                    LOGGER.info("[ArenaRegen] Loaded RegionData from " + datcFile.getName() + ": " +
                            sectionedBlockData.size() + " sections, " + totalBlocks + " total blocks, " +
                            entityDataMap.size() + " entities, " + bannerStates.size() + " banners, " + signStates.size() + " signs, " +
                            modifiedBlocks.size() + " modified blocks. Time: " + timeTaken + "ms");
                }
            }, runnable -> Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable));
            return loadingFuture;
        }
    }

    private void readSections(DataInputStream dis, Map<String, Map<Location, BlockData>> sectionedBlockData) throws IOException {
        int numSections = dis.readInt();
        for (int i = 0; i < numSections; i++) {
            String sectionName = dis.readUTF();
            int numBlocks = dis.readInt();
            Map<Location, BlockData> blocks = new ConcurrentHashMap<>();
            World world = Bukkit.getWorld(worldName);
            for (int j = 0; j < numBlocks; j++) {
                int x = dis.readInt();
                int y = dis.readInt();
                int z = dis.readInt();
                String blockDataStr = dis.readUTF();
                try {
                    blocks.put(new Location(world, x, y, z), Bukkit.createBlockData(blockDataStr));
                } catch (IllegalArgumentException e) {
                    LOGGER.warning("[ArenaRegen] Failed to create block data from string '" + blockDataStr + "' for section " + sectionName + " at " + x + "," + y + "," + z + ". Replacing with AIR. Error: " + e.getMessage());
                    blocks.put(new Location(world, x, y, z), Bukkit.createBlockData(Material.AIR));
                }
            }
            sectionedBlockData.put(sectionName, blocks);
        }
    }

    private void readEntities(DataInputStream dis, Map<Location, Map<String, Object>> entityDataMap) throws IOException {
        int numEntities = dis.readInt();
        World world = Bukkit.getWorld(worldName);
        for (int i = 0; i < numEntities; i++) {
            double x = dis.readDouble();
            double y = dis.readDouble();
            double z = dis.readDouble();
            int dataLength = dis.readInt();
            byte[] entityDataBytes = new byte[dataLength];
            dis.readFully(entityDataBytes);

            try (ByteArrayInputStream bis = new ByteArrayInputStream(entityDataBytes);
                 ObjectInputStream ois = new ObjectInputStream(bis)) {
                @SuppressWarnings("unchecked")
                Map<String, Object> serializedEntity = (Map<String, Object>) ois.readObject();

                Map<String, Object> deserializedEntity = new HashMap<>();
                for (Map.Entry<String, Object> entry : serializedEntity.entrySet()) {
                    String key = entry.getKey();
                    Object value = entry.getValue();
                    if (value instanceof String) {
                        String strValue = (String) value;
                        if (strValue.contains(":") && Material.matchMaterial(strValue.split(":")[0]) != null) {
                            String[] parts = strValue.split(":");
                            deserializedEntity.put(key, new ItemStack(Material.valueOf(parts[0]), Integer.parseInt(parts[1])));
                        } else if (strValue.contains(",")) {
                            String[] parts = strValue.split(",");
                            if (parts.length == 3) {
                                try {
                                    deserializedEntity.put(key, new Vector(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2])));
                                } catch (NumberFormatException e) {
                                    LOGGER.warning("[ArenaRegen] Invalid vector format for key " + key + ": " + strValue + ", replacing with null.");
                                    deserializedEntity.put(key, null);
                                }
                            } else {
                                deserializedEntity.put(key, value);
                            }
                        } else {
                            deserializedEntity.put(key, value);
                        }
                    } else {
                        deserializedEntity.put(key, value);
                    }
                }
                entityDataMap.put(new Location(world, x, y, z), deserializedEntity);
            } catch (ClassNotFoundException | IOException e) {
                LOGGER.warning("[ArenaRegen] Failed to deserialize entity data at " + x + "," + y + "," + z + ": " + e.getMessage());
            }
        }
    }

    private void readBanners(DataInputStream dis, Map<Location, Map<String, Object>> bannerStates) throws IOException {
        int numBanners = dis.readInt();
        World world = Bukkit.getWorld(worldName);
        for (int i = 0; i < numBanners; i++) {
            double x = dis.readDouble();
            double y = dis.readDouble();
            double z = dis.readDouble();
            byte baseColorByte = dis.readByte();
            DyeColor baseColor = baseColorByte == -1 ? null : DyeColor.values()[baseColorByte];

            int numPatterns = dis.readInt();
            List<Map<String, String>> patternDataList = new ArrayList<>();
            for (int j = 0; j < numPatterns; j++) {
                Map<String, String> patternData = new HashMap<>();
                patternData.put("color", dis.readUTF());
                patternData.put("type", dis.readUTF());
                patternDataList.add(patternData);
            }

            Map<String, Object> bannerData = new HashMap<>();
            bannerData.put("baseColor", baseColor != null ? baseColor.name() : "NONE");
            bannerData.put("patterns", patternDataList);

            boolean hasPdc = dis.readBoolean();
            if (hasPdc) {
                int pdcLength = dis.readInt();
                byte[] pdcBytes = new byte[pdcLength];
                dis.readFully(pdcBytes);
                try (ByteArrayInputStream bis = new ByteArrayInputStream(pdcBytes);
                     ObjectInputStream ois = new ObjectInputStream(bis)) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> pdcData = (Map<String, Object>) ois.readObject();
                    bannerData.put("persistentData", pdcData);
                } catch (ClassNotFoundException | IOException e) {
                    LOGGER.warning("[ArenaRegen] Failed to deserialize PDC for banner at " + x + "," + y + "," + z + ": " + e.getMessage());
                }
            }

            bannerStates.put(new Location(world, x, y, z), bannerData);
        }
    }

    private void readSigns(DataInputStream dis, Map<Location, Map<String, Object>> signStates) throws IOException {
        int numSigns = dis.readInt();
        World world = Bukkit.getWorld(worldName);
        for (int i = 0; i < numSigns; i++) {
            double x = dis.readDouble();
            double y = dis.readDouble();
            double z = dis.readDouble();

            List<String> lines = new ArrayList<>();
            for (int j = 0; j < 4; j++) {
                lines.add(dis.readUTF());
            }
            String colorStr = dis.readUTF();
            boolean glowing = dis.readBoolean();

            Map<String, Object> signData = new HashMap<>();
            signData.put("lines", lines);
            signData.put("color", colorStr);
            signData.put("glowing", glowing);

            // Read PersistentDataContainer
            boolean hasPdc = dis.readBoolean();
            if (hasPdc) {
                int pdcLength = dis.readInt();
                byte[] pdcBytes = new byte[pdcLength];
                dis.readFully(pdcBytes);
                try (ByteArrayInputStream bis = new ByteArrayInputStream(pdcBytes);
                     ObjectInputStream ois = new ObjectInputStream(bis)) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> pdcData = (Map<String, Object>) ois.readObject();
                    signData.put("persistentData", pdcData);
                } catch (ClassNotFoundException | IOException e) {
                    LOGGER.warning("[ArenaRegen] Failed to deserialize PDC for sign at " + x + "," + y + "," + z + ": " + e.getMessage());
                }
            }

            signStates.put(new Location(world, x, y, z), signData);
        }
    }

    private void readModifiedBlocks(DataInputStream dis, Map<Location, BlockData> modifiedBlocks) throws IOException {
        int numModifiedBlocks = dis.readInt();
        World world = Bukkit.getWorld(worldName);
        for (int i = 0; i < numModifiedBlocks; i++) {
            int x = dis.readInt();
            int y = dis.readInt();
            int z = dis.readInt();
            String blockDataStr = dis.readUTF();
            try {
                modifiedBlocks.put(new Location(world, x, y, z), Bukkit.createBlockData(blockDataStr));
            } catch (IllegalArgumentException e) {
                LOGGER.warning("[ArenaRegen] Failed to create block data from string '" + blockDataStr + "' for modified block at " + x + "," + y + "," + z + ". Replacing with AIR. Error: " + e.getMessage());
                modifiedBlocks.put(new Location(world, x, y, z), Bukkit.createBlockData(Material.AIR));
            }
        }
    }

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

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getDepth() {
        return depth;
    }

    public boolean isBlockDataLoaded() {
        return isBlockDataLoaded;
    }

    public CompletableFuture<Void> ensureBlockDataLoaded() {
        synchronized (loadLock) {
            if (isBlockDataLoaded) {
                return CompletableFuture.completedFuture(null);
            }
            if (loadFailed) {
                return CompletableFuture.failedFuture(new IllegalStateException("Failed to load region data previously."));
            }
            if (loadingFuture != null) {
                return loadingFuture;
            }

            loadingFuture = loadFromDatc(datcFile).whenComplete((v, ex) -> {
                synchronized (loadLock) {
                    if (ex != null) {
                        loadFailed = true;
                    }
                    loadingFuture = null;
                }
            });
            return loadingFuture;
        }
    }

    public CompletableFuture<Map<Location, BlockData>> getAllBlocks() {
        return ensureBlockDataLoaded().thenApply(v -> {
            Map<Location, BlockData> allBlocks = new HashMap<>();
            for (Map<Location, BlockData> section : sectionedBlockData.values()) {
                allBlocks.putAll(section);
            }
            return Collections.unmodifiableMap(allBlocks);
        });
    }

    public Map<String, Map<Location, BlockData>> getSectionedBlockData() {
        return Collections.unmodifiableMap(sectionedBlockData);
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
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

    public int getArea() {
        return width * height * depth;
    }
}