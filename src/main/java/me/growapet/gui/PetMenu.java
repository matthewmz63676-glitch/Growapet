package me.growapet.gui;

import me.growapet.GrowAPet;
import me.growapet.models.Pet;
import me.growapet.utils.Messages;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Protected, UUID-free pet management menu. */
public final class PetMenu extends Menu {
    private static final int[] PET_SLOTS = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43};
    private static final NumberFormat NUMBER = NumberFormat.getIntegerInstance(Locale.US);
    private final GrowAPet plugin;

    public PetMenu(GrowAPet plugin, Player viewer) {
        super(viewer, Messages.parse("<light_purple><bold>PET COLLECTION</bold></light_purple>"), 45);
        this.plugin = plugin;
    }

    @Override public void build() {
        fill();
        List<Pet> pets = plugin.getPetManager().getPets(viewer.getUniqueId());
        for (int i = 0; i < Math.min(pets.size(), PET_SLOTS.length); i++) {
            Pet pet = pets.get(i);
            setItem(PET_SLOTS[i], petItem(pet), event -> {
                boolean equip = event.getClick() == ClickType.LEFT;
                boolean changed = equip ? plugin.getPetManager().equip(viewer.getUniqueId(), pet.getUuid()) : plugin.getPetManager().unequip(viewer.getUniqueId(), pet.getUuid());
                if (changed) Messages.send(viewer, equip ? "<green>Equipped <white><pet></white>.</green>" : "<yellow>Unequipped <white><pet></white>.</yellow>", Messages.value("pet", pet.getDisplayName()));
                else Messages.send(viewer, "<red>That pet could not be updated. Check your plot slots or whether it is locked in a trade.</red>");
                refresh();
            });
        }
        setItem(4, item(Material.NAME_TAG, "<light_purple><bold>YOUR PETS</bold></light_purple>", List.of(
                "<gray>• Collected → <white>" + pets.size() + "</white></gray>",
                "<gray>• Equipped → <white>" + pets.stream().filter(Pet::isEquipped).count() + "</white></gray>",
                "", "<gray>• Left-click → equip</gray>", "<gray>• Right-click → unequip</gray>")), null);
        setItem(36, item(Material.BARRIER, "<red><bold>CLOSE</bold></red>", List.of("<gray>• Click → close the collection</gray>")), event -> viewer.closeInventory());
        setItem(44, item(Material.COMPASS, "<aqua><bold>REFRESH</bold></aqua>", List.of("<gray>• Click → update live pet data</gray>")), event -> refresh());
    }

    private ItemStack petItem(Pet pet) {
        String state = pet.isEquipped() ? "<green>EQUIPPED</green>" : "<yellow>AVAILABLE</yellow>";
        return item(Material.TURTLE_EGG, "<light_purple><bold>" + escape(pet.getDisplayName()) + "</bold></light_purple>", List.of(
                "<gray>• Rarity → <white>" + pet.getRarity().name() + "</white></gray>",
                "<gray>• Size → <white>" + pet.getSize() + " (" + Pet.sizeTierName(pet.getSize()) + ")</white></gray>",
                "<gray>• Level → <white>" + NUMBER.format(pet.getLevel()) + "</white></gray>",
                "<gray>• XP → <white>" + NUMBER.format(pet.getExp()) + "</white></gray>",
                "<gray>• Damage → <white>" + decimal(pet.getDamageMultiplier()) + "x</white></gray>",
                "<gray>• Coins → <yellow>" + decimal(pet.getCoinMultiplier()) + "x</yellow></gray>",
                "<gray>• Gems → <aqua>" + decimal(pet.getGemMultiplier()) + "x</aqua></gray>",
                "<gray>• State → " + state + "</gray>",
                "", pet.isEquipped() ? "<yellow>Right-click → unequip</yellow>" : "<green>Left-click → equip</green>"));
    }

    private void fill() { ItemStack pane = item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()); for (int slot = 0; slot < 45; slot++) setItem(slot, pane, null); }
    private static ItemStack item(Material material, String name, List<String> lore) { return new ItemBuilder(material).name(Messages.parse(name)).loreComponents(lore.stream().map(Messages::parse).toList()).build(); }
    private static String decimal(double value) { return String.format(Locale.US, "%.2f", value); }
    private static String escape(String value) { return value == null ? "Unknown pet" : value.replace("<", "‹").replace(">", "›"); }
}
