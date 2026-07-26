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
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;

public final class TradeSafetyGUI {

    private static SafeTradePlugin plugin;

    private TradeSafetyGUI() {
    }

    public static void init(SafeTradePlugin pluginInstance) {
        plugin = pluginInstance;
    }

    public static void open(TradeSession session) {
        Inventory inventory = Bukkit.createInventory(null, 54, title());
        update(inventory, session);
        session.getA().openInventory(inventory);
        session.getB().openInventory(inventory);
    }

    public static void update(Inventory inventory, TradeSession session) {
        inventory.clear();
        ItemStack filler = named(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int slot : new int[]{0,1,2,3,4,5,6,7,8,9,17,18,26,27,35,36,44,45,46,47,48,49,50,51,52,53}) {
            inventory.setItem(slot, filler);
        }

        fillOffer(inventory, new int[]{10,11,12,19,20,21,28,29,30,37,38,39}, session.getOfferA());
        fillOffer(inventory, new int[]{14,15,16,23,24,25,32,33,34,41,42,43}, session.getOfferB());

        inventory.setItem(13, playerHead(session.getA(), subtitle("gui.safety-hold-offer", "Held for safety")));
        inventory.setItem(22, info(Material.CLOCK, subtitle("gui.safety-countdown", "Releasing in {seconds}s").replace("{seconds}", String.valueOf(session.getSafetySecondsRemaining()))));
        inventory.setItem(31, info(Material.EMERALD, text("gui.safety-shield-title", "Safe release")));
        inventory.setItem(40, playerHead(session.getB(), subtitle("gui.safety-hold-offer", "Held for safety")));
    }

    public static boolean isSafetyInventory(InventoryView view) {
        return title().equals(view.getTitle());
    }

    private static void fillOffer(Inventory inventory, int[] slots, List<ItemStack> items) {
        for (int i = 0; i < Math.min(slots.length, items.size()); i++) {
            inventory.setItem(slots[i], items.get(i));
        }
    }

    private static ItemStack playerHead(Player player, String subtitle) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(player);
            meta.setDisplayName(player.getName());
            meta.setLore(List.of(subtitle));
            head.setItemMeta(meta);
        }
        return head;
    }

    private static ItemStack info(Material material, String text) {
        return named(material, text);
    }

    private static ItemStack named(Material material, String title) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(title);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static String title() {
        return text("gui.safety-title", "SafeTrade | Safety Hold");
    }

    private static String subtitle(String path, String fallback) {
        return plugin == null ? fallback : plugin.getText(path, fallback);
    }

    private static String text(String path, String fallback) {
        return plugin == null ? fallback : plugin.getText(path, fallback);
    }
}
