package clre20.itemLock.compatibility.shopkeepers;

import com.nisovin.shopkeepers.api.ShopkeepersAPI;
import com.nisovin.shopkeepers.api.ui.DefaultUITypes;
import com.nisovin.shopkeepers.api.ui.UISession;
import com.nisovin.shopkeepers.api.ui.UIType;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

/**
 * 透過 Shopkeepers API 深度整合 Shopkeepers 的介面判定。
 */
public class ShopkeepersHookImpl implements ShopkeepersHook {

    @Override
    public boolean isAvailable() {
        return ShopkeepersAPI.isEnabled();
    }

    @Override
    public boolean isShopkeepersTrading(Player player, Inventory topInventory) {
        if (!ShopkeepersAPI.isEnabled() || player == null) {
            return false;
        }

        UISession session = ShopkeepersAPI.getUIRegistry().getUISession(player);
        if (session != null && session.isValid()) {
            UIType uiType = session.getUIType();
            return uiType.equals(DefaultUITypes.TRADING());
        }

        return false;
    }

    @Override
    public boolean isShopkeepersEditor(Player player, Inventory topInventory) {
        if (!ShopkeepersAPI.isEnabled() || player == null) {
            return false;
        }

        UISession session = ShopkeepersAPI.getUIRegistry().getUISession(player);
        if (session != null && session.isValid()) {
            UIType uiType = session.getUIType();
            // 只要是在 Shopkeepers UI session 內且不是交易介面，即為各類設定/編輯介面
            return !uiType.equals(DefaultUITypes.TRADING());
        }

        return false;
    }

    @Override
    public boolean isShopkeeperEntity(Entity entity) {
        if (!ShopkeepersAPI.isEnabled() || entity == null) {
            return false;
        }
        return ShopkeepersAPI.getShopkeeperRegistry().isShopkeeper(entity);
    }

    @Override
    public boolean isShopkeeperBlock(Block block) {
        if (!ShopkeepersAPI.isEnabled() || block == null) {
            return false;
        }
        return ShopkeepersAPI.getShopkeeperRegistry().isShopkeeper(block);
    }
}
