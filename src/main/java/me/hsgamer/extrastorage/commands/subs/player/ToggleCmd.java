package me.hsgamer.extrastorage.commands.subs.player;

import me.hsgamer.extrastorage.Debug;
import me.hsgamer.extrastorage.api.storage.Storage;
import me.hsgamer.extrastorage.api.user.User;
import me.hsgamer.extrastorage.commands.abstraction.Command;
import me.hsgamer.extrastorage.commands.abstraction.CommandContext;
import me.hsgamer.extrastorage.commands.abstraction.CommandListener;
import me.hsgamer.extrastorage.commands.abstraction.CommandTarget;
import me.hsgamer.extrastorage.configs.Message;
import me.hsgamer.extrastorage.data.Constants;
import me.hsgamer.extrastorage.manager.StorageStatusManager;
import me.hsgamer.extrastorage.util.Utils;

import java.util.UUID;

import org.bukkit.entity.Player;

@Command(value = "toggle", permission = Constants.PLAYER_TOGGLE_PERMISSION, target = CommandTarget.ONLY_PLAYER)
public final class ToggleCmd
        extends CommandListener {

    @Override
    public void execute(CommandContext context) {
        Player player = context.castToPlayer();
        UUID playerId = player.getUniqueId();

        // Lấy và toggle trạng thái từ bộ nhớ
        boolean toggled = !StorageStatusManager.getInstance().getStatus(playerId);
        StorageStatusManager.getInstance().setStatus(playerId, toggled);

        context.sendMessage(Message.getMessage("SUCCESS.storage-usage-toggled")
                .replaceAll(Utils.getRegex("status"),
                        Message.getMessage("STATUS." + (toggled ? "enabled" : "disabled"))));

        Debug.log("[ToggleCmd] Storage status toggled to: " + toggled + " for player: " + player.getName());
    }

}
