package me.hsgamer.extrastorage.storage;

import me.hsgamer.extrastorage.Debug;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quản lý và ghi log các transaction
 */
public class TransactionLogger {
    private static final Map<UUID, StorageTransaction> pendingTransactions = new ConcurrentHashMap<>();

    public static class StorageTransaction {
        public final UUID transactionId;
        public final String itemKey;
        public final long amount;
        public final UUID playerId;
        public final long timestamp;
        public final TransactionType type;
        public boolean completed = false;

        public StorageTransaction(String itemKey, long amount, UUID playerId, TransactionType type) {
            this.transactionId = UUID.randomUUID();
            this.itemKey = itemKey;
            this.amount = amount;
            this.playerId = playerId;
            this.timestamp = System.currentTimeMillis();
            this.type = type;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }

    /**
     * Bắt đầu một transaction mới
     *
     * @param itemKey  key của item
     * @param amount   số lượng
     * @param playerId ID của người chơi
     * @param type     loại transaction
     * @return transaction mới được tạo
     */
    public static StorageTransaction startTransaction(String itemKey, long amount, UUID playerId,
            TransactionType type) {
        StorageTransaction transaction = new StorageTransaction(itemKey, amount, playerId, type);
        pendingTransactions.put(transaction.transactionId, transaction);
        Debug.log("[Transaction] Started: " + transaction.transactionId + " Type: " + type);
        return transaction;
    }

    /**
     * Hoàn thành một transaction
     *
     * @param transactionId ID của transaction
     * @return true nếu transaction được hoàn thành thành công
     */
    public static boolean completeTransaction(UUID transactionId) {
        StorageTransaction transaction = pendingTransactions.remove(transactionId);
        if (transaction != null) {
            Debug.log("[Transaction] Completed: " + transactionId);
            return true;
        }
        return false;
    }

    /**
     * Lấy một transaction đang chờ xử lý
     *
     * @param transactionId ID của transaction
     * @return transaction nếu tìm thấy, null nếu không
     */
    public static StorageTransaction getPendingTransaction(UUID transactionId) {
        return pendingTransactions.get(transactionId);
    }

    /**
     * Lấy danh sách tất cả transaction đang chờ xử lý
     *
     * @return map chứa các transaction
     */
    public static Map<UUID, StorageTransaction> getPendingTransactions() {
        return new ConcurrentHashMap<>(pendingTransactions);
    }

    /**
     * Dọn dẹp các transaction cũ
     */
    public static void cleanupOldTransactions() {
        long currentTime = System.currentTimeMillis();
        long threshold = 30 * 60 * 1000; // 30 phút

        pendingTransactions.entrySet().removeIf(entry -> {
            StorageTransaction transaction = entry.getValue();
            if (currentTime - transaction.getTimestamp() > threshold) {
                Debug.log("[Transaction] Cleaned up old transaction: " + transaction.transactionId);
                return true;
            }
            return false;
        });
    }
}
