package me.hsgamer.extrastorage.listeners;

import me.hsgamer.extrastorage.ExtraStorage;
import me.hsgamer.extrastorage.data.user.UserManager;
import me.hsgamer.extrastorage.util.ItemFilterService;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.UUID;

public final class PlayerListener
        extends BaseListener {

    private final UserManager manager;

    public PlayerListener(ExtraStorage instance) {
        super(instance);
        this.manager = instance.getUserManager();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onLogin(PlayerLoginEvent event) {
        if (!manager.isLoaded())
            event.disallow(PlayerLoginEvent.Result.KICK_OTHER, "Please wait until the server is fully loaded!");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // CHỈ load user, không cần unload vì requests đã được lưu trong database
        manager.load(uuid);

        // Đảm bảo storage được kích hoạt
        me.hsgamer.extrastorage.api.user.User user = manager.getUser(player);
        if (user != null) {
            user.getStorage().setStatus(true);
            user.save(); // Lưu trạng thái
        }

        // Thông báo về yêu cầu kết bạn đang chờ
        instance.getServer().getScheduler().runTaskLater(instance, () -> {
            if (!player.isOnline())
                return;

            int pendingRequests = manager.getUser(player).getPendingPartnerRequests().size();
            if (pendingRequests > 0) {
                player.sendMessage(me.hsgamer.extrastorage.configs.Message.getMessage("INFO.partner-request-received")
                        .replaceAll(me.hsgamer.extrastorage.util.Utils.getRegex("player"), "nhiều người"));
            }
        }, 40L); // Delay 2 giây sau khi đăng nhập
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        manager.save(uuid);
        // KHÔNG unload ở đây để tránh race condition
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        // Tự động recover storage state khi player respawn
        Player player = event.getPlayer();
        instance.getServer().getScheduler().runTaskLater(instance, () -> {
            me.hsgamer.extrastorage.api.user.User user = manager.getUser(player);
            if (user != null) {
                user.getStorage().setStatus(true);
                user.save();
            }
        }, 20L); // Delay 1 giây
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        ItemFilterService.clearCache(e.getPlayer().getUniqueId());
        manager.unload(e.getPlayer().getUniqueId()); // Sửa userManager thành manager
    }
}