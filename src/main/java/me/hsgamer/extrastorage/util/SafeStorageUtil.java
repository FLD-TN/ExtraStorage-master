package me.hsgamer.extrastorage.util;

import me.hsgamer.extrastorage.api.storage.Storage;
import me.hsgamer.extrastorage.api.item.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Material;

import java.util.Optional;
import java.util.Set;
import java.util.HashSet;

public class SafeStorageUtil {
    // Singleton để tránh tạo nhiều instance
    private static SafeStorageUtil instance;

    // Set các material types không được phép lưu metadata
    private static final Set<Material> STRIP_META_MATERIALS = new HashSet<>();

    // Giới hạn số lượng item tối đa
    private static final long MAX_ITEM_AMOUNT = Long.MAX_VALUE;

    static {
        // Thêm các material cần strip metadata
        STRIP_META_MATERIALS.add(Material.WRITTEN_BOOK);
        STRIP_META_MATERIALS.add(Material.FILLED_MAP);
        // Thêm các material khác nếu cần
    }

    private SafeStorageUtil() {
    }

    public static SafeStorageUtil getInstance() {
        if (instance == null) {
            instance = new SafeStorageUtil();
        }
        return instance;
    }

    /**
     * Kiểm tra xem storage có còn chỗ trống không
     */
    public boolean canAddItem(Storage storage, ItemStack item) {
        synchronized (storage) {
            if (storage.isMaxSpace()) {
                return false;
            }

            long freeSpace = storage.getFreeSpace();
            return freeSpace == -1 || freeSpace >= item.getAmount();
        }
    }

    /**
     * Clean và validate item trước khi thêm vào storage
     */
    public ItemStack prepareItemForStorage(ItemStack originalItem) {
        if (originalItem == null || originalItem.getType() == Material.AIR) {
            throw new IllegalArgumentException("Invalid item");
        }

        ItemStack item = originalItem.clone();

        // Strip metadata nếu cần
        if (STRIP_META_MATERIALS.contains(item.getType())) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                // Giữ lại tên và lore, xóa các metadata khác
                String displayName = meta.hasDisplayName() ? meta.getDisplayName() : null;
                item.setItemMeta(new ItemStack(item.getType()).getItemMeta());
                if (displayName != null) {
                    ItemMeta newMeta = item.getItemMeta();
                    newMeta.setDisplayName(displayName);
                    item.setItemMeta(newMeta);
                }
            }
        }

        // Validate số lượng
        if (item.getAmount() <= 0) {
            throw new IllegalArgumentException("Invalid item amount");
        }

        return item;
    }

    /**
     * Thêm item vào storage một cách an toàn
     */
    public boolean addItemSafely(Storage storage, ItemStack item) {
        try {
            synchronized (storage) {
                // Kiểm tra storage space
                if (!canAddItem(storage, item)) {
                    return false;
                }

                // Clean và validate item
                ItemStack safeItem = prepareItemForStorage(item);

                // Lấy material key
                String itemKey = ItemUtil.toMaterialKey(safeItem);

                // Kiểm tra item đã tồn tại
                Optional<Item> existingItem = storage.getItem(itemKey);

                if (existingItem.isPresent()) {
                    // Kiểm tra overflow
                    long currentAmount = existingItem.get().getQuantity();
                    long newAmount = currentAmount + safeItem.getAmount();

                    if (newAmount > MAX_ITEM_AMOUNT) {
                        return false;
                    }

                    // Update số lượng
                    existingItem.get().add(safeItem.getAmount());
                } else {
                    // Thêm item mới
                    storage.addNewItem(safeItem);
                }

                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Xóa item khỏi storage một cách an toàn
     */
    public boolean removeItemSafely(Storage storage, String itemKey, long amount) {
        try {
            synchronized (storage) {
                Optional<Item> existingItem = storage.getItem(itemKey);
                if (!existingItem.isPresent()) {
                    return false;
                }

                Item item = existingItem.get();
                long currentAmount = item.getQuantity();

                if (currentAmount < amount) {
                    return false;
                }

                if (currentAmount == amount) {
                    storage.reset(itemKey);
                } else {
                    item.subtract(amount);
                }

                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Validate Material Type
     */
    public boolean isValidMaterialType(Material material) {
        return material != null
                && material != Material.AIR
                && !material.isLegacy()
                && material.isItem();
    }
}
