package com.safetrade;

import com.safetrade.listeners.AdminTradeListener;
import com.safetrade.trade.AdminTradeGUI;
import com.safetrade.listeners.TradeListener;
import com.safetrade.trade.TradeGUI;
import com.safetrade.trade.TradeManager;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class SafeTradePlugin extends JavaPlugin {

    private TradeManager tradeManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        TradeGUI.init(this);
        AdminTradeGUI.init(this);
        this.tradeManager = new TradeManager(this);

        Bukkit.getPluginManager().registerEvents(new TradeListener(tradeManager), this);
        Bukkit.getPluginManager().registerEvents(new AdminTradeListener(), this);

        getCommand("trade").setExecutor((sender, cmd, label, args) -> {
            if (!(sender instanceof Player p)) {
                return true;
            }
            if (args.length != 1) {
                p.sendMessage(prefixedComponent("messages.usage-trade", "&eUsage: /trade <player>"));
                return true;
            }

            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                p.sendMessage(prefixedComponent("messages.player-offline", "&cThat player is offline."));
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
                target.sendMessage(prefixedComponent("messages.no-matching-request", "&cNo matching trade request found."));
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
                target.sendMessage(prefixedComponent("messages.no-matching-request", "&cNo matching trade request found."));
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

            p.sendMessage(prefixedComponent("messages.no-active-trade-or-request", "&cYou do not have an active trade or pending request."));
            return true;
        });

        getCommand("tradeadmin").setExecutor((sender, cmd, label, args) -> {
            if (!(sender instanceof Player player)) {
                return true;
            }
            if (!player.hasPermission("safetrade.admin")) {
                player.sendMessage(prefixedComponent("messages.no-permission", "&cYou do not have permission to use this command."));
                return true;
            }

            if (args.length == 2 && args[0].equalsIgnoreCase("rollback")) {
                tradeManager.rollbackTrade(player, args[1]);
                return true;
            }

            AdminTradeGUI.open(player, 0);
            return true;
        });
    }

    @Override
    public void onDisable() {
        if (tradeManager != null) {
            tradeManager.cancelAllSessions();
        }
    }

    private Player resolveRequester(Player target, String[] args) {
        if (args.length >= 1) {
            return Bukkit.getPlayerExact(args[0]);
        }
        return tradeManager.getRequester(target);
    }

    private void sendClickableRequest(Player requester, Player target) {
        String requestText = format("messages.trade-request-received", "&a{player} wants to trade with you.", requester.getName());
        String acceptLabel = getText("messages.accept-button", "&a[Accept]");
        String denyLabel = getText("messages.deny-button", "&c[Deny]");
        String acceptHover = getText("messages.accept-hover", "&aAccept trade request");
        String denyHover = getText("messages.deny-hover", "&cDeny trade request");

        target.sendMessage(
                prefixedComponentRaw(requestText)
        );
        target.sendMessage(
                component(acceptLabel)
                        .decoration(TextDecoration.BOLD, true)
                        .clickEvent(ClickEvent.runCommand("/tradeaccept " + requester.getName()))
                        .hoverEvent(HoverEvent.showText(component(acceptHover)))
                        .append(Component.text("  ", NamedTextColor.GRAY))
                        .append(
                                component(denyLabel)
                                        .decoration(TextDecoration.BOLD, true)
                                        .clickEvent(ClickEvent.runCommand("/tradedeny " + requester.getName()))
                                        .hoverEvent(HoverEvent.showText(component(denyHover)))
                        )
        );
    }

    public String getPrefix() {
        return colorize(getConfig().getString("messages.prefix", "&8[&bST&8] "));
    }

    public String getText(String path, String fallback) {
        return colorize(getConfig().getString(path, fallback));
    }

    public String getPrefixedText(String path, String fallback) {
        return getPrefix() + getText(path, fallback);
    }

    public String format(String path, String fallback, String playerName) {
        return getText(path, fallback).replace("{player}", playerName);
    }

    public String formatPrefixed(String path, String fallback, String playerName) {
        return getPrefix() + format(path, fallback, playerName);
    }

    public String formatNumber(String path, String fallback, long number) {
        return getText(path, fallback).replace("{seconds}", String.valueOf(number)).replace("{amount}", String.valueOf(number));
    }

    public String formatNumberPrefixed(String path, String fallback, long number) {
        return getPrefix() + formatNumber(path, fallback, number);
    }

    public String formatValuePrefixed(String path, String fallback, String value) {
        return getPrefix() + getText(path, fallback).replace("{value}", value);
    }

    public Component component(String text) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(colorize(text));
    }

    public Component prefixedComponent(String path, String fallback) {
        return component(getPrefixedText(path, fallback));
    }

    public Component prefixedComponentRaw(String text) {
        return component(getPrefix() + text);
    }

    public String colorize(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    public TradeManager getTradeManager() {
        return tradeManager;
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
