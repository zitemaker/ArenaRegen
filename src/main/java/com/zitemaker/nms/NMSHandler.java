package com.zitemaker.nms;

import org.bukkit.Chunk;
import org.bukkit.World;

import java.util.List;

public interface NMSHandler {
    void setBlocks(World world, List<BlockUpdate> blockUpdates);

    void relightChunks(World world, List<Chunk> chunks, List<BlockUpdate> blockUpdates);
}

