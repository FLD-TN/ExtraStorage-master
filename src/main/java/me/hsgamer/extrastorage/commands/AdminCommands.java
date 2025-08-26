package me.hsgamer.extrastorage.commands;

import me.hsgamer.extrastorage.commands.abstraction.Command;
import me.hsgamer.extrastorage.commands.abstraction.CommandContext;
import me.hsgamer.extrastorage.commands.abstraction.CommandListener;
import me.hsgamer.extrastorage.commands.subs.admin.*;
import me.hsgamer.extrastorage.configs.Message;
import me.hsgamer.extrastorage.data.Constants;
import org.bukkit.command.CommandSender;
import java.util.Arrays;

@Command(value = { "esadmin", "esa" }, permission = Constants.ADMIN_HELP_PERMISSION)
public class AdminCommands extends CommandListener {

    public AdminCommands() {
        add(new HelpCmd());
        add(new OpenCmd());
        add(new SpaceCmd());
        add(new AddSpaceCmd());
        add(new AddCmd());
        add(new SubtractCmd());
        add(new SetCmd());
        add(new ResetCmd());
        add(new WhitelistCmd());
        add(new ReloadCmd());
        add(new FilterToggleCmd());
        add(new AddRndCmd());
        add(new BlockToCmd());
        add(new OreToCmd());
    }

    @Override
    public boolean onCommand(CommandSender sender, org.bukkit.command.Command command, String label, String[] args) {
        if (args.length > 0) {
            String subCommand = args[0].toLowerCase();
            CommandListener listener = getCommand(subCommand);
            if (listener != null) {
                Command cmdAnnotation = listener.getClass().getAnnotation(Command.class);
                String[] subArgs = Arrays.copyOfRange(args, 1, args.length);

                if (subArgs.length < cmdAnnotation.minArgs()) {
                    String usage = cmdAnnotation.usage().replace("{label}", label + " " + subCommand);
                    sender.sendMessage(Message.getMessage("FAIL.missing-args").replace("{usage}", usage));
                    return true;
                }

                CommandContext subContext = me.hsgamer.extrastorage.commands.abstraction.ContextHelper
                        .createContext(sender, label, subArgs);
                listener.execute(subContext);
                return true;
            }
        }

        sendHelp(sender);
        return true;
    }

    @Override
    public void execute(CommandContext context) {
        sendHelp(context.getSender());
    }

    private void sendHelp(CommandSender sender) {
        String version = instance.getDescription().getVersion();
        String label = "esadmin";
        sender.sendMessage(Message.getMessage("HELP.header").replace("{version}", version));
        sender.sendMessage(Message.getMessage("HELP.Admin.help").replace("{label}", label));
        sender.sendMessage(Message.getMessage("HELP.Admin.open").replace("{label}", label));
        sender.sendMessage(Message.getMessage("HELP.Admin.space").replace("{label}", label));
        sender.sendMessage(Message.getMessage("HELP.Admin.addspace").replace("{label}", label));
        sender.sendMessage(Message.getMessage("HELP.Admin.add").replace("{label}", label));
        sender.sendMessage(Message.getMessage("HELP.Admin.addrnd").replace("{label}", label));
        sender.sendMessage(Message.getMessage("HELP.Admin.subtract").replace("{label}", label));
        sender.sendMessage(Message.getMessage("HELP.Admin.set").replace("{label}", label));
        sender.sendMessage(Message.getMessage("HELP.Admin.reset").replace("{label}", label));
        sender.sendMessage(Message.getMessage("HELP.Admin.whitelist").replace("{label}", label));
        sender.sendMessage(Message.getMessage("HELP.Admin.reload").replace("{label}", label));
        sender.sendMessage(Message.getMessage("HELP.Admin.filtertoggle").replace("{label}", label));
        sender.sendMessage(Message.getMessage("HELP.Admin.blockto").replace("{label}", label));
        sender.sendMessage(Message.getMessage("HELP.Admin.oreto").replace("{label}", label));
        sender.sendMessage(Message.getMessage("HELP.footer"));
    }
}