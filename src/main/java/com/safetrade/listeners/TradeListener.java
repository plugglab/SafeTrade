package com.safetrade.listeners;

import com.safetrade.SafeTradePlugin;
import com.safetrade.trade.TradeGUI;
import com.safetrade.trade.TradeManager;
import com.safetrade.trade.TradeSession;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class TradeListener implements Listener {

    private final SafeTradePlugin plugin;
    private final TradeManager manager;

    public TradeListener(SafeTradePlugin plugin, TradeManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler
    public void onShiftRightClick(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!(event.getRightClicked() instanceof Player target)) {
            return;
        }

        Player requester = event.getPlayer();
        if (!requester.isSneaking()) {
            return;
        }

        event.setCancelled(true);
        plugin.sendTradeRequest(requester, target);
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
        ClickType click = e.getClick();

        if (click == ClickType.NUMBER_KEY || click == ClickType.SWAP_OFFHAND || click == ClickType.DOUBLE_CLICK) {
            return;
        }

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
            if (!manager.canOfferItem(p, item)) {
                return;
            }

            int requestedAmount = e.getClick() == ClickType.RIGHT ? item.getAmount() : 1;
            int movableAmount = Math.min(requestedAmount, s.getOfferSpaceFor(p, item));
            if (movableAmount <= 0) {
                p.sendMessage(manager.getPlugin().getPrefixedText("messages.trade-offer-full", "&cYour trade offer is full."));
                return;
            }

            ItemStack movedItem = item.clone();
            movedItem.setAmount(movableAmount);

            s.addOfferItem(p, movedItem);
            manager.onOfferChanged(s);
            int remainingAmount = item.getAmount() - movableAmount;
            if (remainingAmount <= 0) {
                p.getInventory().setItem(e.getSlot(), null);
            } else {
                item.setAmount(remainingAmount);
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

            int removeAmount = e.getClick() == ClickType.RIGHT ? item.getAmount() : 1;
            ItemStack removedItem = s.removeOfferItem(p, item, removeAmount);
            if (removedItem != null) {
                manager.onOfferChanged(s);
                p.getInventory().addItem(removedItem).values()
                        .forEach(leftover -> p.getWorld().dropItemNaturally(p.getLocation(), leftover));
                TradeGUI.update(e.getInventory(), s);
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!TradeGUI.isTradeInventory(event.getView())) {
            return;
        }
        if (manager.getSession(player) == null) {
            return;
        }

        event.setCancelled(true);
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

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (manager.getSession(event.getPlayer()) == null) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        if (manager.getSession(event.getPlayer()) == null) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.cancelSession(event.getPlayer());
    }

    @EventHandler
    public void onKick(PlayerKickEvent event) {
        manager.cancelSession(event.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        manager.cancelSession(event.getEntity());
    }
}
