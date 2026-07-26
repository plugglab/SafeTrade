package com.safetrade.trade;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.safetrade.SafeTradePlugin;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TradeHoldStore {

    private final SafeTradePlugin plugin;
    private final File file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<String, TradeHoldRecord> holds = new LinkedHashMap<>();

    public TradeHoldStore(SafeTradePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "holds.json");
        load();
    }

    public synchronized void load() {
        holds.clear();
        if (!file.exists()) {
            save();
            return;
        }

        try (FileReader reader = new FileReader(file)) {
            HoldFile data = gson.fromJson(reader, HoldFile.class);
            if (data != null && data.holds != null) {
                holds.putAll(data.holds);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to load safety holds: " + e.getMessage());
        }
    }

    public synchronized void saveHold(TradeSession session) {
        if (session == null || session.getHistoryId().isBlank()) {
            return;
        }

        holds.put(session.getHistoryId(), new TradeHoldRecord(
                session.getHistoryId(),
                System.currentTimeMillis(),
                session.getA().getUniqueId(),
                session.getA().getName(),
                session.getB().getUniqueId(),
                session.getB().getName(),
                serializeItems(session.getOfferA()),
                serializeItems(session.getOfferB()),
                session.getMoneyA(),
                session.getMoneyB()
        ));
        save();
    }

    public synchronized TradeHoldRecord getHold(String tradeId) {
        return holds.get(tradeId);
    }

    public synchronized List<TradeHoldRecord> getHoldsForPlayer(UUID playerId) {
        List<TradeHoldRecord> matches = new ArrayList<>();
        for (TradeHoldRecord hold : holds.values()) {
            if (hold.playerAId().equals(playerId) || hold.playerBId().equals(playerId)) {
                matches.add(hold);
            }
        }
        return matches;
    }

    public synchronized void markReleased(String tradeId, UUID playerId) {
        TradeHoldRecord hold = holds.get(tradeId);
        if (hold == null) {
            return;
        }

        boolean releasedA = hold.playerAReleased();
        boolean releasedB = hold.playerBReleased();
        if (hold.playerAId().equals(playerId)) {
            releasedA = true;
        } else if (hold.playerBId().equals(playerId)) {
            releasedB = true;
        } else {
            return;
        }

        holds.put(tradeId, hold.withReleased(releasedA, releasedB));
        save();
    }

    public synchronized TradeHoldRecord removeHold(String tradeId) {
        TradeHoldRecord removed = holds.remove(tradeId);
        if (removed != null) {
            save();
        }
        return removed;
    }

    private void save() {
        HoldFile data = new HoldFile();
        data.holds = holds;
        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(data, writer);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save safety holds: " + e.getMessage());
        }
    }

    public List<ItemStack> deserializeItems(List<Map<String, Object>> source) {
        List<ItemStack> items = new ArrayList<>();
        for (Map<String, Object> itemData : source) {
            if (itemData == null) {
                continue;
            }
            try {
                items.add(ItemStack.deserialize(itemData));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return items;
    }

    private List<Map<String, Object>> serializeItems(List<ItemStack> source) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (ItemStack item : source) {
            if (item != null) {
                items.add(item.serialize());
            }
        }
        return items;
    }

    private static class HoldFile {
        Map<String, TradeHoldRecord> holds = new LinkedHashMap<>();
    }

    public record TradeHoldRecord(
            String tradeId,
            long createdAt,
            UUID playerAId,
            String playerAName,
            UUID playerBId,
            String playerBName,
            List<Map<String, Object>> offerA,
            List<Map<String, Object>> offerB,
            double moneyA,
            double moneyB,
            boolean playerAReleased,
            boolean playerBReleased
    ) {
        public TradeHoldRecord(
                String tradeId,
                long createdAt,
                UUID playerAId,
                String playerAName,
                UUID playerBId,
                String playerBName,
                List<Map<String, Object>> offerA,
                List<Map<String, Object>> offerB,
                double moneyA,
                double moneyB
        ) {
            this(tradeId, createdAt, playerAId, playerAName, playerBId, playerBName, offerA, offerB, moneyA, moneyB, false, false);
        }

        public TradeHoldRecord withReleased(boolean releasedA, boolean releasedB) {
            return new TradeHoldRecord(tradeId, createdAt, playerAId, playerAName, playerBId, playerBName, offerA, offerB, moneyA, moneyB, releasedA, releasedB);
        }
    }
}
