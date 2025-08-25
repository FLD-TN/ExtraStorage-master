package me.hsgamer.extrastorage.storage;

import me.hsgamer.extrastorage.Debug;
import me.hsgamer.extrastorage.api.storage.Storage;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Quản lý các hoạt động an toàn cho storage
 */
public class StorageSafetyManager {
    private static final Map<UUID, Lock> storageLocks = new ConcurrentHashMap<>();
    private static final int MAX_CONCURRENT_OPERATIONS = 100;

    /**
     * Thực hiện một operation an toàn trên storage
     */
    public static boolean safeStorageOperation(Storage storage, Player player, String itemKey,
            long amount, TransactionType type, StorageOperation operation) {
        Lock lock = storageLocks.computeIfAbsent(storage.getUniqueId(), k -> new ReentrantLock());

        if (!lock.tryLock()) {
            Debug.log("[Safety] Storage is locked: " + storage.getUniqueId());
            return false;
        }

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
                }

                return result;
            } catch (Exception e) {
                Debug.log("[Safety] Operation failed: " + e.getMessage());
                return false;
            }
        } catch (RuntimeException e) {
            Debug.log("[Safety] Critical error in storage operation: " + e.getMessage());
            throw e;
        } finally {
            try {
                if (storageLocks.containsKey(storage.getUniqueId())) {
                    lock.unlock();
                }
            } finally {
                storageLocks.remove(storage.getUniqueId());
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
     * 
     * @return số lượng lock đã xóa
     */
    private static int cleanupStaleLocks() {
        int count = 0;
        for (Map.Entry<UUID, Lock> entry : storageLocks.entrySet()) {
            Lock lock = entry.getValue();
            if (lock instanceof ReentrantLock) {
                ReentrantLock rLock = (ReentrantLock) lock;
                if (rLock.hasQueuedThreads() && rLock.getQueueLength() > 0) {
                    // Nếu có thread đang đợi quá lâu, xóa lock để tránh deadlock
                    storageLocks.remove(entry.getKey());
                    count++;
                }
            }
        }
        return count;
    }
}
