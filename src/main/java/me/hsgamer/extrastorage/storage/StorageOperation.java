package me.hsgamer.extrastorage.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Quản lý các thao tác trên storage với khả năng rollback
 * Đảm bảo tính nhất quán khi có lỗi xảy ra
 */
public class StorageOperation {
    private final List<Runnable> rollbackActions = new ArrayList<>();
    private boolean isCommitted = false;

    /**
     * Thực hiện một action với khả năng rollback
     * 
     * @param action   Action cần thực hiện
     * @param rollback Action để rollback nếu có lỗi
     * @throws StorageException Nếu có lỗi xảy ra
     */
    public void execute(Runnable action, Runnable rollback) throws StorageException {
        try {
            action.run();
            rollbackActions.add(0, rollback); // Thêm vào đầu list để rollback theo thứ tự ngược lại
        } catch (Exception e) {
            rollback();
            throw new StorageException("Failed to execute storage operation", e);
        }
    }

    /**
     * Thực hiện một action trả về kết quả với khả năng rollback
     * 
     * @param action   Action cần thực hiện
     * @param rollback Action để rollback nếu có lỗi
     * @return Kết quả của action
     * @throws StorageException Nếu có lỗi xảy ra
     */
    public <T> T execute(Supplier<T> action, Runnable rollback) throws StorageException {
        try {
            T result = action.get();
            rollbackActions.add(0, rollback);
            return result;
        } catch (Exception e) {
            rollback();
            throw new StorageException("Failed to execute storage operation", e);
        }
    }

    /**
     * Rollback tất cả các thay đổi theo thứ tự ngược lại
     */
    public void rollback() {
        if (isCommitted) {
            return;
        }
        for (Runnable rollback : rollbackActions) {
            try {
                rollback.run();
            } catch (Exception e) {
                // Log rollback error but continue with other rollbacks
            }
        }
        rollbackActions.clear();
    }

    /**
     * Commit các thay đổi, ngăn không cho rollback nữa
     */
    public void commit() {
        isCommitted = true;
        rollbackActions.clear();
    }
}

/**
 * Exception riêng cho các lỗi liên quan đến storage
 */
class StorageException extends RuntimeException {
    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
