package me.hsgamer.extrastorage.listeners;

import com.google.common.base.Strings;
import me.hsgamer.extrastorage.Debug;
import me.hsgamer.extrastorage.ExtraStorage;
import me.hsgamer.extrastorage.api.storage.Storage;
import me.hsgamer.extrastorage.configs.Message;
import me.hsgamer.extrastorage.configs.Setting;
import me.hsgamer.extrastorage.util.ActionBar;
import me.hsgamer.extrastorage.util.Digital;
import me.hsgamer.extrastorage.util.Utils;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

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

        try {
            // Gửi thông báo qua ActionBar và đảm bảo nó luôn được hiển thị
            String message = Message.getMessage("WARN.Stored.ActionBar");
            if (!Strings.isNullOrEmpty(message)) {
                String itemName = setting.getNameFormatted(item, true);
                String quantity = String.valueOf(amount);
                String current = "0";
                
                try {
                    current = Digital.formatThousands(storage.getItem(item).get().getQuantity());
                } catch (Exception e) {
                    Debug.log("[ListenerUtil] Failed to get current quantity: " + e.getMessage());
                }
                
                message = message
                    .replaceAll(Utils.getRegex("current"), current)
                    .replaceAll(Utils.getRegex("quantity", "amount"), quantity)
                    .replaceAll(Utils.getRegex("item"), itemName);
                
                // Sử dụng synchronized để đảm bảo thông báo không bị bỏ qua
                synchronized (player) {
                    ActionBar.send(player, message);
                }
                
                // Thêm thông báo chat nếu có cấu hình
                String chatMessage = Message.getMessage("WARN.Stored.Chat");
                if (!Strings.isNullOrEmpty(chatMessage)) {
                    chatMessage = chatMessage
                        .replaceAll(Utils.getRegex("amount"), quantity)
                        .replaceAll(Utils.getRegex("item"), itemName);
                    player.sendMessage(chatMessage);
                }
            }
        } catch (Exception e) {
            ExtraStorage.getInstance().getLogger().warning("Failed to send notification: " + e.getMessage());
            Debug.log("[ListenerUtil] Notification error: " + e.toString());
        }
    }
}
