package clre20.itemLock.command;

import clre20.itemLock.config.PluginConfig;
import clre20.itemLock.matcher.ItemMatcher;
import clre20.itemLock.matcher.MatchResult;
import clre20.itemLock.model.ItemTemplate;
import clre20.itemLock.template.TemplateManager;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * ItemLock 主指令處理器。
 * 權限節點: itemlock.admin (預設 OP Level 2)
 */
public class ItemLockCommand implements CommandExecutor {

    private final PluginConfig config;
    private final TemplateManager templateManager;
    private final ItemMatcher itemMatcher;

    public ItemLockCommand(PluginConfig config, TemplateManager templateManager, ItemMatcher itemMatcher) {
        this.config = config;
        this.templateManager = templateManager;
        this.itemMatcher = itemMatcher;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("itemlock.admin")) {
            sendMessage(sender, config.getNoPermission());
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            handleHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "add" -> handleAdd(sender, args);
            case "remove" -> handleRemove(sender, args);
            case "check" -> handleCheck(sender);
            case "list" -> handleList(sender);
            case "reload" -> handleReload(sender);
            default -> {
                sendMessage(sender, "<red>未知的子指令！請使用 <yellow>/" + label + " help</yellow> 查看說明。</red>");
            }
        }

        return true;
    }

    private void handleHelp(CommandSender sender) {
        for (String line : config.getHelpMessages()) {
            sendMessage(sender, line);
        }
    }

    private void handleAdd(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sendMessage(sender, config.getPlayerOnly());
            return;
        }

        if (args.length < 2) {
            sendMessage(sender, "<red>請指定樣本名稱！用法: <yellow>/itemlock add <名稱></yellow></red>");
            return;
        }

        String name = args[1];
        if (!templateManager.isValidId(name)) {
            sendMessage(sender, config.getAddInvalidName());
            return;
        }

        ItemStack handItem = player.getInventory().getItemInMainHand();
        if (handItem.getType().isAir()) {
            sendMessage(sender, config.getAddAir());
            return;
        }

        boolean alreadyExists = templateManager.hasTemplate(name);
        if (alreadyExists) {
            boolean isForce = args.length >= 3 && (
                    args[2].equalsIgnoreCase("-f") ||
                    args[2].equalsIgnoreCase("--force") ||
                    args[2].equalsIgnoreCase("force")
            );
            if (!isForce) {
                String msg = config.getAddAlreadyExists().replace("<id>", name);
                sendMessage(sender, msg);
                return;
            }
        }

        try {
            ItemTemplate template = templateManager.saveTemplate(name, handItem);
            String templateMsg = alreadyExists ? config.getAddOverwriteSuccess() : config.getAddSuccess();
            String msg = templateMsg
                    .replace("<id>", template.getId())
                    .replace("<material>", template.getMaterial().name());
            sendMessage(sender, msg);
        } catch (IOException e) {
            sendMessage(sender, "<red>儲存保護樣本檔案失敗: " + e.getMessage() + "</red>");
        }
    }

    private void handleRemove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendMessage(sender, "<red>請指定要刪除的樣本名稱！用法: <yellow>/itemlock remove <名稱></yellow></red>");
            return;
        }

        String name = args[1];
        if (templateManager.removeTemplate(name)) {
            String msg = config.getRemoveSuccess().replace("<id>", name);
            sendMessage(sender, msg);
        } else {
            String msg = config.getRemoveNotFound().replace("<id>", name);
            sendMessage(sender, msg);
        }
    }

    private void handleCheck(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sendMessage(sender, config.getPlayerOnly());
            return;
        }

        ItemStack handItem = player.getInventory().getItemInMainHand();
        if (handItem.getType().isAir()) {
            sendMessage(sender, "<red>主手不可為空！請手持欲檢測的物品。</red>");
            return;
        }

        MatchResult result = itemMatcher.match(handItem);
        if (result.matched() && result.getTemplate().isPresent()) {
            ItemTemplate template = result.getTemplate().get();
            String msg = config.getCheckProtected()
                    .replace("<id>", template.getId())
                    .replace("<material>", template.getMaterial().name());
            sendMessage(sender, msg);
        } else {
            sendMessage(sender, config.getCheckNotProtected());
        }
    }

    private void handleList(CommandSender sender) {
        Map<String, ItemTemplate> templates = templateManager.getAllTemplates();
        if (templates.isEmpty()) {
            sendMessage(sender, config.getListEmpty());
            return;
        }

        String header = config.getListHeader().replace("<count>", String.valueOf(templates.size()));
        sendMessage(sender, header);

        File folder = templateManager.getTemplatesFolder();
        for (ItemTemplate template : templates.values()) {
            String fileName = template.getId() + ".yml";
            String itemLine = config.getListItem()
                    .replace("<id>", template.getId())
                    .replace("<material>", template.getMaterial().name())
                    .replace("<file>", fileName);
            sendMessage(sender, itemLine);
        }
    }

    private void handleReload(CommandSender sender) {
        config.load();
        templateManager.loadAll();
        int count = templateManager.getAllTemplates().size();
        String msg = config.getReloadSuccess().replace("<count>", String.valueOf(count));
        sendMessage(sender, msg);
    }

    private void sendMessage(CommandSender sender, String message) {
        Component component = config.parseComponent(config.getPrefix() + message);
        sender.sendMessage(component);
    }
}
