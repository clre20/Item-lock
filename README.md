# Item-lock

---

Item-lock 是一款針對 Minecraft Paper 及 Purpur 伺服器端（1.21+）設計的物品保護插件。透過純存儲容器白名單與動態組件比對，防止伺服器特殊物品（如任務道具、自訂裝備、代幣貨幣）在未經授權的介面中遭到修改、附魔、加工、複製或意外丟失。

### 主要功能

* **動態組件特徵比對**
  * **材質過濾**：以物品材質進行快速初篩；針對已繪製地圖（`FILLED_MAP`）精準比對內部 `map_id`。
  * **動態數值遮罩**：比對時自動屏蔽遊戲常態變動數值（將耐久消耗 `damage` 與鐵砧修復懲罰 `repair_cost` 歸零後再比對），確保物品磨損或維修後仍能正常匹配。
  * **特徵指紋驗證**：比對 Custom Model Data、自訂名稱、Lore、附魔列表與 Persistent Data Container (PDC) 標籤。

* **純存儲容器白名單防護**
  * 僅允許受保護物品存放在以下純儲存用途之容器：
    * 箱子、雙箱、陷阱箱（限世界真實方塊實體）
    * 木桶
    * 潛影盒（已放置之方塊，或設定放行之虛擬潛影盒）
    * 末影箱
    * 玩家自身背包
  * 當玩家開啟非白名單介面（如鐵砧、砂輪、鐵匠台、製圖台、熔爐，以及各類非純存儲的外掛自訂介面）時，攔截滑鼠放置、Shift 快速移入、數字鍵置換與滑鼠拖曳塗抹操作。

* **遊戲內 GUI 管理系統 (v2.1+)**
  * **樣本庫瀏覽介面 (`/itemlock gui` 或 `/itemlock list`)**：54 格分頁箱子介面，清楚陳列所有已註冊的保護物品、材質與個別防護開關摘要。
  * **個別樣本設定介面 (`/itemlock edit <名稱>`)**：27 格箱子介面，管理員可自由針對單一物品調整各項防護開關（三態切換：繼承全域 / 強制開啟 / 強制關閉），並支援取得樣本副本或刪除樣本。

* **副手與世界互動安全**
  * **副手防護**：攔截 F 換手、基岩版 `/geyser offhand` 指令與背包副手槽移入。若因未知途徑殘留於副手，系統將在切換手持或關閉介面時自動移回主背包。
  * **方塊防放**：禁止玩家將受保護的方塊類物品作為方塊放置到世界上。
  * **合成防拓印**：當 3x3 工作台或 2x2 隨身合成矩陣中包含受保護物品時，自動清空合成產物並取消事件，防止地圖複製或配方加工。
  * **非容器方塊交互防護**：手持受保護物品右鍵非純存儲方塊（如祭壇、大鍋、附魔台）時取消交互。門、活板門、柵欄門、按鈕、拉桿等開關類方塊仍可正常互動，但不會使用手持物品（受保護的方塊類物品無法藉潛行右鍵放置）。
  * **實體交互防護**：禁止將受保護物品放入展示框、發光展示框或裝備在盔甲架上。
  * **丟棄防護**：可選開啟禁止按 Q 丟棄。掉落實體生成時自動賦予火焰、岩漿、爆炸傷害免疫，並禁止生物拾取。

* **自動化物流管制**
  * 支援獨立設定是否允許漏斗或漏斗礦車自容器中抽取並傳輸受保護物品，以及地面漏斗是否可吸取掉落的受保護物品。

* **第三方外掛相容支援**
  * **Shopkeepers**：原生支援村民商店，允許受保護物品作為貨幣或商品交易，並支援在店主設定介面中進行配方配置。
  * **eShulkerBox**：支援在手持或隨身開啟的潛影盒介面中安全存取受保護物品。
  * **AdvancedSpyInventory**：支援在查看玩家背包（`/invsee`）與末影箱（`/enderchest`）介面中存取受保護物品；生物背包介面預設不放行，可於設定檔開啟。

* **防幽靈物品與操作反饋**
  * 攔截操作時於螢幕正下方顯示 Actionbar 提示並播放拒絕音效（具備冷卻防刷保護）。
  * 攔截後於下 1 個 Tick 自動排程執行 `player.updateInventory()`，強制對齊伺服器與客戶端數據，消除幽靈物品。

---

### 指令與權限說明

權限節點：`itemlock.admin`（預設為 OP Level 2）

| 指令語法 | 參數說明 | 功能說明 |
| :--- | :--- | :--- |
| `/itemlock help` | 無 | 顯示指令說明手冊。 |
| `/itemlock gui` | 無 | 開啟 54 格保護物品樣本庫管理箱介面。 |
| `/itemlock list` | 無 | 查看所有已註冊的保護樣本（玩家執行將自動開啟 GUI；主控台顯示文字列表）。 |
| `/itemlock edit <名稱>` | 樣本名稱 | 開啟指定樣本的 27 格個別屬性設定介面。 |
| `/itemlock add <名稱> [-f]` | 樣本名稱 (可選 `-f`) | 將主手手持物品註冊為保護樣本（耐久與修復懲罰自動正規化）。已存在時需加上 `-f` 強制覆蓋。 |
| `/itemlock remove <名稱>` | 已存在的樣本名稱 | 自磁碟檔案與記憶體快取中刪除指定樣本。 |
| `/itemlock check` | 無 | 檢測主手物品是否受保護，並顯示所匹配的樣本名稱。 |
| `/itemlock reload` | 無 | 重新載入全域設定檔與所有樣本檔案至記憶體快取。 |

---

## English

Item-lock is an item protection plugin for Paper and Purpur Minecraft servers (1.21+). It protects designated items (such as unique quest items, RPG gear, or custom currency) from accidental loss, unauthorized modification, crafting, and grinding by restricting interactions to whitelisted storage containers.

### Key Features

* **Component-Aware Item Matching**
  * **Material Filter**: Fast preliminary check by item material. Filled maps (`FILLED_MAP`) are matched by their internal `map_id`.
  * **Dynamic Masking**: Normalizes gameplay wear components (resets `minecraft:damage` and anvil `minecraft:repair_cost` during comparison), ensuring used or repaired items remain protected.
  * **Fingerprint Verification**: Compares Custom Model Data, display name, lore, enchantments, and Persistent Data Container (PDC) tags.

* **Storage Whitelist Container Safety**
  * Restricts protected item transfers strictly to pure storage blocks:
    * Chests, Trapped Chests, and Double Chests (physical world blocks only)
    * Barrels
    * Shulker Boxes (placed physical blocks, or whitelisted virtual menus)
    * Ender Chests
    * Player Inventory
  * Prevents depositing protected items into non-storage interfaces (such as anvils, grindstones, smithing tables, cartography tables, furnaces, or unauthorized custom plugin menus) via clicking, shift-clicking, hotbar number keys, or drag-painting.

* **In-Game GUI Management (v2.1+)**
  * **Template Browser (`/itemlock gui` or `/itemlock list`)**: A 54-slot paginated chest GUI displaying all registered templates, their materials, and active protection toggles.
  * **Per-Item Settings (`/itemlock edit <id>`)**: A 27-slot chest GUI allowing administrators to adjust individual protection settings using tri-state toggles (Inherit Global / Enforce True / Enforce False), acquire a duplicate sample item, or safely delete a template.

* **Offhand & World Interaction Safeguards**
  * **Offhand Restriction**: Prevents moving protected items to the offhand slot via keybinds (F key), Bedrock `/geyser offhand` commands, or shift-clicking. Automatically returns residual offhand items to the main inventory.
  * **Block Placement Guard**: Prevents players from placing protected block-type items into the physical world.
  * **Crafting Protection**: Clears the output and cancels crafting if a protected item is placed inside a 3x3 crafting table or 2x2 player inventory grid (prevents vanilla map cloning and recipes).
  * **Non-Storage Block Interaction**: Cancels right-click interactions on non-storage blocks (e.g. cauldrons, enchanting tables) while holding protected items. Doors, trapdoors, fence gates, buttons, and levers can still be toggled, but the held item is not used (protected block items cannot be sneak-placed onto them).
  * **Entity Interaction**: Prevents inserting protected items into item frames, glowing item frames, or armor stands.
  * **Drop Protection**: Optional toggle to prevent dropping via the Q key. Dropped entities receive immunity against fire, lava, explosions, and mob pickup.

* **Logistics & Hoppers**
  * Configurable controls for hoppers and hopper minecarts to allow or prevent transferring (`hopper.allow-move`) and picking up (`hopper.allow-pickup`) protected items.

* **Third-Party Compatibility**
  * **Shopkeepers**: Built-in support allowing protected items to be used as trading currency or items, and allowing configuration in the Shopkeepers editor menu.
  * **eShulkerBox**: Allows storing and retrieving protected items within handheld or virtual shulker box interfaces.
  * **AdvancedSpyInventory**: Allows moving protected items in the player inventory (`/invsee`) and ender chest (`/enderchest`) viewers. Entity inventory viewers are blocked by default and can be enabled in the config.

* **Player Feedback & Anti-Desync**
  * Displays Actionbar notifications and plays refusal sounds when an illegal action is blocked.
  * Automatically schedules `player.updateInventory()` on the next tick to prevent client-side ghost item desynchronization.

### Commands & Permissions

Permission Node: `itemlock.admin` (Default: OP Level 2)

| Command | Arguments | Description |
| :--- | :--- | :--- |
| `/itemlock help` | None | Displays the command help menu. |
| `/itemlock gui` | None | Opens the 54-slot template browser GUI. |
| `/itemlock list` | None | Views all registered templates (opens GUI for players; prints text in console). |
| `/itemlock edit <name>` | Template Name | Opens the 27-slot individual setting editor GUI for the specified template. |
| `/itemlock add <name> [-f]` | Template Name (Optional `-f`) | Registers the item held in the main hand as a template. Durability and repair costs are automatically normalized. Use `-f` to overwrite. |
| `/itemlock remove <name>` | Template Name | Removes a registered template from disk and memory. |
| `/itemlock check` | None | Checks whether the item in the main hand is protected and displays its template ID. |
| `/itemlock reload` | None | Reloads `config.yml` and all template YAML files into memory. |