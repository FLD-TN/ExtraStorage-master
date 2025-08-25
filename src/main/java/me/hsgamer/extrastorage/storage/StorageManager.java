package me.hsgamer.extrastorage.storage;

import me.hsgamer.extrastorage.api.storage.Storage;
import me.hsgamer.extrastorage.api.item.Item;
import me.hsgamer.extrastorage.util.ItemUtil;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Quản lý việc thêm và lấy vật phẩm từ kho một cách an toàn.
 * Class này đảm bảo:
 * - Thread-safe khi nhiều người cùng truy cập
 * - Kiểm tra và xử lý metadata của vật phẩm
 * - Xử lý giới hạn số lượng vật phẩm
 * - Cache thông tin để tăng hiệu năng
 */

public class StorageManager {
    // Giới hạn số lượng tối đa cho mỗi loại vật phẩm
    private static final long MAX_STACK_SIZE = Integer.MAX_VALUE;

    // Storage chính để lưu trữ vật phẩm
    private final Storage storage;

    // Transaction manager để đảm bảo tính atomic
    private final StorageTransaction transaction;

    // Cache để lưu trữ thông tin về các vật phẩm
    private final ConcurrentHashMap<String, Boolean> existingItems = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> itemQuantityCache = new ConcurrentHashMap<>();

    // Cache cho tổng số lượng item đã sử dụng
    private final AtomicLong totalUsedSpace = new AtomicLong(0);

    // Thời gian cache ngắn hơn để tránh inconsistency
    private static final long CACHE_DURATION = 5000; // 5 giây
    private volatile long lastCacheRefresh = 0;

    // Lock để đồng bộ hóa các thao tác quan trọng
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public StorageManager(Storage storage) {
        this.storage = storage;
        this.transaction = new StorageTransaction(storage);
        refreshCache(); // Khởi tạo cache
    }

    /**
     * Thêm vật phẩm vào kho một cách an toàn
     * Sử dụng StorageTransaction để đảm bảo tính atomic
     * 
     * @param itemToAdd Vật phẩm cần thêm vào kho
     * @return true nếu thêm thành công, false nếu thất bại
     */
    public boolean addItem(ItemStack itemToAdd) {
        if (itemToAdd == null || itemToAdd.getType() == Material.AIR) {
            return false;
        }

        // Tạo một StorageOperation để có thể rollback nếu cần
        StorageOperation operation = new StorageOperation();
        try {
            // Tạo bản sao và xóa metadata không cần thiết
            ItemStack cleanItem = cleanItemMetadata(itemToAdd.clone());
            String itemKey = ItemUtil.toMaterialKey(cleanItem);
            int amount = cleanItem.getAmount();

            boolean success = transaction.executeSpaceCheck(amount, (freeSpace) -> {
                // Kiểm tra vật phẩm đã tồn tại
                Optional<Item> existingItem = storage.getItem(itemKey);

                if (existingItem.isPresent()) {
                    Item item = existingItem.get();
                    long currentAmount = item.getQuantity();

                    // Kiểm tra overflow
                    try {
                        long newAmount = Math.addExact(currentAmount, amount);
                        if (newAmount > MAX_STACK_SIZE) {
                            throw new StorageException("Exceeded max stack size");
                        }

                        // Thêm item với khả năng rollback
                        operation.execute(
                                () -> {
                                    item.add(amount);
                                    updateItemQuantityCache(itemKey, newAmount);
                                },
                                () -> {
                                    item.add(-amount);
                                    updateItemQuantityCache(itemKey, currentAmount);
                                });
                    } catch (ArithmeticException e) {
                        throw new StorageException("Item quantity would overflow");
                    }
                } else {
                    // Thêm item mới với khả năng rollback
                    operation.execute(
                            () -> {
                                storage.addNewItem(cleanItem);
                                existingItems.put(itemKey, true);
                                updateItemQuantityCache(itemKey, amount);
                            },
                            () -> {
                                storage.reset(itemKey);
                                existingItems.remove(itemKey);
                                updateItemQuantityCache(itemKey, 0);
                            });
                }
            });
            if (success) {
                operation.commit(); // Commit các thay đổi nếu mọi thứ thành công
            }
            return success;
        } catch (StorageException e) {
            // Tự động rollback nếu có lỗi
            operation.rollback();
            return false;
        }
    }

    /**
     * Kiểm tra xem có thể thêm vật phẩm vào kho không
     * Sử dụng cache và transaction để tối ưu performance
     * 
     * @param item Vật phẩm cần kiểm tra
     * @return true nếu có thể thêm, false nếu không thể
     */
    public boolean canAddItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }

        return transaction.executeRead(() -> {
            // Kiểm tra kho đã đầy chưa
            if (storage.isMaxSpace()) {
                return false;
            }

            String itemKey = ItemUtil.toMaterialKey(item);
            int amount = item.getAmount();

            // Kiểm tra từ cache trước
            Long currentQuantity = itemQuantityCache.get(itemKey);
            if (currentQuantity != null) {
                try {
                    long newQuantity = Math.addExact(currentQuantity, amount);
                    return newQuantity <= MAX_STACK_SIZE;
                } catch (ArithmeticException e) {
                    return false;
                }
            }

            // Nếu không có trong cache, kiểm tra từ storage
            Optional<Item> existingItem = storage.getItem(itemKey);
            if (existingItem.isPresent()) {
                Item storedItem = existingItem.get();
                try {
                    long newQuantity = Math.addExact(storedItem.getQuantity(), amount);
                    return newQuantity <= MAX_STACK_SIZE;
                } catch (ArithmeticException e) {
                    return false;
                }
            }

            // Vật phẩm mới, kiểm tra không gian trống
            return storage.getFreeSpace() == -1 || storage.getFreeSpace() >= amount;
        });
    }

    /**
     * Xóa các metadata không cần thiết của vật phẩm
     * Chỉ giữ lại tên và lore để tiết kiệm bộ nhớ
     * 
     * @param item Vật phẩm cần xử lý
     * @return Vật phẩm đã được làm sạch metadata
     */
    private ItemStack cleanItemMetadata(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return item;

        // Tạo một bản sao để không ảnh hưởng đến item gốc
        ItemStack cleanItem = item.clone();
        ItemMeta newMeta = cleanItem.getItemMeta();

        // Chỉ giữ lại tên và lore
        if (meta.hasDisplayName()) {
            newMeta.setDisplayName(meta.getDisplayName());
        }
        if (meta.hasLore()) {
            newMeta.setLore(meta.getLore());
        }

        // Cập nhật metadata mới
        cleanItem.setItemMeta(newMeta);
        return cleanItem;
    }

    /**
     * Cập nhật cache cho một vật phẩm
     */
    private void updateItemQuantityCache(String itemKey, long newQuantity) {
        if (newQuantity > 0) {
            itemQuantityCache.put(itemKey, newQuantity);
        } else {
            itemQuantityCache.remove(itemKey);
        }
        recalculateTotalUsedSpace();
    }

    /**
     * Tính toán lại tổng số lượng vật phẩm đã sử dụng
     */
    private void recalculateTotalUsedSpace() {
        long total = itemQuantityCache.values().stream()
                .mapToLong(Long::longValue)
                .sum();
        totalUsedSpace.set(total);
    }

    /**
     * Làm mới toàn bộ cache
     */
    private void refreshCache() {
        lastCacheRefresh = System.currentTimeMillis();
        existingItems.clear();
        itemQuantityCache.clear();

        // Cập nhật cache từ storage
        storage.getItems().forEach((key, item) -> {
            existingItems.put(key, true);
            itemQuantityCache.put(key, item.getQuantity());
        });

        recalculateTotalUsedSpace();
    }

    /**
     * Kiểm tra và refresh cache nếu cần
     */
    private void checkAndRefreshCache() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastCacheRefresh > CACHE_DURATION) {
            refreshCache();
        }
    }

    /**
     * Kiểm tra xem vật phẩm đã tồn tại trong kho chưa
     * Sử dụng cache để tăng tốc độ truy vấn
     * 
     * @param itemKey Key của vật phẩm cần kiểm tra
     * @return true nếu vật phẩm đã tồn tại, false nếu chưa
     */
    public boolean hasItem(String itemKey) {
        checkAndRefreshCache();
        return transaction.executeRead(() -> {
            return existingItems.containsKey(itemKey) &&
                    storage.getItem(itemKey).isPresent();
        });
    }

    /**
     * Xóa và làm mới toàn bộ cache
     * Gọi phương thức này khi cần đồng bộ lại với storage
     */
    public void clearCache() {
        refreshCache();
    }

    /**
     * Xóa hoàn toàn một vật phẩm khỏi kho
     * Sử dụng writeLock để đảm bảo thread-safe
     * 
     * @param itemKey Key của vật phẩm cần xóa
     */
    public void removeItem(String itemKey) {
        lock.writeLock().lock();
        try {
            // Sử dụng reset để xóa hoàn toàn vật phẩm
            storage.reset(itemKey);
            // Xóa khỏi cache
            existingItems.remove(itemKey);
        } finally {
            // Luôn đảm bảo unlock sau khi thực hiện xong
            lock.writeLock().unlock();
        }
    }

    /**
     * Lấy số lượng của một vật phẩm trong kho
     * Sử dụng readLock vì chỉ đọc dữ liệu
     * 
     * @param itemKey Key của vật phẩm cần kiểm tra
     * @return Số lượng vật phẩm, hoặc 0 nếu không tồn tại
     */
    public long getItemQuantity(String itemKey) {
        lock.readLock().lock();
        try {
            Optional<Item> item = storage.getItem(itemKey);
            return item.map(Item::getQuantity).orElse(0L);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Cập nhật số lượng một vật phẩm trong kho
     * Sử dụng writeLock để đảm bảo thread-safe
     * 
     * @param itemKey  Key của vật phẩm cần cập nhật
     * @param quantity Số lượng mới của vật phẩm
     * @return true nếu cập nhật thành công, false nếu vật phẩm không tồn tại
     */
    public boolean setItemQuantity(String itemKey, long quantity) {
        if (quantity < 0) {
            return false;
        }

        lock.writeLock().lock();
        try {
            if (!hasItem(itemKey)) {
                return false;
            }

            if (quantity == 0) {
                // Nếu số lượng = 0 thì xóa vật phẩm
                storage.reset(itemKey);
                existingItems.remove(itemKey);
            } else {
                // Cập nhật số lượng mới
                storage.set(itemKey, quantity);
            }
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Lọc hoặc bỏ lọc một vật phẩm trong kho
     * Vật phẩm được lọc sẽ không bị xóa khi số lượng = 0
     * 
     * @param itemKey  Key của vật phẩm cần thay đổi
     * @param filtered true để lọc, false để bỏ lọc
     * @return true nếu thay đổi thành công, false nếu vật phẩm không tồn tại
     */
    public boolean setItemFiltered(String itemKey, boolean filtered) {
        lock.writeLock().lock();
        try {
            if (!hasItem(itemKey)) {
                return false;
            }

            if (filtered) {
                storage.addNewItem(itemKey); // addNewItem sẽ set filtered = true
            } else {
                storage.unfilter(itemKey);
            }
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }
}
