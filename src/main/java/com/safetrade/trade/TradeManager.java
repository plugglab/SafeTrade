package com.safetrade.trade;

import com.safetrade.SafeTradePlugin;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class TradeManager {

    private final Map<UUID, TradeSession> sessions = new HashMap<>();
    private final Map<UUID, TradeSession> holds = new HashMap<>();
    private final Map<UUID, PendingRequest> pendingRequests = new HashMap<>();
    private final Map<UUID, Long> requestCooldowns = new HashMap<>();
    private final Map<UUID, Long> tradeCooldowns = new HashMap<>();
    private final SafeTradePlugin plugin;
    private final TradeHistoryStore historyStore;
    private final TradeHoldStore holdStore;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public TradeManager(SafeTradePlugin plugin) {
        this.plugin = plugin;
        this.historyStore = new TradeHistoryStore(plugin);
        this.holdStore = new TradeHoldStore(plugin);
    }

    public SafeTradePlugin getPlugin() {
        return plugin;
    }

    public TradeHistoryStore getHistoryStore() {
        return historyStore;
    }

    public TradeHoldStore getHoldStore() {
        return holdStore;
    }

    public boolean sendTradeRequest(Player requester, Player target) {
        cleanupExpiredRequest(target);
        cleanupExpiredRequest(requester);

        if (requester.equals(target)) {
            requester.sendMessage(plugin.getPrefixedText("messages.cannot-trade-self", "&cYou cannot trade with yourself."));
            return false;
        }
        if (getSession(requester) != null || getSession(target) != null) {
            requester.sendMessage(plugin.getPrefixedText("messages.player-already-trading", "&cOne of the players is already in a trade."));
            return false;
        }
        if (isOnTradeCooldown(requester) || isOnTradeCooldown(target)) {
            requester.sendMessage(plugin.getPrefixedText("messages.trade-abuse-cooldown", "&cOne of the players must wait before starting another trade."));
            return false;
        }
        if (!validateTradeAccess(requester, target, requester, false)) {
            return false;
        }

        PendingRequest reverseRequest = pendingRequests.get(requester.getUniqueId());
        if (reverseRequest != null && reverseRequest.requesterId().equals(target.getUniqueId())) {
            pendingRequests.remove(requester.getUniqueId());
            createSession(requester, target);
            requester.sendMessage(plugin.formatPrefixed("messages.trade-request-accepted", "&a{player} accepted your trade request.", target.getName()));
            target.sendMessage(plugin.formatPrefixed("messages.trade-started", "&aTrade started with &b{player}&a.", requester.getName()));
            return true;
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
        if (!validateTradeAccess(requester, target, target, false)) {
            pendingRequests.remove(target.getUniqueId());
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

    public TradeSession getHoldSession(Player p) {
        return holds.get(p.getUniqueId());
    }

    public void onOfferChanged(TradeSession session) {
        if (session == null) {
            return;
        }

        cancelCompletionTask(session);
        session.resetAccept();
    }

    public void cancelSession(TradeSession s) {
        if (s == null || (!isActive(s) && !isHeld(s))) {
            return;
        }

        if (s.isSafetyActive()) {
            cancelSafetyHold(s);
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
        if (playerA == null && playerB == null) {
            admin.sendMessage(plugin.getPrefixedText("messages.admin-rollback-players-offline", "&cAt least one player must be online for rollback."));
            return false;
        }
        if (!canRollbackRecord(record)) {
            admin.sendMessage(plugin.getPrefixedText("messages.admin-rollback-too-old", "&cRollback failed because this trade is older than the allowed rollback age."));
            return false;
        }
        TradeHoldStore.TradeHoldRecord holdRecord = holdStore.getHold(tradeId);
        if (holdRecord == null) {
            admin.sendMessage(plugin.getPrefixedText("messages.admin-rollback-hold-missing", "&cRollback failed because the hold record no longer exists."));
            return false;
        }
        if (holdRecord.playerAReleased() || holdRecord.playerBReleased()) {
            admin.sendMessage(plugin.getPrefixedText("messages.admin-rollback-items-missing", "&cRollback failed because the traded items are no longer fully available."));
            return false;
        }

        TradeSession holdSession = playerA != null ? getHoldSession(playerA) : null;
        if (holdSession == null && playerB != null) {
            holdSession = getHoldSession(playerB);
        }
        if (holdSession != null && tradeId.equals(holdSession.getHistoryId())) {
            int safetyTaskId = holdSession.getSafetyTaskId();
            if (safetyTaskId != -1) {
                Bukkit.getScheduler().cancelTask(safetyTaskId);
            }
            holdSession.setSafetyActive(false);
            holdSession.setSafetyTaskId(-1);
            holdSession.setSafetyEndsAt(0L);
        }

        historyStore.markRolledBack(tradeId);
        if (playerA != null) {
            releaseRollbackHoldToPlayer(holdRecord, playerA);
        }
        if (playerB != null) {
            releaseRollbackHoldToPlayer(holdRecord, playerB);
        }
        cleanupHoldIfFullyReleased(tradeId, holdSession);

        admin.sendMessage(plugin.formatValuePrefixed("messages.admin-rollback-success", "&aTrade &b{value}&a was rolled back.", tradeId));
        if (playerA != null) {
            playerA.sendMessage(plugin.getPrefixedText("messages.admin-rollback-player-notice", "&eA previous trade involving you was rolled back by staff."));
        }
        if (playerB != null) {
            playerB.sendMessage(plugin.getPrefixedText("messages.admin-rollback-player-notice", "&eA previous trade involving you was rolled back by staff."));
        }
        return true;
    }

    private void returnItems(Player player, List<ItemStack> items) {
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

    public boolean canOfferItem(Player player, ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }

        Material material = item.getType();
        for (String blocked : plugin.getConfig().getStringList("settings.blocked-materials")) {
            Material blockedMaterial = Material.matchMaterial(blocked);
            if (blockedMaterial != null && blockedMaterial == material) {
                player.sendMessage(plugin.formatValuePrefixed(
                        "messages.trade-blocked-item",
                        "&cThat item cannot be traded: &b{value}",
                        material.name()
                ));
                return false;
            }
        }

        return true;
    }

    private boolean isActive(TradeSession s) {
        return sessions.get(s.getA().getUniqueId()) == s && sessions.get(s.getB().getUniqueId()) == s;
    }

    private boolean isHeld(TradeSession s) {
        return holds.get(s.getA().getUniqueId()) == s && holds.get(s.getB().getUniqueId()) == s;
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
        if (!validateTradeAccess(s.getA(), s.getB(), s.getA(), true)
                || !validateTradeAccess(s.getA(), s.getB(), s.getB(), true)) {
            cancelSession(s);
            return;
        }

        boolean success = TradeExecutor.execute(s);
        if (!success) {
            s.getA().sendMessage(plugin.getPrefixedText("messages.trade-failed", "&cTrade failed."));
            s.getB().sendMessage(plugin.getPrefixedText("messages.trade-failed", "&cTrade failed."));
        } else {
            historyStore.addRecord(s);
            markTradeCooldown(s.getA(), s.getB());
            startSafetyHold(s);
            s.getA().sendMessage(plugin.getPrefixedText("messages.trade-complete", "&aTrade completed successfully."));
            s.getB().sendMessage(plugin.getPrefixedText("messages.trade-complete", "&aTrade completed successfully."));
            sendWebhook(s);
        }
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

    private void startSafetyHold(TradeSession session) {
        int seconds = Math.max(0, plugin.getConfig().getInt("settings.safety-hold-seconds", 120));
        if (seconds <= 0) {
            return;
        }

        session.setSafetyActive(true);
        session.setSafetyEndsAt(System.currentTimeMillis() + seconds * 1000L);
        holdStore.saveHold(session);
        holds.put(session.getA().getUniqueId(), session);
        holds.put(session.getB().getUniqueId(), session);
        sessions.remove(session.getA().getUniqueId());
        sessions.remove(session.getB().getUniqueId());
        TradeSafetyGUI.open(session);
        int task = Bukkit.getScheduler().runTaskLater(plugin, () -> releaseSafetyHold(session), seconds * 20L).getTaskId();
        session.setSafetyTaskId(task);
    }

    private void releaseSafetyHold(TradeSession session) {
        if (session == null || !session.isSafetyActive()) {
            return;
        }
        String tradeId = session.getHistoryId();
        if (tradeId.isBlank()) {
            cleanupReleasedSession(session);
            return;
        }

        TradeHoldStore.TradeHoldRecord holdRecord = holdStore.getHold(tradeId);
        if (holdRecord == null) {
            cleanupReleasedSession(session);
            return;
        }

        releaseHoldToOnlinePlayer(holdRecord, session.getA());
        releaseHoldToOnlinePlayer(holdRecord, session.getB());
        cleanupHoldIfFullyReleased(tradeId, session);
    }

    private void cancelSafetyHold(TradeSession session) {
        int taskId = session.getSafetyTaskId();
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
        session.setSafetyActive(false);
        session.setSafetyTaskId(-1);
        if (!session.getHistoryId().isBlank()) {
            holdStore.removeHold(session.getHistoryId());
        }
        holds.remove(session.getA().getUniqueId());
        holds.remove(session.getB().getUniqueId());
        removeSession(session);
    }

    public void releaseExpiredHolds(Player player) {
        long now = System.currentTimeMillis();
        int holdSeconds = Math.max(0, plugin.getConfig().getInt("settings.safety-hold-seconds", 120));
        long holdMillis = holdSeconds * 1000L;
        for (TradeHoldStore.TradeHoldRecord holdRecord : holdStore.getHoldsForPlayer(player.getUniqueId())) {
            TradeRecord record = historyStore.getRecord(holdRecord.tradeId());
            if (record != null && record.rolledBack()) {
                TradeSession session = getHoldSession(player);
                if (releaseRollbackHoldToPlayer(holdRecord, player)) {
                    cleanupHoldIfFullyReleased(holdRecord.tradeId(), session);
                    player.sendMessage(plugin.getPrefixedText("messages.admin-rollback-player-notice", "&eA previous trade involving you was rolled back by staff."));
                }
                continue;
            }
            if (now - holdRecord.createdAt() < holdMillis) {
                continue;
            }
            TradeSession session = getHoldSession(player);
            if (releaseHoldToPlayer(holdRecord, player)) {
                cleanupHoldIfFullyReleased(holdRecord.tradeId(), session);
            }
        }
    }

    private void releaseHoldToOnlinePlayer(TradeHoldStore.TradeHoldRecord holdRecord, Player player) {
        if (player != null && player.isOnline()) {
            releaseHoldToPlayer(holdRecord, player);
        }
    }

    private boolean releaseHoldToPlayer(TradeHoldStore.TradeHoldRecord holdRecord, Player player) {
        if (holdRecord.playerAId().equals(player.getUniqueId())) {
            if (holdRecord.playerAReleased()) {
                return false;
            }
            withdrawMoney(player, holdRecord.moneyA());
            depositMoney(player, holdRecord.moneyB());
            returnItems(player, holdStore.deserializeItems(holdRecord.offerB()));
            holdStore.markReleased(holdRecord.tradeId(), player.getUniqueId());
            holds.remove(player.getUniqueId());
            player.sendMessage(plugin.getPrefixedText("messages.trade-safety-released", "&aYour trade items have been released."));
            player.closeInventory();
            return true;
        }
        if (holdRecord.playerBId().equals(player.getUniqueId())) {
            if (holdRecord.playerBReleased()) {
                return false;
            }
            withdrawMoney(player, holdRecord.moneyB());
            depositMoney(player, holdRecord.moneyA());
            returnItems(player, holdStore.deserializeItems(holdRecord.offerA()));
            holdStore.markReleased(holdRecord.tradeId(), player.getUniqueId());
            holds.remove(player.getUniqueId());
            player.sendMessage(plugin.getPrefixedText("messages.trade-safety-released", "&aYour trade items have been released."));
            player.closeInventory();
            return true;
        }
        return false;
    }

    private boolean releaseRollbackHoldToPlayer(TradeHoldStore.TradeHoldRecord holdRecord, Player player) {
        if (holdRecord.playerAId().equals(player.getUniqueId())) {
            if (holdRecord.playerAReleased()) {
                return false;
            }
            returnItems(player, holdStore.deserializeItems(holdRecord.offerA()));
            holdStore.markReleased(holdRecord.tradeId(), player.getUniqueId());
            holds.remove(player.getUniqueId());
            player.closeInventory();
            return true;
        }
        if (holdRecord.playerBId().equals(player.getUniqueId())) {
            if (holdRecord.playerBReleased()) {
                return false;
            }
            returnItems(player, holdStore.deserializeItems(holdRecord.offerB()));
            holdStore.markReleased(holdRecord.tradeId(), player.getUniqueId());
            holds.remove(player.getUniqueId());
            player.closeInventory();
            return true;
        }
        return false;
    }

    private void cleanupHoldIfFullyReleased(String tradeId, TradeSession session) {
        TradeHoldStore.TradeHoldRecord latest = holdStore.getHold(tradeId);
        if (latest != null && latest.playerAReleased() && latest.playerBReleased()) {
            holdStore.removeHold(tradeId);
            holds.remove(latest.playerAId());
            holds.remove(latest.playerBId());
            if (session != null) {
                cleanupReleasedSession(session);
            }
        }
    }

    private void cleanupReleasedSession(TradeSession session) {
        session.setSafetyActive(false);
        session.setSafetyTaskId(-1);
        session.setSafetyEndsAt(0L);
        holds.remove(session.getA().getUniqueId());
        holds.remove(session.getB().getUniqueId());
        removeSession(session);
        if (session.getA().isOnline()) {
            session.getA().closeInventory();
        }
        if (session.getB().isOnline()) {
            session.getB().closeInventory();
        }
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

    private boolean isOnTradeCooldown(Player player) {
        int cooldownSeconds = Math.max(0, plugin.getConfig().getInt("settings.trade-start-cooldown-seconds", 0));
        if (cooldownSeconds == 0) {
            return false;
        }
        Long last = tradeCooldowns.get(player.getUniqueId());
        return last != null && System.currentTimeMillis() - last < cooldownSeconds * 1000L;
    }

    private void markTradeCooldown(Player a, Player b) {
        long now = System.currentTimeMillis();
        tradeCooldowns.put(a.getUniqueId(), now);
        tradeCooldowns.put(b.getUniqueId(), now);
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

    private boolean validateTradeAccess(Player playerA, Player playerB, Player messageTarget, boolean activeTradeCheck) {
        if (plugin.isSelfTestUser(playerA) || plugin.isSelfTestUser(playerB)) {
            return true;
        }
        if (!playerA.isOnline() || !playerB.isOnline()) {
            messageTarget.sendMessage(plugin.getPrefixedText("messages.player-offline", "&cThat player is offline."));
            return false;
        }
        if (isBlockedGameMode(playerA) || isBlockedGameMode(playerB)) {
            messageTarget.sendMessage(plugin.getPrefixedText("messages.trade-blocked-gamemode", "&cTrade is blocked while one of the players is in a restricted gamemode."));
            return false;
        }
        if (isBlockedWorld(playerA) || isBlockedWorld(playerB)) {
            messageTarget.sendMessage(plugin.getPrefixedText("messages.trade-blocked-world", "&cTrading is blocked in this world."));
            return false;
        }

        boolean requireSameWorld = plugin.getConfig().getBoolean("settings.require-same-world", true);
        if (requireSameWorld && !playerA.getWorld().equals(playerB.getWorld())) {
            messageTarget.sendMessage(plugin.getPrefixedText("messages.trade-different-world", "&cBoth players must be in the same world to trade."));
            return false;
        }

        double maxDistance = Math.max(0D, plugin.getConfig().getDouble("settings.max-trade-distance", 10D));
        if (maxDistance > 0D && playerA.getWorld().equals(playerB.getWorld())) {
            double maxDistanceSquared = maxDistance * maxDistance;
            if (playerA.getLocation().distanceSquared(playerB.getLocation()) > maxDistanceSquared) {
                String path = activeTradeCheck ? "messages.trade-too-far-active" : "messages.trade-too-far";
                String fallback = activeTradeCheck
                        ? "&cTrade cancelled because the players moved too far apart."
                        : "&cThat player is too far away to trade. You must be within {amount} blocks.";
                messageTarget.sendMessage(plugin.formatNumberPrefixed(path, fallback, (long) Math.round(maxDistance)));
                return false;
            }
        }

        return true;
    }

    public boolean setMoney(Player player, double amount) {
        TradeSession session = getSession(player);
        if (session == null) {
            player.sendMessage(plugin.getPrefixedText("messages.no-active-trade-or-request", "&cYou do not have an active trade or pending request."));
            return false;
        }
        if (!plugin.isVaultEnabled()) {
            player.sendMessage(plugin.getPrefixedText("messages.vault-unavailable", "&cMoney trades are disabled because Vault is unavailable."));
            return false;
        }
        if (!plugin.getConfig().getBoolean("settings.enable-money-trades", true)) {
            player.sendMessage(plugin.getPrefixedText("messages.vault-unavailable", "&cMoney trades are disabled."));
            return false;
        }
        double max = Math.max(0D, plugin.getConfig().getDouble("settings.max-money-per-player", 0D));
        if (max > 0D && amount > max) {
            player.sendMessage(plugin.formatValuePrefixed("messages.money-too-high", "&cThat amount exceeds the limit: {value}", String.valueOf(max)));
            return false;
        }
        if (!Double.isFinite(amount) || amount <= 0D) {
            player.sendMessage(plugin.getPrefixedText("messages.money-invalid", "&cEnter a valid amount."));
            return false;
        }
        if (getBalance(player) < amount) {
            player.sendMessage(plugin.getPrefixedText("messages.money-insufficient", "&cYou do not have enough money."));
            return false;
        }
        if (player.equals(session.getA())) {
            session.setMoneyA(amount);
        } else {
            session.setMoneyB(amount);
        }
        onOfferChanged(session);
        TradeGUI.update(player.getOpenInventory().getTopInventory(), session);
        return true;
    }

    public double getBalance(Player player) {
        Object economy = plugin.getEconomy();
        if (economy == null) {
            return 0D;
        }
        try {
            Object result = economy.getClass().getMethod("getBalance", OfflinePlayer.class).invoke(economy, player);
            if (result instanceof Number number) {
                return number.doubleValue();
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return 0D;
    }

    public boolean withdrawMoney(Player player, double amount) {
        if (!Double.isFinite(amount) || amount <= 0D || !plugin.isVaultEnabled()) {
            return false;
        }
        return invokeEconomyBoolean("withdrawPlayer", new Class<?>[]{OfflinePlayer.class, double.class}, new Object[]{player, amount});
    }

    public boolean depositMoney(Player player, double amount) {
        if (!Double.isFinite(amount) || amount <= 0D || !plugin.isVaultEnabled()) {
            return false;
        }
        return invokeEconomyBoolean("depositPlayer", new Class<?>[]{OfflinePlayer.class, double.class}, new Object[]{player, amount});
    }

    public boolean payMoney(Player sender, Player target, double amount) {
        if (sender.equals(target)) {
            sender.sendMessage(plugin.getPrefixedText("messages.money-pay-self", "&cYou cannot send money to yourself."));
            return false;
        }
        if (!plugin.isVaultEnabled()) {
            sender.sendMessage(plugin.getPrefixedText("messages.vault-unavailable", "&cMoney trades are disabled because Vault is unavailable."));
            return false;
        }
        if (!Double.isFinite(amount) || amount <= 0D) {
            sender.sendMessage(plugin.getPrefixedText("messages.money-invalid", "&cEnter a valid amount."));
            return false;
        }
        if (getBalance(sender) < amount) {
            sender.sendMessage(plugin.getPrefixedText("messages.money-insufficient", "&cYou do not have enough money."));
            return false;
        }

        boolean withdrawn = invokeEconomyBoolean("withdrawPlayer", new Class<?>[]{Player.class, double.class}, new Object[]{sender, amount});
        if (!withdrawn) {
            sender.sendMessage(plugin.getPrefixedText("messages.money-pay-failed", "&cMoney transfer failed."));
            return false;
        }

        boolean deposited = invokeEconomyBoolean("depositPlayer", new Class<?>[]{Player.class, double.class}, new Object[]{target, amount});
        if (!deposited) {
            invokeEconomyBoolean("depositPlayer", new Class<?>[]{Player.class, double.class}, new Object[]{sender, amount});
            sender.sendMessage(plugin.getPrefixedText("messages.money-pay-failed", "&cMoney transfer failed."));
            return false;
        }

        sender.sendMessage(plugin.formatValuePrefixed("messages.money-pay-sent", "&aYou sent &e{amount}&a to &b{player}&a.", formatCurrency(amount)).replace("{player}", target.getName()));
        target.sendMessage(plugin.formatValuePrefixed("messages.money-pay-received", "&aYou received &e{amount}&a from &b{player}&a.", formatCurrency(amount)).replace("{player}", sender.getName()));
        return true;
    }

    private boolean invokeEconomyBoolean(String methodName, Class<?>[] signature, Object[] args) {
        Object economy = plugin.getEconomy();
        if (economy == null) {
            return false;
        }
        try {
            Object result = economy.getClass().getMethod(methodName, signature).invoke(economy, args);
            if (result instanceof Boolean bool) {
                return bool;
            }
            if (result != null) {
                try {
                    return (boolean) result.getClass().getMethod("transactionSuccess").invoke(result);
                } catch (ReflectiveOperationException ignored) {
                }
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return false;
    }

    private String formatCurrency(double amount) {
        if (Math.rint(amount) == amount) {
            return String.valueOf((long) amount);
        }
        return String.valueOf(amount);
    }

    private void sendWebhook(TradeSession session) {
        if (!plugin.getConfig().getBoolean("settings.send-webhook-on-trade", true)) {
            return;
        }
        String url = plugin.getConfig().getString("settings.discord-webhook-url", "");
        if (url == null || url.isBlank()) {
            return;
        }
        try {
            JsonObject embed = new JsonObject();
            embed.addProperty("title", "SafeTrade completed");
            embed.addProperty("description", "**" + escapeDiscord(session.getA().getName()) + "** traded with **" + escapeDiscord(session.getB().getName()) + "**.");
            embed.addProperty("color", 0x23D18B);

            JsonArray fields = new JsonArray();
            fields.add(field("Trade ID", session.getHistoryId().isBlank() ? "Unknown" : "`" + session.getHistoryId() + "`", true));
            fields.add(field("Safety hold", Math.max(0, plugin.getConfig().getInt("settings.safety-hold-seconds", 120)) + " seconds", true));
            fields.add(field("Status", "Items are held until release or staff rollback.", false));
            fields.add(field(session.getA().getName(), playerTradeSummary(session.getA(), session.getMoneyA(), session.getOfferA()), true));
            fields.add(field(session.getB().getName(), playerTradeSummary(session.getB(), session.getMoneyB(), session.getOfferB()), true));
            fields.add(field("Items from " + session.getA().getName(), itemSummary(session.getOfferA()), false));
            fields.add(field("Items from " + session.getB().getName(), itemSummary(session.getOfferB()), false));
            embed.add("fields", fields);

            JsonObject author = new JsonObject();
            author.addProperty("name", "SafeTrade");
            embed.add("author", author);

            JsonObject footer = new JsonObject();
            footer.addProperty("text", plugin.getServer().getName());
            embed.add("footer", footer);
            embed.addProperty("timestamp", java.time.Instant.now().toString());

            JsonArray embeds = new JsonArray();
            embeds.add(embed);

            JsonObject payload = new JsonObject();
            payload.addProperty("content", "");
            payload.add("embeds", embeds);

            httpClient.send(HttpRequest.newBuilder(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                    .build(), HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) {
        }
    }

    private JsonObject field(String name, String value, boolean inline) {
        JsonObject field = new JsonObject();
        field.addProperty("name", name);
        field.addProperty("value", clampDiscord(value == null || value.isBlank() ? "-" : value, 1024));
        field.addProperty("inline", inline);
        return field;
    }

    private String playerTradeSummary(Player player, double money, List<ItemStack> items) {
        return "**UUID:** `" + player.getUniqueId() + "`\n"
                + "**Money offered:** `" + formatCurrency(money) + "`\n"
                + "**Item stacks:** `" + items.size() + "`";
    }

    private String itemSummary(List<ItemStack> items) {
        if (items == null || items.isEmpty()) {
            return "No items";
        }

        Map<String, Integer> counts = new HashMap<>();
        for (ItemStack item : items) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            counts.merge(item.getType().name(), item.getAmount(), Integer::sum);
        }
        if (counts.isEmpty()) {
            return "No items";
        }

        List<String> lines = new ArrayList<>();
        int shown = 0;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (shown >= 10) {
                lines.add("...and " + (counts.size() - shown) + " more item types");
                break;
            }
            lines.add("`" + entry.getValue() + "x` " + entry.getKey());
            shown++;
        }
        return String.join("\n", lines);
    }

    private String clampDiscord(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private String escapeDiscord(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("*", "\\*")
                .replace("_", "\\_")
                .replace("`", "\\`")
                .replace("~", "\\~");
    }

    private boolean isBlockedWorld(Player player) {
        String worldName = player.getWorld().getName();
        for (String blockedWorld : plugin.getConfig().getStringList("settings.blocked-worlds")) {
            if (worldName.equalsIgnoreCase(blockedWorld)) {
                return true;
            }
        }
        return false;
    }

    private boolean isBlockedGameMode(Player player) {
        GameMode gameMode = player.getGameMode();
        for (String blockedMode : plugin.getConfig().getStringList("settings.blocked-gamemodes")) {
            try {
                if (gameMode == GameMode.valueOf(blockedMode.toUpperCase())) {
                    return true;
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        return false;
    }

    private boolean canRollbackRecord(TradeRecord record) {
        long maxAgeHours = Math.max(0L, plugin.getConfig().getLong("settings.rollback-max-age-hours", 0L));
        if (maxAgeHours == 0L) {
            return true;
        }

        Instant tradeTime = Instant.ofEpochMilli(record.createdAt());
        Instant cutoff = Instant.now().minus(Duration.ofHours(maxAgeHours));
        return !tradeTime.isBefore(cutoff);
    }

    private record PendingRequest(UUID requesterId, long createdAt) {
    }
}
