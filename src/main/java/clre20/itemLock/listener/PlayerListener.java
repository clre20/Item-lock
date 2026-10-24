package clre20.itemLock.listener;

import clre20.itemLock.config.PluginConfig;
import clre20.itemLock.feedback.FeedbackService;
import clre20.itemLock.update.UpdateChecker;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * 登入：有新版本時提醒管理員（3 秒後）。離線：清除該玩家的快取。
 */
public final class PlayerListener implements Listener {

    public static final String VERSION_PERMISSION = "itemlock.version";

    private final PluginConfig config;
    private final FeedbackService feedback;
    private final UpdateChecker updates;

    public PlayerListener(PluginConfig config, FeedbackService feedback, UpdateChecker updates) {
        this.config = config;
        this.feedback = feedback;
        this.updates = updates;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (config.isNotifyOnJoin() && player.hasPermission(VERSION_PERMISSION)) {
            updates.notifyLater(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        feedback.clear(event.getPlayer().getUniqueId());
    }
}
