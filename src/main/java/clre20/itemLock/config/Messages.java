package clre20.itemLock.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Map;

/**
 * messages.yml：所有顯示文字。變數格式 {name}，值會先跳脫，玩家無法藉變數注入格式。
 */
public final class Messages {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final JavaPlugin plugin;
    private final Map<String, String> strings = new ConcurrentHashMap<>();
    private final Map<String, List<String>> lists = new ConcurrentHashMap<>();
    private volatile YamlConfiguration yaml;
    private volatile String prefix = "";

    public Messages(JavaPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        this.yaml = YamlUpdater.load(plugin, "messages.yml");
        strings.clear();
        lists.clear();
        this.prefix = raw("prefix");
    }

    /** 原始字串（已快取），找不到時回傳 key。 */
    public String raw(String key) {
        String cached = strings.get(key);
        if (cached != null) {
            return cached;
        }
        String value = yaml.getString(key);
        if (value == null) {
            value = key;
        }
        strings.put(key, value);
        return value;
    }

    private List<String> rawList(String key) {
        List<String> cached = lists.get(key);
        if (cached != null) {
            return cached;
        }
        List<String> value = yaml.isList(key) ? List.copyOf(yaml.getStringList(key)) : List.of();
        lists.put(key, value);
        return value;
    }

    /** 解析指定訊息；vars 依序為 名稱, 值, 名稱, 值…… */
    public Component get(String key, Object... vars) {
        return parse(raw(key), vars);
    }

    /** 不帶前綴、去掉預設斜體，給物品名稱與 Lore 使用。 */
    public Component item(String key, Object... vars) {
        return noItalic(get(key, vars));
    }

    public List<Component> itemList(String key, Object... vars) {
        List<String> lines = rawList(key);
        List<Component> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            out.add(noItalic(parse(line, vars)));
        }
        return out;
    }

    public void send(CommandSender sender, String key, Object... vars) {
        sender.sendMessage(parse(prefix + raw(key), vars));
    }

    /** 不加前綴直接送出（說明清單用）。 */
    public void sendPlain(CommandSender sender, String key, Object... vars) {
        sender.sendMessage(get(key, vars));
    }

    /** 標記為受信任的格式化文字（例如另一條訊息），代入時不跳脫。 */
    public record Raw(String text) {}

    public Raw rawOf(String key) {
        return new Raw(raw(key));
    }

    public static Component noItalic(Component c) {
        return c.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    /**
     * 先依「原始模板」判斷是 & 顏色碼或 MiniMessage，再代入已跳脫的變數。
     */
    public static Component parse(String template, Object... vars) {
        if (template == null || template.isEmpty()) {
            return Component.empty();
        }
        boolean legacy = template.indexOf('&') >= 0 || template.indexOf('§') >= 0;
        String text = template;
        for (int i = 0; i + 1 < vars.length; i += 2) {
            String name = "{" + vars[i] + "}";
            if (!text.contains(name)) {
                continue;
            }
            Object v = vars[i + 1];
            String value;
            if (v instanceof Raw r) {
                // 來自 messages.yml 的格式化文字：轉成與模板相同的格式
                Component c = parse(r.text());
                value = legacy ? LEGACY.serialize(c) : MM.serialize(c);
            } else {
                value = String.valueOf(v);
                value = legacy ? value.replace('&', '＆').replace('§', ' ') : MM.escapeTags(value);
            }
            text = text.replace(name, value);
        }
        if (legacy) {
            return LEGACY.deserialize(text.replace('§', '&'));
        }
        return MM.deserialize(text);
    }
}
