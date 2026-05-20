package com.safetrade.trade;

import com.safetrade.SafeTradePlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TradeHistoryStore {

    private final SafeTradePlugin plugin;
    private final File file;
    private final Map<String, TradeRecord> records = new LinkedHashMap<>();

    public TradeHistoryStore(SafeTradePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "trade-history.yml");
        load();
    }

    public synchronized void load() {
        records.clear();
        if (!file.exists()) {
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("trades");
        if (section == null) {
            return;
        }

        for (String id : section.getKeys(false)) {
            ConfigurationSection tradeSection = section.getConfigurationSection(id);
            if (tradeSection == null) {
                continue;
            }

            try {
                TradeRecord record = new TradeRecord(
                        id,
                        tradeSection.getLong("created-at"),
                        UUID.fromString(tradeSection.getString("player-a.uuid", "")),
                        tradeSection.getString("player-a.name", "Unknown"),
                        UUID.fromString(tradeSection.getString("player-b.uuid", "")),
                        tradeSection.getString("player-b.name", "Unknown"),
                        cloneItems(tradeSection.getList("offer-a", List.of())),
                        cloneItems(tradeSection.getList("offer-b", List.of())),
                        tradeSection.getBoolean("rolled-back", false)
                );
                records.put(id, record);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public synchronized TradeRecord addRecord(TradeSession session) {
        TradeRecord record = new TradeRecord(
                UUID.randomUUID().toString().substring(0, 8),
                System.currentTimeMillis(),
                session.getA().getUniqueId(),
                session.getA().getName(),
                session.getB().getUniqueId(),
                session.getB().getName(),
                cloneItems(session.getOfferA()),
                cloneItems(session.getOfferB()),
                false
        );
        records.put(record.id(), record);
        trimToConfiguredLimit();
        save();
        return record;
    }

    public synchronized TradeRecord getRecord(String id) {
        return records.get(id);
    }

    public synchronized List<TradeRecord> getRecentRecords(int page, int pageSize) {
        List<TradeRecord> sorted = records.values().stream()
                .sorted(Comparator.comparingLong(TradeRecord::createdAt).reversed())
                .toList();

        int start = Math.max(0, page * pageSize);
        if (start >= sorted.size()) {
            return List.of();
        }

        int end = Math.min(sorted.size(), start + pageSize);
        return new ArrayList<>(sorted.subList(start, end));
    }

    public synchronized int getPageCount(int pageSize) {
        int size = records.size();
        return Math.max(1, (int) Math.ceil(size / (double) pageSize));
    }

    public synchronized void markRolledBack(String id) {
        TradeRecord record = records.get(id);
        if (record == null || record.rolledBack()) {
            return;
        }

        records.put(id, record.withRolledBack(true));
        save();
    }

    private void trimToConfiguredLimit() {
        int limit = Math.max(1, plugin.getConfig().getInt("settings.max-history-records", 100));
        List<TradeRecord> sorted = records.values().stream()
                .sorted(Comparator.comparingLong(TradeRecord::createdAt).reversed())
                .toList();

        records.clear();
        for (int i = 0; i < Math.min(limit, sorted.size()); i++) {
            TradeRecord record = sorted.get(i);
            records.put(record.id(), record);
        }
    }

    private void save() {
        YamlConfiguration config = new YamlConfiguration();
        for (TradeRecord record : records.values()) {
            String basePath = "trades." + record.id();
            config.set(basePath + ".created-at", record.createdAt());
            config.set(basePath + ".player-a.uuid", record.playerAId().toString());
            config.set(basePath + ".player-a.name", record.playerAName());
            config.set(basePath + ".player-b.uuid", record.playerBId().toString());
            config.set(basePath + ".player-b.name", record.playerBName());
            config.set(basePath + ".offer-a", cloneItems(record.offerA()));
            config.set(basePath + ".offer-b", cloneItems(record.offerB()));
            config.set(basePath + ".rolled-back", record.rolledBack());
        }

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save trade history: " + e.getMessage());
        }
    }

    private List<ItemStack> cloneItems(Collection<?> source) {
        List<ItemStack> items = new ArrayList<>();
        for (Object value : source) {
            if (value instanceof ItemStack item) {
                items.add(item.clone());
            }
        }
        return items;
    }
}
