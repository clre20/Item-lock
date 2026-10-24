package clre20.itemLock.security;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.ShulkerBox;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * 純儲存容器白名單：箱子／陷阱箱／大箱子、木桶、潛影盒（方塊）、末影箱、玩家背包。
 * <p>
 * 取 holder 一律用 {@code getHolder(false)}，不建立方塊快照；而且先看介面類型，只有需要時才取 holder。
 */
public final class ContainerWhitelist {

    private ContainerWhitelist() {}

    public static boolean isPureStorage(Inventory inventory) {
        return isPureStorage(inventory, false);
    }

    public static boolean isPureStorage(Inventory inventory, boolean allowVirtualShulkerBox) {
        if (inventory == null) {
            return false;
        }
        InventoryType type = inventory.getType();
        switch (type) {
            case PLAYER, ENDER_CHEST -> {
                return true;
            }
            case CHEST -> {
                InventoryHolder holder = inventory.getHolder(false);
                return holder instanceof Chest || holder instanceof DoubleChest;
            }
            case BARREL -> {
                return inventory.getHolder(false) instanceof Barrel;
            }
            case SHULKER_BOX -> {
                InventoryHolder holder = inventory.getHolder(false);
                if (holder instanceof ShulkerBox) {
                    return true;
                }
                return allowVirtualShulkerBox && !(holder instanceof BlockState) && inventory.getLocation() == null;
            }
            default -> {
                return false;
            }
        }
    }

    /** 沒有方塊的潛影盒介面（手持／隨身開啟）。 */
    public static boolean isVirtualShulkerBox(Inventory inventory) {
        return inventory != null
                && inventory.getType() == InventoryType.SHULKER_BOX
                && !(inventory.getHolder(false) instanceof BlockState)
                && inventory.getLocation() == null;
    }

    /** 右鍵世界方塊時，是否為純儲存容器方塊。 */
    public static boolean isPureStorageBlock(Block block) {
        if (block == null) {
            return false;
        }
        Material m = block.getType();
        return m == Material.CHEST || m == Material.TRAPPED_CHEST || m == Material.BARREL
                || m == Material.ENDER_CHEST || Tag.SHULKER_BOXES.isTagged(m);
    }

    /** 門、活板門、柵欄門、按鈕、拉桿：右鍵只切換狀態。 */
    public static boolean isSimpleInteractableBlock(Block block) {
        if (block == null) {
            return false;
        }
        Material m = block.getType();
        return Tag.DOORS.isTagged(m) || Tag.TRAPDOORS.isTagged(m) || Tag.FENCE_GATES.isTagged(m)
                || Tag.BUTTONS.isTagged(m) || m == Material.LEVER;
    }
}
