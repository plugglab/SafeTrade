package com.safetrade.trade;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class TradeSession {

    private final Player a;
    private final Player b;

    private final List<ItemStack> offerA = new ArrayList<>();
    private final List<ItemStack> offerB = new ArrayList<>();

    private boolean acceptA = false;
    private boolean acceptB = false;
    private boolean completing = false;
    private int completionTaskId = -1;

    public TradeSession(Player a, Player b) {
        this.a = a;
        this.b = b;
    }

    public boolean isAcceptedA() {
        return acceptA;
    }

    public boolean isAcceptedB() {
        return acceptB;
    }

    public void setOffer(Player p, List<ItemStack> items) {
        List<ItemStack> copy = items.stream()
                .filter(Objects::nonNull)
                .map(ItemStack::clone)
                .toList();

        List<ItemStack> target = getOffer(p);
        target.clear();
        target.addAll(copy);
        resetAccept();
    }

    public void addOfferItem(Player p, ItemStack item) {
        if (item == null || item.getAmount() <= 0) {
            return;
        }

        List<ItemStack> offer = getOffer(p);
        ItemStack remaining = item.clone();

        for (ItemStack existing : offer) {
            if (!existing.isSimilar(remaining)) {
                continue;
            }

            int maxStack = existing.getMaxStackSize();
            int space = maxStack - existing.getAmount();
            if (space <= 0) {
                continue;
            }

            int moved = Math.min(space, remaining.getAmount());
            existing.setAmount(existing.getAmount() + moved);
            remaining.setAmount(remaining.getAmount() - moved);
            if (remaining.getAmount() <= 0) {
                resetAccept();
                return;
            }
        }

        while (remaining.getAmount() > 0) {
            ItemStack stack = remaining.clone();
            int moved = Math.min(stack.getMaxStackSize(), remaining.getAmount());
            stack.setAmount(moved);
            offer.add(stack);
            remaining.setAmount(remaining.getAmount() - moved);
        }

        resetAccept();
    }

    public ItemStack removeOfferItem(Player p, ItemStack item, int amount) {
        if (item == null || amount <= 0) {
            return null;
        }

        List<ItemStack> offer = getOffer(p);
        for (int i = 0; i < offer.size(); i++) {
            ItemStack existing = offer.get(i);
            if (!existing.isSimilar(item)) {
                continue;
            }

            int removedAmount = Math.min(amount, existing.getAmount());
            ItemStack removed = existing.clone();
            removed.setAmount(removedAmount);

            if (removedAmount >= existing.getAmount()) {
                offer.remove(i);
            } else {
                existing.setAmount(existing.getAmount() - removedAmount);
            }

            resetAccept();
            return removed;
        }

        return null;
    }

    public int getOfferStackCount(Player p) {
        return getOffer(p).size();
    }

    public int getOfferSpaceFor(Player p, ItemStack item) {
        if (item == null || item.getAmount() <= 0) {
            return 0;
        }

        List<ItemStack> offer = getOffer(p);
        int freeSlots = Math.max(0, TradeGUI.getOfferSlotLimit() - offer.size());
        int space = freeSlots * item.getMaxStackSize();

        for (ItemStack existing : offer) {
            if (existing.isSimilar(item)) {
                space += Math.max(0, existing.getMaxStackSize() - existing.getAmount());
            }
        }

        return space;
    }

    public void accept(Player p) {
        if (p.equals(a)) {
            acceptA = true;
        } else if (p.equals(b)) {
            acceptB = true;
        }
    }

    public void resetAccept() {
        acceptA = false;
        acceptB = false;
    }

    public boolean bothAccepted() {
        return acceptA && acceptB;
    }

    public Player getA() {
        return a;
    }

    public Player getB() {
        return b;
    }

    public List<ItemStack> getOfferA() {
        return offerA;
    }

    public List<ItemStack> getOfferB() {
        return offerB;
    }

    public boolean isCompleting() {
        return completing;
    }

    public void setCompleting(boolean completing) {
        this.completing = completing;
    }

    public int getCompletionTaskId() {
        return completionTaskId;
    }

    public void setCompletionTaskId(int completionTaskId) {
        this.completionTaskId = completionTaskId;
    }

    private List<ItemStack> getOffer(Player p) {
        return p.equals(a) ? offerA : offerB;
    }
}
