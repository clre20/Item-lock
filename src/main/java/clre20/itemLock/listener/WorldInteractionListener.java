package clre20.itemLock.listener;

import clre20.itemLock.config.PluginConfig;
import clre20.itemLock.feedback.FeedbackService;
import clre20.itemLock.matcher.ItemMatcher;
import clre20.itemLock.security.ContainerWhitelist;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

/**
 * 實體與世界交互安全監聽器。
 * 包含右鍵非純容器方塊防轉化、展示框/盔甲架塞入防護、防丟棄及掉落物全免疫銷毀機制。
 */
public class WorldInteractionListener implements Listener {

    private final org.bukkit.plugin.Plugin plugin;
    private final PluginConfig config;
    private final ItemMatcher itemMatcher;
    private final FeedbackService feedbackService;
    private final clre20.itemLock.compatibility.shopkeepers.ShopkeepersHook shopkeepersHook;

    public WorldInteractionListener(org.bukkit.plugin.Plugin plugin, PluginConfig config, ItemMatcher itemMatcher, FeedbackService feedbackService, clre20.itemLock.compatibility.shopkeepers.ShopkeepersHook shopkeepersHook) {
        this.plugin = plugin;
        this.config = config;
        this.itemMatcher = itemMatcher;
        this.feedbackService = feedbackService;
        this.shopkeepersHook = shopkeepersHook;
    }

    /**
     * 右鍵祭壇方塊安全：手持受保護物品右鍵非純容器方塊時取消交互，防止觸發多方塊機器轉化。
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteractBlock(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();
        if (!itemMatcher.isProtected(item)) {
            return;
        }

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) {
            return;
        }

        // 若點擊的方塊為 Shopkeeper 方塊（例如告示牌商店），放行交互以開啟商店
        if (shopkeepersHook.isAvailable() && shopkeepersHook.isShopkeeperBlock(clickedBlock)) {
            return;
        }

        if (config.isDenyAltarInteract()) {
            // 若點擊的不是純存儲容器方塊（如祭壇、大鍋、合成台、附魔台、研磨石、鐵砧等）
            if (!ContainerWhitelist.isPureStorageBlock(clickedBlock)) {
                event.setCancelled(true);
                feedbackService.sendDenyFeedback(event.getPlayer());
                feedbackService.syncInventoryNextTick(event.getPlayer());
            }
        }
    }

    /**
     * 實體交互安全：防止將受保護物品塞入物品展示框、發光展示框或盔甲架。
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!config.isDenyEntityInteract()) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();

        boolean holdingProtected = itemMatcher.isProtected(mainHand) || itemMatcher.isProtected(offHand);
        if (!holdingProtected) {
            return;
        }

        Entity target = event.getRightClicked();

        // 若點擊的實體為 Shopkeeper（如盔甲架商店或村民商店），放行交互以開啟商店介面
        if (shopkeepersHook.isAvailable() && shopkeepersHook.isShopkeeperEntity(target)) {
            return;
        }

        if (target instanceof ItemFrame || target instanceof ArmorStand) {
            event.setCancelled(true);
            feedbackService.sendDenyFeedback(player);
            feedbackService.syncInventoryNextTick(player);
        }
    }

    /**
     * 防丟棄安全：若啟用 deny-drop，禁止玩家將受保護物品按 Q 丟出。
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (!config.isDenyDrop()) {
            return;
        }

        ItemStack dropped = event.getItemDrop().getItemStack();
        if (itemMatcher.isProtected(dropped)) {
            event.setCancelled(true);
            Player player = event.getPlayer();
            feedbackService.sendDenyFeedback(player);
            feedbackService.syncInventoryNextTick(player);
        }
    }

    /**
     * 掉落實體生成防護：若允許丟出，實體生成時免疫火焰、熔岩、爆炸與異常實體銷毀。
     * 自動偵測並清理原版 /give 或指令方塊產生的 1-Tick 動畫假物品 (makeFakeItem)，徹底杜絕地上殘留撿不起來的幽靈物品。
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        Item itemEntity = event.getEntity();
        if (itemMatcher.isProtected(itemEntity.getItemStack())) {
            itemEntity.setInvulnerable(true);
            itemEntity.setCanMobPickup(false);

            // 排程於下一個 Tick 檢測是否為 /give 所產生的動畫假物品實體
            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                if (itemEntity.isValid()) {
                    if (isFakeItem(itemEntity)) {
                        // 動畫假物品在玩家獲得物品後應立即銷毀，不可留在地面
                        itemEntity.remove();
                    }
                }
            });
        }
    }

    /**
     * 判定是否為原版 /give 指令產生的動畫假物品實體。
     */
    private boolean isFakeItem(Item item) {
        // 原版 makeFakeItem 會將 pickupDelay 設為 Short.MAX_VALUE (32767) 且禁止撿拾，age 預設設為 5999
        return !item.canPlayerPickup() || item.getPickupDelay() >= 32767 || item.getTicksLived() >= 5990;
    }

    /**
     * 實體受損免疫：防止掉落物受到爆炸、岩漿、虛空、仙人掌等傷害銷毀。
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Item itemEntity) {
            if (itemMatcher.isProtected(itemEntity.getItemStack())) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * 實體燃燒免疫：防止掉落物因火焰燃燒銷毀。
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityCombust(EntityCombustEvent event) {
        if (event.getEntity() instanceof Item itemEntity) {
            if (itemMatcher.isProtected(itemEntity.getItemStack())) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * 掉落物自然消失免疫：
     * 若為動畫假物品則放行銷毀；若為真實掉落物且開啟了 prevent-despawn 才取消消失。
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemDespawn(ItemDespawnEvent event) {
        Item itemEntity = event.getEntity();
        if (itemMatcher.isProtected(itemEntity.getItemStack())) {
            // 動畫假物品必須允許自然消失，不可被攔截
            if (isFakeItem(itemEntity)) {
                return;
            }
            if (config.isPreventDespawn()) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * 玩家離線時清理反饋冷卻快取。
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        feedbackService.clearCooldown(event.getPlayer().getUniqueId());
    }
}
