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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        Item droppedItem = event.getItemDrop();

        // Chỉ xử lý nếu túi đồ của người chơi đã đầy
        if (player.getInventory().firstEmpty() != -1) {
            return;
        }

        // Bỏ qua nếu tính năng bị tắt hoặc thế giới bị cấm
        if (!instance.getSetting().isPickupToStorage()
                || instance.getSetting().getBlacklistWorlds().contains(player.getWorld().getName())) {
            return;
        }

        User user = instance.getUserManager().getUser(player);
        // Bỏ qua nếu người chơi không có quyền, kho đang tắt hoặc kho đã đầy
        if (!user.hasPermission(Constants.STORAGE_PICKUP_PERMISSION) || !user.getStorage().getStatus()
                || user.getStorage().isMaxSpace()) {
            return;
        }

        Storage storage = user.getStorage();
        ItemStack itemStack = droppedItem.getItemStack();

        // Bỏ qua nếu kho không thể chứa loại vật phẩm này
        if (!storage.canStore(itemStack)) {
            return;
        }

        // Kiểm tra tính năng bộ lọc toàn cục
        if (!ExtraStorage.isFilterEnabled()) {
            // Nếu bộ lọc toàn cục tắt, chỉ tự động lấy item khi inventory đã đầy
            // (Không cần kiểm tra vì đã kiểm tra ở trên)
        } else {
            // Nếu bộ lọc toàn cục bật, kiểm tra item có trong filter không
            String itemKey = itemStack.getType().name();
            if (!storage.getFilteredItems().containsKey(itemKey)) {
                // Item không có trong filter, bỏ qua
                return;
            }
        }

        int amount = itemStack.getAmount();
        long freeSpace = storage.getFreeSpace();
        long maxTake = (freeSpace == -1) ? amount : Math.min(amount, freeSpace);

        if (maxTake <= 0) {
            return;
        }

        // Tạo một bản sao của vật phẩm để thêm vào kho
        ItemStack itemToStore = itemStack.clone();
        itemToStore.setAmount((int) maxTake);

        // Thêm vật phẩm vào kho ảo
        ListenerUtil.addToStorage(player, storage, itemToStore, (int) maxTake);

        if (maxTake >= amount) {
            // SỬA LỖI: Thay vì hủy sự kiện, chúng ta cho nó diễn ra
            // và ngay lập tức XÓA vật phẩm vật lý khỏi thế giới.
            // Điều này đảm bảo vật phẩm được trừ khỏi tay người chơi mà không bị nhân đôi.
            droppedItem.remove();
        } else {
            // Nếu kho chỉ đủ chứa một phần, giảm số lượng của vật phẩm bị vứt ra
            itemStack.setAmount(amount - (int) maxTake);
            droppedItem.setItemStack(itemStack);
        }
    }
}