package clre20.itemLock.gui;

import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * ItemLock 自訂 GUI 容器持有者標識介面。
 * 統一管理介面點擊路由與安全保護，杜絕玩家拿取介面按鈕與裝飾方塊。
 */
public interface GuiHolder extends InventoryHolder {

    /**
     * 處理 GUI 內部的點擊事件。
     *
     * @param event 點擊事件
     */
    void onClick(InventoryClickEvent event);

    @Override
    @NotNull
    Inventory getInventory();
}
