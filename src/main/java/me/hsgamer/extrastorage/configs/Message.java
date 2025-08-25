package me.hsgamer.extrastorage.configs;

import org.bukkit.configuration.ConfigurationSection;

public class Message {

    private static ConfigurationSection section;

    // Common messages
    public static final String PREFIX = "§8[§eKHO§bCHỨA§8] §8→ §r";

    // Những thông báo cũ dưới đây sẽ không còn được sử dụng
    // Tất cả thông báo nên được lấy từ file messages.yml thay thế
    @Deprecated
    public static final String NO_PERMISSION = PREFIX + "§c§lBạn không có quyền thực hiện!";
    @Deprecated
    public static final String PLAYER_ONLY = PREFIX + "§c§lChỉ người chơi mới dùng được lệnh này!";
    @Deprecated
    public static final String INVALID_NUMBER = PREFIX + "§c§lGiá trị không hợp lệ!";
    @Deprecated
    public static final String INVALID_ARGS = PREFIX + "§c§lThiếu đối số!";
    @Deprecated
    public static final String PLAYER_NOT_FOUND = PREFIX + "§c§lKhông tìm thấy người chơi!";
    @Deprecated
    public static final String STORAGE_FULL = PREFIX + "§c§lKho chứa đã đầy, không thể thêm!";

    // Command-related messages
    public static final String COMMAND_NOT_FOUND = PREFIX + "&cCommand not found";
    public static final String HELP_MESSAGE = PREFIX + "&fAvailable Commands:";
    public static final String HELP_COMMAND_FORMAT = "&e/{command} &7- &f{description}";

    // Storage-related messages
    public static final String STORAGE_INFO = PREFIX + "&fStorage Info:";
    public static final String STORAGE_SPACE = PREFIX + "&fSpace: &e{space}";
    public static final String STORAGE_ITEMS = PREFIX + "&fItems: &e{items}";
    public static final String STORAGE_ADDED = PREFIX + "&aAdded &e{amount} &aitems to storage";
    public static final String STORAGE_REMOVED = PREFIX + "&aRemoved &e{amount} &aitems from storage";
    public static final String STORAGE_SET = PREFIX + "&aSet storage space to &e{space}";
    public static final String STORAGE_ADDED_SPACE = PREFIX + "&aAdded &e{space} &aspace to storage";

    // Partner-related messages
    public static final String PARTNER_REQUEST_SENT = PREFIX + "&aSent partner request to &e{player}";
    public static final String PARTNER_REQUEST_RECEIVED = PREFIX + "&e{player} &awants to be your partner";
    public static final String PARTNER_ADDED = PREFIX + "&aAdded &e{player} &aas partner";
    public static final String PARTNER_REMOVED = PREFIX + "&aRemoved &e{player} &aas partner";
    public static final String PARTNER_NOT_FOUND = PREFIX + "&cPartner not found";
    public static final String PARTNER_ALREADY_EXISTS = PREFIX + "&cYou're already partners";
    public static final String PARTNER_REQUEST_ACCEPTED = PREFIX + "&e{player} &aaccepted your partner request";
    public static final String PARTNER_REQUEST_DENIED = PREFIX + "&e{player} &cdenied your partner request";

    // Toggle-related messages
    public static final String TOGGLE_ON = PREFIX + "&aEnabled &e{feature}";
    public static final String TOGGLE_OFF = PREFIX + "&cDisabled &e{feature}";

    // Money-related messages
    public static final String INSUFFICIENT_MONEY = PREFIX + "&cInsufficient money";
    public static final String MONEY_WITHDRAWN = PREFIX + "&aWithdrawn &e${amount}";
    public static final String MONEY_ADDED = PREFIX + "&aAdded &e${amount} &ato storage";

    // Block/Item conversion messages
    public static final String BLOCK_CONVERTED = PREFIX + "&aConverted &e{amount} &ablocks to items";
    public static final String ORE_CONVERTED = PREFIX + "&aConverted &e{amount} &aores to items";

    // Special messages
    public static final String MAX_SPACE_NOT_USED = PREFIX + "&cMax space limit is not enabled";
    public static final String MUST_ENTER_PLAYER = PREFIX + "&cYou must enter a player name";
    public static final String PARTNER_LIMIT_REACHED = PREFIX + "&cPartner limit reached";

    private Message() {
        // Private constructor to prevent instantiation
    }

    public static void init(ConfigurationSection section) {
        Message.section = section;
    }

    public static String getMessage(String path) {
        if (section == null) {
            // Return matching constant if available
            try {
                return (String) Message.class.getDeclaredField(path.replace(".", "_").toUpperCase())
                        .get(null);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                return PREFIX + "&cMessage not found: " + path;
            }
        }
        String message = section.getString(path);
        if (message == null) {
            return PREFIX + "&cMessage not found: " + path;
        }

        // Thay thế {prefix} với giá trị PREFIX từ cấu hình
        String prefix = section.getString("PREFIX", "§8[§eKHO§bCHỨA§8] §8→ §r");
        return message.replace("{prefix}", prefix);
    }
}
