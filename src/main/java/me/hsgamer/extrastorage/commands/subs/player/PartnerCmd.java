package me.hsgamer.extrastorage.commands.subs.player;

import me.hsgamer.extrastorage.api.user.Partner;
import me.hsgamer.extrastorage.api.user.User;
import me.hsgamer.extrastorage.commands.abstraction.Command;
import me.hsgamer.extrastorage.commands.abstraction.CommandContext;
import me.hsgamer.extrastorage.commands.abstraction.CommandListener;
import me.hsgamer.extrastorage.commands.abstraction.CommandTarget;
import me.hsgamer.extrastorage.configs.Message;
import me.hsgamer.extrastorage.data.Constants;
import me.hsgamer.extrastorage.data.user.UserManager;
import me.hsgamer.extrastorage.gui.PartnerGui;
import me.hsgamer.extrastorage.gui.StorageGui;
import me.hsgamer.extrastorage.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;

import java.util.Collection;

@Command(value = "partner", permission = Constants.PLAYER_PARTNER_PERMISSION, target = CommandTarget.ONLY_PLAYER)
@SuppressWarnings("deprecation")
public final class PartnerCmd
        extends CommandListener {

    private final UserManager manager;

    public PartnerCmd() {
        this.manager = instance.getUserManager();
    }

    @Override
    public void execute(CommandContext context) {
        Player player = context.castToPlayer();
        User user = manager.getUser(player);

        if (context.getArgsLength() == 0) {
            new PartnerGui(player, 1).open();
            return;
        }

        String args0 = context.getArgs(0).toLowerCase();
        OfflinePlayer target;
        User partner;
        switch (args0) {
            case "open":
                if (context.getArgsLength() == 1) {
                    context.sendMessage(Message.getMessage("FAIL.must-enter-player"));
                    return;
                }

                OfflinePlayer partnerPlayer = Bukkit.getServer().getOfflinePlayer(context.getArgs(1));
                User partnerUser = manager.getUser(partnerPlayer);
                if (partnerUser == null) {
                    context.sendMessage(Message.getMessage("FAIL.player-not-found"));
                    return;
                }
                if (partnerPlayer.getName().equals(player.getName())) {
                    context.sendMessage(Message.getMessage("FAIL.not-yourself"));
                    return;
                }
                if (!user.isPartner(partnerPlayer.getUniqueId())) {
                    context.sendMessage(
                            Message.getMessage("FAIL.player-not-partner").replaceAll(Utils.getRegex("player"),
                                    partnerPlayer.getName()));
                    return;
                }

                new StorageGui(player, partnerUser, 1).open();
                break;
            case "add":
                if (context.getArgsLength() == 1) {
                    context.sendMessage(Message.getMessage("FAIL.must-enter-player"));
                    return;
                }

                target = Bukkit.getServer().getOfflinePlayer(context.getArgs(1));
                partner = manager.getUser(target);
                if (partner == null) {
                    context.sendMessage(Message.getMessage("FAIL.player-not-found"));
                    return;
                }
                if (target.getName().equals(player.getName())) {
                    context.sendMessage(Message.getMessage("FAIL.not-yourself"));
                    return;
                }
                if (user.isPartner(target.getUniqueId())) {
                    context.sendMessage(Message.getMessage("FAIL.already-partner"));
                    return;
                }

                // Kiểm tra xem đã gửi lời mời cho người này chưa
                boolean hasPendingRequest = partner.hasPendingPartnerRequest(player.getName());
                System.out.println("[DEBUG] Checking if " + player.getName() + " already sent request to "
                        + target.getName() + ": " + hasPendingRequest);
                if (hasPendingRequest) {
                    context.sendMessage(Message.getMessage("FAIL.request-already-sent"));
                    return;
                }

                // Gửi yêu cầu kết bạn
                partner.addPendingPartnerRequest(player.getName());
                context.sendMessage(Message.getMessage("SUCCESS.request-sent").replaceAll(Utils.getRegex("player"),
                        target.getName()));
                if (target.isOnline())
                    target.getPlayer().sendMessage(Message.getMessage("INFO.partner-request-received")
                            .replaceAll(Utils.getRegex("player"), player.getName())
                            .replaceAll(Utils.getRegex("label"), context.getLabel()));
                break;
            case "remove":
                if (context.getArgsLength() == 1) {
                    context.sendMessage(Message.getMessage("FAIL.must-enter-player"));
                    return;
                }

                target = Bukkit.getServer().getOfflinePlayer(context.getArgs(1));
                partner = manager.getUser(target);
                if (partner == null) {
                    context.sendMessage(Message.getMessage("FAIL.player-not-found"));
                    return;
                }
                if (target.getName().equals(player.getName())) {
                    context.sendMessage(Message.getMessage("FAIL.not-yourself"));
                    return;
                }
                if (!user.isPartner(target.getUniqueId())) {
                    context.sendMessage(Message.getMessage("FAIL.not-partner"));
                    return;
                }

                // Xóa B từ danh sách đối tác của A
                user.removePartner(target.getUniqueId());

                // Đồng thời xóa A từ danh sách đối tác của B
                partner.removePartner(player.getUniqueId());
                partner.save(); // Đảm bảo lưu thay đổi

                context.sendMessage(Message.getMessage("SUCCESS.removed-partner").replaceAll(Utils.getRegex("player"),
                        target.getName()));
                if (target.isOnline()) {
                    Player p = target.getPlayer();
                    p.sendMessage(Message.getMessage("SUCCESS.no-longer-partner").replaceAll(Utils.getRegex("player"),
                            player.getName()));
                    InventoryHolder holder = p.getOpenInventory().getTopInventory().getHolder();
                    if (holder instanceof StorageGui) {
                        StorageGui gui = (StorageGui) holder;
                        if (gui.getPartner().getUUID().equals(player.getUniqueId()))
                            p.closeInventory();
                    }
                }

                break;
            case "accept":
                if (context.getArgsLength() == 1) {
                    context.sendMessage(Message.getMessage("FAIL.must-enter-player"));
                    return;
                }

                String senderName = context.getArgs(1);
                if (!user.hasPendingPartnerRequest(senderName)) {
                    context.sendMessage(Message.getMessage("FAIL.no-partner-request")
                            .replaceAll(Utils.getRegex("player"), senderName));
                    return;
                }

                OfflinePlayer requester = Bukkit.getOfflinePlayer(senderName);
                if (requester != null && requester.getUniqueId() != null) {
                    // Thêm người gửi vào danh sách đối tác của người nhận
                    user.addPartner(requester.getUniqueId());
                    user.removePendingPartnerRequest(senderName);
                    context.sendMessage(Message.getMessage("SUCCESS.accepted-partner-request")
                            .replaceAll(Utils.getRegex("player"), senderName));

                    // Thêm người nhận vào danh sách đối tác của người gửi
                    User requesterUser = manager.getUser(requester);
                    if (requesterUser != null) {
                        requesterUser.addPartner(player.getUniqueId());
                        // Đảm bảo lưu thay đổi
                        requesterUser.save();
                    }

                    if (requester.isOnline()) {
                        requester.getPlayer().sendMessage(Message.getMessage("SUCCESS.being-partner")
                                .replaceAll(Utils.getRegex("player"), player.getName())
                                .replaceAll(Utils.getRegex("label"), context.getLabel()));
                    }
                } else {
                    context.sendMessage(Message.getMessage("FAIL.player-not-found"));
                    user.removePendingPartnerRequest(senderName);
                }
                break;

            case "deny":
                if (context.getArgsLength() == 1) {
                    context.sendMessage(Message.getMessage("FAIL.must-enter-player"));
                    return;
                }

                String requesterName = context.getArgs(1);
                if (!user.hasPendingPartnerRequest(requesterName)) {
                    context.sendMessage(Message.getMessage("FAIL.no-partner-request")
                            .replaceAll(Utils.getRegex("player"), requesterName));
                    return;
                }

                user.removePendingPartnerRequest(requesterName);
                context.sendMessage(Message.getMessage("SUCCESS.denied-partner-request")
                        .replaceAll(Utils.getRegex("player"), requesterName));
                break;

            case "clear":
                Collection<Partner> partners = user.getPartners();
                if (partners.size() < 1) {
                    context.sendMessage(Message.getMessage("FAIL.partners-list-empty"));
                    return;
                }
                for (Partner pn : partners) {
                    OfflinePlayer offPlayer = pn.getOfflinePlayer();

                    // Cập nhật danh sách đối tác của người chơi khác
                    User otherUser = manager.getUser(offPlayer);
                    if (otherUser != null) {
                        otherUser.removePartner(player.getUniqueId());
                        otherUser.save(); // Đảm bảo lưu thay đổi
                    }

                    // Thông báo và đóng GUI nếu đang online
                    if (offPlayer.isOnline()) {
                        Player p = offPlayer.getPlayer();
                        p.sendMessage(
                                Message.getMessage("SUCCESS.no-longer-partner").replaceAll(Utils.getRegex("player"),
                                        player.getName()));
                        InventoryHolder holder = p.getOpenInventory().getTopInventory().getHolder();
                        if (holder instanceof StorageGui) {
                            StorageGui gui = (StorageGui) holder;
                            if (gui.getPartner().getUUID().equals(player.getUniqueId()))
                                p.closeInventory();
                        }
                    }
                }
                user.clearPartners();
                context.sendMessage(Message.getMessage("SUCCESS.cleanup-partners-list"));
                break;
        }
    }

}
