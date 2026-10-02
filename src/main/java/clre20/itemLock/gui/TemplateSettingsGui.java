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
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 個別物品保護屬性設定 GUI (27 格箱子介面)。
 * 允許管理員自由切換特定保護樣本的各項防護開關（三態切換：繼承全域 / 強制啟用 / 強制關閉），
 * 並支援一鍵取出樣本複製品或刪除樣本。
 */
public class TemplateSettingsGui implements GuiHolder {

    private final TemplateManager templateManager;
    private final PluginConfig config;
    private final ItemTemplate template;
    private final int returnPage;
    private final Inventory inventory;

    private static final int SLOT_SAMPLE = 4;
    private static final int SLOT_DENY_DROP = 10;
    private static final int SLOT_DENY_OFFHAND = 11;
    private static final int SLOT_DENY_BLOCK_PLACE = 12;
    private static final int SLOT_DENY_ALTAR = 13;
    private static final int SLOT_DENY_ENTITY = 14;
    private static final int SLOT_ALLOW_HOPPER_MOVE = 15;
    private static final int SLOT_ALLOW_HOPPER_PICKUP = 16;
    private static final int SLOT_BACK = 22;
    private static final int SLOT_DELETE = 26;

    public TemplateSettingsGui(TemplateManager templateManager, PluginConfig config, ItemTemplate template, int returnPage) {
        this.templateManager = templateManager;
        this.config = config;
        this.template = template;
        this.returnPage = returnPage;

        Component title = config.parseComponent("<gold>設定: <yellow>" + template.getId() + "</yellow></gold>");
        this.inventory = Bukkit.createInventory(this, 27, title);
        render();
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    public void render() {
        inventory.clear();

        // 填充背景裝飾板
        ItemStack filler = createItem(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < 27; i++) {
            inventory.setItem(i, filler);
        }

        // Slot 4: 樣本展示與取出副本
        ItemStack sampleIcon = template.getItemStack();
        ItemMeta sampleMeta = sampleIcon.getItemMeta();
        if (sampleMeta != null) {
            List<Component> lore = sampleMeta.hasLore() ? new ArrayList<>(sampleMeta.lore()) : new ArrayList<>();
            lore.add(Component.empty());
            lore.add(config.parseComponent("<yellow>ID: <gold>" + template.getId() + "</gold></yellow>"));
            lore.add(config.parseComponent("<yellow>材質: <white>" + template.getMaterial().name() + "</white></yellow>"));
            lore.add(config.parseComponent("<yellow>建立時間: <white>" + template.getCreatedAt() + "</white></yellow>"));
            lore.add(Component.empty());
            lore.add(config.parseComponent("<green>▶ 點擊滑鼠左鍵可取得 1 個樣本複製品</green>"));
            sampleMeta.lore(lore);
            sampleIcon.setItemMeta(sampleMeta);
        }
        inventory.setItem(SLOT_SAMPLE, sampleIcon);

        TemplateSettings s = template.getSettings();

        // Slot 10: 禁止按 Q 丟棄 (deny-drop)
        inventory.setItem(SLOT_DENY_DROP, createToggleItem(
                "禁止按 Q 丟棄 (Deny Drop)",
                List.of(
                        "禁止玩家按 Q 鍵或拖曳丟出此物品，",
                        "防範意外掉落於世界中被其他玩家撿走。"
                ),
                s.getDenyDrop(),
                s.isDenyDrop(config),
                Material.FEATHER
        ));

        // Slot 11: 禁止放置在副手 (deny-offhand)
        inventory.setItem(SLOT_DENY_OFFHAND, createToggleItem(
                "禁止放置在副手 (Deny Offhand)",
                List.of(
                        "封鎖 F 換手、基岩版 /geyser offhand 與副手槽，",
                        "全面防範透過副手進行方塊偷放或違規交互。"
                ),
                s.getDenyOffhand(),
                s.isDenyOffhand(config),
                Material.SHIELD
        ));

        // Slot 12: 禁止作為方塊放置 (deny-block-place)
        inventory.setItem(SLOT_DENY_BLOCK_PLACE, createToggleItem(
                "禁止放置方塊 (Deny Block Place)",
                List.of(
                        "禁止主手或副手將此上鎖物品作為方塊放置在世界上，",
                        "避免具有方塊形態的貴重物品被放置化。"
                ),
                s.getDenyBlockPlace(),
                s.isDenyBlockPlace(config),
                Material.BRICKS
        ));

        // Slot 13: 防祭壇/機器交互 (deny-altar-interact)
        inventory.setItem(SLOT_DENY_ALTAR, createToggleItem(
                "防祭壇/方塊轉化 (Deny Altar)",
                List.of(
                        "禁止手持此物品右鍵附魔台、合成台、祭壇或自訂機械，",
                        "防範物品被其他外掛的方塊轉化機制吞噬消耗。"
                ),
                s.getDenyAltarInteract(),
                s.isDenyAltarInteract(config),
                Material.ENCHANTING_TABLE
        ));

        // Slot 14: 防展示框/盔甲架 (deny-entity-interact)
        inventory.setItem(SLOT_DENY_ENTITY, createToggleItem(
                "防展示框/盔甲架 (Deny Entity)",
                List.of(
                        "禁止將此物品右鍵放入展示框或裝備在盔甲架上，",
                        "防範被其他玩家利用實體漏洞或未受保區域取走。"
                ),
                s.getDenyEntityInteract(),
                s.isDenyEntityInteract(config),
                Material.ITEM_FRAME
        ));

        // Slot 15: 允許漏斗抽取傳輸 (allow-hopper-move)
        inventory.setItem(SLOT_ALLOW_HOPPER_MOVE, createToggleItem(
                "允許漏斗抽取 (Hopper Move)",
                List.of(
                        "是否允許漏斗或漏斗礦車自容器中抽取並傳輸此物品，",
                        "開啟時可進行自動化物流；關閉時杜絕漏斗吸取。"
                ),
                s.getAllowHopperMove(),
                s.isAllowHopperMove(config),
                Material.HOPPER
        ));

        // Slot 16: 允許地面漏斗拾取 (allow-hopper-pickup)
        inventory.setItem(SLOT_ALLOW_HOPPER_PICKUP, createToggleItem(
                "允許地面漏斗拾取 (Hopper Pickup)",
                List.of(
                        "是否允許地面上的漏斗直接吸取掉落在上方的此物品，",
                        "開啟時支援掉落物收集；關閉時漏斗無法吸走。"
                ),
                s.getAllowHopperPickup(),
                s.isAllowHopperPickup(config),
                Material.MINECART
        ));

        // Slot 22: 返回樣本清單按鈕
        inventory.setItem(SLOT_BACK, createItem(
                Material.ARROW,
                "<yellow>◀ 返回樣本清單</yellow>",
                List.of("<gray>點擊返回第 " + (returnPage + 1) + " 頁樣本庫清單</gray>")
        ));

        // Slot 26: 刪除樣本按鈕
        inventory.setItem(SLOT_DELETE, createItem(
                Material.BARRIER,
                "<red>✖ 刪除此樣本</red>",
                List.of(
                        "<gray>徹底自樣本庫與磁碟刪除此物品保護樣本。</gray>",
                        "<dark_red>⚠ Shift + 右鍵 點擊以確認刪除</dark_red>"
                )
        ));
    }

    private ItemStack createToggleItem(String name, List<String> descriptionLines, Boolean settingState, boolean effectiveValue, Material baseIcon) {
        Material displayMaterial;
        String stateText;

        if (settingState == null) {
            displayMaterial = Material.YELLOW_DYE;
            stateText = "<yellow>繼承全域預設</yellow> (當前有效: " + (effectiveValue ? "<green>啟用</green>" : "<red>停用</red>") + ")";
        } else if (settingState) {
            displayMaterial = Material.LIME_DYE;
            stateText = "<green>強制啟用 (ENFORCE_TRUE)</green>";
        } else {
            displayMaterial = Material.RED_DYE;
            stateText = "<red>強制停用 (ENFORCE_FALSE)</red>";
        }

        ItemStack item = new ItemStack(displayMaterial);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(config.parseComponent("<gold>" + name + "</gold>"));
            List<Component> lore = new ArrayList<>();
            lore.add(config.parseComponent("<gray>狀態: </gray>" + stateText));
            lore.add(Component.empty());
            lore.add(config.parseComponent("<yellow>功能說明：</yellow>"));
            if (descriptionLines != null) {
                for (String desc : descriptionLines) {
                    lore.add(config.parseComponent("<gray>" + desc + "</gray>"));
                }
            }
            lore.add(Component.empty());
            lore.add(config.parseComponent("<yellow>點擊切換下一個狀態：</yellow>"));
            lore.add(config.parseComponent("<gray>繼承全域 ➔ 強制啟用 ➔ 強制停用 ➔ 繼承全域</gray>"));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
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
        if (slot >= 27) {
            return; // 點擊玩家自身背包由 GuiListener 全局取消，避免非法操作
        }

        TemplateSettings s = template.getSettings();
        boolean changed = false;

        switch (slot) {
            case SLOT_SAMPLE -> {
                // 發放樣本副本
                player.getInventory().addItem(template.getItemStack());
                player.sendMessage(config.parseComponent(config.getPrefix() + "<green>已將保護樣本 <gold>" + template.getId() + "</gold> 副本發放至您的背包。</green>"));
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
                return;
            }
            case SLOT_DENY_DROP -> {
                s.setDenyDrop(TemplateSettings.cycle(s.getDenyDrop()));
                changed = true;
            }
            case SLOT_DENY_OFFHAND -> {
                s.setDenyOffhand(TemplateSettings.cycle(s.getDenyOffhand()));
                changed = true;
            }
            case SLOT_DENY_BLOCK_PLACE -> {
                s.setDenyBlockPlace(TemplateSettings.cycle(s.getDenyBlockPlace()));
                changed = true;
            }
            case SLOT_DENY_ALTAR -> {
                s.setDenyAltarInteract(TemplateSettings.cycle(s.getDenyAltarInteract()));
                changed = true;
            }
            case SLOT_DENY_ENTITY -> {
                s.setDenyEntityInteract(TemplateSettings.cycle(s.getDenyEntityInteract()));
                changed = true;
            }
            case SLOT_ALLOW_HOPPER_MOVE -> {
                s.setAllowHopperMove(TemplateSettings.cycle(s.getAllowHopperMove()));
                changed = true;
            }
            case SLOT_ALLOW_HOPPER_PICKUP -> {
                s.setAllowHopperPickup(TemplateSettings.cycle(s.getAllowHopperPickup()));
                changed = true;
            }
            case SLOT_BACK -> {
                new TemplateListGui(templateManager, config, returnPage).open(player);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.0f);
                return;
            }
            case SLOT_DELETE -> {
                if (event.isShiftClick() && event.getClick() == ClickType.SHIFT_RIGHT) {
                    templateManager.removeTemplate(template.getId());
                    player.sendMessage(config.parseComponent(config.getPrefix() + "<green>已成功刪除保護樣本：<gold>" + template.getId() + "</gold></green>"));
                    player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_BREAK, 0.8f, 1.0f);
                    new TemplateListGui(templateManager, config, returnPage).open(player);
                } else {
                    player.sendMessage(config.parseComponent(config.getPrefix() + "<red>請使用 <yellow>Shift + 右鍵</yellow> 點擊以確認刪除此樣本！</red>"));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                }
                return;
            }
        }

        if (changed) {
            try {
                templateManager.updateTemplateSettings(template.getId(), s);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
                render();
            } catch (IOException e) {
                player.sendMessage(config.parseComponent(config.getPrefix() + "<red>更新設定檔案失敗: " + e.getMessage() + "</red>"));
            }
        }
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
