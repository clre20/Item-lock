package clre20.itemLock.compatibility.eshulkerbox;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

/**
 * eShulkerBox 外掛相容介面。
 * 負責檢測玩家是否正處於 eShulkerBox 手持/隨身開啟的潛影盒介面中。
 */
public interface EShulkerBoxHook {

    /**
     * 檢查 eShulkerBox 外掛是否已在伺服器上載入且啟用。
     */
    boolean isAvailable();

    /**
     * 檢查指定玩家當前開啟的頂部介面是否為 eShulkerBox 的手持/隨身潛影盒介面。
     *
     * @param player 點擊介面的玩家
     * @param topInventory 當前頂部介面 (Top Inventory)
     * @return 若處於 eShulkerBox 手持開啟介面則回傳 true
     */
    boolean isEShulkerBoxOpen(Player player, Inventory topInventory);

    /**
     * 工廠方法：若伺服器已載入 eShulkerBox 則建立專用 Hook，否則回傳 No-op 實作。
     */
    static EShulkerBoxHook create() {
        if (Bukkit.getPluginManager().isPluginEnabled("eShulkerBox")) {
            try {
                return new EShulkerBoxHookImpl();
            } catch (Throwable ignored) {
            }
        }
        return new NoOpEShulkerBoxHook();
    }
}
