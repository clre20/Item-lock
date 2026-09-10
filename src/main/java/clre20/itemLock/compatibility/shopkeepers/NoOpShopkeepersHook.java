package clre20.itemLock.compatibility.shopkeepers;

import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

/**
 * 當伺服器未安裝 Shopkeepers 時使用的空實作。
 */
public class NoOpShopkeepersHook implements ShopkeepersHook {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public boolean isShopkeepersTrading(Player player, Inventory topInventory) {
        return false;
    }

    @Override
    public boolean isShopkeepersEditor(Player player, Inventory topInventory) {
        return false;
    }

    @Override
    public boolean isShopkeeperEntity(Entity entity) {
        return false;
    }

    @Override
    public boolean isShopkeeperBlock(Block block) {
        return false;
    }
}
