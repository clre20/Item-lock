package clre20.itemLock.listener;

import clre20.itemLock.compatibility.shopkeepers.ShopkeepersHook;
import clre20.itemLock.config.PluginConfig;
import clre20.itemLock.feedback.FeedbackService;
import clre20.itemLock.matcher.ItemMatcher;
import clre20.itemLock.security.ContainerWhitelist;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * 介面操作全管道封鎖監聽器 (Inventory Zero-Trust Security)。
 * 當玩家開啟的頂部介面不在純存儲白名單內時，徹底封死所有移入、加工、偷換操作。
 * 支援與 Shopkeepers 插件深度相容（放行交易介面與編輯設定介面）。
 */
public class InventorySecurityListener implements Listener {

    private final PluginConfig config;
    private final ItemMatcher itemMatcher;
    private final FeedbackService feedbackService;
    private final ShopkeepersHook shopkeepersHook;

    public InventorySecurityListener(PluginConfig config, ItemMatcher itemMatcher, FeedbackService feedbackService, ShopkeepersHook shopkeepersHook) {
        this.config = config;
        this.itemMatcher = itemMatcher;
        this.feedbackService = feedbackService;
        this.shopkeepersHook = shopkeepersHook;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Inventory topInventory = event.getView().getTopInventory();
        Inventory clickedInventory = event.getClickedInventory();
        int rawSlot = event.getRawSlot();

        // 檢查是否處於允許的 Shopkeepers 介面中（交易或編輯介面）
        if (isShopkeepersAllowed(player, topInventory)) {
            return;
        }

        boolean isTopPureStorage = ContainerWhitelist.isPureStorage(topInventory);
        boolean isSurvivalCrafting = topInventory.getType() == InventoryType.CRAFTING;

        // 情況 A：點擊頂部介面（Top Inventory）
        if (clickedInventory != null && clickedInventory.equals(topInventory)) {
            // 如果頂部介面不是純存儲容器
            if (!isTopPureStorage) {
                // 若為隨身 2x2 背包介面，僅封鎖合成槽（0 ~ 4 槽位：0 為產物，1~4 為合成矩陣）
                if (isSurvivalCrafting && rawSlot > 4) {
                    return;
                }

                // 1. 滑鼠點擊放入或交換（Cursor Drop / Place / Swap）
                ItemStack cursor = event.getCursor();
                if (itemMatcher.isProtected(cursor)) {
                    cancelAndFeedback(event, player);
                    return;
                }

                // 2. 數字鍵偷換（Hotbar Swap 1~9）
                if (event.getClick() == ClickType.NUMBER_KEY) {
                    int hotbarSlot = event.getHotbarButton();
                    if (hotbarSlot >= 0 && hotbarSlot < 9) {
                        ItemStack hotbarItem = player.getInventory().getItem(hotbarSlot);
                        if (itemMatcher.isProtected(hotbarItem)) {
                            cancelAndFeedback(event, player);
                            return;
                        }
                    }
                }

                // 3. 槽位內已存在受保護物品（防止在非純存儲介面內進行任何操作或取出加工）
                ItemStack current = event.getCurrentItem();
                if (itemMatcher.isProtected(current)) {
                    cancelAndFeedback(event, player);
                    return;
                }
            }
        }

        // 情況 B：點擊底部玩家背包介面（Bottom Inventory）進行 Shift 快速移入
        if (clickedInventory != null && !clickedInventory.equals(topInventory)) {
            if (event.isShiftClick()) {
                ItemStack current = event.getCurrentItem();
                if (itemMatcher.isProtected(current)) {
                    // 若頂部介面非純存儲容器，一律禁止 Shift 移入
                    if (!isTopPureStorage && !isSurvivalCrafting) {
                        cancelAndFeedback(event, player);
                        return;
                    }
                }
            }

            // 雙擊收集（DOUBLE_CLICK / COLLECT_TO_CURSOR）
            if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
                ItemStack cursor = event.getCursor();
                if (itemMatcher.isProtected(cursor) && !isTopPureStorage && !isSurvivalCrafting) {
                    cancelAndFeedback(event, player);
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Inventory topInventory = event.getView().getTopInventory();
        int topSize = topInventory.getSize();

        // 檢查是否處於允許的 Shopkeepers 介面中（交易或編輯介面）
        if (isShopkeepersAllowed(player, topInventory)) {
            return;
        }

        boolean isTopPureStorage = ContainerWhitelist.isPureStorage(topInventory);
        boolean isSurvivalCrafting = topInventory.getType() == InventoryType.CRAFTING;

        // 若頂部介面已是純存儲容器，且不是隨身合成欄位，則放行
        if (isTopPureStorage) {
            return;
        }

        // 檢查拖曳的物品是否受保護
        ItemStack oldCursor = event.getOldCursor();
        boolean cursorProtected = itemMatcher.isProtected(oldCursor);

        if (!cursorProtected) {
            for (ItemStack newItem : event.getNewItems().values()) {
                if (itemMatcher.isProtected(newItem)) {
                    cursorProtected = true;
                    break;
                }
            }
        }

        if (!cursorProtected) {
            return;
        }

        // 檢查拖曳目標槽位是否包含非純存儲的頂部介面槽位
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topSize) {
                // 如果是隨身 2x2，僅攔截 0 ~ 4 槽位
                if (isSurvivalCrafting && rawSlot > 4) {
                    continue;
                }
                // 拖拉塗抹涉及非純存儲頂部介面，立即取消
                cancelAndFeedback(event, player);
                return;
            }
        }
    }

    private boolean isShopkeepersAllowed(Player player, Inventory topInventory) {
        if (!shopkeepersHook.isAvailable()) {
            return false;
        }
        if (config.isAllowShopkeepersTrading() && shopkeepersHook.isShopkeepersTrading(player, topInventory)) {
            return true;
        }
        if (config.isAllowShopkeepersEditor() && shopkeepersHook.isShopkeepersEditor(player, topInventory)) {
            return true;
        }
        return false;
    }

    private void cancelAndFeedback(org.bukkit.event.Cancellable event, Player player) {
        event.setCancelled(true);
        feedbackService.sendDenyFeedback(player);
        feedbackService.syncInventoryNextTick(player);
    }
}
