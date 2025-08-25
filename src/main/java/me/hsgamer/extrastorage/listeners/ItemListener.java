package me.hsgamer.extrastorage.listeners;

import com.google.common.base.Strings;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import me.hsgamer.extrastorage.ExtraStorage;
import me.hsgamer.extrastorage.api.storage.Storage;
import me.hsgamer.extrastorage.api.user.User;
import me.hsgamer.extrastorage.configs.Message;
import me.hsgamer.extrastorage.data.user.UserManager;
import me.hsgamer.extrastorage.util.ActionBar;
import me.hsgamer.extrastorage.util.ItemUtil;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.TimeUnit;

public class ItemListener extends BaseListener {
    private final Cache<String, User> locCache;
    private final UserManager manager;

    public ItemListener(ExtraStorage instance) {
        super(instance);
        this.manager = instance.getUserManager();
        this.locCache = CacheBuilder.newBuilder()
                .expireAfterWrite(10, TimeUnit.SECONDS)
                .build();
    }

    private String locToString(Location loc) {
        return String.format("%s:%d:%d:%d", loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(),
                loc.getBlockZ());
    }

    /**
     * Kiểm tra xem inventory của người chơi có full không
     * 
     * @param player Người chơi cần kiểm tra
     * @param item   Item cần kiểm tra
     * @return true nếu inventory full, false nếu còn chỗ trống
     */
    private boolean isInventoryFull(Player player, ItemStack item) {
        ItemStack[] items = player.getInventory().getStorageContents();
        int count = item.getAmount();

        // Tính toán số lượng item có thể chứa trong inventory
        for (ItemStack iStack : items) {
            if (count < 1)
                break;

            // Slot trống
            if ((iStack == null) || (iStack.getType() == Material.AIR)) {
                count -= item.getMaxStackSize();
                continue;
            }

            // Slot có cùng loại item
            if (iStack.isSimilar(item)) {
                int stackLeft = item.getMaxStackSize() - iStack.getAmount();
                count -= stackLeft;
            }
        }

        // Còn item chưa thể chứa = inventory full
        return count > 0;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        // Skip nếu người chơi trong Creative hoặc Spectator mode
        GameMode gameMode = player.getGameMode();
        if (gameMode == GameMode.CREATIVE || gameMode == GameMode.SPECTATOR) {
            return;
        }

        User user = manager.getUser(player);
        Storage storage = user.getStorage();
        Location location = event.getBlock().getLocation();
        String locToString = locToString(location);

        // Skip nếu thế giới bị blacklist hoặc storage không hoạt động
        if (instance.getSetting().getBlacklistWorlds().contains(location.getWorld().getName())
                || (!storage.getStatus())) {
            locCache.invalidate(locToString);
            return;
        }

        // Không cho phép phá block nếu storage full và setting blockedMining = true
        if (instance.getSetting().isBlockedMining() && storage.isMaxSpace()) {
            event.setCancelled(true);
            locCache.invalidate(locToString);

            String msg = Message.getMessage("WARN.StorageIsFull");
            if (!Strings.isNullOrEmpty(msg))
                ActionBar.send(player, msg);
            return;
        }

        // Cập nhật cache
        User cur = locCache.getIfPresent(locToString);
        if ((cur == null) || (cur.hashCode() != user.hashCode()))
            locCache.put(locToString, user);
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        // Skip nếu setting autoStoreItem = false
        if (!instance.getSetting().isAutoStoreItem())
            return;

        Location loc = event.getLocation();
        String locToString = this.locToString(loc);

        // Skip nếu không tìm thấy người chơi hoặc offline
        User user = locCache.getIfPresent(locToString);
        if (user == null || !user.isOnline())
            return;

        Storage storage = user.getStorage();
        ItemStack item = event.getEntity().getItemStack();

        // Skip nếu item không thể store
        if (!storage.canStore(item))
            return;

        // Kiểm tra xem item có trong filter không
        String itemKey = ItemUtil.toMaterialKey(item);
        boolean isFiltered = storage.getFilteredItems().containsKey(itemKey);

        // Skip nếu storage full
        if (storage.isMaxSpace())
            return;

        // Skip nếu item không được filter và inventory chưa full
        if (!isFiltered && !isInventoryFull(user.getPlayer(), item))
            return;

        // Tính toán số lượng có thể thêm vào storage
        boolean isResidual = false;
        int amount = item.getAmount();
        long freeSpace = storage.getFreeSpace();
        long maxTake = Math.min(amount, freeSpace == -1 ? Integer.MAX_VALUE : Math.min(freeSpace, Integer.MAX_VALUE));
        amount = (int) maxTake;

        // Xử lý trường hợp storage không đủ chỗ chứa
        if ((freeSpace != -1) && (freeSpace < amount)) {
            amount = (int) freeSpace;
            item.setAmount(item.getAmount() - amount);
            isResidual = true;
        }

        // Hủy event nếu lấy hết item
        if (!isResidual)
            event.setCancelled(true);

        // Thêm item vào storage
        ListenerUtil.addToStorage(user.getPlayer(), storage, item, amount);
    }
}