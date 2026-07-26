package com.safetrade.trade;

import com.safetrade.SafeTradePlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public final class TradeCashItem {

    private static final String IDENTIFIER = "SafeTradeCash";
    private static SafeTradePlugin plugin;

    private TradeCashItem() {
    }

    public static void init(SafeTradePlugin pluginInstance) {
        plugin = pluginInstance;
    }

    public static ItemStack create(double amount, String ownerName) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color("&eTrade Cash"));
            meta.setLore(List.of(
                    color("&7Value: &a" + format(amount)),
                    color("&7Owner: &f" + ownerName),
                    color("&8" + IDENTIFIER)
            ));
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
                && meta.hasLore()
                && meta.getLore() != null
                && !meta.getLore().isEmpty()
                && meta.getLore().get(meta.getLore().size() - 1).contains(IDENTIFIER);
    }

    public static double getValue(ItemStack item) {
        if (!isCash(item)) {
            return 0D;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || meta.getLore() == null) {
            return 0D;
        }
        for (String line : meta.getLore()) {
            String stripped = ChatColor.stripColor(line);
            if (stripped != null && stripped.startsWith("Value: ")) {
                try {
                    return Double.parseDouble(stripped.substring("Value: ".length()).trim());
                } catch (NumberFormatException ignored) {
                    return 0D;
                }
            }
        }
        return 0D;
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
}
