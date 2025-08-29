package me.hsgamer.extrastorage.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import me.hsgamer.extrastorage.ExtraStorage;
import me.hsgamer.extrastorage.api.user.User;
import me.hsgamer.extrastorage.data.stub.StubStorage;

// Thêm listener để refresh cache khi quyền thay đổi
public class PermissionChangeListener implements Listener {
    private final ExtraStorage instance;

    public PermissionChangeListener(ExtraStorage instance) {
        this.instance = instance;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerPermissionChange(PlayerChangedWorldEvent event) {
        User user = instance.getUserManager().getUser(event.getPlayer());
        if (user != null) {
            ((StubStorage) user.getStorage()).refreshSpaceCache();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        User user = instance.getUserManager().getUser(event.getPlayer());
        if (user != null) {
            ((StubStorage) user.getStorage()).refreshSpaceCache();
        }
    }
}