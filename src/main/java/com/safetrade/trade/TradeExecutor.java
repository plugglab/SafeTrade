package com.safetrade.trade;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

public class TradeExecutor {

    public static boolean execute(TradeSession s) {
        give(s.getA(), s.getOfferB());
        give(s.getB(), s.getOfferA());
        return true;
    }

    private static void give(Player p, List<ItemStack> items) {
        for (ItemStack item : items) {
            Map<Integer, ItemStack> leftovers = p.getInventory().addItem(item.clone());
            for (ItemStack leftover : leftovers.values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), leftover);
            }
        }
    }
}
