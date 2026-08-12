package com.safetrade.trade;

import com.safetrade.SafeTradePlugin;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public final class TradeCashItem {

    private static final String IDENTIFIER = "SafeTradeCash";
    private static SafeTradePlugin plugin;
    private static NamespacedKey cashKey;
    private static NamespacedKey idKey;
    private static TradeCashStore cashStore;

    private TradeCashItem() {
    }

    public static void init(SafeTradePlugin pluginInstance, TradeCashStore store) {
        plugin = pluginInstance;
        cashKey = new NamespacedKey(plugin, "cash-paper");
        idKey = new NamespacedKey(plugin, "cash-paper-id");
        cashStore = store;
    }

    public static ItemStack create(double amount, String ownerName) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(getText("messages.cash-item-name", "&eTrade Cash"));
            meta.setLore(List.of(
                    getText("messages.cash-item-value-lore", "&7Value: &a{value}").replace("{value}", format(amount)),
                    getText("messages.cash-item-owner-lore", "&7Owner: &f{owner}").replace("{owner}", ownerName),
                    color("&8" + IDENTIFIER)
            ));
            meta.getPersistentDataContainer().set(cashKey, PersistentDataType.DOUBLE, amount);
            meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, cashStore.issue(amount));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static boolean isCash(ItemStack item) {
        if (item == null || item.getType() != Material.PAPER || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null
                && cashKey != null && idKey != null
                && meta.getPersistentDataContainer().has(cashKey, PersistentDataType.DOUBLE)
                && meta.getPersistentDataContainer().has(idKey, PersistentDataType.STRING);
    }

    public static double getValue(ItemStack item) {
        if (!isCash(item)) {
            return 0D;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || cashKey == null) {
            return 0D;
        }
        String id = getId(item);
        return id == null || cashStore == null ? 0D : cashStore.getRedeemableValue(id);
    }

    public static boolean markRedeemed(ItemStack item) {
        String id = getId(item);
        return id != null && cashStore != null && cashStore.markRedeemed(id);
    }

    private static String getId(ItemStack item) {
        if (!isCash(item)) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
    }

    private static String format(double amount) {
        if (Math.rint(amount) == amount) {
            return String.valueOf((long) amount);
        }
        return String.valueOf(amount);
    }

    private static String color(String text) {
        return plugin == null ? text : plugin.colorize(text);
    }

    private static String getText(String path, String fallback) {
        return plugin == null ? color(fallback) : plugin.getText(path, fallback);
    }
}
