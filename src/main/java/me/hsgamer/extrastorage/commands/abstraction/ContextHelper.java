package me.hsgamer.extrastorage.commands.abstraction;

import org.bukkit.command.CommandSender;

/**
 * Helper class to create CommandContext objects, since the constructor is
 * package-private
 */
public class ContextHelper {
    /**
     * Create a new CommandContext object
     * 
     * @param sender the command sender
     * @param label  the command label
     * @param args   the command arguments
     * @return a new CommandContext object
     */
    public static CommandContext createContext(CommandSender sender, String label, String[] args) {
        return new CommandContext(sender, label, args);
    }
}
