package clre20.itemLock.listener;

import clre20.itemLock.feedback.FeedbackService;
import clre20.itemLock.feedback.FeedbackService.Deny;
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
 * 合成格（3x3、2x2）內有受保護物品時清空產物並取消合成（防地圖複製等）。
 */
public final class CraftingSecurityListener implements Listener {

    private final ItemMatcher matcher;
    private final FeedbackService feedback;

    public CraftingSecurityListener(ItemMatcher matcher, FeedbackService feedback) {
        this.matcher = matcher;
        this.feedback = feedback;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareItemCraft(PrepareItemCraftEvent event) {
        CraftingInventory inv = event.getInventory();
        // 沒有產物就沒有東西要擋
        if (inv.getResult() == null) {
            return;
        }
        if (containsProtected(inv)) {
            inv.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        CraftingInventory inv = event.getInventory();
        if (!containsProtected(inv)) {
            return;
        }
        event.setCancelled(true);
        inv.setResult(null);
        if (event.getWhoClicked() instanceof Player player) {
            feedback.deny(player, Deny.DEFAULT);
        }
    }

    private boolean containsProtected(CraftingInventory inv) {
        for (ItemStack item : inv.getMatrix()) {
            if (matcher.find(item) != null) {
                return true;
            }
        }
        return false;
    }
}
