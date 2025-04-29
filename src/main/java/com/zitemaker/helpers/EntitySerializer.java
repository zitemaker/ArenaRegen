package com.zitemaker.helpers;

import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.bukkit.Bukkit;
import org.bukkit.Material;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.logging.Logger;


public class EntitySerializer {

    private static final Logger LOGGER = Logger.getLogger(EntitySerializer.class.getName());
    private static final Attribute MAX_HEALTH_ATTRIBUTE = initializeMaxHealthAttribute();
    private static final Map<EntityType, EntitySerializerHandler> SERIALIZER_REGISTRY = new HashMap<>();

    static {
        registerSerializer(EntityType.VILLAGER, EntitySerializer::serializeVillager, EntitySerializer::deserializeVillager);
        registerSerializer(EntityType.ZOMBIE, EntitySerializer::serializeZombie, EntitySerializer::deserializeZombie);

    }


    private interface EntitySerializerHandler {
        void serialize(Entity entity, Map<String, Object> data);
        void deserialize(Entity entity, Map<String, Object> data);
    }

    private static void registerSerializer(EntityType type, BiConsumer<Entity, Map<String, Object>> serializer,
                                           BiConsumer<Entity, Map<String, Object>> deserializer) {
        SERIALIZER_REGISTRY.put(type, new EntitySerializerHandler() {
            @Override
            public void serialize(Entity entity, Map<String, Object> data) {
                serializer.accept(entity, data);
            }

            @Override
            public void deserialize(Entity entity, Map<String, Object> data) {
                deserializer.accept(entity, data);
            }
        });
    }


    private static Attribute initializeMaxHealthAttribute() {
        try {
            return Attribute.valueOf("GENERIC_MAX_HEALTH");
        } catch (IllegalArgumentException e) {
            try {
                return Attribute.valueOf("MAX_HEALTH");
            } catch (IllegalArgumentException ex) {
                LOGGER.severe("Could not determine max health attribute. Entity health may not be preserved.");
                return null;
            }
        }
    }

    public static Map<String, Object> serializeEntity(Entity entity) {

        if (entity.getType() == EntityType.PLAYER) {
            return null;
        }

        Map<String, Object> data = new HashMap<>();

        data.put("type", entity.getType().name());
        Location loc = entity.getLocation();
        data.put("x", loc.getX());
        data.put("y", loc.getY());
        data.put("z", loc.getZ());
        data.put("yaw", loc.getYaw());
        data.put("pitch", loc.getPitch());
        data.put("velocity", serializeVector(entity.getVelocity()));

        if (entity.getCustomName() != null) {
            data.put("customName", entity.getCustomName());
            data.put("customNameVisible", entity.isCustomNameVisible());
        }

        if (entity instanceof LivingEntity livingEntity) {
            serializeLivingEntity(livingEntity, data);
        }

        EntitySerializerHandler handler = SERIALIZER_REGISTRY.get(entity.getType());
        if (handler != null) {
            handler.serialize(entity, data);
        }

        return data;
    }

    public static Entity deserializeEntity(Map<String, Object> data, Location location) {
        if (!data.containsKey("type") || !(data.get("type") instanceof String typeStr)) {
            LOGGER.warning("Missing or invalid 'type' in entity data: " + data);
            return null;
        }

        try {
            EntityType type = EntityType.valueOf(typeStr);
            Entity entity = location.getWorld().spawnEntity(location, type);

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

            EntitySerializerHandler handler = SERIALIZER_REGISTRY.get(type);
            if (handler != null) {
                handler.deserialize(entity, data);
            }

            return entity;
        } catch (Exception e) {
            LOGGER.warning("Failed to deserialize entity of type '" + typeStr + "': " + e.getMessage());
            return null;
        }
    }

    private static double getDouble(Map<String, Object> data, String key, double defaultValue) {
        Object value = data.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return defaultValue;
    }

    private static float getFloat(Map<String, Object> data, String key, float defaultValue) {
        Object value = data.get(key);
        if (value instanceof Number number) {
            return number.floatValue();
        }
        return defaultValue;
    }

    private static boolean getBoolean(Map<String, Object> data, String key, boolean defaultValue) {
        Object value = data.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return defaultValue;
    }

    private static int getInt(Map<String, Object> data, String key, int defaultValue) {
        Object value = data.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return defaultValue;
    }

    private static Map<String, Object> serializeVector(Vector vector) {
        Map<String, Object> data = new HashMap<>();
        data.put("x", vector.getX());
        data.put("y", vector.getY());
        data.put("z", vector.getZ());
        return data;
    }

    private static Vector deserializeVector(Object data) {
        if (!(data instanceof Map<?, ?> rawMap)) {
            return new Vector(0, 0, 0);
        }

        Map<String, Object> map = new HashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (!(entry.getKey() instanceof String)) {
                LOGGER.warning("Invalid vector data: key is not a String: " + entry.getKey());
                return new Vector(0, 0, 0);
            }
            map.put((String) entry.getKey(), entry.getValue());
        }

        double x = getDouble(map, "x", 0.0);
        double y = getDouble(map, "y", 0.0);
        double z = getDouble(map, "z", 0.0);
        return new Vector(x, y, z);
    }

    private static Map<String, Object> serializeItemStack(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return null;
        }
        Map<String, Object> data = new HashMap<>();
        data.put("type", item.getType().name());
        data.put("amount", item.getAmount());
        return data;
    }

    private static ItemStack deserializeItemStack(Object data) {
        if (!(data instanceof Map<?, ?> rawMap)) {
            return null;
        }

        Map<String, Object> map = new HashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (!(entry.getKey() instanceof String)) {
                LOGGER.warning("Invalid item stack data: key is not a String: " + entry.getKey());
                return null;
            }
            map.put((String) entry.getKey(), entry.getValue());
        }

        String typeStr = (String) map.get("type");
        Material material = Material.getMaterial(typeStr);
        if (material == null) {
            return null;
        }
        ItemStack item = new ItemStack(material);
        item.setAmount(getInt(map, "amount", 1));
        return item;
    }

    private static List<Map<String, Object>> serializePotionEffects(Collection<PotionEffect> effects) {
        List<Map<String, Object>> effectsData = new ArrayList<>();
        for (PotionEffect effect : effects) {
            Map<String, Object> effectData = new HashMap<>();
            effectData.put("type", effect.getType().getName());
            effectData.put("duration", effect.getDuration());
            effectData.put("amplifier", effect.getAmplifier());
            effectData.put("ambient", effect.isAmbient());
            effectData.put("particles", effect.hasParticles());
            effectsData.add(effectData);
        }
        return effectsData;
    }

    private static void deserializePotionEffects(LivingEntity entity, List<?> effectsData) {
        for (Object obj : effectsData) {
            if (!(obj instanceof Map<?, ?> rawMap)) continue;

            Map<String, Object> map = new HashMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                if (!(entry.getKey() instanceof String)) {
                    LOGGER.warning("Invalid potion effect data: key is not a String: " + entry.getKey());
                    continue;
                }
                map.put((String) entry.getKey(), entry.getValue());
            }

            try {
                String typeStr = (String) map.get("type");
                PotionEffectType type = PotionEffectType.getByName(typeStr);
                if (type == null) continue;
                int duration = getInt(map, "duration", 0);
                int amplifier = getInt(map, "amplifier", 0);
                boolean ambient = getBoolean(map, "ambient", false);
                boolean particles = getBoolean(map, "particles", true);
                PotionEffect effect = new PotionEffect(type, duration, amplifier, ambient, particles);
                entity.addPotionEffect(effect);
            } catch (Exception e) {
                LOGGER.warning("Failed to deserialize potion effect: " + e.getMessage());
            }
        }
    }

    private static void serializeLivingEntity(LivingEntity livingEntity, Map<String, Object> data) {
        if (MAX_HEALTH_ATTRIBUTE != null) {
            Double health = livingEntity.getAttribute(MAX_HEALTH_ATTRIBUTE) != null ?
                    livingEntity.getHealth() : null;
            if (health != null) {
                data.put("health", health);
            }
        }

        Collection<PotionEffect> effects = livingEntity.getActivePotionEffects();
        if (!effects.isEmpty()) {
            data.put("potionEffects", serializePotionEffects(effects));
        }

        EntityEquipment equipment = livingEntity.getEquipment();
        if (equipment != null) {
            Map<String, Object> equipmentData = new HashMap<>();
            equipmentData.put("helmet", serializeItemStack(equipment.getHelmet()));
            equipmentData.put("chestplate", serializeItemStack(equipment.getChestplate()));
            equipmentData.put("leggings", serializeItemStack(equipment.getLeggings()));
            equipmentData.put("boots", serializeItemStack(equipment.getBoots()));
            equipmentData.put("mainHand", serializeItemStack(equipment.getItemInMainHand()));
            equipmentData.put("offHand", serializeItemStack(equipment.getItemInOffHand()));
            data.put("equipment", equipmentData);
        }
    }

    private static void deserializeLivingEntity(LivingEntity livingEntity, Map<String, Object> data) {
        if (MAX_HEALTH_ATTRIBUTE != null && data.containsKey("health")) {
            double health = getDouble(data, "health", 0.0);
            double maxHealth = livingEntity.getAttribute(MAX_HEALTH_ATTRIBUTE).getValue();
            livingEntity.setHealth(Math.min(health, maxHealth));
        }

        if (data.containsKey("potionEffects") && data.get("potionEffects") instanceof List<?> effectsData) {
            deserializePotionEffects(livingEntity, effectsData);
        }

        if (data.containsKey("equipment") && data.get("equipment") instanceof Map<?, ?> equipmentData) {
            EntityEquipment equipment = livingEntity.getEquipment();
            if (equipment != null) {
                equipment.setHelmet(deserializeItemStack(equipmentData.get("helmet")));
                equipment.setChestplate(deserializeItemStack(equipmentData.get("chestplate")));
                equipment.setLeggings(deserializeItemStack(equipmentData.get("leggings")));
                equipment.setBoots(deserializeItemStack(equipmentData.get("boots")));
                equipment.setItemInMainHand(deserializeItemStack(equipmentData.get("mainHand")));
                equipment.setItemInOffHand(deserializeItemStack(equipmentData.get("offHand")));
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
        if (data.containsKey("profession")) {
            try {
                villager.setProfession(Villager.Profession.valueOf((String) data.get("profession")));
            } catch (IllegalArgumentException e) {
                LOGGER.warning("Invalid villager profession: " + data.get("profession"));
            }
        }
        if (data.containsKey("villagerType")) {
            try {
                villager.setVillagerType(Villager.Type.valueOf((String) data.get("villagerType")));
            } catch (IllegalArgumentException e) {
                LOGGER.warning("Invalid villager type: " + data.get("villagerType"));
            }
        }
        if (data.containsKey("level")) {
            villager.setVillagerLevel(getInt(data, "level", 1));
        }
    }

    private static void serializeZombie(Entity entity, Map<String, Object> data) {
        Zombie zombie = (Zombie) entity;
        data.put("isBaby", zombie.isBaby());
    }

    private static void deserializeZombie(Entity entity, Map<String, Object> data) {
        Zombie zombie = (Zombie) entity;
        if (data.containsKey("isBaby")) {
            zombie.setBaby(getBoolean(data, "isBaby", false));
        }
    }
}