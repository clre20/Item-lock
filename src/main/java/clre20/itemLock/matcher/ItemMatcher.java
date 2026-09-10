package clre20.itemLock.matcher;

import clre20.itemLock.model.ItemTemplate;
import clre20.itemLock.template.TemplateManager;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.inventory.meta.Repairable;
import org.bukkit.persistence.PersistentDataContainer;

import java.util.Objects;
import java.util.Set;

/**
 * 智慧遮罩比對引擎 (Component Matcher)。
 * 採三層式過濾：
 * 1. 材質初篩 (Material Filter)
 * 2. 動態組件遮罩 (Dynamic Masking: 排除 damage 與 repair_cost)
 * 3. 特徵指紋比對 (Fingerprint Matching: custom_model_data, custom_name/item_name, lore, enchantments, PDC, Filled Map map_id)
 */
public class ItemMatcher {

    private final TemplateManager templateManager;

    public ItemMatcher(TemplateManager templateManager) {
        this.templateManager = templateManager;
    }

    /**
     * 檢查目標物品是否命中任何已註冊的保護樣本。
     */
    public boolean isProtected(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        return match(item).matched();
    }

    /**
     * 進行比對並返回結果與匹配的樣本。
     */
    public MatchResult match(ItemStack target) {
        if (target == null || target.getType().isAir()) {
            return MatchResult.notMatched();
        }

        for (ItemTemplate template : templateManager.getAllTemplates().values()) {
            if (matches(target, template.getItemStack())) {
                return MatchResult.matched(template);
            }
        }

        return MatchResult.notMatched();
    }

    /**
     * 比對目標物品是否符合指定的母本物品。
     */
    @SuppressWarnings("deprecation")
    public boolean matches(ItemStack target, ItemStack template) {
        if (target == null || template == null) {
            return false;
        }

        // 1. 材質初篩 (Material Filter)
        if (target.getType() != template.getType()) {
            return false;
        }

        // 已繪製地圖 (Filled Map)：精準比對其核心組件 minecraft:map_id
        if (target.getType() == Material.FILLED_MAP) {
            ItemMeta targetMeta = target.getItemMeta();
            ItemMeta templateMeta = template.getItemMeta();
            if (targetMeta instanceof MapMeta targetMapMeta && templateMeta instanceof MapMeta templateMapMeta) {
                if (targetMapMeta.hasMapId() && templateMapMeta.hasMapId()) {
                    return targetMapMeta.getMapId() == templateMapMeta.getMapId();
                }
            }
        }

        // 2. 動態組件遮罩 (Dynamic Masking)
        ItemStack normalizedTarget = normalize(target);
        ItemStack normalizedTemplate = normalize(template);

        ItemMeta targetMeta = normalizedTarget.getItemMeta();
        ItemMeta templateMeta = normalizedTemplate.getItemMeta();

        if (targetMeta == null && templateMeta == null) {
            return true;
        }
        if (targetMeta == null || templateMeta == null) {
            return false;
        }

        // 3. 特徵指紋比對 (Fingerprint Matching)
        // (1) 比對 custom_model_data
        boolean targetHasCmd = targetMeta.hasCustomModelData();
        boolean templateHasCmd = templateMeta.hasCustomModelData();
        if (targetHasCmd != templateHasCmd) {
            return false;
        }
        if (targetHasCmd && targetMeta.getCustomModelData() != templateMeta.getCustomModelData()) {
            return false;
        }

        // (2) 比對 item_name / custom_name (支援 Adventure Component 及 legacy 文字)
        if (!Objects.equals(targetMeta.displayName(), templateMeta.displayName())) {
            return false;
        }
        if (!Objects.equals(targetMeta.itemName(), templateMeta.itemName())) {
            return false;
        }

        // (3) 比對 lore
        if (!Objects.equals(targetMeta.lore(), templateMeta.lore())) {
            return false;
        }

        // (4) 比對 enchantments 映射表
        if (!Objects.equals(normalizedTarget.getEnchantments(), normalizedTemplate.getEnchantments())) {
            return false;
        }

        // (5) 比對 PDC (自定義持久化標籤)
        PersistentDataContainer targetPdc = targetMeta.getPersistentDataContainer();
        PersistentDataContainer templatePdc = templateMeta.getPersistentDataContainer();
        Set<NamespacedKey> templateKeys = templatePdc.getKeys();
        Set<NamespacedKey> targetKeys = targetPdc.getKeys();
        if (!targetKeys.equals(templateKeys)) {
            return false;
        }

        // 綜合特徵比對 (排除耐久與修復懲罰後的其餘所有組件)
        return normalizedTarget.isSimilar(normalizedTemplate);
    }

    /**
     * 複製物品並將動態變動的組件（耐久耗損 damage、鐵砧修復成本 repair_cost）歸零。
     */
    public ItemStack normalize(ItemStack original) {
        if (original == null) {
            return null;
        }
        ItemStack clone = original.clone();
        clone.setAmount(1);

        ItemMeta meta = clone.getItemMeta();
        if (meta != null) {
            boolean modified = false;
            if (meta instanceof Damageable damageable) {
                if (damageable.hasDamage()) {
                    damageable.setDamage(0);
                    modified = true;
                }
            }
            if (meta instanceof Repairable repairable) {
                if (repairable.hasRepairCost()) {
                    repairable.setRepairCost(0);
                    modified = true;
                }
            }
            if (modified) {
                clone.setItemMeta(meta);
            }
        }
        return clone;
    }
}
