package clre20.itemLock.listener;

import clre20.itemLock.compatibility.advancedspyinventory.AdvancedSpyInventoryHook;
import clre20.itemLock.compatibility.cmail.CmailCompat;
import clre20.itemLock.compatibility.eshulkerbox.EShulkerBoxHook;
import clre20.itemLock.compatibility.shopkeepers.ShopkeepersHook;
import clre20.itemLock.config.PluginConfig;
import clre20.itemLock.feedback.FeedbackService;
import clre20.itemLock.feedback.FeedbackService.Deny;
import clre20.itemLock.matcher.ItemMatcher;
import clre20.itemLock.model.ItemTemplate;
import clre20.itemLock.security.ContainerWhitelist;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * 介面操作攔截：頂部介面不是純儲存容器時，禁止把受保護物品放入、交換或在裡面操作。
 * <p>
 * 效能：先找出這次點擊涉及的物品（游標、格子、數字鍵、副手）是否受保護，
 * 都不是就立即返回；相容外掛與容器判斷只在真的涉及受保護物品時才做。
 */
public final class InventorySecurityListener implements Listener {

    private final Plugin plugin;
    private final PluginConfig config;
    private final ItemMatcher matcher;
    private final FeedbackService feedback;
    private final ShopkeepersHook shopkeepers;
    private final EShulkerBoxHook eShulkerBox;
    private final AdvancedSpyInventoryHook spyInventory;

    public InventorySecurityListener(Plugin plugin, PluginConfig config, ItemMatcher matcher, FeedbackService feedback,
                                     ShopkeepersHook shopkeepers, EShulkerBoxHook eShulkerBox,
                                     AdvancedSpyInventoryHook spyInventory) {
        this.plugin = plugin;
        this.config = config;
        this.matcher = matcher;
        this.feedback = feedback;
        this.shopkeepers = shopkeepers;
        this.eShulkerBox = eShulkerBox;
        this.spyInventory = spyInventory;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ClickType click = event.getClick();
        PlayerInventory playerInv = player.getInventory();
        ItemStack cursor = event.getCursor();
        ItemStack current = event.getCurrentItem();

        ItemTemplate tCursor = matcher.find(cursor);
        ItemTemplate tCurrent = matcher.find(current);
        ItemTemplate tHotbar = null;
        if (click == ClickType.NUMBER_KEY) {
            int button = event.getHotbarButton();
            if (button >= 0 && button < 9) {
                tHotbar = matcher.find(playerInv.getItem(button));
            }
        }
        ItemTemplate tOffhand = click == ClickType.SWAP_OFFHAND ? matcher.find(playerInv.getItemInOffHand()) : null;

        // 最常見的情況：沒有任何受保護物品參與
        if (tCursor == null && tCurrent == null && tHotbar == null && tOffhand == null) {
            return;
        }

        InventoryView view = event.getView();
        Inventory top = view.getTopInventory();
        if (isShopkeepersAllowed(player, top)) {
            return;
        }

        Inventory clicked = event.getClickedInventory();
        int rawSlot = event.getRawSlot();

        // 副手槽
        if (isOffhandSlot(clicked, event.getSlot(), rawSlot, top)) {
            if (denyOffhand(tCursor)) {
                cancel(event, player, Deny.OFFHAND);
                restoreIfCreative(player, cursor);
                return;
            }
            if (denyOffhand(tHotbar)) {
                cancel(event, player, Deny.OFFHAND);
                return;
            }
        }

        // 在介面中按 F：游標下的物品換到副手（不會觸發 PlayerSwapHandItemsEvent）
        if (click == ClickType.SWAP_OFFHAND && denyOffhand(tCurrent)) {
            cancel(event, player, Deny.OFFHAND);
            return;
        }

        boolean survivalCrafting = top.getType() == InventoryType.CRAFTING;

        if (clicked != null && clicked.equals(top)) {
            // 隨身 2x2 合成介面只管合成格（0 產物、1~4 材料）
            if (survivalCrafting && rawSlot > 4) {
                return;
            }
            if (isPureStorage(player, view)) {
                return;
            }
            if (tCursor != null) {
                cancel(event, player, Deny.DEFAULT);
                restoreIfCreative(player, cursor);
                return;
            }
            if (tHotbar != null || tOffhand != null || tCurrent != null) {
                cancel(event, player, Deny.DEFAULT);
            }
            return;
        }

        if (clicked != null) {
            if (event.isShiftClick() && tCurrent != null) {
                if (!survivalCrafting && !isPureStorage(player, view)) {
                    cancel(event, player, Deny.DEFAULT);
                    return;
                }
                // Shift 點擊把盾牌直接穿到副手
                if (current.getType() == Material.SHIELD && denyOffhand(tCurrent)
                        && playerInv.getItemInOffHand().getType().isAir()) {
                    cancel(event, player, Deny.OFFHAND);
                    return;
                }
            }
            if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR && tCursor != null
                    && !survivalCrafting && !isPureStorage(player, view)) {
                cancel(event, player, Deny.DEFAULT);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack oldCursor = event.getOldCursor();
        ItemTemplate template = matcher.find(oldCursor);
        if (template == null) {
            // 拖曳放下的物品都是同一種，看第一個即可
            Iterator<ItemStack> it = event.getNewItems().values().iterator();
            if (it.hasNext()) {
                template = matcher.find(it.next());
            }
        }
        if (template == null) {
            return;
        }

        InventoryView view = event.getView();
        Inventory top = view.getTopInventory();
        if (isShopkeepersAllowed(player, top)) {
            return;
        }

        if (denyOffhand(template)) {
            for (int rawSlot : event.getRawSlots()) {
                if (isOffhandRawSlot(view, rawSlot)) {
                    cancel(event, player, Deny.OFFHAND);
                    restoreIfCreative(player, oldCursor);
                    return;
                }
            }
        }

        if (isPureStorage(player, view)) {
            return;
        }
        boolean survivalCrafting = top.getType() == InventoryType.CRAFTING;
        int topSize = top.getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topSize && !(survivalCrafting && rawSlot > 4)) {
                cancel(event, player, Deny.DEFAULT);
                restoreIfCreative(player, oldCursor);
                return;
            }
        }
    }

    private boolean denyOffhand(ItemTemplate t) {
        return t != null && t.getSettings().isDenyOffhand(config);
    }

    /** 頂部介面是否算純儲存（方塊容器、末影箱、背包，或相容外掛的介面）。 */
    private boolean isPureStorage(Player player, InventoryView view) {
        Inventory top = view.getTopInventory();
        if (ContainerWhitelist.isPureStorage(top, config.isAllowVirtualShulkerBox())) {
            return true;
        }
        if (spyInventory.isAvailable()) {
            boolean allowed = switch (spyInventory.getSpyType(view)) {
                case PLAYER -> config.isAllowSpyPlayerInventory();
                case ENDER_CHEST -> config.isAllowSpyEnderChest();
                case ENTITY -> config.isAllowSpyEntityInventory();
                case NONE -> false;
            };
            if (allowed) {
                return true;
            }
        }
        if (config.isAllowEShulkerBox() && eShulkerBox.isEShulkerBoxOpen(player, top)) {
            return true;
        }
        return config.isAllowCmail() && CmailCompat.isCmailInventory(top);
    }

    private boolean isShopkeepersAllowed(Player player, Inventory top) {
        if (!shopkeepers.isAvailable()) {
            return false;
        }
        return (config.isAllowShopkeepersTrading() && shopkeepers.isShopkeepersTrading(player, top))
                || (config.isAllowShopkeepersEditor() && shopkeepers.isShopkeepersEditor(player, top));
    }

    private static boolean isOffhandSlot(Inventory clicked, int slot, int rawSlot, Inventory top) {
        // 只有玩家背包的第 40 格是副手（大箱子的第 40 格不算）
        if (clicked != null && clicked.getType() == InventoryType.PLAYER && slot == 40) {
            return true;
        }
        if (rawSlot == 45) {
            InventoryType type = top.getType();
            return type == InventoryType.CRAFTING || type == InventoryType.CREATIVE;
        }
        return false;
    }

    private static boolean isOffhandRawSlot(InventoryView view, int rawSlot) {
        Inventory top = view.getTopInventory();
        InventoryType type = top.getType();
        if ((type == InventoryType.CRAFTING || type == InventoryType.CREATIVE) && rawSlot == 45) {
            return true;
        }
        return rawSlot >= top.getSize() && view.convertSlot(rawSlot) == 40;
    }

    /**
     * 創造模式下取消點擊時，客戶端會把游標物品刪掉；下一 tick 把物品還給玩家（背包滿則掉在腳下）。
     */
    private void restoreIfCreative(Player player, ItemStack item) {
        if (player.getGameMode() != GameMode.CREATIVE || item == null || item.getType().isAir()) {
            return;
        }
        ItemStack copy = item.clone();
        UUID id = player.getUniqueId();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player p = Bukkit.getPlayer(id);
            if (p == null) {
                return;
            }
            p.setItemOnCursor(null);
            Map<Integer, ItemStack> left = p.getInventory().addItem(copy);
            for (ItemStack drop : left.values()) {
                p.getWorld().dropItem(p.getLocation(), drop);
            }
            p.updateInventory();
        });
    }

    private void cancel(Cancellable event, Player player, Deny type) {
        event.setCancelled(true);
        feedback.deny(player, type);
    }
}
