package me.hsgamer.extrastorage.util;

import me.hsgamer.extrastorage.Debug;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tiện ích để kiểm tra và xử lý các loại vật phẩm từ các plugin khác
 */
public class CustomItemDetector {
    private static final Pattern MMO_TYPE_PATTERN = Pattern.compile("Type:\\s*([A-Za-z0-9_]+)");
    private static final Pattern MMO_ID_PATTERN = Pattern.compile("ID:\\s*([A-Za-z0-9_]+)");

    /**
     * Kiểm tra xem một ItemStack có phải là MMOItem hay không
     * 
     * @param item ItemStack cần kiểm tra
     * @return true nếu là MMOItem, false nếu không phải
     */
    public static boolean isMMOItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        
        ItemMeta meta = item.getItemMeta();
        
        // Kiểm tra các thuộc tính đặc trưng của MMOItems
        return meta.hasLore() && meta.hasDisplayName() && 
               (meta.getPersistentDataContainer().getKeys().size() > 0 || 
                (meta.getLore() != null && !meta.getLore().isEmpty() && 
                 meta.getLore().stream().anyMatch(line -> 
                     line.contains("MMO") || 
                     line.contains("Type:") || 
                     line.contains("Level:"))));
    }
    
    /**
     * Lấy ID của MMOItem
     * @param item ItemStack cần lấy ID
     * @return String ID của MMOItem, null nếu không phải MMOItem
     */
    public static String getMMOItemId(ItemStack item) {
        if (item == null || !item.hasItemMeta() || !isMMOItem(item)) {
            return null;
        }
        
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();
        
        // Phương pháp 1: Kiểm tra persistent data container
        for (NamespacedKey key : container.getKeys()) {
            if (key.getKey().equalsIgnoreCase("type") && key.getNamespace().toLowerCase().contains("mmo")) {
                try {
                    String type = container.get(key, PersistentDataType.STRING);
                    
                    // Tìm key cho ID
                    for (NamespacedKey idKey : container.getKeys()) {
                        if (idKey.getKey().equalsIgnoreCase("id") && idKey.getNamespace().toLowerCase().contains("mmo")) {
                            String id = container.get(idKey, PersistentDataType.STRING);
                            return type + ":" + id;
                        }
                    }
                    return type;
                } catch (Exception e) {
                    Debug.log("[CustomItemDetector] Error getting MMOItem ID: " + e.getMessage());
                }
            }
        }
        
        // Phương pháp 2: Kiểm tra lore
        if (meta.hasLore()) {
            List<String> lore = meta.getLore();
            String type = null;
            String id = null;
            
            for (String line : lore) {
                if (type == null) {
                    Matcher typeMatcher = MMO_TYPE_PATTERN.matcher(line);
                    if (typeMatcher.find()) {
                        type = typeMatcher.group(1);
                    }
                }
                
                if (id == null) {
                    Matcher idMatcher = MMO_ID_PATTERN.matcher(line);
                    if (idMatcher.find()) {
                        id = idMatcher.group(1);
                    }
                }
                
                if (type != null && id != null) {
                    return type + ":" + id;
                }
            }
            
            // Nếu chỉ có type
            if (type != null) {
                return type;
            }
        }
        
        // Không tìm thấy thông tin chi tiết, trả về tên hiển thị làm ID
        if (meta.hasDisplayName()) {
            return "UNKNOWN:" + meta.getDisplayName().replaceAll("§[0-9a-fklmnor]", "");
        }
        
        return null;
    }
    
    /**
     * Kiểm tra xem một ItemStack có phải là vật phẩm tùy chỉnh từ plugin khác hay không
     * 
     * @param item ItemStack cần kiểm tra
     * @return true nếu là vật phẩm tùy chỉnh, false nếu là vật phẩm vanilla
     */
    public static boolean isCustomItem(ItemStack item) {
        if (item == null) {
            return false;
        }
        
        // Kiểm tra MMOItems
        if (isMMOItem(item)) {
            Debug.log("Item detected as MMOItem: " + item.getType());
            return true;
        }
        
        // Thêm các kiểm tra khác cho các plugin item khác nếu cần
        
        return false;
    }
}
