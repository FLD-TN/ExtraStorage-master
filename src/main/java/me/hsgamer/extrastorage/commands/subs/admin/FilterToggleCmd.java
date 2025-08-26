package me.hsgamer.extrastorage.commands.subs.admin;

import me.hsgamer.extrastorage.ExtraStorage;
import me.hsgamer.extrastorage.commands.abstraction.Command;
import me.hsgamer.extrastorage.commands.abstraction.CommandContext;
import me.hsgamer.extrastorage.commands.abstraction.CommandListener;
import me.hsgamer.extrastorage.configs.Message;
import me.hsgamer.extrastorage.data.Constants;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

@Command(value = { "filtertoggle", "ftoggle" }, permission = Constants.ADMIN_FILTER_TOGGLE_PERMISSION)
public final class FilterToggleCmd extends CommandListener {

    @Override
    public void execute(CommandContext context) {
        // args[0] = "filtertoggle", args[1] = true|false
        if (context.getArgsLength() < 2) {
            context.sendMessage(Message.getMessage("FAIL.missing-args").replaceAll(USAGE_REGEX,
                    "/" + context.getLabel() + " filtertoggle <true|false>"));
            return;
        }

        String arg = context.getArgs(1).toLowerCase();
        boolean newState;

        if (arg.equals("true") || arg.equals("on") || arg.equals("enable")) {
            newState = true;
        } else if (arg.equals("false") || arg.equals("off") || arg.equals("disable")) {
            newState = false;
        } else {
            context.sendMessage(Message.getMessage("FAIL.missing-args").replaceAll(USAGE_REGEX,
                    "/" + context.getLabel() + " filtertoggle <true|false>"));
            return;
        }

        // Đặt trạng thái mới cho bộ lọc
        ExtraStorage.setFilterEnabled(newState);

        // Gửi thông báo cho người dùng
        String status = newState ? Message.getMessage("STATUS.enabled") : Message.getMessage("STATUS.disabled");
        context.sendMessage(Message.getMessage("SUCCESS.filter-toggle-global").replace("{status}", status));

        // Thông báo cho tất cả người chơi online về việc thay đổi trạng thái
        if (context.getSender().hasPermission(Constants.ADMIN_BROADCAST_PERMISSION)) {
            String message = Message.getMessage("PREFIX") + "§7Quản trị viên §e" + context.getSender().getName() +
                    " §7đã " + (newState ? "§abật" : "§ctắt") + " §7bộ lọc toàn cục";

            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendMessage(message);
            }
        }
    }
}