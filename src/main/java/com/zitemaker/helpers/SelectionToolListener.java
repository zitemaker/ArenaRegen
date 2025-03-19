package com.zitemaker.helpers;

import com.zitemaker.ArenaRegen;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SelectionToolListener implements Listener {

    private final ArenaRegen plugin;
    private final Map<UUID, Vector[]> selections = new HashMap<>();
    private final Map<UUID, BukkitRunnable> expirationTasks = new HashMap<>();
    private final Map<UUID, Location[]> displayedCorners = new HashMap<>();

    private static final BlockData HIGHLIGHT_BLOCK = Material.GOLD_BLOCK.createBlockData();

    public SelectionToolListener(ArenaRegen plugin) {
        this.plugin = plugin;
    }

    public void giveSelectionTool(@NotNull Player player) {
        ItemStack goldenHoe = new ItemStack(Material.GOLDEN_HOE);
        player.getInventory().addItem(goldenHoe);
        player.sendMessage(ChatColor.GREEN + "You have been given the Selection Tool!");
    }

    @EventHandler
    public void onPlayerInteract(@NotNull PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item.getType() != Material.GOLDEN_HOE) return; // must be a golden hoe
        if (!player.hasPermission("arenaregen.create") && !player.hasPermission("arenaregen.resize") && player.getGameMode() != GameMode.CREATIVE) return;

        event.setCancelled(true);

        if (event.getClickedBlock() == null) return;

        UUID playerId = player.getUniqueId();
        Vector[] corners = selections.computeIfAbsent(playerId, k -> new Vector[2]);
        Location[] displayed = displayedCorners.computeIfAbsent(playerId, k -> new Location[2]);

        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            Vector newCorner = event.getClickedBlock().getLocation().toVector();
            corners[0] = newCorner;
            updateCornerDisplay(player, 0, newCorner, displayed);
            player.sendMessage(ChatColor.GREEN + "First corner set at: " + formatVector(newCorner));
            selections.put(playerId, corners);
            resetExpiration(playerId);
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Vector newCorner = event.getClickedBlock().getLocation().toVector();
            corners[1] = newCorner;
            updateCornerDisplay(player, 1, newCorner, displayed);
            player.sendMessage(ChatColor.GREEN + "Second corner set at: " + formatVector(newCorner));
            selections.put(playerId, corners);
            resetExpiration(playerId);
        }
    }

    public Vector[] getSelection(@NotNull Player player) {
        Vector[] corners = selections.get(player.getUniqueId());
        if (corners == null) {
            player.sendMessage(ChatColor.RED + "No selection found. Please use the golden hoe to select corners.");
            return null;
        }
        if (corners[0] == null) {
            player.sendMessage(ChatColor.RED + "First corner not set. Left-click with golden hoe to set.");
            return null;
        }
        if (corners[1] == null) {
            player.sendMessage(ChatColor.RED + "Second corner not set. Right-click with golden hoe to set.");
            return null;
        }
        return corners;
    }

    public void clearSelection(@NotNull Player player) {
        UUID playerId = player.getUniqueId();
        Vector[] corners = selections.remove(playerId);
        Location[] displayed = displayedCorners.remove(playerId);
        if (displayed != null) {
            revertCornerDisplay(player, displayed);
        }
        cancelExpiration(playerId);
    }

    private void resetExpiration(UUID playerId) {
        cancelExpiration(playerId);
        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                Player player = plugin.getServer().getPlayer(playerId);
                if (player != null) {
                    clearSelection(player);
                } else {
                    selections.remove(playerId);
                    displayedCorners.remove(playerId);
                    expirationTasks.remove(playerId);
                }
            }
        };
        long SELECTION_EXPIRATION_TICKS = 3 * 60 * 20; // 3 minutes in ticks
        task.runTaskLater(plugin, SELECTION_EXPIRATION_TICKS);
        expirationTasks.put(playerId, task);
    }

    private void cancelExpiration(UUID playerId) {
        BukkitRunnable task = expirationTasks.remove(playerId);
        if (task != null) task.cancel();
    }

    // this is supposed to change the selected block to a gold block only in the client side. however, it wasn't working and I just gave up
    private void updateCornerDisplay(Player player, int index, Vector corner, Location[] displayed) {
        Location oldLoc = displayed[index];
        Location newLoc = corner.toLocation(player.getWorld());


        if (oldLoc != null && !oldLoc.equals(newLoc)) {
            player.sendBlockChange(oldLoc, oldLoc.getBlock().getBlockData());
        }


        player.sendBlockChange(newLoc, HIGHLIGHT_BLOCK);
        displayed[index] = newLoc;
        displayedCorners.put(player.getUniqueId(), displayed);
    }

    private void revertCornerDisplay(Player player, Location[] displayed) {
        for (Location loc : displayed) {
            if (loc != null) {
                player.sendBlockChange(loc, loc.getBlock().getBlockData());
            }
        }
    }

    private @NotNull String formatVector(@NotNull Vector vector) {
        return "(" + vector.getBlockX() + ", " + vector.getBlockY() + ", " + vector.getBlockZ() + ")";
    }
}