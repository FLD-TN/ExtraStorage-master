package me.hsgamer.extrastorage.listeners;

import me.hsgamer.extrastorage.ExtraStorage;
import me.hsgamer.extrastorage.api.storage.Storage;
import me.hsgamer.extrastorage.api.user.User;
import me.hsgamer.extrastorage.data.Constants;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.MetadataValue;

import java.util.UUID;

public class ItemSpawnEventListener extends BaseListener {
    private static final String MINER_META_KEY = "ES_MINER";

    public ItemSpawnEventListener(ExtraStorage instance) {
        super(instance);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        // Skip items that were manually dropped by players or handled by
        // BlockBreakListener
        if (event.getEntity().hasMetadata("player_dropped") || event.getEntity().hasMetadata("bypass_storage"))
            return;

        if (!instance.getSetting().isPickupToStorage())
            return;

        Item item = event.getEntity();
        Location itemLoc = item.getLocation();

        // Check if in blacklisted world
        if (instance.getSetting().getBlacklistWorlds().contains(itemLoc.getWorld().getName()))
            return;

        // Try to get the miner from metadata first
        Player miner = null;
        if (item.hasMetadata(MINER_META_KEY)) {
            for (MetadataValue meta : item.getMetadata(MINER_META_KEY)) {
                if (meta.value() instanceof Player) {
                    miner = (Player) meta.value();
                    break;
                }
            }
        }

        // Check if nearby block has metadata about who broke it
        if (miner == null) {
            org.bukkit.block.Block block = item.getLocation().getBlock();
            if (block.hasMetadata("broken_by")) {
                UUID playerId = null;
                for (MetadataValue value : block.getMetadata("broken_by")) {
                    if (value.getOwningPlugin() instanceof ExtraStorage) {
                        playerId = (UUID) value.value();
                        break;
                    }
                }

                if (playerId != null) {
                    miner = instance.getServer().getPlayer(playerId);

                    // We don't need to clean up metadata here as it has a scheduled cleanup
                    // and other items might need it
                }
            }
        }

        // If no miner metadata, only then check nearby players
        if (miner == null || !miner.isOnline()) {
            double bestScore = Double.MAX_VALUE;

            for (Player player : itemLoc.getWorld().getPlayers()) {
                if (player.getGameMode() == GameMode.CREATIVE)
                    continue;

                double distance = player.getLocation().distance(itemLoc);
                if (distance > 8)
                    continue;

                if (distance < bestScore) {
                    bestScore = distance;
                    miner = player;
                }
            }
        }

        if (miner == null)
            return;

        User user = instance.getUserManager().getUser(miner);
        if (!user.hasPermission(Constants.STORAGE_PICKUP_PERMISSION))
            return;

        Storage storage = user.getStorage();
        ItemStack itemStack = item.getItemStack().clone();

        // Kiểm tra điều kiện lọc trước để tránh tính toán không cần thiết
        if (storage.canStore(itemStack) && !storage.isMaxSpace()) {
            // Xử lý số lượng dựa trên không gian có sẵn
            int amount = itemStack.getAmount();
            long freeSpace = storage.getFreeSpace();

            if (freeSpace == -1 || freeSpace >= amount) {
                // Kho không giới hạn hoặc đủ chỗ - thêm tất cả
                storage.add(itemStack, amount);
                event.setCancelled(true);
                item.remove();
            } else if (freeSpace > 0) {
                // Kho không đủ chỗ - thêm một phần và để lại phần còn lại
                storage.add(itemStack, freeSpace);
                itemStack.setAmount(amount - (int) freeSpace);
                item.setItemStack(itemStack);
            }
        }
    }
}
