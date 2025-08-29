package me.hsgamer.extrastorage.util;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import me.hsgamer.extrastorage.ExtraStorage;
import me.hsgamer.extrastorage.api.storage.Storage;
import me.hsgamer.extrastorage.api.user.User;

public class ItemFilterService {
    private static final Map<UUID, Set<String>> playerFilterCache = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> cacheTimestamp = new ConcurrentHashMap<>();
    // Thêm cache mới cho các loại vật phẩm phổ biến
    private static final Map<String, Boolean> commonItemTypeCache = new ConcurrentHashMap<>();
    // Thêm một cache để lưu trữ thông tin người chơi có filter hay không
    private static final Map<UUID, Boolean> hasFilterCache = new ConcurrentHashMap<>();

    private static final long CACHE_DURATION_MS = 30000; // 30 giây

    public static boolean shouldPickupToStorage(Player player, ItemStack item, Storage storage) {
        UUID playerId = player.getUniqueId();

        // Kiểm tra global filter
        if (!ExtraStorage.isFilterEnabled()) {
            return true;
        }

        // Lấy item key
        String itemKey = ItemUtil.toMaterialKey(item);
        if (itemKey == null || itemKey.equals("INVALID")) {
            return false;
        }

        // Kiểm tra xem người chơi có bộ lọc không
        if (!hasPlayerFilter(playerId)) {
            return false;
        }

        // Kiểm tra cache cho các loại vật phẩm phổ biến
        Boolean commonCacheResult = commonItemTypeCache.get(itemKey);
        if (commonCacheResult != null) {
            return commonCacheResult;
        }

        // Kiểm tra cache người dùng
        Long lastUpdate = cacheTimestamp.get(playerId);
        Set<String> filters = playerFilterCache.get(playerId);

        if (filters == null || lastUpdate == null ||
                System.currentTimeMillis() - lastUpdate > CACHE_DURATION_MS) {
            // Update cache
            filters = new HashSet<>(storage.getFilteredItems().keySet());
            playerFilterCache.put(playerId, filters);
            cacheTimestamp.put(playerId, System.currentTimeMillis());
        }

        boolean result = filters.contains(itemKey);

        // Thêm vào cache toàn cầu nếu là vật phẩm phổ biến
        if (isCommonItemType(itemKey)) {
            commonItemTypeCache.put(itemKey, result);
        }

        return result;
    }

    /**
     * Kiểm tra nhanh xem người chơi có bất kỳ bộ lọc nào không
     */
    public static boolean hasPlayerFilter(UUID playerId) {
        return hasFilterCache.computeIfAbsent(playerId, uuid -> {
            try {
                User user = ExtraStorage.getInstance().getUserManager().getUser(uuid);
                if (user == null)
                    return false;

                Storage storage = user.getStorage();
                if (storage == null)
                    return false;

                return !storage.getFilteredItems().isEmpty();
            } catch (Exception e) {
                return false;
            }
        });
    }

    /**
     * Xác định xem một loại vật phẩm có phổ biến không để cân nhắc cache toàn cục
     */
    private static boolean isCommonItemType(String itemType) {
        switch (itemType) {
            case "STONE":
            case "DIRT":
            case "COBBLESTONE":
            case "OAK_LOG":
            case "BIRCH_LOG":
            case "SPRUCE_LOG":
            case "SAND":
            case "GRAVEL":
            case "COAL":
            case "IRON_ORE":
            case "GOLD_ORE":
            case "DIAMOND_ORE":
            case "EMERALD_ORE":
            case "REDSTONE_ORE":
            case "LAPIS_ORE":
                return true;
            default:
                return false;
        }
    }

    public static void clearCache(UUID playerId) {
        playerFilterCache.remove(playerId);
        cacheTimestamp.remove(playerId);
        hasFilterCache.remove(playerId);
    }

    public static void clearAllCache() {
        playerFilterCache.clear();
        cacheTimestamp.clear();
        commonItemTypeCache.clear();
        hasFilterCache.clear();
    }

    /**
     * Làm sạch định kỳ các mục cache của các vật phẩm chung đã hết hạn
     * Gọi hàm này mỗi 5-10 phút để ngăn cache phát triển quá lớn
     */
    public static void performCacheCleanup() {
        long currentTime = System.currentTimeMillis();

        // Xóa cache người chơi quá hạn
        cacheTimestamp.entrySet().removeIf(entry -> currentTime - entry.getValue() > CACHE_DURATION_MS);

        // Xóa cache cho người chơi không còn timestamp
        playerFilterCache.keySet().removeIf(key -> !cacheTimestamp.containsKey(key));

        // Không cần xóa hasFilterCache và commonItemTypeCache vì chúng sẽ được cập nhật
        // khi cần
    }

    public static int getCacheSize() {
        return playerFilterCache.size() + commonItemTypeCache.size() + hasFilterCache.size();
    }
}