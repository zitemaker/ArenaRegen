package com.zitemaker.nms;

import org.bukkit.Bukkit;

public class NMSHandlerFactoryProvider {
    private static NMSHandler instance;

    static {
        String version = Bukkit.getBukkitVersion().split("-")[0];
        if (version.startsWith("1.20.5") || version.startsWith("1.20.6") || version.startsWith("1.21")) {
            try {
                Class<?> nmsHandlerClass = Class.forName("com.zitemaker.nms.NMSHandler_1_21");
                instance = (NMSHandler) nmsHandlerClass.getDeclaredConstructor().newInstance();
                Bukkit.getLogger().info("[ArenaRegen] Using optimized NMS for version " + version);
            } catch (Exception e) {
                Bukkit.getLogger().warning("[ArenaRegen] Failed to load NMS handler for version " + version + ": " + e.getMessage() + ". Falling back to Bukkit API.");
                instance = new BukkitNMSHandler();
            }
        } else {
            Bukkit.getLogger().info("[ArenaRegen] Using Bukkit API for version " + version);
            instance = new BukkitNMSHandler();
        }
    }

    public static NMSHandler getNMSHandler() {
        return instance;
    }
}