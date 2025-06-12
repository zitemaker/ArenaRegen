package com.zitemaker.nms;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.block.data.CraftBlockData;

import java.util.List;
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
}
