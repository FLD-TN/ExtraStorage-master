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

public class BlockBreakListener extends BaseListener {

    public BlockBreakListener(ExtraStorage instance) {
        super(instance);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        // This is just to mark that a block is being broken by a player
        event.getBlock().setMetadata("broken_by", new FixedMetadataValue(instance, event.getPlayer().getUniqueId()));
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
            
            // Try to store in storage first
            if (storage.canStore(itemStack) && !storage.isMaxSpace()) {
                storage.add(itemStack, itemStack.getAmount());
                droppedItem.remove(); // Remove the original dropped item
                continue;
            }
            
            // If we couldn't store it, leave it as a drop
            droppedItem.setMetadata("bypass_storage", new FixedMetadataValue(instance, true));
        }
    }
}