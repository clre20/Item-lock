package clre20.itemLock.listener;

import clre20.itemLock.config.PluginConfig;
import clre20.itemLock.matcher.ItemMatcher;
import clre20.itemLock.security.ContainerWhitelist;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.inventory.ItemStack;

/**
 * 自動化物流管制監聽器。
 * 攔截漏斗、投擲器、發射器或管線等自動化設備移動或拾取受保護物品。
 * 支援透過 config.yml 自訂是否允許漏斗移動或吸取。
 */
public class AutomationSecurityListener implements Listener {

    private final PluginConfig config;
    private final ItemMatcher itemMatcher;

    public AutomationSecurityListener(PluginConfig config, ItemMatcher itemMatcher) {
        this.config = config;
        this.itemMatcher = itemMatcher;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        ItemStack item = event.getItem();
        if (itemMatcher.isProtected(item)) {
            // 若設定允許漏斗移動，則放行
            if (config.isAllowHopperMove()) {
                return;
            }

            // 若目標容器非白名單純存儲容器，一律取消移動
            if (!ContainerWhitelist.isPureStorage(event.getDestination())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryPickupItem(InventoryPickupItemEvent event) {
        ItemStack item = event.getItem().getItemStack();
        if (itemMatcher.isProtected(item)) {
            // 若設定允許地面漏斗吸取，則放行
            if (config.isAllowHopperPickup()) {
                return;
            }

            // 若拾取容器非白名單純存儲容器（如地面漏斗拾取），一律取消
            if (!ContainerWhitelist.isPureStorage(event.getInventory())) {
                event.setCancelled(true);
            }
        }
    }
}
