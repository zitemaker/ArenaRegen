package com.zitemaker.helpers;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.data.BlockData;
import org.bukkit.boss.BossBar;
import org.bukkit.boss.DragonBattle;
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
import java.util.stream.Stream;

public final class EntitySerializer {
    private static final Logger LOGGER = Bukkit.getLogger();
    private static final Attribute MAX_HEALTH_ATTRIBUTE = initializeMaxHealthAttribute();
    private static final Map<EntityType, EntitySerializerHandler> SERIALIZER_REGISTRY = new ConcurrentHashMap<>();

    static {
        for (EntityType type : EntityType.values()) {
            registerDefaultSerializer(type);
        }
        registerSerializer(EntityType.VILLAGER, EntitySerializer::serializeVillager, EntitySerializer::deserializeVillager);
        registerSerializer(EntityType.ZOMBIE, EntitySerializer::serializeZombie, EntitySerializer::deserializeZombie);
        registerSerializer(EntityType.ENDERMAN, EntitySerializer::serializeEnderman, EntitySerializer::deserializeEnderman);
        registerSerializer(EntityType.ENDER_DRAGON, EntitySerializer::serializeEnderDragon, EntitySerializer::deserializeEnderDragon);
        registerSerializer(EntityType.MINECART, EntitySerializer::serializeMinecart, EntitySerializer::deserializeMinecart);
        registerSerializer(EntityType.ARROW, EntitySerializer::serializeArrow, EntitySerializer::deserializeArrow);
        registerSerializer(EntityType.ITEM, EntitySerializer::serializeItem, EntitySerializer::deserializeItem);
        registerSerializer(EntityType.FIREBALL, EntitySerializer::serializeFireball, EntitySerializer::deserializeFireball);
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

    private static void registerDefaultSerializer(EntityType type) {
        registerSerializer(type,
                (entity, data) -> LOGGER.fine("Using default serialization for " + type.name()),
                (entity, data) -> LOGGER.fine("Using default deserialization for " + type.name()));
    }

    private static Attribute initializeMaxHealthAttribute() {
        return Stream.of(Attribute.values())
                .filter(attr -> "generic.max_health".equals(attr.getKey().getKey()))
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

        Map<String, Object> data = new HashMap<>(32);
        Location loc = entity.getLocation();
        data.put("type", entity.getType().name());
        data.putAll(serializeLocation(loc));
        data.put("velocity", serializeVector(entity.getVelocity()));
        data.put("isDead", entity.isDead());

        if (entity.getCustomName() != null) {
            data.put("customName", entity.getCustomName());
            data.put("customNameVisible", entity.isCustomNameVisible());
        }

        if (entity instanceof LivingEntity livingEntity) {
            serializeLivingEntity(livingEntity, data);
        }

        SERIALIZER_REGISTRY.getOrDefault(entity.getType(), (e, d, s) -> {})
                .process(entity, data, true);

        return data.isEmpty() ? null : Collections.unmodifiableMap(new HashMap<>(data));
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
            if (world == null) {
                LOGGER.warning("World null for entity spawn at " + location);
                return null;
            }

            Entity entity = world.spawnEntity(location, type);
            location.setYaw(getFloat(data, "yaw", 0.0f));
            location.setPitch(getFloat(data, "pitch", 0.0f));
            entity.teleport(location);

            if (data.containsKey("velocity")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> velocityData = (Map<String, Object>) data.get("velocity");
                entity.setVelocity(deserializeVector(velocityData));
            }
            if (data.containsKey("isDead") && getBoolean(data, "isDead", false)) {
                entity.remove();
                return null;
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
        return new HashMap<>() {{
            put("x", loc.getX());
            put("y", loc.getY());
            put("z", loc.getZ());
            put("yaw", loc.getYaw());
            put("pitch", loc.getPitch());
            put("world", loc.getWorld().getName());
        }};
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
        if (vector == null) return new HashMap<>();
        return new HashMap<>() {{
            put("x", vector.getX());
            put("y", vector.getY());
            put("z", vector.getZ());
        }};
    }

    @SuppressWarnings("unchecked")
    private static Vector deserializeVector(Map<String, Object> data) {
        if (data == null) return new Vector();
        return new Vector(
                getDouble(data, "x", 0.0),
                getDouble(data, "y", 0.0),
                getDouble(data, "z", 0.0)
        );
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> serializeItemStack(ItemStack item) {
        return item != null && item.getType() != Material.AIR ? new HashMap<>() {{
            put("type", item.getType().name());
            put("amount", item.getAmount());
            put("meta", item.hasItemMeta() ? item.getItemMeta().toString() : null);
        }} : null;
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
        Material material = Material.matchMaterial(typeStr);
        if (material == null) return null;
        ItemStack item = new ItemStack(material, getInt(map, "amount", 1));
        if (map.containsKey("meta") && map.get("meta") instanceof String metaStr) {
            LOGGER.warning("Complex item meta not fully supported: " + metaStr);
        }
        return item;
    }

    private static List<Map<String, Object>> serializePotionEffects(Collection<PotionEffect> effects) {
        return effects.stream()
                .filter(Objects::nonNull)
                .map(effect -> {
                    Map<String, Object> effectData = new HashMap<>();
                    effectData.put("type", effect.getType().getKey().getKey());
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
                        String typeKey = (String) map.get("type");
                        PotionEffectType type = PotionEffectType.getByKey(NamespacedKey.fromString("minecraft:" + typeKey));
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
            Map<String, Object> equipData = new HashMap<>();
            equipData.put("helmet", serializeItemStack(equipment.getHelmet()));
            equipData.put("chestplate", serializeItemStack(equipment.getChestplate()));
            equipData.put("leggings", serializeItemStack(equipment.getLeggings()));
            equipData.put("boots", serializeItemStack(equipment.getBoots()));
            equipData.put("mainHand", serializeItemStack(equipment.getItemInMainHand()));
            equipData.put("offHand", serializeItemStack(equipment.getItemInOffHand()));
            data.put("equipment", equipData);
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

    private static void serializeEnderman(Entity entity, Map<String, Object> data) {
        Enderman enderman = (Enderman) entity;
        BlockData carriedBlock = enderman.getCarriedBlock();
        if (carriedBlock != null) {
            data.put("carriedBlock", carriedBlock.getAsString());
        }
    }

    private static void deserializeEnderman(Entity entity, Map<String, Object> data) {
        Enderman enderman = (Enderman) entity;
        if (data.containsKey("carriedBlock") && data.get("carriedBlock") instanceof String blockStr) {
            BlockData blockData = Bukkit.createBlockData(blockStr);
            enderman.setCarriedBlock(blockData);
        }
    }

    private static void serializeEnderDragon(Entity entity, Map<String, Object> data) {
        EnderDragon dragon = (EnderDragon) entity;
        data.put("phase", dragon.getPhase().name());
        DragonBattle battle = dragon.getDragonBattle();
        if (battle != null) {
            BossBar bossBar = battle.getBossBar();
            data.put("healthProgress", bossBar.getProgress());
            data.put("previouslyKilled", battle.hasBeenPreviouslyKilled());
        }
        data.put("deathAnimationTicks", dragon.getDeathAnimationTicks());
    }

    private static void deserializeEnderDragon(Entity entity, Map<String, Object> data) {
        EnderDragon dragon = (EnderDragon) entity;
        DragonBattle battle = dragon.getDragonBattle();
        if (battle != null) {
            if (data.containsKey("phase") && data.get("phase") instanceof String phaseStr) {
                try {
                    dragon.setPhase(EnderDragon.Phase.valueOf(phaseStr));
                } catch (IllegalArgumentException e) {
                    LOGGER.warning("Invalid dragon phase: " + phaseStr);
                }
            }
            if (data.containsKey("previouslyKilled")) {
                battle.setPreviouslyKilled(getBoolean(data, "previouslyKilled", false));
            }
            if (data.containsKey("healthProgress") && battle.getBossBar() != null) {
                LOGGER.warning("Health progress deserialization not fully supported; consider NMS for precision.");
            }
            if (data.containsKey("deathAnimationTicks") && getInt(data, "deathAnimationTicks", 0) > 0) {
                if (getInt(data, "deathAnimationTicks", 0) >= 200) {
                    dragon.remove();
                }
            }
        }
    }

    private static void serializeMinecart(Entity entity, Map<String, Object> data) {
        Minecart minecart = (Minecart) entity;
        data.put("customName", minecart.getCustomName());
        data.put("customNameVisible", minecart.isCustomNameVisible());
        data.put("maxSpeed", minecart.getMaxSpeed());
        data.put("slowWhenEmpty", minecart.isSlowWhenEmpty());
    }

    private static void deserializeMinecart(Entity entity, Map<String, Object> data) {
        Minecart minecart = (Minecart) entity;
        if (data.containsKey("customName") && data.get("customName") instanceof String name) {
            minecart.setCustomName(name);
        }
        if (data.containsKey("customNameVisible")) {
            minecart.setCustomNameVisible(getBoolean(data, "customNameVisible", false));
        }
        if (data.containsKey("maxSpeed")) {
            minecart.setMaxSpeed(getDouble(data, "maxSpeed", 0.4));
        }
        if (data.containsKey("slowWhenEmpty")) {
            minecart.setSlowWhenEmpty(getBoolean(data, "slowWhenEmpty", true));
        }
    }

    private static void serializeArrow(Entity entity, Map<String, Object> data) {
        AbstractArrow arrow = (AbstractArrow) entity;
        data.put("damage", arrow.getDamage());
        data.put("isCritical", arrow.isCritical());
        data.put("knockbackStrength", arrow.getKnockbackStrength());
    }

    private static void deserializeArrow(Entity entity, Map<String, Object> data) {
        AbstractArrow arrow = (AbstractArrow) entity;
        if (data.containsKey("damage")) {
            arrow.setDamage(getDouble(data, "damage", 2.0));
        }
        if (data.containsKey("isCritical")) {
            arrow.setCritical(getBoolean(data, "isCritical", false));
        }
        if (data.containsKey("knockbackStrength")) {
            arrow.setKnockbackStrength(getInt(data, "knockbackStrength", 0));
        }
    }

    private static void serializeItem(Entity entity, Map<String, Object> data) {
        Item item = (Item) entity;
        data.put("itemStack", serializeItemStack(item.getItemStack()));
        data.put("pickupDelay", item.getPickupDelay());
    }

    private static void deserializeItem(Entity entity, Map<String, Object> data) {
        Item item = (Item) entity;
        if (data.containsKey("itemStack")) {
            item.setItemStack(deserializeItemStack(data.get("itemStack")));
        }
        if (data.containsKey("pickupDelay")) {
            item.setPickupDelay(getInt(data, "pickupDelay", 10));
        }
    }

    private static void serializeFireball(Entity entity, Map<String, Object> data) {
        Fireball fireball = (Fireball) entity;
        data.put("direction", serializeVector(fireball.getDirection()));
        data.put("yield", fireball.getYield());
    }

    private static void deserializeFireball(Entity entity, Map<String, Object> data) {
        Fireball fireball = (Fireball) entity;
        if (data.containsKey("direction")) {
            fireball.setDirection(deserializeVector((Map<String, Object>) data.get("direction")));
        }
        if (data.containsKey("yield")) {
            fireball.setYield((float) getDouble(data, "yield", 1.0));
        }
    }

    private static void serializeVillager(Entity entity, Map<String, Object> data) {
        Villager villager = (Villager) entity;
        data.put("profession", villager.getProfession().getKey().getKey());
        data.put("villagerType", villager.getVillagerType().getKey().getKey());
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