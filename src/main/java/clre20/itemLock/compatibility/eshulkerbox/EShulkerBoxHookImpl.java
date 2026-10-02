package clre20.itemLock.compatibility.eshulkerbox;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * 透過動態反射整合 eShulkerBox 外掛，精確識別手持/隨身開啟的潛影盒介面狀態。
 */
public class EShulkerBoxHookImpl implements EShulkerBoxHook {

    private Object shulkerBoxMechanic;
    private Field playersField;
    private Method isShulkerOpenMethod;
    private Method isReadOnlyMethod;
    private boolean reflectionReady = false;
    private boolean loggedHook = false;

    public EShulkerBoxHookImpl() {
        initReflection();
    }

    private synchronized void initReflection() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("eShulkerBox");
        if (plugin == null || !plugin.isEnabled()) {
            this.reflectionReady = false;
            return;
        }

        try {
            Method getMechanicMethod = plugin.getClass().getMethod("getShulkerBoxMechanic");
            this.shulkerBoxMechanic = getMechanicMethod.invoke(plugin);
            if (this.shulkerBoxMechanic != null) {
                this.playersField = this.shulkerBoxMechanic.getClass().getDeclaredField("players");
                this.playersField.setAccessible(true);

                Class<?> playerModelClass = plugin.getClass().getClassLoader().loadClass("cz.esb.shulkerbox.mechanic.shulker.model.ShulkerPlayer");
                this.isShulkerOpenMethod = playerModelClass.getMethod("isShulkerOpen");
                try {
                    this.isReadOnlyMethod = playerModelClass.getMethod("isReadOnly");
                } catch (NoSuchMethodException ignored) {
                    this.isReadOnlyMethod = null;
                }
                this.reflectionReady = true;
                if (!loggedHook) {
                    loggedHook = true;
                    Bukkit.getLogger().info("[Item-lock] 已成功動態掛接 eShulkerBox 插件！已啟用手持與背包快速開啟潛影盒相容支援。");
                }
            }
        } catch (Throwable ignored) {
            this.reflectionReady = false;
        }
    }

    @Override
    public boolean isAvailable() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("eShulkerBox");
        return plugin != null && plugin.isEnabled();
    }

    @Override
    public boolean isEShulkerBoxOpen(Player player, Inventory topInventory) {
        if (!isAvailable() || player == null || topInventory == null) {
            return false;
        }

        // 必須為潛影盒類型
        if (topInventory.getType() != InventoryType.SHULKER_BOX) {
            return false;
        }

        // eShulkerBox 手持/隨身開啟的介面非世界方塊實體 (holder 非 BlockState 且 location == null)
        if (topInventory.getHolder() instanceof org.bukkit.block.BlockState || topInventory.getLocation() != null) {
            return false;
        }

        // 若尚未就緒，嘗試再次初始化（防範外掛重載後參考失效）
        if (!reflectionReady || shulkerBoxMechanic == null) {
            initReflection();
        }

        // 若反射成功，精準查詢 eShulkerBox 內部玩家狀態
        if (reflectionReady && playersField != null && isShulkerOpenMethod != null) {
            try {
                @SuppressWarnings("unchecked")
                Map<Player, ?> playersMap = (Map<Player, ?>) playersField.get(shulkerBoxMechanic);
                if (playersMap != null) {
                    Object shulkerPlayer = playersMap.get(player);
                    if (shulkerPlayer == null) {
                        for (Map.Entry<Player, ?> entry : playersMap.entrySet()) {
                            if (entry.getKey() != null && entry.getKey().getUniqueId().equals(player.getUniqueId())) {
                                shulkerPlayer = entry.getValue();
                                break;
                            }
                        }
                    }
                    if (shulkerPlayer != null) {
                        boolean isOpen = (Boolean) isShulkerOpenMethod.invoke(shulkerPlayer);
                        if (isOpen) {
                            if (isReadOnlyMethod != null) {
                                boolean readOnly = (Boolean) isReadOnlyMethod.invoke(shulkerPlayer);
                                if (readOnly) {
                                    return false;
                                }
                            }
                            return true;
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        // 特徵回退判定：eShulkerBox 處於啟用狀態，且該介面為標準無實體虛擬潛影盒
        return true;
    }
}
