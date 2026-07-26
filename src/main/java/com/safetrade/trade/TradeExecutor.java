package com.safetrade.trade;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

public class TradeExecutor {

    public static boolean execute(TradeSession s) {
        // Items are intentionally not delivered here. They stay in the safety hold
        // until the configured timer expires.
        return true;
    }
}
