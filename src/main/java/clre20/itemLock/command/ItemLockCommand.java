package clre20.itemLock.command;

import clre20.itemLock.ItemLock;
import clre20.itemLock.config.Messages;
import clre20.itemLock.gui.TemplateListGui;
import clre20.itemLock.gui.TemplateSettingsGui;
import clre20.itemLock.model.ItemTemplate;
import clre20.itemLock.template.TemplateManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * /itemlock 指令與 Tab 補全。每個子指令有自己的權限；說明與補全只列出有權限的項目。
 */
public final class ItemLockCommand implements TabExecutor {

    private record Sub(String name, String permission, String usage) {}

    private static final List<Sub> SUBS = List.of(
            new Sub("help", Perm.HELP, "/itemlock help"),
            new Sub("gui", Perm.LIST, "/itemlock gui"),
            new Sub("list", Perm.LIST, "/itemlock list"),
            new Sub("edit", Perm.EDIT, "/itemlock edit <名稱>"),
            new Sub("add", Perm.ADD, "/itemlock add <名稱> [-f]"),
            new Sub("remove", Perm.REMOVE, "/itemlock remove <名稱>"),
            new Sub("check", Perm.CHECK, "/itemlock check"),
            new Sub("reload", Perm.RELOAD, "/itemlock reload"),
            new Sub("version", Perm.VERSION, "/itemlock version")
    );

    private final ItemLock plugin;

    public ItemLockCommand(ItemLock plugin) {
        this.plugin = plugin;
    }

    private Messages msg() {
        return plugin.getMessages();
    }

    private TemplateManager templates() {
        return plugin.getTemplateManager();
    }

    private static Sub find(String name) {
        for (Sub s : SUBS) {
            if (s.name().equals(name)) {
                return s;
            }
        }
        return null;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        String name = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        if (name.equals("delete")) {
            name = "remove";
        }
        Sub sub = find(name);
        if (sub == null) {
            msg().send(sender, "command.unknown", "label", label);
            return true;
        }
        if (!sender.hasPermission(sub.permission())) {
            msg().send(sender, "command.no-permission");
            return true;
        }
        switch (sub.name()) {
            case "help" -> help(sender);
            case "gui", "list" -> list(sender);
            case "edit" -> edit(sender, args);
            case "add" -> add(sender, args);
            case "remove" -> remove(sender, args);
            case "check" -> check(sender);
            case "reload" -> reload(sender);
            case "version" -> plugin.getUpdateChecker().reply(sender);
            default -> { }
        }
        return true;
    }

    private void help(CommandSender sender) {
        msg().sendPlain(sender, "command.help-header", "version", plugin.getUpdateChecker().getCurrent());
        for (Sub s : SUBS) {
            if (sender.hasPermission(s.permission())) {
                msg().sendPlain(sender, "command.help-line", "usage", s.usage(), "desc", msg().raw("command.help." + s.name()));
            }
        }
    }

    private void list(CommandSender sender) {
        if (sender instanceof Player player) {
            new TemplateListGui(plugin, 0).open(player);
            return;
        }
        List<ItemTemplate> all = new ArrayList<>(templates().getAll());
        if (all.isEmpty()) {
            msg().send(sender, "command.list-empty");
            return;
        }
        all.sort(Comparator.comparing(ItemTemplate::getId, String.CASE_INSENSITIVE_ORDER));
        msg().send(sender, "command.list-header", "count", all.size());
        for (ItemTemplate t : all) {
            msg().sendPlain(sender, "command.list-item", "id", t.getId(), "material", t.getMaterial().name());
        }
    }

    private void edit(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            msg().send(sender, "command.player-only");
            return;
        }
        if (args.length < 2) {
            msg().send(sender, "command.usage", "usage", "/itemlock edit <名稱>");
            return;
        }
        String id = args[1];
        if (!templates().hasTemplate(id)) {
            msg().send(sender, "command.not-found", "id", id);
            return;
        }
        new TemplateSettingsGui(plugin, id, 0).open(player);
    }

    private void add(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            msg().send(sender, "command.player-only");
            return;
        }
        if (args.length < 2) {
            msg().send(sender, "command.usage", "usage", "/itemlock add <名稱> [-f]");
            return;
        }
        String id = args[1];
        if (!templates().isValidNewId(id)) {
            msg().send(sender, "command.invalid-id");
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            msg().send(sender, "command.hand-empty");
            return;
        }
        boolean exists = templates().hasTemplate(id);
        boolean force = args.length >= 3 && (args[2].equalsIgnoreCase("-f") || args[2].equalsIgnoreCase("--force"));
        if (exists && !force) {
            msg().send(sender, "command.add-exists", "id", id);
            return;
        }
        try {
            templates().saveTemplate(id, hand);
        } catch (RuntimeException e) {
            plugin.getLogger().warning("儲存樣本失敗 " + id + "：" + e);
            msg().send(sender, "command.save-failed");
            return;
        }
        msg().send(sender, exists ? "command.add-overwrite" : "command.add-success", "id", id);
    }

    private void remove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            msg().send(sender, "command.usage", "usage", "/itemlock remove <名稱>");
            return;
        }
        String id = args[1];
        if (templates().removeTemplate(id)) {
            msg().send(sender, "command.remove-success", "id", id);
        } else {
            msg().send(sender, "command.not-found", "id", id);
        }
    }

    private void check(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            msg().send(sender, "command.player-only");
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            msg().send(sender, "command.hand-empty");
            return;
        }
        ItemTemplate t = plugin.getItemMatcher().find(hand);
        if (t != null) {
            msg().send(sender, "command.check-protected", "id", t.getId());
        } else {
            msg().send(sender, "command.check-not-protected");
        }
    }

    private void reload(CommandSender sender) {
        int count = plugin.reloadAll();
        msg().send(sender, "command.reload", "count", count);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            for (Sub s : SUBS) {
                if (s.name().startsWith(prefix) && sender.hasPermission(s.permission())) {
                    out.add(s.name());
                }
            }
            return out;
        }
        String name = args[0].toLowerCase(Locale.ROOT);
        if (name.equals("delete")) {
            name = "remove";
        }
        Sub sub = find(name);
        if (sub == null || !sender.hasPermission(sub.permission())) {
            return out;
        }
        if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            switch (sub.name()) {
                case "edit", "remove" -> {
                    for (String id : templates().getIds()) {
                        if (id.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                            out.add(id);
                        }
                    }
                    out.sort(String.CASE_INSENSITIVE_ORDER);
                }
                case "add" -> {
                    if (sender instanceof Player player) {
                        ItemStack hand = player.getInventory().getItemInMainHand();
                        if (!hand.getType().isAir()) {
                            String suggestion = hand.getType().name().toLowerCase(Locale.ROOT);
                            if (suggestion.startsWith(prefix)) {
                                out.add(suggestion);
                            }
                        }
                    }
                }
                default -> { }
            }
        } else if (args.length == 3 && sub.name().equals("add") && templates().hasTemplate(args[1])
                && "-f".startsWith(args[2].toLowerCase(Locale.ROOT))) {
            out.add("-f");
        }
        return out;
    }
}
