package me.hsgamer.extrastorage.util;

import io.github.projectunified.uniitem.all.AllItemProvider;
import io.github.projectunified.uniitem.api.ItemKey;
import me.hsgamer.extrastorage.Debug;
import me.hsgamer.extrastorage.ExtraStorage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import static me.hsgamer.extrastorage.data.Constants.INVALID;

public class ItemUtil {
    public static final ExtraStorage instance = ExtraStorage.getInstance();
    public static final AllItemProvider provider = new AllItemProvider();

    // Sử dụng CacheBuilder để tạo cache với thời gian hết hạn
    private static final Cache<String, ItemPair> ITEM_CACHE = CacheBuilder.newBuilder()
            .maximumSize(1000) // Giới hạn kích thước cache
            .expireAfterAccess(30, TimeUnit.MINUTES) // Hết hạn sau 30 phút không được truy cập
            .build();

    /**
     * Validate the key to the material-key
     *
     * @param key the item key. Can be an ItemStack or a string as MATERIAL:DATA
     * @return the material-key
     */
    /**
     * Chuyển đổi đối tượng thành material key
     * 
     * @param key đối tượng cần chuyển đổi (ItemStack hoặc String)
     * @return material key đã chuẩn hóa, hoặc INVALID nếu không hợp lệ
     */
    public static String toMaterialKey(Object key) {
        if (key == null) {
            Debug.log("[KEY] Input key is null");
            return INVALID;
        }

        if (key instanceof ItemStack) {
            ItemStack item = (ItemStack) key;

            // Kiểm tra item null hoặc AIR
            if (item == null || item.getType() == Material.AIR) {
                Debug.log("[KEY] Item is null or AIR");
                return INVALID;
            }

            String keyStr = INVALID;

            // Thử lấy key từ provider
            try {
                ItemKey itemKey = provider.key(item);
                Debug.log("[KEY] ItemKey: " + itemKey);
                if (itemKey != null) {
                    keyStr = itemKey.type().toUpperCase(Locale.ROOT) + ":" + itemKey.id();
                }
            } catch (Exception e) {
                Debug.log("[KEY] Error getting item key: " + e.getMessage());
            }

            // Fallback nếu không lấy được key từ provider
            if (Objects.equals(INVALID, keyStr)) {
                if (!item.hasItemMeta()) {
                    keyStr = item.getType().name();
                } else {
                    // Có thể xử lý thêm các trường hợp đặc biệt ở đây
                    keyStr = item.getType().name();
                }
            }

            Debug.log(
                    "[ITEM] Item: " + item,
                    "[ITEM] KeyStr: " + keyStr);

            return keyStr;
        } else {
            try {
                String keyStr = normalizeMaterialKey(key.toString());
                Debug.log(
                        "[ITEM] Key: " + key,
                        "[ITEM] KeyStr: " + keyStr);
                return keyStr;
            } catch (Exception e) {
                Debug.log("[KEY] Error normalizing material key: " + e.getMessage());
                return INVALID;
            }
        }
    }

    private static boolean match(String key, String... keys) {
        for (String k : keys) {
            if (key.equalsIgnoreCase(k))
                return true;
        }
        return false;
    }

    // Normalize material key to ensure consistent format
    public static String normalizeMaterialKey(String key) {
        // Nếu key có định dạng MATERIAL:0, chuyển về MATERIAL
        if (key.endsWith(":0")) {
            key = key.substring(0, key.length() - 2);
        }

        String[] split = key.split(":", 2);
        if (split.length == 1) {
            return key;
        }

        String type = split[0];
        String rest = split[1];

        // Xử lý các plugin custom item
        if (match(type, "oraxen", "orx")) {
            return "ORAXEN:" + rest;
        }
        if (match(type, "itemsadder", "ia")) {
            return "ITEMSADDER:" + rest;
        }
        if (match(type, "nexo")) {
            return "NEXO:" + rest;
        }

        String finalType = provider.getType(type);
        finalType = finalType == null ? type : finalType;
        if (provider.isValidKey(new ItemKey(finalType, rest))) {
            return finalType.toUpperCase(Locale.ROOT) + ":" + rest;
        }

        return type;
    }

    public static ItemPair getItem(String key) {
        try {
            // Sử dụng Callable để tạo item nếu không có trong cache
            return ITEM_CACHE.get(key, () -> {
                String[] split = key.split(":", 2);
                ItemType itemType = ItemType.VANILLA;
                ItemStack item = null;
                if (split.length >= 2) {
                    String type = split[0].toLowerCase(Locale.ROOT);
                    String id = split[1];

                    itemType = ItemType.CUSTOM;
                    ItemKey itemKey = new ItemKey(type, id);
                    item = provider.item(itemKey);
                }

                if (itemType == ItemType.VANILLA) {
                    Material material = Material.matchMaterial(key);
                    if (material != null) {
                        item = new ItemStack(material, 1);
                        item.setItemMeta(null);
                    }
                }
                if (item == null) {
                    itemType = ItemType.NONE;
                }
                return new ItemPair(item, itemType);
            });
        } catch (Exception e) {
            // Xử lý trường hợp ngoại lệ
            Debug.log("[CACHE] Error getting item from cache: " + e.getMessage());

            // Tạo mới item và không cache
            String[] split = key.split(":", 2);
            ItemType itemType = ItemType.VANILLA;
            ItemStack item = null;

            if (split.length >= 2) {
                String type = split[0].toLowerCase(Locale.ROOT);
                String id = split[1];

                itemType = ItemType.CUSTOM;
                ItemKey itemKey = new ItemKey(type, id);
                item = provider.item(itemKey);
            }

            if (itemType == ItemType.VANILLA) {
                Material material = Material.matchMaterial(key);
                if (material != null) {
                    item = new ItemStack(material, 1);
                    item.setItemMeta(null);
                }
            }
            if (item == null) {
                itemType = ItemType.NONE;
            }
            return new ItemPair(item, itemType);
        }
    }

    public static boolean isValidItem(String key) {
        ItemPair itemPair = getItem(key);
        return itemPair.type() != ItemType.NONE;
    }

    public static void giveItem(Player player, ItemStack item) {
        int amount = item.getAmount();
        if (amount <= 0)
            return;

        while (amount > 0) {
            int give = Math.min(amount, item.getMaxStackSize());
            amount -= give;

            ItemStack clone = item.clone();
            clone.setAmount(give);
            player.getInventory().addItem(clone);
        }
    }

    public enum ItemType {
        NONE, VANILLA, CUSTOM
    }

    public static final class ItemPair {
        private final ItemStack item;
        private final ItemType type;

        public ItemPair(ItemStack item, ItemType type) {
            this.item = item;
            this.type = type;
        }

        public ItemStack item() {
            return item;
        }

        public ItemType type() {
            return type;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this)
                return true;
            if (obj == null || obj.getClass() != this.getClass())
                return false;
            ItemPair that = (ItemPair) obj;
            return Objects.equals(this.item, that.item) &&
                    Objects.equals(this.type, that.type);
        }

        @Override
        public int hashCode() {
            return Objects.hash(item, type);
        }

        @Override
        public String toString() {
            return "ItemPair[" +
                    "item=" + item + ", " +
                    "type=" + type + ']';
        }

    }
}
