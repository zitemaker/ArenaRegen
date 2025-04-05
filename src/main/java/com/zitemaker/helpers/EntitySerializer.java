package com.zitemaker.helpers;

import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Zombie;

import java.util.HashMap;
import java.util.Map;

public class EntitySerializer {

    public static Map<String, Object> serializeEntity(Entity entity) {
        Map<String, Object> data = new HashMap<>();

        data.put("type", entity.getType().name());
        data.put("x", entity.getLocation().getX());
        data.put("y", entity.getLocation().getY());
        data.put("z", entity.getLocation().getZ());
        data.put("yaw", entity.getLocation().getYaw());
        data.put("pitch", entity.getLocation().getPitch());


        if (entity.getCustomName() != null) {
            data.put("customName", entity.getCustomName());
            data.put("customNameVisible", entity.isCustomNameVisible());
        }

        if (entity instanceof LivingEntity livingEntity) {
            Double health = livingEntity.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null ?
                    livingEntity.getHealth() : null;
            if (health != null) {
                data.put("health", health);
            }
        }

        if (entity instanceof Villager villager) {
            data.put("profession", villager.getProfession().name());
            data.put("villagerType", villager.getVillagerType().name());
            data.put("level", villager.getVillagerLevel());
        } else if (entity instanceof Zombie zombie) {
            data.put("isBaby", zombie.isBaby());
        }

        return data;
    }

    public static Entity deserializeEntity(Map<String, Object> data, Location location) {
        if (!data.containsKey("type")) {
            return null;
        }

        try {
            EntityType type = EntityType.valueOf((String) data.get("type"));
            Entity entity = location.getWorld().spawnEntity(location, type);

            if (data.containsKey("customName")) {
                entity.setCustomName((String) data.get("customName"));
                entity.setCustomNameVisible((Boolean) data.getOrDefault("customNameVisible", false));
            }

            if (entity instanceof LivingEntity livingEntity) {
                if (data.containsKey("health")) {
                    double health = (double) data.get("health");
                    double maxHealth = livingEntity.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
                    livingEntity.setHealth(Math.min(health, maxHealth));
                }
            }

            if (entity instanceof Villager villager) {
                if (data.containsKey("profession")) {
                    villager.setProfession(Villager.Profession.valueOf((String) data.get("profession")));
                }
                if (data.containsKey("villagerType")) {
                    villager.setVillagerType(Villager.Type.valueOf((String) data.get("villagerType")));
                }
                if (data.containsKey("level")) {
                    villager.setVillagerLevel((int) data.get("level"));
                }
            } else if (entity instanceof Zombie zombie) {
                if (data.containsKey("isBaby")) {
                    zombie.setBaby((boolean) data.get("isBaby"));
                }
            }

            return entity;
        } catch (Exception e) {
            return null;
        }
    }
}