package clre20.itemLock.template;

import clre20.itemLock.model.ItemTemplate;
import clre20.itemLock.model.TemplateSettings;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.Repairable;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 負責單檔物品樣本庫 (Per-Item YAML) 的載入、快取、保存與路徑安全檢驗。
 */
public class TemplateManager {

    private static final Pattern ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Plugin plugin;
    private final File templatesFolder;
    private final Map<String, ItemTemplate> templateCache = new ConcurrentHashMap<>();

    public TemplateManager(Plugin plugin) {
        this.plugin = plugin;
        this.templatesFolder = new File(plugin.getDataFolder(), "templates");
    }

    /**
     * 驗證範本名稱是否符合安全規範，防杜路徑遍歷攻擊。
     */
    public boolean isValidId(String id) {
        return id != null && ID_PATTERN.matcher(id).matches();
    }

    /**
     * 重新載入所有保護樣本（清空快取 -> 遍歷 templates/*.yml -> 寫入記憶體快取）。
     * 實現零磁碟延遲機制。
     */
    public void loadAll() {
        templateCache.clear();

        if (!templatesFolder.exists()) {
            if (!templatesFolder.mkdirs()) {
                plugin.getLogger().warning("無法建立 templates 目錄: " + templatesFolder.getAbsolutePath());
                return;
            }
        }

        File[] files = templatesFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return;
        }

        int loadedCount = 0;
        for (File file : files) {
            try {
                ItemTemplate template = loadTemplateFile(file);
                if (template != null) {
                    templateCache.put(template.getId(), template);
                    loadedCount++;
                }
            } catch (Exception e) {
                plugin.getLogger().severe("載入樣本檔案失敗: " + file.getName() + ", 原因: " + e.getMessage());
            }
        }

        plugin.getLogger().info("已成功載入 " + loadedCount + " 個物品保護樣本至快取庫中。");
    }

    /**
     * 解析單一 YAML 樣本檔案。
     */
     private ItemTemplate loadTemplateFile(File file) throws IOException {
        // 若檔案內殘留舊版的註解行，自動清除以保持檔案乾淨，避免每次都加上註解
        try {
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            if (content.contains("採用 Paper 原生二進位序列化") || content.contains("Data Components")) {
                String cleaned = content.lines()
                        .filter(line -> !line.contains("採用 Paper 原生二進位序列化") && !line.contains("Data Components"))
                        .collect(Collectors.joining("\n")) + "\n";
                Files.writeString(file.toPath(), cleaned, StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        String id = config.getString("id");
        if (id == null || id.isBlank()) {
            String fileName = file.getName();
            id = fileName.substring(0, fileName.lastIndexOf('.'));
        }

        String createdAt = config.getString("created-at", "Unknown");
        String materialName = config.getString("material");
        Material material = materialName != null ? Material.matchMaterial(materialName) : null;

        String base64Data = config.getString("data");
        if (base64Data == null || base64Data.isBlank()) {
            plugin.getLogger().warning("檔案 " + file.getName() + " 缺少 binary data，跳過載入。");
            return null;
        }

        byte[] bytes = Base64.getDecoder().decode(base64Data);
        ItemStack itemStack = ItemStack.deserializeBytes(bytes);

        if (material == null) {
            material = itemStack.getType();
        }

        TemplateSettings settings = TemplateSettings.fromSection(config.getConfigurationSection("settings"));

        return new ItemTemplate(id, createdAt, material, itemStack, settings);
    }

    /**
     * 採樣物品並保存為樣本檔案與快取。
     * 自動將耐久與修復成本歸零。
     */
    public ItemTemplate saveTemplate(String id, ItemStack sampleItem) throws IOException {
        if (!isValidId(id)) {
            throw new IllegalArgumentException("無效的樣本名稱: " + id);
        }

        // 採樣主手物品 -> 耐久與修復成本歸零
        ItemStack templateItem = sampleItem.clone();
        templateItem.setAmount(1);

        ItemMeta meta = templateItem.getItemMeta();
        if (meta != null) {
            boolean changed = false;
            if (meta instanceof Damageable damageable && damageable.hasDamage()) {
                damageable.setDamage(0);
                changed = true;
            }
            if (meta instanceof Repairable repairable && repairable.hasRepairCost()) {
                repairable.setRepairCost(0);
                changed = true;
            }
            if (changed) {
                templateItem.setItemMeta(meta);
            }
        }

        if (!templatesFolder.exists()) {
            templatesFolder.mkdirs();
        }

        File targetFile = new File(templatesFolder, id + ".yml");
        String now = LocalDateTime.now().format(DATE_FORMATTER);
        Material material = templateItem.getType();
        byte[] serializedBytes = templateItem.serializeAsBytes();
        String base64 = Base64.getEncoder().encodeToString(serializedBytes);

        // 若舊樣本已存在，繼承舊的個別設定；否則建立新預設設定
        ItemTemplate existing = templateCache.get(id);
        TemplateSettings settings = existing != null ? existing.getSettings() : new TemplateSettings();

        YamlConfiguration config = new YamlConfiguration();
        config.options().setHeader(Collections.emptyList());
        config.options().setFooter(Collections.emptyList());
        config.set("id", id);
        config.set("created-at", now);
        config.set("material", material.name());
        config.set("data", base64);
        Map<String, Object> settingsMap = settings.toMap();
        if (!settingsMap.isEmpty()) {
            config.createSection("settings", settingsMap);
        }
        config.save(targetFile);

        cleanCommentLines(targetFile);

        ItemTemplate template = new ItemTemplate(id, now, material, templateItem, settings);
        templateCache.put(id, template);
        return template;
    }

    /**
     * 更新指定樣本的個別防護設定，並即時寫入檔案與快取。
     */
    public void updateTemplateSettings(String id, TemplateSettings settings) throws IOException {
        ItemTemplate template = templateCache.get(id);
        if (template == null) {
            return;
        }
        template.setSettings(settings);

        File targetFile = new File(templatesFolder, id + ".yml");
        if (!targetFile.exists()) {
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(targetFile);
        config.options().setHeader(Collections.emptyList());
        config.options().setFooter(Collections.emptyList());
        config.setComments("data", Collections.emptyList());
        config.setInlineComments("data", Collections.emptyList());
        config.set("settings", null);
        Map<String, Object> settingsMap = settings.toMap();
        if (!settingsMap.isEmpty()) {
            config.createSection("settings", settingsMap);
        }
        config.save(targetFile);

        cleanCommentLines(targetFile);
    }

    /**
     * 清理檔案中殘留的舊版註解行，確保保持乾淨的 YAML 結構。
     */
    private void cleanCommentLines(File file) {
        try {
            if (!file.exists()) {
                return;
            }
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            if (content.contains("採用 Paper 原生二進位序列化") || content.contains("Data Components")) {
                String cleaned = content.lines()
                        .filter(line -> !line.contains("採用 Paper 原生二進位序列化") && !line.contains("Data Components"))
                        .collect(Collectors.joining("\n")) + "\n";
                Files.writeString(file.toPath(), cleaned, StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * 刪除指定樣本（刪除 YAML 檔案與快取）。
     */
    public boolean removeTemplate(String id) {
        if (!isValidId(id)) {
            return false;
        }

        ItemTemplate removed = templateCache.remove(id);
        File targetFile = new File(templatesFolder, id + ".yml");
        boolean deleted = false;
        if (targetFile.exists()) {
            deleted = targetFile.delete();
        }

        return removed != null || deleted;
    }

    /**
     * 檢查指定 ID 的樣本是否存在。
     */
    public boolean hasTemplate(String id) {
        if (id == null) {
            return false;
        }
        return templateCache.containsKey(id);
    }

    /**
     * 獲取指定 ID 的樣本。
     */
    public Optional<ItemTemplate> getTemplate(String id) {
        return Optional.ofNullable(templateCache.get(id));
    }

    /**
     * 獲取所有已快取的樣本。
     */
    public Map<String, ItemTemplate> getAllTemplates() {
        return Collections.unmodifiableMap(templateCache);
    }

    public File getTemplatesFolder() {
        return templatesFolder;
    }
}
