package clre20.itemLock.security;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.ShulkerBox;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * 零信任容器安全白名單。
 * 僅允許純存儲容器：
 * - 單箱 / 雙箱 / 陷阱箱 (Chest / DoubleChest)
 * - 木桶 (Barrel)
 * - 潛影盒 (Shulker Box)
 * - 末影箱 (Ender Chest)
 * - 玩家自身背包 (PlayerInventory)
 */
public final class ContainerWhitelist {

    private ContainerWhitelist() {}

    /**
     * 檢查指定庫存是否為純存儲白名單容器。
     */
    public static boolean isPureStorage(Inventory inventory) {
        if (inventory == null) {
            return false;
        }

        InventoryType type = inventory.getType();
        InventoryHolder holder = inventory.getHolder();

        // 玩家自身背包
        if (type == InventoryType.PLAYER) {
            return true;
        }

        // 末影箱
        if (type == InventoryType.ENDER_CHEST) {
            return true;
        }

        // 箱子、陷阱箱、雙箱（需為真實世界方塊，徹底防杜第三方插件虛擬 GUI / 自定義強化台）
        if (type == InventoryType.CHEST) {
            return holder instanceof Chest || holder instanceof DoubleChest;
        }

        // 木桶
        if (type == InventoryType.BARREL) {
            return holder instanceof Barrel;
        }

        // 潛影盒
        if (type == InventoryType.SHULKER_BOX) {
            return holder instanceof ShulkerBox;
        }

        return false;
    }

    /**
     * 檢查指定方塊是否為純存儲容器方塊（用於手持受保護物品右鍵世界方塊交互檢測）。
     */
    public static boolean isPureStorageBlock(Block block) {
        if (block == null) {
            return false;
        }

        Material material = block.getType();

        // 箱子與陷阱箱
        if (material == Material.CHEST || material == Material.TRAPPED_CHEST) {
            return true;
        }

        // 木桶
        if (material == Material.BARREL) {
            return true;
        }

        // 末影箱
        if (material == Material.ENDER_CHEST) {
            return true;
        }

        // 所有顏色的潛影盒
        return Tag.SHULKER_BOXES.isTagged(material);
    }
}
