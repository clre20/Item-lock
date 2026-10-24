package clre20.itemLock.util;

import clre20.itemLock.config.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;

/**
 * 後台訊息：文字來自 messages.yml 的 console.*，加上 messages.yml 的前綴。
 */
public final class Log {

    private static volatile Messages messages;

    private Log() {}

    public static void init(Messages m) {
        messages = m;
    }

    public static void console(String key, Object... vars) {
        Messages m = messages;
        if (m == null) {
            return;
        }
        Component line = Messages.parse(m.raw("prefix") + m.raw(key), vars);
        Bukkit.getConsoleSender().sendMessage(line);
    }
}
