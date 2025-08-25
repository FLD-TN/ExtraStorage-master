package me.hsgamer.extrastorage.data;

import me.hsgamer.extrastorage.ExtraStorage;
import me.hsgamer.extrastorage.Debug;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NotificationManager {
    private final ExtraStorage plugin;
    private final Map<UUID, NotificationSettings> playerSettings;
    private final Map<UUID, Long> lastNotificationTime;
    private static final long NOTIFICATION_COOLDOWN = 500; // 0.5 giây

    public NotificationManager(ExtraStorage plugin) {
        this.plugin = plugin;
        this.playerSettings = new ConcurrentHashMap<>();
        this.lastNotificationTime = new ConcurrentHashMap<>();
    }

    public static class NotificationSettings {
        private boolean enabled = true;
        private boolean showPickup = true;
        private boolean showBreak = true;
        private boolean showFull = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isShowPickup() {
            return showPickup && enabled;
        }

        public void setShowPickup(boolean showPickup) {
            this.showPickup = showPickup;
        }

        public boolean isShowBreak() {
            return showBreak && enabled;
        }

        public void setShowBreak(boolean showBreak) {
            this.showBreak = showBreak;
        }

        public boolean isShowFull() {
            return showFull && enabled;
        }

        public void setShowFull(boolean showFull) {
            this.showFull = showFull;
        }
    }

    public NotificationSettings getSettings(UUID playerId) {
        return playerSettings.computeIfAbsent(playerId, k -> new NotificationSettings());
    }

    public void sendPickupNotification(Player player, String itemName, long amount) {
        NotificationSettings settings = getSettings(player.getUniqueId());
        if (!settings.isShowPickup() || isOnCooldown(player.getUniqueId())) {
            return;
        }

        try {
            // Gửi thông báo qua ActionBar để ít gây phiền hơn
            String message = "§a+ " + amount + " " + itemName;
            player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                    net.md_5.bungee.api.chat.TextComponent.fromLegacyText(message));

            updateCooldown(player.getUniqueId());
        } catch (Exception e) {
            Debug.log("[Notification] Error sending pickup notification: " + e.getMessage());
        }
    }

    public void sendBreakNotification(Player player, String itemName, long amount) {
        NotificationSettings settings = getSettings(player.getUniqueId());
        if (!settings.isShowBreak() || isOnCooldown(player.getUniqueId())) {
            return;
        }

        try {
            String message = "§a+ " + amount + " " + itemName + " §7(Từ đào block)";
            player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                    net.md_5.bungee.api.chat.TextComponent.fromLegacyText(message));

            updateCooldown(player.getUniqueId());
        } catch (Exception e) {
            Debug.log("[Notification] Error sending break notification: " + e.getMessage());
        }
    }

    public void sendStorageFullNotification(Player player) {
        NotificationSettings settings = getSettings(player.getUniqueId());
        if (!settings.isShowFull()) {
            return;
        }

        try {
            String message = "§c! Kho của bạn đã đầy";
            player.sendMessage(message);
        } catch (Exception e) {
            Debug.log("[Notification] Error sending full notification: " + e.getMessage());
        }
    }

    private boolean isOnCooldown(UUID playerId) {
        long lastTime = lastNotificationTime.getOrDefault(playerId, 0L);
        return System.currentTimeMillis() - lastTime < NOTIFICATION_COOLDOWN;
    }

    private void updateCooldown(UUID playerId) {
        lastNotificationTime.put(playerId, System.currentTimeMillis());
    }

    // Lưu settings
    public void saveSettings() {
        // TODO: Implement settings save
    }
}
