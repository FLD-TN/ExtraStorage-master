package me.hsgamer.extrastorage.commands;

import me.hsgamer.extrastorage.api.item.Item;
import me.hsgamer.extrastorage.api.user.User;
import me.hsgamer.extrastorage.commands.abstraction.*;
import me.hsgamer.extrastorage.commands.subs.player.*;
import me.hsgamer.extrastorage.configs.Message;
import me.hsgamer.extrastorage.data.Constants;
import me.hsgamer.extrastorage.gui.StorageGui;
import me.hsgamer.extrastorage.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Command(value = "extrastorage", permission = Constants.PLAYER_OPEN_PERMISSION, target = CommandTarget.ONLY_PLAYER)
public final class PlayerCommands extends CommandListener {

    public PlayerCommands() {
        this.add(new HelpCmd());
        this.add(new ToggleCmd());
        this.add(new FilterCmd());
        this.add(new PartnerCmd());
        this.add(new SellCmd());
        this.add(new WithdrawCmd());
    }

    @Override
    public boolean onCommand(CommandSender sender, org.bukkit.command.Command command, String label, String[] args) {
        execute(ContextHelper.createContext(sender, label, args));
        return true;
    }

    @Override
    public void execute(CommandContext context) {
        Player player = context.castToPlayer();

        if (context.getArgsLength() == 0) {
            new StorageGui(player, 1).open();
            return;
        }

        String subCommand = context.getArgs(0).toLowerCase();
        CommandListener listener = this.getCommand(subCommand);

        if (listener != null) {
            Command cmdAnnotation = listener.getClass().getAnnotation(Command.class);
            if (!context.hasPermission(cmdAnnotation.permission())) {
                context.sendMessage(Message.getMessage("FAIL.no-permission"));
                return;
            }

            String[] subArgs = Arrays.copyOfRange(context.getArguments(), 1, context.getArgsLength());

            if (subArgs.length < cmdAnnotation.minArgs()) {
                String usage = cmdAnnotation.usage().replace("{label}", context.getLabel() + " " + subCommand);
                context.sendMessage(Message.getMessage("FAIL.missing-args").replace("{usage}", usage));
                return;
            }

            CommandContext subContext = ContextHelper.createContext(context.getSender(), context.getLabel(), subArgs);
            listener.execute(subContext);
            return;
        }

        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getServer().getOfflinePlayer(subCommand);
        User user = instance.getUserManager().getUser(target);
        if (user == null) {
            context.sendMessage(Message.getMessage("FAIL.player-not-found"));
            return;
        }

        if (target.getName().equals(player.getName())) {
            context.sendMessage(Message.getMessage("FAIL.not-yourself"));
            return;
        }

        if (!user.isPartner(player.getUniqueId())) {
            context.sendMessage(Message.getMessage("FAIL.player-not-partner").replaceAll(Utils.getRegex("player"),
                    target.getName()));
            return;
        }

        new StorageGui(player, user, 1).open();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, org.bukkit.command.Command command, String label,
            String[] args) {
        if (!(sender instanceof Player))
            return null;
        Player player = (Player) sender;

        List<String> cmds = Arrays.asList(
                this.hasPermission(sender, Constants.PLAYER_HELP_PERMISSION) ? "help" : "",
                this.hasPermission(sender, Constants.PLAYER_TOGGLE_PERMISSION) ? "toggle" : "",
                this.hasPermission(sender, Constants.PLAYER_FILTER_PERMISSION) ? "filter" : "",
                this.hasPermission(sender, Constants.PLAYER_PARTNER_PERMISSION) ? "partner" : "",
                this.hasPermission(sender, Constants.PLAYER_SELL_PERMISSION) ? "sell" : "",
                this.hasPermission(sender, Constants.PLAYER_WITHDRAW_PERMISSION) ? "withdraw" : "");

        String args0 = args[0].toLowerCase();
        if (args.length == 1)
            return cmds.stream().filter(s -> !s.isEmpty() && s.startsWith(args0)).collect(Collectors.toList());

        User user = instance.getUserManager().getUser(player);

        String args1 = args[1].toLowerCase();
        if (args.length == 2) {
            switch (args0) {
                case "partner":
                    return Stream.of("add", "remove", "clear")
                            .filter(s -> s.startsWith(args1))
                            .collect(Collectors.toList());
                case "withdraw":
                case "sell":
                    return user.getStorage()
                            .getItems()
                            .values()
                            .stream()
                            .filter(item -> (item.getKey().toLowerCase().startsWith(args1)) && (item.getQuantity() > 0))
                            .map(Item::getKey)
                            .collect(Collectors.toList());
                default:
                    return null;
            }
        }

        return null;
    }
}