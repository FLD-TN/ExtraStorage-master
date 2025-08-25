package me.hsgamer.extrastorage.listeners;

import me.hsgamer.extrastorage.ExtraStorage;
import me.hsgamer.extrastorage.api.storage.Storage;
import me.hsgamer.extrastorage.api.user.User;
import me.hsgamer.extrastorage.data.Constants;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;

public class BlockBreakEventListener extends BaseListener {

    public BlockBreakEventListener(ExtraStorage instance) {
        super(instance);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        // Only set metadata if pickup to storage is enabled
        if (!instance.getSetting().isPickupToStorage())
            return;

        // This is just to mark that a block is being broken by a player
        // Set with a short expiry time (5 seconds) to avoid memory leaks
        event.getBlock().setMetadata("broken_by", new FixedMetadataValue(instance, event.getPlayer().getUniqueId()));

        // Schedule a task to clean up the metadata after 5 seconds
        instance.getServer().getScheduler().runTaskLater(instance, () -> {
            if (event.getBlock().hasMetadata("broken_by")) {
                event.getBlock().removeMetadata("broken_by", instance);
            }
        }, 100L); // 5 seconds = 100 ticks
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockDrop(BlockDropItemEvent event) {
        if (!instance.getSetting().isPickupToStorage())
            return;

        Player player = event.getPlayer();

        // Check if in blacklisted world
        if (instance.getSetting().getBlacklistWorlds().contains(player.getWorld().getName()))
            return;

        User user = instance.getUserManager().getUser(player);
        if (!user.hasPermission(Constants.STORAGE_PICKUP_PERMISSION))
            return;

        Storage storage = user.getStorage();

        // Process each dropped item
        for (org.bukkit.entity.Item droppedItem : event.getItems()) {
            ItemStack itemStack = droppedItem.getItemStack().clone();

            // Kiểm tra điều kiện lọc trước để tránh tính toán không cần thiết
            if (storage.canStore(itemStack) && !storage.isMaxSpace()) {
                int amount = itemStack.getAmount();
                long freeSpace = storage.getFreeSpace();

                if (freeSpace == -1 || freeSpace >= amount) {
                    // Kho không giới hạn hoặc đủ chỗ - thêm tất cả
                    storage.add(itemStack, amount);
                    droppedItem.remove(); // Remove the original dropped item
                    continue;
                } else if (freeSpace > 0) {
                    // Kho không đủ chỗ - thêm một phần và để lại phần còn lại
                    storage.add(itemStack, freeSpace);
                    itemStack.setAmount(amount - (int) freeSpace);
                    droppedItem.setItemStack(itemStack);
                    // Đánh dấu để không xử lý lại
                    droppedItem.setMetadata("bypass_storage", new FixedMetadataValue(instance, true));
                    continue;
                }
            }

            // If we couldn't store it, leave it as a drop
            droppedItem.setMetadata("bypass_storage", new FixedMetadataValue(instance, true));
        }
    }
}
