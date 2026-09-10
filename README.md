# Item-lock

Item-lock 是一款針對 Minecraft Paper 及 Purpur 伺服器端設計的高安全性物品保護與防竄改系統。本系統捨棄傳統易遺漏的黑名單機制，採用「零信任容器安全網」與「動態遮罩特徵比對引擎」，徹底阻斷原版與第三方插件介面對受保護物品的非法修改、加工、複製與轉換。

---

## 核心架構與儲存設計

### 單檔樣本獨立儲存
系統全面採用單檔獨立配置設計，便於伺服器管理員針對個別樣本進行備份、版本控管與跨伺服器轉移：

```text
plugins/Item-lock/
├── config.yml              # 全域參數
└── templates/              # 保護物品樣本目錄
    ├── dragon_sword.yml    # 範例：特定裝備保護配置
    └── secret_map.yml      # 範例：核心地圖配置
```

* **原生二進位序列化**：樣本資料透過 Paper 原生 `ItemStack.serializeAsBytes()` 結合 Base64 儲存，完整保留 1.20.5+ 引入的全部 Data Components，包含自定義模型資料（Custom Model Data）、附魔標籤、屬性修飾符與 PersistentDataContainer（PDC）。
* **零磁碟延遲快取 (Zero Disk Latency)**：伺服器開機或執行重新載入時，系統遍歷 `templates/*.yml` 載入記憶體快取。遊戲內的即時安全比對全數於記憶體內完成，主執行緒不產生任何磁碟 I/O。
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
   * **已繪製地圖 (Filled Map)**：精確比對核心組件 `minecraft:map_id`。只要 Map ID 與母本一致即判定命中。

---

## 零信任安全攔截網

系統不依賴第三方插件黑名單，全數採用白名單存儲容器原則：

### 純存儲白名單容器
僅允許以下純存放用途之容器：
* 箱子、雙箱、陷阱箱（`Chest` / `DoubleChest`，限真實方塊）
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

### 自動化物流管制
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

## 防幽靈物品與操作回饋

* **Tick 延遲同步 (Anti-Ghost Item)**：當違規操作被取消時，排程於下 1 個 Tick 對玩家執行 `player.updateInventory()`，強制對齊伺服器與客戶端數據，杜絕客戶端預測產生的幽靈物品現象。
* **Actionbar 警示**：透過 Adventure API 於玩家螢幕正下方顯示警示訊息。
* **音效打擊反饋**：播放原版拒絕音效（例如 `ENTITY_VILLAGER_NO`）。
* **冷卻防刷 (Cooldown)**：以暫態 UUID 時間戳記錄警示觸發時機，將提示與音效限制為每秒最多觸發一次，避免連點造成干擾。

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

Item-lock is a high-security item protection and anti-tampering system designed for Minecraft Paper and Purpur servers. Departing from traditional, omission-prone blacklists, it employs a **Zero-Trust Container Safety Net** and a **Dynamic Mask Feature Comparison Engine** to thoroughly prevent vanilla mechanics and third-party plugin GUIs from illegally modifying, processing, duplicating, or converting protected items.

---

## Core Architecture & Storage Design

### Single-File Template Storage
The system uses an isolated, single-file configuration layout, making it straightforward for server administrators to back up, version control, and transfer individual templates across servers:

```text
plugins/Item-lock/
├── config.yml              # Global settings
└── templates/              # Protected item templates directory
    ├── dragon_sword.yml    # Example: Specific equipment configuration
    └── secret_map.yml      # Example: Core map configuration
```

* **Native Binary Serialization**: Template data is serialized via Paper's native `ItemStack.serializeAsBytes()` combined with Base64 encoding. This completely preserves all Data Components introduced in 1.20.5+, including Custom Model Data, enchantments, attribute modifiers, and the PersistentDataContainer (PDC).
* **Zero Disk Latency Cache**: On startup or during a reload, the system iterates over `templates/*.yml` and caches all data in memory. Runtime security checks occur strictly in memory, introducing zero disk I/O to the main thread.
* **Path Traversal Sanitization**: Template names strictly accept alphanumeric characters, underscores, and hyphens (regex pattern: `^[a-zA-Z0-9_-]+$`), eliminating directory traversal vulnerabilities.
* **Accidental Overwrite Protection**: Attempting to register an already existing template name triggers an interception, requiring the `-f` flag to force an overwrite and preventing administrators from accidentally replacing existing templates.

---

## Smart Mask Comparison Engine

To prevent normal gameplay actions (such as wear and tear or repairs) from invalidating protection, the comparison engine utilizes a three-tier filtering pipeline:

1. **Layer 1: Material Pre-Filter**
    * Checks the item's `Material` first. Any mismatch is discarded in a single operational cycle, significantly minimizing computational overhead.

2. **Layer 2: Dynamic Component Masking**
    * Automatically ignores components that fluctuate naturally during regular gameplay:
        * Ignores `minecraft:damage`: Durability wear does not affect protection status.
        * Ignores `minecraft:repair_cost`: Accumulated anvil repair penalties do not invalidate matches.

3. **Layer 3: Feature Fingerprinting**
    * **Weapons & General Gear**: Compares `custom_model_data`, `custom_name` / `item_name`, `lore`, enchantment maps, and the PersistentDataContainer (PDC), verifying that all other static components match identically.
    * **Filled Maps**: Precisely checks the core `minecraft:map_id` component. Any map matching the template's Map ID triggers a hit.

---

## Zero-Trust Security Interception Net

Rather than relying on third-party plugin blacklists, the system operates entirely on a strict whitelist of dedicated storage containers:

### Storage-Only Container Whitelist
Only pure storage containers are permitted:
* Chests, Double Chests, Trapped Chests (`Chest` / `DoubleChest`, real tile blocks only)
* Barrels (`Barrel`)
* Shulker Boxes (`Shulker Box`)
* Ender Chests (`Ender Chest`)
* The player's own inventory (`PlayerInventory`)

### Comprehensive GUI Pipeline Lockdown
If the top inventory interface opened by a player is not on the whitelist (including anvils, grindstones, smithing tables, cartography tables, furnaces, or virtual GUIs like Slimefun and custom upgrade stations):
* **Cursor Drop**: Cancels the event; placing protected items into non-whitelisted GUI slots is forbidden.
* **Shift-Click Transfers**: Cancels the event; prevents rapid transfers from the inventory into unauthorized GUIs.
* **Hotbar Swapping**: Cancels the event; prevents swapping items via number keys.
* **Cursor Dragging**: Cancels the event; prevents distributing items across non-whitelisted slots using mouse drag actions.

### Crafting Table & 2x2 Crafting Grid Protection
* Listens to `PrepareItemCraftEvent` and `CraftItemEvent`.
* Checks the 3x3 crafting grid as well as the 2x2 survival crafting grid. If any crafting slot contains a protected item or map, the result slot is wiped to `null` and the crafting event is cancelled, blocking vanilla map copying and all crafting recipes.

### Automated Logistics Control
* Listens to `InventoryMoveItemEvent`: Cancels any transfer involving a protected item if the destination container is not a whitelisted storage block.
* Listens to `InventoryPickupItemEvent`: Prevents hoppers from picking up protected items into automated pipe systems.
* Hopper transfers (`allow-move`) and hopper floor pickups (`allow-pickup`) can be toggled independently in `config.yml`.

### World Interaction & Item Drop Protection
* **Altar Block Conversion Prevention**: Cancels right-click interactions when holding protected items against non-storage blocks, preventing multi-block ritual transformations.
* **Item Frame Protection**: Cancels right-click interactions on item frames or armor stands while holding protected items.
* **Drop Prevention (Q-Key)**: Setting `deny-drop: true` in the configuration blocks item dropping entirely.
* **Item Entity Invulnerability**: If dropping is permitted, dropped entities spawn with invulnerability flags—making them immune to fire, lava, explosions, and entity damage, while preventing mob pickup (e.g., zombies, piglins).
* **Vanilla `/give` Pickup Cleanup**: Accurately tracks dummy item entities (`makeFakeItem`) generated by command blocks and vanilla `/give` animations, cleaning them up once the animation finishes to avoid unpickable leftover entities on the ground.

---

## Anti-Ghost Item Sync & Feedback

* **Tick-Delayed Synchronization (Anti-Ghost Item)**: When an illegal interaction is blocked, the system schedules a `player.updateInventory()` call on the subsequent tick to force-sync the client with the server, eliminating ghost items caused by client-side prediction.
* **Actionbar Alerts**: Displays on-screen warning messages at the bottom center of the player's screen using the Adventure API.
* **Auditory Feedback**: Triggers a vanilla rejection sound (e.g., `ENTITY_VILLAGER_NO`).
* **Cooldown Throttle**: Tracks alert timestamps using transient UUIDs to throttle sound and text feedback to a maximum of once per second, preventing audio-visual spamming.

---

## Commands & Permissions

All commands require the `itemlock.admin` permission node (default: OP Level 2):

| Command | Parameters | Description |
| :--- | :--- | :--- |
| `/itemlock help` | None | Displays the formatted command reference manual. |
| `/itemlock add <name> [-f]` | Template name (Optional `-f`) | Samples the main-hand item, resets durability and repair cost to zero, and saves it. Requires `-f` to overwrite an existing template. |
| `/itemlock remove <name>` | Existing template name | Deletes the physical file and evicts it from the in-memory cache. |
| `/itemlock check` | None | Checks if the held item is protected and displays the matched template name. |
| `/itemlock list` | None | Lists all registered template names, their source files, and item materials. |
| `/itemlock reload` | None | Clears the cache, rescans template files, and reloads global configurations. |