package me.hsgamer.extrastorage.listeners;

import me.hsgamer.extrastorage.ExtraStorage;
import me.hsgamer.extrastorage.api.storage.Storage;
import me.hsgamer.extrastorage.api.user.User;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Iterator;
import java.util.List;

public class EntityDeathListener extends BaseListener {

    public EntityDeathListener(ExtraStorage instance) {
        super(instance);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        // Chỉ xử lý nếu kẻ giết là người chơi
        Player player = event.getEntity().getKiller();
        if (player == null) {
            return;
        }

        // Kiểm tra các điều kiện cơ bản
        if (!instance.getSetting().isAutoStoreItem()
                || instance.getSetting().getBlacklistWorlds().contains(player.getWorld().getName())) {
            return;
        }

        User user = instance.getUserManager().getUser(player);
        Storage storage = user.getStorage();

        if (!storage.getStatus() || storage.isMaxSpace()) {
            return;
        }

        List<ItemStack> drops = event.getDrops();
        // Dùng Iterator để có thể xóa item khỏi danh sách một cách an toàn
        Iterator<ItemStack> iterator = drops.iterator();

        while (iterator.hasNext()) {
            ItemStack drop = iterator.next();

            // Nếu kho có thể chứa vật phẩm này
            if (storage.canStore(drop)) {
                long freeSpace = storage.getFreeSpace();
                int amount = drop.getAmount();

                if (freeSpace == -1 || freeSpace >= amount) {
                    // Nếu kho đủ chỗ, thêm toàn bộ và xóa khỏi danh sách drop
                    ListenerUtil.addToStorage(player, storage, drop, amount);
                    iterator.remove();
                } else if (freeSpace > 0) {
                    // Nếu kho chỉ đủ chứa một phần
                    int toStore = (int) freeSpace;
                    ListenerUtil.addToStorage(player, storage, drop, toStore);
                    // Giảm số lượng item còn lại trong danh sách drop
                    drop.setAmount(amount - toStore);
                }
            }
        }
    }
}