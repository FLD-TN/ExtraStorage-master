package me.hsgamer.extrastorage.listeners;

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

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (!instance.getSetting().isPickupToStorage())
            return;

        Player player = event.getPlayer();
        Item droppedItem = event.getItemDrop();

        // Check if in blacklisted world
        if (instance.getSetting().getBlacklistWorlds().contains(player.getWorld().getName()))
            return;

        // Check if player has permission and full inventory
        if (player.getInventory().firstEmpty() != -1)
            return; // Only process if inventory is full

        User user = instance.getUserManager().getUser(player);
        if (!user.hasPermission(Constants.STORAGE_PICKUP_PERMISSION))
            return;

        Storage storage = user.getStorage();
        if (storage.isMaxSpace())
            return;

        ItemStack itemStack = droppedItem.getItemStack();
        if (!storage.canStore(itemStack))
            return;

        // Calculate how many items we can store
        int amount = itemStack.getAmount();
        long freeSpace = storage.getFreeSpace();
        long maxTake = Math.min(amount, freeSpace == -1 ? Integer.MAX_VALUE : Math.min(freeSpace, Integer.MAX_VALUE));

        if (maxTake <= 0)
            return;

        // Create a new ItemStack to avoid NBT conflicts
        ItemStack newItem = new ItemStack(itemStack.getType(), (int) maxTake);
        newItem.setData(itemStack.getData());
        if (itemStack.hasItemMeta()) {
            newItem.setItemMeta(itemStack.getItemMeta().clone());
        }

        // If we can take all items, cancel the drop
        if (maxTake >= amount) {
            event.setCancelled(true);
        } else {
            // If we can only take some items, update the dropped amount
            itemStack.setAmount(amount - (int) maxTake);
            droppedItem.setItemStack(itemStack);
        }

        // Add items to storage
        ListenerUtil.addToStorage(player, storage, newItem, (int) maxTake);
    }
}
