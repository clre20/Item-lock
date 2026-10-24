package clre20.itemLock.compatibility.advancedspyinventory;

import org.bukkit.inventory.InventoryView;

/**
 * AdvancedSpyInventory 外掛相容介面。
 * 負責辨識玩家當前開啟的介面是否為 AdvancedSpyInventory 的查看背包 (/invsee) / 末影箱 (/enderchest) / 生物背包介面。
 */
public interface AdvancedSpyInventoryHook {

    /**
     * AdvancedSpyInventory 介面種類。
     */
    enum SpyType {
        /** 非 AdvancedSpyInventory 介面 */
        NONE,
        /** 玩家背包 (/invsee) */
        PLAYER,
        /** 玩家末影箱 (/enderchest) */
        ENDER_CHEST,
        /** 生物背包與裝備 (馬、驢、村民、怪物裝備等) */
        ENTITY
    }

    /**
     * 檢查 AdvancedSpyInventory 外掛是否已在伺服器上載入且啟用。
     */
    boolean isAvailable();

    /**
     * 判定指定介面所屬的 AdvancedSpyInventory 介面種類。
     *
     * @param view 玩家當前開啟的介面
     * @return 介面種類；若非 AdvancedSpyInventory 介面則回傳 {@link SpyType#NONE}
     */
    SpyType getSpyType(InventoryView view);

    /** 外掛啟用／停用時重新確認是否可用（由 PluginEnable/DisableEvent 呼叫）。 */
    default void refresh() {
    }

    /** 外掛正在停用（事件觸發時外掛仍顯示為啟用）。 */
    default void disable() {
    }

    /**
     * 工廠方法：建立具備延遲動態掛接能力的 Hook，失敗時回傳 No-op 實作。
     */
    static AdvancedSpyInventoryHook create() {
        try {
            return new AdvancedSpyInventoryHookImpl();
        } catch (Throwable ignored) {
            return new NoOpAdvancedSpyInventoryHook();
        }
    }
}
