package me.hsgamer.extrastorage.util;

import me.hsgamer.extrastorage.Debug;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;

/**
 * Tiện ích để kiểm tra và xử lý các loại vật phẩm từ các plugin khác (PHIÊN BẢN
 * NÂNG CẤP)
 */
public class CustomItemDetector {

    /**
     * Kiểm tra xem một ItemStack có phải là vật phẩm tùy chỉnh từ các plugin phổ
     * biến hay không
     * Đây là phương pháp kiểm tra chính và đáng tin cậy nhất.
     *
     * @param item ItemStack cần kiểm tra
     * @return true nếu là vật phẩm tùy chỉnh, false nếu là vật phẩm vanilla
     */
    public static boolean isCustomItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        // Phương pháp đáng tin cậy nhất là kiểm tra PersistentDataContainer
        PersistentDataContainer container = meta.getPersistentDataContainer();
        for (NamespacedKey key : container.getKeys()) {
            String namespace = key.getNamespace().toLowerCase();
            // Nếu item có bất kỳ "dấu hiệu" nào từ các plugin này, nó là custom item
            if (namespace.equals("mmoitems") || namespace.equals("itemsadder") || namespace.equals("oraxen")) {
                Debug.log("Item detected as Custom Item (namespace: " + namespace + "): " + item.getType());
                return true;
            }
        }

        // Có thể thêm các phương pháp kiểm tra cũ hơn (dựa vào lore) làm dự phòng nếu
        // cần,
        // nhưng phương pháp trên đã rất hiệu quả.

        return false;
    }

    /**
     * Lấy ID của MMOItem (vẫn giữ lại để tương thích)
     *
     * @param item ItemStack cần lấy ID
     * @return String ID của MMOItem, ví dụ "MATERIAL:GOLD_INGOT", hoặc null nếu
     *         không tìm thấy
     */
    public static String getMMOItemId(ItemStack item) {
        if (!isCustomItem(item)) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();

        // MMOItems lưu Type và ID trong PersistentDataContainer
        NamespacedKey typeKey = new NamespacedKey("mmoitems", "type");
        NamespacedKey idKey = new NamespacedKey("mmoitems", "id");

        if (container.has(typeKey, org.bukkit.persistence.PersistentDataType.STRING)
                && container.has(idKey, org.bukkit.persistence.PersistentDataType.STRING)) {
            String type = container.get(typeKey, org.bukkit.persistence.PersistentDataType.STRING);
            String id = container.get(idKey, org.bukkit.persistence.PersistentDataType.STRING);
            return type + ":" + id;
        }

        return null;
    }
}