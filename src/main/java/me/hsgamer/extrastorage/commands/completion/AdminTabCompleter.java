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
        if (args.length == 1) {
            return Arrays.asList("help", "open", "space", "addspace", "add", "subtract", "set", "reset", "whitelist",
                    "reload", "filtertoggle");
        } else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "open":
                case "space":
                    // Các lệnh chỉ nhận người chơi
                    List<String> players = new ArrayList<>();
                    players.addAll(
                            Bukkit.getOnlinePlayers().stream().map(p -> p.getName()).collect(Collectors.toList()));
                    return players;
                case "addspace":
                    // /esadmin addspace <amount> [player]
                    List<String> amounts = new ArrayList<>();
                    amounts.add("10");
                    amounts.add("50");
                    amounts.add("100");
                    amounts.add("500");
                    amounts.add("1000");
                    return amounts;
                case "add":
                case "subtract":
                case "set":
                    // /esadmin add/subtract/set <material-key> <amount> [player]
                    // Trả về danh sách material-keys
                    List<String> materials = new ArrayList<>(setting.getMaterialTypes().keySet());
                    return materials;
                case "reset":
                    // /esadmin reset <material-key|*> [player]
                    List<String> resetMaterials = new ArrayList<>(setting.getMaterialTypes().keySet());
                    resetMaterials.add("*");
                    return resetMaterials;
                case "whitelist":
                    return Arrays.asList("add", "remove", "clear");
                default:
                    return new ArrayList<>();
            }
        } else if (args.length == 3) {
            switch (args[0].toLowerCase()) {
                case "add":
                case "subtract":
                case "set":
                    // Gợi ý amount cho add/subtract/set
                    List<String> amounts = new ArrayList<>();
                    amounts.add("1");
                    amounts.add("5");
                    amounts.add("10");
                    amounts.add("64");
                    amounts.add("100");
                    return amounts;
                case "reset":
                case "addspace":
                    // Gợi ý player cho reset và addspace
                    List<String> players = new ArrayList<>();
                    players.addAll(
                            Bukkit.getOnlinePlayers().stream().map(p -> p.getName()).collect(Collectors.toList()));
                    return players;
                default:
                    return new ArrayList<>();
            }
        } else if (args.length == 4) {
            switch (args[0].toLowerCase()) {
                case "add":
                case "subtract":
                case "set":
                    // Gợi ý player cho tham số cuối cùng của add/subtract/set
                    List<String> players = new ArrayList<>();
                    players.addAll(
                            Bukkit.getOnlinePlayers().stream().map(p -> p.getName()).collect(Collectors.toList()));
                    return players;
                default:
                    return new ArrayList<>();
            }
        }
        return new ArrayList<>();
    }
}
