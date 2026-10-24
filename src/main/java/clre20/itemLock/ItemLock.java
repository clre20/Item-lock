package clre20.itemLock;

import clre20.itemLock.command.ItemLockCommand;
import clre20.itemLock.compatibility.HookRefreshListener;
import clre20.itemLock.compatibility.advancedspyinventory.AdvancedSpyInventoryHook;
import clre20.itemLock.compatibility.eshulkerbox.EShulkerBoxHook;
import clre20.itemLock.compatibility.shopkeepers.ShopkeepersHook;
import clre20.itemLock.config.Messages;
import clre20.itemLock.config.PluginConfig;
import clre20.itemLock.feedback.FeedbackService;
import clre20.itemLock.gui.GuiListener;
import clre20.itemLock.listener.AutomationSecurityListener;
import clre20.itemLock.listener.CraftingSecurityListener;
import clre20.itemLock.listener.InventorySecurityListener;
import clre20.itemLock.listener.OffhandAndPlacementSecurityListener;
import clre20.itemLock.listener.PlayerListener;
import clre20.itemLock.listener.WorldInteractionListener;
import clre20.itemLock.matcher.ItemMatcher;
import clre20.itemLock.template.TemplateManager;
import clre20.itemLock.update.UpdateChecker;
import clre20.itemLock.util.Log;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Item-lock：保護指定物品不被加工、修改或放到不該放的地方。
 */
public final class ItemLock extends JavaPlugin {

    private PluginConfig pluginConfig;
    private Messages messages;
    private ItemMatcher itemMatcher;
    private TemplateManager templateManager;
    private FeedbackService feedbackService;
    private UpdateChecker updateChecker;

    @Override
    public void onEnable() {
        messages = new Messages(this);
        Log.init(messages);
        pluginConfig = new PluginConfig(this);
        warnConfig();

        itemMatcher = new ItemMatcher();
        templateManager = new TemplateManager(this, itemMatcher);
        int count = templateManager.loadAll();
        templateManager.start();

        feedbackService = new FeedbackService(this, pluginConfig, messages);
        updateChecker = new UpdateChecker(this, messages);

        EShulkerBoxHook eShulkerBox = EShulkerBoxHook.create();
        ShopkeepersHook shopkeepers = ShopkeepersHook.create();
        AdvancedSpyInventoryHook spyInventory = AdvancedSpyInventoryHook.create();

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new GuiListener(this), this);
        pm.registerEvents(new InventorySecurityListener(this, pluginConfig, itemMatcher, feedbackService, shopkeepers, eShulkerBox, spyInventory), this);
        pm.registerEvents(new CraftingSecurityListener(itemMatcher, feedbackService), this);
        pm.registerEvents(new AutomationSecurityListener(pluginConfig, itemMatcher), this);
        pm.registerEvents(new WorldInteractionListener(this, pluginConfig, itemMatcher, feedbackService, shopkeepers), this);
        pm.registerEvents(new OffhandAndPlacementSecurityListener(pluginConfig, itemMatcher, feedbackService), this);
        pm.registerEvents(new PlayerListener(pluginConfig, feedbackService, updateChecker), this);
        pm.registerEvents(new HookRefreshListener(shopkeepers, eShulkerBox, spyInventory), this);

        PluginCommand command = getCommand("itemlock");
        if (command != null) {
            ItemLockCommand executor = new ItemLockCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        Log.console("console.enabled", "version", updateChecker.getCurrent(), "count", count);
        if (pluginConfig.isCheckOnStartup()) {
            updateChecker.logOnStartup();
        }
    }

    @Override
    public void onDisable() {
        GuiListener.closeAll(this);
        if (templateManager != null) {
            templateManager.shutdown();
        }
        if (updateChecker != null) {
            updateChecker.shutdown();
        }
        if (feedbackService != null) {
            feedbackService.clearAll();
        }
        Bukkit.getScheduler().cancelTasks(this);
        Log.console("console.disabled");
    }

    /** /itemlock reload：重新讀取並補齊 config.yml、messages.yml，重新載入樣本。回傳樣本數。 */
    public int reloadAll() {
        messages.load();
        pluginConfig.load();
        warnConfig();
        feedbackService.reload();
        return templateManager.loadAll();
    }

    private void warnConfig() {
        String bad = pluginConfig.getInvalidSoundName();
        if (bad != null) {
            Log.console("console.invalid-sound", "sound", bad);
        }
    }

    public PluginConfig getPluginConfig() { return pluginConfig; }
    public Messages getMessages() { return messages; }
    public ItemMatcher getItemMatcher() { return itemMatcher; }
    public TemplateManager getTemplateManager() { return templateManager; }
    public FeedbackService getFeedbackService() { return feedbackService; }
    public UpdateChecker getUpdateChecker() { return updateChecker; }
}
