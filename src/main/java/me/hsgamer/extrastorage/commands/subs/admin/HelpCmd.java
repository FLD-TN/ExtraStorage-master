package me.hsgamer.extrastorage.commands.subs.admin;

import me.hsgamer.extrastorage.commands.abstraction.Command;
import me.hsgamer.extrastorage.commands.abstraction.CommandContext;
import me.hsgamer.extrastorage.commands.abstraction.CommandListener;
import me.hsgamer.extrastorage.configs.Message;
import me.hsgamer.extrastorage.data.Constants;
import org.bukkit.command.CommandSender;

@Command(value = { "_help" }, permission = Constants.ADMIN_HELP_PERMISSION)
public final class HelpCmd extends CommandListener {

    @Override
    public void execute(CommandContext context) {
        // Simplified help: only show usage
        context.sendMessage("§cUsage: /" + context.getLabel() + " <subcommand> [args]");
    }
}