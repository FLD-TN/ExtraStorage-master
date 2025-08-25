package me.hsgamer.extrastorage.storage;

import me.hsgamer.extrastorage.api.storage.Storage;
import me.hsgamer.extrastorage.data.Constants;
import org.bukkit.entity.Player;

/**
 * Kiểm tra quyền của người chơi với storage
 */
public class PermissionValidator {

    /**
     * Kiểm tra xem người chơi có thể chỉnh sửa storage không
     * 
     * @param player  Người chơi cần kiểm tra
     * @param storage Storage cần kiểm tra
     * @return true nếu có quyền, false nếu không
     */
    public static boolean canModifyStorage(Player player, Storage storage) {
        // Admin có toàn quyền
        if (player.hasPermission(Constants.ADMIN_PERMISSION)) {
            return true;
        }

        // Người sở hữu có quyền chỉnh sửa
        if (storage.getStatus()) {
            return true;
        }

        return false;
    }

    /**
     * Kiểm tra xem người chơi có thể xem storage không
     * 
     * @param player  Người chơi cần kiểm tra
     * @param storage Storage cần kiểm tra
     * @return true nếu có quyền, false nếu không
     */
    public static boolean canViewStorage(Player player, Storage storage) {
        // Admin có toàn quyền xem
        if (player.hasPermission(Constants.ADMIN_PERMISSION)) {
            return true;
        }

        // Người sở hữu luôn có quyền xem
        if (storage.getStatus()) {
            return true;
        }

        return false;
    }

    /**
     * Kiểm tra xem người chơi có thể thêm item vào storage không
     * 
     * @param player  Người chơi cần kiểm tra
     * @param storage Storage cần kiểm tra
     * @return true nếu có quyền, false nếu không
     */
    public static boolean canAddItems(Player player, Storage storage) {
        // Storage phải đang được bật
        if (!storage.getStatus()) {
            return false;
        }

        // Admin có toàn quyền
        if (player.hasPermission(Constants.ADMIN_PERMISSION)) {
            return true;
        }

        // Người sở hữu có quyền thêm
        if (storage.getStatus()) {
            return true;
        }

        return false;
    }
}
