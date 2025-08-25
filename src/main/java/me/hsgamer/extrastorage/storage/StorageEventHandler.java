package me.hsgamer.extrastorage.storage;

import me.hsgamer.extrastorage.Debug;
import me.hsgamer.extrastorage.api.storage.Storage;
import me.hsgamer.extrastorage.configs.MaterialTypeConfig;
import me.hsgamer.extrastorage.util.ItemUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Xử lý các sự kiện liên quan đến storage
 */
public class StorageEventHandler {
    private final Storage storage;
    private final StorageOperation operation;
    private final MaterialTypeConfig materialTypeConfig;
    private final ItemOperation itemOperation;

    public StorageEventHandler(Storage storage, MaterialTypeConfig materialTypeConfig) {
        this.storage = storage;
        this.operation = new StorageOperation();
        this.materialTypeConfig = materialTypeConfig;
        this.itemOperation = new ItemOperation(storage);
    }

    /**
     * Xử lý thêm item vào storage
     * 
     * @param item   Item cần thêm
     * @param player Người chơi thực hiện
     * @throws StorageException nếu có lỗi xảy ra
     */
    public void handleItemAdd(ItemStack item, Player player) throws StorageException {
        try {
            // Kiểm tra quyền
            if (!PermissionValidator.canAddItems(player, storage)) {
                throw new StorageException("Bạn không có quyền thêm vật phẩm vào kho này");
            }

            // Validate item
            validateItem(item);

            // Kiểm tra trạng thái storage
            validateStorageState();

            // Thực hiện thêm item
            String itemKey = ItemUtil.toMaterialKey(item);
            int amount = item.getAmount();

            if (!itemOperation.executeItemAdd(item, itemKey, amount)) {
                throw new StorageException("Không thể thêm vật phẩm vào kho");
            }

            // Log hành động
            Debug.log("[Storage] Player " + player.getName() + " added " + amount + " " + itemKey);

        } catch (Exception e) {
            throw new StorageException(e.getMessage(), e);
        }
    }

    private void validateItem(ItemStack item) throws StorageException {
        // Kiểm tra null và AIR
        if (item == null || item.getType() == Material.AIR) {
            throw new StorageException("Vật phẩm không hợp lệ");
        }

        // Kiểm tra blacklist
        if (materialTypeConfig.isBlacklisted(item.getType())) {
            throw new StorageException("Vật phẩm này nằm trong danh sách cấm");
        }

        // Kiểm tra số lượng
        if (item.getAmount() <= 0) {
            throw new StorageException("Số lượng vật phẩm không hợp lệ");
        }

        // Kiểm tra metadata
        validateItemMetadata(item);
    }

    private void validateItemMetadata(ItemStack item) throws StorageException {
        if (!item.hasItemMeta()) {
            return;
        }

        ItemMeta meta = item.getItemMeta();

        // Kiểm tra custom model data
        if (meta.hasCustomModelData()) {
            throw new StorageException("Không chấp nhận vật phẩm có custom model data");
        }

        // Kiểm tra enchantments
        if (!item.getEnchantments().isEmpty()) {
            throw new StorageException("Không chấp nhận vật phẩm có enchant");
        }
    }

    private void validateStorageState() throws StorageException {
        if (!storage.getStatus()) {
            throw new StorageException("Kho đang bị tắt");
        }

        if (storage.isMaxSpace()) {
            throw new StorageException("Kho đã đầy");
        }
    }

    /**
     * Xử lý rút item ra khỏi storage
     * 
     * @param itemKey Key của item
     * @param amount  Số lượng cần rút
     * @param player  Người chơi thực hiện
     * @throws StorageException nếu có lỗi xảy ra
     */
    public void handleItemWithdraw(String itemKey, long amount, Player player) throws StorageException {
        try {
            // Kiểm tra quyền
            if (!PermissionValidator.canModifyStorage(player, storage)) {
                throw new StorageException("Bạn không có quyền rút vật phẩm từ kho này");
            }

            // Thực hiện rút item
            if (!itemOperation.executeItemRemove(itemKey, amount)) {
                throw new StorageException("Không thể rút vật phẩm từ kho");
            }

            // Log hành động
            Debug.log("[Storage] Player " + player.getName() + " withdrew " + amount + " " + itemKey);

        } catch (Exception e) {
            throw new StorageException(e.getMessage(), e);
        }
    }
}
