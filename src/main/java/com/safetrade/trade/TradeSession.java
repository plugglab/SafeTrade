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
        if (item == null) {
            return;
        }

        getOffer(p).add(item.clone());
        resetAccept();
    }

    public boolean removeOfferItem(Player p, ItemStack item) {
        if (item == null) {
            return false;
        }

        boolean removed = getOffer(p).removeIf(i -> i.isSimilar(item) && i.getAmount() == item.getAmount());
        if (removed) {
            resetAccept();
        }
        return removed;
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

    private List<ItemStack> getOffer(Player p) {
        return p.equals(a) ? offerA : offerB;
    }
}
