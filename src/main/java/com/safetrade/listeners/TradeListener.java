package com.safetrade.listeners;

import com.safetrade.trade.TradeGUI;
import com.safetrade.trade.TradeManager;
import com.safetrade.trade.TradeSession;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

public class TradeListener implements Listener {

    private final TradeManager manager;

    public TradeListener(TradeManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) {
            return;
        }
        if (!TradeGUI.isTradeInventory(e.getView())) {
            return;
        }

        e.setCancelled(true);

        TradeSession s = manager.getSession(p);
        if (s == null) {
            return;
        }

        int slot = e.getRawSlot();

        if (TradeGUI.isAcceptSlot(p, s, slot)) {
            manager.accept(p);
            TradeGUI.update(e.getInventory(), s);
            return;
        }

        if (e.getClickedInventory() == p.getInventory()) {
            ItemStack item = e.getCurrentItem();
            if (item == null) {
                return;
            }
            if (getOfferSize(p, s) >= TradeGUI.getOfferSlotLimit()) {
                p.sendMessage(ChatColor.RED + "Your trade offer is full.");
                return;
            }

            ItemStack singleItem = item.clone();
            singleItem.setAmount(1);

            s.addOfferItem(p, singleItem);
            if (item.getAmount() <= 1) {
                p.getInventory().setItem(e.getSlot(), null);
            } else {
                item.setAmount(item.getAmount() - 1);
                p.getInventory().setItem(e.getSlot(), item);
            }
            TradeGUI.update(e.getInventory(), s);
            return;
        }

        if (e.getClickedInventory() == e.getView().getTopInventory() && TradeGUI.isOwnOfferSlot(p, s, slot)) {
            ItemStack item = e.getCurrentItem();
            if (item == null) {
                return;
            }

            if (s.removeOfferItem(p, item)) {
                ItemStack singleItem = item.clone();
                singleItem.setAmount(1);
                p.getInventory().addItem(singleItem);
                TradeGUI.update(e.getInventory(), s);
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) {
            return;
        }
        if (!TradeGUI.isTradeInventory(e.getView())) {
            return;
        }

        TradeSession s = manager.getSession(p);
        if (s != null) {
            manager.cancelSession(s);
        }
    }

    private int getOfferSize(Player p, TradeSession s) {
        return p.equals(s.getA()) ? s.getOfferA().size() : s.getOfferB().size();
    }
}
