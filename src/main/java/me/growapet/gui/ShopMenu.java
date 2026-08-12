package me.growapet.gui;

import me.growapet.GrowAPet;
import me.growapet.models.PlayerData;
import me.growapet.shop.GearTier;
import me.growapet.shop.ToolTier;
import me.growapet.shop.ZoneShopProfile;
import me.growapet.utils.Messages;
import me.growapet.zones.Zone;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Secure, zone-branded storefront for the existing gear/tool progression. */
public final class ShopMenu extends Menu {
    private static final NumberFormat NUMBER = NumberFormat.getIntegerInstance(Locale.US);
    private final GrowAPet plugin;

    public ShopMenu(GrowAPet plugin, Player viewer) {
        super(viewer, Messages.parse("<dark_aqua><bold>GEAR SHOP</bold></dark_aqua>"), 54);
        this.plugin = plugin;
    }

    @Override public void build() {
        PlayerData data = plugin.getPlayerManager().get(viewer);
        if (data == null) return;
        fill();
        Zone current = plugin.getZoneManager().getZoneAt(viewer.getLocation());
        String zoneId = current == null ? "spawn" : current.getId();
        ZoneShopProfile profile = ZoneShopProfile.forZone(zoneId);

        setItem(4, new ItemBuilder(profile.icon())
                .name(Messages.parse("<" + profile.style() + "><bold><shop> GEAR SHOP</bold></" + profile.style() + ">",
                        Messages.value("shop", profile.label().toUpperCase(Locale.ROOT))))
                .loreComponents(List.of(
                        Messages.parse("<gray>• Storefront → <white><zone></white></gray>", Messages.value("zone", profile.label())),
                        Messages.parse("<gray>• Progress → <white>shared tier ownership</white></gray>"),
                        Messages.parse("<dark_gray>Your existing gear progression is preserved.</dark_gray>")))
                .build(), null);

        int ownedGear = plugin.getShopManager().getGearTier(data);
        int[] gearSlots = {10, 19, 28, 37};
        for (GearTier tier : GearTier.values()) {
            setItem(gearSlots[tier.getTier() - 1], gearItem(data, tier, ownedGear, profile), event -> {
                if (plugin.getShopManager().purchaseGear(viewer, tier)) refresh();
            });
        }

        int ownedTools = plugin.getShopManager().getToolTier(data);
        int[] toolSlots = {15, 16, 24, 25, 33, 34, 42, 43};
        for (ToolTier tier : ToolTier.values()) {
            setItem(toolSlots[tier.getTier() - 1], toolItem(data, tier, ownedTools, profile), event -> {
                if (plugin.getShopManager().purchaseTool(viewer, tier)) refresh();
            });
        }

        setItem(48, new ItemBuilder(Material.TURTLE_EGG)
                .name(Messages.parse("<green><bold>ZONE EGG SHOP</bold></green>"))
                .loreComponents(List.of(
                        Messages.parse("<gray>• Catalog → <white><zone></white></gray>", Messages.value("zone", profile.label())),
                        Messages.parse("<green>Click → browse eggs</green>"))).build(), event -> new EggShopMenu(plugin, viewer).open());
        setItem(49, new ItemBuilder(Material.NETHER_STAR)
                .name(Messages.parse("<light_purple><bold>MULTIPLIER SHOP</bold></light_purple>"))
                .loreComponents(List.of(
                        Messages.parse("<gray>• Improve permanent player stats</gray>"),
                        Messages.parse("<green>Click → view upgrades</green>"))).glow(true).build(), event -> new MultiplierShopMenu(plugin, viewer).open());
        setItem(50, new ItemBuilder(Material.PLAYER_HEAD)
                .name(Messages.parse("<white><bold>YOUR BALANCE</bold></white>"))
                .loreComponents(List.of(
                        Messages.parse("<gray>• Coins → <yellow><coins></yellow></gray>", Messages.value("coins", NUMBER.format(data.getCoins()))),
                        Messages.parse("<gray>• Gems → <aqua><gems></aqua></gray>", Messages.value("gems", NUMBER.format(data.getGems()))),
                        Messages.parse("<gray>• Gear bonus → <gold>+<bonus>% Coins</gold></gray>", Messages.value("bonus", Math.round(GearTier.cumulativeBonus(ownedGear) * 100))),
                        Messages.parse("<gray>• Tool bonus → <red>+<bonus>% Damage</red></gray>", Messages.value("bonus", Math.round(ToolTier.cumulativeBonus(ownedTools) * 100)))))
                .build(), null);
    }

    private ItemStack gearItem(PlayerData data, GearTier tier, int owned, ZoneShopProfile profile) {
        String branded = profile.label() + " " + tier.getDisplayName();
        if (tier.getTier() <= owned) return new ItemBuilder(tier.getIcon())
                .name(Messages.parse("<green><bold><name></bold></green>", Messages.value("name", branded)))
                .loreComponents(List.of(
                        Messages.parse("<gray>• Tier → <white><tier>/4</white></gray>", Messages.value("tier", tier.getTier())),
                        Messages.parse("<gray>• Coin bonus → <gold>+<bonus>%</gold></gray>", Messages.value("bonus", Math.round(tier.getCoinMultiplierBonus() * 100))),
                        Messages.parse("<green>Owned</green>"))).glow(true).build();
        if (tier.getTier() > owned + 1) return locked(branded, "Buy gear tier " + (owned + 1) + " first");
        boolean affordable = plugin.getShopManager().canAffordGear(data, tier);
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(Messages.parse("<gray>• Tier → <white><tier>/4</white></gray>", Messages.value("tier", tier.getTier())));
        lore.add(Messages.parse("<gray>• Coin bonus → <gold>+<bonus>%</gold></gray>", Messages.value("bonus", Math.round(tier.getCoinMultiplierBonus() * 100))));
        lore.add(Messages.parse(""));
        lore.add(Messages.parse("<gray>• Cost → <yellow><coins> Coins</yellow> <dark_gray>•</dark_gray> <aqua><gems> Gems</aqua></gray>", Messages.value("coins", NUMBER.format(tier.getCoinCost())), Messages.value("gems", NUMBER.format(tier.getGemCost()))));
        lore.add(Messages.parse(affordable ? "<green>Click → purchase</green>" : "<red>Insufficient balance</red>"));
        return new ItemBuilder(tier.getIcon()).name(Messages.parse("<" + profile.style() + "><bold><name></bold></" + profile.style() + ">", Messages.value("name", branded))).loreComponents(lore).glow(tier.isGlow()).build();
    }

    private ItemStack toolItem(PlayerData data, ToolTier tier, int owned, ZoneShopProfile profile) {
        String branded = profile.label() + " " + tier.getDisplayName();
        if (tier.getTier() <= owned) return new ItemBuilder(tier.getIcon())
                .name(Messages.parse("<green><bold><name></bold></green>", Messages.value("name", branded)))
                .loreComponents(List.of(
                        Messages.parse("<gray>• Tier → <white><tier>/8</white></gray>", Messages.value("tier", tier.getTier())),
                        Messages.parse("<gray>• Damage bonus → <red>+<bonus>%</red></gray>", Messages.value("bonus", Math.round(tier.getDamageMultiplierBonus() * 100))),
                        Messages.parse("<green>Owned</green>"))).glow(true).build();
        if (tier.getTier() > owned + 1) return locked(branded, "Buy tool tier " + (owned + 1) + " first");
        boolean affordable = plugin.getShopManager().canAffordTool(data, tier);
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(Messages.parse("<gray>• Tier → <white><tier>/8</white></gray>", Messages.value("tier", tier.getTier())));
        lore.add(Messages.parse("<gray>• Damage bonus → <red>+<bonus>%</red></gray>", Messages.value("bonus", Math.round(tier.getDamageMultiplierBonus() * 100))));
        lore.add(Messages.parse(""));
        lore.add(Messages.parse("<gray>• Cost → <yellow><coins> Coins</yellow> <dark_gray>•</dark_gray> <aqua><gems> Gems</aqua></gray>", Messages.value("coins", NUMBER.format(tier.getCoinCost())), Messages.value("gems", NUMBER.format(tier.getGemCost()))));
        lore.add(Messages.parse(affordable ? "<green>Click → purchase</green>" : "<red>Insufficient balance</red>"));
        return new ItemBuilder(tier.getIcon()).name(Messages.parse("<" + profile.style() + "><bold><name></bold></" + profile.style() + ">", Messages.value("name", branded))).loreComponents(lore).glow(tier.isGlow()).build();
    }

    private static ItemStack locked(String name, String requirement) {
        return new ItemBuilder(Material.GRAY_DYE).name(Messages.parse("<dark_gray><bold><name></bold></dark_gray>", Messages.value("name", name)))
                .loreComponents(List.of(Messages.parse("<gray>• Status → <red>Locked</red></gray>"), Messages.parse("<gray>• Requirement → <white><requirement></white></gray>", Messages.value("requirement", requirement)))).build();
    }

    private void fill() {
        ItemStack pane = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(Messages.parse(" ")).build();
        for (int slot = 0; slot < 54; slot++) setItem(slot, pane, null);
    }
}
