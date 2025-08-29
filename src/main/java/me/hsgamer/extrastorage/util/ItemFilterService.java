package me.hsgamer.extrastorage.util;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import me.hsgamer.extrastorage.Debug;
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

    /**
     * Kiểm tra xem một vật phẩm có được thu nhặt vào kho không
     * Cải tiến: Sử dụng phương thức updateFilterCache mới và xử lý lỗi tốt hơn
     *
     * @param player  Người chơi
     * @param item    Vật phẩm cần kiểm tra
     * @param storage Kho chứa của người chơi
     * @return true nếu vật phẩm nên được thu nhặt vào kho
     */
    public static boolean shouldPickupToStorage(Player player, ItemStack item, Storage storage) {
        if (player == null || item == null || storage == null) {
            Debug.log("[ItemFilterService] Invalid input: player=" + (player != null) +
                    ", item=" + (item != null) + ", storage=" + (storage != null));
            return false;
        }

        UUID playerId = player.getUniqueId();

        // Ghi log để debug
        Debug.log("[ItemFilterService] Checking if item should be picked up to storage for player " + player.getName());

        // Kiểm tra global filter
        if (!ExtraStorage.isFilterEnabled()) {
            Debug.log("[ItemFilterService] Global filter is disabled, always allow pickup");
            return true;
        }

        // Lấy item key
        String itemKey;
        try {
            itemKey = ItemUtil.toMaterialKey(item);
            if (itemKey == null || itemKey.equals("INVALID")) {
                Debug.log("[ItemFilterService] Invalid item key: " + (item != null ? item.getType().name() : "null"));
                return false;
            }
        } catch (Exception e) {
            Debug.log("[ItemFilterService] Error getting material key: " + e.getMessage());
            return false;
        }

        Debug.log("[ItemFilterService] Processing item key: " + itemKey);

        // Kiểm tra xem người chơi có bộ lọc không
        if (!hasPlayerFilter(playerId)) {
            Debug.log("[ItemFilterService] Player " + player.getName() + " has no filters");
            return false;
        }

        // Kiểm tra cache cho các loại vật phẩm phổ biến
        Boolean commonCacheResult = commonItemTypeCache.get(itemKey);
        if (commonCacheResult != null) {
            Debug.log("[ItemFilterService] Using common item cache for " + itemKey + ": " + commonCacheResult);
            return commonCacheResult;
        }

        // Kiểm tra cache người dùng
        Long lastUpdate = cacheTimestamp.get(playerId);
        Set<String> filters = playerFilterCache.get(playerId);

        // Nếu cache không tồn tại hoặc đã hết hạn, cập nhật cache
        if (filters == null || lastUpdate == null || System.currentTimeMillis() - lastUpdate > CACHE_DURATION_MS) {
            // Sử dụng phương thức updateFilterCache mới
            Debug.log("[ItemFilterService] Cache expired or missing for player " + player.getName() + ", updating...");
            boolean updated = updateFilterCache(playerId, storage);

            if (!updated) {
                Debug.log("[ItemFilterService] Failed to update filter cache for player " + player.getName());
                return false;
            }

            // Lấy lại bộ lọc sau khi cập nhật
            filters = playerFilterCache.get(playerId);

            // Log toàn bộ danh sách filter để debug
            if (filters != null && filters.size() < 20) { // Chỉ log khi số lượng filter nhỏ
                Debug.log("[ItemFilterService] Filter list: " + String.join(", ", filters));
            }
        }

        // Kiểm tra xem filters có null không (có thể xảy ra nếu cập nhật thất bại)
        if (filters == null) {
            Debug.log("[ItemFilterService] Filters is null for player " + player.getName() + ", denying pickup");
            return false;
        }

        boolean result = filters.contains(itemKey);
        Debug.log("[ItemFilterService] Item " + itemKey + " is " + (result ? "in" : "not in") + " filter");

        // Thêm vào cache toàn cầu nếu là vật phẩm phổ biến
        if (isCommonItemType(itemKey)) {
            commonItemTypeCache.put(itemKey, result);
            Debug.log("[ItemFilterService] Added result to common item cache: " + itemKey + "=" + result);
        }

        return result;
    }

    /**
     * Kiểm tra nhanh xem người chơi có bất kỳ bộ lọc nào không
     * Cải tiến: Thêm log và xử lý lỗi tốt hơn
     */
    public static boolean hasPlayerFilter(UUID playerId) {
        return hasFilterCache.computeIfAbsent(playerId, uuid -> {
            try {
                User user = ExtraStorage.getInstance().getUserManager().getUser(uuid);
                if (user == null) {
                    Debug.log("[ItemFilterService] User not found for UUID: " + uuid);
                    return false;
                }

                Storage storage = user.getStorage();
                if (storage == null) {
                    Debug.log("[ItemFilterService] Storage not found for UUID: " + uuid);
                    return false;
                }

                Map<String, ?> filteredItems = storage.getFilteredItems();
                boolean hasFilter = !filteredItems.isEmpty();

                // Log thông tin để debug
                Debug.log("[ItemFilterService] Player " + uuid + " has filter: " + hasFilter +
                        " (filter count: " + filteredItems.size() + ")");

                return hasFilter;
            } catch (Exception e) {
                Debug.log("[ItemFilterService] Error checking filter for UUID " + uuid + ": " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        });
    }

    /**
     * Xác định xem một loại vật phẩm có phổ biến không để cân nhắc cache toàn cục
     * Đã mở rộng danh sách để bao gồm nhiều loại vật phẩm phổ biến hơn
     */
    private static boolean isCommonItemType(String itemType) {
        // Phân loại thành các nhóm để dễ quản lý

        // Nhóm các khối xây dựng phổ biến
        switch (itemType) {
            // Khối cơ bản
            case "STONE":
            case "DIRT":
            case "GRASS_BLOCK":
            case "COBBLESTONE":
            case "SAND":
            case "GRAVEL":
            case "CLAY":
            case "NETHERRACK":
            case "END_STONE":
            case "SOUL_SAND":
            case "SOUL_SOIL":
            case "DEEPSLATE":
            case "TUFF":
            case "CALCITE":
            case "DRIPSTONE_BLOCK":
            case "MOSS_BLOCK":

                // Gỗ các loại
            case "OAK_LOG":
            case "BIRCH_LOG":
            case "SPRUCE_LOG":
            case "JUNGLE_LOG":
            case "ACACIA_LOG":
            case "DARK_OAK_LOG":
            case "CRIMSON_STEM":
            case "WARPED_STEM":
            case "MANGROVE_LOG":
            case "CHERRY_LOG":

                // Quặng các loại
            case "COAL_ORE":
            case "IRON_ORE":
            case "GOLD_ORE":
            case "DIAMOND_ORE":
            case "EMERALD_ORE":
            case "REDSTONE_ORE":
            case "LAPIS_ORE":
            case "COPPER_ORE":
            case "NETHER_GOLD_ORE":
            case "NETHER_QUARTZ_ORE":
            case "ANCIENT_DEBRIS":
            case "DEEPSLATE_COAL_ORE":
            case "DEEPSLATE_IRON_ORE":
            case "DEEPSLATE_GOLD_ORE":
            case "DEEPSLATE_DIAMOND_ORE":
            case "DEEPSLATE_EMERALD_ORE":
            case "DEEPSLATE_REDSTONE_ORE":
            case "DEEPSLATE_LAPIS_ORE":
            case "DEEPSLATE_COPPER_ORE":

                // Vật phẩm quý
            case "DIAMOND":
            case "EMERALD":
            case "GOLD_INGOT":
            case "IRON_INGOT":
            case "NETHERITE_INGOT":
            case "NETHERITE_SCRAP":
            case "COAL":
            case "LAPIS_LAZULI":
            case "REDSTONE":
            case "COPPER_INGOT":
            case "RAW_IRON":
            case "RAW_GOLD":
            case "RAW_COPPER":
            case "AMETHYST_SHARD":
            case "QUARTZ":
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
        Debug.log("[ItemFilterService] All cache cleared");
    }

    /**
     * Cập nhật cache bộ lọc cho một người chơi cụ thể
     * Cải tiến: Trực tiếp cập nhật cache thay vì xóa nó
     *
     * @param playerId UUID của người chơi
     * @param storage  Kho chứa của người chơi
     * @return true nếu cập nhật thành công
     */
    public static boolean updateFilterCache(UUID playerId, Storage storage) {
        try {
            if (storage == null) {
                Debug.log("[ItemFilterService] Cannot update filter cache - storage is null for player " + playerId);
                clearCache(playerId);
                return false;
            }

            Map<String, ?> filteredItems = storage.getFilteredItems();
            Set<String> filters = new HashSet<>(filteredItems.keySet());

            playerFilterCache.put(playerId, filters);
            cacheTimestamp.put(playerId, System.currentTimeMillis());
            hasFilterCache.put(playerId, !filters.isEmpty());

            Debug.log("[ItemFilterService] Successfully updated filter cache for player " + playerId +
                    " with " + filters.size() + " items");
            return true;
        } catch (Exception e) {
            Debug.log("[ItemFilterService] Error updating filter cache for player " + playerId + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Làm sạch định kỳ các mục cache đã hết hạn
     * Cải tiến: Giới hạn kích thước cho commonItemTypeCache và hasFilterCache
     * Gọi hàm này mỗi 5-10 phút để ngăn cache phát triển quá lớn
     */
    public static void performCacheCleanup() {
        long currentTime = System.currentTimeMillis();
        Debug.log("[ItemFilterService] Starting cache cleanup. Current cache sizes: " +
                "playerFilter=" + playerFilterCache.size() +
                ", commonItemType=" + commonItemTypeCache.size() +
                ", hasFilter=" + hasFilterCache.size());

        // Xóa cache người chơi quá hạn
        cacheTimestamp.entrySet().removeIf(entry -> {
            boolean expired = currentTime - entry.getValue() > CACHE_DURATION_MS;
            if (expired) {
                UUID playerId = entry.getKey();
                playerFilterCache.remove(playerId);
                hasFilterCache.remove(playerId);
                Debug.log("[ItemFilterService] Removed expired cache for player: " + playerId);
            }
            return expired;
        });

        // Xóa cache cho người chơi không còn timestamp
        playerFilterCache.keySet().removeIf(key -> {
            boolean shouldRemove = !cacheTimestamp.containsKey(key);
            if (shouldRemove) {
                Debug.log("[ItemFilterService] Removed orphaned filter cache for player: " + key);
            }
            return shouldRemove;
        });

        // Giới hạn kích thước cho commonItemTypeCache (giữ tối đa 1000 mục)
        if (commonItemTypeCache.size() > 1000) {
            int itemsToRemove = commonItemTypeCache.size() - 1000;
            commonItemTypeCache.keySet().stream()
                    .limit(itemsToRemove)
                    .forEach(key -> commonItemTypeCache.remove(key));
            Debug.log("[ItemFilterService] Trimmed commonItemTypeCache, removed " + itemsToRemove + " items");
        }

        // Giới hạn kích thước cho hasFilterCache (giữ tối đa 500 mục)
        if (hasFilterCache.size() > 500) {
            int itemsToRemove = hasFilterCache.size() - 500;
            hasFilterCache.keySet().stream()
                    .limit(itemsToRemove)
                    .forEach(key -> hasFilterCache.remove(key));
            Debug.log("[ItemFilterService] Trimmed hasFilterCache, removed " + itemsToRemove + " items");
        }

        Debug.log("[ItemFilterService] Finished cache cleanup. New cache sizes: " +
                "playerFilter=" + playerFilterCache.size() +
                ", commonItemType=" + commonItemTypeCache.size() +
                ", hasFilter=" + hasFilterCache.size());
    }

    /**
     * Trả về tổng kích thước của cache
     * 
     * @return số lượng mục trong tất cả các cache
     */
    public static int getCacheSize() {
        return playerFilterCache.size() + commonItemTypeCache.size() + hasFilterCache.size();
    }

    /**
     * Trả về thông tin chi tiết về kích thước của từng loại cache
     * 
     * @return chuỗi mô tả chi tiết về kích thước cache
     */
    public static String getCacheStats() {
        StringBuilder stats = new StringBuilder();
        stats.append("Filter Cache Stats:\n");
        stats.append("- Player filter cache: ").append(playerFilterCache.size()).append(" entries\n");
        stats.append("- Common item type cache: ").append(commonItemTypeCache.size()).append(" entries\n");
        stats.append("- Has filter cache: ").append(hasFilterCache.size()).append(" entries\n");
        stats.append("- Total cached timestamps: ").append(cacheTimestamp.size()).append(" entries\n");

        // Phân tích chi tiết kích thước của từng bộ lọc người chơi
        if (!playerFilterCache.isEmpty()) {
            int minFilterSize = Integer.MAX_VALUE;
            int maxFilterSize = 0;
            int totalFilterSize = 0;

            for (Set<String> filters : playerFilterCache.values()) {
                int size = filters.size();
                minFilterSize = Math.min(minFilterSize, size);
                maxFilterSize = Math.max(maxFilterSize, size);
                totalFilterSize += size;
            }

            double avgFilterSize = (double) totalFilterSize / playerFilterCache.size();
            stats.append("- Filter sizes: min=").append(minFilterSize)
                    .append(", max=").append(maxFilterSize)
                    .append(", avg=").append(String.format("%.2f", avgFilterSize)).append("\n");
        }

        return stats.toString();
    }

    /**
     * Kiểm tra và sửa chữa các vấn đề không nhất quán trong cache
     * Gọi hàm này khi plugin khởi động lại hoặc khi cần đảm bảo dữ liệu nhất quán
     */
    public static void validateAndRepairCache() {
        Debug.log("[ItemFilterService] Starting cache validation and repair");
        int fixedEntries = 0;

        // Đảm bảo mỗi mục trong playerFilterCache có timestamp tương ứng
        Set<UUID> playersWithoutTimestamp = new HashSet<>();
        for (UUID playerId : playerFilterCache.keySet()) {
            if (!cacheTimestamp.containsKey(playerId)) {
                playersWithoutTimestamp.add(playerId);
                cacheTimestamp.put(playerId, System.currentTimeMillis());
                fixedEntries++;
            }
        }

        // Đảm bảo mỗi mục trong cacheTimestamp có playerFilterCache tương ứng
        Set<UUID> orphanedTimestamps = new HashSet<>();
        for (UUID playerId : cacheTimestamp.keySet()) {
            if (!playerFilterCache.containsKey(playerId)) {
                orphanedTimestamps.add(playerId);
                fixedEntries++;
            }
        }

        // Xóa timestamps không có filter tương ứng
        for (UUID playerId : orphanedTimestamps) {
            cacheTimestamp.remove(playerId);
        }

        // Đảm bảo hasFilterCache phản ánh đúng trạng thái của playerFilterCache
        for (UUID playerId : playerFilterCache.keySet()) {
            Set<String> filters = playerFilterCache.get(playerId);
            Boolean hasFilter = hasFilterCache.get(playerId);

            if (hasFilter == null) {
                hasFilterCache.put(playerId, !filters.isEmpty());
                fixedEntries++;
            } else if (hasFilter && filters.isEmpty()) {
                hasFilterCache.put(playerId, false);
                fixedEntries++;
            } else if (!hasFilter && !filters.isEmpty()) {
                hasFilterCache.put(playerId, true);
                fixedEntries++;
            }
        }

        Debug.log("[ItemFilterService] Cache validation complete. Fixed " + fixedEntries + " issues");
        Debug.log("[ItemFilterService] Players without timestamp: " + playersWithoutTimestamp.size());
        Debug.log("[ItemFilterService] Orphaned timestamps: " + orphanedTimestamps.size());
    }
}