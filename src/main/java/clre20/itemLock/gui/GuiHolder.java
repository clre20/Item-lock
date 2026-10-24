package clre20.itemLock.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * Item-lock 箱子介面的簽署：
 * <ul>
 *   <li>InventoryHolder 為本介面，{@link #getPlugin()} 回傳 Item-lock 插件實例</li>
 *   <li>每個圖示都有 PDC {@code itemlock:gui} = 介面類別名稱</li>
 * </ul>
 * 只接受左鍵；其他點擊方式在 {@link GuiListener} 一律取消。
 */
public interface GuiHolder extends InventoryHolder {

    String PDC_KEY = "gui";

    Plugin getPlugin();

    /** 只會收到左鍵點擊頂部介面的事件（事件已取消）。 */
    void onLeftClick(InventoryClickEvent event, int slot);

    static NamespacedKey key(Plugin plugin) {
        return new NamespacedKey(plugin, PDC_KEY);
    }

    /** 為物品加上簽署標籤（物品已有 meta 時使用）。 */
    static ItemStack sign(Plugin plugin, ItemStack item, String guiName) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(key(plugin), PersistentDataType.STRING, guiName);
            item.setItemMeta(meta);
        }
        return item;
    }

    static ItemStack icon(Plugin plugin, String guiName, Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(name);
            if (lore != null && !lore.isEmpty()) {
                meta.lore(lore);
            }
            meta.getPersistentDataContainer().set(key(plugin), PersistentDataType.STRING, guiName);
            item.setItemMeta(meta);
        }
        return item;
    }
}
