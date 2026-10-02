package clre20.itemLock.gui;

import clre20.itemLock.config.PluginConfig;
import clre20.itemLock.model.ItemTemplate;
import clre20.itemLock.model.TemplateSettings;
import clre20.itemLock.template.TemplateManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 保護物品樣本清單 GUI (54 格分頁箱子介面)。
 * 展示所有已登錄之保護物品樣本，點擊任一物品即可開啟該物品專屬的個別屬性開關設定箱。
 */
public class TemplateListGui implements GuiHolder {

    private static final int PAGE_SIZE = 45; // 0 ~ 44 格為樣本展示區
    private static final int SLOT_PREV = 45;
    private static final int SLOT_INFO = 49;
    private static final int SLOT_NEXT = 53;

    private final TemplateManager templateManager;
    private final PluginConfig config;
    private final int page;
    private final Inventory inventory;
    private final List<ItemTemplate> currentTemplates;

    public TemplateListGui(TemplateManager templateManager, PluginConfig config, int page) {
        this.templateManager = templateManager;
        this.config = config;
        this.page = Math.max(0, page);

        Map<String, ItemTemplate> all = templateManager.getAllTemplates();
        List<ItemTemplate> list = new ArrayList<>(all.values());
        list.sort((a, b) -> a.getId().compareToIgnoreCase(b.getId()));

        int totalCount = list.size();
        int maxPage = Math.max(0, (totalCount - 1) / PAGE_SIZE);
        int validPage = Math.min(this.page, maxPage);

        Component title = config.parseComponent("<gold>保護樣本庫清單 (<yellow>" + (validPage + 1) + "</yellow>/<yellow>" + (maxPage + 1) + "</yellow>)</gold>");
        this.inventory = Bukkit.createInventory(this, 54, title);

        int startIndex = validPage * PAGE_SIZE;
        int endIndex = Math.min(startIndex + PAGE_SIZE, totalCount);

        this.currentTemplates = (startIndex < totalCount) ? list.subList(startIndex, endIndex) : List.of();

        render(totalCount, maxPage, validPage);
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    private void render(int totalCount, int maxPage, int validPage) {
        inventory.clear();

        // 渲染樣本展示物品 (0 ~ 44)
        for (int i = 0; i < currentTemplates.size(); i++) {
            ItemTemplate template = currentTemplates.get(i);
            ItemStack item = template.getItemStack();
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
                lore.add(Component.empty());
                lore.add(config.parseComponent("<yellow>ID: <gold>" + template.getId() + "</gold></yellow>"));
                lore.add(config.parseComponent("<yellow>材質: <white>" + template.getMaterial().name() + "</white></yellow>"));

                TemplateSettings s = template.getSettings();
                lore.add(config.parseComponent("<gray>── 個別防護摘要 ──</gray>"));
                lore.add(config.parseComponent("<gray>禁止副手: </gray>" + formatBool(s.isDenyOffhand(config))));
                lore.add(config.parseComponent("<gray>禁止放置: </gray>" + formatBool(s.isDenyBlockPlace(config))));
                lore.add(config.parseComponent("<gray>禁止丟棄: </gray>" + formatBool(s.isDenyDrop(config))));
                lore.add(config.parseComponent("<gray>漏斗抽取: </gray>" + formatBool(s.isAllowHopperMove(config))));
                lore.add(Component.empty());
                lore.add(config.parseComponent("<green>▶ 點擊左鍵進入個別屬性設定介面</green>"));
                meta.lore(lore);
                item.setItemMeta(meta);
            }
            inventory.setItem(i, item);
        }

        // 控制列背景裝飾 (45 ~ 53)
        ItemStack filler = createItem(Material.BLACK_STAINED_GLASS_PANE, " ", null);
        for (int i = 45; i < 54; i++) {
            inventory.setItem(i, filler);
        }

        // 上一頁按鈕 (Slot 45)
        if (validPage > 0) {
            inventory.setItem(SLOT_PREV, createItem(
                    Material.ARROW,
                    "<yellow>◀ 上一頁</yellow>",
                    List.of("<gray>點擊前往第 " + validPage + " 頁</gray>")
            ));
        }

        // 中間統計書本 (Slot 49)
        inventory.setItem(SLOT_INFO, createItem(
                Material.BOOK,
                "<gold>樣本統計與頁數</gold>",
                List.of(
                        "<gray>當前頁碼: <yellow>" + (validPage + 1) + " / " + (maxPage + 1) + "</yellow></gray>",
                        "<gray>已登錄樣本總數: <green>" + totalCount + "</green> 個</gray>",
                        "",
                        "<gray>點擊任一物品以自由自訂該物品之防護設定。</gray>"
                )
        ));

        // 下一頁按鈕 (Slot 53)
        if (validPage < maxPage) {
            inventory.setItem(SLOT_NEXT, createItem(
                    Material.ARROW,
                    "<yellow>下一頁 ▶</yellow>",
                    List.of("<gray>點擊前往第 " + (validPage + 2) + " 頁</gray>")
            ));
        }
    }

    private String formatBool(boolean val) {
        return val ? "<green>開啟</green>" : "<red>關閉</red>";
    }

    private ItemStack createItem(Material material, String name, List<String> loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(config.parseComponent(name));
            if (loreLines != null && !loreLines.isEmpty()) {
                List<Component> lore = new ArrayList<>();
                for (String line : loreLines) {
                    lore.add(config.parseComponent(line));
                }
                meta.lore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int slot = event.getRawSlot();
        if (slot >= 54) {
            return;
        }

        // 點擊樣本展示區 (0 ~ 44)
        if (slot < PAGE_SIZE && slot < currentTemplates.size()) {
            ItemTemplate template = currentTemplates.get(slot);
            new TemplateSettingsGui(templateManager, config, template, page).open(player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
            return;
        }

        // 點擊上一頁
        if (slot == SLOT_PREV && page > 0) {
            new TemplateListGui(templateManager, config, page - 1).open(player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.0f);
            return;
        }

        // 點擊下一頁
        Map<String, ItemTemplate> all = templateManager.getAllTemplates();
        int maxPage = Math.max(0, (all.size() - 1) / PAGE_SIZE);
        if (slot == SLOT_NEXT && page < maxPage) {
            new TemplateListGui(templateManager, config, page + 1).open(player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.0f);
        }
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
