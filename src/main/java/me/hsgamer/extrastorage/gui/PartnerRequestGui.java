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
                                // Thêm người gửi vào danh sách đối tác của người nhận
                                user.addPartner(offline.getUniqueId());
                                user.removePendingPartnerRequest(name);
                                player.sendMessage(me.hsgamer.extrastorage.configs.Message
                                        .getMessage("SUCCESS.accepted-partner-request")
                                        .replaceAll(me.hsgamer.extrastorage.util.Utils.getRegex("player"), name));

                                // Thêm người nhận vào danh sách đối tác của người gửi
                                me.hsgamer.extrastorage.api.user.User senderUser = instance.getUserManager()
                                        .getUser(offline);
                                if (senderUser != null) {
                                    senderUser.addPartner(player.getUniqueId());
                                    // Đảm bảo lưu thay đổi
                                    senderUser.save();
                                }

                                // Thông báo cho người gửi nếu họ trực tuyến
                                if (offline.isOnline()) {
                                    offline.getPlayer().sendMessage(me.hsgamer.extrastorage.configs.Message
                                            .getMessage("SUCCESS.being-partner")
                                            .replaceAll(me.hsgamer.extrastorage.util.Utils.getRegex("player"),
                                                    player.getName())
                                            .replaceAll(me.hsgamer.extrastorage.util.Utils.getRegex("label"), "kho"));
                                }
                            } else {
                                player.sendMessage(
                                        me.hsgamer.extrastorage.configs.Message.getMessage("FAIL.player-not-found"));
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
