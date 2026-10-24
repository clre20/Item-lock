# Item-lock

Item-lock 是 Paper 1.21+ 的物品保護插件。把任務道具、自訂裝備、代幣等物品登錄為「樣本」後，這些物品只能放在純儲存容器裡，無法被鐵砧、砂輪、熔爐、合成台或其他插件的介面加工、修改或吞掉。比對已針對效能最佳化：材質沒有樣本的物品只需一次查表，不讀取物品資料。

## 功能特性

- **樣本比對**：
  * 依材質建立索引，大部分物品不需要任何比對
  * 忽略耐久消耗與鐵砧修復成本，用過、修過的物品仍算同一個
  * 已繪製地圖比對 map_id；其餘名稱、Lore、附魔、自訂模型、PDC 等全部要相同
- **只能放在純儲存容器**：
  * 箱子、陷阱箱、大箱子、木桶、潛影盒、末影箱、玩家背包
  * 其他介面擋下點擊放入、Shift 移入、數字鍵交換、拖曳、雙擊收集
- **箱子介面管理**：
  * `/itemlock gui` 列出所有樣本，左鍵進入設定
  * 每個樣本可個別設定 7 個開關（跟隨全域／強制開／強制關）
  * 全部左鍵操作，刪除需點兩下確認
- **世界互動防護**：
  * 副手：擋 F 換手、`/geyser offhand`、副手槽；殘留時自動移回背包
  * 禁止放置方塊、禁止右鍵機器方塊、禁止放進展示框或盔甲架
  * 合成格內有受保護物品時清空產物（防地圖複製）
  * 可選禁止丟出；掉落物免疫火焰、爆炸，生物不能撿
- **漏斗**：
  * 可分別設定漏斗搬運與吸取掉落物
- **相容外掛**：
  * Shopkeepers（交易與編輯介面）、eShulkerBox（手持潛影盒）
  * AdvancedSpyInventory（/invsee、/enderchest）、Cmail（郵件附件）
- **低資源用量**：
  * 主執行緒只做必要判斷；寫檔在背景執行緒批次處理
  * 版本檢查快取 10 分鐘，不輪詢
- **設定與訊息**：
  * 所有文字都在 `messages.yml`，支援 MiniMessage 與 & 顏色碼
  * `config.yml`、`messages.yml` 啟動與重載時自動補齊缺少的項目

## 指令與權限

主指令為 `/itemlock`。

| 指令 | 說明 | 執行者 | 權限節點 | 預設 |
| :--- | :--- | :--- | :--- | :--- |
| `/itemlock help` | 列出有權限的指令 | 遊戲內／後台 | `itemlock.help` | OP |
| `/itemlock gui` | 開啟樣本列表介面 | 玩家 | `itemlock.list` | OP |
| `/itemlock list` | 玩家開介面，後台顯示文字列表 | 遊戲內／後台 | `itemlock.list` | OP |
| `/itemlock edit <名稱>` | 開啟樣本設定介面 | 玩家 | `itemlock.edit` | OP |
| `/itemlock add <名稱> [-f]` | 把主手物品設為樣本（`-f` 覆蓋） | 玩家 | `itemlock.add` | OP |
| `/itemlock remove <名稱>` | 刪除樣本 | 遊戲內／後台 | `itemlock.remove` | OP |
| `/itemlock check` | 檢查主手物品是否受保護 | 玩家 | `itemlock.check` | OP |
| `/itemlock reload` | 重新載入設定、訊息與樣本 | 遊戲內／後台 | `itemlock.reload` | OP |
| `/itemlock version` | 檢查 GitHub 新版本 | 遊戲內／後台 | `itemlock.version` | OP |

> - `itemlock.admin` 包含以上全部權限
> - 有 `itemlock.version` 的玩家登入 3 秒後，若有新版本會收到提醒
> - 名稱只能用英數字、`_`、`-`，最多 32 字

## 使用範例

### 1. 保護一把自訂武器
1. 手持武器輸入：
```bash
/itemlock add dragon_sword
```
2. 之後這把武器（含磨損、修理過的）都不能放進鐵砧、砂輪等介面。

### 2. 讓某個樣本禁止丟出
1. 開啟設定：
```bash
/itemlock edit dragon_sword
```
2. 左鍵點「禁止丟出」，切換成「強制開」。
3. 設定會在 10 秒內自動存檔（關服時也會存）。

### 3. 關閉更新檢查
在 `config.yml` 設定後執行 `/itemlock reload`：
```bash
update-checker:
  check-on-startup: false
  notify-on-join: false
```

## 設定檔

`config.yml` 主要項目：

| 項目 | 說明 |
| :--- | :--- |
| `deny-drop`、`deny-offhand`、`deny-block-place`、`deny-altar-interact`、`deny-entity-interact` | 全域防護開關（樣本可個別覆蓋） |
| `hopper.allow-move`、`hopper.allow-pickup` | 漏斗搬運、吸取 |
| `drop-protection.prevent-despawn` | 掉落物永不消失 |
| `compatibility.*` | 各相容外掛的放行設定 |
| `feedback.*` | 動作列提示、音效、冷卻 |
| `gui.sounds` | 介面音效 |
| `update-checker.check-on-startup`、`update-checker.notify-on-join` | 版本檢查 |

所有訊息都在 `messages.yml`，變數格式為 `{名稱}`。

---

# Item-lock

Item-lock is an item protection plugin for Paper 1.21+. Register special items (quest items, custom gear, currency) as templates; they can then only be stored in pure storage containers and cannot be processed, modified, or consumed by anvils, grindstones, furnaces, crafting, or other plugins' menus. Matching is optimized: items whose material has no template cost a single table lookup.

## Features

- **Template matching**:
  * Indexed by material — most items skip matching entirely
  * Ignores durability damage and anvil repair cost
  * Filled maps match by map_id; everything else (name, lore, enchantments, model data, PDC) must match
- **Pure storage only**:
  * Chests, trapped chests, double chests, barrels, shulker boxes, ender chests, player inventory
  * Blocks click, shift-click, number-key swap, drag and double-click collect in other menus
- **Chest GUI management**:
  * `/itemlock gui` lists templates; left-click to edit
  * 7 per-template toggles (inherit / force on / force off)
  * Left-click only; delete requires two clicks
- **World interaction**:
  * Offhand guard (F key, `/geyser offhand`, offhand slot) with automatic return
  * Block placement, machine right-click, item frame / armor stand guards
  * Crafting output cleared when a protected item is in the grid
  * Optional drop prevention; dropped items are immune to fire and explosions
- **Hoppers**: separate settings for moving and picking up
- **Compatibility**: Shopkeepers, eShulkerBox, AdvancedSpyInventory, Cmail
- **Low resource use**: minimal main-thread work, batched background file writes, update result cached 10 minutes
- **Config & messages**: all text in `messages.yml` (MiniMessage or & codes); missing keys auto-filled on start and reload

## Commands & Permissions

Main command: `/itemlock`.

| Command | Description | Executor | Permission Node | Default |
| :--- | :--- | :--- | :--- | :--- |
| `/itemlock help` | List permitted commands | In-game / Console | `itemlock.help` | OP |
| `/itemlock gui` | Open template list | Player only | `itemlock.list` | OP |
| `/itemlock list` | GUI for players, text in console | In-game / Console | `itemlock.list` | OP |
| `/itemlock edit <name>` | Open template settings | Player only | `itemlock.edit` | OP |
| `/itemlock add <name> [-f]` | Register main-hand item (`-f` overwrites) | Player only | `itemlock.add` | OP |
| `/itemlock remove <name>` | Delete a template | In-game / Console | `itemlock.remove` | OP |
| `/itemlock check` | Check main-hand item | Player only | `itemlock.check` | OP |
| `/itemlock reload` | Reload config, messages and templates | In-game / Console | `itemlock.reload` | OP |
| `/itemlock version` | Check GitHub for updates | In-game / Console | `itemlock.version` | OP |

> - `itemlock.admin` grants all of the above
> - Players with `itemlock.version` are notified 3 seconds after joining when an update exists
> - Names: letters, digits, `_`, `-`, up to 32 characters

## Usage Examples

### 1. Protect a custom weapon
```bash
/itemlock add dragon_sword
```

### 2. Prevent dropping one template
```bash
/itemlock edit dragon_sword
```
Left-click "禁止丟出" until it shows force on. Saved within 10 seconds (and on shutdown).

### 3. Disable update checks
```bash
update-checker:
  check-on-startup: false
  notify-on-join: false
```

## Configurable Message Keys

All messages live in `messages.yml` (`command.*`, `deny.*`, `update.*`, `gui.*`, `console.*`); placeholders use `{name}`.
