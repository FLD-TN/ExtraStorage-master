package me.hsgamer.extrastorage.util;

import me.hsgamer.extrastorage.ExtraStorage;
import me.hsgamer.extrastorage.api.user.User;
import me.hsgamer.extrastorage.data.stub.StubStorage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class PerformanceOptimizer implements Listener {
    private final ExtraStorage instance;
    private final ConcurrentHashMap<UUID, Long> permissionCheckCache = new ConcurrentHashMap<>();

    public PerformanceOptimizer(ExtraStorage instance) {
        this.instance = instance;
        Bukkit.getPluginManager().registerEvents(this, instance);

        // Schedule cache cleanup
        Bukkit.getScheduler().runTaskTimerAsynchronously(instance, () -> {
            long currentTime = System.currentTimeMillis();
            permissionCheckCache.entrySet()
                    .removeIf(entry -> currentTime - entry.getValue() > TimeUnit.MINUTES.toMillis(5));
        }, 10, 10 * 60 * 20); // 10 phút
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        User user = instance.getUserManager().getUser(event.getPlayer());
        if (user != null) {
            ((StubStorage) user.getStorage()).refreshSpaceCache();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        permissionCheckCache.remove(uuid);

        // Cleanup user cache
        instance.getUserManager().cleanupCache();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        User user = instance.getUserManager().getUser(event.getPlayer());
        if (user != null) {
            ((StubStorage) user.getStorage()).refreshSpaceCache();
        }
    }
}