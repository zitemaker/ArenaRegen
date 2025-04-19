package com.zitemaker.nms;

import org.bukkit.World;

import java.util.List;

public interface NMSHandler {
    void setBlocks(World world, List<BlockUpdate> blockUpdates);
}
