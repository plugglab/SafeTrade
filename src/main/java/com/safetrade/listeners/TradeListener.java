package com.safetrade.listeners;

import com.safetrade.SafeTradePlugin;
import com.safetrade.trade.TradeCashItem;
import com.safetrade.trade.TradeGUI;
import com.safetrade.trade.TradeManager;
import com.safetrade.trade.TradeMoneyGUI;
import com.safetrade.trade.TradeSafetyGUI;
import com.safetrade.trade.TradeSession;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.block.Action;
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
    public void onCashUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!TradeCashItem.isCash(item)) {
            return;
        }

        double amount = TradeCashItem.getValue(item);
        if (amount <= 0D) {
            player.sendMessage(manager.getPlugin().getPrefixedText("messages.money-invalid", "&cThat cash paper is invalid."));
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);
        if (!manager.depositMoney(player, amount)) {
            player.sendMessage(manager.getPlugin().getPrefixedText("messages.money-pay-failed", "&cMoney transfer failed."));
            return;
        }
        if (!TradeCashItem.markRedeemed(item)) {
            player.sendMessage(manager.getPlugin().getPrefixedText("messages.money-invalid", "&cThat cash paper was already redeemed."));
            return;
        }

        int newAmount = item.getAmount() - 1;
        if (newAmount <= 0) {
            player.getInventory().setItemInMainHand(null);
        } else {
            item.setAmount(newAmount);
            player.getInventory().setItemInMainHand(item);
        }
        player.sendMessage(manager.getPlugin().formatValuePrefixed("messages.tradecash-redeemed", "&aRedeemed &e{value}&a from cash paper.", String.valueOf(amount)));
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) {
            return;
        }
        if (e.getClickedInventory() == p.getInventory() && TradeCashItem.isCash(e.getCurrentItem()) && e.getClick().isRightClick()) {
            ItemStack item = e.getCurrentItem();
            double amount = TradeCashItem.getValue(item);
            if (amount <= 0D) {
                p.sendMessage(manager.getPlugin().getPrefixedText("messages.money-invalid", "&cThat cash paper is invalid."));
                e.setCancelled(true);
                return;
            }

            e.setCancelled(true);
            if (!manager.depositMoney(p, amount)) {
                p.sendMessage(manager.getPlugin().getPrefixedText("messages.money-pay-failed", "&cMoney transfer failed."));
                return;
            }
            if (!TradeCashItem.markRedeemed(item)) {
                p.sendMessage(manager.getPlugin().getPrefixedText("messages.money-invalid", "&cThat cash paper was already redeemed."));
                return;
            }

            int remaining = item.getAmount() - 1;
            if (remaining <= 0) {
                p.getInventory().setItem(e.getSlot(), null);
            } else {
                item.setAmount(remaining);
                p.getInventory().setItem(e.getSlot(), item);
            }
            p.sendMessage(manager.getPlugin().formatValuePrefixed("messages.tradecash-redeemed", "&aRedeemed &e{value}&a from cash paper.", String.valueOf(amount)));
            return;
        }
        if (!TradeGUI.isTradeInventory(e.getView())
                && !TradeSafetyGUI.isSafetyInventory(e.getView())
                && !TradeMoneyGUI.isMoneyInventory(e.getView())) {
            return;
        }

        e.setCancelled(true);

        if (TradeMoneyGUI.isMoneyInventory(e.getView())) {
            handleMoneyGuiClick(p, e);
            return;
        }

        if (TradeSafetyGUI.isSafetyInventory(e.getView())) {
            return;
        }

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

        if (slot == 4 && e.getClickedInventory() == e.getView().getTopInventory()) {
            s.setSuppressNextTradeClose(true);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (manager.getSession(p) == s) {
                    TradeMoneyGUI.open(p);
                }
            });
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
        if (!TradeGUI.isTradeInventory(event.getView())
                && !TradeSafetyGUI.isSafetyInventory(event.getView())
                && !TradeMoneyGUI.isMoneyInventory(event.getView())) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) {
            return;
        }
        if (!TradeGUI.isTradeInventory(e.getView())
                && !TradeSafetyGUI.isSafetyInventory(e.getView())
                && !TradeMoneyGUI.isMoneyInventory(e.getView())) {
            return;
        }

        if (TradeMoneyGUI.isMoneyInventory(e.getView())) {
            return;
        }
        if (TradeSafetyGUI.isSafetyInventory(e.getView())) {
            return;
        }

        TradeSession s = manager.getSession(p);
        if (s != null && s.isSuppressNextTradeClose()) {
            s.setSuppressNextTradeClose(false);
            return;
        }
        if (s != null && !s.isSafetyActive()) {
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

    private void handleMoneyGuiClick(Player player, InventoryClickEvent e) {
        TradeSession session = manager.getSession(player);
        if (session == null) {
            return;
        }

        int slot = e.getRawSlot();
        String input = session.getMoneyInput(player);
        if (slot >= 10 && slot <= 12) {
            appendMoneyDigit(player, session, String.valueOf(slot - 9));
            return;
        }
        if (slot >= 19 && slot <= 21) {
            appendMoneyDigit(player, session, String.valueOf(slot - 18));
            return;
        }
        if (slot >= 28 && slot <= 30) {
            appendMoneyDigit(player, session, String.valueOf(slot - 27));
            return;
        }
        if (slot == 37) {
            appendMoneyDigit(player, session, ".");
            return;
        }
        if (slot == 38) {
            appendMoneyDigit(player, session, "0");
            return;
        }
        if (slot == 15) {
            if (input != null && !input.isBlank()) {
                TradeMoneyGUI.applyInput(player, input.substring(0, input.length() - 1));
            }
            return;
        }
        if (slot == 24) {
            TradeMoneyGUI.clearInput(player);
            return;
        }
        if (slot == 39) {
            applyMoneyInput(player, session);
            return;
        }
        if (slot == 42) {
            TradeGUI.open(session);
        }
    }

    private void appendMoneyDigit(Player player, TradeSession session, String digit) {
        String input = session.getMoneyInput(player);
        String next = input == null ? "" : input;
        if (".".equals(digit)) {
            if (next.contains(".")) {
                return;
            }
            if (next.isBlank()) {
                next = "0";
            }
        }
        next = next + digit;
        TradeMoneyGUI.applyInput(player, next);
    }

    private void applyMoneyInput(Player player, TradeSession session) {
        String input = session.getMoneyInput(player);
        if (input == null || input.isBlank() || ".".equals(input)) {
            player.sendMessage(manager.getPlugin().getPrefixedText("messages.money-invalid", "&cEnter a valid amount."));
            return;
        }
        try {
            double amount = Double.parseDouble(input);
            if (manager.setMoney(player, amount)) {
                TradeGUI.open(session);
            }
        } catch (NumberFormatException ex) {
            player.sendMessage(manager.getPlugin().getPrefixedText("messages.money-invalid", "&cEnter a valid amount."));
        }
    }
}
