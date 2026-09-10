package clre20.itemLock.command;

import clre20.itemLock.template.TemplateManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 智慧補全處理器 (Tab Completion)。
 * 完整支援所有子指令與參數補齊。
 */
public class ItemLockTabCompleter implements TabCompleter {

    private static final List<String> SUB_COMMANDS = List.of("help", "add", "remove", "check", "list", "reload");

    private final TemplateManager templateManager;

    public ItemLockTabCompleter(TemplateManager templateManager) {
        this.templateManager = templateManager;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("itemlock.admin")) {
            return Collections.emptyList();
        }

        List<String> completions = new ArrayList<>();

        // 第一個參數：所有子指令補全
        if (args.length == 1) {
            StringUtil.copyPartialMatches(args[0], SUB_COMMANDS, completions);
            Collections.sort(completions);
            return completions;
        }

        // 第二個參數：根據子指令補全對應參數
        if (args.length == 2) {
            String subCommand = args[0].toLowerCase(Locale.ROOT);
            switch (subCommand) {
                case "remove" -> {
                    // 自動補齊所有已登錄的範本名稱
                    List<String> templateIds = new ArrayList<>(templateManager.getAllTemplates().keySet());
                    StringUtil.copyPartialMatches(args[1], templateIds, completions);
                    Collections.sort(completions);
                    return completions;
                }
                case "add" -> {
                    // 若玩家主手手持物品，智慧提示該物品材質小寫名稱作為命名參考
                    if (sender instanceof Player player) {
                        ItemStack hand = player.getInventory().getItemInMainHand();
                        if (!hand.getType().isAir()) {
                            String suggested = hand.getType().name().toLowerCase(Locale.ROOT);
                            if (args[1].isEmpty() || suggested.startsWith(args[1].toLowerCase(Locale.ROOT))) {
                                completions.add(suggested);
                            }
                        }
                    }
                    return completions;
                }
                default -> {
                    return Collections.emptyList();
                }
            }
        }

        // 第三個參數：針對 add 指令，若該名稱已存在則提示 -f (強制覆蓋)
        if (args.length == 3) {
            String subCommand = args[0].toLowerCase(Locale.ROOT);
            if (subCommand.equals("add")) {
                if (templateManager.hasTemplate(args[1])) {
                    if (args[2].isEmpty() || "-f".startsWith(args[2].toLowerCase(Locale.ROOT))) {
                        completions.add("-f");
                    }
                }
            }
            return completions;
        }

        return Collections.emptyList();
    }
}
