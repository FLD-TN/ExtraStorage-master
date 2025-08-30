package me.hsgamer.extrastorage.data.user;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.hsgamer.extrastorage.util.ItemUtil;
import me.hsgamer.topper.storage.sql.converter.BooleanSqlValueConverter;
import me.hsgamer.topper.storage.sql.converter.ComplexSqlValueConverter;
import me.hsgamer.topper.storage.sql.converter.NumberSqlValueConverter;
import me.hsgamer.topper.storage.sql.converter.StringSqlValueConverter;
import me.hsgamer.topper.storage.sql.core.SqlValueConverter;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;
import java.util.function.Function;
import java.util.concurrent.ConcurrentHashMap;

public class UserImpl {
    public static final UserImpl EMPTY = new UserImpl(
            Collections.emptyMap(), // partners
            "", // texture
            Collections.emptyMap(), // items
            0 // space
    );

    // Sử dụng biến static để lưu trữ pending requests
    // Không lưu trong database nữa mà chỉ lưu trong bộ nhớ
    public static final Map<UUID, Map<String, Long>> pendingPartnerRequestsMap = new ConcurrentHashMap<>();

    public final Map<UUID, Long> partners;
    public final String texture;
    public final Map<String, ItemImpl> items;
    public final long space;

    private UserImpl(Map<UUID, Long> partners, String texture, Map<String, ItemImpl> items, long space) {
        this.partners = partners;
        this.texture = texture;
        this.items = items;
        this.space = space;
    }

    public static SqlValueConverter<UserImpl> getConverter(boolean isMySql) {
        return ComplexSqlValueConverter.<UserImpl>builder()
                .constructor(() -> EMPTY)
                .entry(new StringSqlValueConverter("texture", "TINYTEXT"), user -> user.texture, UserImpl::withTexture)
                .entry(new NumberSqlValueConverter<>("space", false, Number::longValue), user -> user.space,
                        UserImpl::withSpace)
                .entry(
                        new StringSqlValueConverter("partners", isMySql ? "LONGTEXT" : "TEXT"),
                        user -> {
                            JsonObject jsonObject = new JsonObject();
                            for (Map.Entry<UUID, Long> entry : user.partners.entrySet()) {
                                jsonObject.addProperty(entry.getKey().toString(), entry.getValue());
                            }
                            return jsonObject.toString();
                        },
                        (user, string) -> {
                            JsonObject jsonObject = JsonParser.parseString(string).getAsJsonObject();
                            Map<UUID, Long> partners = new HashMap<>();
                            jsonObject.entrySet().forEach(entry -> {
                                UUID uuid = UUID.fromString(entry.getKey());
                                long timestamp = entry.getValue().getAsLong();
                                partners.put(uuid, timestamp);
                            });
                            return user.withPartners(partners);
                        })
                .entry(
                        new StringSqlValueConverter("filter", isMySql ? "LONGTEXT" : "TEXT"),
                        user -> {
                            JsonObject jsonObject = new JsonObject();
                            for (Map.Entry<String, ItemImpl> entry : user.items.entrySet()) {
                                if (!entry.getValue().filtered)
                                    continue;
                                jsonObject.addProperty(entry.getKey(), entry.getValue().quantity);
                            }
                            return jsonObject.toString();
                        },
                        (user, string) -> {
                            JsonObject jsonObject = JsonParser.parseString(string).getAsJsonObject();
                            Map<String, ItemImpl> items = new HashMap<>();
                            jsonObject.entrySet().forEach(entry -> {
                                String key = ItemUtil.normalizeMaterialKey(entry.getKey());
                                long quantity = entry.getValue().getAsLong();
                                items.put(key, ItemImpl.EMPTY.withFiltered(true).withQuantity(quantity));
                            });
                            return user.withAdditionalItems(items);
                        })
                .entry(
                        new StringSqlValueConverter("unfilter", isMySql ? "LONGTEXT" : "TEXT"),
                        user -> {
                            JsonObject jsonObject = new JsonObject();
                            for (Map.Entry<String, ItemImpl> entry : user.items.entrySet()) {
                                if (entry.getValue().filtered)
                                    continue;
                                jsonObject.addProperty(entry.getKey(), entry.getValue().quantity);
                            }
                            return jsonObject.toString();
                        },
                        (user, string) -> {
                            JsonObject jsonObject = JsonParser.parseString(string).getAsJsonObject();
                            Map<String, ItemImpl> items = new HashMap<>();
                            jsonObject.entrySet().forEach(entry -> {
                                String key = ItemUtil.normalizeMaterialKey(entry.getKey());
                                long quantity = entry.getValue().getAsLong();
                                items.put(key, ItemImpl.EMPTY.withFiltered(false).withQuantity(quantity));
                            });
                            return user.withAdditionalItems(items);
                        })
                .build();
    }

    // Các phương thức xử lý pending partner requests không lưu trong database
    public UserImpl withPendingPartnerRequests(Map<String, Long> pendingPartnerRequests) {
        // Không còn cần thiết, chỉ trả về instance hiện tại
        return this;
    }

    public UserImpl withPendingPartnerRequest(String username, long timestamp) {
        // Không còn cần thiết, chỉ trả về instance hiện tại
        return this;
    }

    public UserImpl withPendingPartnerRequestRemoved(String username) {
        // Không còn cần thiết, chỉ trả về instance hiện tại
        return this;
    }

    public UserImpl withPartners(Map<UUID, Long> partners) {
        return new UserImpl(
                Collections.unmodifiableMap(partners),
                this.texture,
                this.items,
                this.space);
    }

    public UserImpl withPartner(UUID uuid) {
        HashMap<UUID, Long> partners = new HashMap<>(this.partners);
        partners.put(uuid, System.currentTimeMillis());
        return new UserImpl(
                Collections.unmodifiableMap(partners),
                this.texture,
                this.items,
                this.space);
    }

    public UserImpl withPartnerRemoved(UUID uuid) {
        HashMap<UUID, Long> partners = new HashMap<>(this.partners);
        partners.remove(uuid);
        return new UserImpl(
                Collections.unmodifiableMap(partners),
                this.texture,
                this.items,
                this.space);
    }

    public UserImpl withTexture(String texture) {
        return new UserImpl(
                this.partners,
                texture,
                this.items,
                this.space);
    }

    public UserImpl withItems(Map<String, ItemImpl> items) {
        return new UserImpl(
                this.partners,
                this.texture,
                Collections.unmodifiableMap(items),
                this.space);
    }

    public UserImpl withAdditionalItems(Map<String, ItemImpl> additionalItems) {
        HashMap<String, ItemImpl> items = new HashMap<>(this.items);
        additionalItems.forEach(items::putIfAbsent);
        return new UserImpl(
                this.partners,
                this.texture,
                Collections.unmodifiableMap(items),
                this.space);
    }

    public UserImpl withItemIfNotFound(String key, ItemImpl item) {
        HashMap<String, ItemImpl> items = new HashMap<>(this.items);
        items.putIfAbsent(key, item);
        return new UserImpl(
                this.partners,
                this.texture,
                Collections.unmodifiableMap(items),
                this.space);
    }

    public UserImpl withItemRemoved(String key) {
        HashMap<String, ItemImpl> items = new HashMap<>(this.items);
        items.remove(key);
        return new UserImpl(
                this.partners,
                this.texture,
                Collections.unmodifiableMap(items),
                this.space);
    }

    public UserImpl withItemModifiedIfFound(String key, Function<ItemImpl, ItemImpl> function) {
        HashMap<String, ItemImpl> items = new HashMap<>(this.items);
        ItemImpl current = items.get(key);
        if (current != null) {
            ItemImpl modified = function.apply(current);
            if (modified == null) {
                items.remove(key); // Xóa item nếu function trả về null
            } else {
                items.put(key, modified);
            }
        } else {
            // Nếu item không tồn tại và function muốn tạo mới
            ItemImpl newItem = function.apply(ItemImpl.EMPTY);
            if (newItem != null) {
                items.put(key, newItem);
            }
        }

        return withItems(items);
    }

    public UserImpl withSpace(long space) {
        return new UserImpl(
                this.partners,
                this.texture,
                this.items,
                space);
    }

}
