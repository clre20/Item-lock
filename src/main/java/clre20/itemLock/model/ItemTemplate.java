package clre20.itemLock.model;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;

/**
 * 代表一個已登錄的物品保護樣本。
 */
public class ItemTemplate {

    private final String id;
    private final String createdAt;
    private final Material material;
    private final ItemStack itemStack;
    private TemplateSettings settings;

    public ItemTemplate(String id, String createdAt, Material material, ItemStack itemStack) {
        this(id, createdAt, material, itemStack, new TemplateSettings());
    }

    public ItemTemplate(String id, String createdAt, Material material, ItemStack itemStack, TemplateSettings settings) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt cannot be null");
        this.material = Objects.requireNonNull(material, "material cannot be null");
        this.itemStack = Objects.requireNonNull(itemStack, "itemStack cannot be null");
        this.settings = settings != null ? settings : new TemplateSettings();
    }

    public String getId() {
        return id;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public Material getMaterial() {
        return material;
    }

    public ItemStack getItemStack() {
        return itemStack.clone();
    }

    public TemplateSettings getSettings() {
        return settings;
    }

    public void setSettings(TemplateSettings settings) {
        this.settings = settings != null ? settings : new TemplateSettings();
    }
}
