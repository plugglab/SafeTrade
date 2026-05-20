package com.safetrade.trade;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class TradeValidator {

    public static boolean hasItems(Player p, List<ItemStack> expected) {
        for (ItemStack e : expected) {
            int needed = e.getAmount();
            int found = 0;

            for (ItemStack inv : p.getInventory().getContents()) {
                if (inv != null && inv.isSimilar(e)) {
                    found += inv.getAmount();
                    if (found >= needed) break;
                }
            }

            if (found < needed) return false;
        }
        return true;
    }
}
