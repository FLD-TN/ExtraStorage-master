package me.hsgamer.extrastorage.listeners;

import com.bgsoftware.wildstacker.api.WildStackerAPI;
import com.craftaro.ultimatestacker.api.UltimateStackerApi;
import com.craftaro.ultimatestacker.api.stack.item.StackedItemManager;
import dev.rosewood.rosestacker.api.RoseStackerAPI;
import me.hsgamer.extrastorage.Debug;
import me.hsgamer.extrastorage.ExtraStorage;
import me.hsgamer.extrastorage.api.storage.Storage;
import me.hsgamer.extrastorage.api.user.User;
import me.hsgamer.extrastorage.data.Constants;
import me.hsgamer.hscore.bukkit.utils.VersionUtils;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;

public class PickupListener implements Listener {
    private final ExtraStorage instance;
    private final PickupHandler pickupHandler;

    public PickupListener(ExtraStorage instance) {
        this.instance = instance;
        this.pickupHandler = getPickupHandler();
        register();
    }

    private PickupHandler getPickupHandler() {
        PluginManager pluginManager = instance.getServer().getPluginManager();
        if (pluginManager.isPluginEnabled("WildStacker"))
            return new PickupHandler() {
                @Override
                public int getAmount(EntityPickupItemEvent event, Item entity, ItemStack item) {
                    return WildStackerAPI.getItemAmount(entity);
                }

                @Override
                public void applyAmount(Item entity, ItemStack item, int amount) {
                    com.bgsoftware.wildstacker.api.objects.StackedItem sItem = WildStackerAPI.getStackedItem(entity);
                    sItem.setStackAmount(amount, true);
                }
            };
        else if (pluginManager.isPluginEnabled("UltimateStacker"))
            return new PickupHandler() {
                @Override
                public int getAmount(EntityPickupItemEvent event, Item entity, ItemStack item) {
                    StackedItemManager manager = UltimateStackerApi.getStackedItemManager();
                    return manager.isStackedItem(entity) ? manager.getActualItemAmount(entity) : item.getAmount();
                }

                @Override
                public void applyAmount(Item entity, ItemStack item, int amount) {
                    StackedItemManager manager = UltimateStackerApi.getStackedItemManager();
                    manager.updateStack(entity, amount);
                }
            };
        else if (pluginManager.isPluginEnabled("RoseStacker"))
            return new PickupHandler() {
                @Override
                public int getAmount(EntityPickupItemEvent event, Item entity, ItemStack item) {
                    RoseStackerAPI api = RoseStackerAPI.getInstance();
                    dev.rosewood.rosestacker.stack.StackedItem stackedItem = api.getStackedItem(entity);
                    return stackedItem != null ? stackedItem.getStackSize() : item.getAmount();
                }

                @Override
                public void applyAmount(Item entity, ItemStack item, int amount) {
                    RoseStackerAPI api = RoseStackerAPI.getInstance();
                    dev.rosewood.rosestacker.stack.StackedItem stackedItem = api.getStackedItem(entity);
                    if (stackedItem != null) {
                        stackedItem.setStackSize(amount);
                    }
                }
            };
        else
            return new PickupHandler() {
                @Override
                public int getAmount(EntityPickupItemEvent event, Item entity, ItemStack item) {
                    int amount = item.getAmount();
                    if (VersionUtils.isAtLeast(17)) {
                        amount += event.getRemaining();
                    }
                    return amount;
                }

                @Override
                public void applyAmount(Item entity, ItemStack item, int amount) {
                    // Không cần thực hiện hành động nào cho trường hợp mặc định
                }
            };
    }

    private void register() {
        instance.getServer().getPluginManager().registerEvent(EntityPickupItemEvent.class, this,
                // Sử dụng HIGHEST thay vì LOW để đảm bảo plugin chúng ta xử lý trước các plugin
                // khác
                EventPriority.HIGHEST, (listener, event) -> {
                    if (event instanceof EntityPickupItemEvent) {
                        try {
                            EntityPickupItemEvent pickupEvent = (EntityPickupItemEvent) event;
                            onEntityPickupItem(pickupEvent);
                        } catch (Exception e) {
                            // Ghi log ngoại lệ để dễ debug
                            instance.getLogger().warning("Error processing pickup event: " + e.getMessage());
                            Debug.log("[PickupListener] Exception in pickup handler: " + e.toString());
                        }
                    }
                }, instance, true);
    }

    private void onEntityPickupItem(EntityPickupItemEvent event) {
        // Kiểm tra xem tính năng pickup to storage có được bật không
        if ((!instance.getSetting().isPickupToStorage()) || (!(event.getEntity() instanceof Player))) {
            Debug.log("[PickupListener] Pickup to storage is disabled or entity is not a player");
            return;
        }

        Player player = (Player) event.getEntity();
        Item entity = event.getItem();

        // Kiểm tra xem thế giới có nằm trong danh sách đen không
        if (instance.getSetting().getBlacklistWorlds().contains(entity.getWorld().getName())) {
            Debug.log("[PickupListener] World is blacklisted: " + entity.getWorld().getName());
            return;
        }

        // Nếu entity được đánh dấu bypass_storage, không xử lý
        if (entity.hasMetadata("bypass_storage")) {
            Debug.log("[PickupListener] Item has bypass_storage metadata, skipping");
            return;
        }

        // Clone item và giữ lại các meta quan trọng để đảm bảo tương thích với các
        // plugin khác
        ItemStack originalItem = entity.getItemStack();
        ItemStack item = originalItem.clone();

        // Debug log
        Debug.log("[PickupListener] Processing pickup: " + item.getType() + " x" + item.getAmount() + " for player "
                + player.getName());

        // Đảm bảo rằng chúng ta có bản sao hoàn chỉnh của metadata
        if (originalItem.hasItemMeta()) {
            try {
                item.setItemMeta(originalItem.getItemMeta().clone());
            } catch (Exception e) {
                // Fallback nếu có lỗi khi clone metadata
                item = new ItemStack(originalItem.getType(), originalItem.getAmount());
                item.setData(originalItem.getData());
                instance.getLogger().warning(
                        "Failed to clone item metadata for " + originalItem.getType() + ". Using simplified clone.");
            }
        }

        User user = instance.getUserManager().getUser(player);
        if (!user.hasPermission(Constants.STORAGE_PICKUP_PERMISSION))
            return;

        Storage storage = user.getStorage();
        // Kiểm tra cả hai điều kiện trước để tránh tính toán không cần thiết
        if (!storage.canStore(item))
            return;

        // Kiểm tra xem global filter có bật không và item có được lọc không
        if (!ExtraStorage.isFilterEnabled()) {
            // Nếu global filter tắt, chỉ nhặt item vào kho khi hành trang đầy
            if (!isInventoryFull(player)) {
                Debug.log("[PickupListener] Global filter is disabled and inventory is not full, skipping");
                return;
            }
        } else {
            // Nếu global filter bật, kiểm tra item có trong filter không
            String itemKey = item.getType().name();
            if (!storage.getFilteredItems().containsKey(itemKey) && !isInventoryFull(player)) {
                Debug.log("[PickupListener] Item is not filtered and inventory is not full, skipping");
                return;
            }
        }

        // Kiểm tra giới hạn kho chỉ khi item có thể được lưu trữ
        if (storage.isMaxSpace())
            return;

        // Đảm bảo lấy số lượng vật phẩm từ handler một cách an toàn
        int amount;
        try {
            amount = pickupHandler.getAmount(event, entity, item);
        } catch (Exception e) {
            // Fallback nếu có lỗi từ handler - sử dụng số lượng từ item
            amount = item.getAmount();
            instance.getLogger().warning("Failed to get amount from handler, using item amount: " + e.getMessage());
        }

        // Đảm bảo amount > 0
        if (amount <= 0) {
            instance.getLogger().warning("Item amount is invalid: " + amount + ", ignoring pickup");
            return;
        }

        int result = amount;

        // Lấy giới hạn không gian một lần và sử dụng cho tính toán
        long freeSpace = storage.getFreeSpace();

        // Xử lý logic dựa trên free space
        if (freeSpace == -1) {
            // Không giới hạn - nhận tất cả
            result = amount;
            // Hủy sự kiện pickup và xóa vật phẩm từ thế giới
            event.setCancelled(true);
            entity.remove();
        } else if (freeSpace <= 0) {
            // Không còn chỗ trống - không làm gì cả
            return;
        } else if (freeSpace < amount) {
            // Giới hạn - chỉ lấy số lượng tối đa có thể
            result = (int) Math.min(freeSpace, Integer.MAX_VALUE);
            int residual = amount - result;

            try {
                // Cập nhật số lượng còn lại
                pickupHandler.applyAmount(entity, item, residual);
                item.setAmount(residual);
                entity.setItemStack(item);
            } catch (Exception e) {
                instance.getLogger().warning("Failed to update residual amount: " + e.getMessage());
                // Nếu lỗi khi cập nhật, hủy xử lý
                return;
            }
        } else {
            // Đủ chỗ trống - hủy sự kiện pickup và xóa vật phẩm
            event.setCancelled(true);
            entity.remove();
        }

        // Clone lại item để đảm bảo không ảnh hưởng đến item gốc
        ItemStack storageItem = item.clone();
        storageItem.setAmount(result);

        // Thêm vào storage với số lượng đã tính toán
        ListenerUtil.addToStorage(player, storage, storageItem, result);
    }

    private interface PickupHandler {
        int getAmount(EntityPickupItemEvent event, Item entity, ItemStack item);

        void applyAmount(Item entity, ItemStack item, int amount);
    }

    /**
     * Kiểm tra xem hành trang của người chơi có đầy không
     * 
     * @param player Người chơi cần kiểm tra
     * @return true nếu đầy, false nếu còn chỗ trống
     */
    private boolean isInventoryFull(Player player) {
        return player.getInventory().firstEmpty() == -1;
    }
}