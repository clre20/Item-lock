package clre20.itemLock.listener;

import clre20.itemLock.config.PluginConfig;
import clre20.itemLock.matcher.ItemMatcher;
import clre20.itemLock.model.ItemTemplate;
import clre20.itemLock.security.ContainerWhitelist;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;

/**
 * 漏斗／漏斗礦車搬運與吸取。高頻事件：材質沒有樣本的物品一次查表就返回。
 */
public final class AutomationSecurityListener implements Listener {

    private final PluginConfig config;
    private final ItemMatcher matcher;

    public AutomationSecurityListener(PluginConfig config, ItemMatcher matcher) {
        this.config = config;
        this.matcher = matcher;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        ItemTemplate t = matcher.find(event.getItem());
        if (t == null || t.getSettings().isAllowHopperMove(config)) {
            return;
        }
        if (!ContainerWhitelist.isPureStorage(event.getDestination())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryPickupItem(InventoryPickupItemEvent event) {
        ItemTemplate t = matcher.find(event.getItem().getItemStack());
        if (t == null || t.getSettings().isAllowHopperPickup(config)) {
            return;
        }
        if (!ContainerWhitelist.isPureStorage(event.getInventory())) {
            event.setCancelled(true);
        }
    }
}
