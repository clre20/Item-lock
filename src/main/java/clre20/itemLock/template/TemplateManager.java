package clre20.itemLock.template;

import clre20.itemLock.config.YamlUpdater;
import clre20.itemLock.matcher.ItemMatcher;
import clre20.itemLock.model.ItemTemplate;
import clre20.itemLock.model.TemplateSettings;
import clre20.itemLock.util.Log;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.regex.Pattern;

/**
 * 樣本庫：templates/&lt;ID&gt;.yml。
 * <p>
 * 記憶體中的 Map 只在主執行緒使用。寫檔一律交給單一 I/O 執行緒依序處理：
 * 新增／刪除立即排入；GUI 改設定只標記髒資料，每 10 秒批次寫入，關服與重載前寫完。
 */
public final class TemplateManager {

    public static final int MAX_ID_LENGTH = 32;
    private static final Pattern ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{1,64}$");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long MAX_FILE_BYTES = 2L * 1024 * 1024;

    private final JavaPlugin plugin;
    private final ItemMatcher matcher;
    private final Path folder;
    private final Map<String, ItemTemplate> templates = new HashMap<>();
    private final Set<String> dirty = new LinkedHashSet<>();
    private final ExecutorService io;
    private BukkitTask flushTask;

    public TemplateManager(JavaPlugin plugin, ItemMatcher matcher) {
        this.plugin = plugin;
        this.matcher = matcher;
        this.folder = new File(plugin.getDataFolder(), "templates").toPath().toAbsolutePath().normalize();
        this.io = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Item-lock-IO");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        flushTask = Bukkit.getScheduler().runTaskTimer(plugin, this::flushDirty, 200L, 200L);
    }

    /** 新增時使用的名稱檢查（最多 32 字）。 */
    public boolean isValidNewId(String id) {
        return isValidId(id) && id.length() <= MAX_ID_LENGTH;
    }

    /** 一般名稱檢查：只允許英數字、_、-，避免路徑穿越。 */
    public boolean isValidId(String id) {
        return id != null && ID_PATTERN.matcher(id).matches();
    }

    /** 讀取全部樣本（啟動與 /itemlock reload，主執行緒）。重載前先寫完排隊中的資料。 */
    public int loadAll() {
        flushDirty();
        waitForWrites();
        templates.clear();
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "無法建立 templates 資料夾", e);
        }

        File[] files = folder.toFile().listFiles((dir, name) -> name.endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                String name = file.getName();
                String id = name.substring(0, name.length() - 4);
                if (!isValidId(id)) {
                    Log.console("console.template-invalid-name", "file", name);
                    continue;
                }
                if (file.length() > MAX_FILE_BYTES) {
                    Log.console("console.template-too-large", "file", name);
                    continue;
                }
                try {
                    ItemTemplate t = read(file, id);
                    if (t != null) {
                        templates.put(id, t);
                    }
                } catch (Exception e) {
                    Log.console("console.template-load-failed", "file", name, "reason", String.valueOf(e.getMessage()));
                }
            }
        }
        matcher.rebuild(templates.values());
        return templates.size();
    }

    private ItemTemplate read(File file, String id) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String data = yaml.getString("data");
        if (data == null || data.isBlank()) {
            Log.console("console.template-load-failed", "file", file.getName(), "reason", "missing data");
            return null;
        }
        ItemStack item = ItemStack.deserializeBytes(Base64.getDecoder().decode(data.trim()));
        if (item.getType().isAir()) {
            return null;
        }
        String createdAt = yaml.getString("created-at", "-");
        TemplateSettings settings = TemplateSettings.fromSection(yaml.getConfigurationSection("settings"));
        return new ItemTemplate(id, createdAt, item.getType(), item, settings);
    }

    /** 以物品建立或覆蓋樣本；檔案在背景寫入。 */
    public ItemTemplate saveTemplate(String id, ItemStack sample) {
        if (!isValidId(id)) {
            throw new IllegalArgumentException("invalid id");
        }
        ItemStack item = ItemMatcher.normalizeSample(sample);
        ItemTemplate old = templates.get(id);
        TemplateSettings settings = old != null ? old.getSettings() : new TemplateSettings();
        ItemTemplate t = new ItemTemplate(id, LocalDateTime.now().format(DATE), item.getType(), item, settings);
        templates.put(id, t);
        dirty.remove(id);
        matcher.rebuild(templates.values());
        queueWrite(t);
        return t;
    }

    /** GUI 改了設定：只標記，定時批次寫入。 */
    public void markDirty(String id) {
        if (templates.containsKey(id)) {
            dirty.add(id);
        }
    }

    public boolean removeTemplate(String id) {
        if (!isValidId(id)) {
            return false;
        }
        ItemTemplate removed = templates.remove(id);
        dirty.remove(id);
        if (removed == null) {
            return false;
        }
        matcher.rebuild(templates.values());
        Path target = resolve(id);
        io.execute(() -> {
            try {
                Files.deleteIfExists(target);
            } catch (IOException e) {
                Log.console("console.template-write-failed", "id", id, "reason", String.valueOf(e.getMessage()));
            }
        });
        return true;
    }

    /** 寫出所有髒資料（主執行緒序列化，I/O 執行緒寫檔）。 */
    public void flushDirty() {
        if (dirty.isEmpty()) {
            return;
        }
        for (String id : dirty) {
            ItemTemplate t = templates.get(id);
            if (t != null) {
                queueWrite(t);
            }
        }
        dirty.clear();
    }

    private void queueWrite(ItemTemplate t) {
        String content = serialize(t);
        Path target = resolve(t.getId());
        String id = t.getId();
        io.execute(() -> {
            try {
                Files.createDirectories(folder);
                YamlUpdater.writeAtomic(target, content);
            } catch (IOException e) {
                Log.console("console.template-write-failed", "id", id, "reason", String.valueOf(e.getMessage()));
            }
        });
    }

    private static String serialize(ItemTemplate t) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("id", t.getId());
        yaml.set("created-at", t.getCreatedAt());
        yaml.set("material", t.getMaterial().name());
        yaml.set("data", Base64.getEncoder().encodeToString(t.getItemStack().serializeAsBytes()));
        Map<String, Object> settings = t.getSettings().toMap();
        if (!settings.isEmpty()) {
            yaml.createSection("settings", settings);
        }
        return yaml.saveToString();
    }

    /** 名稱已驗證過；再確認結果仍在 templates 資料夾內。 */
    private Path resolve(String id) {
        Path p = folder.resolve(id + ".yml").normalize();
        if (!p.getParent().equals(folder)) {
            throw new IllegalArgumentException("invalid id");
        }
        return p;
    }

    private void waitForWrites() {
        try {
            io.submit(() -> { }).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            plugin.getLogger().warning("等待樣本寫入逾時");
        }
    }

    /** 關服：寫完所有資料後停止 I/O 執行緒。 */
    public void shutdown() {
        if (flushTask != null) {
            flushTask.cancel();
        }
        flushDirty();
        io.shutdown();
        try {
            if (!io.awaitTermination(15, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("樣本寫入未在 15 秒內完成");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        templates.clear();
    }

    public boolean hasTemplate(String id) {
        return id != null && templates.containsKey(id);
    }

    public Optional<ItemTemplate> getTemplate(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(templates.get(id));
    }

    public Collection<ItemTemplate> getAll() {
        return Collections.unmodifiableCollection(templates.values());
    }

    public Set<String> getIds() {
        return Collections.unmodifiableSet(templates.keySet());
    }

    public int size() {
        return templates.size();
    }
}
