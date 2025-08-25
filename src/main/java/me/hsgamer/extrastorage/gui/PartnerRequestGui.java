package me.hsgamer.extrastorage.gui;

import me.hsgamer.extrastorage.api.user.User;
import me.hsgamer.extrastorage.gui.base.ESGui;
import me.hsgamer.extrastorage.gui.icon.Icon;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.List;

public class PartnerRequestGui extends ESGui {
    public PartnerRequestGui(Player player) {
        super("gui/partner_request", player, 1);
        User user = instance.getUserManager().getUser(player);
        List<String> requests = new java.util.ArrayList<>(user.getPendingPartnerRequests());
        int slot = 0;
        for (String name : requests) {
            ItemStack item = new ItemStack(Material.PAPER);
            org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
            meta.setDisplayName("§eYêu cầu từ: " + name);
            java.util.List<String> lore = new java.util.ArrayList<>();
            lore.add("§aClick trái để chấp nhận");
            lore.add("§cClick phải để từ chối");
            meta.setLore(lore);
            item.setItemMeta(meta);
            Icon icon = new Icon(item)
                    .handleClick(event -> {
                        if (event.isLeftClick()) {
                            // Lấy UUID từ username
                            org.bukkit.OfflinePlayer offline = org.bukkit.Bukkit.getOfflinePlayer(name);
                            if (offline != null && offline.getUniqueId() != null) {
                                user.addPartner(offline.getUniqueId());
                                user.removePendingPartnerRequest(name);
                                player.sendMessage("§aĐã chấp nhận yêu cầu kết bạn từ " + name);
                            } else {
                                player.sendMessage("§cKhông tìm thấy player " + name);
                            }
                            this.reopenGui(1);
                        } else if (event.isRightClick()) {
                            user.removePendingPartnerRequest(name);
                            player.sendMessage("§cĐã từ chối yêu cầu kết bạn từ " + name);
                            this.reopenGui(1);
                        }
                    });
            this.addIcon(icon);
        }
    }

    @Override
    public void reopenGui(int page) {
        new PartnerRequestGui(player).open();
    }

    @Override
    public void reopenGui(int page, SortType sort, boolean order) {
        new PartnerRequestGui(player).open();
    }
}
