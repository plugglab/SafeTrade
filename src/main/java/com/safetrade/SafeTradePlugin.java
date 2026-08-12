package com.safetrade;

import com.safetrade.listeners.AdminTradeListener;
import com.safetrade.listeners.SystemListener;
import com.safetrade.trade.AdminTradeGUI;
import com.safetrade.listeners.TradeListener;
import com.safetrade.trade.TradeCashItem;
import com.safetrade.trade.TradeCashStore;
import com.safetrade.trade.TradeGUI;
import com.safetrade.trade.TradeManager;
import com.safetrade.trade.TradeMoneyGUI;
import com.safetrade.trade.TradeSafetyGUI;
import com.safetrade.trade.TradeSession;
import com.safetrade.update.VersionChecker;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class SafeTradePlugin extends JavaPlugin {

    private TradeManager tradeManager;
    private VersionChecker versionChecker;
    private Object economy;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        TradeGUI.init(this);
        TradeSafetyGUI.init(this);
        TradeMoneyGUI.init(this);
        TradeCashItem.init(this, new TradeCashStore(this));
        AdminTradeGUI.init(this);
        this.tradeManager = new TradeManager(this);
        this.versionChecker = new VersionChecker(this);
        hookEconomy();

        Bukkit.getPluginManager().registerEvents(new TradeListener(this, tradeManager), this);
        Bukkit.getPluginManager().registerEvents(new AdminTradeListener(), this);
        Bukkit.getPluginManager().registerEvents(new SystemListener(this), this);
        versionChecker.checkForUpdates();

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

            sendTradeRequest(p, target);
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

        getCommand("tradehold").setExecutor((sender, cmd, label, args) -> {
            if (!(sender instanceof Player player)) {
                return true;
            }

            TradeSession session = tradeManager.getHoldSession(player);
            if (session == null || !session.isSafetyActive()) {
                player.sendMessage(prefixedComponent("messages.no-active-hold", "&cYou do not have anything on safety hold."));
                return true;
            }

            player.sendMessage(prefixedComponent("messages.trade-hold-header", "&eCurrent safety hold:"));
            player.sendMessage(formatHoldLine("messages.trade-hold-player", "&7{player}: {count} item stacks, money: {money}, release in {seconds}s",
                    session.getA().getName(), session.getOfferA().size(), session.getMoneyA(), session.getSafetySecondsRemaining()));
            player.sendMessage(formatHoldLine("messages.trade-hold-player", "&7{player}: {count} item stacks, money: {money}, release in {seconds}s",
                    session.getB().getName(), session.getOfferB().size(), session.getMoneyB(), session.getSafetySecondsRemaining()));
            player.sendMessage(colorize("&7Held items for " + session.getA().getName() + ":"));
            sendHoldItems(player, session.getA().getName(), session.getOfferA());
            player.sendMessage(colorize("&7Held items for " + session.getB().getName() + ":"));
            sendHoldItems(player, session.getB().getName(), session.getOfferB());
            return true;
        });

        getCommand("tradecash").setExecutor((sender, cmd, label, args) -> {
            if (!(sender instanceof Player player)) {
                return true;
            }
            if (args.length != 1) {
                player.sendMessage(prefixedComponent("messages.usage-tradecash", "&eUsage: /tradecash <amount>"));
                return true;
            }
            if (!isVaultEnabled()) {
                player.sendMessage(prefixedComponent("messages.vault-unavailable", "&cMoney trades are disabled because Vault is unavailable."));
                return true;
            }
            if (!getConfig().getBoolean("settings.enable-money-trades", true)) {
                player.sendMessage(prefixedComponent("messages.vault-unavailable", "&cMoney trades are disabled."));
                return true;
            }

            double amount;
            try {
                amount = Double.parseDouble(args[0]);
            } catch (NumberFormatException ex) {
                player.sendMessage(prefixedComponent("messages.money-invalid", "&cEnter a valid amount."));
                return true;
            }

            if (!Double.isFinite(amount) || amount <= 0D) {
                player.sendMessage(prefixedComponent("messages.money-invalid", "&cEnter a valid amount."));
                return true;
            }

            double max = Math.max(0D, getConfig().getDouble("settings.max-money-per-player", 0D));
            if (max > 0D && amount > max) {
                player.sendMessage(formatValuePrefixed("messages.money-too-high", "&cThat amount exceeds the limit: {value}", String.valueOf(max)));
                return true;
            }

            if (tradeManager.getBalance(player) < amount) {
                player.sendMessage(prefixedComponent("messages.money-insufficient", "&cYou do not have enough money."));
                return true;
            }

            if (!tradeManager.withdrawMoney(player, amount)) {
                player.sendMessage(prefixedComponent("messages.money-pay-failed", "&cMoney transfer failed."));
                return true;
            }

            ItemStack cash = TradeCashItem.create(amount, player.getName());
            var leftover = player.getInventory().addItem(cash);
            if (!leftover.isEmpty()) {
                tradeManager.depositMoney(player, amount);
                player.sendMessage(prefixedComponent("messages.inventory-full", "&cYour inventory is full."));
                return true;
            }

            player.sendMessage(formatValuePrefixed("messages.tradecash-created", "&aCreated cash paper worth &e{value}&a.", String.valueOf(amount)));
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

    public boolean sendTradeRequest(Player requester, Player target) {
        if (!tradeManager.sendTradeRequest(requester, target)) {
            return false;
        }
        Player pendingRequester = tradeManager.getRequester(target);
        if (pendingRequester != null && pendingRequester.getUniqueId().equals(requester.getUniqueId())) {
            sendClickableRequest(requester, target);
        }
        return true;
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

    private String formatHoldLine(String path, String fallback, String playerName, int count, double money, long seconds) {
        return getPrefix() + getText(path, fallback)
                .replace("{player}", playerName)
                .replace("{count}", String.valueOf(count))
                .replace("{money}", String.valueOf(money))
                .replace("{seconds}", String.valueOf(seconds));
    }

    private void sendHoldItems(Player player, String owner, List<ItemStack> items) {
        if (items == null || items.isEmpty()) {
            player.sendMessage(colorize("&7- " + owner + ": none"));
            return;
        }
        for (ItemStack item : items) {
            if (item == null) {
                continue;
            }
            player.sendMessage(colorize("&7- " + owner + ": " + item.getAmount() + "x " + item.getType().name()));
        }
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

    public VersionChecker getVersionChecker() {
        return versionChecker;
    }

    public boolean isVaultEnabled() {
        return economy != null;
    }

    public boolean isSelfTestUser(Player player) {
        return player != null && "Wilczek015".equalsIgnoreCase(player.getName());
    }

    public Object getEconomy() {
        return economy;
    }

    private void hookEconomy() {
        try {
            Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            Object provider = getServer().getServicesManager().getRegistration(economyClass);
            if (provider != null) {
                economy = provider.getClass().getMethod("getProvider").invoke(provider);
            }
        } catch (ReflectiveOperationException ignored) {
        }
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
