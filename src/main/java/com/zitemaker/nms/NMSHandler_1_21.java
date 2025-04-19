package com.zitemaker.nms;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.block.data.CraftBlockData;
import java.util.List;
import java.util.logging.Logger;

public class NMSHandler_1_21 implements NMSHandler {
    private static final Logger LOGGER = Logger.getLogger(NMSHandler_1_21.class.getName());

    @Override
    public void setBlocks(World world, List<BlockUpdate> blockUpdates) {
        try {
            CraftWorld craftWorld = (CraftWorld) world;
            Long2ObjectMap<LevelChunk> chunkCache = new Long2ObjectOpenHashMap<>();

            for (BlockUpdate update : blockUpdates) {
                int x = update.getX();
                int y = update.getY();
                int z = update.getZ();
                long chunkKey = new ChunkPos(x >> 4, z >> 4).toLong();
                LevelChunk chunk = chunkCache.computeIfAbsent(chunkKey, key -> craftWorld.getHandle().getChunk(x >> 4, z >> 4));

                int sectionIndex = chunk.getSectionIndex(y);
                LevelChunkSection section = chunk.getSection(sectionIndex);

                int sectionX = x & 15;
                int sectionY = y & 15;
                int sectionZ = z & 15;
                net.minecraft.world.level.block.state.BlockState state = ((CraftBlockData) update.getBlockData()).getState();

                section.setBlockState(sectionX, sectionY, sectionZ, state);
            }
        } catch (Exception e){
            LOGGER.info("NMS has failed, Falling back to Bukkit API for regeneration...");
            new BukkitNMSHandler().setBlocks(world, blockUpdates);
        }

    }
}