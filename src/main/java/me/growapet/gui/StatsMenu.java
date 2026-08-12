package me.growapet.gui;

import me.growapet.GrowAPet;
import me.growapet.models.PlayerData;
import me.growapet.utils.Messages;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/** Live, read-only player statistics with reference-inspired detail and help views. */
public final class StatsMenu extends Menu {
    private static final NumberFormat NUMBER = NumberFormat.getIntegerInstance(Locale.US);
    private final GrowAPet plugin;
    private View view = View.SUMMARY;

    public StatsMenu(GrowAPet plugin, org.bukkit.entity.Player viewer) {
        super(viewer, Messages.parse("<dark_aqua><bold>PLAYER STATISTICS</bold></dark_aqua>"), 45);
        this.plugin = plugin;
    }

    @Override public void build() {
        PlayerData data = plugin.getPlayerManager().get(viewer);
        fill();
        if (data == null) {
            setItem(22, item(Material.BARRIER, "<red><bold>DATA UNAVAILABLE</bold></red>",
                    List.of("<gray>Your profile is still loading.</gray>")), null);
            return;
        }

        setItem(4, totalStats(data), event -> {
            view = event.getClick() == ClickType.RIGHT ? View.INFO : View.BREAKDOWN;
            refresh();
        });
        setItem(10, item(Material.EXPERIENCE_BOTTLE, "<aqua><bold>PROGRESSION</bold></aqua>", List.of(
                "<gray>• Level → <white>" + NUMBER.format(data.getLevel()) + "</white></gray>",
                "<gray>• Experience → <white>" + NUMBER.format(data.getExp()) + " / " + NUMBER.format(PlayerData.expToLevelUp(data.getLevel())) + "</white></gray>",
                "<gray>• Progress → <white>" + decimal(data.getExpProgress() * 100) + "%</white></gray>")), null);
        setItem(12, item(Material.GOLD_INGOT, "<gold><bold>CURRENCIES</bold></gold>", List.of(
                "<gray>• Coins → <yellow>" + NUMBER.format(data.getCoins()) + "</yellow></gray>",
                "<gray>• Gems → <aqua>" + NUMBER.format(data.getGems()) + "</aqua></gray>",
                "<gray>• Credits → <light_purple>" + NUMBER.format(data.getCredits()) + "</light_purple></gray>")), null);
        setItem(14, item(Material.DIAMOND_SWORD, "<red><bold>COMBAT HISTORY</bold></red>", List.of(
                "<gray>• Mob kills → <white>" + NUMBER.format(data.getMobKills()) + "</white></gray>",
                "<gray>• Boss kills → <white>" + NUMBER.format(data.getBossKills()) + "</white></gray>",
                "<gray>• Boss damage → <white>" + NUMBER.format(Math.round(data.getBossDamage())) + "</white></gray>")), null);
        setItem(16, item(Material.TURTLE_EGG, "<light_purple><bold>PET COLLECTION</bold></light_purple>", List.of(
                "<gray>• Eggs hatched → <white>" + NUMBER.format(data.getEggsHatched()) + "</white></gray>",
                "<gray>• Pets collected → <white>" + NUMBER.format(data.getPetsCollected()) + "</white></gray>",
                "<gray>• Highest pet level → <white>" + NUMBER.format(data.getHighestPetLevel()) + "</white></gray>",
                "<gray>• Pet power → <white>" + decimal(data.getPetPower()) + "</white></gray>")), null);
        setItem(22, playtime(data), null);
        setItem(30, item(Material.WRITABLE_BOOK, "<green><bold>ACTIVITY</bold></green>", List.of(
                "<gray>• Quests completed → <white>" + NUMBER.format(data.getQuestsCompleted()) + "</white></gray>",
                "<gray>• Trades completed → <white>" + NUMBER.format(data.getTrades()) + "</white></gray>",
                "<gray>• Zones unlocked → <white>" + NUMBER.format(data.getUnlockedZones().size()) + "</white></gray>")), null);
        setItem(32, detailCard(data), event -> { view = View.SUMMARY; refresh(); });
        setItem(39, item(Material.BARRIER, "<red><bold>CLOSE</bold></red>",
                List.of("<gray>• Click → close this menu</gray>")), event -> viewer.closeInventory());
        setItem(41, item(Material.COMPASS, "<aqua><bold>REFRESH</bold></aqua>",
                List.of("<gray>• Click → update live statistics</gray>")), event -> refresh());
    }

    private ItemStack totalStats(PlayerData data) {
        return item(Material.BOOK, "<green><bold>TOTAL STATS</bold></green>", List.of(
                "<red>❤ Health: <white>" + decimal(attribute(Attribute.MAX_HEALTH, 20)) + "</white></red>",
                "<red>⚔ Damage: <white>" + decimal(plugin.getMobManager().weaponDamage(viewer) * data.getDamageMultiplier()) + "</white></red>",
                "<gold>✦ Critical: <white>" + decimal(data.getCriticalChance() * 100) + "%</white></gold>",
                "<aqua>✦ Speed: <white>" + decimal(attribute(Attribute.MOVEMENT_SPEED, .1) * 100) + "</white></aqua>",
                "<yellow>↗ Fortune: <white>" + decimal((data.getCoinMultiplier() + data.getGemMultiplier()) / 2) + "x</white></yellow>",
                "<green>→ Efficiency: <white>" + decimal(data.getExpMultiplier()) + "x</white></green>",
                "",
                "<green>Left-Click → show breakdown</green>",
                "<yellow>Right-Click → stat information</yellow>"));
    }

    private ItemStack playtime(PlayerData data) {
        return item(Material.CLOCK, "<aqua><bold>PLAYTIME</bold></aqua>", List.of(
                "<white>" + duration(plugin.getPlayerManager().getLivePlaytimeSeconds(data.getUuid())) + "</white>",
                "",
                "<gray>• Includes this live session</gray>"));
    }

    private ItemStack detailCard(PlayerData data) {
        if (view == View.INFO) return item(Material.KNOWLEDGE_BOOK, "<yellow><bold>STAT INFORMATION</bold></yellow>", List.of(
                "<gray>• Health → your maximum hearts</gray>",
                "<gray>• Damage → weapon and player multiplier</gray>",
                "<gray>• Critical → chance for a stronger hit</gray>",
                "<gray>• Speed → live movement attribute</gray>",
                "<gray>• Fortune → average coin and gem bonus</gray>",
                "<gray>• Efficiency → experience reward bonus</gray>",
                "",
                "<yellow>Click → return to summary</yellow>"));
        if (view == View.BREAKDOWN) return item(Material.MAP, "<green><bold>STAT BREAKDOWN</bold></green>", List.of(
                "<gray>• Base weapon damage → <white>" + decimal(plugin.getMobManager().weaponDamage(viewer)) + "</white></gray>",
                "<gray>• Player damage bonus → <white>" + decimal(data.getDamageMultiplier()) + "x</white></gray>",
                "<gray>• Critical damage → <white>" + decimal(data.getCriticalDamage()) + "x</white></gray>",
                "<gray>• Coin multiplier → <white>" + decimal(data.getCoinMultiplier()) + "x</white></gray>",
                "<gray>• Gem multiplier → <white>" + decimal(data.getGemMultiplier()) + "x</white></gray>",
                "<gray>• EXP multiplier → <white>" + decimal(data.getExpMultiplier()) + "x</white></gray>",
                "",
                "<green>Click → return to summary</green>"));
        return item(Material.PAPER, "<white><bold>HOW TO USE</bold></white>", List.of(
                "<gray>• Hover the book for total stats</gray>",
                "<gray>• Left-click → detailed values</gray>",
                "<gray>• Right-click → stat explanations</gray>"));
    }

    private double attribute(Attribute attribute, double fallback) {
        AttributeInstance instance = viewer.getAttribute(attribute);
        return instance == null ? fallback : instance.getValue();
    }

    private void fill() {
        ItemStack pane = item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = 0; slot < 45; slot++) setItem(slot, pane, null);
    }

    private static ItemStack item(Material material, String name, List<String> lore) {
        return new ItemBuilder(material).name(Messages.parse(name))
                .loreComponents(lore.stream().map(Messages::parse).toList()).build();
    }

    private static String decimal(double value) { return String.format(Locale.US, "%.1f", value); }
    static String duration(long seconds) {
        long days = seconds / 86400, hours = seconds % 86400 / 3600, minutes = seconds % 3600 / 60;
        return (days > 0 ? days + "d " : "") + (hours > 0 || days > 0 ? hours + "h " : "") + minutes + "m";
    }
    private enum View { SUMMARY, BREAKDOWN, INFO }
}
