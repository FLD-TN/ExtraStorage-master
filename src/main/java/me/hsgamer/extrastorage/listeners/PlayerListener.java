package me.hsgamer.extrastorage.listeners;

import me.hsgamer.extrastorage.ExtraStorage;
import me.hsgamer.extrastorage.data.user.UserManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

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
        
        // Sử dụng runTask để đảm bảo manager.load() được thực hiện sau khi player đã hoàn toàn tham gia server
        instance.getServer().getScheduler().runTask(instance, () -> {
            try {
                // Đảm bảo player vẫn online trước khi load data
                if (!player.isOnline()) return;
                
                // Load data và đảm bảo nó được hoàn thành
                manager.load(uuid);
                
                // Debug log
                instance.getLogger().info("Loaded storage data for player: " + player.getName());
            } catch (Exception e) {
                instance.getLogger().warning("Failed to load storage data for " + player.getName() + ": " + e.getMessage());
                e.printStackTrace();
            }
        });
        
        // Thông báo về yêu cầu kết bạn đang chờ - đảm bảo chạy sau khi dữ liệu đã được tải
        instance.getServer().getScheduler().runTaskLater(instance, () -> {
            if (!player.isOnline()) return;
            
            try {
                int pendingRequests = manager.getUser(player).getPendingPartnerRequests().size();
                if (pendingRequests > 0) {
                    player.sendMessage(me.hsgamer.extrastorage.configs.Message.getMessage("INFO.partner-request-received")
                        .replaceAll(me.hsgamer.extrastorage.util.Utils.getRegex("player"), "nhiều người"));
                }
            } catch (Exception e) {
                instance.getLogger().warning("Failed to check partner requests for " + player.getName() + ": " + e.getMessage());
            }
        }, 60L); // Tăng delay lên 3 giây để đảm bảo data đã được tải xong
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        
        try {
            // Lưu dữ liệu ngay lập tức khi player thoát
            manager.save(uuid);
            
            // Debug log
            instance.getLogger().info("Saved storage data for player: " + player.getName());
        } catch (Exception e) {
            instance.getLogger().severe("Failed to save storage data for " + player.getName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

}
