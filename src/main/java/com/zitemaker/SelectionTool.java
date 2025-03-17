package com.zitemaker;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;
import java.util.HashMap;
import java.util.Map;

public class SelectionTool implements Listener {

    private final Map<Player, Selection> playerSelections = new HashMap<>();

    public static class Selection {
        private Location pos1;
        private Location pos2;

        public void setPos1(Location loc) { this.pos1 = loc; }
        public void setPos2(Location loc) { this.pos2 = loc; }
        public Location getPos1() { return pos1; }
        public Location getPos2() { return pos2; }
        public boolean isComplete() { return pos1 != null && pos2 != null; }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        // Check if player has permission and is in Creative mode
        if (!player.hasPermission("arenaregen.select") || player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() != Material.IRON_HOE) return; // Only works with Iron Hoe

        event.setCancelled(true); // Prevents breaking blocks with hoe

        Selection selection = playerSelections.computeIfAbsent(player, k -> new Selection());
        Location clickedLoc = event.getClickedBlock() != null ? event.getClickedBlock().getLocation() : null;

        if (clickedLoc == null) return;

        if (event.getAction().toString().contains("LEFT_CLICK")) {
            selection.setPos1(clickedLoc);
            player.sendMessage(ChatColor.GREEN + "First position set at " + formatLocation(clickedLoc));
        } else if (event.getAction().toString().contains("RIGHT_CLICK")) {
            selection.setPos2(clickedLoc);
            player.sendMessage(ChatColor.GREEN + "Second position set at " + formatLocation(clickedLoc));
        }

        if (selection.isComplete()) {
            player.sendMessage(ChatColor.AQUA + "Selection complete! Use /ar create <name> to register.");
        }
    }

    @EventHandler
    public void onItemSwap(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (playerSelections.containsKey(player) && player.getInventory().getItem(event.getNewSlot()).getType() != Material.IRON_HOE) {
            playerSelections.remove(player);
            player.sendMessage(ChatColor.RED + "Selection cleared! You must hold the Iron Hoe to keep selecting.");
        }
    }

    public Selection getSelection(Player player) {
        return playerSelections.get(player);
    }

    private String formatLocation(Location loc) {
        return ChatColor.YELLOW + "(" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ")";
    }
}
