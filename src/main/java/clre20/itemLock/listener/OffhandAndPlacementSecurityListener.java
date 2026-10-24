package clre20.itemLock.listener;

import clre20.itemLock.config.PluginConfig;
import clre20.itemLock.feedback.FeedbackService;
import clre20.itemLock.feedback.FeedbackService.Deny;
import clre20.itemLock.matcher.ItemMatcher;
import clre20.itemLock.model.ItemTemplate;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Map;

/**
 * 副手與方塊放置：F 換手、/geyser offhand、副手右鍵、放置方塊；副手殘留時自動移回背包。
 */
public final class OffhandAndPlacementSecurityListener implements Listener {

    private final PluginConfig config;
    private final ItemMatcher matcher;
    private final FeedbackService feedback;

    public OffhandAndPlacementSecurityListener(PluginConfig config, ItemMatcher matcher, FeedbackService feedback) {
        this.config = config;
        this.matcher = matcher;
        this.feedback = feedback;
    }

    private boolean denyOffhand(ItemStack item) {
        ItemTemplate t = matcher.find(item);
        return t != null && t.getSettings().isDenyOffhand(config);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        if (denyOffhand(event.getMainHandItem()) || denyOffhand(event.getOffHandItem())) {
            event.setCancelled(true);
            feedback.deny(event.getPlayer(), Deny.OFFHAND);
        }
    }

    /** 基岩版常用的 /geyser offhand、/offhand。 */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        String msg = event.getMessage();
        if (!startsWithIgnoreCase(msg, "/geyser offhand") && !startsWithIgnoreCase(msg, "/offhand")) {
            return;
        }
        Player player = event.getPlayer();
        if (denyOffhand(player.getInventory().getItemInMainHand())) {
            event.setCancelled(true);
            feedback.deny(player, Deny.OFFHAND);
        }
    }

    private static boolean startsWithIgnoreCase(String s, String prefix) {
        return s.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemTemplate t = matcher.find(event.getItemInHand());
        if (t == null) {
            return;
        }
        if (event.getHand() == EquipmentSlot.OFF_HAND) {
            event.setCancelled(true);
            feedback.deny(event.getPlayer(), Deny.OFFHAND);
            return;
        }
        if (t.getSettings().isDenyBlockPlace(config)) {
            event.setCancelled(true);
            feedback.deny(event.getPlayer(), Deny.BLOCK_PLACE);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.OFF_HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (denyOffhand(offhand)) {
            event.setCancelled(true);
            feedback.deny(player, Deny.OFFHAND);
            ejectOffhand(player, offhand);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (denyOffhand(offhand)) {
            ejectOffhand(player, offhand);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            ItemStack offhand = player.getInventory().getItemInOffHand();
            if (denyOffhand(offhand)) {
                ejectOffhand(player, offhand);
            }
        }
    }

    /** 副手有禁止副手的物品時移回背包，滿了就掉在腳下。 */
    private void ejectOffhand(Player player, ItemStack offhand) {
        if (offhand == null || offhand.getType().isAir()) {
            return;
        }
        PlayerInventory inv = player.getInventory();
        inv.setItemInOffHand(null);
        Map<Integer, ItemStack> left = inv.addItem(offhand);
        for (ItemStack drop : left.values()) {
            player.getWorld().dropItem(player.getLocation(), drop);
        }
        feedback.syncNextTick(player);
    }
}
