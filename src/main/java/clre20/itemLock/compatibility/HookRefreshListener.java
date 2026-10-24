package clre20.itemLock.compatibility;

import clre20.itemLock.compatibility.advancedspyinventory.AdvancedSpyInventoryHook;
import clre20.itemLock.compatibility.eshulkerbox.EShulkerBoxHook;
import clre20.itemLock.compatibility.shopkeepers.ShopkeepersHook;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;

/**
 * 相容外掛啟用／停用時更新快取的可用狀態，讓高頻事件不必每次查詢外掛列表。
 */
public final class HookRefreshListener implements Listener {

    private final ShopkeepersHook shopkeepers;
    private final EShulkerBoxHook eShulkerBox;
    private final AdvancedSpyInventoryHook spyInventory;

    public HookRefreshListener(ShopkeepersHook shopkeepers, EShulkerBoxHook eShulkerBox, AdvancedSpyInventoryHook spyInventory) {
        this.shopkeepers = shopkeepers;
        this.eShulkerBox = eShulkerBox;
        this.spyInventory = spyInventory;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEnable(PluginEnableEvent event) {
        refresh(event.getPlugin().getName());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDisable(PluginDisableEvent event) {
        switch (event.getPlugin().getName()) {
            case "Shopkeepers" -> shopkeepers.disable();
            case "eShulkerBox" -> eShulkerBox.disable();
            case "AdvancedSpyInventory" -> spyInventory.disable();
            default -> { }
        }
    }

    private void refresh(String name) {
        switch (name) {
            case "Shopkeepers" -> shopkeepers.refresh();
            case "eShulkerBox" -> eShulkerBox.refresh();
            case "AdvancedSpyInventory" -> spyInventory.refresh();
            default -> { }
        }
    }
}
