package me.hsgamer.extrastorage.data;

import com.google.common.base.Strings;
import me.hsgamer.extrastorage.ExtraStorage;
import me.hsgamer.extrastorage.Debug;
import me.hsgamer.extrastorage.configs.Message;
import me.hsgamer.extrastorage.util.ActionBar;
import me.hsgamer.extrastorage.util.Utils;
import org.bukkit.entity.Player;

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
        // ... (phần này giữ nguyên)
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

    // ĐỔI TÊN HÀM NÀY ĐỂ RÕ NGHĨA HƠN
    public void sendItemStoredNotification(Player player, String itemName, long amount, boolean fromBreak) {
        NotificationSettings settings = getSettings(player.getUniqueId());
        // Kiểm tra xem người chơi có muốn nhận thông báo này không
        if (fromBreak && !settings.isShowBreak())
            return;
        if (!fromBreak && !settings.isShowPickup())
            return;

        // Kiểm tra cooldown
        if (isOnCooldown(player.getUniqueId())) {
            return;
        }

        try {
            // LẤY TIN NHẮN TỪ MESSAGES.YML
            String messagePath = fromBreak ? "WARN.Stored.ActionBarFromBreak" : "WARN.Stored.ActionBar";
            String message = Message.getMessage(messagePath);

            // Nếu không có message trong config, dùng message mặc định
            if (Strings.isNullOrEmpty(message) || message.contains("Message not found")) {
                message = "§a+{amount} {item}" + (fromBreak ? " §7(Đào)" : "");
            }

            // Thay thế các placeholder
            message = message
                    .replaceAll(Utils.getRegex("amount"), String.valueOf(amount))
                    .replaceAll(Utils.getRegex("item"), itemName);

            // Gửi action bar
            ActionBar.send(player, message);

            // Cập nhật cooldown
            updateCooldown(player.getUniqueId());
        } catch (Exception e) {
            Debug.log("[Notification] Error sending stored notification: " + e.getMessage());
        }
    }

    // Sửa lại hàm sendPickupNotification để gọi hàm mới
    public void sendPickupNotification(Player player, String itemName, long amount) {
        sendItemStoredNotification(player, itemName, amount, false);
    }

    // Sửa lại hàm sendBreakNotification để gọi hàm mới
    public void sendBreakNotification(Player player, String itemName, long amount) {
        sendItemStoredNotification(player, itemName, amount, true);
    }

    public void sendStorageFullNotification(Player player) {
        NotificationSettings settings = getSettings(player.getUniqueId());
        if (!settings.isShowFull()) {
            return;
        }

        try {
            // LẤY TIN NHẮN TỪ MESSAGES.YML
            String message = Message.getMessage("WARN.StorageIsFull");
            if (Strings.isNullOrEmpty(message) || message.contains("Message not found")) {
                message = "§c! Kho của bạn đã đầy";
            }
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
}