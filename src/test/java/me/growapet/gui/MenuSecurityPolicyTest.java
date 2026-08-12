package me.growapet.gui;

import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MenuSecurityPolicyTest {
    @Test void onlySimpleTopSlotInteractionsCanDispatch(){
        assertTrue(MenuListener.isDispatchable(ClickType.LEFT,InventoryAction.PICKUP_ALL));
        assertTrue(MenuListener.isDispatchable(ClickType.RIGHT,InventoryAction.PICKUP_HALF));
        assertFalse(MenuListener.isDispatchable(ClickType.SHIFT_LEFT,InventoryAction.MOVE_TO_OTHER_INVENTORY));
        assertFalse(MenuListener.isDispatchable(ClickType.NUMBER_KEY,InventoryAction.HOTBAR_SWAP));
        assertFalse(MenuListener.isDispatchable(ClickType.DOUBLE_CLICK,InventoryAction.COLLECT_TO_CURSOR));
        assertFalse(MenuListener.isDispatchable(ClickType.DROP,InventoryAction.DROP_ALL_SLOT));
    }
}
