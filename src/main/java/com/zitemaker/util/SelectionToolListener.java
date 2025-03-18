package com.zitemaker.util;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class SelectionToolListener implements Listener {
    private final Map<UUID, Vector[]> selections = new HashMap<>();

    public void giveSelectionTool(@NotNull Player player) {
        ItemStack goldenHoe = new ItemStack(Material.GOLDEN_HOE);
        ItemMeta meta = goldenHoe.getItemMeta();
        Objects.requireNonNull(meta).setDisplayName("§eSelection Tool");
        goldenHoe.setItemMeta(meta);
        player.getInventory().addItem(goldenHoe);
        player.sendMessage("§aYou have been given the Selection Tool!");
    }

    @EventHandler
    public void onPlayerInteract(@NotNull PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item.getType() == Material.GOLDEN_HOE && item.hasItemMeta() && Objects.requireNonNull(item.getItemMeta()).getDisplayName().equals("§eSelection Tool")) {
            event.setCancelled(true);

            UUID playerId = player.getUniqueId();
            Vector[] corners = selections.getOrDefault(playerId, new Vector[2]);

            if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
                corners[0] = Objects.requireNonNull(event.getClickedBlock()).getLocation().toVector();
                player.sendMessage("§aFirst corner set at: " + formatVector(corners[0]));
            } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                corners[1] = Objects.requireNonNull(event.getClickedBlock()).getLocation().toVector();
                player.sendMessage("§aSecond corner set at: " + formatVector(corners[1]));
            }

            selections.put(playerId, corners);
        }
    }

    public Vector[] getSelection(@NotNull Player player) {
        return selections.get(player.getUniqueId());
    }

    public void clearSelection(@NotNull Player player) {
        selections.remove(player.getUniqueId());
    }

    private String formatVector(Vector vector) {
        return "(" + vector.getBlockX() + ", " + vector.getBlockY() + ", " + vector.getBlockZ() + ")";
    }
}