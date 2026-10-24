package clre20.itemLock.gui;

import clre20.itemLock.ItemLock;
import clre20.itemLock.command.Perm;
import clre20.itemLock.config.Messages;
import clre20.itemLock.config.PluginConfig;
import clre20.itemLock.model.ItemTemplate;
import clre20.itemLock.model.TemplateSettings;
import clre20.itemLock.gui.TemplateListGui.Gui;
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
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Function;

/**
 * 樣本設定（3 排）：4 樣本（取得複製品）｜10~16 七個開關｜22 返回｜26 刪除（左鍵點兩下確認）。
 */
public final class TemplateSettingsGui implements GuiHolder {

    private static final String NAME = "TemplateSettingsGui";
    private static final int SLOT_SAMPLE = 4;
    private static final int SLOT_BACK = 22;
    private static final int SLOT_DELETE = 26;
    private static final long CONFIRM_MS = 5000L;

    /** 開關定義：格位、訊息 key、圖示、讀值、寫值、有效值。 */
    private record Toggle(int slot, String key, Material icon,
                          Function<TemplateSettings, Boolean> get,
                          BiConsumer<TemplateSettings, Boolean> set,
                          BiPredicate<TemplateSettings, PluginConfig> effective) {}

    private static final List<Toggle> TOGGLES = List.of(
            new Toggle(10, "deny-drop", Material.FEATHER,
                    TemplateSettings::getDenyDrop, TemplateSettings::setDenyDrop, TemplateSettings::isDenyDrop),
            new Toggle(11, "deny-offhand", Material.SHIELD,
                    TemplateSettings::getDenyOffhand, TemplateSettings::setDenyOffhand, TemplateSettings::isDenyOffhand),
            new Toggle(12, "deny-block-place", Material.BRICKS,
                    TemplateSettings::getDenyBlockPlace, TemplateSettings::setDenyBlockPlace, TemplateSettings::isDenyBlockPlace),
            new Toggle(13, "deny-altar-interact", Material.ENCHANTING_TABLE,
                    TemplateSettings::getDenyAltarInteract, TemplateSettings::setDenyAltarInteract, TemplateSettings::isDenyAltarInteract),
            new Toggle(14, "deny-entity-interact", Material.ITEM_FRAME,
                    TemplateSettings::getDenyEntityInteract, TemplateSettings::setDenyEntityInteract, TemplateSettings::isDenyEntityInteract),
            new Toggle(15, "allow-hopper-move", Material.HOPPER,
                    TemplateSettings::getAllowHopperMove, TemplateSettings::setAllowHopperMove, TemplateSettings::isAllowHopperMove),
            new Toggle(16, "allow-hopper-pickup", Material.MINECART,
                    TemplateSettings::getAllowHopperPickup, TemplateSettings::setAllowHopperPickup, TemplateSettings::isAllowHopperPickup)
    );

    private final ItemLock plugin;
    private final String id;
    private final int returnPage;
    private final Inventory inventory;
    private long deleteConfirmUntil;

    public TemplateSettingsGui(ItemLock plugin, String id, int returnPage) {
        this.plugin = plugin;
        this.id = id;
        this.returnPage = returnPage;
        this.inventory = Bukkit.createInventory(this, 27, plugin.getMessages().get("gui.settings.title", "id", id));
        render();
    }

    private void render() {
        ItemTemplate t = plugin.getTemplateManager().getTemplate(id).orElse(null);
        if (t == null) {
            return;
        }
        Messages msg = plugin.getMessages();
        PluginConfig config = plugin.getPluginConfig();

        ItemStack filler = GuiHolder.icon(plugin, NAME, Material.GRAY_STAINED_GLASS_PANE, Component.empty(), null);
        for (int i = 0; i < 27; i++) {
            inventory.setItem(i, filler);
        }

        ItemStack sample = t.getItemStack();
        ItemMeta meta = sample.getItemMeta();
        if (meta != null) {
            List<Component> lore = meta.hasLore() && meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
            lore.addAll(msg.itemList("gui.settings.sample-lore", "id", id, "material", t.getMaterial().name(), "created", t.getCreatedAt()));
            meta.lore(lore);
            sample.setItemMeta(meta);
        }
        inventory.setItem(SLOT_SAMPLE, GuiHolder.sign(plugin, sample, NAME));

        TemplateSettings s = t.getSettings();
        for (Toggle toggle : TOGGLES) {
            Boolean state = toggle.get().apply(s);
            Material icon = state == null ? Material.YELLOW_DYE : state ? Material.LIME_DYE : Material.RED_DYE;
            List<Component> lore = new ArrayList<>(msg.itemList("gui.settings." + toggle.key() + ".lore"));
            if (state == null) {
                lore.add(msg.item("gui.settings.state-inherit", "value",
                        TemplateListGui.onOff(msg, toggle.effective().test(s, config))));
            } else {
                lore.add(msg.item(state ? "gui.settings.state-on" : "gui.settings.state-off"));
            }
            lore.add(msg.item("gui.settings.toggle-hint"));
            inventory.setItem(toggle.slot(), GuiHolder.icon(plugin, NAME, icon,
                    msg.item("gui.settings." + toggle.key() + ".name"), lore));
        }

        inventory.setItem(SLOT_BACK, GuiHolder.icon(plugin, NAME, Material.ARROW, msg.item("gui.settings.back"), null));
        renderDelete();
    }

    private void renderDelete() {
        Messages msg = plugin.getMessages();
        boolean confirming = System.currentTimeMillis() < deleteConfirmUntil;
        inventory.setItem(SLOT_DELETE, confirming
                ? GuiHolder.icon(plugin, NAME, Material.TNT, msg.item("gui.settings.delete-confirm"), null)
                : GuiHolder.icon(plugin, NAME, Material.BARRIER, msg.item("gui.settings.delete"), msg.itemList("gui.settings.delete-lore")));
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    @Override
    public void onLeftClick(InventoryClickEvent event, int slot) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Messages msg = plugin.getMessages();
        if (slot == SLOT_BACK) {
            Gui.click(plugin, player);
            Gui.open(plugin, player, () -> new TemplateListGui(plugin, returnPage).getInventory());
            return;
        }

        ItemTemplate t = plugin.getTemplateManager().getTemplate(id).orElse(null);
        if (t == null) {
            msg.send(player, "command.not-found", "id", id);
            Gui.open(plugin, player, () -> new TemplateListGui(plugin, returnPage).getInventory());
            return;
        }

        if (slot == SLOT_SAMPLE) {
            if (!player.hasPermission(Perm.EDIT)) {
                msg.send(player, "command.no-permission");
                return;
            }
            Map<Integer, ItemStack> left = player.getInventory().addItem(t.getItemStack());
            for (ItemStack drop : left.values()) {
                player.getWorld().dropItem(player.getLocation(), drop);
            }
            msg.send(player, "gui.settings.copy-given", "id", id);
            Gui.sound(plugin, player, Sound.ENTITY_ITEM_PICKUP, 1.0f);
            return;
        }

        if (slot == SLOT_DELETE) {
            if (!player.hasPermission(Perm.REMOVE)) {
                msg.send(player, "command.no-permission");
                return;
            }
            long now = System.currentTimeMillis();
            if (now < deleteConfirmUntil) {
                plugin.getTemplateManager().removeTemplate(id);
                msg.send(player, "command.remove-success", "id", id);
                Gui.sound(plugin, player, Sound.BLOCK_ANVIL_BREAK, 1.0f);
                Gui.open(plugin, player, () -> new TemplateListGui(plugin, returnPage).getInventory());
            } else {
                deleteConfirmUntil = now + CONFIRM_MS;
                renderDelete();
                Gui.click(plugin, player);
            }
            return;
        }

        for (Toggle toggle : TOGGLES) {
            if (toggle.slot() == slot) {
                if (!player.hasPermission(Perm.EDIT)) {
                    msg.send(player, "command.no-permission");
                    return;
                }
                TemplateSettings s = t.getSettings();
                toggle.set().accept(s, TemplateSettings.cycle(toggle.get().apply(s)));
                plugin.getTemplateManager().markDirty(id);
                Gui.click(plugin, player);
                render();
                return;
            }
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
}
