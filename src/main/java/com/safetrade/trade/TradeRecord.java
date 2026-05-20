package com.safetrade.trade;

import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

public record TradeRecord(
        String id,
        long createdAt,
        UUID playerAId,
        String playerAName,
        UUID playerBId,
        String playerBName,
        List<ItemStack> offerA,
        List<ItemStack> offerB,
        boolean rolledBack
) {

    public TradeRecord withRolledBack(boolean rolledBack) {
        return new TradeRecord(id, createdAt, playerAId, playerAName, playerBId, playerBName, offerA, offerB, rolledBack);
    }
}
