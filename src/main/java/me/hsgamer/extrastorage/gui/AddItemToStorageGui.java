package me.hsgamer.extrastorage.gui;

import me.hsgamer.extrastorage.Debug;
import me.hsgamer.extrastorage.configs.MaterialTypeConfig;
import me.hsgamer.extrastorage.gui.base.ESGui;
import me.hsgamer.extrastorage.gui.icon.Icon;
import me.hsgamer.extrastorage.storage.StorageManager;
import me.hsgamer.extrastorage.util.ItemUtil;
import me.hsgamer.extrastorage.util.CustomItemDetector;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AddItemToStorageGui extends ESGui {
    // Instance của MaterialTypeConfig để kiểm tra các loại vật liệu
    private final MaterialTypeConfig materialTypeConfig;
    private final StorageManager storageManager;

    @Override
    public void reopenGui(int page) {
        new AddItemToStorageGui(player).open();
    }

    @Override
    public void reopenGui(int page, ESGui.SortType sort, boolean order) {
        new AddItemToStorageGui(player).open();
    }

    public AddItemToStorageGui(Player player) {
        super("gui/add_item_to_storage", player, 1);
        // Khởi tạo MaterialTypeConfig từ ExtraStorage instance
        this.materialTypeConfig = instance.getMaterialTypeConfig();
        // Kiểm tra null player khi load config
        if (player != null) {
            this.storageManager = new StorageManager(instance.getUserManager().getUser(player).getStorage());
        } else {
            // Chỉ khởi tạo config khi player null (lúc load file)
            this.storageManager = null;
        }

        this.handleClick(event -> {
            // Lấy thông tin cơ bản về sự kiện
            int slot = event.getEvent().getSlot();
            int rawSlot = event.getEvent().getRawSlot();
            InventoryAction action = event.getEvent().getAction();

            // Lấy current item (item đang được click vào)
            ItemStack currentItem = event.getEvent().getCurrentItem();

            // Lấy cursor item (item đang cầm trên chuột)
            ItemStack cursorItem = event.getEvent().getWhoClicked().getItemOnCursor();

            Debug.log("[GUI] Click event: Action=" + action + ", Slot=" + slot + ", RawSlot=" + rawSlot + ", TopClick="
                    + event.isTopClick() + ", Inventory=" + event.getEvent().getClickedInventory());

            // CHẶN MỌI TƯƠNG TÁC VỚI KÍNH ĐEN
            // Chặn mọi tương tác có liên quan đến kính đen, bất kể là trong GUI hay túi đồ
            if (isBlackGlassPane(currentItem) || isBlackGlassPane(cursorItem)) {
                Debug.log("[GUI] Blocking interaction with BLACK_STAINED_GLASS_PANE");
                event.getEvent().setCancelled(true);

                return;
            }

            // Chặn mọi tương tác với các slot trang trí trong GUI
            // Cho phép tương tác với slot 5 (sách hướng dẫn), 12 (xác nhận), 13 (ô trống),
            // 16 (huỷ)
            if (event.isTopClick() && slot != 5 && slot != 12 && slot != 13 && slot != 16) {
                Debug.log("[GUI] Blocking interaction with decoration slot " + slot);
                event.getEvent().setCancelled(true);
                return;
            }

            // Chặn một số action đặc biệt có thể gây ra stack kính đen
            if (action == InventoryAction.COLLECT_TO_CURSOR ||
                    action == InventoryAction.MOVE_TO_OTHER_INVENTORY ||
                    action == InventoryAction.HOTBAR_SWAP ||
                    action == InventoryAction.HOTBAR_MOVE_AND_READD) {

                // Kiểm tra toàn bộ inventory xem có kính đen không
                boolean hasBlackGlass = false;
                for (ItemStack item : this.getInventory().getContents()) {
                    if (isBlackGlassPane(item)) {
                        hasBlackGlass = true;
                        break;
                    }
                }

                // Nếu có kính đen, chặn những action có thể gây stack
                if (hasBlackGlass) {
                    Debug.log("[GUI] Blocking potential stacking action: " + action);
                    event.getEvent().setCancelled(true);
                    return;
                }
            }

            // Mặc định hủy tất cả các sự kiện để tránh lỗi
            // Các trường hợp cụ thể sẽ được xử lý bên dưới
            event.getEvent().setCancelled(true);

            if (event.isTopClick()) {
                // Nếu click vào phần GUI bên trên
                if (slot == 13) {
                    // Kiểm tra xem người chơi có đang cố gắng đặt kính đen vào slot 13 không
                    if (cursorItem != null && cursorItem.getType() == Material.BLACK_STAINED_GLASS_PANE) {
                        Debug.log("[GUI] Blocking placement of BLACK_STAINED_GLASS_PANE in slot 13");
                        event.getEvent().setCancelled(true);
                        player.sendMessage("§c[ExtraStorage] §7Không thể thêm kính đen vào kho.");
                        return;
                    }

                    // Kiểm tra xem vật phẩm trên chuột có được phép lưu trữ không
                    if (cursorItem != null && cursorItem.getType() != Material.AIR) {
                        Material material = cursorItem.getType();
                        if (!isAllowedItem(material)) {
                            Debug.log("[GUI] Blocking placement of disallowed item in slot 13: " + material.name());
                            event.getEvent().setCancelled(true);
                            player.sendMessage("§c[ExtraStorage] §fVật phẩm này không được phép lưu trữ trong kho!");

                            if (materialTypeConfig.isBlacklisted(material)) {
                                player.sendMessage(
                                        "§c[ExtraStorage] Vật phẩm này nằm trong danh sách vật phẩm bị cấm.");
                            }

                            return;
                        }
                    }

                    // Slot 13 là ô đặt item, cho phép tất cả các tương tác khác
                    Debug.log("[GUI] Allowing interaction with item slot 13");
                    event.getEvent().setCancelled(false);

                    // Thông báo hướng dẫn nếu slot trống và người chơi không đang cầm vật phẩm
                    if ((this.getInventory().getItem(13) == null ||
                            this.getInventory().getItem(13).getType() == Material.AIR) &&
                            (cursorItem == null || cursorItem.getType() == Material.AIR)) {
                        player.sendMessage(
                                "§e[ExtraStorage] §7Để thêm vật phẩm vào kho, hãy SHIFT+CLICK vào vật phẩm trong túi đồ của bạn.");
                    }

                    // Nếu người chơi đặt vật phẩm vào slot 13, thông báo nhấn nút xác nhận
                    if (cursorItem != null && cursorItem.getType() != Material.AIR) {
                        player.sendMessage("§a[ExtraStorage] §fĐã đặt vật phẩm. Nhấn nút Xác nhận để thêm vào kho.");
                    }
                } else if (slot == 11) {
                    // Nút xác nhận - cho phép click để trigger icon handler
                    Debug.log("[GUI] Confirm button clicked");
                    event.getEvent().setCancelled(false);
                } else if (slot == 15) {
                    // Nút hủy bỏ - cho phép click để trigger icon handler
                    Debug.log("[GUI] Cancel button clicked");
                    event.getEvent().setCancelled(false);
                } else if (slot == 4) {
                    // Nút hướng dẫn - cho phép click để trigger icon handler
                    Debug.log("[GUI] Guide book clicked");
                    event.getEvent().setCancelled(false);
                } else {
                    // Tất cả các ô khác (kính đen, v.v.) - ngăn mọi tương tác
                    Debug.log("[GUI] Blocking interaction with decoration slot " + slot);
                    event.getEvent().setCancelled(true);
                }
            } else {
                // Người chơi click vào inventory của họ

                // Lấy item đang được click
                ItemStack clickedItem = event.getEvent().getCurrentItem();

                // Chặn COLLECT_TO_CURSOR (double-click) nếu trong túi người chơi có kính đen
                // hoặc book
                // Điều này ngăn không cho người chơi gom kính đen từ GUI vào túi đồ
                if (action == InventoryAction.COLLECT_TO_CURSOR) {
                    // Nếu cursor đang cầm kính đen hoặc book, hoặc vật phẩm được click là kính đen
                    // hoặc book
                    if ((cursorItem != null
                            && (isBlackGlassPane(cursorItem) || cursorItem.getType() == Material.WRITTEN_BOOK)) ||
                            (clickedItem != null && (isBlackGlassPane(clickedItem)
                                    || clickedItem.getType() == Material.WRITTEN_BOOK))) {
                        Debug.log("[GUI] Blocking COLLECT_TO_CURSOR for BLACK_STAINED_GLASS_PANE or WRITTEN_BOOK");
                        event.getEvent().setCancelled(true);
                        return;
                    }
                }

                // Chặn các tương tác khác từ inventory người chơi có thể ảnh hưởng đến kính đen
                // trong GUI
                if (isBlackGlassPane(clickedItem) || isBlackGlassPane(cursorItem)) {
                    Debug.log("[GUI] Blocking interaction with BLACK_STAINED_GLASS_PANE from player inventory");
                    event.getEvent().setCancelled(true);
                    return;
                }

                if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                    // Xử lý shift-click đặc biệt
                    Debug.log("[GUI] Shift-click detected from player inventory");
                    event.getEvent().setCancelled(true); // Luôn hủy shift-click mặc định

                    // Kiểm tra xem slot 13 có trống không
                    boolean isSlot13Empty = this.getInventory().getItem(13) == null ||
                            this.getInventory().getItem(13).getType() == Material.AIR;

                    // Nếu slot 13 trống và item hợp lệ, đặt item vào đó
                    if (isSlot13Empty && clickedItem != null && clickedItem.getType() != Material.AIR) {
                        // Kiểm tra xem item có được phép lưu trữ không trước khi cho phép đưa vào kho
                        Material material = clickedItem.getType();
                        if (isAllowedItem(material)) {
                            // Di chuyển item thủ công thay vì dựa vào hành vi mặc định
                            ItemStack itemToMove = clickedItem.clone();
                            // Đặt item vào slot 13 với số lượng giống như trong inventory
                            this.getInventory().setItem(13, itemToMove);

                            // Xóa item từ inventory người chơi
                            event.getEvent().setCurrentItem(null);

                            player.sendMessage(
                                    "§a[ExtraStorage] §fĐã đặt vật phẩm vào ô thêm vào kho. Nhấn nút Xác nhận để thêm vào kho.");
                            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);

                            Debug.log("[GUI] Successfully moved item to slot 13");
                        } else {
                            // Không cho phép đưa vật phẩm không hợp lệ vào kho
                            player.sendMessage("§c[ExtraStorage] §fVật phẩm này không được phép lưu trữ trong kho!");

                            // Cung cấp thông tin chi tiết hơn dựa trên loại vật phẩm
                            if (materialTypeConfig.isBlacklisted(material)) {
                                player.sendMessage(
                                        "§c[ExtraStorage] Vật phẩm này nằm trong danh sách vật phẩm bị cấm.");
                            }

                            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                            Debug.log("[GUI] Blocked invalid item: " + material.name());
                        }
                    } else if (!isSlot13Empty) {
                        player.sendMessage(
                                "§c[ExtraStorage] §fÔ đã có vật phẩm! Hãy xác nhận hoặc hủy vật phẩm hiện tại trước.");
                        Debug.log("[GUI] Blocked shift-click: Slot 13 already has an item");
                    } else {
                        Debug.log("[GUI] Blocked shift-click: Invalid item");
                    }
                } else {
                    // Cho phép các tương tác bình thường với inventory của người chơi
                    // và cho phép người chơi kéo vật phẩm vào slot 13
                    if (event.getEvent().getRawSlot() == 13 && (action == InventoryAction.PLACE_ALL ||
                            action == InventoryAction.PLACE_ONE ||
                            action == InventoryAction.PLACE_SOME ||
                            action == InventoryAction.SWAP_WITH_CURSOR)) {

                        Debug.log("[GUI] Drag/Place action to slot 13 detected: " + action);

                        // Kiểm tra xem item có được phép lưu trữ không
                        if (cursorItem != null && cursorItem.getType() != Material.AIR) {
                            Material material = cursorItem.getType();
                            if (isAllowedItem(material)) {
                                // Cho phép đặt item vào slot 13
                                event.getEvent().setCancelled(false);
                                Debug.log("[GUI] Allowing placing item in slot 13");
                                player.sendMessage(
                                        "§a[ExtraStorage] §fĐã đặt vật phẩm. Nhấn nút Xác nhận để thêm vào kho.");
                            } else {
                                // Không cho phép đặt vật phẩm không hợp lệ
                                event.getEvent().setCancelled(true);
                                player.sendMessage(
                                        "§c[ExtraStorage] §fVật phẩm này không được phép lưu trữ trong kho!");
                                if (materialTypeConfig.isBlacklisted(material)) {
                                    player.sendMessage(
                                            "§c[ExtraStorage] Vật phẩm này nằm trong danh sách vật phẩm bị cấm.");
                                }
                            }
                        }
                    } else if (action == InventoryAction.PLACE_ALL ||
                            action == InventoryAction.PLACE_ONE ||
                            action == InventoryAction.PLACE_SOME ||
                            action == InventoryAction.SWAP_WITH_CURSOR) {

                        Debug.log("[GUI] Drag/Place action detected: " + action);
                        // Cho phép người chơi tương tác với inventory của họ
                        // nhưng nhắc nhở họ có thể sử dụng shift-click hoặc kéo trực tiếp vào slot 13
                        player.sendMessage(
                                "§e[ExtraStorage] §fĐể thêm vật phẩm, hãy SHIFT+CLICK hoặc kéo trực tiếp vào ô trống.");
                        event.getEvent().setCancelled(false);
                    } else {
                        // Cho phép các tương tác khác với inventory của người chơi
                        Debug.log("[GUI] Allowing normal interaction with player inventory");
                        event.getEvent().setCancelled(false);
                    }
                }
            }
        });
        this.load();
    }

    /**
     * Kiểm tra xem một loại vật liệu có được phép lưu trữ không
     *
     * @param material Loại vật liệu cần kiểm tra
     * @return true nếu được phép, false nếu không
     */
    private boolean isAllowedItem(Material material) {
        // Chặn các vật phẩm bị cấm, kính đen, và các block đặc biệt
        if (material == Material.BLACK_STAINED_GLASS_PANE
                || material == Material.AIR
                || material == Material.BEDROCK
                || material == Material.BARRIER) {
            return false;
        }

        // Kiểm tra xem vật phẩm có trong blacklist không
        if (materialTypeConfig.isBlacklisted(material)) {
            return false;
        }

        // Cho phép thêm vào kho
        return true;
    }
    
    /**
     * Kiểm tra một ItemStack xem có được phép thêm vào kho không
     * @param item ItemStack cần kiểm tra
     * @return true nếu được phép, false nếu không
     */
    public boolean isItemStackAllowed(ItemStack item) {
        if (item == null) {
            return false;
        }
        
        // Kiểm tra các thuộc tính Material cơ bản
        if (!isAllowedItem(item.getType())) {
            return false;
        }
        
        // Kiểm tra xem có phải là vật phẩm tùy chỉnh không (MMOItems, ItemsAdder, etc.)
        if (CustomItemDetector.isCustomItem(item)) {
            // Kiểm tra MMOItems
            if (CustomItemDetector.isMMOItem(item)) {
                // Lấy MMOItem ID
                String mmoItemId = CustomItemDetector.getMMOItemId(item);
                if (mmoItemId != null) {
                    // Tách TYPE và ID
                    String[] parts = mmoItemId.split(":", 2);
                    String type = parts[0];
                    String id = parts.length > 1 ? parts[1] : "";
                    
                    // Kiểm tra whitelist
                    boolean allowed = materialTypeConfig.isCustomItemWhitelisted(id, "mmoitems") || 
                                     materialTypeConfig.isCustomItemWhitelisted(type + ":" + id, "mmoitems") ||
                                     materialTypeConfig.isCustomItemWhitelisted(type + ":*", "mmoitems");
                    
                    Debug.log("[AddItemToStorageGui] MMOItem check: " + mmoItemId + " - Allowed: " + allowed);
                    return allowed;
                }
                
                // Nếu không lấy được ID, không cho phép
                Debug.log("[AddItemToStorageGui] Không xác định được ID của MMOItem, từ chối");
                return false;
            }
            
            // Kiểm tra các loại vật phẩm tùy chỉnh khác
            // Hiện tại từ chối tất cả các loại khác
            Debug.log("[AddItemToStorageGui] Từ chối vật phẩm tùy chỉnh không được nhận dạng: " + item.getType());
            return false;
        }
        
        return true;
    }

    /**
     * Kiểm tra công khai xem một loại vật liệu có được phép lưu trữ không
     *
     * @param material Loại vật liệu cần kiểm tra
     * @return true nếu được phép, false nếu không
     */
    public boolean isItemAllowed(Material material) {
        return isAllowedItem(material);
    }

    /**
     * Kiểm tra xem một ItemStack có phải là Black Stained Glass Pane không
     *
     * @param item ItemStack cần kiểm tra
     * @return true nếu là Black Stained Glass Pane, false nếu không phải
     */
    private boolean isBlackGlassPane(ItemStack item) {
        return item != null && item.getType() == Material.BLACK_STAINED_GLASS_PANE;
    }

    /**
     * Tải các icon và layout cho GUI từ file cấu hình YAML
     */
    private void load() {
        // Đọc các phần tử từ config
        // Nút xác nhận từ ConfirmItem
        Icon confirmIcon = this.createIconFromConfig(null, "ConfirmItem")
                .handleClick(event -> {
                    Player p = event.getPlayer();
                    // Lấy item trong slot 13
                    ItemStack item = this.getInventory().getItem(13);

                    // Nếu không có item, thông báo và return
                    if (item == null || item.getType() == Material.AIR) {
                        p.sendMessage("§c[ExtraStorage] §fBạn chưa đặt vật phẩm vào ô trống!");
                        p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                        return;
                    }

                    // Kiểm tra một lần nữa xem vật phẩm có được phép lưu trữ không
                    Material material = item.getType();
                    if (!isItemStackAllowed(item)) {
                        p.sendMessage("§c[ExtraStorage] §fVật phẩm này không được phép lưu trữ trong kho!");
                        if (materialTypeConfig.isBlacklisted(material)) {
                            p.sendMessage("§c[ExtraStorage] Vật phẩm này nằm trong danh sách vật phẩm bị cấm.");
                        } else if (me.hsgamer.extrastorage.util.CustomItemDetector.isCustomItem(item)) {
                            p.sendMessage("§c[ExtraStorage] Không thể thêm vật phẩm tùy chỉnh (MMOItems, ItemsAdder...) vào kho.");
                        }
                        p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                        return;
                    }

                    // Thêm item vào kho
                    if (storageManager != null) {
                        storageManager.addItem(item);
                        p.sendMessage("§a[ExtraStorage] §fĐã thêm vật phẩm vào kho!");
                        p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                    } else {
                        Debug.log("[GUI] StorageManager is null, cannot add item");
                    }

                    // Xóa item khỏi slot 13
                    this.getInventory().setItem(13, null);
                });

        // Nút hủy bỏ từ CancelItem
        Icon cancelIcon = this.createIconFromConfig(null, "CancelItem")
                .handleClick(event -> {
                    Player p = event.getPlayer();
                    // Lấy item trong slot 13
                    ItemStack item = this.getInventory().getItem(13);

                    // Nếu không có item, chỉ cần đóng GUI
                    if (item == null || item.getType() == Material.AIR) {
                        p.sendMessage("§c[ExtraStorage] §fĐã hủy bỏ thao tác thêm vật phẩm.");
                        p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 1.0f);
                        p.closeInventory();
                        return;
                    }

                    // Trả lại item vào túi đồ của người chơi
                    ItemUtil.giveItem(p, item);

                    // Thông báo
                    p.sendMessage("§a[ExtraStorage] §fĐã trả lại vật phẩm vào túi đồ của bạn.");
                    p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);

                    // Xóa item khỏi slot 13
                    this.getInventory().setItem(13, null);

                    // Đóng GUI
                    p.closeInventory();
                });

        // Sách hướng dẫn từ InfoItem
        Icon guideIcon = this.createIconFromConfig(null, "InfoItem")
                .handleClick(event -> {
                    Player p = event.getPlayer();
                    // Hiển thị hướng dẫn khi người chơi click vào sách
                    p.sendMessage("");
                    p.sendMessage("§e§l=== HƯỚNG DẪN THÊM VẬT PHẨM VÀO KHO ===");
                    p.sendMessage("§f1. Đặt vật phẩm vào ô trống ở giữa bằng cách SHIFT+CLICK");
                    p.sendMessage("§f   hoặc kéo thả trực tiếp vào ô trống.");
                    p.sendMessage("§f2. Nhấn nút Xác nhận (khối len xanh lá) để thêm vào kho.");
                    p.sendMessage("§f3. Nhấn nút Huỷ bỏ (khối len đỏ) để hủy thao tác.");
                    p.sendMessage("§c⚠ Lưu ý: Vật phẩm đã thêm vào kho sẽ không thể lấy ra được nữa!");
                    p.sendMessage("");
                });

        // Tạo các item trang trí từ phần DecorateItems
        ConfigurationSection decorSection = this.getConfig().getConfigurationSection("DecorateItems");
        if (decorSection != null) {
            for (String key : decorSection.getKeys(false)) {
                Icon decorIcon = this.createIconFromDecorSection(key, null);
                this.addIcon(decorIcon);
            }
        }

        // Thêm tất cả các icon chính vào GUI
        this.addIcon(confirmIcon);
        this.addIcon(cancelIcon);
        this.addIcon(guideIcon);
    }

    /**
     * Tạo Icon từ phần cấu hình trong file YAML
     * 
     * @param config     ConfigurationSection chứa toàn bộ cấu hình
     * @param sectionKey Khóa của phần cấu hình (ví dụ: "ConfirmItem")
     * @return Icon được tạo từ cấu hình
     */
    private Icon createIconFromConfig(Map<String, Object> config, String sectionKey) {
        ConfigurationSection itemConfig = this.getConfig().getConfigurationSection(sectionKey);
        if (itemConfig == null) {
            Debug.log("[GUI] Missing config section: " + sectionKey);
            // Trả về icon mặc định nếu không tìm thấy cấu hình
            ItemStack defaultItem = new ItemStack(Material.BARRIER);
            ItemMeta meta = defaultItem.getItemMeta();
            meta.setDisplayName("§cMissing Config: " + sectionKey);
            defaultItem.setItemMeta(meta);
            return new Icon(defaultItem);
        }

        // Lấy thông tin vật liệu
        String materialName = itemConfig.getString("Material");
        Material material = Material.valueOf(materialName);

        // Tạo ItemStack
        ItemStack itemStack = new ItemStack(material);
        ItemMeta meta = itemStack.getItemMeta();

        // Đặt tên hiển thị nếu có
        if (itemConfig.contains("Name")) {
            String name = itemConfig.getString("Name");
            meta.setDisplayName(name.replace("&", "§"));
        }

        // Đặt lore nếu có
        if (itemConfig.contains("Lore")) {
            List<String> lore = new ArrayList<>();
            List<String> configLore = itemConfig.getStringList("Lore");
            for (String line : configLore) {
                lore.add(line.replace("&", "§"));
            }
            meta.setLore(lore);
        }

        itemStack.setItemMeta(meta);

        // Tạo Icon với ItemStack
        Icon icon = new Icon(itemStack);

        // Đặt slots nếu có
        if (itemConfig.contains("Slot")) {
            int slot = itemConfig.getInt("Slot");
            icon.setSlots(slot);
        } else if (itemConfig.contains("Slots")) {
            String slotsStr = itemConfig.getString("Slots");
            icon.setSlots(this.parseSlots(slotsStr));
        }

        return icon;
    }

    /**
     * Tạo Icon từ phần Decorate trong file YAML
     * 
     * @param key         Tên của item trang trí
     * @param sectionPath Đường dẫn đến phần cấu hình của item trang trí
     * @return Icon được tạo từ cấu hình
     */
    private Icon createIconFromDecorSection(String key, Map<String, Object> decorValue) {
        ConfigurationSection decorConfig = this.getConfig().getConfigurationSection("DecorateItems." + key);
        if (decorConfig == null) {
            Debug.log("[GUI] Missing decor config section: " + key);
            // Trả về icon mặc định nếu không tìm thấy cấu hình
            ItemStack defaultItem = new ItemStack(Material.BARRIER);
            ItemMeta meta = defaultItem.getItemMeta();
            meta.setDisplayName("§cMissing Decor Config: " + key);
            defaultItem.setItemMeta(meta);
            return new Icon(defaultItem);
        }

        // Lấy thông tin vật liệu
        String materialName = decorConfig.getString("Material");
        Material material = Material.valueOf(materialName);

        // Tạo ItemStack
        ItemStack itemStack = new ItemStack(material);
        ItemMeta meta = itemStack.getItemMeta();

        // Đặt tên hiển thị nếu có
        if (decorConfig.contains("Name")) {
            String name = decorConfig.getString("Name");
            meta.setDisplayName(name.replace("&", "§"));
        }

        // Đặt lore nếu có
        if (decorConfig.contains("Lore")) {
            List<String> lore = new ArrayList<>();
            List<String> configLore = decorConfig.getStringList("Lore");
            for (String line : configLore) {
                lore.add(line.replace("&", "§"));
            }
            meta.setLore(lore);
        }

        itemStack.setItemMeta(meta);

        // Tạo Icon với ItemStack
        Icon icon = new Icon(itemStack);

        // Đặt slots
        if (decorConfig.contains("Slots")) {
            String slotsStr = decorConfig.getString("Slots");
            icon.setSlots(this.parseSlots(slotsStr));
        }

        return icon;
    }

    /**
     * Chuyển đổi chuỗi định dạng slots (như "0-4,6-11,14-15,17-26") thành mảng số
     * nguyên
     * 
     * @param slotsStr Chuỗi định dạng slots
     * @return Mảng số nguyên chứa các slot
     */
    private int[] parseSlots(String slotsStr) {
        List<Integer> slots = new ArrayList<>();

        // Tách các phần cách nhau bởi dấu phẩy
        String[] parts = slotsStr.split(",");
        for (String part : parts) {
            if (part.contains("-")) {
                // Xử lý phạm vi (ví dụ: "0-4")
                String[] range = part.split("-");
                int start = Integer.parseInt(range[0]);
                int end = Integer.parseInt(range[1]);
                for (int i = start; i <= end; i++) {
                    slots.add(i);
                }
            } else {
                // Xử lý số đơn lẻ
                slots.add(Integer.parseInt(part));
            }
        }

        // Chuyển List<Integer> thành int[]
        return slots.stream().mapToInt(Integer::intValue).toArray();
    }
}
