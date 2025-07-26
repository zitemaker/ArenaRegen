package com.zitemaker.nms;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.core.BlockPos;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.block.data.CraftBlockData;

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
        if (chunks == null || chunks.isEmpty() || blockUpdates == null || blockUpdates.isEmpty()) return;

        CraftWorld craftWorld = (CraftWorld) world;

        try {
            LevelLightEngine lightEngine = craftWorld.getHandle().getChunkSource().getLightEngine();
            Set<ChunkPos> affectedChunks = new HashSet<>();

            for (BlockUpdate update : blockUpdates) {
                int x = update.getX();
                int y = update.getY();
                int z = update.getZ();

                ChunkPos chunkPos = new ChunkPos(x >> 4, z >> 4);
                affectedChunks.add(chunkPos);

                BlockPos pos = new BlockPos(x, y, z);

                lightEngine.checkBlock(pos);

                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (dx == 0 && dy == 0 && dz == 0) continue;
                            BlockPos neighborPos = pos.offset(dx, dy, dz);
                            lightEngine.checkBlock(neighborPos);
                        }
                    }
                }
            }

            for (ChunkPos chunkPos : affectedChunks) {
                try {
                    lightEngine.retainData(chunkPos, true);
                    lightEngine.setLightEnabled(chunkPos, true);
                    int maxUpdates = lightEngine.runLightUpdates();
                    final int chunkX = chunkPos.x;
                    final int chunkZ = chunkPos.z;

                    Bukkit.getScheduler().runTaskLater(
                            Bukkit.getPluginManager().getPlugin("ArenaRegen"),
                            () -> {
                                try {
                                    world.refreshChunk(chunkX, chunkZ);
                                } catch (Exception e) {
                                    // LOGGER.warning("Failed delayed chunk refresh for " + chunkX + "," + chunkZ + ": " + e.getMessage());
                                }
                            },
                            2L
                    );

                } catch (Exception e) {
                   world.refreshChunk(chunkPos.x, chunkPos.z);
                }
            }

        } catch (Throwable t) {
            LOGGER.warning("NMS relight failed, falling back to Bukkit API: " + t.getMessage());
            new BukkitNMSHandler().relightChunks(world, chunks, blockUpdates);
        }
    }
}