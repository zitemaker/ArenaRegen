package com.zitemaker.listeners;

import com.zitemaker.ArenaRegen;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;

public class WorldLoadListener implements Listener {
    private final ArenaRegen plugin;

    public WorldLoadListener(ArenaRegen plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        if (event != null && event.getWorld() != null) {
            plugin.onWorldLoaded(event.getWorld());
        }
    }
}
