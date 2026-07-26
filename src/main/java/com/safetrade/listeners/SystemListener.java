package com.safetrade.listeners;

import com.safetrade.SafeTradePlugin;
import com.safetrade.update.VersionChecker;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class SystemListener implements Listener {

    private final SafeTradePlugin plugin;

    public SystemListener(SafeTradePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getTradeManager().releaseExpiredHolds(player);

        if (!player.hasPermission("safetrade.admin")) {
            return;
        }

        VersionChecker versionChecker = plugin.getVersionChecker();
        if (versionChecker == null || !versionChecker.isUpdateAvailable()) {
            return;
        }

        player.sendMessage(plugin.formatValuePrefixed(
                "messages.update-available",
                "&eSafeTrade update available: &b{value}",
                versionChecker.getLatestVersion()
        ));
        player.sendMessage(plugin.formatValuePrefixed(
                "messages.update-link",
                "&7Download: &f{value}",
                versionChecker.getLatestUrl()
        ));
    }
}
