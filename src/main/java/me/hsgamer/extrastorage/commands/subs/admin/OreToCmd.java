package me.hsgamer.extrastorage.commands.subs.admin;

import me.hsgamer.extrastorage.api.item.Item;
import me.hsgamer.extrastorage.api.storage.Storage;
import me.hsgamer.extrastorage.api.user.User;
import me.hsgamer.extrastorage.commands.abstraction.Command;
import me.hsgamer.extrastorage.commands.abstraction.CommandContext;
import me.hsgamer.extrastorage.commands.abstraction.CommandListener;
import me.hsgamer.extrastorage.configs.Message;
import me.hsgamer.extrastorage.data.Constants;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

// Sử dụng Annotation để định nghĩa lệnh, đúng theo cấu trúc của plugin
@Command(value = "oreto", usage = "/{label} oreto <material-key> <player>", permission = "exstorage.command.admin.oreto", minArgs = 2)
public final class OreToCmd extends CommandListener {

    private static final Map<Material, Material> BLOCK_TO_ORE_MAP = new HashMap<>();

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
        // Constructor rỗng là đủ
    }

    @Override
    public void execute(CommandContext context) {
        // Lấy tham số theo đúng API của CommandContext
        String blockKey = context.getArgs(0).toUpperCase();
        String playerName = context.getArgs(1);

        Material blockMaterial = Material.matchMaterial(blockKey);
        if (blockMaterial == null || blockMaterial.isAir()) {
            context.sendMessage(Message.getMessage("ERROR.invalid-material").replace("{input}", blockKey));
            return;
        }

        if (!BLOCK_TO_ORE_MAP.containsKey(blockMaterial)) {
            context.sendMessage(
                    "§8[§eᴋʜᴏ§bᴄʜứᴀ§8] §cLoại vật phẩm '" + blockMaterial.name() + "' không thể đổi thành quặng.");
            return;
        }
        Material oreMaterial = BLOCK_TO_ORE_MAP.get(blockMaterial);

        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(playerName);
        if (!targetPlayer.hasPlayedBefore() && !targetPlayer.isOnline()) {
            context.sendMessage(Message.getMessage("ERROR.player-not-found").replace("{input}", playerName));
            return;
        }

        // Truy cập instance trực tiếp vì nó là 'protected' trong class cha
        User targetUser = instance.getUserManager().getUser(targetPlayer.getUniqueId());
        Storage storage = targetUser.getStorage();

        // Logic lấy số lượng chính xác theo API
        String blockMaterialKey = blockMaterial.name();
        String oreMaterialKey = oreMaterial.name();

        Optional<Item> blockItemOptional = storage.getItem(blockMaterialKey);
        long currentBlockAmount = blockItemOptional.map(Item::getQuantity).orElse(0L);

        if (currentBlockAmount < 1) {
            context.sendMessage(
                    "§8[§eᴋʜᴏ§bᴄʜứᴀ§8] §cNgười chơi " + targetPlayer.getName() + " không đủ " + blockMaterial.name()
                            + " để đổi (hiện có: " + currentBlockAmount + ", cần ít nhất 1).");
            return;
        }

        // Tính toán
        long blocksToRemove = currentBlockAmount;
        long oreToAdd = blocksToRemove * 9;

        // Thực hiện thay đổi trong kho bằng các hàm add/subtract chính xác
        storage.subtract(blockMaterialKey, blocksToRemove);
        storage.add(oreMaterialKey, oreToAdd);

        // Gửi thông báo thành công
        context.sendMessage("§8[§eᴋʜᴏ§bᴄʜứᴀ§8] §a✔ Đã chuyển đổi thành công " + blocksToRemove + " "
                + blockMaterial.name() + " thành "
                + oreToAdd + " " + oreMaterial.name() + " cho người chơi " + targetPlayer.getName() + ".");

        if (targetPlayer.isOnline()) {
            targetPlayer.getPlayer()
                    .sendMessage(
                            "§8[§eᴋʜᴏ§bᴄʜứᴀ§8] §fTài nguyên của bạn đã được quản trị viên chuyển đổi thành quặng.");
        } else {
            // Lưu dữ liệu cho người chơi offline
            targetUser.save();
        }
    }
}