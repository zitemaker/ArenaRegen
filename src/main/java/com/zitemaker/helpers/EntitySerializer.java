package com.zitemaker.helpers;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

public final class EntitySerializer {

    private static final Logger LOGGER = Bukkit.getLogger();
    private static final Attribute MAX_HEALTH_ATTRIBUTE = initializeMaxHealthAttribute();
    private static final Map<EntityType, EntitySerializerHandler> SERIALIZER_REGISTRY =
            new ConcurrentHashMap<>();

    static {
        registerSerializer(EntityType.VILLAGER, EntitySerializer::serializeVillager, EntitySerializer::deserializeVillager);
        registerSerializer(EntityType.ZOMBIE, EntitySerializer::serializeZombie, EntitySerializer::deserializeZombie);
    }

    @FunctionalInterface
    private interface EntitySerializerHandler {
        void process(Entity entity, Map<String, Object> data, boolean serialize);
    }

    public static void registerSerializer(EntityType type, BiConsumer<Entity, Map<String, Object>> serializer,
                                          BiConsumer<Entity, Map<String, Object>> deserializer) {
        SERIALIZER_REGISTRY.put(type, (entity, data, isSerialize) ->
                (isSerialize ? serializer : deserializer).accept(entity, data));
    }

    private static Attribute initializeMaxHealthAttribute() {
        return Arrays.stream(Attribute.values())
                .filter(attr -> attr.name().contains("MAX_HEALTH"))
                .findFirst()
                .orElseGet(() -> {
                    LOGGER.severe("Could not determine max health attribute. Entity health may not be preserved.");
                    return null;
                });
    }

    public static Map<String, Object> serializeEntity(Entity entity) {
        if (entity == null || entity.getType() == EntityType.PLAYER) {
            return null;
        }

        Map<String, Object> data = new HashMap<>(16);
        Location loc = entity.getLocation();
        data.put("type", entity.getType().name());
        data.putAll(serializeLocation(loc));
        data.put("velocity", serializeVector(entity.getVelocity()));

        if (entity.getCustomName() != null) {
            data.put("customName", entity.getCustomName());
            data.put("customNameVisible", entity.isCustomNameVisible());
        }

        if (entity instanceof LivingEntity livingEntity) {
            serializeLivingEntity(livingEntity, data);
        }

        SERIALIZER_REGISTRY.getOrDefault(entity.getType(), (e, d, s) -> {})
                .process(entity, data, true);

        return data.isEmpty() ? null : Collections.unmodifiableMap(data);
    }

    public static Entity deserializeEntity(Map<String, Object> data, Location location) {
        if (data == null || location == null || !data.containsKey("type") ||
                !(data.get("type") instanceof String typeStr)) {
            LOGGER.warning("Invalid entity data: " + data);
            return null;
        }

        try {
            EntityType type = EntityType.valueOf(typeStr);
            World world = location.getWorld();
            if (world == null) return null;

            Entity entity = world.spawnEntity(location, type);
            location.setYaw(getFloat(data, "yaw", 0.0f));
            location.setPitch(getFloat(data, "pitch", 0.0f));
            entity.teleport(location);

            if (data.containsKey("velocity")) {
                entity.setVelocity(deserializeVector(data.get("velocity")));
            }

            if (data.containsKey("customName") && data.get("customName") instanceof String customName) {
                entity.setCustomName(customName);
                entity.setCustomNameVisible(getBoolean(data, "customNameVisible", false));
            }

            if (entity instanceof LivingEntity livingEntity) {
                deserializeLivingEntity(livingEntity, data);
            }

            SERIALIZER_REGISTRY.getOrDefault(type, (e, d, s) -> {})
                    .process(entity, data, false);

            return entity;
        } catch (Exception e) {
            LOGGER.warning("Deserialization failed for type '" + typeStr + "': " + e.getMessage());
            return null;
        }
    }

    private static Map<String, Object> serializeLocation(Location loc) {
        return Map.of(
                "x", loc.getX(),
                "y", loc.getY(),
                "z", loc.getZ(),
                "yaw", loc.getYaw(),
                "pitch", loc.getPitch()
        );
    }

    private static double getDouble(Map<String, Object> data, String key, double defaultValue) {
        return data.getOrDefault(key, defaultValue) instanceof Number n ? n.doubleValue() : defaultValue;
    }

    private static float getFloat(Map<String, Object> data, String key, float defaultValue) {
        return data.getOrDefault(key, defaultValue) instanceof Number n ? n.floatValue() : defaultValue;
    }

    private static boolean getBoolean(Map<String, Object> data, String key, boolean defaultValue) {
        return data.getOrDefault(key, defaultValue) instanceof Boolean b ? b : defaultValue;
    }

    private static int getInt(Map<String, Object> data, String key, int defaultValue) {
        return data.getOrDefault(key, defaultValue) instanceof Number n ? n.intValue() : defaultValue;
    }

    private static Map<String, Object> serializeVector(Vector vector) {
        return vector == null ? Map.of() : Map.of(
                "x", vector.getX(),
                "y", vector.getY(),
                "z", vector.getZ()
        );
    }

    @SuppressWarnings("unchecked")
    private static Vector deserializeVector(Object data) {
        if (!(data instanceof Map<?, ?> rawMap)) return new Vector();
        Map<String, Object> map = new HashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (entry.getKey() instanceof String key) {
                map.put(key, entry.getValue());
            }
        }
        return new Vector(
                getDouble(map, "x", 0.0),
                getDouble(map, "y", 0.0),
                getDouble(map, "z", 0.0)
        );
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> serializeItemStack(ItemStack item) {
        return item != null && item.getType() != Material.AIR ? Map.of(
                "type", item.getType().name(),
                "amount", item.getAmount()
        ) : null;
    }

    @SuppressWarnings("unchecked")
    private static ItemStack deserializeItemStack(Object data) {
        if (!(data instanceof Map<?, ?> rawMap)) return null;
        Map<String, Object> map = new HashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (entry.getKey() instanceof String key) {
                map.put(key, entry.getValue());
            }
        }
        String typeStr = (String) map.get("type");
        Material material = Material.getMaterial(typeStr);
        return material != null ? new ItemStack(material, getInt(map, "amount", 1)) : null;
    }

    private static List<Map<String, Object>> serializePotionEffects(Collection<PotionEffect> effects) {
        return effects.stream()
                .filter(Objects::nonNull)
                .map(effect -> {
                    Map<String, Object> effectData = new HashMap<>();
                    effectData.put("type", effect.getType().getName());
                    effectData.put("duration", effect.getDuration());
                    effectData.put("amplifier", effect.getAmplifier());
                    effectData.put("ambient", effect.isAmbient());
                    effectData.put("particles", effect.hasParticles());
                    return effectData;
                })
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static void deserializePotionEffects(LivingEntity entity, List<?> effectsData) {
        effectsData.stream()
                .filter(obj -> obj instanceof Map<?, ?>)
                .map(obj -> {
                    Map<String, Object> map = new HashMap<>();
                    for (Map.Entry<?, ?> entry : ((Map<?, ?>) obj).entrySet()) {
                        if (entry.getKey() instanceof String key) {
                            map.put(key, entry.getValue());
                        }
                    }
                    return map;
                })
                .forEach(map -> {
                    try {
                        String typeStr = (String) map.get("type");
                        PotionEffectType type = PotionEffectType.getByName(typeStr);
                        if (type == null) return;
                        entity.addPotionEffect(new PotionEffect(
                                type,
                                getInt(map, "duration", 0),
                                getInt(map, "amplifier", 0),
                                getBoolean(map, "ambient", false),
                                getBoolean(map, "particles", true)
                        ));
                    } catch (Exception e) {
                        LOGGER.warning("Potion effect deserialization error: " + e.getMessage());
                    }
                });
    }

    private static void serializeLivingEntity(LivingEntity livingEntity, Map<String, Object> data) {
        if (MAX_HEALTH_ATTRIBUTE != null) {
            double health = livingEntity.getHealth();
            if (health > 0) data.put("health", health);
        }

        Collection<PotionEffect> effects = livingEntity.getActivePotionEffects();
        if (!effects.isEmpty()) data.put("potionEffects", serializePotionEffects(effects));

        EntityEquipment equipment = livingEntity.getEquipment();
        if (equipment != null) {
            data.put("equipment", Map.of(
                    "helmet", serializeItemStack(equipment.getHelmet()),
                    "chestplate", serializeItemStack(equipment.getChestplate()),
                    "leggings", serializeItemStack(equipment.getLeggings()),
                    "boots", serializeItemStack(equipment.getBoots()),
                    "mainHand", serializeItemStack(equipment.getItemInMainHand()),
                    "offHand", serializeItemStack(equipment.getItemInOffHand())
            ));
        }
    }

    @SuppressWarnings("unchecked")
    private static void deserializeLivingEntity(LivingEntity livingEntity, Map<String, Object> data) {
        if (MAX_HEALTH_ATTRIBUTE != null && data.containsKey("health")) {
            double health = getDouble(data, "health", 0.0);
            livingEntity.setHealth(Math.min(health, livingEntity.getAttribute(MAX_HEALTH_ATTRIBUTE).getValue()));
        }

        if (data.containsKey("potionEffects")) {
            deserializePotionEffects(livingEntity, (List<?>) data.get("potionEffects"));
        }

        if (data.containsKey("equipment") && data.get("equipment") instanceof Map<?, ?> equipmentData) {
            EntityEquipment equipment = livingEntity.getEquipment();
            if (equipment != null) {
                Map<String, Object> equipMap = new HashMap<>();
                for (Map.Entry<?, ?> entry : equipmentData.entrySet()) {
                    if (entry.getKey() instanceof String key) {
                        equipMap.put(key, entry.getValue());
                    }
                }
                equipment.setHelmet(deserializeItemStack(equipMap.get("helmet")));
                equipment.setChestplate(deserializeItemStack(equipMap.get("chestplate")));
                equipment.setLeggings(deserializeItemStack(equipMap.get("leggings")));
                equipment.setBoots(deserializeItemStack(equipMap.get("boots")));
                equipment.setItemInMainHand(deserializeItemStack(equipMap.get("mainHand")));
                equipment.setItemInOffHand(deserializeItemStack(equipMap.get("offHand")));
            }
        }
    }

    private static void serializeVillager(Entity entity, Map<String, Object> data) {
        Villager villager = (Villager) entity;
        data.put("profession", villager.getProfession().name());
        data.put("villagerType", villager.getVillagerType().name());
        data.put("level", villager.getVillagerLevel());
    }

    private static void deserializeVillager(Entity entity, Map<String, Object> data) {
        Villager villager = (Villager) entity;
        Optional.ofNullable((String) data.get("profession"))
                .map(prof -> {
                    try {
                        return Villager.Profession.valueOf(prof);
                    } catch (IllegalArgumentException e) {
                        LOGGER.warning("Invalid profession: " + prof);
                        return null;
                    }
                })
                .ifPresent(villager::setProfession);
        Optional.ofNullable((String) data.get("villagerType"))
                .map(type -> {
                    try {
                        return Villager.Type.valueOf(type);
                    } catch (IllegalArgumentException e) {
                        LOGGER.warning("Invalid villager type: " + type);
                        return null;
                    }
                })
                .ifPresent(villager::setVillagerType);
        Optional.ofNullable(data.get("level"))
                .ifPresent(level -> villager.setVillagerLevel(getInt(data, "level", 1)));
    }

    private static void serializeZombie(Entity entity, Map<String, Object> data) {
        Zombie zombie = (Zombie) entity;
        data.put("isBaby", zombie.isBaby());
    }

    private static void deserializeZombie(Entity entity, Map<String, Object> data) {
        Zombie zombie = (Zombie) entity;
        Optional.ofNullable(data.get("isBaby"))
                .ifPresent(baby -> zombie.setBaby(getBoolean(data, "isBaby", false)));
    }
}