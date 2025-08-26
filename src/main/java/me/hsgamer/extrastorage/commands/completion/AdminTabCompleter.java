package me.hsgamer.extrastorage.commands.completion;

import me.hsgamer.extrastorage.ExtraStorage;
import me.hsgamer.extrastorage.configs.Setting;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class AdminTabCompleter implements TabCompleter {
    private final ExtraStorage plugin;
    private final Setting setting;

    public AdminTabCompleter(ExtraStorage plugin) {
        this.plugin = plugin;
        this.setting = plugin.getSetting();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String input = args[args.length - 1].toLowerCase();

        if (args.length == 1) {
            return Arrays
                    .asList("help", "open", "space", "addspace", "add", "addrnd", "subtract", "set", "reset",
                            "whitelist", "reload", "filtertoggle", "blockto", "oreto")
                    .stream().filter(s -> s.startsWith(input)).collect(Collectors.toList());
        }

        String subCommand = args[0].toLowerCase();
        if (args.length == 2) {
            switch (subCommand) {
                case "open":
                case "space":
                    return Bukkit.getOnlinePlayers().stream().map(p -> p.getName())
                            .filter(s -> s.toLowerCase().startsWith(input)).collect(Collectors.toList());
                case "add":
                case "subtract":
                case "set":
                case "reset":
                case "addrnd":
                case "blockto":
                case "oreto":
                    List<String> materials = new ArrayList<>(setting.getMaterialTypes().keySet());
                    if ("reset".equals(subCommand) || "addrnd".equals(subCommand)) {
                        materials.add("*");
                    }
                    return materials.stream().filter(s -> s.toLowerCase().startsWith(input))
                            .collect(Collectors.toList());
                case "whitelist":
                    return Arrays.asList("add", "remove", "clear").stream().filter(s -> s.startsWith(input))
                            .collect(Collectors.toList());
                default:
                    return new ArrayList<>();
            }
        }

        if (args.length == 3) {
            switch (subCommand) {
                case "addspace":
                case "reset":
                case "addrnd":
                case "blockto":
                case "oreto":
                    return Bukkit.getOnlinePlayers().stream().map(p -> p.getName())
                            .filter(s -> s.toLowerCase().startsWith(input)).collect(Collectors.toList());
                case "add":
                case "subtract":
                case "set":
                    return Arrays.asList("1", "10", "64", "100", "1000").stream().filter(s -> s.startsWith(input))
                            .collect(Collectors.toList());
                default:
                    return new ArrayList<>();
            }
        }

        if (args.length == 4) {
            switch (subCommand) {
                case "add":
                case "subtract":
                case "set":
                    return Bukkit.getOnlinePlayers().stream().map(p -> p.getName())
                            .filter(s -> s.toLowerCase().startsWith(input)).collect(Collectors.toList());
                default:
                    return new ArrayList<>();
            }
        }

        return new ArrayList<>();
    }
}