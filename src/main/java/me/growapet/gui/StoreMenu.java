package me.growapet.gui;

import me.growapet.GrowAPet;
import me.growapet.models.PlayerData;
import me.growapet.store.StoreOffer;
import me.growapet.utils.Messages;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public final class StoreMenu extends Menu {
    private static final NumberFormat NUMBER = NumberFormat.getIntegerInstance(Locale.US);
    private static final int[] SLOTS = {10,12,14,16,28,30,32,34};
    private final GrowAPet plugin;
    public StoreMenu(GrowAPet plugin, Player viewer) {
        super(viewer, Messages.parse("<light_purple><bold>CREDIT STORE</bold></light_purple>"), 45);
        this.plugin = plugin;
    }

    @Override public void build() {
        fill(); PlayerData data = plugin.getPlayerManager().get(viewer); if (data == null) return;
        setItem(4, item(Material.NETHER_STAR, "<light_purple><bold>YOUR CREDITS</bold></light_purple>", List.of(
                "<gray>• Balance → <white>" + NUMBER.format(data.getCredits()) + "</white></gray>",
                "", "<gray>Earn Credits from daily rewards,</gray>", "<gray>quests, mobs, and bosses.</gray>")), null);
        StoreOffer[] offers = StoreOffer.values();
        for (int index = 0; index < offers.length && index < SLOTS.length; index++) {
            StoreOffer offer = offers[index]; boolean afford = plugin.getStoreManager().canAfford(data, offer);
            setItem(SLOTS[index], item(offer.getIcon(), (afford ? "<yellow>" : "<red>") + "<bold>" + offer.getDisplayName() + "</bold>" + (afford ? "</yellow>" : "</red>"), List.of(
                    "<gray>• Reward → <white>" + offer.getRewardLabel() + "</white></gray>",
                    "<gray>• Price → <light_purple>" + NUMBER.format(offer.getCreditPrice()) + " Credits</light_purple></gray>",
                    "", afford ? "<green>Click → purchase</green>" : "<red>You need more Credits.</red>")),
                    afford ? event -> { if (plugin.getStoreManager().purchase(viewer, offer)) refresh(); } : null);
        }
        setItem(40, item(Material.BARRIER, "<red><bold>CLOSE</bold></red>", List.of("<gray>• Click → close this menu</gray>")), event -> viewer.closeInventory());
    }
    private void fill(){ItemStack pane=item(Material.GRAY_STAINED_GLASS_PANE," ",List.of());for(int i=0;i<45;i++)setItem(i,pane,null);}
    private static ItemStack item(Material material,String name,List<String>lore){return new ItemBuilder(material).name(Messages.parse(name)).loreComponents(lore.stream().map(Messages::parse).toList()).build();}
}
