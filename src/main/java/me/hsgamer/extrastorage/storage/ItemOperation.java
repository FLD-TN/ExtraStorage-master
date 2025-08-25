package me.hsgamer.extrastorage.storage;

import me.hsgamer.extrastorage.api.storage.Storage;
import me.hsgamer.extrastorage.api.item.Item;
import org.bukkit.inventory.ItemStack;

/**
 * Quản lý các thao tác liên quan đến item một cách atomic
 * Đảm bảo tính nhất quán và có khả năng rollback
 */
public class ItemOperation {
    private static final long MAX_STACK_SIZE = Integer.MAX_VALUE;
    private final Storage storage;
    private final StorageTransaction transaction;
    private final StorageOperation operation;

    public ItemOperation(Storage storage) {
        this.storage = storage;
        this.transaction = new StorageTransaction(storage);
        this.operation = new StorageOperation();
    }

    /**
     * Thêm một số lượng item vào storage
     * 
     * @param item   Item cần thêm
     * @param key    Key của item
     * @param amount Số lượng cần thêm
     * @return true nếu thành công, false nếu thất bại
     */
    public boolean executeItemAdd(ItemStack item, String key, long amount) {
        // Sử dụng một biến tạm để lưu kết quả
        final boolean[] result = { false };

        boolean hasSpace = transaction.executeSpaceCheck(amount, (freeSpace) -> {
            try {
                operation.execute(
                        () -> {
                            validateAmount(amount);
                            storage.add(key, amount);
                        },
                        () -> storage.subtract(key, amount));
                operation.commit();
                result[0] = true;
            } catch (Exception e) {
                operation.rollback();
                throw new StorageException("Không thể thêm vật phẩm vào kho", e);
            }
        });

        return hasSpace && result[0];
    }

    /**
     * Xóa một số lượng item khỏi storage
     * 
     * @param key    Key của item
     * @param amount Số lượng cần xóa
     * @return true nếu thành công, false nếu thất bại
     */
    public boolean executeItemRemove(String key, long amount) {
        try {
            operation.execute(
                    () -> {
                        validateAmount(amount);
                        storage.subtract(key, amount);
                    },
                    () -> storage.add(key, amount));
            operation.commit();
            return true;
        } catch (Exception e) {
            operation.rollback();
            throw new StorageException("Không thể xóa vật phẩm khỏi kho", e);
        }
    }

    /**
     * Đặt số lượng item trong storage
     * 
     * @param key    Key của item
     * @param amount Số lượng mới
     * @return true nếu thành công, false nếu thất bại
     */
    public boolean executeItemSet(String key, long amount) {
        try {
            long currentAmount = storage.getItem(key)
                    .map(Item::getQuantity)
                    .orElse(0L);

            operation.execute(
                    () -> {
                        validateAmount(amount);
                        storage.set(key, amount);
                    },
                    () -> storage.set(key, currentAmount));
            operation.commit();
            return true;
        } catch (Exception e) {
            operation.rollback();
            throw new StorageException("Không thể cập nhật số lượng vật phẩm", e);
        }
    }

    private void validateAmount(long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Số lượng không thể âm");
        }
        if (amount > MAX_STACK_SIZE) {
            throw new IllegalArgumentException("Số lượng vượt quá giới hạn cho phép");
        }
    }
}
