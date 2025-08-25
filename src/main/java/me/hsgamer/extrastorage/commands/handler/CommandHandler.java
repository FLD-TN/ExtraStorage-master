package me.hsgamer.extrastorage.commands.handler;

import me.hsgamer.extrastorage.commands.abstraction.AbstractCommand;
import me.hsgamer.extrastorage.commands.abstraction.Command;
import me.hsgamer.extrastorage.commands.abstraction.CommandContext;
import me.hsgamer.extrastorage.configs.Message;
import me.hsgamer.extrastorage.commands.abstraction.CommandTarget;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

public final class CommandHandler extends AbstractCommand {

    @Override
    public void execute(CommandContext context) {
        CommandSender sender = context.getSender();
        String label = context.getLabel();

        Command cmd = this.getClass().getAnnotation(Command.class);
        if (!check(sender, cmd, label, new String[context.getArgsLength()])) {
            return;
        }
    }

    @Override
    public boolean check(CommandSender sender, Command cmd, String label, String[] args) {
        if (!cmd.permission().isEmpty() && !sender.hasPermission(cmd.permission())) {
            sender.sendMessage(Message.NO_PERMISSION);
            return false;
        }

        if (args.length < cmd.minArgs()) {
            if (!cmd.usage().isEmpty()) {
                sender.sendMessage(Message.INVALID_ARGS);
                sender.sendMessage("§fUsage: " + cmd.usage().replaceAll(LABEL_REGEX, label));
            } else {
                sender.sendMessage(Message.INVALID_ARGS);
            }
            return false;
        }

        switch (cmd.target()) {
            case ONLY_PLAYER:
                if (!(sender instanceof Player)) {
                    sender.sendMessage(Message.PLAYER_ONLY);
                    return false;
                }
                break;
            case ONLY_CONSOLE:
                if (!(sender instanceof ConsoleCommandSender)) {
                    sender.sendMessage(Message.PLAYER_ONLY);
                    return false;
                }
                break;
            case BOTH:
                break;
        }

        return true;
    }
}
