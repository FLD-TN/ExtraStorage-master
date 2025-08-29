package me.hsgamer.extrastorage.listeners;

import me.hsgamer.extrastorage.Debug;
import me.hsgamer.extrastorage.ExtraStorage;
import me.hsgamer.extrastorage.api.storage.Storage;
import me.hsgamer.extrastorage.api.user.User;
import me.hsgamer.extrastorage.data.Constants;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;

public class PlayerDropListener implements Listener {
    private final ExtraStorage instance;

    public PlayerDropListener(ExtraStorage instance) {
        this.instance = instance;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        Item droppedItem = event.getItemDrop();

        // Kiểm tra metadata để tránh xử lý lại
        if (droppedItem.hasMetadata("processed_by_storage")) {
            return;
        }

        // Bỏ qua nếu tính năng bị tắt hoặc thế giới bị cấm
        if (!instance.getSetting().isPickupToStorage()
                || instance.getSetting().getBlacklistWorlds().contains(player.getWorld().getName())) {
            return;
        }

        User user = instance.getUserManager().getUser(player);
        if (user == null || !user.hasPermission(Constants.STORAGE_PICKUP_PERMISSION) || !user.getStorage().getStatus()
                || user.getStorage().isMaxSpace()) {
            return;
        }

        Storage storage = user.getStorage();
        ItemStack itemStack = droppedItem.getItemStack();

        // Bỏ qua nếu kho không thể chứa loại vật phẩm này
        if (!storage.canStore(itemStack)) {
            return;
        }

        // 🔴 XÓA ĐIỀU KIỆN KIỂM TRA INVENTORY Ở ĐÂY 🔴
        // CHỈ kiểm tra global filter và item filter

        if (!ExtraStorage.isFilterEnabled()) {
            // Nếu global filter tắt, LUÔN nhặt vào kho
            Debug.log("[DropListener] Global filter disabled - always pickup to storage");
        } else {
            // Nếu global filter bật, kiểm tra item có trong filter không
            String itemKey = itemStack.getType().name();
            if (!storage.getFilteredItems().containsKey(itemKey)) {
                return; // Item không có trong filter, bỏ qua
            }
            Debug.log("[DropListener] Item is filtered - pickup to storage: " + itemKey);
        }

        int amount = itemStack.getAmount();
        long freeSpace = storage.getFreeSpace();
        long maxTake = (freeSpace == -1) ? amount : Math.min(amount, freeSpace);

        if (maxTake <= 0) {
            return;
        }

        // Thêm vật phẩm vào kho ảo
        ItemStack itemToStore = itemStack.clone();
        itemToStore.setAmount((int) maxTake);
        ListenerUtil.addToStorage(player, storage, itemToStore, (int) maxTake);

        // Đánh dấu item đã được xử lý
        droppedItem.setMetadata("processed_by_storage", new org.bukkit.metadata.FixedMetadataValue(instance, true));

        if (maxTake >= amount) {
            droppedItem.remove();
        } else {
            itemStack.setAmount(amount - (int) maxTake);
            droppedItem.setItemStack(itemStack);
        }
    }
}