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

    private static final String TITLE = "SafeTrade";
    private static final int[] OFFER_A_SLOTS = {10, 11, 19, 20};
    private static final int[] OFFER_B_SLOTS = {15, 16, 24, 25};
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
        inv.setItem(9, createPlayerItem(s.getA(), "Left side"));
        inv.setItem(17, createPlayerItem(s.getB(), "Right side"));
        inv.setItem(13, createInfoItem("Your items", "Each side can offer up to 4 stacks."));
        inv.setItem(22, createInfoItem("Acceptance", "Any item change resets both accepts."));
        inv.setItem(31, createInfoItem("Protection", "Only the owner can remove offered items."));
        inv.setItem(ACCEPT_SLOT_A, createAcceptItem(s.getA().getName(), s.isAcceptedA()));
        inv.setItem(ACCEPT_SLOT_B, createAcceptItem(s.getB().getName(), s.isAcceptedB()));
        inv.setItem(STATUS_SLOT, createSummaryItem(s));
    }

    private static void fillBackground(Inventory inv) {
        ItemStack filler = createNamedItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        int[] fillerSlots = {
                0, 1, 2, 3, 5, 6, 7, 8,
                12, 14, 18, 21, 23, 26,
                27, 28, 29, 30, 32, 33, 34, 35,
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
        String lore = accepted ? "Ready to trade." : "Click your button to accept.";
        return createNamedItem(material, title, lore);
    }

    private static ItemStack createStatusItem(TradeSession s) {
        String text = s.bothAccepted() ? "Trade confirmed" : "Waiting for both players";
        return createNamedItem(Material.BELL, text, "Any item change resets both accepts.");
    }

    private static ItemStack createSummaryItem(TradeSession s) {
        return createNamedItem(
                Material.EMERALD,
                "Finalize Trade",
                s.getA().getName() + ": " + s.getOfferA().size() + "/4 stacks",
                s.getB().getName() + ": " + s.getOfferB().size() + "/4 stacks"
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

    private static boolean isOfferSlot(int slot, int[] slots) {
        for (int offerSlot : slots) {
            if (offerSlot == slot) {
                return true;
            }
        }
        return false;
    }
}
