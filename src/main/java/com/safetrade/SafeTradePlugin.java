package com.safetrade;

import com.safetrade.listeners.TradeListener;
import com.safetrade.trade.TradeManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class SafeTradePlugin extends JavaPlugin {

    private TradeManager tradeManager;

    @Override
    public void onEnable() {
        this.tradeManager = new TradeManager(this);

        Bukkit.getPluginManager().registerEvents(new TradeListener(tradeManager), this);

        getCommand("trade").setExecutor((sender, cmd, label, args) -> {
            if (!(sender instanceof Player p)) {
                return true;
            }
            if (args.length != 1) {
                p.sendMessage("Usage: /trade <player>");
                return true;
            }

            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                p.sendMessage("That player is offline.");
                return true;
            }

            if (tradeManager.sendTradeRequest(p, target)) {
                sendClickableRequest(p, target);
            }
            return true;
        });

        getCommand("tradeaccept").setExecutor((sender, cmd, label, args) -> {
            if (!(sender instanceof Player target)) {
                return true;
            }

            Player requester = resolveRequester(target, args);
            if (requester == null) {
                target.sendMessage("No matching trade request found.");
                return true;
            }

            tradeManager.acceptRequest(target, requester);
            return true;
        });

        getCommand("tradedeny").setExecutor((sender, cmd, label, args) -> {
            if (!(sender instanceof Player target)) {
                return true;
            }

            Player requester = resolveRequester(target, args);
            if (requester == null) {
                target.sendMessage("No matching trade request found.");
                return true;
            }

            tradeManager.denyRequest(target, requester);
            return true;
        });

        getCommand("tradecancel").setExecutor((sender, cmd, label, args) -> {
            if (!(sender instanceof Player p)) {
                return true;
            }

            TradeSessionWrapper wrapper = getTradeSessionWrapper(p);
            if (wrapper.activeTrade()) {
                tradeManager.cancelSession(wrapper.session());
                return true;
            }

            Player requester = resolveRequester(p, args);
            if (requester != null) {
                tradeManager.denyRequest(p, requester);
                return true;
            }

            p.sendMessage("You do not have an active trade or pending request.");
            return true;
        });
    }

    private Player resolveRequester(Player target, String[] args) {
        if (args.length >= 1) {
            return Bukkit.getPlayerExact(args[0]);
        }
        return tradeManager.getRequester(target);
    }

    private void sendClickableRequest(Player requester, Player target) {
        target.sendMessage(Component.text(requester.getName() + " wants to trade with you."));
        target.sendMessage(
                Component.text("[Accept]")
                        .clickEvent(ClickEvent.runCommand("/tradeaccept " + requester.getName()))
                        .hoverEvent(HoverEvent.showText(Component.text("Accept trade request")))
                        .append(Component.text(" "))
                        .append(
                                Component.text("[Deny]")
                                        .clickEvent(ClickEvent.runCommand("/tradedeny " + requester.getName()))
                                        .hoverEvent(HoverEvent.showText(Component.text("Deny trade request")))
                        )
        );
    }

    private TradeSessionWrapper getTradeSessionWrapper(Player player) {
        return new TradeSessionWrapper(tradeManager.getSession(player));
    }

    private record TradeSessionWrapper(com.safetrade.trade.TradeSession session) {
        private boolean activeTrade() {
            return session != null;
        }
    }
}
