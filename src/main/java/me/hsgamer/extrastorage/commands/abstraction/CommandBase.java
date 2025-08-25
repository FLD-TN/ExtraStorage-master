package me.hsgamer.extrastorage.commands.abstraction;

import me.hsgamer.extrastorage.ExtraStorage;
import me.hsgamer.extrastorage.util.Utils;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

abstract class CommandBase implements CommandExecutor {

    protected final ExtraStorage instance;
    protected final String VERSION_REGEX, LABEL_REGEX, USAGE_REGEX, VALUE_REGEX;

    CommandBase() {
        this.instance = ExtraStorage.getInstance();

        this.VERSION_REGEX = Utils.getRegex("ver(sion)?");
        this.LABEL_REGEX = Utils.getRegex("label");
        this.USAGE_REGEX = Utils.getRegex("usage");
        this.VALUE_REGEX = Utils.getRegex("value");
    }

    @Override
    public boolean onCommand(CommandSender sender, org.bukkit.command.Command command, String label, String[] args) {
        execute(new CommandContext(sender, label, args));
        return true;
    }

    public abstract void execute(CommandContext context);

    protected final boolean hasPermission(CommandSender sender, String perm) {
        return (sender.isOp() || sender.hasPermission(perm));
    }

}
