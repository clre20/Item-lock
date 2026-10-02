package clre20.itemLock.model;

import clre20.itemLock.config.PluginConfig;
import org.bukkit.configuration.ConfigurationSection;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 每個保護物品樣本的個別自訂防護開關。
 * 採用三態設計：
 * - null: 繼承全域 config.yml 設定 (GLOBAL)
 * - true: 強制啟用 (ENFORCE_TRUE)
 * - false: 強制停用 (ENFORCE_FALSE)
 */
public class TemplateSettings {

    private Boolean denyDrop;
    private Boolean denyOffhand;
    private Boolean denyBlockPlace;
    private Boolean denyAltarInteract;
    private Boolean denyEntityInteract;
    private Boolean allowHopperMove;
    private Boolean allowHopperPickup;

    public TemplateSettings() {
    }

    public TemplateSettings(Boolean denyDrop, Boolean denyOffhand, Boolean denyBlockPlace,
                            Boolean denyAltarInteract, Boolean denyEntityInteract,
                            Boolean allowHopperMove, Boolean allowHopperPickup) {
        this.denyDrop = denyDrop;
        this.denyOffhand = denyOffhand;
        this.denyBlockPlace = denyBlockPlace;
        this.denyAltarInteract = denyAltarInteract;
        this.denyEntityInteract = denyEntityInteract;
        this.allowHopperMove = allowHopperMove;
        this.allowHopperPickup = allowHopperPickup;
    }

    // --- 三態切換輔助方法 (null -> true -> false -> null) ---
    public static Boolean cycle(Boolean current) {
        if (current == null) {
            return Boolean.TRUE;
        } else if (current) {
            return Boolean.FALSE;
        } else {
            return null;
        }
    }

    // --- 有效值解析方法 (若為 null 則取全域設定) ---

    public boolean isDenyDrop(PluginConfig config) {
        return denyDrop != null ? denyDrop : config.isDenyDrop();
    }

    public boolean isDenyOffhand(PluginConfig config) {
        return denyOffhand != null ? denyOffhand : config.isDenyOffhand();
    }

    public boolean isDenyBlockPlace(PluginConfig config) {
        return denyBlockPlace != null ? denyBlockPlace : config.isDenyBlockPlace();
    }

    public boolean isDenyAltarInteract(PluginConfig config) {
        return denyAltarInteract != null ? denyAltarInteract : config.isDenyAltarInteract();
    }

    public boolean isDenyEntityInteract(PluginConfig config) {
        return denyEntityInteract != null ? denyEntityInteract : config.isDenyEntityInteract();
    }

    public boolean isAllowHopperMove(PluginConfig config) {
        return allowHopperMove != null ? allowHopperMove : config.isAllowHopperMove();
    }

    public boolean isAllowHopperPickup(PluginConfig config) {
        return allowHopperPickup != null ? allowHopperPickup : config.isAllowHopperPickup();
    }

    // --- YAML 序列化與反序列化 ---

    public static TemplateSettings fromSection(ConfigurationSection section) {
        TemplateSettings settings = new TemplateSettings();
        if (section == null) {
            return settings;
        }

        if (section.contains("deny-drop")) {
            settings.setDenyDrop(section.getBoolean("deny-drop"));
        }
        if (section.contains("deny-offhand")) {
            settings.setDenyOffhand(section.getBoolean("deny-offhand"));
        }
        if (section.contains("deny-block-place")) {
            settings.setDenyBlockPlace(section.getBoolean("deny-block-place"));
        }
        if (section.contains("deny-altar-interact")) {
            settings.setDenyAltarInteract(section.getBoolean("deny-altar-interact"));
        }
        if (section.contains("deny-entity-interact")) {
            settings.setDenyEntityInteract(section.getBoolean("deny-entity-interact"));
        }
        if (section.contains("allow-hopper-move")) {
            settings.setAllowHopperMove(section.getBoolean("allow-hopper-move"));
        }
        if (section.contains("allow-hopper-pickup")) {
            settings.setAllowHopperPickup(section.getBoolean("allow-hopper-pickup"));
        }

        return settings;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        if (denyDrop != null) map.put("deny-drop", denyDrop);
        if (denyOffhand != null) map.put("deny-offhand", denyOffhand);
        if (denyBlockPlace != null) map.put("deny-block-place", denyBlockPlace);
        if (denyAltarInteract != null) map.put("deny-altar-interact", denyAltarInteract);
        if (denyEntityInteract != null) map.put("deny-entity-interact", denyEntityInteract);
        if (allowHopperMove != null) map.put("allow-hopper-move", allowHopperMove);
        if (allowHopperPickup != null) map.put("allow-hopper-pickup", allowHopperPickup);
        return map;
    }

    // --- Getters and Setters ---

    public Boolean getDenyDrop() {
        return denyDrop;
    }

    public void setDenyDrop(Boolean denyDrop) {
        this.denyDrop = denyDrop;
    }

    public Boolean getDenyOffhand() {
        return denyOffhand;
    }

    public void setDenyOffhand(Boolean denyOffhand) {
        this.denyOffhand = denyOffhand;
    }

    public Boolean getDenyBlockPlace() {
        return denyBlockPlace;
    }

    public void setDenyBlockPlace(Boolean denyBlockPlace) {
        this.denyBlockPlace = denyBlockPlace;
    }

    public Boolean getDenyAltarInteract() {
        return denyAltarInteract;
    }

    public void setDenyAltarInteract(Boolean denyAltarInteract) {
        this.denyAltarInteract = denyAltarInteract;
    }

    public Boolean getDenyEntityInteract() {
        return denyEntityInteract;
    }

    public void setDenyEntityInteract(Boolean denyEntityInteract) {
        this.denyEntityInteract = denyEntityInteract;
    }

    public Boolean getAllowHopperMove() {
        return allowHopperMove;
    }

    public void setAllowHopperMove(Boolean allowHopperMove) {
        this.allowHopperMove = allowHopperMove;
    }

    public Boolean getAllowHopperPickup() {
        return allowHopperPickup;
    }

    public void setAllowHopperPickup(Boolean allowHopperPickup) {
        this.allowHopperPickup = allowHopperPickup;
    }
}
