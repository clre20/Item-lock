package clre20.itemLock.gui;

import clre20.itemLock.ItemLock;
import clre20.itemLock.command.Perm;
import clre20.itemLock.config.Messages;
import clre20.itemLock.config.PluginConfig;
import clre20.itemLock.model.ItemTemplate;
import clre20.itemLock.model.TemplateSettings;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 樣本列表（6 排）：0~44 樣本，45 上一頁｜49 資訊｜53 下一頁。
 */
public final class TemplateListGui implements GuiHolder {

    private static final String NAME = "TemplateListGui";
    private static final int PAGE_SIZE = 45;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_INFO = 49;
    private static final int SLOT_NEXT = 53;

    private final ItemLock plugin;
    private final Inventory inventory;
    private final List<String> ids;
    private final int page;
    private final int pages;

    public TemplateListGui(ItemLock plugin, int requestedPage) {
        this.plugin = plugin;
        Messages msg = plugin.getMessages();
        PluginConfig config = plugin.getPluginConfig();

        List<ItemTemplate> all = new ArrayList<>(plugin.getTemplateManager().getAll());
        all.sort(Comparator.comparing(ItemTemplate::getId, String.CASE_INSENSITIVE_ORDER));
        this.pages = Math.max(1, (all.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        this.page = Math.max(0, Math.min(requestedPage, pages - 1));
        this.inventory = Bukkit.createInventory(this, 54, msg.get("gui.list.title", "page", page + 1, "pages", pages));

        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, all.size());
        this.ids = new ArrayList<>(Math.max(0, end - start));
        for (int i = start; i < end; i++) {
            ItemTemplate t = all.get(i);
            ids.add(t.getId());
            inventory.setItem(i - start, display(t, msg, config));
        }

        ItemStack filler = GuiHolder.icon(plugin, NAME, Material.BLACK_STAINED_GLASS_PANE, Component.empty(), null);
        for (int i = 45; i < 54; i++) {
            inventory.setItem(i, filler);
        }
        if (page > 0) {
            inventory.setItem(SLOT_PREV, GuiHolder.icon(plugin, NAME, Material.ARROW, msg.item("gui.prev"), null));
        }
        inventory.setItem(SLOT_INFO, GuiHolder.icon(plugin, NAME, Material.BOOK,
                msg.item("gui.list.info-name", "count", all.size()), msg.itemList("gui.list.info-lore")));
        if (page < pages - 1) {
            inventory.setItem(SLOT_NEXT, GuiHolder.icon(plugin, NAME, Material.ARROW, msg.item("gui.next"), null));
        }
    }

    private ItemStack display(ItemTemplate t, Messages msg, PluginConfig config) {
        ItemStack item = t.getItemStack();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        TemplateSettings s = t.getSettings();
        List<Component> lore = meta.hasLore() && meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        lore.addAll(msg.itemList("gui.list.item-lore",
                "id", t.getId(),
                "material", t.getMaterial().name(),
                "offhand", onOff(msg, s.isDenyOffhand(config)),
                "place", onOff(msg, s.isDenyBlockPlace(config)),
                "drop", onOff(msg, s.isDenyDrop(config)),
                "hopper", onOff(msg, s.isAllowHopperMove(config))));
        meta.lore(lore);
        item.setItemMeta(meta);
        return GuiHolder.sign(plugin, item, NAME);
    }

    static Messages.Raw onOff(Messages msg, boolean value) {
        return msg.rawOf(value ? "gui.value-on" : "gui.value-off");
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    @Override
    public void onLeftClick(InventoryClickEvent event, int slot) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (slot < PAGE_SIZE) {
            if (slot >= ids.size()) {
                return;
            }
            if (!player.hasPermission(Perm.EDIT)) {
                plugin.getMessages().send(player, "command.no-permission");
                return;
            }
            String id = ids.get(slot);
            if (!plugin.getTemplateManager().hasTemplate(id)) {
                plugin.getMessages().send(player, "command.not-found", "id", id);
                Gui.open(plugin, player, () -> new TemplateListGui(plugin, page).inventory);
                return;
            }
            Gui.click(plugin, player);
            Gui.open(plugin, player, () -> new TemplateSettingsGui(plugin, id, page).getInventory());
        } else if (slot == SLOT_PREV && page > 0) {
            Gui.click(plugin, player);
            Gui.open(plugin, player, () -> new TemplateListGui(plugin, page - 1).inventory);
        } else if (slot == SLOT_NEXT && page < pages - 1) {
            Gui.click(plugin, player);
            Gui.open(plugin, player, () -> new TemplateListGui(plugin, page + 1).inventory);
        }
    }

    @Override
    public Plugin getPlugin() {
        return plugin;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    /** 給 GUI 共用的小工具。 */
    static final class Gui {
        private Gui() {}

        /** 在點擊事件中換介面要延到下一 tick；只存 UUID。 */
        static void open(ItemLock plugin, Player player, java.util.function.Supplier<Inventory> factory) {
            java.util.UUID id = player.getUniqueId();
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player p = Bukkit.getPlayer(id);
                if (p != null) {
                    p.openInventory(factory.get());
                }
            });
        }

        static void click(ItemLock plugin, Player player) {
            sound(plugin, player, Sound.UI_BUTTON_CLICK, 1.0f);
        }

        static void sound(ItemLock plugin, Player player, Sound sound, float pitch) {
            if (plugin.getPluginConfig().isGuiSounds()) {
                player.playSound(player.getLocation(), sound, 0.8f, pitch);
            }
        }
    }
}
