# Item-lock

Item-lock 是一款針對 Minecraft Paper 及 Purpur 伺服器端（相容 Minecraft 1.20.5 至 1.21.x 最新版本）設計的高安全性物品保護與防竄改系統。本系統捨棄傳統易遺漏的黑名單機制，採用「零信任容器安全網」與「動態遮罩特徵比對引擎」，徹底阻斷原版與第三方插件介面對受保護物品的非法修改、加工、複製與轉換。

---

## 核心架構與儲存設計

### 單檔樣本獨立儲存
系統全面採用單檔獨立配置設計，便於伺服器管理員針對個別樣本進行備份、版本控管與跨伺服器轉移：

```text
plugins/Item-lock/
├── config.yml              # 全域參數 (提示字詞、音效開關、漏斗與丟棄原則)
└── templates/              # 保護物品樣本目錄 (每個物品對應單一 YAML 檔案)
    ├── dragon_sword.yml    # 範例：特定裝備保護配置
    └── secret_map.yml      # 範例：核心地圖配置
```

* **原生二進位序列化**：樣本資料透過 Paper 原生 `ItemStack.serializeAsBytes()` 結合 Base64 儲存，完整保留 1.20.5+ 引入的全部 Data Components，包含自定義模型資料（Custom Model Data）、附魔標籤、屬性修飾符與 PersistentDataContainer（PDC）。
* **零磁碟延遲快取**：伺服器開機或執行重新載入時，系統遍歷 `templates/*.yml` 載入記憶體快取。遊戲內的即時安全比對全數於記憶體內完成，主執行緒不產生任何磁碟 I/O。
* **路徑安全性校驗**：樣本名稱嚴格限制僅能使用英數字、底線與連字符（正則規範：`^[a-zA-Z0-9_-]+$`），杜絕目錄遍歷（Directory Traversal）漏洞。
* **覆蓋防呆機制**：若嘗試登錄已存在的樣本名稱，系統會主動攔截並要求附帶強制覆蓋參數 `-f`，防止管理員因暫用命名不慎沖掉既有配置。

---

## 智慧遮罩比對引擎

為避免玩家在正常遊戲行為中因物品磨損或維修而導致保護失效，比對引擎採三層式過濾機制：

1. **第一層：材質初篩**
   * 優先比對物品 `Material`。若材質不一致，直接於單一運算週期剔除，大幅降低後續運算負擔。

2. **第二層：動態組件遮罩**
   * 自動屏蔽正常遊戲過程中會動態變動的組件數值：
     * 排除 `minecraft:damage`：耐久度耗損不影響保護判定。
     * 排除 `minecraft:repair_cost`：鐵砧使用累積的修復懲罰不影響判定。

3. **第三層：特徵指紋比對**
   * **武器與一般裝備**：比對 `custom_model_data`、`custom_name` / `item_name`、`lore`、`enchantments` 映射表及持久化標籤（PersistentDataContainer, PDC），並驗證其餘靜態組件完全一致。
   * **已繪製地圖**：精確比對核心組件 `minecraft:map_id`。只要 Map ID 與母本一致即判定命中。

---

## 零信任安全攔截網

系統不依賴第三方插件黑名單，全數採用白名單存儲容器原則：

### 純存儲白名單容器
僅允許以下純存放用途之容器：
* 箱子、雙箱、陷阱箱（`Chest` / `DoubleChest`，限真實世界方塊）
* 木桶（`Barrel`）
* 潛影盒（`Shulker Box`）
* 末影箱（`Ender Chest`）
* 玩家自身背包（`PlayerInventory`）

### 介面操作全管道封鎖
若玩家開啟的頂部介面不在白名單內（包含鐵砧、砂輪、鐵匠台、製圖台、熔爐，以及 Slimefun、自定義強化台等虛擬 GUI）：
* **滑鼠游標放入**：取消事件，禁止將保護物品放入非白名單介面槽位。
* **Shift 快速移入**：取消事件，禁止自背包快速傳送進入非白名單介面。
* **數字鍵偷換**：取消事件，禁止透過快捷列快捷鍵進行物品置換。
* **滑鼠拖拉塗抹**：取消事件，禁止以游標拖曳將物品劃入非白名單槽位。

### 工作台與隨身 2x2 合成防拓印
* 監聽 `PrepareItemCraftEvent` 與 `CraftItemEvent`。
* 檢查 3x3 工作台以及玩家隨身背包的 2x2 合成矩陣。若合成槽內包含任何受保護的物品或地圖，直接將合成產物清空為 null 並取消合成事件，封死原版地圖拓印與所有配方合成。

### 自動化物流管制 (Hopper & Logistics)
* 監聽 `InventoryMoveItemEvent`：若移動對象為受保護物品，且目標容器非純存儲白名單容器，一律取消移動。
* 監聽 `InventoryPickupItemEvent`：防止地面漏斗將受保護物品吸入自動化管線。
* 可透過 `config.yml` 獨立開關是否允許漏斗移動 (`allow-move`) 與地面吸取 (`allow-pickup`)。

### 世界互動與掉落防護
* **防祭壇方塊轉換**：手持受保護物品右鍵非純容器方塊時取消交互，防止觸發多方塊機器或儀式轉化。
* **展示框防塞入**：手持受保護物品右鍵點擊物品展示框或盔甲架時取消交互。
* **防 Q 丟棄**：可於設定檔啟用 `deny-drop: true` 禁止丟出。
* **掉落物免疫機制**：若設定允許丟出，掉落實體生成時自動賦予無敵屬性，免疫火焰、熔岩、爆炸與實體受傷；禁止生物（如殭屍、豬靈）拾取。
* **原版 `/give` 動畫假物品清理**：精準識別指令方塊與原版 `/give` 產生的拾取動畫假實體（`makeFakeItem`），於動畫結束時自動清理，防止在地面產生無法拾取的殘留物。

---

## 第三方插件相容支援

本插件深度相容 [Shopkeepers](https://www.spigotmc.org/resources/shopkeepers.80756/) 村民商店插件，允許受保護物品作為商店交易道具或貨幣使用：

* **交易介面相容**：
  * 開啟 Shopkeepers 村民交易視窗時，允許玩家使用受保護物品作為貨幣支付放入交易槽。
  * 允許玩家從交易產物槽安全領取受保護物品。
* **設定與編輯介面相容**：
  * 店主或管理員於 Shopkeepers 編輯介面中，可自由放入、設定或更換包含保護物品在內的交易公式配方。
* **實體與方塊辨識**：
  * 自動辨識 Shopkeeper 實體（含盔甲架型態店主）與方塊（告示牌商店），手持受保護物品右鍵店主時正常開啟商店介面，不予誤攔截。
* 可於 `config.yml` 透過 `compatibility.shopkeepers.allow-trading` 與 `allow-editor` 分別進行獨立開關控制。

---

## 防幽靈物品與操作回饋

* **Tick 延遲同步**：當違規操作被取消時，排程於下 1 個 Tick 對玩家執行 `player.updateInventory()`，強制對齊伺服器與客戶端數據，杜絕客戶端預測產生的幽靈物品現象。
* **Actionbar 警示**：透過 Adventure API 於玩家螢幕正下方顯示警示訊息。
* **音效打擊反饋**：播放原版拒絕音效（例如 `ENTITY_VILLAGER_NO`）。
* **冷卻防刷 **：以暫態 UUID 時間戳記錄警示觸發時機，將提示與音效限制為每秒最多觸發一次，避免連點造成干擾。

---

## 指令與權限說明

全指令皆要求權限節點 `itemlock.admin`（預設為 OP Level 2）：

| 指令語法 | 參數說明 | 說明 |
| :--- | :--- | :--- |
| `/itemlock help` | 無 | 顯示結構化指令說明手冊。 |
| `/itemlock add <名稱> [-f]` | 樣本名稱 (可選 `-f`) | 採樣主手物品並將耐久與修復成本歸零後儲存。若名稱已存在需附帶 `-f` 強制覆蓋。 |
| `/itemlock remove <名稱>` | 已存在的樣本名稱 | 刪除實體檔案並從記憶體快取中移除。 |
| `/itemlock check` | 無 | 檢測當前手持物品是否受保護，並顯示匹配的樣本名稱。 |
| `/itemlock list` | 無 | 列出所有已登錄的樣本名稱、對應檔案與物品材質。 |
| `/itemlock reload` | 無 | 清空快取、重新掃描並載入樣本檔案與全域設定。 |

# Item-lock

Item-lock is a high-security item protection and anti-tamper system designed for Minecraft Paper and Purpur servers (compatible with Minecraft 1.20.5 through the latest 1.21.x releases). Bypassing error-prone blacklists, it leverages a "Zero-Trust Container Safety Net" alongside a "Dynamic Mask Feature Comparison Engine" to completely block illicit modifications, processing, duplications, and conversions across vanilla mechanics and third-party plugin interfaces.

---

## Core Architecture & Storage Design

### Individual Template Storage
The system uses a standalone file layout per template, streamlining backups, version control, and cross-server transfers:

```text
plugins/Item-lock/
├── config.yml              # Global settings (messages, sound toggles, hopper & drop rules)
└── templates/              # Protected item templates (one YAML per template)
    ├── dragon_sword.yml    # Example: custom gear protection
    └── secret_map.yml      # Example: core map configuration
```

* **Native Binary Serialization**: Template data is stored using Paper's native `ItemStack.serializeAsBytes()` combined with Base64, preserving all Data Components introduced in 1.20.5+ (Custom Model Data, enchantments, attribute modifiers, and PersistentDataContainers).
* **Zero Disk-I/O In-Game**: Templates in `templates/*.yml` load into memory at boot and during reload. All real-time checks run strictly in-memory without blocking the main server thread with disk operations.
* **Path Traversal Sanitization**: Template names strictly accept alphanumeric characters, underscores, and hyphens (`^[a-zA-Z0-9_-]+$`) to prevent directory traversal exploits.
* **Overwrite Guard**: Attempting to register an existing template name is blocked unless explicitly overridden with the `-f` flag.

---

## Dynamic Mask Comparison Engine

To prevent normal gameplay wear, tears, and maintenance from voiding item protection, the engine uses a 3-layer filter:

1. **Layer 1: Material Check**
   * Validates the item's `Material`. Mismatches are rejected in a single cycle, eliminating unneeded overhead.

2. **Layer 2: Dynamic Component Masking**
   * Masks out fluid gameplay components during checks:
     * Ignores `minecraft:damage`: Durability loss will not bypass protection.
     * Ignores `minecraft:repair_cost`: Accumulated anvil repair penalties will not bypass protection.

3. **Layer 3: Fingerprint Matching**
   * **Weapons & Standard Equipment**: Matches `custom_model_data`, `custom_name` / `item_name`, `lore`, `enchantments`, and the PersistentDataContainer (PDC), verifying that all other static components match precisely.
   * **Filled Maps**: Matches the core `minecraft:map_id` component. Matches are triggered if the Map ID aligns with the master template.

---

## Zero-Trust Security Interception

The plugin discards fragile third-party blocklists, enforcing a strict whitelist for storage containers.

### Storage Whitelist Containers
Access is restricted strictly to pure storage blocks:
* Chests, Double Chests, Trapped Chests (`Chest` / `DoubleChest`, physical world blocks only)
* Barrels (`Barrel`)
* Shulker Boxes (`Shulker Box`)
* Ender Chests (`Ender Chest`)
* Player inventory (`PlayerInventory`)

### Comprehensive GUI Pipeline Blockade
If an opened top inventory falls outside the whitelist (e.g., anvils, grindstones, smithing tables, cartography tables, furnaces, or virtual menus such as Slimefun workbenches):
* **Cursor Placement**: Cancels events moving protected items into unauthorized slots.
* **Shift-Click Transfers**: Blocks rapid transfers from the player inventory into forbidden interfaces.
* **Hotbar Swapping**: Blocks number-key slot swaps.
* **Mouse Dragging / Painting**: Prevents painting or distributing items across non-whitelisted slots.

### Crafting Grid & Map Cloning Prevention
* Hooks into `PrepareItemCraftEvent` and `CraftItemEvent`.
* Scans both 3x3 crafting tables and 2x2 player inventory grids. If any protected item or map enters the matrix, the recipe output is cleared to `null` and the event is cancelled, shutting down vanilla map cloning and crafting exploits.

### Hopper & Automation Logistics
* Listens to `InventoryMoveItemEvent`: Cancels item transfers if the item is protected and the destination is not an approved storage container.
* Listens to `InventoryPickupItemEvent`: Prevents hoppers from vacuuming protected items off the floor.
* Configurable via `config.yml` with toggles for hopper transfers (`allow-move`) and hopper pickups (`allow-pickup`).

### World Interactions & Drop Protections
* **Altar Block Conversion Prevention**: Cancels right-click interactions on non-storage blocks when holding protected items, preventing multi-block machine triggers or ritual sacrifices.
* **Item Frame Protection**: Cancels right-clicks targeting item frames and armor stands while holding protected items.
* **Drop Prevention**: Dropping items via the drop key (Q) can be locked down via `deny-drop: true`.
* **Entity Immunity**: If item drops are permitted, spawned drop entities receive invulnerability against fire, lava, explosions, and general damage, and cannot be picked up by mobs (e.g., zombies, piglins).
* **Vanilla `/give` Entity Cleanup**: Detects pickup animation items (`makeFakeItem`) produced by command blocks or `/give`, clearing them right as animations finish to prevent uncollectible residue.

---

## Third-Party Compatibility

Item-lock features native integration with the [Shopkeepers](https://www.spigotmc.org/resources/shopkeepers.80756/) plugin, allowing protected items to serve as trade stock or currency:

* **Trading Windows**:
  * Allows protected items in trading input slots as valid currency.
  * Allows players to collect protected items safely from trade output slots.
* **Editor Interface**:
  * Shopkeepers and server staff can insert, set, or swap trade recipes using protected items inside the Shopkeepers editor GUI.
* **Target Recognition**:
  * Detects Shopkeeper entities (including armor stands) and blocks (sign shops), allowing shop menus to open without false-positive interaction cancellations.
* Configurable independently via `compatibility.shopkeepers.allow-trading` and `allow-editor` in `config.yml`.

---

## Anti-Ghost Items & Player Feedback

* **Tick-Delayed Synchronization**: When an illegal move is cancelled, `player.updateInventory()` is scheduled on the subsequent tick to reconcile the server state with the client, eliminating client-side desync and ghost items.
* **Actionbar Alerts**: Displays clean, real-time alert notifications via the Adventure API.
* **Audio Feedback**: Triggers vanilla refusal audio (e.g., `ENTITY_VILLAGER_NO`).
* **Cooldown Protection**: Tracks alert events via transient UUID timestamps, rate-limiting warnings and sound triggers to once per second to mitigate spam.

---

## Commands & Permissions

All commands require the `itemlock.admin` permission node (defaults to OP Level 2):

| Command | Arguments | Description |
| :--- | :--- | :--- |
| `/itemlock help` | None | Displays the formatted command manual. |
| `/itemlock add <name> [-f]` | Template name (optional `-f`) | Samples the main-hand item, resets durability and repair costs to zero, and saves it. Requires `-f` to overwrite existing templates. |
| `/itemlock remove <name>` | Existing template name | Deletes the template file and unloads it from memory. |
| `/itemlock check` | None | Inspects the main-hand item to verify if it is protected and prints the matching template name. |
| `/itemlock list` | None | Lists all loaded template names, files, and corresponding materials. |
| `/itemlock reload` | None | Clears the cache and reloads all template files and global settings. |
