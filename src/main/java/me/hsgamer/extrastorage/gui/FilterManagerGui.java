package me.hsgamer.extrastorage.gui;

import me.hsgamer.extrastorage.ExtraStorage;
import me.hsgamer.extrastorage.api.item.Item;
import me.hsgamer.extrastorage.api.storage.Storage;
import me.hsgamer.extrastorage.configs.Message;
import me.hsgamer.extrastorage.data.Constants;
import me.hsgamer.extrastorage.gui.base.ESGui;
import me.hsgamer.extrastorage.gui.icon.Icon;
import me.hsgamer.extrastorage.util.ItemUtil;
import me.hsgamer.extrastorage.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public final class FilterManagerGui extends ESGui {
    private Map<String, Material> materials;
    private int[] slots;
    private int page;
    private int maxPages;

    public FilterManagerGui(Player player, int page) {
        super("gui/filter_manager", player, page, SortType.NAME, true);

        if (player == null)
            return;

        // Kiểm tra xem bộ lọc có bị khóa không (kiểm tra cả trạng thái cá nhân và toàn
        // cục)
        if (!ExtraStorage.isFilterEnabled() || !storage.getStatus()) {
            // Thông báo cho người chơi biết bộ lọc đang bị khóa
            player.sendMessage(Message.getMessage("FAIL.filter-disabled"));
            // Đóng GUI này và mở FilterGui
            player.closeInventory();
            new FilterGui(player, 1).open();
            return;
        }

        this.page = page;
        this.slots = this.getSlots("MaterialItem");

        // Lấy danh sách tất cả các material trong game
        this.materials = new HashMap<>();
        for (Material material : Material.values()) {
            if (material.isItem() && !material.isAir() && material != Material.AIR) {
                String key = material.name().toLowerCase();
                materials.put(key, material);
            }
        }

        // Tính số trang tối đa
        this.maxPages = (int) Math.ceil((double) materials.size() / slots.length);

        this.handleClick(event -> {
            if (event.isTopClick())
                return;
            event.setCancelled(true);

            final ItemStack clickedItem = event.getClickedItem();
            if (clickedItem == null || clickedItem.getType() == Material.AIR)
                return;
            this.playSoundIfPresent();

            final String validKey = ItemUtil.toMaterialKey(clickedItem);
            if (validKey.equals(Constants.INVALID)) {
                player.sendMessage(Message.getMessage("FAIL.invalid-item"));
                return;
            }

            // Kiểm tra blacklist
            if (instance.getSetting().getBlacklist().contains(validKey)) {
                player.sendMessage(Message.getMessage("FAIL.item-blacklisted"));
                return;
            }

            // Thêm vào bộ lọc
            storage.addNewItem(validKey);
            Optional<Item> optional = storage.getItem(validKey);
            if (optional.isPresent()) {
                optional.get().setFiltered(true);
                player.sendMessage(Message.getMessage("SUCCESS.item-added-to-filter")
                        .replaceAll(Utils.getRegex("item"), instance.getSetting().getNameFormatted(validKey, true)));
            }

            this.reopenGui(page);
        });

        this.load();
    }

    @Override
    public void reopenGui(int page) {
        new FilterManagerGui(player, page).open();
    }

    @Override
    public void reopenGui(int page, SortType sort, boolean order) {
        new FilterManagerGui(player, page).open();
    }

    private void load() {
        this.addBackButton();
        this.addFilterStatusButton();
        this.addMaterialItems();
    }

    private void addBackButton() {
        int[] slots = this.getSlots("ControlItems.Back");
        if ((slots == null) || (slots.length < 1))
            return;

        final String PATH = "ControlItems.Back";

        Icon icon = new Icon(
                this.getItemStack(
                        PATH,
                        user,
                        meta -> {
                            String name = config.getString(PATH + ".Name", "");
                            if (!name.isEmpty())
                                meta.setDisplayName(name);

                            List<String> lores = config.getStringList(PATH + ".Lore");
                            if (!lores.isEmpty())
                                meta.setLore(lores);

                            if (config.contains(PATH + ".CustomModelData")) {
                                int modelData = config.getInt(PATH + ".CustomModelData");
                                meta.setCustomModelData(modelData);
                            }
                        }))
                .handleClick(event -> {
                    this.playSoundIfPresent();
                    new FilterGui(player, 1).open();
                }).setSlots(slots);

        this.addIcon(icon);
    }

    private void addFilterStatusButton() {
        int[] slots = this.getSlots("ControlItems.FilterStatus");
        if ((slots == null) || (slots.length < 1))
            return;

        final String PATH = "ControlItems.FilterStatus";

        Icon icon = new Icon(
                this.getItemStack(
                        PATH,
                        user,
                        meta -> {
                            String name = config.getString(PATH + ".Name", "");
                            if (!name.isEmpty())
                                meta.setDisplayName(name);

                            List<String> lores = config.getStringList(PATH + ".Lore");
                            if (!lores.isEmpty()) {
                                for (int i = 0; i < lores.size(); i++) {
                                    String lore = lores.get(i)
                                            .replaceAll(Utils.getRegex("status"), Message.getMessage(
                                                    "STATUS." + (storage.getStatus() ? "filtered" : "unfiltered")));
                                    lores.set(i, lore);
                                }
                                meta.setLore(lores);
                            }

                            if (config.contains(PATH + ".CustomModelData")) {
                                int modelData = config.getInt(PATH + ".CustomModelData");
                                meta.setCustomModelData(modelData);
                            }
                        }))
                .handleClick(event -> {
                    this.playSoundIfPresent();
                    storage.setStatus(!storage.getStatus());
                    this.reopenGui(page);
                }).setSlots(slots);

        this.addIcon(icon);
    }

    private void addMaterialItems() {
        List<String> sortedMaterials = new ArrayList<>(materials.keySet());
        Collections.sort(sortedMaterials);

        int index = 0, startIndex, endIndex;
        endIndex = Math.min(sortedMaterials.size(), page * slots.length);

        for (startIndex = (page - 1) * slots.length; startIndex < endIndex; startIndex++) {
            String key = sortedMaterials.get(startIndex);
            Material material = materials.get(key);

            ItemStack iStack = new ItemStack(material);
            ItemMeta meta = iStack.getItemMeta();

            // Kiểm tra xem vật phẩm này đã có trong bộ lọc chưa
            String validKey = ItemUtil.toMaterialKey(iStack);
            boolean isFiltered = storage.canStore(validKey);

            String name = material.name();
            meta.setDisplayName("§f" + name);

            List<String> lore = new ArrayList<>();
            lore.add("§7Material: §f" + material.name());

            if (isFiltered) {
                lore.add("§a✓ §7Đã có trong bộ lọc");
                lore.add("§8[§6Nhấp chuột§8] §7để xem trong bộ lọc");
            } else {
                lore.add("§c✗ §7Chưa có trong bộ lọc");
                lore.add("§8[§6Nhấp chuột§8] §7để thêm vào bộ lọc");
            }

            meta.setLore(lore);
            iStack.setItemMeta(meta);

            Icon icon = new Icon(iStack)
                    .handleClick(event -> {
                        this.playSoundIfPresent();

                        if (isFiltered) {
                            // Nếu đã có trong bộ lọc, mở FilterGui
                            new FilterGui(player, 1).open();
                        } else {
                            // Nếu chưa có, thêm vào bộ lọc
                            storage.addNewItem(validKey);
                            Optional<Item> optional = storage.getItem(validKey);
                            if (optional.isPresent()) {
                                optional.get().setFiltered(true);
                                player.sendMessage(Message.getMessage("SUCCESS.item-added-to-filter")
                                        .replaceAll(Utils.getRegex("item"),
                                                instance.getSetting().getNameFormatted(validKey, true)));
                            }
                            this.reopenGui(page);
                        }
                    })
                    .setSlots(slots[index++]);
            this.addIcon(icon);
        }

        // Nút trang trước/sau
        this.addPageButtons();
    }

    private void addPageButtons() {
        // Previous button
        if (page > 1) {
            int[] slots = this.getSlots("ControlItems.Previous");
            if ((slots == null) || (slots.length < 1))
                return;

            final String PATH = "ControlItems.Previous";

            Icon icon = new Icon(
                    this.getItemStack(
                            PATH,
                            user,
                            meta -> {
                                String name = config.getString(PATH + ".Name", "");
                                if (!name.isEmpty())
                                    meta.setDisplayName(name);

                                List<String> lores = config.getStringList(PATH + ".Lore");
                                if (!lores.isEmpty()) {
                                    for (int i = 0; i < lores.size(); i++) {
                                        String lore = lores.get(i)
                                                .replaceAll(Utils.getRegex("page"), String.valueOf(page))
                                                .replaceAll(Utils.getRegex("max(\\_|\\-)?page(s)?"),
                                                        String.valueOf(maxPages));
                                        lores.set(i, lore);
                                    }
                                    meta.setLore(lores);
                                }

                                if (config.contains(PATH + ".CustomModelData")) {
                                    int modelData = config.getInt(PATH + ".CustomModelData");
                                    meta.setCustomModelData(modelData);
                                }
                            }))
                    .handleClick(event -> {
                        this.playSoundIfPresent();
                        this.reopenGui(page - 1);
                    }).setSlots(slots);

            this.addIcon(icon);
        }

        if (page < maxPages) {
            int[] slots = this.getSlots("ControlItems.Next");
            if ((slots == null) || (slots.length < 1))
                return;

            final String PATH = "ControlItems.Next";

            Icon icon = new Icon(
                    this.getItemStack(
                            PATH,
                            user,
                            meta -> {
                                String name = config.getString(PATH + ".Name", "");
                                if (!name.isEmpty())
                                    meta.setDisplayName(name);

                                List<String> lores = config.getStringList(PATH + ".Lore");
                                if (!lores.isEmpty()) {
                                    for (int i = 0; i < lores.size(); i++) {
                                        String lore = lores.get(i)
                                                .replaceAll(Utils.getRegex("page"), String.valueOf(page))
                                                .replaceAll(Utils.getRegex("max(\\_|\\-)?page(s)?"),
                                                        String.valueOf(maxPages));
                                        lores.set(i, lore);
                                    }
                                    meta.setLore(lores);
                                }

                                if (config.contains(PATH + ".CustomModelData")) {
                                    int modelData = config.getInt(PATH + ".CustomModelData");
                                    meta.setCustomModelData(modelData);
                                }
                            }))
                    .handleClick(event -> {
                        this.playSoundIfPresent();
                        this.reopenGui(page + 1);
                    }).setSlots(slots);

            this.addIcon(icon);
        }
    }
}