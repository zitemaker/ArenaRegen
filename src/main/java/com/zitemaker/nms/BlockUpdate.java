package com.zitemaker.nms;

public class BlockUpdate {
    private final int x, y, z;
    private final org.bukkit.block.data.BlockData blockData;

    public BlockUpdate(int x, int y, int z, org.bukkit.block.data.BlockData blockData) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.blockData = blockData;
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
    public org.bukkit.block.data.BlockData getBlockData() { return blockData; }
}