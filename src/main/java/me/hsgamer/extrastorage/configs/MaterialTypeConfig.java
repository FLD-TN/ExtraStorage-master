package me.hsgamer.extrastorage.configs;

import me.hsgamer.extrastorage.ExtraStorage;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;

/**
 * Quản lý các loại vật liệu có thể thêm vào kho
 */
public class MaterialTypeConfig {
    private static volatile MaterialTypeConfig instance;
    private static final Object LOCK = new Object();

    private final ExtraStorage plugin;
    private final File configFile;
    private FileConfiguration config;

    // Sử dụng thread-safe collections
    private final java.util.concurrent.ConcurrentHashMap<Material, Boolean> blacklistedMaterials = new java.util.concurrent.ConcurrentHashMap<>();
    private volatile List<String> oreMaterialKeywords;
    private volatile List<String> cropMaterialKeywords;
    private volatile List<String> mobDropMaterialKeywords;

    // Cache cho material type checks
    private final java.util.concurrent.ConcurrentHashMap<Material, Boolean> materialTypeCache = new java.util.concurrent.ConcurrentHashMap<>();
    private volatile long lastCacheRefresh = System.currentTimeMillis();
    private static final long CACHE_EXPIRY = 60000; // 1 minute cache expiry

    public MaterialTypeConfig(ExtraStorage plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "material_types.yml");
        if (!configFile.exists()) {
            plugin.saveResource("material_types.yml", false);
        }
        loadConfig();
    }

    /**
     * Kiểm tra và làm mới cache nếu cần
     */
    private void refreshCacheIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastCacheRefresh > CACHE_EXPIRY) {
            synchronized (this) {
                if (now - lastCacheRefresh > CACHE_EXPIRY) {
                    materialTypeCache.clear();
                    lastCacheRefresh = now;
                }
            }
        }
    }

    /**
     * Lấy instance của MaterialTypeConfig
     * 
     * @param plugin ExtraStorage plugin
     * @return MaterialTypeConfig instance
     */
    public static MaterialTypeConfig getInstance(ExtraStorage plugin) {
        MaterialTypeConfig result = instance;
        if (result == null) {
            synchronized (LOCK) {
                result = instance;
                if (result == null) {
                    instance = result = new MaterialTypeConfig(plugin);
                }
            }
        }
        return result;
    }

    /**
     * Tải cấu hình từ file
     */
    public void loadConfig() {
        if (!configFile.exists()) {
            plugin.saveResource("material_types.yml", false);
        }

        config = YamlConfiguration.loadConfiguration(configFile);

        // Clear caches
        materialTypeCache.clear();
        blacklistedMaterials.clear();

        // Tải danh sách vật phẩm bị cấm
        List<String> blacklistedNames = config.getStringList("blacklisted-materials");
        for (String name : blacklistedNames) {
            try {
                Material material = Material.valueOf(name.toUpperCase());
                blacklistedMaterials.put(material, Boolean.TRUE);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid material name in blacklisted-materials: " + name);
            }
        }

        // Tải các từ khóa cho các loại vật liệu
        oreMaterialKeywords = config.getStringList("ore-materials");
        cropMaterialKeywords = config.getStringList("crop-materials");
        mobDropMaterialKeywords = config.getStringList("mobdrop-materials");
    }

    /**
     * Lưu cấu hình ra file
     */
    public void saveConfig() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save material types config", e);
        }
    }

    /**
     * Kiểm tra xem vật phẩm có bị cấm không
     * 
     * @param material Loại vật liệu cần kiểm tra
     * @return true nếu vật phẩm bị cấm
     */
    public boolean isBlacklisted(Material material) {
        refreshCacheIfNeeded();
        Boolean cached = materialTypeCache.get(material);
        if (cached != null) {
            return cached;
        }
        boolean result = blacklistedMaterials.containsKey(material);
        materialTypeCache.put(material, result);
        return result;
    }

    /**
     * Kiểm tra xem vật liệu có phải là block không
     * 
     * @param material Loại vật liệu cần kiểm tra
     * @return true nếu là block
     */
    public boolean isBlock(Material material) {
        return material.isBlock() && !material.name().contains("SLAB") &&
                !material.name().contains("STAIR") && !material.name().contains("DOOR") &&
                !material.name().contains("FENCE") && !material.name().contains("WALL") &&
                !material.name().contains("PLATE") && !material.name().contains("BUTTON") &&
                !material.name().contains("PRESSURE") && !material.name().contains("LEVER") &&
                !material.name().contains("SIGN") && !material.name().contains("BED");
    }

    /**
     * Kiểm tra xem vật liệu có phải là quặng hoặc khoáng sản không
     * 
     * @param material Loại vật liệu cần kiểm tra
     * @return true nếu là quặng hoặc khoáng sản
     */
    public boolean isOre(Material material) {
        String name = material.name();
        for (String keyword : oreMaterialKeywords) {
            if (name.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Kiểm tra xem vật liệu có phải là nông sản không
     * 
     * @param material Loại vật liệu cần kiểm tra
     * @return true nếu là nông sản
     */
    public boolean isCrop(Material material) {
        String name = material.name();
        for (String keyword : cropMaterialKeywords) {
            if (name.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Kiểm tra xem vật liệu có phải là vật phẩm rơi từ mob không
     * 
     * @param material Loại vật liệu cần kiểm tra
     * @return true nếu là vật phẩm rơi từ mob
     */
    public boolean isMobDrop(Material material) {
        String name = material.name();
        for (String keyword : mobDropMaterialKeywords) {
            if (name.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Kiểm tra xem vật liệu có được phép lưu trữ không
     * 
     * @param material Loại vật liệu cần kiểm tra
     * @return true nếu được phép lưu trữ
     */
    public boolean isAllowedItem(Material material) {
        // Nếu vật phẩm nằm trong danh sách cấm, trả về false
        if (isBlacklisted(material)) {
            return false;
        }

        // Kiểm tra xem vật phẩm có thuộc một trong các loại cho phép hay không
        return isBlock(material) || isOre(material) || isCrop(material) || isMobDrop(material);
    }
}
