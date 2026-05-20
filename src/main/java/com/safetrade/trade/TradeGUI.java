package com.safetrade.trade;

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

    private static final String TITLE = "SafeTrade | Secure Exchange";
    private static final int[] OFFER_A_SLOTS = {9, 10, 11, 12, 18, 19, 20, 21, 27, 28, 29, 30};
    private static final int[] OFFER_B_SLOTS = {14, 15, 16, 17, 23, 24, 25, 26, 32, 33, 34, 35};
    public static final int ACCEPT_SLOT_A = 45;
    public static final int ACCEPT_SLOT_B = 53;
    private static final int STATUS_SLOT = 49;

    public static void open(TradeSession s) {
        Inventory inv = Bukkit.createInventory(null, 54, TITLE);
        update(inv, s);
        s.getA().openInventory(inv);
        s.getB().openInventory(inv);
    }

    public static void update(Inventory inv, TradeSession s) {
        inv.clear();
        fillBackground(inv);
        fillOffer(inv, OFFER_A_SLOTS, s.getOfferA());
        fillOffer(inv, OFFER_B_SLOTS, s.getOfferB());
        inv.setItem(4, createStatusItem(s));
        inv.setItem(0, createPlayerItem(s.getA(), "Your trade slots"));
        inv.setItem(8, createPlayerItem(s.getB(), "Their trade slots"));
        inv.setItem(5, createInfoItem("Add Items", "Click an item in your inventory to add 1 item."));
        inv.setItem(13, createInfoItem("Capacity", "Each player can offer up to 12 single items."));
        inv.setItem(22, createInfoItem("Accept Rules", "Any change resets both accept buttons."));
        inv.setItem(31, createInfoItem("Remove Items", "Click your own trade slots to take 1 item back."));
        inv.setItem(ACCEPT_SLOT_A, createAcceptItem(s.getA().getName(), s.isAcceptedA()));
        inv.setItem(ACCEPT_SLOT_B, createAcceptItem(s.getB().getName(), s.isAcceptedB()));
        inv.setItem(STATUS_SLOT, createSummaryItem(s));
    }

    private static void fillBackground(Inventory inv) {
        ItemStack filler = createNamedItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        int[] fillerSlots = {
                1, 2, 3, 6, 7,
                36, 37, 38, 39, 40, 41, 42, 43, 44,
                46, 47, 48, 50, 51, 52
        };
        for (int slot : fillerSlots) {
            inv.setItem(slot, filler);
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
            meta.setDisplayName(player.getName());
            meta.setLore(List.of(subtitle));
            head.setItemMeta(meta);
        }
        return head;
    }

    private static ItemStack createAcceptItem(String playerName, boolean accepted) {
        Material material = accepted ? Material.LIME_CONCRETE : Material.RED_CONCRETE;
        String title = accepted ? playerName + " accepted" : playerName + " pending";
        String lore = accepted ? "Waiting for the other player." : "Click to confirm your side.";
        return createNamedItem(material, title, lore);
    }

    private static ItemStack createStatusItem(TradeSession s) {
        String text = s.bothAccepted() ? "Trade locked in" : "Review the offers";
        return createNamedItem(Material.BELL, text, "Both players must accept the current offer.");
    }

    private static ItemStack createSummaryItem(TradeSession s) {
        return createNamedItem(
                Material.EMERALD,
                "Trade Summary",
                s.getA().getName() + ": " + s.getOfferA().size() + "/12 items",
                s.getB().getName() + ": " + s.getOfferB().size() + "/12 items"
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
        return TITLE.equals(view.getTitle());
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
        return OFFER_A_SLOTS.length;
    }

    private static boolean isOfferSlot(int slot, int[] slots) {
        for (int offerSlot : slots) {
            if (offerSlot == slot) {
                return true;
            }
        }
        return false;
    }
}
