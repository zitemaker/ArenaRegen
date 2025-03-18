package com.zitemaker.helpers;

import com.zitemaker.ArenaRegen;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class SelectionToolListener implements Listener {

    private final ArenaRegen plugin;

    public SelectionToolListener(ArenaRegen plugin) {
        this.plugin = plugin;
    }

    private final Map<UUID, Vector[]> selections = new HashMap<>();
    private final Map<UUID, BukkitRunnable> expirationTasks = new HashMap<>();

    public void giveSelectionTool(@NotNull Player player) {
        ItemStack goldenHoe = new ItemStack(Material.GOLDEN_HOE); // No display name
        player.getInventory().addItem(goldenHoe);
        player.sendMessage(ChatColor.GREEN + "You have been given the Selection Tool!");
    }

    @EventHandler
    public void onPlayerInteract(@NotNull PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item.getType() != Material.GOLDEN_HOE) return; // Must be a golden hoe
        if (!player.hasPermission("arena.select") && player.getGameMode() != GameMode.CREATIVE) return;

        event.setCancelled(true);
        UUID playerId = player.getUniqueId();
        Vector[] corners = selections.getOrDefault(playerId, new Vector[2]);

        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            Vector newCorner = Objects.requireNonNull(event.getClickedBlock()).getLocation().toVector();
            if (corners[0] == null || !corners[0].equals(newCorner)) {
                corners[0] = newCorner;
                player.sendMessage(ChatColor.GREEN + "First corner set at: " + formatVector(newCorner));
            }
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Vector newCorner = Objects.requireNonNull(event.getClickedBlock()).getLocation().toVector();
            if (corners[1] == null || !corners[1].equals(newCorner)) {
                corners[1] = newCorner;
                player.sendMessage(ChatColor.GREEN + "Second corner set at: " + formatVector(newCorner));
            }
        }

        selections.put(playerId, corners);
        resetExpiration(playerId);
    }

    public Vector[] getSelection(@NotNull Player player) {
        Vector[] corners = selections.get(player.getUniqueId());
        return (corners != null && corners[0] != null && corners[1] != null) ? corners : null;
    }

    public void clearSelection(@NotNull Player player) {
        selections.remove(player.getUniqueId());
        cancelExpiration(player.getUniqueId());
    }

    private void resetExpiration(UUID playerId) {
        cancelExpiration(playerId);
        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                selections.remove(playerId);
                expirationTasks.remove(playerId);
            }
        };
        // 3 minutes in ticks
        long SELECTION_EXPIRATION_TICKS = 3 * 60 * 20;
        task.runTaskLater(plugin, SELECTION_EXPIRATION_TICKS);
        expirationTasks.put(playerId, task);
    }

    private void cancelExpiration(UUID playerId) {
        BukkitRunnable task = expirationTasks.remove(playerId);
        if (task != null) task.cancel();
    }

    private @NotNull String formatVector(@NotNull Vector vector) {
        return "(" + vector.getBlockX() + ", " + vector.getBlockY() + ", " + vector.getBlockZ() + ")";
    }
}