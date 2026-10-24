package clre20.itemLock.compatibility.advancedspyinventory;

import org.bukkit.Bukkit;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

/**
 * 透過動態反射整合 AdvancedSpyInventory 外掛。
 * 呼叫其靜態方法 BaseInventory.fromView(InventoryView) 取得介面背後的容器物件，
 * 再依容器類別判定為玩家背包、末影箱或生物背包介面。
 */
public class AdvancedSpyInventoryHookImpl implements AdvancedSpyInventoryHook {

    private static final String PLUGIN_NAME = "AdvancedSpyInventory";
    private static final String BASE_PACKAGE = "mc.dailycraft.advancedspyinventory.";

    private Method fromViewMethod;
    private Class<?> playerInventoryClass;
    private Class<?> enderChestInventoryClass;
    private ClassLoader hookedClassLoader;
    private boolean loggedHook = false;
    private volatile boolean available;

    public AdvancedSpyInventoryHookImpl() {
        refresh();
    }

    @Override
    public void disable() {
        available = false;
        fromViewMethod = null;
    }

    @Override
    public void refresh() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
        available = plugin != null && plugin.isEnabled();
        if (available) {
            initReflection();
        } else {
            fromViewMethod = null;
        }
    }

    private synchronized void initReflection() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
        if (plugin == null || !plugin.isEnabled()) {
            this.fromViewMethod = null;
            return;
        }

        ClassLoader loader = plugin.getClass().getClassLoader();
        if (this.fromViewMethod != null && loader == this.hookedClassLoader) {
            return;
        }

        try {
            Class<?> baseInventoryClass = loader.loadClass(BASE_PACKAGE + "inventory.BaseInventory");
            this.fromViewMethod = baseInventoryClass.getMethod("fromView", InventoryView.class);
            this.playerInventoryClass = loader.loadClass(BASE_PACKAGE + "inventory.PlayerInventory");
            this.enderChestInventoryClass = loader.loadClass(BASE_PACKAGE + "inventory.EnderChestInventory");
            this.hookedClassLoader = loader;
            if (!loggedHook) {
                loggedHook = true;
                clre20.itemLock.util.Log.console("console.hooked", "plugin", PLUGIN_NAME);
            }
        } catch (Throwable ignored) {
            this.fromViewMethod = null;
        }
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public SpyType getSpyType(InventoryView view) {
        if (!available || view == null || !view.getClass().getName().startsWith(BASE_PACKAGE)) {
            return SpyType.NONE;
        }
        if (fromViewMethod == null) {
            initReflection();
        }
        if (fromViewMethod == null) {
            return SpyType.NONE;
        }

        try {
            Object container = fromViewMethod.invoke(null, view);
            if (container == null) {
                return SpyType.NONE;
            }
            if (playerInventoryClass.isInstance(container)) {
                return SpyType.PLAYER;
            }
            if (enderChestInventoryClass.isInstance(container)) {
                return SpyType.ENDER_CHEST;
            }
            return SpyType.ENTITY;
        } catch (Throwable ignored) {
            return SpyType.NONE;
        }
    }
}
