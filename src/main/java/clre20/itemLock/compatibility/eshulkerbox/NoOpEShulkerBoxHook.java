package clre20.itemLock.compatibility.eshulkerbox;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

/**
 * 未載入 eShulkerBox 時採用的空實作 (No-op)。
 */
public class NoOpEShulkerBoxHook implements EShulkerBoxHook {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public boolean isEShulkerBoxOpen(Player player, Inventory topInventory) {
        return false;
    }
}
