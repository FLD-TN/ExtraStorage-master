package me.hsgamer.extrastorage.gui;

import me.hsgamer.extrastorage.api.item.Item;
import me.hsgamer.extrastorage.configs.Message;
import me.hsgamer.extrastorage.gui.base.ESGui;
import me.hsgamer.extrastorage.gui.icon.Icon;
import me.hsgamer.extrastorage.util.Digital;
import me.hsgamer.extrastorage.util.ItemUtil;
import me.hsgamer.extrastorage.util.Utils;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ConfirmUnfilterGui extends ESGui {
    private final String itemKey;
    private final Item itemObj;

    public ConfirmUnfilterGui(Player player, String itemKey) {
        super("gui/confirm_unfilter", player, 1);
        this.itemKey = itemKey;
        Optional<Item> optionalItem = storage.getItem(itemKey);
        this.itemObj = optionalItem.orElse(null);

        if (this.itemObj == null) {
            player.sendMessage(Message.getMessage("FAIL.item-not-found"));
            return;
        }

        setupGui();
    }

    private void setupGui() {
        // Thêm các nút và thông tin
        addInfoItem();
        addYesButton();
        addNoButton();
        addDecorateItems();
    }

    private void addInfoItem() {
        int[] slots = this.getSlots("InfoItem");
        if ((slots == null) || (slots.length < 1))
            return;

        ItemStack itemStack = itemObj.getItem().clone();
        ItemMeta meta = itemStack.getItemMeta();

        String name = config.getString("InfoItem.Name", "&fThông tin vật phẩm");
        if (!name.isEmpty()) {
            name = name.replace("%item_name%", instance.getSetting().getNameFormatted(itemKey, true));
            meta.setDisplayName(Utils.colorize(name));
        }

        List<String> curLore = (meta.hasLore() ? meta.getLore() : new ArrayList<>());
        List<String> newLore = config.getStringList("InfoItem.Lore");
        if (!newLore.isEmpty()) {
            for (int i = 0; i < newLore.size(); i++) {
                String lore = newLore.get(i)
                        .replace("%item_name%", instance.getSetting().getNameFormatted(itemKey, true))
                        .replace("%quantity%", Digital.formatThousands(itemObj.getQuantity()));
                newLore.set(i, Utils.colorize(lore));
            }
            curLore.addAll(newLore);
            meta.setLore(curLore);
        }

        itemStack.setItemMeta(meta);

        Icon icon = new Icon(itemStack).setSlots(slots);
        this.addIcon(icon);
    }

    private void addYesButton() {
        int[] slots = this.getSlots("YesItem");
        if ((slots == null) || (slots.length < 1))
            return;

        final String PATH = "YesItem";

        Icon icon = new Icon(
                this.getItemStack(
                        PATH,
                        user,
                        meta -> {
                            String name = config.getString(PATH + ".Name", "§aXác nhận xoá khỏi bộ lọc");
                            if (!name.isEmpty())
                                meta.setDisplayName(name);

                            List<String> lores = config.getStringList(PATH + ".Lore");
                            meta.setLore(lores);
                        }))
                .handleClick(event -> {
                    this.playSoundIfPresent();

                    // Thực hiện xoá khỏi bộ lọc
                    storage.unfilter(itemKey);

                    // Thông báo thành công và mở lại giao diện bộ lọc
                    player.sendMessage(Message.getMessage("SUCCESS.item-unfiltered").replace("{item}",
                            instance.getSetting().getNameFormatted(itemKey, true)));
                    new FilterGui(player, 1).open();
                }).setSlots(slots);

        this.addIcon(icon);
    }

    private void addNoButton() {
        int[] slots = this.getSlots("NoItem");
        if ((slots == null) || (slots.length < 1))
            return;

        final String PATH = "NoItem";

        Icon icon = new Icon(
                this.getItemStack(
                        PATH,
                        user,
                        meta -> {
                            String name = config.getString(PATH + ".Name", "§cHuỷ bỏ");
                            if (!name.isEmpty())
                                meta.setDisplayName(name);

                            List<String> lores = config.getStringList(PATH + ".Lore");
                            meta.setLore(lores);
                        }))
                .handleClick(event -> {
                    this.playSoundIfPresent();

                    // Quay lại giao diện bộ lọc
                    new FilterGui(player, 1).open();
                }).setSlots(slots);

        this.addIcon(icon);
    }

    @Override
    public void reopenGui(int page) {
        new ConfirmUnfilterGui(player, itemKey).open();
    }

    @Override
    public void reopenGui(int page, SortType sort, boolean order) {
        new ConfirmUnfilterGui(player, itemKey).open();
    }
}