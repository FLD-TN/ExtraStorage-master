package me.hsgamer.extrastorage.listeners;

import me.hsgamer.extrastorage.ExtraStorage;
import me.hsgamer.extrastorage.api.storage.Storage;
import me.hsgamer.extrastorage.configs.Setting;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

// ListenerUtil giờ đây chỉ có một nhiệm vụ duy nhất: thêm đồ vào kho và gọi NotificationManager.
interface ListenerUtil {
    static void addToStorage(Player player, Storage storage, ItemStack item, int amount) {
        // Thêm vật phẩm vào kho
        storage.add(item, amount);

        // Lấy setting
        Setting setting = ExtraStorage.getInstance().getSetting();

        // Phát âm thanh nếu được cấu hình
        try {
            if (setting.getPickupSound() != null) {
                player.playSound(player.getLocation(), setting.getPickupSound(), 4.0f, 2.0f);
            }
        } catch (Exception e) {
            ExtraStorage.getInstance().getLogger().warning("Failed to play pickup sound: " + e.getMessage());
        }

        // GIAO TOÀN BỘ VIỆC GỬI THÔNG BÁO CHO NOTIFICATION MANAGER
        try {
            String itemName = setting.getNameFormatted(item, true);
            ExtraStorage.getInstance().getNotificationManager().sendPickupNotification(player, itemName, amount);
        } catch (Exception e) {
            ExtraStorage.getInstance().getLogger().warning("Failed to send notification: " + e.getMessage());
        }
    }
}