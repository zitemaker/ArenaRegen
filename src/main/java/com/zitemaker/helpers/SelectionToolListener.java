package com.zitemaker.helpers;

import com.zitemaker.ArenaRegen;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.FileConfiguration;
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
    private final Material wandMaterial;

    private static final BlockData HIGHLIGHT_BLOCK = Material.GOLD_BLOCK.createBlockData();
    private static final Material DEFAULT_WAND_MATERIAL = Material.GOLDEN_HOE;

    public SelectionToolListener(ArenaRegen plugin) {
        this.plugin = plugin;


        String wandToolString = plugin.getConfig().getString("general.selection-tool", "Material.GOLDEN_HOE");
        Material configuredMaterial;
        try {
            configuredMaterial = Material.valueOf(wandToolString.replace("Material.", "").toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.console.sendMessage(ChatColor.RED + "[ArenaRegen] Invalid wand-tool material '" + wandToolString + "' in config.yml. Defaulting to GOLDEN_HOE.");
            configuredMaterial = DEFAULT_WAND_MATERIAL;
        }
        this.wandMaterial = configuredMaterial;
    }

    public void giveSelectionTool(@NotNull Player player) {
        ItemStack wandTool = new ItemStack(wandMaterial);
        player.getInventory().addItem(wandTool);
        player.sendMessage(ChatColor.GREEN + "You have been given the Flag Selection Tool (" + wandMaterial.name() + "). Use the /jailsetflag command after selecting the two corners.");
    }

    @EventHandler
    public void onPlayerInteract(@NotNull PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item.getType() != wandMaterial) return;
        if (!player.hasPermission("jails.setflag")) return;

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
            player.sendMessage(ChatColor.RED + "No selection found. Use the /jailwand command to get the selection tool.");
            return null;
        }
        if (corners[0] == null) {
            player.sendMessage(ChatColor.RED + "First corner not set. Left-click with the selection tool to set.");
            return null;
        }
        if (corners[1] == null) {
            player.sendMessage(ChatColor.RED + "Second corner not set. Right-click with the selection tool to set.");
            return null;
        }
        return corners;
    }

    public void clearSelection(@NotNull Player player) {
        UUID playerId = player.getUniqueId();
        selections.remove(playerId);
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