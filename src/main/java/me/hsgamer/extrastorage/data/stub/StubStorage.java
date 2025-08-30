package me.hsgamer.extrastorage.data.stub;

import me.hsgamer.extrastorage.Debug;
import me.hsgamer.extrastorage.ExtraStorage;
import me.hsgamer.extrastorage.api.item.Item;
import me.hsgamer.extrastorage.api.storage.Storage;
import me.hsgamer.extrastorage.configs.Message;
import me.hsgamer.extrastorage.data.Constants;
import me.hsgamer.extrastorage.data.user.ItemImpl;
import me.hsgamer.extrastorage.hooks.placeholder.ESPlaceholder;
import me.hsgamer.extrastorage.manager.StorageStatusManager;
import me.hsgamer.extrastorage.util.Digital;
import me.hsgamer.extrastorage.util.ItemUtil;
import me.hsgamer.extrastorage.util.Utils;
import me.hsgamer.extrastorage.util.ItemFilterService;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

import java.util.*;
import java.util.stream.Collectors;

public class StubStorage implements Storage {
    private volatile boolean cachedStatus;
    private volatile long lastStatusCacheTime = 0;
    private static final long STATUS_CACHE_DURATION = 1000; // 1 giây

    private static final ExtraStorage instance = ExtraStorage.getInstance();
    final StubUser user;

    public StubStorage(StubUser user) {
        this.user = user;
    }

    @Override
    public boolean getStatus() {
        // Lấy từ bộ nhớ thay vì database
        return StorageStatusManager.getInstance().getStatus(user.getUUID());
    }

    @Override
    public void setStatus(boolean status) {
        // Lưu vào bộ nhớ thay vì database
        StorageStatusManager.getInstance().setStatus(user.getUUID(), status);

        Debug.log("[setStatus] Storage status changed to: " + status + " for player: " + user.getName());

        // Clear filter cache khi trạng thái thay đổi
        ItemFilterService.clearCache(user.getUUID());

        // Gửi thông báo cho người chơi
        Player player = user.getPlayer();
        if (player != null && player.isOnline()) {
            player.sendMessage(Message.getMessage("SUCCESS.storage-usage-toggled")
                    .replaceAll(Utils.getRegex("status"),
                            Message.getMessage("STATUS." + (status ? "enabled" : "disabled"))));
        }
    }

    // Cache cho giá trị space để không phải tính toán lại liên tục
    private volatile long cachedSpace = -2; // -2 là giá trị không hợp lệ, cho biết cache chưa được tính
    private volatile long lastCacheTime = 0;
    private static final long CACHE_DURATION = 15000; // Giảm thời gian cache xuống 15 giây để cập nhật nhanh hơn

    @Override
    public long getSpace() {
        long currentTime = System.currentTimeMillis();

        // Sử dụng giá trị cache nếu còn hạn
        if (cachedSpace != -2 && currentTime - lastCacheTime < CACHE_DURATION) {
            return cachedSpace;
        }

        // Luôn ưu tiên quyền không giới hạn
        if (user.hasPermission(Constants.STORAGE_UNLIMITED_PERMISSION)) {
            cachedSpace = -1;
            lastCacheTime = currentTime;
            return cachedSpace;
        }

        Player player = user.getPlayer();
        // Nếu người chơi đang offline, chỉ trả về giá trị đã lưu trong data
        if (player == null || !player.isOnline()) {
            cachedSpace = user.entry.getValue().space;
            lastCacheTime = currentTime;
            return cachedSpace;
        }

        // --- LOGIC KIỂM TRA QUYỀN ĐỘNG ---
        long maxSpaceFromPerms = -1; // Đặt giá trị khởi đầu là -1 (chưa tìm thấy)

        // Lặp qua tất cả các quyền mà người chơi có
        for (PermissionAttachmentInfo info : player.getEffectivePermissions()) {
            String perm = info.getPermission().toLowerCase();

            // Kiểm tra xem quyền có bắt đầu bằng tiền tố chúng ta cần không
            if (perm.startsWith("extrastorage.space.")) {
                String numberPart = perm.substring("extrastorage.space.".length());
                try {
                    long spaceValue = Long.parseLong(numberPart);
                    if (spaceValue > maxSpaceFromPerms) {
                        maxSpaceFromPerms = spaceValue;
                    }
                } catch (NumberFormatException e) {
                    // Bỏ qua nếu perm không phải là số
                }
            }
        }

        // Nếu đã tìm thấy một quyền động hợp lệ, hãy sử dụng nó
        if (maxSpaceFromPerms != -1) {
            cachedSpace = maxSpaceFromPerms;
            lastCacheTime = currentTime;
            return cachedSpace;
        }

        // Nếu không tìm thấy quyền động nào, quay về giá trị mặc định từ data
        cachedSpace = user.entry.getValue().space;
        lastCacheTime = currentTime;
        return cachedSpace;
    }

    @Override
    public void setSpace(long space) {
        user.entry.setValue(u -> u.withSpace(space));
        // Reset cache khi thay đổi space
        cachedSpace = -2;
    }

    @Override
    public void addSpace(long space) {
        user.entry.setValue(u -> u.withSpace(u.space + space));
        // Reset cache khi thay đổi space
        cachedSpace = -2;
    }

    /**
     * Refresh cache của space khi cần thiết
     * Ví dụ: khi người chơi thay đổi quyền
     */
    public void refreshSpaceCache() {
        cachedSpace = -2;
    }

    // Cache cho giá trị usedSpace
    private long cachedUsedSpace = -1; // -1 là giá trị không hợp lệ, cho biết cache chưa được tính
    private long lastUsedSpaceCacheTime = 0;
    private static final long USED_SPACE_CACHE_DURATION = 5000; // Cache trong 5 giây

    @Override
    public long getUsedSpace() {
        long currentTime = System.currentTimeMillis();

        // Sử dụng giá trị cache nếu còn hạn
        if (cachedUsedSpace != -1 && currentTime - lastUsedSpaceCacheTime < USED_SPACE_CACHE_DURATION) {
            return cachedUsedSpace;
        }

        long usedSpace = 0;
        for (Map.Entry<String, ItemImpl> entry : user.entry.getValue().items.entrySet()) {
            if (!ItemUtil.isValidItem(entry.getKey())) {
                continue;
            }
            try {
                usedSpace = Math.addExact(usedSpace, entry.getValue().quantity);
            } catch (ArithmeticException e) {
                cachedUsedSpace = Long.MAX_VALUE;
                lastUsedSpaceCacheTime = currentTime;
                return Long.MAX_VALUE;
            }
        }

        cachedUsedSpace = usedSpace;
        lastUsedSpaceCacheTime = currentTime;
        return usedSpace;
    }

    /**
     * Reset cache của used space
     * Gọi phương thức này sau khi thêm/bớt item
     */
    private void resetUsedSpaceCache() {
        cachedUsedSpace = -1;
    }

    @Override
    public long getFreeSpace() {
        long space = this.getSpace();
        if (space < 0)
            return -1;

        // Cache giá trị usedSpace để tránh tính toán lại
        long usedSpace = this.getUsedSpace();
        return (space - usedSpace);
    }

    @Override
    public boolean isMaxSpace() {
        // Nếu không giới hạn thì luôn có thể thêm vật phẩm
        long space = this.getSpace();
        if (space < 0)
            return false;

        // Kiểm tra trực tiếp thay vì gọi getFreeSpace() để tránh tính toán lại
        return (space - this.getUsedSpace()) < 1;
    }

    @Override
    public double getSpaceAsPercent(boolean order) {
        long space = this.getSpace();
        if (space < 0)
            return -1;
        if (space == 0)
            return 0;

        double percent = (double) getUsedSpace() / space * 100;
        return Digital.getBetween(0.0, 100.0, Digital.formatDouble(order ? percent : (100.0 - percent)));
    }

    @Override
    public boolean canStore(Object key) {
        // 1. Kiểm tra trạng thái từ bộ nhớ
        if (!StorageStatusManager.getInstance().getStatus(user.getUUID())) {
            Debug.log("[canStore] Storage is disabled for player: " + user.getName());
            return false;
        }

        // 2. Chuyển đổi item thành key định danh
        String validKey = ItemUtil.toMaterialKey(key);
        if (validKey == null || validKey.equals(Constants.INVALID)) {
            return false;
        }

        // 3. Kiểm tra điều kiện lọc (phần còn lại giữ nguyên)
        ItemImpl item = user.entry.getValue().items.get(validKey);
        if (item != null) {
            return item.filtered || item.quantity > 0;
        }

        return false;
    }

    @Override
    public Map<String, Item> getFilteredItems() {
        return user.entry.getValue().items.entrySet()
                .stream()
                .filter(entry -> entry.getValue().filtered)
                .map(entry -> new AbstractMap.SimpleEntry<>(entry.getKey(), new StubItem(this, entry.getKey())))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Override
    public Map<String, Item> getUnfilteredItems() {
        return user.entry.getValue().items.entrySet()
                .stream()
                .filter(entry -> !entry.getValue().filtered)
                .map(entry -> new AbstractMap.SimpleEntry<>(entry.getKey(), new StubItem(this, entry.getKey())))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Override
    public Map<String, Item> getItems() {
        return user.entry.getValue().items.keySet()
                .stream()
                .map(item -> new AbstractMap.SimpleEntry<>(item, new StubItem(this, item)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Override
    public Optional<Item> getItem(Object key) {
        if (key == null) {
            return Optional.empty();
        }

        String validKey = ItemUtil.toMaterialKey(key);
        if (validKey == null || validKey.isEmpty() || validKey.equals(Constants.INVALID)) {
            Debug.log("[getItem] Invalid key: " + key);
            return Optional.empty();
        }

        Debug.log("[getItem] Looking for key: " + validKey);

        // 1. Tìm kiếm CHÍNH XÁC key (exact match)
        ItemImpl exactItem = user.entry.getValue().items.get(validKey);
        if (exactItem != null && (exactItem.quantity > 0 || exactItem.filtered)) {
            Debug.log("[getItem] Exact match found: " + validKey);
            return Optional.of(new StubItem(this, validKey));
        }

        // 2. Chuẩn hóa key và tìm kiếm chính xác normalized key
        String normalizedKey = normalizeKey(validKey);
        for (Map.Entry<String, ItemImpl> entry : user.entry.getValue().items.entrySet()) {
            String entryNormalized = normalizeKey(entry.getKey());
            if (entryNormalized.equals(normalizedKey) && (entry.getValue().quantity > 0 || entry.getValue().filtered)) {
                Debug.log("[getItem] Normalized match found: " + entry.getKey() + " for " + validKey);
                return Optional.of(new StubItem(this, entry.getKey()));
            }
        }

        // 3. Đối với plugin items (có chứa ":") - tìm kiếm chính xác namespace
        if (validKey.contains(":")) {
            for (Map.Entry<String, ItemImpl> entry : user.entry.getValue().items.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(validKey)
                        && (entry.getValue().quantity > 0 || entry.getValue().filtered)) {
                    Debug.log("[getItem] Plugin item exact match: " + entry.getKey());
                    return Optional.of(new StubItem(this, entry.getKey()));
                }
            }
        } else {
            // 4. Đối với vanilla items - chỉ tìm kiếm CHÍNH XÁC tên material
            // KHÔNG dùng startsWith() để tránh namespace collision
            for (Map.Entry<String, ItemImpl> entry : user.entry.getValue().items.entrySet()) {
                String entryKey = entry.getKey();
                // Chỉ so sánh với vanilla items không có namespace
                if (!entryKey.contains(":") && entryKey.equalsIgnoreCase(validKey) &&
                        (entry.getValue().quantity > 0 || entry.getValue().filtered)) {
                    Debug.log("[getItem] Vanilla item exact match: " + entry.getKey());
                    return Optional.of(new StubItem(this, entry.getKey()));
                }
            }
        }

        Debug.log("[getItem] No match found for: " + validKey);
        return Optional.empty();
    }

    /**
     * Chuẩn hóa key để so sánh - phiên bản cải tiến để xử lý nhiều định dạng và
     * tình huống
     * Phương pháp này giúp tăng tính tương thích giữa các phiên bản và plugin khác
     * nhau
     *
     * @param key Khóa cần chuẩn hóa
     * @return Khóa đã chuẩn hóa
     */
    private String normalizeKey(String key) {
        if (key == null || key.isEmpty()) {
            return "";
        }

        try {
            key = key.trim();

            // Xử lý đặc biệt cho vanilla materials - không chuẩn hóa nếu không có namespace
            if (!key.contains(":")) {
                return key.toUpperCase(Locale.ROOT);
            }

            if (key.endsWith(":0")) {
                key = key.substring(0, key.length() - 2);
            }

            String[] parts = key.split(":", 2);
            String type = parts[0].toUpperCase(Locale.ROOT);

            if (parts.length == 1) {
                return type;
            }

            // Xử lý các plugin item đặc biệt
            switch (type) {
                case "IA":
                case "ITEMSADDER":
                    return "ITEMSADDER:" + parts[1].toLowerCase();
                case "ORX":
                case "ORAXEN":
                    return "ORAXEN:" + parts[1].toLowerCase();
                case "NEXO":
                    return "NEXO:" + parts[1].toLowerCase();
                case "MMOITEMS":
                case "MI":
                    return "MMOITEMS:" + parts[1].toLowerCase();
                case "SLIMEFUN":
                case "SF":
                    return "SLIMEFUN:" + parts[1].toLowerCase();
                case "MYTHICMOBS":
                case "MM":
                    return "MYTHICMOBS:" + parts[1].toLowerCase();
                default:
                    if (parts[1].equals("0")) {
                        return type;
                    }
                    return type + ":" + parts[1];
            }
        } catch (Exception e) {
            Debug.log("[normalizeKey] Error: " + key + " - " + e.getMessage());
            return key.toUpperCase(Locale.ROOT);
        }
    }

    @Override
    public void addNewItem(Object key) {
        if (key == null)
            return;

        String validKey = ItemUtil.toMaterialKey(key);
        if (validKey == null || validKey.isEmpty() || validKey.equals(Constants.INVALID)) {
            Debug.log("[addNewItem] Invalid key: " + key);
            return;
        }

        // Kiểm tra xem đã có item nào tương tự trong kho hay không
        // Sử dụng phương thức normalizeKey để đảm bảo nhận diện đúng các vật phẩm trùng
        // lặp
        String normalizedKey = normalizeKey(validKey);
        Debug.log("[addNewItem] Original key: " + key + ", Valid key: " + validKey + ", Normalized key: "
                + normalizedKey);

        // Kiểm tra trực tiếp trong map trước
        if (user.entry.getValue().items.containsKey(validKey)) {
            Debug.log("[addNewItem] Direct match found: " + validKey);
            // Cập nhật item hiện có - đặt filtered=true
            user.entry.setValue(u -> u.withItemModifiedIfFound(validKey, i -> i.withFiltered(true)));
            return;
        }

        // Lấy snapshot hiện tại của toàn bộ items để tránh race condition
        Map<String, ItemImpl> currentItems = new HashMap<>(user.entry.getValue().items);

        // Tìm item đã tồn tại với key tương tự
        Optional<Map.Entry<String, ItemImpl>> existingItemEntry = currentItems.entrySet()
                .stream()
                .filter(entry -> normalizeKey(entry.getKey()).equals(normalizedKey))
                .findFirst();

        if (existingItemEntry.isPresent()) {
            // Nếu đã có item, chỉ cần đánh dấu nó là filtered
            Debug.log("[addNewItem] Found existing item with normalized key: " + existingItemEntry.get().getKey());
            String existingKey = existingItemEntry.get().getKey();
            user.entry.setValue(u -> u.withItemModifiedIfFound(existingKey, i -> i.withFiltered(true)));
        } else {
            // Nếu chưa có, thêm mới với key đã được chuẩn hóa
            Debug.log("[addNewItem] Adding new item with key: " + validKey);

            // Xóa item trước đó nếu có (để đảm bảo không bị xung đột)
            user.entry.setValue(u -> {
                Map<String, ItemImpl> items = new HashMap<>(u.items);
                items.remove(validKey); // Xóa item cũ nếu có

                // Thêm item mới với filtered=true và quantity=0
                items.put(validKey, ItemImpl.EMPTY.withFiltered(true).withQuantity(0));

                Debug.log("[addNewItem] Added fresh item with key: " + validKey);
                return u.withItems(items);
            });
        }

        // Reset cache khi thêm item mới
        resetUsedSpaceCache();
        // Clear placeholder cache khi có thay đổi
        Bukkit.getScheduler().runTask(instance, () -> {
            try {
                ESPlaceholder.clearCacheForPlayer(user.getUUID());
            } catch (Exception e) {
                // Ignore if PlaceholderAPI is not loaded
            }
        });
    }

    @Override
    public void unfilter(Object key) {
        if (key == null)
            return;

        String validKey = ItemUtil.toMaterialKey(key);
        if (validKey == null || validKey.isEmpty() || validKey.equals(Constants.INVALID)) {
            Debug.log("[unfilter] Invalid key: " + key);
            return;
        }

        // Mở rộng phạm vi tìm kiếm để giải quyết vấn đề định dạng không nhất quán
        String normalizedKey = normalizeKey(validKey);
        String bareKey = validKey.split(":", 2)[0].toUpperCase(Locale.ROOT);

        Debug.log("[unfilter] Original key: " + key);
        Debug.log("[unfilter] Valid key: " + validKey);
        Debug.log("[unfilter] Normalized key: " + normalizedKey);
        Debug.log("[unfilter] Bare key: " + bareKey);

        // Chiến lược tìm kiếm:
        // 1. Tìm chính xác với key đã chuẩn hóa
        Optional<Map.Entry<String, ItemImpl>> exactMatch = user.entry.getValue().items.entrySet()
                .stream()
                .filter(entry -> normalizeKey(entry.getKey()).equals(normalizedKey))
                .findFirst();

        if (exactMatch.isPresent()) {
            // Tìm thấy chính xác - xóa hoàn toàn item thay vì chỉ đặt filtered = false
            Debug.log("[unfilter] Found exact match, removing completely: " + exactMatch.get().getKey());
            String actualKey = exactMatch.get().getKey();
            user.entry.setValue(u -> {
                Map<String, ItemImpl> items = new HashMap<>(u.items);
                items.remove(actualKey);
                Debug.log("[unfilter] Completely removed item with key: " + actualKey);
                return u.withItems(items);
            });
            return;
        }

        // 2. Tìm với phần material chính (không kèm data)
        Optional<Map.Entry<String, ItemImpl>> baseMatch = user.entry.getValue().items.entrySet()
                .stream()
                .filter(entry -> entry.getKey().startsWith(bareKey))
                .findFirst();

        if (baseMatch.isPresent()) {
            // Tìm thấy với phần material cơ bản - xóa hoàn toàn item
            Debug.log("[unfilter] Found base match, removing completely: " + baseMatch.get().getKey());
            String similarKey = baseMatch.get().getKey();
            user.entry.setValue(u -> {
                Map<String, ItemImpl> items = new HashMap<>(u.items);
                items.remove(similarKey);
                Debug.log("[unfilter] Completely removed item with key: " + similarKey);
                return u.withItems(items);
            });
            return;
        }

        // 3. Thử với key ban đầu nếu không tìm thấy - xóa hoàn toàn item
        Debug.log("[unfilter] Using original key as last resort: " + validKey);
        user.entry.setValue(u -> {
            Map<String, ItemImpl> items = new HashMap<>(u.items);
            items.remove(validKey);
            Debug.log("[unfilter] Attempted to remove item with key: " + validKey);
            return u.withItems(items);
        });

        // Reset cache khi xóa item
        resetUsedSpaceCache();
        // Clear placeholder cache khi có thay đổi
        Bukkit.getScheduler().runTask(instance, () -> {
            try {
                ESPlaceholder.clearCacheForPlayer(user.getUUID());
            } catch (Exception e) {
                // Ignore if PlaceholderAPI is not loaded
            }
        });
    }

    @Override
    public void add(Object key, long quantity) {
        String validKey = ItemUtil.toMaterialKey(key);
        user.entry.setValue(u -> u.withItemModifiedIfFound(validKey, i -> {
            // Nếu item không tồn tại hoặc đã bị xóa, tạo mới với filtered=true
            if (i == null) {
                return ItemImpl.EMPTY.withQuantity(quantity).withFiltered(true);
            }
            return i.withQuantity(i.quantity + quantity);
        }));

        // Reset cache khi thêm item
        resetUsedSpaceCache();
        Bukkit.getScheduler().runTask(instance, () -> {
            try {
                ESPlaceholder.clearCacheForPlayer(user.getUUID());
            } catch (Exception e) {
                // Ignore if PlaceholderAPI is not loaded
            }
        });
    }

    @Override
    public void subtract(Object key, long quantity) {
        String validKey = ItemUtil.toMaterialKey(key);
        user.entry.setValue(u -> u.withItemModifiedIfFound(validKey, i -> {
            long newQuantity = i.quantity - quantity;

            // Nếu số lượng <= 0 VÀ filter đã tắt, thì xóa item hoàn toàn
            if (newQuantity <= 0 && !i.filtered) {
                return null; // Xóa item
            }

            // Nếu số lượng < 0 nhưng filter vẫn bật, đặt về 0
            if (newQuantity < 0) {
                newQuantity = 0;
            }

            return i.withQuantity(newQuantity);
        }));

        // Reset cache khi bớt item
        resetUsedSpaceCache();
        // Clear placeholder cache khi có thay đổi
        Bukkit.getScheduler().runTask(instance, () -> {
            try {
                ESPlaceholder.clearCacheForPlayer(user.getUUID());
            } catch (Exception e) {
                // Ignore if PlaceholderAPI is not loaded
            }
        });
    }

    @Override
    public void set(Object key, long quantity) {
        String validKey = ItemUtil.toMaterialKey(key);
        user.entry.setValue(u -> u.withItemModifiedIfFound(validKey, i -> {
            if (quantity < 0)
                return null;
            return i.withQuantity(quantity);
        }));
        // Clear placeholder cache khi có thay đổi
        Bukkit.getScheduler().runTask(instance, () -> {
            try {
                ESPlaceholder.clearCacheForPlayer(user.getUUID());
            } catch (Exception e) {
                // Ignore if PlaceholderAPI is not loaded
            }
        });
    }

    @Override
    public void reset(Object key) {
        if (key != null) {
            String validKey = ItemUtil.toMaterialKey(key);
            // Completely remove the item if it has a valid key
            if (validKey != null && !validKey.isEmpty() && !validKey.equals(Constants.INVALID)) {
                user.entry.setValue(u -> {
                    Map<String, ItemImpl> items = new HashMap<>(u.items);
                    // Remove the item completely instead of just setting quantity to 0
                    items.remove(validKey);
                    Debug.log("[reset] Completely removed item with key: " + validKey);
                    return u.withItems(items);
                });
            } else {
                this.set(key, 0);
            }
        } else {
            user.entry.setValue(u -> {
                Map<String, ItemImpl> items = new HashMap<>(user.entry.getValue().items);
                Iterator<Map.Entry<String, ItemImpl>> iterator = items.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<String, ItemImpl> entry = iterator.next();
                    if (entry.getValue().quantity < 1 && !entry.getValue().filtered) {
                        iterator.remove();
                        Debug.log("[reset-all] Removed item: " + entry.getKey());
                    } else {
                        entry.setValue(entry.getValue().withQuantity(0));
                        Debug.log("[reset-all] Reset quantity for item: " + entry.getKey());
                    }
                }
                return u.withItems(items);
            });
        }
        // Clear any cached lookups
        resetUsedSpaceCache();
        // Clear placeholder cache khi có thay đổi
        Bukkit.getScheduler().runTask(instance, () -> {
            try {
                ESPlaceholder.clearCacheForPlayer(user.getUUID());
            } catch (Exception e) {
                // Ignore if PlaceholderAPI is not loaded
            }
        });

    }

    // Thêm phương thức này trong class StubStorage
    public void refreshStatusCache() {
        // Reset cache bằng cách đánh dấu là dirty
        // Hoặc reset các giá trị cache cụ thể nếu cần
    }
}
