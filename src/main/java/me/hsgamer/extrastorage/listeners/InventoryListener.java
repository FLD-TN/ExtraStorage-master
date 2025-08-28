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

        // Đối với các trường hợp khác, ngăn chặn tất cả các tương tác với GUI
        if (event.getClickedInventory().getHolder() instanceof GuiCreator) {
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

        // Nếu là GUI AddItemToStorageGui, cho phép kéo vào slot 13
        if (holder instanceof me.hsgamer.extrastorage.gui.AddItemToStorageGui) {
            // Kiểm tra xem có slot nào thuộc về GUI (không phải inventory người chơi)
            boolean hasTopInventorySlot = false;
            for (int slot : event.getRawSlots()) {
                if (slot < event.getView().getTopInventory().getSize()) {
                    // Nếu slot không phải là slot 13, hủy bỏ
                    if (slot != 13) {
                        hasTopInventorySlot = true;
                        break;
                    }
                }
            }

            // Nếu chỉ kéo vào slot 13 hoặc kéo vào inventory người chơi, cho phép
            if (!hasTopInventorySlot || event.getRawSlots().contains(13)) {
                // Kiểm tra item có hợp lệ không nếu kéo vào slot 13
                if (event.getRawSlots().contains(13)) {
                    Player player = (Player) event.getWhoClicked();
                    ItemStack cursorItem = event.getOldCursor();

                    me.hsgamer.extrastorage.gui.AddItemToStorageGui gui = (me.hsgamer.extrastorage.gui.AddItemToStorageGui) holder;

                    // Kiểm tra xem item có được phép lưu trữ không
                    if (!gui.isItemStackAllowed(cursorItem)) {
                        event.setCancelled(true);
                        player.sendMessage("§c[ExtraStorage] §fVật phẩm này không được phép lưu trữ trong kho!");
                        return;
                    }

                    // Cho phép kéo vào slot 13
                    return;
                }
                return;
            }
        }

        // Hủy toàn bộ hành động kéo (drag) trong các GUI khác
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onMoveItem(InventoryMoveItemEvent event) {
        if (event.getSource().getHolder() instanceof GuiCreator ||
                event.getDestination().getHolder() instanceof GuiCreator) {
            // Hủy toàn bộ hành động di chuyển item vào/ra GUI
            event.setCancelled(true);
        }
    }

}