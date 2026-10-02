package clre20.itemLock.listener;

import clre20.itemLock.config.PluginConfig;
import clre20.itemLock.feedback.FeedbackService;
import clre20.itemLock.matcher.ItemMatcher;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * 全面副手防護與方塊放置安全監聽器。
 * 1. 攔截 F 鍵換手與 Bedrock /geyser offhand 指令換手。
 * 2. 攔截副手手持上鎖物品放置方塊或世界右鍵交互。
 * 3. 攔截主副手將上鎖方塊放置到世界上。
 * 4. 具備副手異常殘留自我糾正機制（自動退回主背包）。
 */
public class OffhandAndPlacementSecurityListener implements Listener {

    private final PluginConfig config;
    private final ItemMatcher itemMatcher;
    private final FeedbackService feedbackService;

    public OffhandAndPlacementSecurityListener(PluginConfig config, ItemMatcher itemMatcher, FeedbackService feedbackService) {
        this.config = config;
        this.itemMatcher = itemMatcher;
        this.feedbackService = feedbackService;
    }

    /**
     * 1. 攔截手持物品換手 (Java F 鍵 或 Bedrock /geyser offhand 觸發的 SWAP_HANDS 動作)。
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        ItemStack mainItem = event.getMainHandItem();
        ItemStack offItem = event.getOffHandItem();

        boolean denyMain = itemMatcher.isDenyOffhand(mainItem, config);
        boolean denyOff = itemMatcher.isDenyOffhand(offItem, config);

        if (denyMain || denyOff) {
            event.setCancelled(true);
            feedbackService.sendDenyFeedback(player, config.getDenyOffhandMessage());
            feedbackService.syncInventoryNextTick(player);
        }
    }

    /**
     * 2. 前置攔截 Bedrock 常用之 /geyser offhand 與 /offhand 指令。
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        String msg = event.getMessage().toLowerCase().trim();
        if (msg.startsWith("/geyser offhand") || msg.startsWith("/offhand")) {
            Player player = event.getPlayer();
            ItemStack mainHand = player.getInventory().getItemInMainHand();

            if (itemMatcher.isDenyOffhand(mainHand, config)) {
                event.setCancelled(true);
                feedbackService.sendDenyFeedback(player, config.getDenyOffhandMessage());
                feedbackService.syncInventoryNextTick(player);
            }
        }
    }

    /**
     * 3. 攔截方塊放置行為 (BlockPlaceEvent)。
     * 禁止副手放置，且若該物品開啟了禁止方塊放置 (deny-block-place)，主副手皆禁止放置在世界上。
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        ItemStack placedItem = event.getItemInHand();

        // 檢查放置手部：若為副手放置受保護物品，一律封鎖
        if (event.getHand() == EquipmentSlot.OFF_HAND && itemMatcher.isProtected(placedItem)) {
            event.setCancelled(true);
            feedbackService.sendDenyFeedback(player, config.getDenyOffhandMessage());
            feedbackService.syncInventoryNextTick(player);
            return;
        }

        // 檢查方塊放置開關 (deny-block-place)
        if (itemMatcher.isDenyBlockPlace(placedItem, config)) {
            event.setCancelled(true);
            feedbackService.sendDenyFeedback(player, config.getDenyBlockPlaceMessage());
            feedbackService.syncInventoryNextTick(player);
        }
    }

    /**
     * 4. 攔截副手右鍵點擊方塊交互 (防範透過副手進行方塊交互偷放或觸發機械)。
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.OFF_HAND) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack offhandItem = player.getInventory().getItemInOffHand();

        if (itemMatcher.isDenyOffhand(offhandItem, config)) {
            // 如果副手拿著禁止副手的物品右鍵世界
            if (event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.RIGHT_CLICK_AIR) {
                event.setCancelled(true);
                feedbackService.sendDenyFeedback(player, config.getDenyOffhandMessage());
                feedbackService.syncInventoryNextTick(player);

                // 觸發自我糾正：將副手物品安全退回主背包
                autoEjectOffhand(player, offhandItem);
            }
        }
    }

    /**
     * 5. 切換快捷欄手持時檢查副手 (自我糾正機制)。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        ItemStack offhandItem = player.getInventory().getItemInOffHand();
        if (itemMatcher.isDenyOffhand(offhandItem, config)) {
            autoEjectOffhand(player, offhandItem);
        }
    }

    /**
     * 6. 關閉介面時檢查副手 (防範介面操作後殘留副手)。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            ItemStack offhandItem = player.getInventory().getItemInOffHand();
            if (itemMatcher.isDenyOffhand(offhandItem, config)) {
                autoEjectOffhand(player, offhandItem);
            }
        }
    }

    /**
     * 自我糾正機制：若玩家副手因未知途徑殘留了禁止副手的上鎖物品，安全移回主背包或腳下掉落。
     */
    private void autoEjectOffhand(Player player, ItemStack offhandItem) {
        if (offhandItem == null || offhandItem.getType().isAir()) {
            return;
        }

        player.getInventory().setItemInOffHand(null);
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(offhandItem);
        if (!leftover.isEmpty()) {
            for (ItemStack drop : leftover.values()) {
                player.getWorld().dropItem(player.getLocation(), drop);
            }
        }
        feedbackService.syncInventoryNextTick(player);
    }
}
