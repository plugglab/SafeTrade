package com.safetrade.trade;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.safetrade.SafeTradePlugin;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class TradeCashStore {

    private final SafeTradePlugin plugin;
    private final File file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<String, CashRecord> papers = new LinkedHashMap<>();

    public TradeCashStore(SafeTradePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "cash-papers.json");
        load();
    }

    public synchronized String issue(double amount) {
        String id = UUID.randomUUID().toString();
        papers.put(id, new CashRecord(amount, false));
        save();
        return id;
    }

    public synchronized double getRedeemableValue(String id) {
        CashRecord record = papers.get(id);
        return record != null && !record.redeemed() && Double.isFinite(record.amount()) && record.amount() > 0D
                ? record.amount() : 0D;
    }

    public synchronized boolean markRedeemed(String id) {
        CashRecord record = papers.get(id);
        if (record == null || record.redeemed()) {
            return false;
        }
        papers.put(id, new CashRecord(record.amount(), true));
        save();
        return true;
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        try (FileReader reader = new FileReader(file)) {
            CashFile data = gson.fromJson(reader, CashFile.class);
            if (data != null && data.papers != null) {
                papers.putAll(data.papers);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to load cash papers: " + e.getMessage());
        }
    }

    private void save() {
        CashFile data = new CashFile();
        data.papers = papers;
        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(data, writer);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save cash papers: " + e.getMessage());
        }
    }

    private static final class CashFile {
        private Map<String, CashRecord> papers = new LinkedHashMap<>();
    }

    private record CashRecord(double amount, boolean redeemed) {
    }
}
