package clre20.itemLock.config;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * 讀取資料夾內的 YAML，並以 jar 內預設檔補齊缺少的項目。
 * 保留使用者的值與註解；預設檔沒有的舊項目會移除。只有內容有變時才寫回。
 */
public final class YamlUpdater {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private YamlUpdater() {}

    public static YamlConfiguration load(JavaPlugin plugin, String name) {
        YamlConfiguration def = loadDefault(plugin, name);
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) {
            plugin.saveResource(name, false);
        }

        YamlConfiguration user = new YamlConfiguration();
        try {
            user.load(file);
        } catch (IOException | InvalidConfigurationException e) {
            // 格式壞掉：備份原檔、暫時使用預設值，不覆蓋原檔
            Path broken = file.toPath().resolveSibling(name + ".broken-" + LocalDateTime.now().format(STAMP));
            try {
                Files.copy(file.toPath(), broken, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
            }
            plugin.getLogger().warning(name + " 格式錯誤，已備份為 " + broken.getFileName() + "，暫時使用預設值：" + e.getMessage());
            return def;
        }

        boolean changed = false;
        for (String key : def.getKeys(true)) {
            if (user.contains(key, true)) {
                // 型別不同（區段 ↔ 單一值）時改回預設
                if (def.isConfigurationSection(key) == user.isConfigurationSection(key)) {
                    continue;
                }
                user.set(key, null);
            }
            if (def.isConfigurationSection(key)) {
                user.createSection(key);
            } else {
                user.set(key, def.get(key));
            }
            // 版本號是檔案第一行，它的註解其實是檔頭，補到檔尾時不要帶上
            if (!key.endsWith("-version") || key.contains(".")) {
                user.setComments(key, def.getComments(key));
                user.setInlineComments(key, def.getInlineComments(key));
            }
            changed = true;
        }

        List<String> userKeys = new ArrayList<>(user.getKeys(true));
        for (String key : userKeys) {
            if (!def.contains(key, true) && user.contains(key, true)) {
                user.set(key, null);
                changed = true;
            }
        }

        if (changed) {
            try {
                Path target = file.toPath();
                Files.copy(target, target.resolveSibling(name + ".bak"), StandardCopyOption.REPLACE_EXISTING);
                writeAtomic(target, user.saveToString());
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "無法寫回 " + name, e);
            }
        }

        user.setDefaults(def);
        return user;
    }

    private static YamlConfiguration loadDefault(JavaPlugin plugin, String name) {
        YamlConfiguration def = new YamlConfiguration();
        try (InputStream in = plugin.getResource(name)) {
            if (in != null) {
                try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    def.load(reader);
                }
            }
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().log(Level.SEVERE, "無法讀取內建的 " + name, e);
        }
        return def;
    }

    public static void writeAtomic(Path target, String content) throws IOException {
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(tmp, content, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
