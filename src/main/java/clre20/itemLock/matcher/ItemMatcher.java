package clre20.itemLock.matcher;

import clre20.itemLock.model.ItemTemplate;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.inventory.meta.Repairable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 物品比對。
 * <p>
 * 效能重點：樣本依材質建索引，材質沒有樣本的物品（絕大多數）只做一次 EnumMap 查詢就返回，
 * 不讀 ItemMeta、不建立任何物件。樣本本身預先正規化並記下名稱、Lore 等特徵，比對時不再複製樣本。
 * <p>
 * 比對規則（與舊版相同）：耐久消耗與修復成本不計；已繪製地圖比對 map_id；其餘所有組件需相同（不計數量）。
 * 只在主執行緒使用。
 */
public final class ItemMatcher {

    private volatile Map<Material, Entry[]> index = new EnumMap<>(Material.class);

    /** 樣本變動後重建索引（主執行緒）。 */
    public void rebuild(Collection<ItemTemplate> templates) {
        Map<Material, List<Entry>> grouped = new EnumMap<>(Material.class);
        List<ItemTemplate> sorted = new ArrayList<>(templates);
        sorted.sort(Comparator.comparing(ItemTemplate::getId));
        for (ItemTemplate t : sorted) {
            ItemStack sample = t.getItemStack();
            if (sample.getType().isAir()) {
                continue;
            }
            grouped.computeIfAbsent(sample.getType(), k -> new ArrayList<>(1)).add(new Entry(t, sample));
        }
        Map<Material, Entry[]> built = new EnumMap<>(Material.class);
        grouped.forEach((m, list) -> built.put(m, list.toArray(new Entry[0])));
        this.index = built;
    }

    public boolean isProtected(ItemStack item) {
        return find(item) != null;
    }

    /** 回傳命中的樣本；沒有則為 null。 */
    public ItemTemplate find(ItemStack item) {
        if (item == null) {
            return null;
        }
        Entry[] entries = index.get(item.getType());
        if (entries == null) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        ItemStack normalized = null;
        for (Entry e : entries) {
            if (e.mapId >= 0 && meta instanceof MapMeta map && map.hasMapId()) {
                if (map.getMapId() == e.mapId) {
                    return e.template;
                }
                continue;
            }
            // 便宜的特徵先篩，不符就跳過
            if (meta.hasDisplayName() != e.hasName
                    || meta.hasItemName() != e.hasItemName
                    || meta.hasLore() != e.hasLore
                    || meta.hasEnchants() != e.hasEnchants
                    || meta.hasCustomModelData() != e.hasModelData) {
                continue;
            }
            if (e.hasName && !Objects.equals(meta.displayName(), e.name)) {
                continue;
            }
            if (e.hasLore && !Objects.equals(meta.lore(), e.lore)) {
                continue;
            }
            if (normalized == null) {
                normalized = normalize(item, meta);
            }
            if (normalized.isSimilar(e.normalized)) {
                return e.template;
            }
        }
        return null;
    }

    /**
     * 去掉耐久消耗與修復成本。沒有這兩項時直接回傳原物品，不複製。
     * meta 必須是 item.getItemMeta() 取得的副本（會被修改）。
     */
    private static ItemStack normalize(ItemStack item, ItemMeta meta) {
        boolean changed = false;
        if (meta instanceof Damageable d && d.hasDamage()) {
            d.setDamage(0);
            changed = true;
        }
        if (meta instanceof Repairable r && r.hasRepairCost()) {
            r.setRepairCost(0);
            changed = true;
        }
        if (!changed) {
            return item;
        }
        ItemStack copy = item.clone();
        copy.setItemMeta(meta);
        return copy;
    }

    /** 給樣本使用：數量 1、去掉耐久消耗與修復成本。 */
    public static ItemStack normalizeSample(ItemStack item) {
        ItemStack copy = item.clone();
        copy.setAmount(1);
        ItemMeta meta = copy.getItemMeta();
        if (meta != null) {
            ItemStack n = normalize(copy, meta);
            if (n != copy) {
                return n;
            }
        }
        return copy;
    }

    private static final class Entry {
        final ItemTemplate template;
        final ItemStack normalized;
        final int mapId;
        final boolean hasName;
        final boolean hasItemName;
        final boolean hasLore;
        final boolean hasEnchants;
        final boolean hasModelData;
        final Component name;
        final List<Component> lore;

        Entry(ItemTemplate template, ItemStack sample) {
            this.template = template;
            this.normalized = normalizeSample(sample);
            ItemMeta meta = normalized.getItemMeta();
            int id = -1;
            if (sample.getType() == Material.FILLED_MAP && meta instanceof MapMeta map && map.hasMapId()) {
                id = map.getMapId();
            }
            this.mapId = id;
            this.hasName = meta != null && meta.hasDisplayName();
            this.hasItemName = meta != null && meta.hasItemName();
            this.hasLore = meta != null && meta.hasLore();
            this.hasEnchants = meta != null && meta.hasEnchants();
            this.hasModelData = meta != null && meta.hasCustomModelData();
            this.name = hasName ? meta.displayName() : null;
            this.lore = hasLore ? meta.lore() : null;
        }
    }
}
