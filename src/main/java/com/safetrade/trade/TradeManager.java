package com.safetrade.trade;

import com.safetrade.SafeTradePlugin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TradeManager {

    private final Map<UUID, TradeSession> sessions = new HashMap<>();
    private final Map<UUID, UUID> pendingRequests = new HashMap<>();
    private final SafeTradePlugin plugin;

    public TradeManager(SafeTradePlugin plugin) {
        this.plugin = plugin;
    }

    public boolean sendTradeRequest(Player requester, Player target) {
        if (requester.equals(target)) {
            requester.sendMessage("You cannot trade with yourself.");
            return false;
        }
        if (getSession(requester) != null || getSession(target) != null) {
            requester.sendMessage("One of the players is already trading.");
            return false;
        }

        UUID existingRequester = pendingRequests.get(target.getUniqueId());
        if (existingRequester != null) {
            if (existingRequester.equals(requester.getUniqueId())) {
                requester.sendMessage("Trade request already sent.");
            } else {
                requester.sendMessage("That player already has a pending trade request.");
            }
            return false;
        }

        pendingRequests.put(target.getUniqueId(), requester.getUniqueId());
        requester.sendMessage("Trade request sent to " + target.getName() + ".");
        return true;
    }

    public boolean acceptRequest(Player target, Player requester) {
        UUID requesterId = pendingRequests.get(target.getUniqueId());
        if (requesterId == null || !requesterId.equals(requester.getUniqueId())) {
            target.sendMessage("You do not have a trade request from that player.");
            return false;
        }

        pendingRequests.remove(target.getUniqueId());
        createSession(requester, target);
        requester.sendMessage(target.getName() + " accepted your trade request.");
        target.sendMessage("Trade started with " + requester.getName() + ".");
        return true;
    }

    public boolean denyRequest(Player target, Player requester) {
        UUID requesterId = pendingRequests.get(target.getUniqueId());
        if (requesterId == null || !requesterId.equals(requester.getUniqueId())) {
            target.sendMessage("You do not have a trade request from that player.");
            return false;
        }

        pendingRequests.remove(target.getUniqueId());
        requester.sendMessage(target.getName() + " denied your trade request.");
        target.sendMessage("Trade request denied.");
        return true;
    }

    public Player getRequester(Player target) {
        UUID requesterId = pendingRequests.get(target.getUniqueId());
        if (requesterId == null) {
            return null;
        }
        return plugin.getServer().getPlayer(requesterId);
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
            boolean success = TradeExecutor.execute(s);
            if (!success) {
                s.getA().sendMessage("Trade failed.");
                s.getB().sendMessage("Trade failed.");
            } else {
                s.getA().sendMessage("Trade completed.");
                s.getB().sendMessage("Trade completed.");
            }

            removeSession(s);
            s.getA().closeInventory();
            s.getB().closeInventory();
        }
    }

    public TradeSession getSession(Player p) {
        return sessions.get(p.getUniqueId());
    }

    public void cancelSession(TradeSession s) {
        if (s == null || !isActive(s)) {
            return;
        }

        returnItems(s.getA(), s.getOfferA());
        returnItems(s.getB(), s.getOfferB());
        removeSession(s);

        s.getA().sendMessage("Trade cancelled.");
        s.getB().sendMessage("Trade cancelled.");
        s.getA().closeInventory();
        s.getB().closeInventory();
    }

    private void returnItems(Player player, List<ItemStack> items) {
        for (ItemStack item : items) {
            player.getInventory().addItem(item.clone());
        }
    }

    private boolean isActive(TradeSession s) {
        return sessions.get(s.getA().getUniqueId()) == s && sessions.get(s.getB().getUniqueId()) == s;
    }

    private void removeSession(TradeSession s) {
        sessions.remove(s.getA().getUniqueId());
        sessions.remove(s.getB().getUniqueId());
    }
}
