package clre20.itemLock.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;

/**
 * 集中管理與美化 Item-lock 的控制台 Log 輸出。
 * 採用 Adventure MiniMessage 漸層與彩色標籤排版，
 * 方便伺服器管理員在控制台中將 Item-lock 與其他插件內容清楚區分。
 */
public final class Log {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String PREFIX = "<gradient:#FFAA00:#FF5555><bold>[Item-Lock]</bold></gradient>";
    private static final String DIVIDER = "<dark_gray>»</dark_gray>";
    private static final String SEPARATOR = "<gradient:#FFAA00:#FF5555>▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬</gradient>";

    private Log() {}

    /**
     * 發送 MiniMessage 格式字串至控制台。
     */
    public static void raw(String miniMessageText) {
        ConsoleCommandSender console = Bukkit.getConsoleSender();
        if (console != null) {
            console.sendMessage(MM.deserialize(miniMessageText));
        } else {
            System.out.println(MM.stripTags(miniMessageText));
        }
    }

    /**
     * 核心生命週期與系統日誌 (Core)
     */
    public static void core(String message) {
        raw(PREFIX + " <aqua>[核心/Core]</aqua> " + DIVIDER + " <green>" + message + "</green>");
    }

    /**
     * 物品樣本庫與快取管理日誌 (Template)
     */
    public static void template(String message) {
        raw(PREFIX + " <yellow>[樣本庫]</yellow> " + DIVIDER + " <white>" + message + "</white>");
    }

    /**
     * 第三方外掛相容動態掛接日誌 (Hook)
     */
    public static void hook(String message) {
        raw(PREFIX + " <light_purple>[相容掛接]</light_purple> " + DIVIDER + " <green>" + message + "</green>");
    }

    /**
     * 設定檔解析與管理日誌 (Config)
     */
    public static void config(String message) {
        raw(PREFIX + " <blue>[設定/Config]</blue> " + DIVIDER + " <white>" + message + "</white>");
    }

    /**
     * 一般資訊日誌 (Info)
     */
    public static void info(String tag, String message) {
        raw(PREFIX + " <blue>[" + tag + "]</blue> " + DIVIDER + " <white>" + message + "</white>");
    }

    /**
     * 警告日誌 (Warn)
     */
    public static void warn(String tag, String message) {
        raw(PREFIX + " <gold>[" + tag + "/Warn]</gold> " + DIVIDER + " <yellow>" + message + "</yellow>");
    }

    public static void warn(String message) {
        raw(PREFIX + " <gold>[警告/Warn]</gold> " + DIVIDER + " <yellow>" + message + "</yellow>");
    }

    /**
     * 錯誤日誌 (Error)
     */
    public static void error(String tag, String message) {
        raw(PREFIX + " <red>[" + tag + "/Error]</red> " + DIVIDER + " <red>" + message + "</red>");
    }

    public static void error(String message) {
        raw(PREFIX + " <red>[錯誤/Error]</red> " + DIVIDER + " <red>" + message + "</red>");
    }

    public static void error(String tag, String message, Throwable throwable) {
        error(tag, message);
        if (throwable != null) {
            throwable.printStackTrace();
        }
    }

    /**
     * 輸出精美啟動橫幅與模組狀態卡
     */
    public static void printStartupBanner(String version, boolean shopkeepersHooked, boolean eshulkerBoxHooked, boolean spyInventoryHooked) {
        raw(SEPARATOR);
        raw(PREFIX + " <gold><bold>Item-lock v" + version + " 啟動</bold></gold>");
        raw(PREFIX + " <dark_gray>•</dark_gray> <gray>零信任:</gray> <green>✔ 已就緒</green>");
        raw(PREFIX + " <dark_gray>•</dark_gray> <gray>組件遮罩:</gray> <green>✔ 1.20.5+ Data Components</green>");
        raw(PREFIX + " <dark_gray>•</dark_gray> <gray>第三方插件相容:</gray> "
                + (shopkeepersHooked ? "<green>[Shopkeepers ✔]</green>" : "<dark_gray>[Shopkeepers ✘]</dark_gray>") + " "
                + (eshulkerBoxHooked ? "<green>[eShulkerBox ✔]</green>" : "<dark_gray>[eShulkerBox ✘]</dark_gray>") + " "
                + (spyInventoryHooked ? "<green>[AdvancedSpyInventory ✔]</green>" : "<dark_gray>[AdvancedSpyInventory ✘]</dark_gray>"));
        raw(SEPARATOR);
    }
}
