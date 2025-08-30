package me.hsgamer.extrastorage.listeners;

import me.hsgamer.extrastorage.manager.StorageStatusManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class StorageStatusListener implements Listener {

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Xóa trạng thái khỏi bộ nhớ khi người chơi thoát
        StorageStatusManager.getInstance().removeStatus(event.getPlayer().getUniqueId());
    }
}