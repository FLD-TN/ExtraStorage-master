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

@Command(value = "oreto", usage = "/{label} oreto <material-key> <player>", permission = Constants.ADMIN_ORETO_PERMISSION, minArgs = 2)
public final class OreToCmd extends CommandListener {

    private static final Map<Material, Material> BLOCK_TO_ORE_MAP = new HashMap<>();
    private final String PLAYER_REGEX, ITEM_REGEX, QUANTITY_REGEX, VALUE_REGEX;

    static {
        BLOCK_TO_ORE_MAP.put(Material.COAL_BLOCK, Material.COAL);
        BLOCK_TO_ORE_MAP.put(Material.IRON_BLOCK, Material.IRON_INGOT);
        BLOCK_TO_ORE_MAP.put(Material.GOLD_BLOCK, Material.GOLD_INGOT);
        BLOCK_TO_ORE_MAP.put(Material.DIAMOND_BLOCK, Material.DIAMOND);
        BLOCK_TO_ORE_MAP.put(Material.EMERALD_BLOCK, Material.EMERALD);
        BLOCK_TO_ORE_MAP.put(Material.REDSTONE_BLOCK, Material.REDSTONE);
        BLOCK_TO_ORE_MAP.put(Material.LAPIS_BLOCK, Material.LAPIS_LAZULI);
    }

    public OreToCmd() {
        this.PLAYER_REGEX = Utils.getRegex("player");
        this.ITEM_REGEX = Utils.getRegex("item");
        this.QUANTITY_REGEX = Utils.getRegex("quantity");
        this.VALUE_REGEX = Utils.getRegex("value");
    }

    @Override
    public void execute(CommandContext context) {
        String blockKey = context.getArgs(0).toUpperCase();
        String playerName = context.getArgs(1);

        Material blockMaterial;
        try {
            blockMaterial = Material.valueOf(blockKey);
        } catch (IllegalArgumentException e) {
            context.sendMessage(Message.getMessage("FAIL.invalid-material").replace(VALUE_REGEX, blockKey));
            return;
        }

        if (!BLOCK_TO_ORE_MAP.containsKey(blockMaterial)) {
            context.sendMessage(
                    Message.getMessage("FAIL.not-convertible-to-ore").replace(ITEM_REGEX, blockMaterial.name()));
            return;
        }
        Material oreMaterial = BLOCK_TO_ORE_MAP.get(blockMaterial);

        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(playerName);
        User targetUser = instance.getUserManager().getUser(targetPlayer);
        if (targetUser == null) {
            context.sendMessage(Message.getMessage("FAIL.player-not-found"));
            return;
        }

        Storage storage = targetUser.getStorage();
        String blockMaterialKey = blockMaterial.name();
        String oreMaterialKey = oreMaterial.name();

        Optional<Item> blockItemOptional = storage.getItem(blockMaterialKey);

        // Nếu item không tồn tại hoặc filter đã tắt, không cho phép chuyển đổi
        if (!blockItemOptional.isPresent() || !blockItemOptional.get().isFiltered()) {
            context.sendMessage(Message.getMessage("FAIL.item-filter-disabled")
                    .replaceAll(PLAYER_REGEX, targetPlayer.getName())
                    .replaceAll(ITEM_REGEX, blockMaterial.name()));
            return;
        }

        // Lấy số lượng hiện tại
        long currentBlockAmount = blockItemOptional.get().getQuantity();

        if (currentBlockAmount < 1) {
            context.sendMessage(Message.getMessage("FAIL.not-enough-to-convert")
                    .replaceAll(PLAYER_REGEX, targetPlayer.getName())
                    .replaceAll(ITEM_REGEX, blockMaterial.name())
                    .replaceAll(QUANTITY_REGEX, String.valueOf(currentBlockAmount)));
            return;
        }

        // Thêm kiểm tra bổ sung để đảm bảo số lượng vật phẩm
        if (currentBlockAmount <= 0) {
            context.sendMessage(Message.getMessage("FAIL.not-enough-to-convert")
                    .replaceAll(PLAYER_REGEX, targetPlayer.getName())
                    .replaceAll(ITEM_REGEX, blockMaterial.name())
                    .replaceAll(QUANTITY_REGEX, "0"));
            return;
        }

        long blocksToRemove = currentBlockAmount;
        long oreToAdd = blocksToRemove * 9;

        // Kiểm tra một lần nữa xem vật phẩm có tồn tại và số lượng có đúng không
        Optional<Item> checkItem = storage.getItem(blockMaterialKey);
        if (!checkItem.isPresent() || !checkItem.get().isFiltered() || checkItem.get().getQuantity() < blocksToRemove) {
            context.sendMessage(Message.getMessage("FAIL.not-enough-to-convert")
                    .replaceAll(PLAYER_REGEX, targetPlayer.getName())
                    .replaceAll(ITEM_REGEX, blockMaterial.name())
                    .replaceAll(QUANTITY_REGEX, String.valueOf(currentBlockAmount)));
            return;
        }

        // DEBUG: Log trước khi chuyển đổi
        instance.getLogger().info("[OreTo] Converting " + blocksToRemove + " " + blockMaterialKey + " to " + oreToAdd
                + " " + oreMaterialKey + " for " + playerName);

        storage.subtract(blockMaterialKey, blocksToRemove);

        // Kiểm tra sau khi subtract
        Optional<Item> afterSubtract = storage.getItem(blockMaterialKey);
        instance.getLogger().info("[OreTo] After subtract - " + blockMaterialKey + ": "
                + (afterSubtract.isPresent() ? afterSubtract.get().getQuantity() : 0));

        storage.add(oreMaterialKey, oreToAdd);

        // Kiểm tra sau khi add và verify ore được thêm vào
        Optional<Item> verifyOre = storage.getItem(oreMaterialKey);
        if (!verifyOre.isPresent() || verifyOre.get().getQuantity() < oreToAdd) {
            // Nếu ore không được thêm đúng số lượng, hoàn lại block
            storage.add(blockMaterialKey, blocksToRemove);
            instance.getLogger().warning("[OreTo] Conversion failed for " + playerName + ". Rolling back.");
            context.sendMessage(Message.getMessage("FAIL.conversion-failed")
                    .replaceAll(ITEM_REGEX, blockMaterial.name())
                    .replaceAll(PLAYER_REGEX, targetPlayer.getName()));
            return;
        }

        instance.getLogger().info("[OreTo] After add - " + oreMaterialKey + ": "
                + (verifyOre.isPresent() ? verifyOre.get().getQuantity() : 0));
        instance.getLogger().info("[OreTo] Conversion successful for " + playerName);

        context.sendMessage(Message.getMessage("SUCCESS.OreTo.sender")
                .replaceAll(QUANTITY_REGEX, String.valueOf(blocksToRemove))
                .replaceAll(ITEM_REGEX, blockMaterial.name())
                .replaceAll(Utils.getRegex("ores"), String.valueOf(oreToAdd))
                .replaceAll(Utils.getRegex("ore_item"), oreMaterial.name())
                .replaceAll(PLAYER_REGEX, targetPlayer.getName()));

        if (targetPlayer.isOnline()) {
            targetPlayer.getPlayer().sendMessage(Message.getMessage("SUCCESS.OreTo.target"));
        } else {
            targetUser.save();
        }
    }
}