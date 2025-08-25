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

public class ItemSpawnListener extends BaseListener {
    private static final String MINER_META_KEY = "ES_MINER";

    public ItemSpawnListener(ExtraStorage instance) {
        super(instance);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        // Skip items that were manually dropped by players or handled by BlockBreakListener
        if (event.getEntity().hasMetadata("player_dropped") || event.getEntity().hasMetadata("handled_by_storage")) return;
        
        if (!instance.getSetting().isPickupToStorage()) return;

        Item item = event.getEntity();
        Location itemLoc = item.getLocation();
        
        // Check if in blacklisted world
        if (instance.getSetting().getBlacklistWorlds().contains(itemLoc.getWorld().getName())) return;

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

        // If no miner metadata, only then check nearby players
        if (miner == null || !miner.isOnline()) {
            double bestScore = Double.MAX_VALUE;
            
            for (Player player : itemLoc.getWorld().getPlayers()) {
                if (player.getGameMode() == GameMode.CREATIVE) continue;
                
                double distance = player.getLocation().distance(itemLoc);
                if (distance > 8) continue;

                if (distance < bestScore) {
                    bestScore = distance;
                    miner = player;
                }
            }
        }

        if (miner == null) return;
        
        User user = instance.getUserManager().getUser(miner);
        if (!user.hasPermission(Constants.STORAGE_PICKUP_PERMISSION)) return;

        Storage storage = user.getStorage();
        ItemStack itemStack = item.getItemStack().clone();
        int amount = itemStack.getAmount();
        boolean fullyProcessed = false;

        // Try to add to storage if possible
        if (!storage.isMaxSpace() && storage.canStore(itemStack)) {
            long freeSpace = storage.getFreeSpace();
            long maxTake = Math.min(amount, freeSpace == -1 ? Integer.MAX_VALUE : Math.min(freeSpace, Integer.MAX_VALUE));
            
            if (maxTake > 0) {
                // Add to storage what we can
                ItemStack storageItem = itemStack.clone();
                storageItem.setAmount((int) maxTake);
                storage.add(storageItem, (int) maxTake);
                
                // If we stored everything
                if (maxTake >= amount) {
                    event.setCancelled(true);
                    item.remove();
                    fullyProcessed = true;
                } else {
                    // Update remaining amount
                    amount -= maxTake;
                    itemStack.setAmount(amount);
                    item.setItemStack(itemStack);
                }
            }
        }
    }
}
