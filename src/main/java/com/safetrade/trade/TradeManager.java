package com.safetrade.trade;

import com.safetrade.SafeTradePlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class TradeManager {

    private final Map<UUID, TradeSession> sessions = new HashMap<>();
    private final Map<UUID, PendingRequest> pendingRequests = new HashMap<>();
    private final Map<UUID, Long> requestCooldowns = new HashMap<>();
    private final SafeTradePlugin plugin;
    private final TradeHistoryStore historyStore;

    public TradeManager(SafeTradePlugin plugin) {
        this.plugin = plugin;
        this.historyStore = new TradeHistoryStore(plugin);
    }

    public SafeTradePlugin getPlugin() {
        return plugin;
    }

    public TradeHistoryStore getHistoryStore() {
        return historyStore;
    }

    public boolean sendTradeRequest(Player requester, Player target) {
        cleanupExpiredRequest(target);

        if (requester.equals(target)) {
            requester.sendMessage(plugin.getPrefixedText("messages.cannot-trade-self", "&cYou cannot trade with yourself."));
            return false;
        }
        if (getSession(requester) != null || getSession(target) != null) {
            requester.sendMessage(plugin.getPrefixedText("messages.player-already-trading", "&cOne of the players is already in a trade."));
            return false;
        }
        if (isOnRequestCooldown(requester)) {
            long seconds = getRemainingCooldownSeconds(requester);
            requester.sendMessage(plugin.formatNumberPrefixed("messages.request-cooldown", "&cYou must wait {seconds}s before sending another trade request.", seconds));
            return false;
        }

        PendingRequest existingRequester = pendingRequests.get(target.getUniqueId());
        if (existingRequester != null) {
            if (existingRequester.requesterId().equals(requester.getUniqueId())) {
                requester.sendMessage(plugin.getPrefixedText("messages.request-already-sent", "&eYou already sent that trade request."));
            } else {
                requester.sendMessage(plugin.getPrefixedText("messages.target-has-pending-request", "&cThat player already has a pending trade request."));
            }
            return false;
        }

        pendingRequests.put(target.getUniqueId(), new PendingRequest(requester.getUniqueId(), System.currentTimeMillis()));
        requestCooldowns.put(requester.getUniqueId(), System.currentTimeMillis());
        requester.sendMessage(plugin.formatPrefixed("messages.trade-request-sent", "&aTrade request sent to &b{player}&a.", target.getName()));
        return true;
    }

    public boolean acceptRequest(Player target, Player requester) {
        cleanupExpiredRequest(target);

        PendingRequest request = pendingRequests.get(target.getUniqueId());
        if (request == null || !request.requesterId().equals(requester.getUniqueId())) {
            target.sendMessage(plugin.getPrefixedText("messages.request-not-found", "&cYou do not have a trade request from that player."));
            return false;
        }

        pendingRequests.remove(target.getUniqueId());
        createSession(requester, target);
        requester.sendMessage(plugin.formatPrefixed("messages.trade-request-accepted", "&a{player} accepted your trade request.", target.getName()));
        target.sendMessage(plugin.formatPrefixed("messages.trade-started", "&aTrade started with &b{player}&a.", requester.getName()));
        return true;
    }

    public boolean denyRequest(Player target, Player requester) {
        cleanupExpiredRequest(target);

        PendingRequest request = pendingRequests.get(target.getUniqueId());
        if (request == null || !request.requesterId().equals(requester.getUniqueId())) {
            target.sendMessage(plugin.getPrefixedText("messages.request-not-found", "&cYou do not have a trade request from that player."));
            return false;
        }

        pendingRequests.remove(target.getUniqueId());
        requester.sendMessage(plugin.formatPrefixed("messages.trade-request-denied-by-target", "&c{player} denied your trade request.", target.getName()));
        target.sendMessage(plugin.getPrefixedText("messages.trade-request-denied", "&eTrade request denied."));
        return true;
    }

    public Player getRequester(Player target) {
        cleanupExpiredRequest(target);

        PendingRequest request = pendingRequests.get(target.getUniqueId());
        if (request == null) {
            return null;
        }
        return plugin.getServer().getPlayer(request.requesterId());
    }

    public void createSession(Player a, Player b) {
        TradeSession session = new TradeSession(a, b);
        sessions.put(a.getUniqueId(), session);
        sessions.put(b.getUniqueId(), session);
        TradeGUI.open(session);
    }

    public void accept(Player p) {
        TradeSession s = sessions.get(p.getUniqueId());
        if (s == null) {
            return;
        }

        s.accept(p);
        if (s.bothAccepted()) {
            startCompletionCountdown(s);
        }
    }

    public TradeSession getSession(Player p) {
        return sessions.get(p.getUniqueId());
    }

    public void onOfferChanged(TradeSession session) {
        if (session == null) {
            return;
        }

        cancelCompletionTask(session);
        session.resetAccept();
    }

    public void cancelSession(TradeSession s) {
        if (s == null || !isActive(s)) {
            return;
        }

        cancelCompletionTask(s);
        returnItems(s.getA(), s.getOfferA());
        returnItems(s.getB(), s.getOfferB());
        removeSession(s);

        s.getA().sendMessage(plugin.getPrefixedText("messages.trade-cancelled", "&eTrade cancelled."));
        s.getB().sendMessage(plugin.getPrefixedText("messages.trade-cancelled", "&eTrade cancelled."));
        s.getA().closeInventory();
        s.getB().closeInventory();
    }

    public void cancelSession(Player player) {
        cancelSession(getSession(player));
    }

    public void cancelAllSessions() {
        Set<TradeSession> activeSessions = new HashSet<>(sessions.values());
        for (TradeSession session : activeSessions) {
            cancelSession(session);
        }
    }

    public boolean rollbackTrade(Player admin, String tradeId) {
        TradeRecord record = historyStore.getRecord(tradeId);
        if (record == null) {
            admin.sendMessage(plugin.getPrefixedText("messages.admin-trade-not-found", "&cTrade record not found."));
            return false;
        }
        if (record.rolledBack()) {
            admin.sendMessage(plugin.getPrefixedText("messages.admin-trade-already-rolled-back", "&eThat trade was already rolled back."));
            return false;
        }

        Player playerA = plugin.getServer().getPlayer(record.playerAId());
        Player playerB = plugin.getServer().getPlayer(record.playerBId());
        if (playerA == null || playerB == null) {
            admin.sendMessage(plugin.getPrefixedText("messages.admin-rollback-players-offline", "&cBoth players must be online for rollback."));
            return false;
        }
        if (getSession(playerA) != null || getSession(playerB) != null) {
            admin.sendMessage(plugin.getPrefixedText("messages.admin-rollback-active-trade", "&cRollback is blocked while one of the players is trading."));
            return false;
        }
        List<Inventory> playerAStorages = getRollbackSearchInventories(playerA);
        List<Inventory> playerBStorages = getRollbackSearchInventories(playerB);
        if (!InventoryUtils.containsAll(playerAStorages, record.offerB())
                || !InventoryUtils.containsAll(playerBStorages, record.offerA())) {
            admin.sendMessage(plugin.getPrefixedText("messages.admin-rollback-items-missing", "&cRollback failed because the traded items are no longer fully available."));
            return false;
        }

        InventoryUtils.removeAll(playerAStorages, record.offerB());
        InventoryUtils.removeAll(playerBStorages, record.offerA());
        give(playerA, record.offerA());
        give(playerB, record.offerB());

        historyStore.markRolledBack(tradeId);
        admin.sendMessage(plugin.formatValuePrefixed("messages.admin-rollback-success", "&aTrade &b{value}&a was rolled back.", tradeId));
        playerA.sendMessage(plugin.getPrefixedText("messages.admin-rollback-player-notice", "&eA previous trade involving you was rolled back by staff."));
        playerB.sendMessage(plugin.getPrefixedText("messages.admin-rollback-player-notice", "&eA previous trade involving you was rolled back by staff."));
        return true;
    }

    private void returnItems(Player player, List<ItemStack> items) {
        for (ItemStack item : items) {
            player.getInventory().addItem(item.clone());
        }
    }

    private void give(Player player, List<ItemStack> items) {
        for (ItemStack item : items) {
            player.getInventory().addItem(item.clone()).values()
                    .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        }
    }

    private List<Inventory> getRollbackSearchInventories(Player player) {
        if (plugin.getConfig().getBoolean("settings.rollback-check-ender-chest", false)) {
            return List.of(player.getInventory(), player.getEnderChest());
        }
        return List.of(player.getInventory());
    }

    private boolean isActive(TradeSession s) {
        return sessions.get(s.getA().getUniqueId()) == s && sessions.get(s.getB().getUniqueId()) == s;
    }

    private void removeSession(TradeSession s) {
        cancelCompletionTask(s);
        sessions.remove(s.getA().getUniqueId());
        sessions.remove(s.getB().getUniqueId());
    }

    private void startCompletionCountdown(TradeSession s) {
        if (s.isCompleting()) {
            return;
        }

        int delaySeconds = Math.max(0, plugin.getConfig().getInt("settings.trade-complete-delay-seconds", 5));
        if (delaySeconds == 0) {
            finishTrade(s);
            return;
        }

        s.setCompleting(true);
        long ticks = delaySeconds * 20L;
        int taskId = Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> finishTrade(s), ticks);
        s.setCompletionTaskId(taskId);

        String countdownMessage = plugin.formatNumberPrefixed(
                "messages.trade-completing",
                "&eTrade will complete in {seconds}s if nothing changes.",
                delaySeconds
        );
        s.getA().sendMessage(countdownMessage);
        s.getB().sendMessage(countdownMessage);
    }

    private void finishTrade(TradeSession s) {
        if (!isActive(s) || !s.bothAccepted()) {
            return;
        }
        if (!s.getA().isOnline() || !s.getB().isOnline()) {
            cancelSession(s);
            return;
        }

        boolean success = TradeExecutor.execute(s);
        if (!success) {
            s.getA().sendMessage(plugin.getPrefixedText("messages.trade-failed", "&cTrade failed."));
            s.getB().sendMessage(plugin.getPrefixedText("messages.trade-failed", "&cTrade failed."));
        } else {
            historyStore.addRecord(s);
            s.getA().sendMessage(plugin.getPrefixedText("messages.trade-complete", "&aTrade completed successfully."));
            s.getB().sendMessage(plugin.getPrefixedText("messages.trade-complete", "&aTrade completed successfully."));
        }

        removeSession(s);
        s.getA().closeInventory();
        s.getB().closeInventory();
    }

    private void cancelCompletionTask(TradeSession s) {
        int taskId = s.getCompletionTaskId();
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
        s.setCompleting(false);
        s.setCompletionTaskId(-1);
    }

    private boolean isOnRequestCooldown(Player requester) {
        int cooldownSeconds = Math.max(0, plugin.getConfig().getInt("settings.request-cooldown-seconds", 3));
        if (cooldownSeconds == 0) {
            return false;
        }

        Long lastRequestAt = requestCooldowns.get(requester.getUniqueId());
        if (lastRequestAt == null) {
            return false;
        }

        long cooldownMillis = cooldownSeconds * 1000L;
        return System.currentTimeMillis() - lastRequestAt < cooldownMillis;
    }

    private long getRemainingCooldownSeconds(Player requester) {
        int cooldownSeconds = Math.max(0, plugin.getConfig().getInt("settings.request-cooldown-seconds", 3));
        Long lastRequestAt = requestCooldowns.get(requester.getUniqueId());
        if (lastRequestAt == null || cooldownSeconds == 0) {
            return 0;
        }

        long cooldownMillis = cooldownSeconds * 1000L;
        long remainingMillis = cooldownMillis - (System.currentTimeMillis() - lastRequestAt);
        return Math.max(1, (long) Math.ceil(remainingMillis / 1000.0));
    }

    private void cleanupExpiredRequest(Player target) {
        PendingRequest request = pendingRequests.get(target.getUniqueId());
        if (request == null) {
            return;
        }

        int expirySeconds = Math.max(0, plugin.getConfig().getInt("settings.request-expire-seconds", 30));
        if (expirySeconds == 0) {
            return;
        }

        long expiryMillis = expirySeconds * 1000L;
        if (System.currentTimeMillis() - request.createdAt() >= expiryMillis) {
            pendingRequests.remove(target.getUniqueId());
        }
    }

    private record PendingRequest(UUID requesterId, long createdAt) {
    }
}
