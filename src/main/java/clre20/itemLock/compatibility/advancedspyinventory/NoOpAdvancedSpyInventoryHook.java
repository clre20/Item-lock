package clre20.itemLock.compatibility.advancedspyinventory;

import org.bukkit.inventory.InventoryView;

/**
 * 未載入 AdvancedSpyInventory 時採用的空實作 (No-op)。
 */
public class NoOpAdvancedSpyInventoryHook implements AdvancedSpyInventoryHook {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public SpyType getSpyType(InventoryView view) {
        return SpyType.NONE;
    }
}
