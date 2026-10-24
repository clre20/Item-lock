package clre20.itemLock.feedback;

import clre20.itemLock.config.Messages;
import clre20.itemLock.config.PluginConfig;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 被擋下時的提示（動作列＋音效，有冷卻）與下一 tick 的背包同步。
 * 只在主執行緒使用；只存 UUID，玩家離線時清除。
 */
public final class FeedbackService {

    public enum Deny { DEFAULT, OFFHAND, BLOCK_PLACE }

    private final Plugin plugin;
    private final PluginConfig config;
    private final Messages messages;
    private final Map<UUID, Long> lastAlert = new HashMap<>();
    private final Set<UUID> pendingSync = new HashSet<>();
    private Component denyDefault;
    private Component denyOffhand;
    private Component denyBlockPlace;

    public FeedbackService(Plugin plugin, PluginConfig config, Messages messages) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        reload();
    }

    /** 訊息只在載入時解析一次。 */
    public void reload() {
        denyDefault = messages.get("deny.default");
        denyOffhand = messages.get("deny.offhand");
        denyBlockPlace = messages.get("deny.block-place");
    }

    /** 提示＋下一 tick 同步背包（大部分攔截的標準處理）。 */
    public void deny(Player player, Deny type) {
        alert(player, type);
        syncNextTick(player);
    }

    public void alert(Player player, Deny type) {
        long now = System.currentTimeMillis();
        UUID id = player.getUniqueId();
        Long last = lastAlert.get(id);
        if (last != null && now - last < config.getCooldownMs()) {
            return;
        }
        lastAlert.put(id, now);

        if (config.isActionbarEnabled()) {
            player.sendActionBar(switch (type) {
                case OFFHAND -> denyOffhand;
                case BLOCK_PLACE -> denyBlockPlace;
                default -> denyDefault;
            });
        }
        if (config.isSoundEnabled()) {
            player.playSound(player.getLocation(), config.getSound(), config.getSoundVolume(), config.getSoundPitch());
        }
    }

    /** 同一玩家同一 tick 只排一次 updateInventory。 */
    public void syncNextTick(Player player) {
        UUID id = player.getUniqueId();
        if (!pendingSync.add(id)) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            pendingSync.remove(id);
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.updateInventory();
            }
        });
    }

    public void clear(UUID id) {
        lastAlert.remove(id);
    }

    public void clearAll() {
        lastAlert.clear();
        pendingSync.clear();
    }
}
