package me.hsgamer.extrastorage.commands.subs.admin;

import me.hsgamer.extrastorage.api.item.Item;
import me.hsgamer.extrastorage.api.storage.Storage;
import me.hsgamer.extrastorage.api.user.User;
import me.hsgamer.extrastorage.commands.abstraction.Command;
import me.hsgamer.extrastorage.commands.abstraction.CommandContext;
import me.hsgamer.extrastorage.commands.abstraction.CommandListener;
import me.hsgamer.extrastorage.configs.Message;
import me.hsgamer.extrastorage.data.Constants;
import me.hsgamer.extrastorage.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Command(value = "blockto", usage = "/{label} blockto <material-key> <player>", permission = Constants.ADMIN_BLOCKTO_PERMISSION, minArgs = 2)
public final class BlockToCmd extends CommandListener {

    private static final Map<Material, Material> ORE_TO_BLOCK_MAP = new HashMap<>();
    private final String PLAYER_REGEX, ITEM_REGEX, QUANTITY_REGEX, VALUE_REGEX;

    static {
        ORE_TO_BLOCK_MAP.put(Material.COAL, Material.COAL_BLOCK);
        ORE_TO_BLOCK_MAP.put(Material.IRON_INGOT, Material.IRON_BLOCK);
        ORE_TO_BLOCK_MAP.put(Material.GOLD_INGOT, Material.GOLD_BLOCK);
        ORE_TO_BLOCK_MAP.put(Material.DIAMOND, Material.DIAMOND_BLOCK);
        ORE_TO_BLOCK_MAP.put(Material.EMERALD, Material.EMERALD_BLOCK);
        ORE_TO_BLOCK_MAP.put(Material.REDSTONE, Material.REDSTONE_BLOCK);
        ORE_TO_BLOCK_MAP.put(Material.LAPIS_LAZULI, Material.LAPIS_BLOCK);
    }

    public BlockToCmd() {
        this.PLAYER_REGEX = Utils.getRegex("player");
        this.ITEM_REGEX = Utils.getRegex("item");
        this.QUANTITY_REGEX = Utils.getRegex("quantity");
        this.VALUE_REGEX = Utils.getRegex("value");
    }

    @Override
    public void execute(CommandContext context) {
        String oreKey = context.getArgs(0).toUpperCase();
        String playerName = context.getArgs(1);

        Material oreMaterial;
        try {
            oreMaterial = Material.valueOf(oreKey);
        } catch (IllegalArgumentException e) {
            context.sendMessage(Message.getMessage("FAIL.invalid-material").replace(VALUE_REGEX, oreKey));
            return;
        }

        if (!ORE_TO_BLOCK_MAP.containsKey(oreMaterial)) {
            context.sendMessage(
                    Message.getMessage("FAIL.not-convertible-to-block").replace(ITEM_REGEX, oreMaterial.name()));
            return;
        }
        Material blockMaterial = ORE_TO_BLOCK_MAP.get(oreMaterial);

        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(playerName);
        User targetUser = instance.getUserManager().getUser(targetPlayer);
        if (targetUser == null) {
            context.sendMessage(Message.getMessage("FAIL.player-not-found"));
            return;
        }

        Storage storage = targetUser.getStorage();
        String oreMaterialKey = oreMaterial.name();
        String blockMaterialKey = blockMaterial.name();

        Optional<Item> oreItemOptional = storage.getItem(oreMaterialKey);
        long currentOreAmount = oreItemOptional.map(Item::getQuantity).orElse(0L);
        
        // Check if the item has filter disabled
        if (oreItemOptional.isPresent() && !oreItemOptional.get().isFiltered()) {
            context.sendMessage(Message.getMessage("FAIL.item-filter-disabled")
                    .replaceAll(PLAYER_REGEX, targetPlayer.getName())
                    .replaceAll(ITEM_REGEX, oreMaterial.name()));
            return;
        }

        if (currentOreAmount < 9) {
            context.sendMessage(Message.getMessage("FAIL.not-enough-to-convert")
                    .replaceAll(PLAYER_REGEX, targetPlayer.getName())
                    .replaceAll(ITEM_REGEX, oreMaterial.name())
                    .replaceAll(QUANTITY_REGEX, String.valueOf(currentOreAmount)));
            return;
        }

        long blocksToCraft = currentOreAmount / 9;
        long oreToRemove = blocksToCraft * 9;

        storage.subtract(oreMaterialKey, oreToRemove);
        storage.add(blockMaterialKey, blocksToCraft);

        context.sendMessage(Message.getMessage("SUCCESS.BlockTo.sender")
                .replaceAll(QUANTITY_REGEX, String.valueOf(oreToRemove))
                .replaceAll(ITEM_REGEX, oreMaterial.name())
                .replaceAll(Utils.getRegex("blocks"), String.valueOf(blocksToCraft))
                .replaceAll(Utils.getRegex("block_item"), blockMaterial.name())
                .replaceAll(PLAYER_REGEX, targetPlayer.getName()));

        if (targetPlayer.isOnline()) {
            targetPlayer.getPlayer().sendMessage(Message.getMessage("SUCCESS.BlockTo.target"));
        } else {
            targetUser.save();
        }
    }
}