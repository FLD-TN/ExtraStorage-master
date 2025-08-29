package me.hsgamer.extrastorage.storage;

import me.hsgamer.extrastorage.Debug;
import me.hsgamer.extrastorage.api.storage.Storage;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

// Removed incorrect import for Iterator

/**
 * Quản lý các hoạt động an toàn cho storage
 */
public class StorageSafetyManager {
    // ⚡ Sử dụng ConcurrentHashMap để an toàn thread và tránh memory leak
    private static final Map<UUID, Lock> storageLocks = new ConcurrentHashMap<>();
    // Thêm map theo dõi thời gian khóa để phát hiện deadlock
    private static final Map<UUID, Long> lockTimestamps = new ConcurrentHashMap<>();
    private static final long LOCK_TIMEOUT_MS = 30000; // 30 giây
    private static final int MAX_CONCURRENT_OPERATIONS = 100;

    // ⚡ Scheduled cleanup service
    private static final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "StorageSafetyManager-Cleanup");
        thread.setDaemon(true);
        return thread;
    });

    static {
        // Dọn dẹp mỗi 5 phút
        cleanupExecutor.scheduleAtFixedRate(
                StorageSafetyManager::cleanupStaleLocks,
                5, 5, TimeUnit.MINUTES);
    }

    /**
     * Thực hiện một operation an toàn trên storage
     */
    public static boolean safeStorageOperation(Storage storage, Player player, String itemKey,
            long amount, TransactionType type, StorageOperation operation) {

        UUID storageId = storage.getUniqueId();
        Lock lock = storageLocks.computeIfAbsent(storageId, k -> new ReentrantLock());

        // Kiểm tra deadlock - nếu lock đã tồn tại quá lâu, có thể bị deadlock
        Long lockTime = lockTimestamps.get(storageId);
        if (lockTime != null && System.currentTimeMillis() - lockTime > LOCK_TIMEOUT_MS) {
            Debug.log("[Safety] Possible deadlock detected for storage: " + storageId);
            lockTimestamps.remove(storageId);
            storageLocks.remove(storageId);
            lock = new ReentrantLock();
            storageLocks.put(storageId, lock);
        }

        if (!lock.tryLock()) {
            Debug.log("[Safety] Storage is locked: " + storageId);
            return false;
        }

        // Ghi nhận thời điểm lock
        lockTimestamps.put(storageId, System.currentTimeMillis());

        try {
            // Kiểm tra giới hạn đồng thời
            if (storageLocks.size() > MAX_CONCURRENT_OPERATIONS) {
                Debug.log("[Safety] Too many concurrent operations");
                return false;
            }

            // Bắt đầu transaction
            TransactionLogger.StorageTransaction transaction = TransactionLogger.startTransaction(itemKey, amount,
                    player.getUniqueId(), type);

            try {
                // Thực hiện operation
                boolean result = operation.execute();

                if (result) {
                    TransactionLogger.completeTransaction(transaction.transactionId);
                } else {
                    // Nếu thất bại, ghi nhận lại để debug
                    Debug.log("[Safety] Operation failed for item: " + itemKey + " amount: " + amount);
                }

                return result;
            } catch (Exception e) {
                Debug.log("[Safety] Operation failed: " + e.getMessage());
                return false;
            }
        } finally {
            try {
                lock.unlock();
                // Xóa timestamp khi unlock
                lockTimestamps.remove(storageId);

                // Không cần cleanup liên tục, điều này được thực hiện định kỳ
            } catch (Exception e) {
                Debug.log("[Safety] Error unlocking: " + e.getMessage());
            }
        }
    }

    /**
     * Kiểm tra giới hạn và tính hợp lệ của số lượng item
     */
    public static boolean validateItemAmount(long amount) {
        return amount > 0 && amount <= Integer.MAX_VALUE;
    }

    /**
     * Kiểm tra item có hợp lệ không
     */
    public static boolean validateItem(ItemStack item) {
        return item != null && item.getType().isItem() && item.getAmount() > 0;
    }

    /**
     * Interface cho các storage operation
     */
    @FunctionalInterface
    public interface StorageOperation {
        boolean execute();
    }

    /**
     * Clean up các transaction cũ
     * 
     * @return số lượng transaction và lock đã dọn dẹp
     */
    public static int cleanupTransactions() {
        TransactionLogger.cleanupOldTransactions();
        Debug.log("[Safety] Cleaned up expired transactions");

        // Kiểm tra và xóa lock bị treo
        int staleLocks = cleanupStaleLocks();
        if (staleLocks > 0) {
            Debug.log("[Safety] Cleaned up " + staleLocks + " stale locks");
        }

        return staleLocks;
    }

    /**
     * Xóa các lock bị treo quá lâu
     */
    public static int cleanupStaleLocks() {
        int count = 0;

        // Lấy thời gian hiện tại một lần để tránh gọi System.currentTimeMillis nhiều
        // lần
        long currentTime = System.currentTimeMillis();

        // Xử lý deadlock - các lock tồn tại quá lâu
        for (Map.Entry<UUID, Long> entry : lockTimestamps.entrySet()) {
            if (currentTime - entry.getValue() > LOCK_TIMEOUT_MS) {
                UUID id = entry.getKey();
                Debug.log("[Safety] Detected stale lock for storage: " + id);
                storageLocks.remove(id);
                lockTimestamps.remove(id);
                count++;
            }
        }

        // Xóa các lock không sử dụng nữa
        for (Map.Entry<UUID, Lock> entry : storageLocks.entrySet()) {
            Lock lock = entry.getValue();
            if (lock instanceof ReentrantLock) {
                ReentrantLock rLock = (ReentrantLock) lock;
                if (!rLock.hasQueuedThreads() && rLock.getHoldCount() == 0) {
                    UUID id = entry.getKey();
                    storageLocks.remove(id);
                    lockTimestamps.remove(id);
                    count++;
                }
            }
        }

        // Đảm bảo không có timestamp mà không có lock tương ứng
        lockTimestamps.keySet().removeIf(id -> !storageLocks.containsKey(id));

        return count;
    }
}
