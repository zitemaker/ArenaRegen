package com.zitemaker.nms;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.LightLayer;
import net.minecraft.core.SectionPos;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.block.data.CraftBlockData;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

public class NMSHandler_1_21 implements NMSHandler {
    private static final Logger LOGGER = Bukkit.getLogger();

    @Override
    public void setBlocks(World world, List<BlockUpdate> blockUpdates) {
        if (blockUpdates == null || blockUpdates.isEmpty()) return;

        CraftWorld craftWorld = (CraftWorld) world;
        Long2ObjectMap<LevelChunk> chunkCache = new Long2ObjectOpenHashMap<>();

        try {
            for (BlockUpdate update : blockUpdates) {
                final int x = update.getX();
                final int y = update.getY();
                final int z = update.getZ();

                final long chunkKey = ChunkPos.asLong(x >> 4, z >> 4);
                final LevelChunk chunk = chunkCache.computeIfAbsent(chunkKey,
                        key -> craftWorld.getHandle().getChunk(x >> 4, z >> 4));

                final LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(y));

                section.setBlockState(x & 15, y & 15, z & 15,
                        ((CraftBlockData) update.getBlockData()).getState());
            }
        } catch (Throwable t) {
            LOGGER.warning("NMS failed, falling back to Bukkit API: " + t.getMessage());
            new BukkitNMSHandler().setBlocks(world, blockUpdates);
        }
    }

    @Override
    public void relightChunks(World world, List<Chunk> chunks, List<BlockUpdate> blockUpdates) {
        if (chunks == null || chunks.isEmpty()) return;

        Set<ChunkPos> affectedChunks = new HashSet<>();
        for (Chunk chunk : chunks) {
            affectedChunks.add(new ChunkPos(chunk.getX(), chunk.getZ()));
        }

        List<ChunkPos> chunkList = new ArrayList<>(affectedChunks);
        processChunksWithLighting(world, chunkList, 0);
    }

    private void processChunksWithLighting(World world, List<ChunkPos> chunks, int index) {
        if (index >= chunks.size()) return;

        int batchSize = Math.min(3, chunks.size() - index);
        
        try {
            CraftWorld craftWorld = (CraftWorld) world;
            LevelLightEngine lightEngine = craftWorld.getHandle().getLightEngine();
            
            for (int i = 0; i < batchSize; i++) {
                ChunkPos chunkPos = chunks.get(index + i);
                try {
                    lightEngine.retainData(chunkPos, true);
                    lightEngine.setLightEnabled(chunkPos, true);

                    for (int sectionIndex = lightEngine.getMinLightSection(); 
                         sectionIndex < lightEngine.getMaxLightSection(); sectionIndex++) {
                        
                        SectionPos sectionPos = SectionPos.of(chunkPos, sectionIndex);
                        lightEngine.updateSectionStatus(sectionPos, false);
                        
                        lightEngine.queueSectionData(LightLayer.BLOCK, sectionPos, null);
                        lightEngine.queueSectionData(LightLayer.SKY, sectionPos, null);
                    }

                    world.refreshChunk(chunkPos.x, chunkPos.z);
                    
                } catch (Exception e) {
                    LOGGER.warning("Failed to relight chunk at " + chunkPos.x + ", " + chunkPos.z + ": " + e.getMessage());
                    try {
                        world.refreshChunk(chunkPos.x, chunkPos.z);
                    } catch (Exception fallbackException) {
                    }
                }
            }
        } catch (Throwable t) {
            LOGGER.warning("Lighting engine failed, falling back to simple chunk refresh: " + t.getMessage());
            // Fallback to simple refresh for all chunks in this batch
            for (int i = 0; i < batchSize; i++) {
                ChunkPos chunkPos = chunks.get(index + i);
                try {
                    world.refreshChunk(chunkPos.x, chunkPos.z);
                } catch (Exception e) {
                    // Silent fallback failure
                }
            }
        }

        if (index + batchSize < chunks.size()) {
            Bukkit.getScheduler().runTaskLater(
                    Bukkit.getPluginManager().getPlugin("ArenaRegen"),
                    () -> processChunksWithLighting(world, chunks, index + batchSize),
                    3L
            );
        }
    }
}