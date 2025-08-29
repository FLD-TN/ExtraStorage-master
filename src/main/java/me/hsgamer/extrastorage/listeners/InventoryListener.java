package me.hsgamer.extrastorage.listeners;

import me.hsgamer.extrastorage.ExtraStorage;
import me.hsgamer.extrastorage.gui.abstraction.GuiCreator;
import me.hsgamer.extrastorage.gui.events.GuiClickEvent;
import me.hsgamer.extrastorage.gui.icon.Icon;
import me.hsgamer.extrastorage.gui.icon.events.IconClickEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class InventoryListener
        extends BaseListener {

    private final Map<UUID, Double> delayed;

    public InventoryListener(ExtraStorage instance) {
        super(instance);
        this.delayed = new HashMap<>();
    }

    private boolean isDelayed(Player player) {
        double current = System.currentTimeMillis() / 1000.0;
        Double last = delayed.get(player.getUniqueId());
        if ((last != null) && (current < last))
            return true;
        delayed.put(player.getUniqueId(), current + 0.15);
        return false;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player))
            return;
        Player player = (Player) event.getWhoClicked();

        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof GuiCreator))
            return;

        GuiCreator gui = (GuiCreator) holder;

        // Nếu không có inventory được click, chỉ cần hủy sự kiện
        if (event.getClickedInventory() == null) {
            event.setCancelled(true);
            return;
        }

        // Xử lý đặc biệt cho AddItemToStorageGui
        if (holder instanceof me.hsgamer.extrastorage.gui.AddItemToStorageGui) {
            // Nếu click vào inventory của người chơi, cho phép tương tác
            if (event.getClickedInventory().getHolder() != holder) {
                // Cho phép các tương tác bình thường với inventory người chơi
                return;
            }

            // Nếu click vào slot 13 của GUI (ô trống để đặt item), cho phép tương tác
            if (event.getSlot() == 13) {
                me.hsgamer.extrastorage.gui.AddItemToStorageGui addItemGui = (me.hsgamer.extrastorage.gui.AddItemToStorageGui) holder;

                // Nếu đang shift-click từ inventory của người chơi
                if (event.isShiftClick() && event.getCurrentItem() != null
                        && event.getCurrentItem().getType() != org.bukkit.Material.AIR) {
                    // Kiểm tra xem vật phẩm có được phép lưu trữ không
                    if (!addItemGui.isItemStackAllowed(event.getCurrentItem())) {
                        event.setCancelled(true);
                        player.sendMessage("§c[ExtraStorage] §fVật phẩm này không được phép lưu trữ trong kho!");
                        return;
                    }
                    // Cho phép shift-click
                    return;
                }

                // Cho phép tương tác với slot 13
                return;
            }
        }

        // Đối với các trường hợp khác:
        // 1. Ngăn chặn tất cả các tương tác trực tiếp với GUI
        if (event.getClickedInventory().getHolder() instanceof GuiCreator) {
            event.setCancelled(true);
        }

        // 2. Ngăn chặn cả shift-click từ inventory người chơi vào GUI
        if (event.isShiftClick() && !(holder instanceof me.hsgamer.extrastorage.gui.AddItemToStorageGui)) {
            event.setCancelled(true);
        }

        if (this.isDelayed(player))
            return;

        GuiClickEvent clickEvent = new GuiClickEvent(event, gui, player);
        gui.callClick(clickEvent);

        // Chỉ xử lý icon nếu click vào GUI
        if (event.getClickedInventory().getHolder() instanceof GuiCreator) {
            Icon icon = gui.getIconAt(event.getSlot());
            if (icon == null)
                return;
            icon.callClick(new IconClickEvent(event, icon, player));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player))
            return;

        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof GuiCreator))
            return;

        // Kiểm tra xem có bất kỳ slot nào trong GUI đang được kéo thả
        boolean affectsTopInventory = false;
        int topInventorySize = event.getView().getTopInventory().getSize();

        for (int slot : event.getRawSlots()) {
            // Nếu slot nhỏ hơn kích thước của top inventory, thì đó là slot trong GUI
            if (slot < topInventorySize) {
                affectsTopInventory = true;
                break;
            }
        }

        // Nếu không ảnh hưởng đến GUI, cho phép kéo thả
        if (!affectsTopInventory) {
            return;
        }

        // Nếu là GUI AddItemToStorageGui, kiểm tra kỹ lưỡng
        if (holder instanceof me.hsgamer.extrastorage.gui.AddItemToStorageGui) {
            // Kiểm tra xem có slot nào thuộc về GUI (không phải inventory người chơi)
            boolean hasOtherTopSlots = false;
            for (int slot : event.getRawSlots()) {
                if (slot < topInventorySize) {
                    // Nếu slot không phải là slot 13, hủy bỏ
                    if (slot != 13) {
                        hasOtherTopSlots = true;
                        break;
                    }
                }
            }

            // Nếu chỉ kéo vào slot 13, cho phép sau khi kiểm tra
            if (!hasOtherTopSlots && event.getRawSlots().contains(13)) {
                Player player = (Player) event.getWhoClicked();
                ItemStack cursorItem = event.getOldCursor();

                me.hsgamer.extrastorage.gui.AddItemToStorageGui gui = (me.hsgamer.extrastorage.gui.AddItemToStorageGui) holder;

                // Kiểm tra xem item có được phép lưu trữ không
                if (gui.isItemStackAllowed(cursorItem)) {
                    // Cho phép kéo vào slot 13
                    return;
                } else {
                    event.setCancelled(true);
                    player.sendMessage("§c[ExtraStorage] §fVật phẩm này không được phép lưu trữ trong kho!");
                    return;
                }
            }
        }

        // Hủy toàn bộ hành động kéo (drag) trong các GUI khác
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onMoveItem(InventoryMoveItemEvent event) {
        // Chặn mọi di chuyển item vào hoặc ra khỏi bất kỳ GUI nào
        if (event.getSource().getHolder() instanceof GuiCreator ||
                event.getDestination().getHolder() instanceof GuiCreator) {
            // Hủy toàn bộ hành động di chuyển item vào/ra GUI
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPickupItem(InventoryPickupItemEvent event) {
        // Chặn các hopper, v.v. nhặt item từ GUI
        if (event.getInventory().getHolder() instanceof GuiCreator) {
            event.setCancelled(true);
        }
    }

}