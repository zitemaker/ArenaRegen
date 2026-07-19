package com.zitemaker.nms;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class NMSHandlerFactoryProvider {
    private static NMSHandler instance;

    static {
        String version = Bukkit.getBukkitVersion().split("-")[0];
        if (supportsOptimizedNms(version)) {
            try {
                Class<?> nmsHandlerClass = Class.forName("com.zitemaker.nms.NMSHandler_1_21");
                Plugin plugin = JavaPlugin.getPlugin(com.zitemaker.ArenaRegen.class);
                java.lang.reflect.Constructor<?> constructor = nmsHandlerClass.getConstructor(Plugin.class);
                instance = (NMSHandler) constructor.newInstance(plugin);
                Bukkit.getLogger().info("[ArenaRegen] Using optimized NMS for version " + version);
            } catch (Exception e) {
                Bukkit.getLogger().warning("[ArenaRegen] Failed to load NMS handler for version " + version + ": "
                        + e.getMessage() + ". Falling back to Bukkit API.");
                instance = new BukkitNMSHandler();
            }
        } else {
            Bukkit.getLogger().info("[ArenaRegen] Using Bukkit API for version " + version);
            instance = new BukkitNMSHandler();
        }
    }

    /**
     * Optimized NMS path is available for Mojang-mapped Paper runtimes from 1.20.5
     * through the current 26.x line.
     */
    public static boolean supportsOptimizedNms(String version) {
        if (version == null || version.isEmpty()) {
            return false;
        }
        // New calendar versioning: 26.1, 26.2, ...
        if (version.startsWith("26.")) {
            return true;
        }
        // Legacy Mojang-mapped range
        return version.startsWith("1.20.5")
                || version.startsWith("1.20.6")
                || version.startsWith("1.21");
    }

    public static NMSHandler getNMSHandler() {
        return instance;
    }
}
