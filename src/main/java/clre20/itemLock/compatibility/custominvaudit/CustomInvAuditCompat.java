package clre20.itemLock.compatibility.custominvaudit;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CustomInvAudit（/ca）相容判定。
 * <p>
 * CustomInvAudit 的介面都以 {@code clre20.customInvAudit.gui.GuiHolder} 子類別作為 InventoryHolder，
 * 只靠類別名稱辨識，不需要硬依賴。會鏡像目標物品的介面有三種：
 * PlayerInventoryGui（玩家背包）、EnderChestGui（終界箱）、EntityGui（生物）；
 * 前兩者再以 {@code offlineData()} 是否為 null 區分線上／離線。
 */
public final class CustomInvAuditCompat {

    private static final String GUI_PACKAGE = "clre20.customInvAudit.gui.";

    /** CustomInvAudit 介面種類，名稱即 config.yml 白名單中使用的值。 */
    public enum AuditGui {
        NONE(null),
        PLAYER_INVENTORY("player-inventory"),
        OFFLINE_PLAYER_INVENTORY("offline-player-inventory"),
        ENDER_CHEST("ender-chest"),
        OFFLINE_ENDER_CHEST("offline-ender-chest"),
        ENTITY("entity");

        private final String key;

        AuditGui(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }

        public static AuditGui fromKey(String key) {
            if (key == null) {
                return null;
            }
            String k = key.trim().toLowerCase(Locale.ROOT).replace('_', '-');
            for (AuditGui g : values()) {
                if (g.key != null && g.key.equals(k)) {
                    return g;
                }
            }
            return null;
        }
    }

    /** 每個 holder 類別的 offlineData() 方法快取（重載 CustomInvAudit 後類別不同，會重新取得）。 */
    private static final Map<Class<?>, Method> OFFLINE_DATA = new ConcurrentHashMap<>();

    private CustomInvAuditCompat() {}

    /**
     * 判定介面種類；不是 CustomInvAudit 的物品鏡像介面時回傳 {@link AuditGui#NONE}。
     */
    public static AuditGui getType(Inventory top) {
        if (top == null) {
            return AuditGui.NONE;
        }
        InventoryHolder holder = top.getHolder(false);
        if (holder == null) {
            return AuditGui.NONE;
        }
        Class<?> cls = holder.getClass();
        String name = cls.getName();
        if (!name.startsWith(GUI_PACKAGE)) {
            return AuditGui.NONE;
        }
        return switch (name.substring(GUI_PACKAGE.length())) {
            case "PlayerInventoryGui" -> isOffline(holder) ? AuditGui.OFFLINE_PLAYER_INVENTORY : AuditGui.PLAYER_INVENTORY;
            case "EnderChestGui" -> isOffline(holder) ? AuditGui.OFFLINE_ENDER_CHEST : AuditGui.ENDER_CHEST;
            case "EntityGui" -> AuditGui.ENTITY;
            default -> AuditGui.NONE;
        };
    }

    private static boolean isOffline(InventoryHolder holder) {
        try {
            Method m = OFFLINE_DATA.computeIfAbsent(holder.getClass(), c -> {
                try {
                    return c.getMethod("offlineData");
                } catch (NoSuchMethodException e) {
                    throw new IllegalStateException(e);
                }
            });
            return m.invoke(holder) != null;
        } catch (Throwable ignored) {
            // 取不到時當成離線，讓白名單以較嚴格的離線設定判斷
            return true;
        }
    }

    /** 重載 CustomInvAudit 時清掉舊類別的快取，避免留住舊的 ClassLoader。 */
    public static void clearCache() {
        OFFLINE_DATA.clear();
    }
}
