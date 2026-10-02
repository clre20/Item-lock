package clre20.itemLock.listener;

import clre20.itemLock.compatibility.eshulkerbox.EShulkerBoxHook;
import clre20.itemLock.compatibility.shopkeepers.ShopkeepersHook;
import clre20.itemLock.config.PluginConfig;
import clre20.itemLock.feedback.FeedbackService;
import clre20.itemLock.matcher.ItemMatcher;
import clre20.itemLock.ItemLock;
import clre20.itemLock.security.ContainerWhitelist;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
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

import java.util.Map;

/**
 * 介面操作全管道封鎖監聽器 (Inventory Zero-Trust Security)。
 * 當玩家開啟的頂部介面不在純存儲白名單內時，徹底封死所有移入、加工、偷換操作。
 * 支援與 Shopkeepers 插件深度相容（放行交易介面與編輯設定介面）。
 * 支援與 eShulkerBox 等手持/隨身開啟潛影盒插件相容（放行純存儲取放操作）。
 */
public class InventorySecurityListener implements Listener {

    private final PluginConfig config;
    private final ItemMatcher itemMatcher;
    private final FeedbackService feedbackService;
    private final ShopkeepersHook shopkeepersHook;
    private final EShulkerBoxHook eshulkerBoxHook;

    public InventorySecurityListener(PluginConfig config, ItemMatcher itemMatcher, FeedbackService feedbackService,
                                     ShopkeepersHook shopkeepersHook, EShulkerBoxHook eshulkerBoxHook) {
        this.config = config;
        this.itemMatcher = itemMatcher;
        this.feedbackService = feedbackService;
        this.shopkeepersHook = shopkeepersHook;
        this.eshulkerBoxHook = eshulkerBoxHook;
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

        // 檢查副手槽位放入防護 (PlayerInventory Slot 40 或 CraftingView / Creative RawSlot 45)
        if (isOffhandSlot(event)) {
            ItemStack cursor = event.getCursor();
            if (itemMatcher.isDenyOffhand(cursor, config)) {
                cancelAndFeedback(event, player, config.getDenyOffhandMessage());
                if (player.getGameMode() == GameMode.CREATIVE) {
                    restoreCreativeItem(player, cursor);
                }
                return;
            }
            if (event.getClick() == ClickType.NUMBER_KEY) {
                int hotbarSlot = event.getHotbarButton();
                if (hotbarSlot >= 0 && hotbarSlot < 9) {
                    ItemStack hotbarItem = player.getInventory().getItem(hotbarSlot);
                    if (itemMatcher.isDenyOffhand(hotbarItem, config)) {
                        cancelAndFeedback(event, player, config.getDenyOffhandMessage());
                        return;
                    }
                }
            }
        }

        boolean isTopPureStorage = isPureStorage(player, topInventory);
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
                    if (player.getGameMode() == GameMode.CREATIVE) {
                        restoreCreativeItem(player, cursor);
                    }
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

                // 防範透過 Shift-Click 將上鎖盾牌快捷穿戴入副手槽
                if (current != null && current.getType() == org.bukkit.Material.SHIELD && itemMatcher.isDenyOffhand(current, config)) {
                    if (player.getInventory().getItemInOffHand().getType().isAir()) {
                        cancelAndFeedback(event, player, config.getDenyOffhandMessage());
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

        // 檢查拖曳是否涉及副手槽位
        for (int rawSlot : event.getRawSlots()) {
            if (isOffhandRawSlot(event.getView(), rawSlot)) {
                ItemStack oldCursor = event.getOldCursor();
                if (itemMatcher.isDenyOffhand(oldCursor, config)) {
                    cancelAndFeedback(event, player, config.getDenyOffhandMessage());
                    if (player.getGameMode() == GameMode.CREATIVE) {
                        restoreCreativeItem(player, oldCursor);
                    }
                    return;
                }
                for (ItemStack newItem : event.getNewItems().values()) {
                    if (itemMatcher.isDenyOffhand(newItem, config)) {
                        cancelAndFeedback(event, player, config.getDenyOffhandMessage());
                        if (player.getGameMode() == GameMode.CREATIVE) {
                            restoreCreativeItem(player, oldCursor);
                        }
                        return;
                    }
                }
            }
        }

        boolean isTopPureStorage = isPureStorage(player, topInventory);
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
                if (player.getGameMode() == GameMode.CREATIVE) {
                    restoreCreativeItem(player, oldCursor);
                }
                return;
            }
        }
    }

    /**
     * 判定指定介面是否為純存儲容器（含真實方塊容器、末影箱、隨身背包及受相容的虛擬/手持潛影盒）。
     */
    private boolean isPureStorage(Player player, Inventory topInventory) {
        if (ContainerWhitelist.isPureStorage(topInventory, config.isAllowVirtualShulkerBox())) {
            return true;
        }

        // eShulkerBox 手持/隨身開啟潛影盒相容判定
        if (config.isAllowEShulkerBox() && eshulkerBoxHook.isEShulkerBoxOpen(player, topInventory)) {
            return true;
        }

        // 虛擬潛影盒通用開關判定
        if (config.isAllowVirtualShulkerBox() && ContainerWhitelist.isVirtualShulkerBox(topInventory)) {
            return true;
        }

        return false;
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

    private boolean isOffhandSlot(InventoryClickEvent event) {
        Inventory clicked = event.getClickedInventory();
        if (clicked != null && clicked.getType() == InventoryType.PLAYER && event.getSlot() == 40) {
            return true;
        }
        if (event.getSlot() == 40) {
            return true;
        }
        // 在原版隨身 2x2 合成介面 (CraftingView) 或創造模式生存分頁中，副手槽為 rawSlot 45
        int rawSlot = event.getRawSlot();
        if (rawSlot == 45) {
            InventoryType topType = event.getView().getTopInventory().getType();
            if (topType == InventoryType.CRAFTING || topType == InventoryType.CREATIVE) {
                return true;
            }
        }
        return false;
    }

    private boolean isOffhandRawSlot(org.bukkit.inventory.InventoryView view, int rawSlot) {
        InventoryType topType = view.getTopInventory().getType();
        if ((topType == InventoryType.CRAFTING || topType == InventoryType.CREATIVE) && rawSlot == 45) {
            return true;
        }
        int topSize = view.getTopInventory().getSize();
        if (rawSlot >= topSize) {
            int slotInBottom = view.convertSlot(rawSlot);
            if (slotInBottom == 40) {
                return true;
            }
        }
        return false;
    }

    /**
     * 創造模式專屬防遺失返還機制：
     * 在創造模式下，客戶端在拖曳/點擊被伺服端取消時會自動抹除游標物品且不接收游標同步封包，
     * 因此透過排程在下一個 Tick 將物品安全歸還至玩家主背包或快捷欄，若背包滿則安全掉落在腳下。
     */
    private void restoreCreativeItem(Player player, ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return;
        }
        ItemStack restoreItem = item.clone();
        Bukkit.getScheduler().runTask(ItemLock.getInstance(), () -> {
            if (!player.isOnline()) {
                return;
            }
            player.setItemOnCursor(null);
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(restoreItem);
            if (!leftover.isEmpty()) {
                for (ItemStack drop : leftover.values()) {
                    player.getWorld().dropItem(player.getLocation(), drop);
                }
            }
            player.updateInventory();
        });
    }

    private void cancelAndFeedback(org.bukkit.event.Cancellable event, Player player) {
        cancelAndFeedback(event, player, null);
    }

    private void cancelAndFeedback(org.bukkit.event.Cancellable event, Player player, String customMessage) {
        event.setCancelled(true);
        feedbackService.sendDenyFeedback(player, customMessage);
        feedbackService.syncInventoryNextTick(player);
    }
}
