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
import me.hsgamer.extrastorage.util.InventoryCheckUtil;
import me.hsgamer.extrastorage.util.ItemFilterService;
import me.hsgamer.extrastorage.util.ItemUtil;
import me.hsgamer.hscore.bukkit.utils.VersionUtils;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import me.hsgamer.extrastorage.util.ItemFilterService;

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
                // Sử dụng MONITOR để xử lý sau cùng
                EventPriority.MONITOR, (listener, event) -> {
                    if (event instanceof EntityPickupItemEvent) {
                        try {
                            EntityPickupItemEvent pickupEvent = (EntityPickupItemEvent) event;
                            // CHỈ xử lý nếu event chưa bị cancel
                            if (!pickupEvent.isCancelled()) {
                                onEntityPickupItem(pickupEvent);
                            }
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

        // Kiểm tra nếu item đã được xử lý bởi system khác
        if (entity.hasMetadata("processed_by_storage")) {
            Debug.log("[PickupListener] Item already processed by storage, skipping");
            return;
        }

        // Clone item và giữ lại các meta quan trọng
        ItemStack originalItem = entity.getItemStack();
        ItemStack item = originalItem.clone();

        // Debug log
        Debug.log("[PickupListener] Processing pickup: " + item.getType() + " x" + item.getAmount() + " for player "
                + player.getName());

        // Đảm bảo metadata
        if (originalItem.hasItemMeta()) {
            try {
                item.setItemMeta(originalItem.getItemMeta().clone());
            } catch (Exception e) {
                item = new ItemStack(originalItem.getType(), originalItem.getAmount());
                item.setData(originalItem.getData());
                instance.getLogger().warning(
                        "Failed to clone item metadata for " + originalItem.getType() + ". Using simplified clone.");
            }
        }

        User user = instance.getUserManager().getUser(player);
        if (user == null || !user.hasPermission(Constants.STORAGE_PICKUP_PERMISSION))
            return;

        Storage storage = user.getStorage();

        // Kiểm tra item có thể lưu trữ không
        if (!storage.canStore(item))
            return;

        // 🔴 XÓA HOÀN TOÀN ĐIỀU KIỆN KIỂM TRA INVENTORY Ở ĐÂY 🔴
        // CHỈ kiểm tra global filter và item filter

        // Kiểm tra xem global filter có bật không
        if (!ExtraStorage.isFilterEnabled()) {
            Debug.log("[PickupListener] Global filter disabled - always pickup to storage");
        } else {
            String itemKey = ItemUtil.toMaterialKey(item);
            // Tối ưu: Trước hết kiểm tra xem người chơi có bất kỳ bộ lọc nào không
            if (!ItemFilterService.hasPlayerFilter(player.getUniqueId())) {
                Debug.log("[PickupListener] Player has no filters, skipping: " + player.getName());
                return;
            }

            // Sử dụng ItemFilterService để kiểm tra vật phẩm
            if (!ItemFilterService.shouldPickupToStorage(player, item, storage)) {
                Debug.log("[PickupListener] Item is not filtered, skipping: " + itemKey);
                return;
            }
            Debug.log("[PickupListener] Item is filtered - pickup to storage: " + itemKey);
        }

        // Kiểm tra giới hạn kho
        if (storage.isMaxSpace())
            return;

        // Lấy số lượng vật phẩm
        int amount;
        try {
            amount = pickupHandler.getAmount(event, entity, item);
        } catch (Exception e) {
            amount = item.getAmount();
            instance.getLogger().warning("Failed to get amount from handler, using item amount: " + e.getMessage());
        }

        if (amount <= 0) {
            instance.getLogger().warning("Item amount is invalid: " + amount + ", ignoring pickup");
            return;
        }

        int result = amount;
        long freeSpace = storage.getFreeSpace();

        // Xử lý logic dựa trên free space
        if (freeSpace == -1) {
            // Không giới hạn - nhận tất cả
            result = amount;
            event.setCancelled(true);
            entity.remove();
        } else if (freeSpace <= 0) {
            // Không còn chỗ trống
            return;
        } else if (freeSpace < amount) {
            // Giới hạn - chỉ lấy số lượng tối đa có thể
            result = (int) Math.min(freeSpace, Integer.MAX_VALUE);
            int residual = amount - result;

            try {
                pickupHandler.applyAmount(entity, item, residual);
                item.setAmount(residual);
                entity.setItemStack(item);
                // Kiểm tra trạng thái kho
                if (!storage.getStatus()) {
                    Debug.log("[PickupListener] Storage is disabled, not picking up item for " + player.getName());
                    return;
                }
                entity.setMetadata("processed_by_storage", new org.bukkit.metadata.FixedMetadataValue(instance, true));
            } catch (Exception e) {
                instance.getLogger().warning("Failed to update residual amount: " + e.getMessage());
                return;
            }
        } else {
            // Đủ chỗ trống
            event.setCancelled(true);
            entity.remove();
        }

        // Thêm vào storage
        ItemStack storageItem = item.clone();
        storageItem.setAmount(result);
        ListenerUtil.addToStorage(player, storage, storageItem, result);
    }

    private interface PickupHandler {
        int getAmount(EntityPickupItemEvent event, Item entity, ItemStack item);

        void applyAmount(Item entity, ItemStack item, int amount);
    }
}