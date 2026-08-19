package me.growapet.mockbukkit;

import me.growapet.gui.ItemBuilder;
import me.growapet.gui.Menu;
import me.growapet.gui.MenuListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Tag("mockbukkit")
@SuppressWarnings("removal")
final class GuiRenderingMockBukkitTest extends GrowAPetMockTest {
    @Test
    void rendersProtectedNonItalicItemsAndDispatchesOnlySupportedClicks() {
        PlayerMock player = addPlayer("MenuTester");
        ProbeMenu menu = new ProbeMenu(player);
        server.getPluginManager().registerEvents(new MenuListener(null), dependencyStubs.get("WorldGuard"));

        menu.open();

        ItemStack rendered = menu.getInventory().getItem(0);
        assertNotNull(rendered);
        assertEquals(Material.DIAMOND, rendered.getType());
        assertEquals("Title", PlainTextComponentSerializer.plainText().serialize(rendered.getItemMeta().displayName()));
        assertEquals(TextDecoration.State.FALSE, rendered.getItemMeta().displayName().decoration(TextDecoration.ITALIC));
        assertEquals("Lore", PlainTextComponentSerializer.plainText().serialize(rendered.getItemMeta().lore().get(0)));
        assertEquals(TextDecoration.State.FALSE, rendered.getItemMeta().lore().get(0).decoration(TextDecoration.ITALIC));

        var leftClick = player.simulateInventoryClick(player.getOpenInventory(), ClickType.LEFT, 0);
        assertTrueCancelled(leftClick);
        assertEquals(1, menu.handledClicks);

        var shiftClick = player.simulateInventoryClick(player.getOpenInventory(), ClickType.SHIFT_LEFT, 0);
        assertTrueCancelled(shiftClick);
        assertEquals(1, menu.handledClicks);
    }

    private static void assertTrueCancelled(org.bukkit.event.inventory.InventoryClickEvent event) {
        assertEquals(true, event.isCancelled());
    }

    private static final class ProbeMenu extends Menu {
        private int handledClicks;

        private ProbeMenu(PlayerMock viewer) {
            super(viewer, Component.text("Probe"), 9);
        }

        @Override
        public void build() {
            setItem(0, new ItemBuilder(Material.DIAMOND).name("&6Title").lore("&7Lore").build(), event -> handledClicks++);
        }
    }
}
