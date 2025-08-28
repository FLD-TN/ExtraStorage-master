package me.hsgamer.extrastorage.gui;

import me.hsgamer.extrastorage.api.user.Partner;
import me.hsgamer.extrastorage.api.user.User;
import me.hsgamer.extrastorage.configs.Message;
import me.hsgamer.extrastorage.data.Constants;
import me.hsgamer.extrastorage.gui.base.ESGui;
import me.hsgamer.extrastorage.gui.icon.Icon;
import me.hsgamer.extrastorage.util.Utils;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

public final class PartnerGui
        extends ESGui {

    private List<Partner> partners;
    private int[] slots;

    private boolean confirm;

    private PartnerGui(Player player, int page, SortType sort, boolean order) {
        super("gui/partner", player, page, sort, order);

        if (player == null)
            return;

        this.partners = this.sortPartnerList(user.getPartners());
        this.slots = this.getSlots("RepresentItem");

        this.confirm = false;

        this.handleClick(event -> {
            if (!event.isTopClick())
                event.setCancelled(true);
        });

        this.load();
    }

    public PartnerGui(Player player, int page) {
        super("gui/partner", player, page);

        if (player == null)
            return;

        this.partners = this.sortPartnerList(user.getPartners());
        this.slots = this.getSlots("RepresentItem");

        this.confirm = false;

        this.handleClick(event -> {
            if (!event.isTopClick())
                event.setCancelled(true);
        });

        this.load();
    }

    @Override
    public void reopenGui(int page) {
        new PartnerGui(player, page, sort, orderSort).open();
    }

    @Override
    public void reopenGui(int page, SortType sort, boolean order) {
        new PartnerGui(player, page, sort, order).open();
    }

    private void load() {
        this.addDecorateItems();

        // Thêm nút mở GUI request partner ở slot 47
        ItemStack requestItem = new ItemStack(Material.BOOK);
        org.bukkit.inventory.meta.ItemMeta meta = requestItem.getItemMeta();
        meta.setDisplayName("§eYêu cầu kết bạn");
        java.util.List<String> lore = new java.util.ArrayList<>();
        lore.add("§7Click để xem các yêu cầu kết bạn đang chờ xác nhận");
        meta.setLore(lore);
        requestItem.setItemMeta(meta);
        Icon requestIcon = new Icon(requestItem)
                .handleClick(event -> {
                    new PartnerRequestGui(player).open();
                })
                .setSlots(new int[] { 47 });
        this.addIcon(requestIcon);

        switch (sort) {
            case NAME:
                this.addSortByName();
                break;
            case TIME:
                this.addSortByTime();
                break;
            case MATERIAL:
            case UNFILTER:
            case QUANTITY:
                // Không xử lý gì, hoặc thêm logic nếu cần
                break;
        }

        this.addSwitchButton();
        this.addRepresentItem();
        this.addAboutItem();
    }

    private void addRepresentItem() {
        final String PATH = "RepresentItem";

        int startIndex, endIndex;
        endIndex = Math.min(partners.size(), page * slots.length);
        int slotIdx = 0;
        for (startIndex = (page - 1) * slots.length; startIndex < endIndex; startIndex++, slotIdx++) {
            Partner partner = partners.get(startIndex);
            OfflinePlayer pnPlayer = partner.getOfflinePlayer();
            User user = instance.getUserManager().getUser(pnPlayer);

            String texture = config.getString("RepresentItem.Texture", "");
            ItemStack item = this.getItemStack(
                    config.getString(PATH + ".Model"),
                    this.user,
                    Material.matchMaterial(config.getString(PATH + ".Material")),
                    config.getInt(PATH + ".Amount"),
                    (short) config.getInt(PATH + ".Data"),
                    texture.matches(Utils.getRegex("partner")) ? user.getTexture() : texture,
                    config.getStringList(PATH + ".Enchantments"),
                    config.getStringList(PATH + ".HideFlags"),
                    meta -> {
                        String name = config.getString("RepresentItem.Name", "");
                        if (!name.isEmpty())
                            meta.setDisplayName(name.replaceAll(Utils.getRegex("partner"), pnPlayer.getName())
                                    .replaceAll(Utils.getRegex("time(stamp)?"), partner.getTimeFormatted()));

                        List<String> lores = new ArrayList<>();
                        lores.add("");
                        lores.add("§7+ Timestamp: §f" + partner.getTimeFormatted());
                        lores.add("");
                        lores.add("§8[§eShift-Click§8] §7Xoá đối tác này");
                        lores.add("§8[§bRight-Click§8] §7Mở kho của đối tác");
                        lores.add("§8[§aLeft-Click§8] §7Không có tác dụng");
                        meta.setLore(lores);

                        if (config.contains(PATH + ".CustomModelData")) {
                            int modelData = config.getInt("RepresentItem.CustomModelData");
                            meta.setCustomModelData(modelData);
                        }
                    });
            int[] representSlots = this.getSlots("RepresentItem");
            int slot = (representSlots != null && slotIdx < representSlots.length) ? representSlots[slotIdx] : -1;
            Icon icon = new Icon(item)
                    .handleClick(event -> {
                        this.playSoundIfPresent();
                        if (event.isShiftClick()) {
                            new ConfirmRemovePartnerGui(player, pnPlayer).open();
                        } else if (event.isRightClick()) {
                            new StorageGui(player, instance.getUserManager().getUser(pnPlayer), 1).open();
                        } else if (event.isLeftClick()) {
                            player.sendMessage("§eNhấn Shift để xoá, nhấn chuột phải để mở kho đối tác!");
                        }
                    })
                    .setSlots(new int[] { slot });
            this.addIcon(icon);
        }
        // Nếu không có partner, hiển thị icon trống
        if (partners.isEmpty()) {
            ItemStack emptyItem = new ItemStack(Material.BARRIER);
            org.bukkit.inventory.meta.ItemMeta emptyMeta = emptyItem.getItemMeta();
            emptyMeta.setDisplayName("§cKhông có đối tác nào");
            emptyItem.setItemMeta(emptyMeta);
            int[] representSlots = this.getSlots("RepresentItem");
            Icon emptyIcon = new Icon(emptyItem).setSlots(representSlots);
            this.addIcon(emptyIcon);
        }
        int maxPages = (int) Math.ceil((double) partners.size() / slots.length);

        if (page > 1)
            this.addPreviousButton(maxPages);
        if (page < maxPages)
            this.addNextButton(maxPages);
    }

    private void addAboutItem() {
        int[] slots = this.getSlots("ControlItems.About");
        if ((slots == null) || (slots.length < 1))
            return;

        final String PATH = "ControlItems.About";

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
                                    String lore = lores.get(i).replaceAll(Utils.getRegex("total(\\_|\\-)partners"),
                                            Integer.toString(partners.size()));
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
                    if (partners.isEmpty() || (!event.isShiftClick()))
                        return;

                    this.playSoundIfPresent();

                    if (!confirm) {
                        confirm = true;
                        player.sendMessage(Message.getMessage("WARN.confirm-cleanup"));
                        return;
                    }

                    for (Partner pn : partners) {
                        OfflinePlayer offPlayer = pn.getOfflinePlayer();
                        if (!offPlayer.isOnline())
                            continue;

                        Player p = offPlayer.getPlayer();
                        p.sendMessage(Message.getMessage("SUCCESS.no-longer-partner")
                                .replaceAll(Utils.getRegex("player"), player.getName()));
                        InventoryHolder holder = p.getOpenInventory().getTopInventory().getHolder();
                        if (holder instanceof StorageGui) {
                            StorageGui gui = (StorageGui) holder;
                            if (gui.getPartner().getUUID().equals(player.getUniqueId()))
                                p.closeInventory();
                        }
                    }
                    user.clearPartners();
                    player.sendMessage(Message.getMessage("SUCCESS.cleanup-partners-list"));

                    this.reopenGui(1);
                }).setSlots(slots);
        this.addIcon(icon);
    }

    private void addSortByName() {
        int[] slots = this.getSlots("ControlItems.SortByName");
        if ((slots == null) || (slots.length < 1))
            return;

        final String PATH = "ControlItems.SortByName";

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
                    if (partners.isEmpty())
                        return;

                    SortType newSort = (event.isShiftClick() ? sort : SortType.TIME);

                    this.playSoundIfPresent();
                    this.reopenGui(page, newSort, !event.isShiftClick());
                }).setSlots(slots);
        this.addIcon(icon);
    }

    private void addSortByTime() {
        int[] slots = this.getSlots("ControlItems.SortByTime");
        if ((slots == null) || (slots.length < 1))
            return;

        final String PATH = "ControlItems.SortByTime";

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
                    if (partners.isEmpty())
                        return;

                    SortType newSort = (event.isShiftClick() ? sort : SortType.NAME);

                    this.playSoundIfPresent();
                    this.reopenGui(page, newSort, !event.isShiftClick());
                }).setSlots(slots);
        this.addIcon(icon);
    }

    private void addSwitchButton() {
        int[] slots = this.getSlots("ControlItems.SwitchGui");
        if ((slots == null) || (slots.length < 1))
            return;

        final String PATH = "ControlItems.SwitchGui";

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

                    if (event.isLeftClick()) {
                        // Chuyển GUI theo chiều ngược (Partner → Filter → Storage → Sell)
                        if (this.hasPermission(Constants.PLAYER_FILTER_PERMISSION))
                            new FilterGui(player, 1).open();
                        else if (this.hasPermission(Constants.PLAYER_OPEN_PERMISSION))
                            new StorageGui(player, 1).open();
                        else if (this.hasPermission(Constants.PLAYER_SELL_PERMISSION))
                            new SellGui(player, 1).open();
                    } else if (event.isRightClick()) {
                        // Chuyển GUI theo chiều thuận (Partner → Sell → Storage → Filter)
                        if (this.hasPermission(Constants.PLAYER_SELL_PERMISSION))
                            new SellGui(player, 1).open();
                        else if (this.hasPermission(Constants.PLAYER_OPEN_PERMISSION))
                            new StorageGui(player, 1).open();
                        else if (this.hasPermission(Constants.PLAYER_FILTER_PERMISSION))
                            new FilterGui(player, 1).open();
                    }
                }).setSlots(slots);
        this.addIcon(icon);
    }

    private List<Partner> sortPartnerList(Collection<Partner> unsort) {
        if (unsort.isEmpty() || (unsort.size() < 2))
            return new ArrayList<>(unsort);
        List<Partner> entries = new LinkedList<>(unsort);
        entries.sort((obj1, obj2) -> {
            int compare = 0;
            OfflinePlayer p1 = obj1.getOfflinePlayer(), p2 = obj2.getOfflinePlayer();
            switch (sort) {
                case NAME:
                    if (orderSort) {
                        compare = p1.getName().compareTo(p2.getName());
                        if (compare == 0)
                            compare = Long.compare(obj2.getTimestamp(), obj1.getTimestamp());
                    } else {
                        compare = p2.getName().compareTo(p1.getName());
                        if (compare == 0)
                            compare = Long.compare(obj1.getTimestamp(), obj2.getTimestamp());
                    }
                    break;
                case TIME:
                    if (orderSort) {
                        compare = Long.compare(obj2.getTimestamp(), obj1.getTimestamp());
                        if (compare == 0)
                            compare = p1.getName().compareTo(p2.getName());
                    } else {
                        compare = Long.compare(obj1.getTimestamp(), obj2.getTimestamp());
                        if (compare == 0)
                            compare = p2.getName().compareTo(p1.getName());
                    }
                    break;
                case MATERIAL:
                case UNFILTER:
                case QUANTITY:
                    // Không xử lý gì, hoặc thêm logic nếu cần
                    break;
            }
            return compare;
        });
        return entries;
    }

}