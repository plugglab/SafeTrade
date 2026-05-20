package com.safetrade;

import com.safetrade.listeners.TradeListener;
import com.safetrade.trade.TradeManager;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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
                p.sendMessage(Component.text("Usage: /trade <player>", NamedTextColor.YELLOW));
                return true;
            }

            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                p.sendMessage(Component.text("That player is offline.", NamedTextColor.RED));
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
                target.sendMessage(Component.text("No matching trade request found.", NamedTextColor.RED));
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
                target.sendMessage(Component.text("No matching trade request found.", NamedTextColor.RED));
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

            p.sendMessage(Component.text("You do not have an active trade or pending request.", NamedTextColor.RED));
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
        target.sendMessage(
                Component.text()
                        .append(Component.text(requester.getName(), NamedTextColor.AQUA))
                        .append(Component.text(" wants to trade with you.", NamedTextColor.GREEN))
                        .build()
        );
        target.sendMessage(
                Component.text("[Accept]", NamedTextColor.GREEN)
                        .decoration(TextDecoration.BOLD, true)
                        .clickEvent(ClickEvent.runCommand("/tradeaccept " + requester.getName()))
                        .hoverEvent(HoverEvent.showText(Component.text("Accept trade request", NamedTextColor.GREEN)))
                        .append(Component.text("  ", NamedTextColor.GRAY))
                        .append(
                                Component.text("[Deny]", NamedTextColor.RED)
                                        .decoration(TextDecoration.BOLD, true)
                                        .clickEvent(ClickEvent.runCommand("/tradedeny " + requester.getName()))
                                        .hoverEvent(HoverEvent.showText(Component.text("Deny trade request", NamedTextColor.RED)))
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
