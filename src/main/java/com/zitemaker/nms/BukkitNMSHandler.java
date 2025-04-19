package com.zitemaker.nms;

import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.List;

public class BukkitNMSHandler implements NMSHandler {
    @Override
    public void setBlocks(World world, List<BlockUpdate> blockUpdates) {
        for (BlockUpdate update : blockUpdates) {
            Block block = world.getBlockAt(update.getX(), update.getY(), update.getZ());
            block.setBlockData(update.getBlockData(), false);
        }
    }
}