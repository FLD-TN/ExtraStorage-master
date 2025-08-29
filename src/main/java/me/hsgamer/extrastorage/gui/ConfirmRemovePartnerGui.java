package me.hsgamer.extrastorage.gui;

import me.hsgamer.extrastorage.api.user.User;
import me.hsgamer.extrastorage.gui.base.ESGui;
import me.hsgamer.extrastorage.gui.icon.Icon;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;

public class ConfirmRemovePartnerGui extends ESGui {
    public ConfirmRemovePartnerGui(Player player, OfflinePlayer partner) {
        super("gui/confirm_remove_partner", player, 1);
        User user = instance.getUserManager().getUser(player);
        User partnerUser = instance.getUserManager().getUser(partner);

        // Nút xác nhận xoá
        ItemStack yesItem = new ItemStack(Material.GREEN_WOOL);
        org.bukkit.inventory.meta.ItemMeta yesMeta = yesItem.getItemMeta();
        yesMeta.setDisplayName("§aXác nhận xoá partner: " + partner.getName());
        yesItem.setItemMeta(yesMeta);
        Icon yesIcon = new Icon(yesItem)
                .handleClick(event -> {
                    user.removePartner(partner.getUniqueId());
                    partnerUser.removePartner(player.getUniqueId());

                    // Đảm bảo lưu thay đổi
                    user.save();
                    partnerUser.save();

                    player.sendMessage("§aĐã xoá partner " + partner.getName());
                    new PartnerGui(player, 1).open();
                });
        this.addIcon(yesIcon);

        // Nút huỷ
        ItemStack noItem = new ItemStack(Material.RED_WOOL);
        org.bukkit.inventory.meta.ItemMeta noMeta = noItem.getItemMeta();
        noMeta.setDisplayName("§cHuỷ xoá partner");
        noItem.setItemMeta(noMeta);
        Icon noIcon = new Icon(noItem)
                .handleClick(event -> {
                    new PartnerGui(player, 1).open();
                });
        this.addIcon(noIcon);
    }

    @Override
    public void reopenGui(int page) {
        new ConfirmRemovePartnerGui(player, null).open();
    }

    @Override
    public void reopenGui(int page, SortType sort, boolean order) {
        new ConfirmRemovePartnerGui(player, null).open();
    }
}
