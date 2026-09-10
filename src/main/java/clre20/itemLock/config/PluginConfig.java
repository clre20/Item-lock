package clre20.itemLock.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.util.Collections;
import java.util.List;

/**
 * 負責解析與管理 Item-lock 的全域設定檔。
 */
public class PluginConfig {

    private final Plugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.legacyAmpersand();

    private boolean denyDrop;
    private boolean denyAltarInteract;
    private boolean denyEntityInteract;

    private boolean allowHopperMove;
    private boolean allowHopperPickup;

    private boolean preventDespawn;

    private boolean actionbarEnabled;
    private String actionbarMessage;

    private boolean soundEnabled;
    private Sound soundType;
    private float soundVolume;
    private float soundPitch;

    private long cooldownMs;

    private String prefix;
    private String noPermission;
    private String playerOnly;
    private String reloadSuccess;
    private String addSuccess;
    private String addAlreadyExists;
    private String addOverwriteSuccess;
    private String addAir;
    private String addInvalidName;
    private String removeSuccess;
    private String removeNotFound;
    private String checkProtected;
    private String checkNotProtected;
    private String listHeader;
    private String listItem;
    private String listEmpty;
    private List<String> helpMessages;

    public PluginConfig(Plugin plugin) {
        this.plugin = plugin;
        load();
    }

    /**
     * 從磁碟重新載入設定。
     */
    @SuppressWarnings({"deprecation", "removal"})
    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        this.denyDrop = config.getBoolean("deny-drop", true);
        this.denyAltarInteract = config.getBoolean("deny-altar-interact", true);
        this.denyEntityInteract = config.getBoolean("deny-entity-interact", true);

        this.allowHopperMove = config.getBoolean("hopper.allow-move", false);
        this.allowHopperPickup = config.getBoolean("hopper.allow-pickup", false);

        this.preventDespawn = config.getBoolean("drop-protection.prevent-despawn", false);

        this.actionbarEnabled = config.getBoolean("feedback.actionbar.enabled", true);
        this.actionbarMessage = config.getString("feedback.actionbar.message",
                "<red>⚠ 此物品受特殊保護，無法在此介面中進行修改或加工！</red>");

        this.soundEnabled = config.getBoolean("feedback.sound.enabled", true);
        String soundName = config.getString("feedback.sound.type", "ENTITY_VILLAGER_NO");
        try {
            @SuppressWarnings("deprecation")
            Sound s = Sound.valueOf(soundName.toUpperCase());
            this.soundType = s;
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("找不到設定的音效: " + soundName + "，使用預設音效 ENTITY_VILLAGER_NO");
            this.soundType = Sound.ENTITY_VILLAGER_NO;
        }

        this.soundVolume = (float) config.getDouble("feedback.sound.volume", 1.0);
        this.soundPitch = (float) config.getDouble("feedback.sound.pitch", 0.8);
        this.cooldownMs = config.getLong("feedback.cooldown-ms", 1000L);

        this.prefix = config.getString("messages.prefix", "<gold>[Item-Lock]</gold> ");
        this.noPermission = config.getString("messages.no-permission", "<red>你沒有權限執行此指令！</red>");
        this.playerOnly = config.getString("messages.player-only", "<red>此指令僅能由玩家在遊戲內執行！</red>");
        this.reloadSuccess = config.getString("messages.reload-success",
                "<green>設定檔與物品樣本庫已成功重新載入！(共載入 <count> 個保護樣本)</green>");
        this.addSuccess = config.getString("messages.add-success",
                "<green>已成功將主手物品登錄為保護樣本：<gold><id></gold></green>");
        this.addAlreadyExists = config.getString("messages.add-already-exists",
                "<red>保護樣本 <gold><id></gold> 已存在！若要覆蓋請使用：<yellow>/itemlock add <id> -f</yellow></red>");
        this.addOverwriteSuccess = config.getString("messages.add-overwrite-success",
                "<green>已成功覆蓋並更新保護樣本：<gold><id></gold></green>");
        this.addAir = config.getString("messages.add-air", "<red>主手不可為空！請手持欲保護的物品。</red>");
        this.addInvalidName = config.getString("messages.add-invalid-name",
                "<red>名稱格式錯誤！僅能使用英數字、底線與連字符 (^[a-zA-Z0-9_-]+$)</red>");
        this.removeSuccess = config.getString("messages.remove-success",
                "<green>已成功刪除保護樣本：<gold><id></gold></green>");
        this.removeNotFound = config.getString("messages.remove-not-found",
                "<red>找不到名為 <gold><id></gold> 的保護樣本！</red>");
        this.checkProtected = config.getString("messages.check-protected",
                "<green>手持物品受特殊保護！匹配樣本：<gold><id></gold></green>");
        this.checkNotProtected = config.getString("messages.check-not-protected",
                "<yellow>手持物品不受保護。</yellow>");
        this.listHeader = config.getString("messages.list-header",
                "<gold>===== [ 已登錄的保護物品樣本 (<count>) ] =====</gold>");
        this.listItem = config.getString("messages.list-item",
                "<yellow>- <gold><id></gold> (<white><material></white>)</yellow>");
        this.listEmpty = config.getString("messages.list-empty",
                "<gray>目前尚未登錄任何保護物品樣本。</gray>");
        this.helpMessages = config.getStringList("messages.help");
        if (this.helpMessages.isEmpty()) {
            this.helpMessages = List.of(
                    "<gold>================ [ Item-Lock 指令手冊 ] ================",
                    "<yellow>/itemlock help <gray>- 顯示指令說明手冊",
                    "<yellow>/itemlock add <名稱> <gray>- 將主手物品登錄為保護樣本",
                    "<yellow>/itemlock remove <名稱> <gray>- 刪除指定的保護樣本",
                    "<yellow>/itemlock check <gray>- 檢測主手物品是否受保護",
                    "<yellow>/itemlock list <gray>- 列出所有已登錄的保護樣本",
                    "<yellow>/itemlock reload <gray>- 重新載入設定檔與保護樣本庫",
                    "<gold>========================================================"
            );
        }
    }

    /**
     * 將包含 MiniMessage 標籤或 Legacy 顏色代碼的字串解析為 Adventure Component。
     */
    public Component parseComponent(String text) {
        if (text == null) {
            return Component.empty();
        }
        if (text.contains("&") || text.contains("§")) {
            return legacySerializer.deserialize(text.replace("§", "&"));
        }
        return miniMessage.deserialize(text);
    }

    public boolean isDenyDrop() {
        return denyDrop;
    }

    public boolean isDenyAltarInteract() {
        return denyAltarInteract;
    }

    public boolean isDenyEntityInteract() {
        return denyEntityInteract;
    }

    public boolean isActionbarEnabled() {
        return actionbarEnabled;
    }

    public String getActionbarMessage() {
        return actionbarMessage;
    }

    public boolean isSoundEnabled() {
        return soundEnabled;
    }

    public Sound getSoundType() {
        return soundType;
    }

    public float getSoundVolume() {
        return soundVolume;
    }

    public float getSoundPitch() {
        return soundPitch;
    }

    public long getCooldownMs() {
        return cooldownMs;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getNoPermission() {
        return noPermission;
    }

    public String getPlayerOnly() {
        return playerOnly;
    }

    public String getReloadSuccess() {
        return reloadSuccess;
    }

    public boolean isAllowHopperMove() {
        return allowHopperMove;
    }

    public boolean isAllowHopperPickup() {
        return allowHopperPickup;
    }

    public boolean isPreventDespawn() {
        return preventDespawn;
    }

    public String getAddSuccess() {
        return addSuccess;
    }

    public String getAddAlreadyExists() {
        return addAlreadyExists;
    }

    public String getAddOverwriteSuccess() {
        return addOverwriteSuccess;
    }

    public String getAddAir() {
        return addAir;
    }

    public String getAddInvalidName() {
        return addInvalidName;
    }

    public String getRemoveSuccess() {
        return removeSuccess;
    }

    public String getRemoveNotFound() {
        return removeNotFound;
    }

    public String getCheckProtected() {
        return checkProtected;
    }

    public String getCheckNotProtected() {
        return checkNotProtected;
    }

    public String getListHeader() {
        return listHeader;
    }

    public String getListItem() {
        return listItem;
    }

    public String getListEmpty() {
        return listEmpty;
    }

    public List<String> getHelpMessages() {
        return Collections.unmodifiableList(helpMessages);
    }
}
