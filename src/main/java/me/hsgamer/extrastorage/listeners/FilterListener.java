package me.hsgamer.extrastorage.listeners;

import me.hsgamer.extrastorage.ExtraStorage;
import me.hsgamer.extrastorage.util.ItemFilterService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class FilterListener implements Listener {
    public FilterListener(ExtraStorage plugin) {
        // Constructor need to accept the plugin parameter for consistency with other
        // listeners
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Xóa cache filter khi người chơi thoát game để giảm sử dụng bộ nhớ
        ItemFilterService.clearCache(event.getPlayer().getUniqueId());
    }
}
