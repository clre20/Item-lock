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

    private volatile ShopkeepersHook delegate;
    private boolean loggedHook = false;

    public DynamicShopkeepersHook() {
        refresh();
    }

    @Override
    public void disable() {
        delegate = null;
    }

    /** 只在啟動與外掛啟用／停用時檢查，事件中不再查詢外掛列表。 */
    @Override
    public void refresh() {
        ShopkeepersHook hook = null;
        if (Bukkit.getPluginManager().isPluginEnabled("Shopkeepers")) {
            try {
                Class.forName("com.nisovin.shopkeepers.api.ShopkeepersAPI");
                if (com.nisovin.shopkeepers.api.ShopkeepersAPI.isEnabled()) {
                    hook = new ShopkeepersHookImpl();
                    if (!loggedHook) {
                        loggedHook = true;
                        clre20.itemLock.util.Log.console("console.hooked", "plugin", "Shopkeepers");
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        this.delegate = hook;
    }

    private ShopkeepersHook getDelegate() {
        ShopkeepersHook d = delegate;
        return d != null && d.isAvailable() ? d : null;
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
