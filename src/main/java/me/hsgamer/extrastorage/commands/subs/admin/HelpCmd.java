package me.hsgamer.extrastorage.commands.subs.admin;

import me.hsgamer.extrastorage.commands.abstraction.Command;
import me.hsgamer.extrastorage.commands.abstraction.CommandContext;
import me.hsgamer.extrastorage.commands.abstraction.CommandListener;
import me.hsgamer.extrastorage.configs.Message;
import me.hsgamer.extrastorage.data.Constants;
import org.bukkit.command.CommandSender;

@Command(value = { "help", "?" }, permission = Constants.ADMIN_HELP_PERMISSION)
public final class HelpCmd extends CommandListener {

    @Override
    public void execute(CommandContext context) {
        sendHelp(context.getSender());
    }

    private void sendHelp(CommandSender sender) {
        String version = instance.getDescription().getVersion();
        String label = "esadmin"; // Mặc định label cho esadmin

        sender.sendMessage(Message.getMessage("HELP.header").replace("{version}", version));
        sender.sendMessage(Message.getMessage("HELP.Admin.help").replace("{label}", label));
        sender.sendMessage(Message.getMessage("HELP.Admin.open").replace("{label}", label));
        sender.sendMessage(Message.getMessage("HELP.Admin.space").replace("{label}", label));
        sender.sendMessage(Message.getMessage("HELP.Admin.addspace").replace("{label}", label));
        sender.sendMessage(Message.getMessage("HELP.Admin.add").replace("{label}", label));
        sender.sendMessage(Message.getMessage("HELP.Admin.subtract").replace("{label}", label));
        sender.sendMessage(Message.getMessage("HELP.Admin.set").replace("{label}", label));
        sender.sendMessage(Message.getMessage("HELP.Admin.reset").replace("{label}", label));
        sender.sendMessage(Message.getMessage("HELP.Admin.whitelist").replace("{label}", label));
        sender.sendMessage(Message.getMessage("HELP.Admin.reload").replace("{label}", label));

        // Thêm thông tin về lệnh filtertoggle
        if (sender.hasPermission(Constants.ADMIN_FILTER_TOGGLE_PERMISSION)) {
            sender.sendMessage(Message.getMessage("HELP.Admin.filtertoggle").replace("{label}", label));
        }
    }
}
