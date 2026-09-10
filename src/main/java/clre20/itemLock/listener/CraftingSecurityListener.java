package clre20.itemLock.listener;

import clre20.itemLock.feedback.FeedbackService;
import clre20.itemLock.matcher.ItemMatcher;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;

/**
 * 工作台與隨身 2x2 地圖拓印與合成防堵監聽器。
 * 檢查 3x3 工作台及 2x2 隨身合成矩陣，若包含受保護物品或地圖，直接將產物設為空並取消合成。
 */
public class CraftingSecurityListener implements Listener {

    private final ItemMatcher itemMatcher;
    private final FeedbackService feedbackService;

    public CraftingSecurityListener(ItemMatcher itemMatcher, FeedbackService feedbackService) {
        this.itemMatcher = itemMatcher;
        this.feedbackService = feedbackService;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareItemCraft(PrepareItemCraftEvent event) {
        CraftingInventory inventory = event.getInventory();
        ItemStack[] matrix = inventory.getMatrix();

        for (ItemStack item : matrix) {
            if (itemMatcher.isProtected(item)) {
                // 直接將產物設為空，徹底封死原版地圖拓印複製與一切合成產出
                inventory.setResult(null);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        CraftingInventory inventory = event.getInventory();
        ItemStack[] matrix = inventory.getMatrix();

        for (ItemStack item : matrix) {
            if (itemMatcher.isProtected(item)) {
                event.setCancelled(true);
                inventory.setResult(null);

                if (event.getWhoClicked() instanceof Player player) {
                    feedbackService.sendDenyFeedback(player);
                    feedbackService.syncInventoryNextTick(player);
                }
                return;
            }
        }
    }
}
