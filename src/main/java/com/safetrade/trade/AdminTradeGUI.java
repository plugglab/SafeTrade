package com.safetrade.trade;

import com.safetrade.SafeTradePlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;

public class AdminTradeGUI {

    private static final int PREVIOUS_PAGE_SLOT = 45;
    private static final int INFO_SLOT = 49;
    private static final int NEXT_PAGE_SLOT = 53;
    private static final int PAGE_SIZE = 45;
    private static final Map<UUID, AdminViewState> STATES = new HashMap<>();
    private static SafeTradePlugin plugin;

    public static void init(SafeTradePlugin pluginInstance) {
        plugin = pluginInstance;
    }

    public static void open(Player player, int page) {
        if (plugin == null) {
            return;
        }

        int maxPage = Math.max(1, plugin.getTradeManager().getHistoryStore().getPageCount(PAGE_SIZE));
        int currentPage = Math.max(0, Math.min(page, maxPage - 1));
        List<TradeRecord> records = plugin.getTradeManager().getHistoryStore().getRecentRecords(currentPage, PAGE_SIZE);

        Inventory inventory = Bukkit.createInventory(null, 54, getTitle());
        List<String> recordIds = new ArrayList<>();

        for (int slot = 0; slot < records.size(); slot++) {
            TradeRecord record = records.get(slot);
            recordIds.add(record.id());
            inventory.setItem(slot, createTradeItem(record));
        }

        inventory.setItem(PREVIOUS_PAGE_SLOT, createNavItem(Material.ARROW, text("admin-gui.previous-page", "Previous Page")));
        inventory.setItem(INFO_SLOT, createInfoItem(currentPage + 1, maxPage));
        inventory.setItem(NEXT_PAGE_SLOT, createNavItem(Material.ARROW, text("admin-gui.next-page", "Next Page")));

        fillBottomRow(inventory);
        inventory.setItem(PREVIOUS_PAGE_SLOT, createNavItem(Material.ARROW, text("admin-gui.previous-page", "Previous Page")));
        inventory.setItem(INFO_SLOT, createInfoItem(currentPage + 1, maxPage));
        inventory.setItem(NEXT_PAGE_SLOT, createNavItem(Material.ARROW, text("admin-gui.next-page", "Next Page")));

        STATES.put(player.getUniqueId(), new AdminViewState(currentPage, recordIds));
        player.openInventory(inventory);
    }

    public static boolean isAdminInventory(InventoryView view) {
        return getTitle().equals(view.getTitle());
    }

    public static void close(Player player) {
        STATES.remove(player.getUniqueId());
    }

    public static void handleClick(Player player, int rawSlot) {
        AdminViewState state = STATES.get(player.getUniqueId());
        if (state == null || plugin == null) {
            return;
        }

        if (rawSlot == PREVIOUS_PAGE_SLOT) {
            open(player, state.page() - 1);
            return;
        }
        if (rawSlot == NEXT_PAGE_SLOT) {
            open(player, state.page() + 1);
            return;
        }
        if (rawSlot < 0 || rawSlot >= state.recordIds().size()) {
            return;
        }

        String recordId = state.recordIds().get(rawSlot);
        boolean success = plugin.getTradeManager().rollbackTrade(player, recordId);
        if (success) {
            open(player, state.page());
        }
    }

    private static void fillBottomRow(Inventory inventory) {
        ItemStack filler = createNamedItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int slot = 45; slot < 54; slot++) {
            inventory.setItem(slot, filler);
        }
    }

    private static ItemStack createTradeItem(TradeRecord record) {
        Material material = record.rolledBack() ? Material.BARRIER : Material.WRITABLE_BOOK;
        String status = record.rolledBack()
                ? text("admin-gui.status-rolled-back", "&cRolled back")
                : text("admin-gui.status-completed", "&aCompleted");

        return createNamedItem(
                material,
                text("admin-gui.trade-title", "Trade {id}").replace("{id}", record.id()),
                text("admin-gui.trade-players", "{playerA} <-> {playerB}")
                        .replace("{playerA}", record.playerAName())
                        .replace("{playerB}", record.playerBName()),
                text("admin-gui.trade-time", "Time: {time}").replace("{time}", formatTime(record.createdAt())),
                text("admin-gui.trade-status", "Status: {status}").replace("{status}", status),
                text("admin-gui.trade-action", "Click to rollback this trade.")
        );
    }

    private static ItemStack createInfoItem(int page, int maxPage) {
        return createNamedItem(
                Material.BOOK,
                text("admin-gui.title-item", "Trade History"),
                text("admin-gui.page", "Page {page}/{maxPage}")
                        .replace("{page}", String.valueOf(page))
                        .replace("{maxPage}", String.valueOf(maxPage)),
                text("admin-gui.info", "Click a trade to attempt rollback.")
        );
    }

    private static ItemStack createNavItem(Material material, String title) {
        return createNamedItem(material, title);
    }

    private static ItemStack createNamedItem(Material material, String title, String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(title);
            if (loreLines.length > 0) {
                meta.setLore(List.of(loreLines));
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static String formatTime(long epochMillis) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        format.setTimeZone(TimeZone.getDefault());
        return format.format(new Date(epochMillis));
    }

    private static String getTitle() {
        return text("admin-gui.title", "SafeTrade Admin");
    }

    private static String text(String path, String fallback) {
        return plugin == null ? fallback : plugin.getText(path, fallback);
    }

    private record AdminViewState(int page, List<String> recordIds) {
    }
}
