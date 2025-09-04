package com.zitemaker.nms;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
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
        List<Chunk> chunkList = new ArrayList<>(uniqueChunks);
        processChunks(world, chunkList, 0);
    }

    private void processChunks(World world, List<Chunk> chunks, int index) {
        if (index >= chunks.size()) return;

        JavaPlugin plugin = null;
        try {
            plugin = (JavaPlugin) Bukkit.getPluginManager().getPlugin("ArenaRegen");
        } catch (Exception e) {
            plugin = (JavaPlugin) Bukkit.getPluginManager().getPlugins()[0];
        }

        if (plugin == null) return;

        int batchSize = Math.min(3, chunks.size() - index);
        for (int i = 0; i < batchSize; i++) {
            Chunk chunk = chunks.get(index + i);
            try {

                int chunkX = chunk.getX();
                int chunkZ = chunk.getZ();
                

                world.refreshChunk(chunkX, chunkZ);

                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    try {
                        for (int dx = -1; dx <= 1; dx++) {
                            for (int dz = -1; dz <= 1; dz++) {
                                if (world.isChunkLoaded(chunkX + dx, chunkZ + dz)) {
                                    world.refreshChunk(chunkX + dx, chunkZ + dz);
                                }
                            }
                        }
                    } catch (Exception e) {
                    }
                }, 10L);
                
            } catch (Exception e) {
                Bukkit.getLogger().warning("Failed delayed chunk refresh " + chunk.getX() + "," + chunk.getZ() + ": " + e.getMessage());
            }
        }

        if (index + batchSize < chunks.size()) {
            final JavaPlugin finalPlugin = plugin;
            Bukkit.getScheduler().runTaskLater(finalPlugin, () -> processChunks(world, chunks, index + batchSize), 3L);
        }
    }
}
