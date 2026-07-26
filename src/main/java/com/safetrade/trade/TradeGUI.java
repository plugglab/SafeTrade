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

public class TradeGUI {

    private static final int[] OFFER_A_SLOTS = {9, 10, 11, 12, 18, 19, 20, 21, 27, 28, 29, 30, 36, 37, 38, 39};
    private static final int[] OFFER_B_SLOTS = {14, 15, 16, 17, 23, 24, 25, 26, 32, 33, 34, 35, 41, 42, 43, 44};
    public static final int ACCEPT_SLOT_A = 45;
    public static final int ACCEPT_SLOT_B = 53;
    private static final int STATUS_SLOT = 49;
    private static final int[] CENTER_GLASS_SLOTS = {4, 13, 22, 31, 40, 49};
    private static SafeTradePlugin plugin;

    public static void init(SafeTradePlugin pluginInstance) {
        plugin = pluginInstance;
    }

    public static void open(TradeSession s) {
        Inventory inv = Bukkit.createInventory(null, 54, getTitle());
        update(inv, s);
        s.getA().openInventory(inv);
        s.getB().openInventory(inv);
    }

    public static void update(Inventory inv, TradeSession s) {
        inv.clear();
        fillBackground(inv);
        fillOffer(inv, OFFER_A_SLOTS, s.getOfferA());
        fillOffer(inv, OFFER_B_SLOTS, s.getOfferB());
        fillCenterGlass(inv);
        inv.setItem(0, createPlayerItem(s.getA(), text("gui.player-offer-lore", "Items offered by this player")));
        inv.setItem(8, createPlayerItem(s.getB(), text("gui.player-offer-lore", "Items offered by this player")));
        inv.setItem(2, createInfoItem(text("gui.add-title", "Add Items"), text("gui.add-lore", "Left click adds 1. Right click adds the whole stack.")));
        inv.setItem(3, createInfoItem(text("gui.capacity-title", "Capacity"), text("gui.capacity-lore", "Each player can offer up to {max} stacked item slots.")));
        inv.setItem(4, createInfoItem(text("gui.money-title", "Money Bet"), text("gui.money-lore", "Click to set a custom money amount for this trade.")));
        inv.setItem(5, createInfoItem(text("gui.accept-rules-title", "Accept Rules"), text("gui.accept-rules-lore", "Any change resets both accept buttons.")));
        inv.setItem(6, createInfoItem(text("gui.remove-title", "Remove Items"), text("gui.remove-lore", "Left click removes 1. Right click removes the whole stack.")));
        inv.setItem(ACCEPT_SLOT_A, createAcceptItem(s.getA().getName(), s.isAcceptedA()));
        inv.setItem(ACCEPT_SLOT_B, createAcceptItem(s.getB().getName(), s.isAcceptedB()));
        inv.setItem(STATUS_SLOT, createSummaryItem(s));
    }

    private static void fillBackground(Inventory inv) {
        ItemStack filler = createNamedItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        int[] fillerSlots = {
                1, 7, 46, 47, 48, 50, 51, 52
        };
        for (int slot : fillerSlots) {
            inv.setItem(slot, filler);
        }
    }

    private static void fillCenterGlass(Inventory inv) {
        ItemStack divider = createNamedItem(Material.LIGHT_BLUE_STAINED_GLASS_PANE, " ");
        for (int slot : CENTER_GLASS_SLOTS) {
            if (slot == STATUS_SLOT) {
                continue;
            }
            inv.setItem(slot, divider);
        }
    }

    private static void fillOffer(Inventory inv, int[] slots, List<ItemStack> items) {
        for (int i = 0; i < Math.min(slots.length, items.size()); i++) {
            inv.setItem(slots[i], items.get(i));
        }
    }

    private static ItemStack createPlayerItem(Player player, String subtitle) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(player);
            meta.setDisplayName(text("gui.player-title", "{player}'s Offer").replace("{player}", player.getName()));
            meta.setLore(List.of(subtitle));
            head.setItemMeta(meta);
        }
        return head;
    }

    private static ItemStack createAcceptItem(String playerName, boolean accepted) {
        Material material = accepted ? Material.LIME_CONCRETE : Material.RED_CONCRETE;
        String title = accepted
                ? text("gui.accepted-title", "{player} accepted").replace("{player}", playerName)
                : text("gui.pending-title", "{player} pending").replace("{player}", playerName);
        String lore = accepted
                ? text("gui.accepted-lore", "Waiting for the other player.")
                : text("gui.pending-lore", "Click to confirm your side.");
        return createNamedItem(material, title, lore);
    }

    private static ItemStack createStatusItem(TradeSession s) {
        String statusText = s.bothAccepted()
                ? text("gui.status-locked-title", "Trade locked in")
                : text("gui.status-review-title", "Review the offers");
        return createNamedItem(Material.BELL, statusText, text("gui.status-lore", "Both players must accept the current offer."));
    }

    private static ItemStack createSummaryItem(TradeSession s) {
        return createNamedItem(
                Material.EMERALD,
                text("gui.summary-title", "Trade Summary"),
                text("gui.summary-line", "{player}: {amount}/{max} stacks")
                        .replace("{player}", s.getA().getName())
                        .replace("{amount}", String.valueOf(s.getOfferA().size()))
                        .replace("{max}", String.valueOf(getOfferSlotLimit())),
                text("gui.summary-line", "{player}: {amount}/{max} stacks")
                        .replace("{player}", s.getB().getName())
                        .replace("{amount}", String.valueOf(s.getOfferB().size()))
                        .replace("{max}", String.valueOf(getOfferSlotLimit())),
                text("gui.summary-line", "{player}: {amount} money")
                        .replace("{player}", s.getA().getName())
                        .replace("{amount}", formatMoney(s.getMoneyA())),
                text("gui.summary-line", "{player}: {amount} money")
                        .replace("{player}", s.getB().getName())
                        .replace("{amount}", formatMoney(s.getMoneyB()))
        );
    }

    private static ItemStack createInfoItem(String title, String lore) {
        return createNamedItem(Material.PAPER, title, lore);
    }

    private static ItemStack createNamedItem(Material material, String title, String... loreLines) {
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

    public static boolean isTradeInventory(InventoryView view) {
        return getTitle().equals(view.getTitle());
    }

    public static boolean isOwnOfferSlot(Player player, TradeSession session, int slot) {
        if (player.equals(session.getA())) {
            return isOfferSlot(slot, OFFER_A_SLOTS);
        }
        if (player.equals(session.getB())) {
            return isOfferSlot(slot, OFFER_B_SLOTS);
        }
        return false;
    }

    public static boolean isAcceptSlot(Player player, TradeSession session, int slot) {
        if (player.equals(session.getA())) {
            return slot == ACCEPT_SLOT_A;
        }
        if (player.equals(session.getB())) {
            return slot == ACCEPT_SLOT_B;
        }
        return false;
    }

    public static int getOfferSlotLimit() {
        if (plugin == null) {
            return OFFER_A_SLOTS.length;
        }
        int configured = plugin.getConfig().getInt("settings.max-offer-slots", OFFER_A_SLOTS.length);
        return Math.max(1, Math.min(configured, OFFER_A_SLOTS.length));
    }

    private static boolean isOfferSlot(int slot, int[] slots) {
        for (int offerSlot : slots) {
            if (offerSlot == slot) {
                return true;
            }
        }
        return false;
    }

    private static String getTitle() {
        return text("gui.title", "SafeTrade | Secure Exchange");
    }

    private static String text(String path, String fallback) {
        String value = plugin == null ? fallback : plugin.getText(path, fallback);
        return value.replace("{max}", String.valueOf(getOfferSlotLimit()));
    }

    private static String formatMoney(double amount) {
        if (Math.rint(amount) == amount) {
            return String.valueOf((long) amount);
        }
        return String.valueOf(amount);
    }
}
