package com.zitemaker.commands;


import com.zitemaker.ArenaRegen;
import com.zitemaker.RegionData;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class RegisterCommand implements CommandExecutor {

    private final ArenaRegen plugin;

    public RegisterCommand(ArenaRegen plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }

        if (args.length != 7) {
            sender.sendMessage(ChatColor.RED + "Usage: /register <name> <x1> <y1> <z1> <x2> <y2> <z2>");
            return true;
        }

        String name = args[0];
        Player player = (Player) sender;

        try {
            // Parse coordinates
            int x1 = Integer.parseInt(args[1]);
            int y1 = Integer.parseInt(args[2]);
            int z1 = Integer.parseInt(args[3]);
            int x2 = Integer.parseInt(args[4]);
            int y2 = Integer.parseInt(args[5]);
            int z2 = Integer.parseInt(args[6]);

            // Ensure coordinates are within world bounds
            if (!isCoordinateValid(player.getWorld(), x1, y1, z1) || !isCoordinateValid(player.getWorld(), x2, y2, z2)) {
                sender.sendMessage(ChatColor.RED + "Invalid coordinates. Coordinates must be within the world bounds.");
                return true;
            }

            // Ensure x1 < x2, y1 < y2, z1 < z2
            int minX = Math.min(x1, x2);
            int minY = Math.min(y1, y2);
            int minZ = Math.min(z1, z2);
            int maxX = Math.max(x1, x2);
            int maxY = Math.max(y1, y2);
            int maxZ = Math.max(z1, z2);

            RegionData regionData = new RegionData();

            // Iterate through all blocks in the region
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        Location location = new Location(player.getWorld(), x, y, z);
                        Block block = location.getBlock();
                        regionData.addBlock(location, block.getBlockData());
                    }
                }
            }

            // Save the region data
            plugin.getRegisteredRegions().put(name, regionData);
            sender.sendMessage(ChatColor.GREEN + "Region '" + name + "' has been registered.");

            // Save regions to config
            plugin.saveRegions();
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid coordinates. Please use integers.");
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "An error occurred while registering the region.");
            plugin.getLogger().severe("Error registering region: " + e.getMessage());
        }

        return true;
    }

    // Helper method to validate coordinates
    private boolean isCoordinateValid(World world, int x, int y, int z) {
        return y >= world.getMinHeight() && y <= world.getMaxHeight();
    }
}
