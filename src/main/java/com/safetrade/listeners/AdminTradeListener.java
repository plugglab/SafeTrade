package com.safetrade.listeners;

import com.safetrade.trade.AdminTradeGUI;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

public class AdminTradeListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!AdminTradeGUI.isAdminInventory(event.getView())) {
            return;
        }

        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }

        AdminTradeGUI.handleClick(player, event.getRawSlot());
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (!AdminTradeGUI.isAdminInventory(event.getView())) {
            return;
        }

        AdminTradeGUI.close(player);
    }
}
