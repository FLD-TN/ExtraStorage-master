package me.hsgamer.extrastorage.commands.abstraction;

import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class CommandListener extends CommandBase implements CommandExecutor, TabCompleter {

    private final List<CommandListener> listeners;

    public CommandListener() {
        this.listeners = new ArrayList<>();
    }

    @Override
    public boolean onCommand(CommandSender sender, org.bukkit.command.Command command, String label, String[] args) {
        // Phương thức này là bắt buộc bởi CommandExecutor.
        // Các lớp lệnh chính sẽ ghi đè (override) nó với logic riêng.
        execute(ContextHelper.createContext(sender, label, args));
        return true;
    }

    protected void add(CommandListener listener) {
        if (!listener.getClass().isAnnotationPresent(Command.class))
            return;
        listeners.add(listener);
    }

    public CommandListener getCommand(String command) {
        return listeners.stream()
                .filter(lis -> Arrays.stream(lis.getClass().getAnnotation(Command.class).value())
                        .anyMatch(command::equalsIgnoreCase))
                .findFirst()
                .orElse(null);
    }

    public abstract void execute(CommandContext context);

    @Override
    public List<String> onTabComplete(CommandSender sender, org.bukkit.command.Command command, String label,
            String[] args) {
        return null; // Các lớp con sẽ tự triển khai
    }
}