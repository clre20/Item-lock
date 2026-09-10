# Item-lock

Item-lock 是一款針對 Minecraft Paper 及 Purpur 伺服器端（相容 Minecraft 1.20.5 至 1.21.x 最新版本）設計的高安全性物品保護與防竄改系統。本系統捨棄傳統易遺漏的黑名單機制，採用「零信任容器安全網」與「動態遮罩特徵比對引擎」，徹底阻斷原版與第三方插件介面對受保護物品的非法修改、加工、複製與轉換。

---

## 核心架構與儲存設計

### 單檔樣本獨立儲存 (Per-Item YAML)
系統全面採用單檔獨立配置設計，便於伺服器管理員針對個別樣本進行備份、版本控管與跨伺服器轉移：

```text
plugins/Item-lock/
├── config.yml              # 全域參數 (提示字詞、音效開關、漏斗與丟棄原則)
└── templates/              # 保護物品樣本目錄 (每個物品對應單一 YAML 檔案)
    ├── dragon_sword.yml    # 範例：特定裝備保護配置
    └── secret_map.yml      # 範例：核心地圖配置
```

* **原生二進位序列化**：樣本資料透過 Paper 原生 `ItemStack.serializeAsBytes()` 結合 Base64 儲存，完整保留 1.20.5+ 引入的全部 Data Components，包含自定義模型資料（Custom Model Data）、附魔標籤、屬性修飾符與 PersistentDataContainer（PDC）。
* **零磁碟延遲快取 (Zero Disk Latency)**：伺服器開機或執行重新載入時，系統遍歷 `templates/*.yml` 載入記憶體快取。遊戲內的即時安全比對全數於記憶體內完成，主執行緒不產生任何磁碟 I/O。
* **路徑安全性校驗**：樣本名稱嚴格限制僅能使用英數字、底線與連字符（正則規範：`^[a-zA-Z0-9_-]+$`），杜絕目錄遍歷（Directory Traversal）漏洞。
* **覆蓋防呆機制**：若嘗試登錄已存在的樣本名稱，系統會主動攔截並要求附帶強制覆蓋參數 `-f`，防止管理員因暫用命名不慎沖掉既有配置。

---

## 智慧遮罩比對引擎 (Component Matcher)

為避免玩家在正常遊戲行為中因物品磨損或維修而導致保護失效，比對引擎採三層式過濾機制：

1. **第一層：材質初篩 (Material Filter)**
   * 優先比對物品 `Material`。若材質不一致，直接於單一運算週期剔除，大幅降低後續運算負擔。

2. **第二層：動態組件遮罩 (Dynamic Component Masking)**
   * 自動屏蔽正常遊戲過程中會動態變動的組件數值：
     * 排除 `minecraft:damage`：耐久度耗損不影響保護判定。
     * 排除 `minecraft:repair_cost`：鐵砧使用累積的修復懲罰不影響判定。

3. **第三層：特徵指紋比對 (Fingerprint Matching)**
   * **武器與一般裝備**：比對 `custom_model_data`、`custom_name` / `item_name`、`lore`、`enchantments` 映射表及持久化標籤（PersistentDataContainer, PDC），並驗證其餘靜態組件完全一致。
   * **已繪製地圖 (Filled Map)**：精確比對核心組件 `minecraft:map_id`。只要 Map ID 與母本一致即判定命中。

---

## 零信任安全攔截網 (Zero-Trust Security)

系統不依賴第三方插件黑名單，全數採用白名單存儲容器原則：

### 純存儲白名單容器
僅允許以下純存放用途之容器：
* 箱子、雙箱、陷阱箱（`Chest` / `DoubleChest`，限真實世界方塊）
* 木桶（`Barrel`）
* 潛影盒（`Shulker Box`）
* 末影箱（`Ender Chest`）
* 玩家自身背包（`PlayerInventory`）

### 介面操作全管道封鎖
若玩家開啟的頂部介面（Top Inventory）不在白名單內（包含鐵砧、砂輪、鐵匠台、製圖台、熔爐，以及 Slimefun、自定義強化台等虛擬 GUI）：
* **滑鼠游標放入 (Cursor Drop)**：取消事件，禁止將保護物品放入非白名單介面槽位。
* **Shift 快速移入 (Quick Move)**：取消事件，禁止自背包快速傳送進入非白名單介面。
* **數字鍵偷換 (Hotbar Swap 1~9)**：取消事件，禁止透過快捷列快捷鍵進行物品置換。
* **滑鼠拖拉塗抹 (InventoryDragEvent)**：取消事件，禁止以游標拖曳將物品劃入非白名單槽位。

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

## 第三方插件相容支援 (Shopkeepers)

本插件深度相容 [Shopkeepers](https://www.spigotmc.org/resources/shopkeepers.80756/) 村民商店插件，允許受保護物品作為商店交易道具或貨幣使用：

* **交易介面相容 (Trading Interface)**：
  * 開啟 Shopkeepers 村民交易視窗時，允許玩家使用受保護物品作為貨幣支付放入交易槽。
  * 允許玩家從交易產物槽安全領取受保護物品。
* **設定與編輯介面相容 (Editor Interface)**：
  * 店主或管理員於 Shopkeepers 編輯介面（Trade Editor）中，可自由放入、設定或更換包含保護物品在內的交易公式配方。
* **實體與方塊辨識**：
  * 自動辨識 Shopkeeper 實體（含盔甲架型態店主）與方塊（告示牌商店），手持受保護物品右鍵店主時正常開啟商店介面，不予誤攔截。
* 可於 `config.yml` 透過 `compatibility.shopkeepers.allow-trading` 與 `allow-editor` 分別進行獨立開關控制。

---

## 防幽靈物品與操作回饋 (UX & Anti-Desync)

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

### 智慧補全 (Tab Completion)
* 輸入 `/itemlock `：自動補全全部子指令（`add`、`check`、`help`、`list`、`reload`、`remove`）。
* 輸入 `/itemlock remove `：自動補全所有已註冊的範本名稱。
* 輸入 `/itemlock add `：主手手持物品時自動建議物品材質小寫名稱；若輸入名稱已存在，自動提示 `-f` 參數。

---

## 設定檔說明 (`config.yml`)

```yaml
# 是否禁止按 Q 丟棄受保護物品 (true: 禁止丟棄; false: 允許丟棄但實體免疫所有傷害與銷毀)
deny-drop: true

# 是否禁止手持受保護物品右鍵非純容器方塊 (防止觸發祭壇/多方塊機器轉化)
deny-altar-interact: true

# 是否禁止將受保護物品放入展示框或盔甲架
deny-entity-interact: true

# 漏斗物流管制設定
hopper:
  # 是否允許漏斗抽取/傳送受保護物品 (true: 允許漏斗傳輸; false: 禁止漏斗傳輸，預設 false)
  allow-move: false
  # 是否允許地面漏斗吸取掉落的受保護物品 (true: 允許漏斗拾取; false: 禁止漏斗吸取，預設 false)
  allow-pickup: false

# 掉落實體安全防護
drop-protection:
  # 掉落物是否防止自然消失 (false: 5分鐘後正常消失防卡頓; true: 永遠不消失，預設 false)
  prevent-despawn: false

# 第三方插件相容設定
compatibility:
  shopkeepers:
    # 是否允許在 Shopkeepers 交易介面使用受保護物品 (作為交易貨幣或換取獲得，預設 true)
    allow-trading: true
    # 是否允許在 Shopkeepers 設定/編輯介面 (Editor) 設定受保護物品 (預設 true)
    allow-editor: true

# 反饋與體驗設定 (UX & Anti-Desync)
feedback:
  actionbar:
    enabled: true
    message: "<red>⚠ 此物品受特殊保護，無法在此介面中進行修改或加工！</red>"
  sound:
    enabled: true
    type: "ENTITY_VILLAGER_NO"
    volume: 1.0
    pitch: 0.8
  cooldown-ms: 1000

# 系統提示訊息 (支援 MiniMessage 標籤與 Legacy 顏色代碼)
messages:
  prefix: "<gold>[Item-Lock]</gold> "
  no-permission: "<red>你沒有權限執行此指令！</red>"
  player-only: "<red>此指令僅能由玩家在遊戲內執行！</red>"
  reload-success: "<green>設定檔與物品樣本庫已成功重新載入！(共載入 <count> 個保護樣本)</green>"
  add-success: "<green>已成功將主手物品登錄為保護樣本：<gold><id></gold></green>"
  add-already-exists: "<red>保護樣本 <gold><id></gold> 已存在！若要覆蓋請使用：<yellow>/itemlock add <id> -f</yellow></red>"
  add-overwrite-success: "<green>已成功覆蓋並更新保護樣本：<gold><id></gold></green>"
  add-air: "<red>主手不可為空！請手持欲保護的物品。</red>"
  add-invalid-name: "<red>名稱格式錯誤！僅能使用英數字、底線與連字符 (^[a-zA-Z0-9_-]+$)</red>"
  remove-success: "<green>已成功刪除保護樣本：<gold><id></gold></green>"
  remove-not-found: "<red>找不到名為 <gold><id></gold> 的保護樣本！</red>"
  check-protected: "<green>手持物品受特殊保護！匹配樣本：<gold><id></gold></green>"
  check-not-protected: "<yellow>手持物品不受保護。</yellow>"
  list-header: "<gold>===== [ 已登錄的保護物品樣本 (<count>) ] =====</gold>"
  list-item: "<yellow>- <gold><id></gold> (<white><material></white>)</yellow>"
  list-empty: "<gray>目前尚未登錄任何保護物品樣本。</gray>"
```

---

## 構建與部署

### 環境需求
* Java 21 或更高版本
* Maven 3.8 或更高版本
* Paper / Purpur 1.20.5+ 伺服器核心

### 編譯打包
於專案根目錄執行：
```bash
mvn clean package
```
產出的外掛 Jar 檔位於 `target/Item-lock-1.0.0.jar`，將其放置於伺服器 `plugins/` 目錄並重新啟動伺服器即可啟用。