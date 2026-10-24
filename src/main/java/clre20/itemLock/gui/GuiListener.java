package clre20.itemLock.gui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.Plugin;

/**
 * Item-lock 介面：所有點擊與拖曳一律取消，只有「左鍵點頂部介面」會交給介面處理。
 * holder 用 getHolder(false) 取得，不建立方塊快照（這個監聽器會收到伺服器上所有介面的點擊）。
 */
public final class GuiListener implements Listener {

    private final Plugin plugin;

    public GuiListener(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder(false);
        if (!(holder instanceof GuiHolder gui) || gui.getPlugin() != plugin) {
            return;
        }
        event.setCancelled(true);
        int raw = event.getRawSlot();
        if (event.getClick() != ClickType.LEFT || raw < 0 || raw >= event.getView().getTopInventory().getSize()) {
            return;
        }
        gui.onLeftClick(event, raw);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof GuiHolder gui && gui.getPlugin() == plugin) {
            event.setCancelled(true);
        }
    }

    /** 插件停用時關閉所有開著的 Item-lock 介面，避免監聽器移除後物品被拿走。 */
    public static void closeAll(Plugin plugin) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder(false) instanceof GuiHolder gui && gui.getPlugin() == plugin) {
                p.closeInventory();
            }
        }
    }
}
