package me.hsgamer.extrastorage.storage;

import me.hsgamer.extrastorage.api.storage.Storage;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Quản lý giao dịch trong storage một cách an toàn
 * Đảm bảo tính atomic cho các thao tác liên quan đến space và item
 */
public class StorageTransaction {
    private final Storage storage;
    private final ReadWriteLock lock;

    public StorageTransaction(Storage storage) {
        this.storage = storage;
        this.lock = new ReentrantReadWriteLock();
    }

    /**
     * Thực hiện kiểm tra space và operation trong một transaction
     * 
     * @param requiredSpace Không gian cần thiết
     * @param operation     Operation cần thực hiện nếu đủ space
     * @return true nếu thành công, false nếu không đủ space
     */
    public boolean executeSpaceCheck(long requiredSpace, Consumer<Long> operation) {
        lock.writeLock().lock();
        try {
            long freeSpace = storage.getFreeSpace();
            if (freeSpace != -1 && freeSpace < requiredSpace) {
                return false;
            }
            operation.accept(freeSpace);
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Thực hiện một read operation với lock
     */
    public <T> T executeRead(Supplier<T> operation) {
        lock.readLock().lock();
        try {
            return operation.get();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Thực hiện một write operation với lock
     */
    public void executeWrite(Runnable operation) {
        lock.writeLock().lock();
        try {
            operation.run();
        } finally {
            lock.writeLock().unlock();
        }
    }
}
