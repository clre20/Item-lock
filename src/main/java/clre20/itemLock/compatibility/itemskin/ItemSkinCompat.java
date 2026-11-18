package clre20.itemLock.compatibility.itemskin;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * ItemSkin 外觀外掛相容判定。
 * ItemSkin 的介面皆以 clre20.itemSkin.gui.ItemSkinHolder 作為 InventoryHolder，
 * 透過類別名稱比對即可辨識，無需硬依賴 ItemSkin。
 * 放進 ItemSkin 編輯介面的物品只會被改外觀，關閉介面時原樣歸還。
 */
public final class ItemSkinCompat {

    private static final String HOLDER_CLASS = "clre20.itemSkin.gui.ItemSkinHolder";

    private ItemSkinCompat() {}

    /** 檢查指定庫存是否為 ItemSkin 的介面。 */
    public static boolean isItemSkinInventory(Inventory inventory) {
        if (inventory == null) {
            return false;
        }
        InventoryHolder holder = inventory.getHolder(false);
        return holder != null && holder.getClass().getName().equals(HOLDER_CLASS);
    }
}
