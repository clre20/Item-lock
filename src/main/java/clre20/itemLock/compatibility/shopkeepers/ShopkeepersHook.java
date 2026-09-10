package clre20.itemLock.compatibility.shopkeepers;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

/**
 * Shopkeepers 外掛相容介面。
 * 負責檢測玩家是否正處於 Shopkeepers 的交易介面或設定/編輯介面中。
 */
public interface ShopkeepersHook {

    /**
     * 檢查 Shopkeepers 外掛與 API 是否可用。
     */
    boolean isAvailable();

    /**
     * 檢查玩家當前開啟的介面是否為 Shopkeepers 交易介面。
     */
    boolean isShopkeepersTrading(Player player, Inventory topInventory);

    /**
     * 檢查玩家當前開啟的介面是否為 Shopkeepers 設定/編輯介面 (Editor)。
     */
    boolean isShopkeepersEditor(Player player, Inventory topInventory);

    /**
     * 檢查指定實體是否為 Shopkeeper（例如盔甲架商店或村民商店）。
     */
    boolean isShopkeeperEntity(Entity entity);

    /**
     * 檢查指定方塊是否為 Shopkeeper（例如告示牌商店）。
     */
    boolean isShopkeeperBlock(Block block);

    /**
     * 工廠方法：若伺服器已載入 Shopkeepers 則建立專用 Hook，否則回傳 No-op 實作。
     */
    static ShopkeepersHook create() {
        if (Bukkit.getPluginManager().isPluginEnabled("Shopkeepers")) {
            try {
                Class.forName("com.nisovin.shopkeepers.api.ShopkeepersAPI");
                if (com.nisovin.shopkeepers.api.ShopkeepersAPI.isEnabled()) {
                    return new ShopkeepersHookImpl();
                }
            } catch (Throwable ignored) {
            }
        }
        return new NoOpShopkeepersHook();
    }
}
