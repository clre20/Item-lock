package clre20.itemLock.listener;

import clre20.itemLock.compatibility.shopkeepers.ShopkeepersHook;
import clre20.itemLock.config.PluginConfig;
import clre20.itemLock.feedback.FeedbackService;
import clre20.itemLock.feedback.FeedbackService.Deny;
import clre20.itemLock.matcher.ItemMatcher;
import clre20.itemLock.model.ItemTemplate;
import clre20.itemLock.security.ContainerWhitelist;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;

/**
 * 世界互動：右鍵機器方塊、展示框／盔甲架、丟出、掉落物免疫。
 * 每個事件都先做最便宜的判斷（動作類型、實體類型），再比對物品。
 */
public final class WorldInteractionListener implements Listener {

    private final Plugin plugin;
    private final PluginConfig config;
    private final ItemMatcher matcher;
    private final FeedbackService feedback;
    private final ShopkeepersHook shopkeepers;

    public WorldInteractionListener(Plugin plugin, PluginConfig config, ItemMatcher matcher,
                                    FeedbackService feedback, ShopkeepersHook shopkeepers) {
        this.plugin = plugin;
        this.config = config;
        this.matcher = matcher;
        this.feedback = feedback;
        this.shopkeepers = shopkeepers;
    }

    /** 手持受保護物品右鍵非儲存方塊（祭壇、附魔台、合成台等）時取消。 */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteractBlock(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemTemplate t = matcher.find(event.getItem());
        if (t == null || !t.getSettings().isDenyAltarInteract(config)) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        if (shopkeepers.isAvailable() && shopkeepers.isShopkeeperBlock(block)) {
            return;
        }
        // 門、按鈕等：可以切換，但不使用手上的物品（避免潛行右鍵放置）
        if (ContainerWhitelist.isSimpleInteractableBlock(block)) {
            event.setUseItemInHand(Event.Result.DENY);
            return;
        }
        if (!ContainerWhitelist.isPureStorageBlock(block)) {
            event.setCancelled(true);
            feedback.deny(event.getPlayer(), Deny.DEFAULT);
        }
    }

    /** 不能放進展示框或穿到盔甲架上。 */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Entity target = event.getRightClicked();
        if (!(target instanceof ItemFrame) && !(target instanceof ArmorStand)) {
            return;
        }
        Player player = event.getPlayer();
        PlayerInventory inv = player.getInventory();
        if (!denyEntity(inv.getItemInMainHand()) && !denyEntity(inv.getItemInOffHand())) {
            return;
        }
        if (shopkeepers.isAvailable() && shopkeepers.isShopkeeperEntity(target)) {
            return;
        }
        event.setCancelled(true);
        feedback.deny(player, Deny.DEFAULT);
    }

    private boolean denyEntity(ItemStack item) {
        ItemTemplate t = matcher.find(item);
        return t != null && t.getSettings().isDenyEntityInteract(config);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        ItemTemplate t = matcher.find(event.getItemDrop().getItemStack());
        if (t != null && t.getSettings().isDenyDrop(config)) {
            event.setCancelled(true);
            feedback.deny(event.getPlayer(), Deny.DEFAULT);
        }
    }

    /**
     * 受保護的掉落物：無敵、生物不能撿。
     * /give 產生的動畫假物品（撿不起來）下一 tick 移除，避免地上留下撿不起來的物品。
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        Item entity = event.getEntity();
        if (matcher.find(entity.getItemStack()) == null) {
            return;
        }
        entity.setInvulnerable(true);
        entity.setCanMobPickup(false);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (entity.isValid() && isFakeItem(entity)) {
                entity.remove();
            }
        });
    }

    /** 原版 /give 動畫物品：pickupDelay 32767 且 age 5999。 */
    private static boolean isFakeItem(Item item) {
        return !item.canPlayerPickup() || item.getPickupDelay() >= 32767 || item.getTicksLived() >= 5990;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Item item && matcher.find(item.getItemStack()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityCombust(EntityCombustEvent event) {
        if (event.getEntity() instanceof Item item && matcher.find(item.getItemStack()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemDespawn(ItemDespawnEvent event) {
        if (!config.isPreventDespawn()) {
            return;
        }
        Item item = event.getEntity();
        if (matcher.find(item.getItemStack()) != null && !isFakeItem(item)) {
            event.setCancelled(true);
        }
    }
}
