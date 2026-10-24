package clre20.itemLock.config;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

/**
 * config.yml 的值。每次讀取都先補齊缺少的項目；所有值都有預設，不會出現 null。
 */
public final class PluginConfig {

    private final JavaPlugin plugin;

    private boolean denyDrop;
    private boolean denyOffhand;
    private boolean denyBlockPlace;
    private boolean denyAltarInteract;
    private boolean denyEntityInteract;
    private boolean allowHopperMove;
    private boolean allowHopperPickup;
    private boolean preventDespawn;

    private boolean allowShopkeepersTrading;
    private boolean allowShopkeepersEditor;
    private boolean allowEShulkerBox;
    private boolean allowVirtualShulkerBox;
    private boolean allowSpyPlayerInventory;
    private boolean allowSpyEnderChest;
    private boolean allowSpyEntityInventory;
    private boolean allowCmail;

    private boolean actionbarEnabled;
    private boolean soundEnabled;
    private Sound sound = Sound.ENTITY_VILLAGER_NO;
    private String invalidSoundName;
    private float soundVolume;
    private float soundPitch;
    private long cooldownMs;

    private boolean guiSounds;
    private boolean checkOnStartup;
    private boolean notifyOnJoin;

    public PluginConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        YamlConfiguration c = YamlUpdater.load(plugin, "config.yml");

        denyDrop = c.getBoolean("deny-drop", false);
        denyOffhand = c.getBoolean("deny-offhand", false);
        denyBlockPlace = c.getBoolean("deny-block-place", true);
        denyAltarInteract = c.getBoolean("deny-altar-interact", true);
        denyEntityInteract = c.getBoolean("deny-entity-interact", false);
        allowHopperMove = c.getBoolean("hopper.allow-move", true);
        allowHopperPickup = c.getBoolean("hopper.allow-pickup", true);
        preventDespawn = c.getBoolean("drop-protection.prevent-despawn", false);

        allowShopkeepersTrading = c.getBoolean("compatibility.shopkeepers.allow-trading", true);
        allowShopkeepersEditor = c.getBoolean("compatibility.shopkeepers.allow-editor", true);
        allowEShulkerBox = c.getBoolean("compatibility.eshulkerbox.allow-handheld-shulkerbox", true);
        allowVirtualShulkerBox = c.getBoolean("compatibility.virtual-shulkerbox.allow-virtual", true);
        allowSpyPlayerInventory = c.getBoolean("compatibility.advancedspyinventory.allow-player-inventory", true);
        allowSpyEnderChest = c.getBoolean("compatibility.advancedspyinventory.allow-ender-chest", true);
        allowSpyEntityInventory = c.getBoolean("compatibility.advancedspyinventory.allow-entity-inventory", false);
        allowCmail = c.getBoolean("compatibility.cmail.allow-mail", true);

        actionbarEnabled = c.getBoolean("feedback.actionbar.enabled", true);
        soundEnabled = c.getBoolean("feedback.sound.enabled", true);
        String soundName = c.getString("feedback.sound.type", "ENTITY_VILLAGER_NO");
        Sound parsed = parseSound(soundName);
        invalidSoundName = parsed == null ? soundName : null;
        sound = parsed != null ? parsed : Sound.ENTITY_VILLAGER_NO;
        soundVolume = clamp((float) c.getDouble("feedback.sound.volume", 1.0), 0f, 10f);
        soundPitch = clamp((float) c.getDouble("feedback.sound.pitch", 0.8), 0.5f, 2f);
        cooldownMs = Math.max(0L, c.getLong("feedback.cooldown-ms", 1000L));

        guiSounds = c.getBoolean("gui.sounds", true);
        checkOnStartup = c.getBoolean("update-checker.check-on-startup", true);
        notifyOnJoin = c.getBoolean("update-checker.notify-on-join", true);
    }

    /** 接受 ENTITY_VILLAGER_NO 或 entity.villager.no 兩種寫法。 */
    private static Sound parseSound(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String key = name.trim().toLowerCase(Locale.ROOT);
        if (!key.contains(".")) {
            key = key.replace('_', '.');
        }
        NamespacedKey nk = NamespacedKey.fromString(key);
        if (nk == null) {
            return null;
        }
        Sound s = Registry.SOUNDS.get(nk);
        if (s != null) {
            return s;
        }
        // 底線本身是名稱一部分的音效（例如 ui.button.click 以外的特殊名稱）退回列舉名稱比對
        for (Sound candidate : Registry.SOUNDS) {
            String enumName = candidate.getKey().getKey().replace('.', '_').toUpperCase(Locale.ROOT);
            if (enumName.equals(name.trim().toUpperCase(Locale.ROOT))) {
                return candidate;
            }
        }
        return null;
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    /** 讀取時找不到的音效名稱（沒有則為 null），由主類別輸出警告。 */
    public String getInvalidSoundName() { return invalidSoundName; }

    public boolean isDenyDrop() { return denyDrop; }
    public boolean isDenyOffhand() { return denyOffhand; }
    public boolean isDenyBlockPlace() { return denyBlockPlace; }
    public boolean isDenyAltarInteract() { return denyAltarInteract; }
    public boolean isDenyEntityInteract() { return denyEntityInteract; }
    public boolean isAllowHopperMove() { return allowHopperMove; }
    public boolean isAllowHopperPickup() { return allowHopperPickup; }
    public boolean isPreventDespawn() { return preventDespawn; }
    public boolean isAllowShopkeepersTrading() { return allowShopkeepersTrading; }
    public boolean isAllowShopkeepersEditor() { return allowShopkeepersEditor; }
    public boolean isAllowEShulkerBox() { return allowEShulkerBox; }
    public boolean isAllowVirtualShulkerBox() { return allowVirtualShulkerBox; }
    public boolean isAllowSpyPlayerInventory() { return allowSpyPlayerInventory; }
    public boolean isAllowSpyEnderChest() { return allowSpyEnderChest; }
    public boolean isAllowSpyEntityInventory() { return allowSpyEntityInventory; }
    public boolean isAllowCmail() { return allowCmail; }
    public boolean isActionbarEnabled() { return actionbarEnabled; }
    public boolean isSoundEnabled() { return soundEnabled; }
    public Sound getSound() { return sound; }
    public float getSoundVolume() { return soundVolume; }
    public float getSoundPitch() { return soundPitch; }
    public long getCooldownMs() { return cooldownMs; }
    public boolean isGuiSounds() { return guiSounds; }
    public boolean isCheckOnStartup() { return checkOnStartup; }
    public boolean isNotifyOnJoin() { return notifyOnJoin; }
}
