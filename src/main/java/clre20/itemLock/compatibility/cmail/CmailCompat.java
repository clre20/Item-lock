package clre20.itemLock.compatibility.cmail;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Cmail 郵件外掛相容判定。
 * Cmail 所有 GUI 皆以 clre20.cmail.CmailHolder 作為 InventoryHolder，
 * 透過類別名稱比對即可辨識，無需硬依賴 Cmail。
 */
public final class CmailCompat {

    private static final String HOLDER_CLASS = "clre20.cmail.CmailHolder";

    private CmailCompat() {}

    /**
     * 檢查指定庫存是否為 Cmail 的郵件介面（寄件附件、讀取郵件、管理員預覽、禮包打包等）。
     */
    public static boolean isCmailInventory(Inventory inventory) {
        if (inventory == null) {
            return false;
        }
        InventoryHolder holder = inventory.getHolder(false);
        return holder != null && holder.getClass().getName().equals(HOLDER_CLASS);
    }
}
