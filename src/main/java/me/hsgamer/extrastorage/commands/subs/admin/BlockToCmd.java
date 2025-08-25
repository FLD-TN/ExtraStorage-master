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
@Command(value = "blockto", usage = "/{label} blockto <material-key> <player>", permission = "exstorage.command.admin.blockto", minArgs = 2)
public final class BlockToCmd extends CommandListener {

    private static final Map<Material, Material> ORE_TO_BLOCK_MAP = new HashMap<>();

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
        // Constructor rỗng là đủ
    }

    @Override
    public void execute(CommandContext context) {
        // Lấy tham số theo đúng API của CommandContext
        String oreKey = context.getArgs(0).toUpperCase();
        String playerName = context.getArgs(1);

        Material oreMaterial = Material.matchMaterial(oreKey);
        if (oreMaterial == null || oreMaterial.isAir()) {
            context.sendMessage(Message.getMessage("ERROR.invalid-material").replace("{input}", oreKey));
            return;
        }

        if (!ORE_TO_BLOCK_MAP.containsKey(oreMaterial)) {
            context.sendMessage(
                    "§8[§eᴋʜᴏ§bᴄʜứᴀ§8] §cLoại vật phẩm '" + oreMaterial.name() + "' không thể đổi thành khối.");
            return;
        }
        Material blockMaterial = ORE_TO_BLOCK_MAP.get(oreMaterial);

        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(playerName);
        if (!targetPlayer.hasPlayedBefore() && !targetPlayer.isOnline()) {
            context.sendMessage(Message.getMessage("ERROR.player-not-found").replace("{input}", playerName));
            return;
        }

        // Truy cập instance trực tiếp vì nó là 'protected' trong class cha
        User targetUser = instance.getUserManager().getUser(targetPlayer.getUniqueId());
        Storage storage = targetUser.getStorage();

        // Logic lấy số lượng chính xác theo API
        String oreMaterialKey = oreMaterial.name();
        String blockMaterialKey = blockMaterial.name();

        Optional<Item> oreItemOptional = storage.getItem(oreMaterialKey);
        long currentOreAmount = oreItemOptional.map(Item::getQuantity).orElse(0L);

        if (currentOreAmount < 9) {
            context.sendMessage(
                    "§8[§eᴋʜᴏ§bᴄʜứᴀ§8] §cNgười chơi " + targetPlayer.getName() + " không đủ " + oreMaterial.name()
                            + " để đổi (hiện có: " + currentOreAmount + ", cần ít nhất 9).");
            return;
        }

        // Tính toán
        long blocksToCraft = currentOreAmount / 9;
        long oreToRemove = blocksToCraft * 9;

        // Thực hiện thay đổi trong kho bằng các hàm add/subtract chính xác
        storage.subtract(oreMaterialKey, oreToRemove);
        storage.add(blockMaterialKey, blocksToCraft);

        // Gửi thông báo thành công
        context.sendMessage("§8[§eᴋʜᴏ§bᴄʜứᴀ§8] §a✔ Đã chuyển đổi thành công " + oreToRemove + " " + oreMaterial.name()
                + " thành "
                + blocksToCraft + " " + blockMaterial.name() + " cho người chơi " + targetPlayer.getName() + ".");

        if (targetPlayer.isOnline()) {
            targetPlayer.getPlayer()
                    .sendMessage("§8[§eᴋʜᴏ§bᴄʜứᴀ§8] §fTài nguyên của bạn đã được quản trị viên chuyển đổi thành khối.");
        } else {
            // Lưu dữ liệu cho người chơi offline
            targetUser.save();
        }
    }
}