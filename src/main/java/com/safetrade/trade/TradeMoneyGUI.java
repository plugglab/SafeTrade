package com.safetrade.trade;

import com.safetrade.SafeTradePlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public final class TradeMoneyGUI {

    private static final int[] DIGIT_SLOTS = {10, 11, 12, 19, 20, 21, 28, 29, 30};
    private static final int COMMA_SLOT = 37;
    private static final int ZERO_SLOT = 38;
    private static final int CONFIRM_SLOT = 39;
    private static final int BACKSPACE_SLOT = 15;
    private static final int CLEAR_SLOT = 24;
    private static final int CANCEL_SLOT = 42;
    private static SafeTradePlugin plugin;

    private TradeMoneyGUI() {
    }

    public static void init(SafeTradePlugin pluginInstance) {
        plugin = pluginInstance;
    }

    public static void open(Player player) {
        TradeSession session = session(player);
        if (session == null) {
            return;
        }
        Inventory inventory = Bukkit.createInventory(null, 45, title());
        update(inventory, player, session);
        player.openInventory(inventory);
    }

    public static void update(Inventory inventory, Player player, TradeSession session) {
        inventory.clear();
        ItemStack filler = named(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int slot : new int[]{0,1,2,3,4,5,6,7,8,9,13,14,16,17,18,22,23,25,26,27,31,32,33,34,35,36,40,41,43,44}) {
            inventory.setItem(slot, filler);
        }

        inventory.setItem(4, named(Material.EMERALD, "Current bet", betLore(player, session)));
        inventory.setItem(13, named(Material.PAPER, "Input", inputLore(session.getMoneyInput(player))));
        inventory.setItem(BACKSPACE_SLOT, named(Material.ARROW, "Backspace", "Remove the last digit."));
        inventory.setItem(CLEAR_SLOT, named(Material.BARRIER, "Clear", "Reset the current amount."));
        inventory.setItem(CONFIRM_SLOT, named(Material.LIME_CONCRETE, "Confirm", "Apply this amount to the trade."));
        inventory.setItem(CANCEL_SLOT, named(Material.RED_CONCRETE, "Cancel", "Return to the trade screen."));

        String[] labels = {"1","2","3","4","5","6","7","8","9"};
        for (int i = 0; i < DIGIT_SLOTS.length; i++) {
            inventory.setItem(DIGIT_SLOTS[i], named(Material.BLACK_STAINED_GLASS_PANE, labels[i], "Add " + labels[i]));
        }
        inventory.setItem(COMMA_SLOT, named(Material.SLIME_BALL, ".", "Add a decimal point."));
        inventory.setItem(ZERO_SLOT, named(Material.BLACK_STAINED_GLASS_PANE, "0", "Add 0"));
    }

    public static boolean isMoneyInventory(InventoryView view) {
        return title().equals(view.getTitle());
    }

    public static void applyInput(Player player, String input) {
        TradeSession session = session(player);
        if (session == null) {
            return;
        }
        session.setMoneyInput(player, input);
        if (player.getOpenInventory() != null && isMoneyInventory(player.getOpenInventory())) {
            update(player.getOpenInventory().getTopInventory(), player, session);
        }
    }

    public static void clearInput(Player player) {
        applyInput(player, "");
    }

    private static TradeSession session(Player player) {
        return plugin == null ? null : plugin.getTradeManager().getSession(player);
    }

    private static String title() {
        return plugin == null ? "SafeTrade | Money Bet" : plugin.getText("gui.money-title", "SafeTrade | Money Bet");
    }

    private static ItemStack named(Material material, String title, String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(title);
            if (loreLines.length > 0) {
                meta.setLore(List.of(loreLines));
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static String[] betLore(Player player, TradeSession session) {
        double amount = player.equals(session.getA()) ? session.getMoneyA() : session.getMoneyB();
        return new String[]{
                "Applied to this trade: " + format(amount),
                "Balance: " + formatBalance(player)
        };
    }

    private static String[] inputLore(String input) {
        return new String[]{
                "Typed amount: " + (input == null || input.isBlank() ? "0" : input),
                "Use digits, decimal point, and confirm."
        };
    }

    private static String format(double amount) {
        if (Math.rint(amount) == amount) {
            return String.valueOf((long) amount);
        }
        return String.valueOf(amount);
    }

    private static String formatBalance(Player player) {
        if (plugin == null || plugin.getTradeManager() == null || !plugin.isVaultEnabled()) {
            return "Vault unavailable";
        }
        double balance = plugin.getTradeManager().getBalance(player);
        if (balance <= 0D) {
            if (plugin.getEconomy() == null) {
                return "Vault unavailable";
            }
        }
        try {
            return format(balance);
        } catch (Exception ignored) {
            return "Vault unavailable";
        }
    }
}
