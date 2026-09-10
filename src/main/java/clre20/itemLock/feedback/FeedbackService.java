package clre20.itemLock.feedback;

import clre20.itemLock.config.PluginConfig;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 防幽靈物品與回饋體驗服務 (UX & Anti-Desync)。
 * 提供 1-Tick 延遲庫存同步、Actionbar 警示、音效打擊反饋與每秒冷卻防刷機制。
 */
public class FeedbackService {

    private final Plugin plugin;
    private final PluginConfig config;
    private final Map<UUID, Long> lastAlertTimes = new ConcurrentHashMap<>();

    public FeedbackService(Plugin plugin, PluginConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    /**
     * 當操作被拒絕時，觸發警示回饋（受冷卻時間限制）。
     */
    public void sendDenyFeedback(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        long now = System.currentTimeMillis();
        long lastTime = lastAlertTimes.getOrDefault(player.getUniqueId(), 0L);

        if (now - lastTime < config.getCooldownMs()) {
            return;
        }

        lastAlertTimes.put(player.getUniqueId(), now);

        // Actionbar 警示
        if (config.isActionbarEnabled()) {
            Component message = config.parseComponent(config.getActionbarMessage());
            player.sendActionBar(message);
        }

        // 音效打擊反饋
        if (config.isSoundEnabled()) {
            player.playSound(
                    player.getLocation(),
                    config.getSoundType(),
                    config.getSoundVolume(),
                    config.getSoundPitch()
            );
        }
    }

    /**
     * 下 1 個 Tick 強制執行 player.updateInventory()，杜絕客戶端預測產生的幽靈物品。
     */
    public void syncInventoryNextTick(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                player.updateInventory();
            }
        });
    }

    /**
     * 清理玩家離線時的冷卻快取。
     */
    public void clearCooldown(UUID uuid) {
        lastAlertTimes.remove(uuid);
    }
}
