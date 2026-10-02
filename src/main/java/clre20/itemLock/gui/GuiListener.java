package clre20.itemLock.gui;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

/**
 * 監聽所有 ItemLock 自訂箱子 GUI 互動。
 * 全域鎖定介面點擊與拖拉，杜絕玩家拿走介面控制項或裝飾板，並安全路由點擊。
 */
public class GuiListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        if (topInventory.getHolder() instanceof GuiHolder guiHolder) {
            // 一律取消原版搬移行為
            event.setCancelled(true);

            // 路由點擊至對應 GUI 處理
            guiHolder.onClick(event);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        if (topInventory.getHolder() instanceof GuiHolder) {
            // 禁止在自訂 GUI 內拖曳塗抹
            event.setCancelled(true);
        }
    }
}
