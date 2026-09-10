package clre20.itemLock;

import clre20.itemLock.command.ItemLockCommand;
import clre20.itemLock.command.ItemLockTabCompleter;
import clre20.itemLock.config.PluginConfig;
import clre20.itemLock.feedback.FeedbackService;
import clre20.itemLock.listener.AutomationSecurityListener;
import clre20.itemLock.listener.CraftingSecurityListener;
import clre20.itemLock.listener.InventorySecurityListener;
import clre20.itemLock.listener.WorldInteractionListener;
import clre20.itemLock.matcher.ItemMatcher;
import clre20.itemLock.template.TemplateManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Item-lock 主插件類別。
 * 實現零信任容器安全與動態遮罩特徵比對的物品保護系統。
 */
public final class ItemLock extends JavaPlugin {

    private static ItemLock instance;

    private PluginConfig pluginConfig;
    private TemplateManager templateManager;
    private ItemMatcher itemMatcher;
    private FeedbackService feedbackService;

    public static ItemLock getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        // 1. 初始化設定檔與樣本庫
        this.pluginConfig = new PluginConfig(this);
        this.templateManager = new TemplateManager(this);
        this.templateManager.loadAll();

        // 2. 初始化比對引擎與反饋服務
        this.itemMatcher = new ItemMatcher(this.templateManager);
        this.feedbackService = new FeedbackService(this, this.pluginConfig);

        // 3. 註冊安全監聽器
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new InventorySecurityListener(this.itemMatcher, this.feedbackService), this);
        pm.registerEvents(new CraftingSecurityListener(this.itemMatcher, this.feedbackService), this);
        pm.registerEvents(new AutomationSecurityListener(this.pluginConfig, this.itemMatcher), this);
        pm.registerEvents(new WorldInteractionListener(this, this.pluginConfig, this.itemMatcher, this.feedbackService), this);

        // 4. 註冊指令與智慧補全
        PluginCommand command = getCommand("itemlock");
        if (command != null) {
            command.setExecutor(new ItemLockCommand(this.pluginConfig, this.templateManager, this.itemMatcher));
            command.setTabCompleter(new ItemLockTabCompleter(this.templateManager));
        } else {
            getLogger().severe("無法找到 plugin.yml 中的指令 'itemlock'，請檢查設定！");
        }

        getLogger().info("Item-lock 插件已成功載入！零信任安全網已啟動。");
    }

    @Override
    public void onDisable() {
        getLogger().info("Item-lock 插件已卸載。");
        instance = null;
    }

    public PluginConfig getPluginConfig() {
        return pluginConfig;
    }

    public TemplateManager getTemplateManager() {
        return templateManager;
    }

    public ItemMatcher getItemMatcher() {
        return itemMatcher;
    }

    public FeedbackService getFeedbackService() {
        return feedbackService;
    }
}
