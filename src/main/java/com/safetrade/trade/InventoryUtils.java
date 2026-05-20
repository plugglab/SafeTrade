package com.safetrade.trade;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class InventoryUtils {

    private InventoryUtils() {
    }

    public static boolean containsAll(Inventory inventory, List<ItemStack> items) {
        return containsAll(List.of(inventory), items);
    }

    public static boolean containsAll(List<Inventory> inventories, List<ItemStack> items) {
        for (ItemStack required : compact(items)) {
            if (countSimilar(inventories, required) < required.getAmount()) {
                return false;
            }
        }
        return true;
    }

    public static void removeAll(Inventory inventory, List<ItemStack> items) {
        removeAll(List.of(inventory), items);
    }

    public static void removeAll(List<Inventory> inventories, List<ItemStack> items) {
        for (ItemStack required : compact(items)) {
            int remaining = required.getAmount();
            for (Inventory inventory : inventories) {
                ItemStack[] contents = inventory.getContents();
                for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
                    ItemStack current = contents[slot];
                    if (current == null || !current.isSimilar(required)) {
                        continue;
                    }

                    int removed = Math.min(remaining, current.getAmount());
                    current.setAmount(current.getAmount() - removed);
                    remaining -= removed;

                    if (current.getAmount() <= 0) {
                        inventory.setItem(slot, null);
                    } else {
                        inventory.setItem(slot, current);
                    }
                }

                if (remaining <= 0) {
                    break;
                }
            }
        }
    }

    private static List<ItemStack> compact(List<ItemStack> items) {
        List<ItemStack> compacted = new ArrayList<>();
        for (ItemStack item : items) {
            if (item == null || item.getAmount() <= 0) {
                continue;
            }

            boolean merged = false;
            for (ItemStack existing : compacted) {
                if (!existing.isSimilar(item)) {
                    continue;
                }
                existing.setAmount(existing.getAmount() + item.getAmount());
                merged = true;
                break;
            }

            if (!merged) {
                compacted.add(item.clone());
            }
        }
        return compacted;
    }

    private static int countSimilar(List<Inventory> inventories, ItemStack required) {
        int amount = 0;
        for (Inventory inventory : inventories) {
            for (ItemStack current : inventory.getContents()) {
                if (current == null || !current.isSimilar(required)) {
                    continue;
                }

                amount += current.getAmount();
                if (amount >= required.getAmount()) {
                    return amount;
                }
            }
        }
        return amount;
    }
}
