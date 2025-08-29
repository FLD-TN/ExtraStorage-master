package me.hsgamer.extrastorage.util;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class InventoryCheckUtil {

    public static boolean isInventoryTrulyFull(Player player, ItemStack item) {
        if (item == null) {
            return player.getInventory().firstEmpty() == -1;
        }

        // Kiểm tra inventory có thực sự đầy không (kể cả stack được không)
        for (ItemStack content : player.getInventory().getContents()) {
            if (content == null) {
                return false; // Có slot trống
            }
            if (content.isSimilar(item) && content.getAmount() < content.getMaxStackSize()) {
                return false; // Có thể stack thêm
            }
        }
        return true; // Inventory thực sự đầy
    }
}