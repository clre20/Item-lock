package clre20.itemLock.compatibility.shopkeepers;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

/**
 * 動態延遲解析之 Shopkeepers 相容 Hook。
 * 解決伺服器外掛載入順序競爭或外掛重載時 Hook 失效之問題。
 */
public class DynamicShopkeepersHook implements ShopkeepersHook {

    private ShopkeepersHook delegate;
    private boolean loggedHook = false;

    private ShopkeepersHook getDelegate() {
        if (delegate != null && delegate.isAvailable()) {
            return delegate;
        }

        if (Bukkit.getPluginManager().isPluginEnabled("Shopkeepers")) {
            try {
                Class.forName("com.nisovin.shopkeepers.api.ShopkeepersAPI");
                if (com.nisovin.shopkeepers.api.ShopkeepersAPI.isEnabled()) {
                    this.delegate = new ShopkeepersHookImpl();
                    if (!loggedHook) {
                        loggedHook = true;
                        clre20.itemLock.util.Log.hook("已成功動態掛接 <white>Shopkeepers</white> 插件！");
                    }
                    return this.delegate;
                }
            } catch (Throwable ignored) {
            }
        }

        return null;
    }

    @Override
    public boolean isAvailable() {
        ShopkeepersHook hook = getDelegate();
        return hook != null && hook.isAvailable();
    }

    @Override
    public boolean isShopkeepersTrading(Player player, Inventory topInventory) {
        ShopkeepersHook hook = getDelegate();
        return hook != null && hook.isShopkeepersTrading(player, topInventory);
    }

    @Override
    public boolean isShopkeepersEditor(Player player, Inventory topInventory) {
        ShopkeepersHook hook = getDelegate();
        return hook != null && hook.isShopkeepersEditor(player, topInventory);
    }

    @Override
    public boolean isShopkeeperEntity(Entity entity) {
        ShopkeepersHook hook = getDelegate();
        return hook != null && hook.isShopkeeperEntity(entity);
    }

    @Override
    public boolean isShopkeeperBlock(Block block) {
        ShopkeepersHook hook = getDelegate();
        return hook != null && hook.isShopkeeperBlock(block);
    }
}
