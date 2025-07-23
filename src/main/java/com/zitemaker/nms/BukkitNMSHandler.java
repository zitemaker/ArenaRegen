package com.zitemaker.nms;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BukkitNMSHandler implements NMSHandler {

    @Override
    public void setBlocks(World world, List<BlockUpdate> blockUpdates) {
        for (BlockUpdate update : blockUpdates) {
            Block block = world.getBlockAt(update.getX(), update.getY(), update.getZ());
            block.setBlockData(update.getBlockData(), false);
        }
    }

    @Override
    public void relightChunks(World world, List<Chunk> chunks, List<BlockUpdate> blockUpdates) {
        if (chunks == null || chunks.isEmpty()) return;

        Set<Chunk> uniqueChunks = new HashSet<>(chunks);

        for (Chunk chunk : uniqueChunks) {
            try {
                world.refreshChunk(chunk.getX(), chunk.getZ());
            } catch (Exception e) {
                Bukkit.getLogger().warning("Failed to refresh chunk " + chunk.getX() + "," + chunk.getZ() + ": " + e.getMessage());
            }
        }

        JavaPlugin plugin = null;
        try {
            plugin = (JavaPlugin) Bukkit.getPluginManager().getPlugin("ArenaRegen");
        } catch (Exception e) {
            plugin = (JavaPlugin) Bukkit.getPluginManager().getPlugins()[0];
        }

        if (plugin != null) {
            final JavaPlugin finalPlugin = plugin;
            Bukkit.getScheduler().runTaskLater(finalPlugin, () -> {
                for (Chunk chunk : uniqueChunks) {
                    try {
                        world.refreshChunk(chunk.getX(), chunk.getZ());
                    } catch (Exception e) {
                        Bukkit.getLogger().warning("Failed delayed chunk refresh " + chunk.getX() + "," + chunk.getZ() + ": " + e.getMessage());
                    }
                }
            }, 3L);
        }
    }
}