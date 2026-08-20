package me.growapet.gui;

import me.growapet.GrowAPet;
import me.growapet.models.PlayerData;
import me.growapet.utils.Messages;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/** Read-only reference-style statistics view for the viewer or an online target. */
public final class StatsMenu extends Menu {
    private static final NumberFormat NUMBER = NumberFormat.getIntegerInstance(Locale.US);
    private final GrowAPet plugin;
    private final Player subject;
    private View view = View.SUMMARY;

    public StatsMenu(GrowAPet plugin, Player viewer) { this(plugin, viewer, viewer); }
    public StatsMenu(GrowAPet plugin, Player viewer, Player subject) {
        super(viewer, Messages.parse(plugin.getConfigManager().stats().getString("title", "<dark_aqua><bold>PLAYER STATISTICS</bold></dark_aqua>")), validSize(plugin.getConfigManager().stats().getInt("size", 54)));
        this.plugin = plugin; this.subject = subject;
    }

    @Override public void build() {
        PlayerData data = plugin.getPlayerManager().get(subject);
        fill();
        if (data == null) { setItem(slot("profile", 22), item(Material.BARRIER, "<red><bold>DATA UNAVAILABLE</bold></red>", List.of("<gray>This player's profile is not loaded.</gray>")), null); return; }
        setItem(slot("profile", 4), totalStats(data), event -> { view = event.getClick() == ClickType.RIGHT ? View.INFO : View.BREAKDOWN; refresh(); });
        setItem(slot("progression", 10), item(Material.EXPERIENCE_BOTTLE, "<aqua><bold>PROGRESSION</bold></aqua>", List.of("<gray>• Player → <white>" + safe(subject.getName()) + "</white></gray>", "<gray>• Level → <white>" + NUMBER.format(data.getLevel()) + "</white></gray>", "<gray>• Experience → <white>" + NUMBER.format(data.getExp()) + " / " + NUMBER.format(PlayerData.expToLevelUp(data.getLevel())) + "</white></gray>", "<gray>• Progress → <white>" + decimal(data.getExpProgress() * 100) + "%</white></gray>")), null);
        setItem(slot("currencies", 12), item(Material.GOLD_INGOT, "<gold><bold>CURRENCIES</bold></gold>", List.of("<gray>• Coins → <yellow>" + NUMBER.format(data.getCoins()) + "</yellow></gray>", "<gray>• Gems → <aqua>" + NUMBER.format(data.getGems()) + "</aqua></gray>", "<gray>• Credits → <light_purple>" + NUMBER.format(data.getCredits()) + "</light_purple></gray>")), null);
        setItem(slot("combat", 14), item(Material.DIAMOND_SWORD, "<red><bold>COMBAT HISTORY</bold></red>", List.of("<gray>• Mob kills → <white>" + NUMBER.format(data.getMobKills()) + "</white></gray>", "<gray>• Boss kills → <white>" + NUMBER.format(data.getBossKills()) + "</white></gray>", "<gray>• Boss damage → <white>" + NUMBER.format(Math.round(data.getBossDamage())) + "</white></gray>")), null);
        setItem(slot("pets", 16), item(Material.TURTLE_EGG, "<light_purple><bold>PET COLLECTION</bold></light_purple>", List.of("<gray>• Eggs hatched → <white>" + NUMBER.format(data.getEggsHatched()) + "</white></gray>", "<gray>• Pets collected → <white>" + NUMBER.format(data.getPetsCollected()) + "</white></gray>", "<gray>• Highest pet level → <white>" + NUMBER.format(data.getHighestPetLevel()) + "</white></gray>", "<gray>• Pet power → <white>" + decimal(data.getPetPower()) + "</white></gray>")), null);
        setItem(slot("playtime", 22), item(Material.CLOCK, "<aqua><bold>PLAYTIME</bold></aqua>", List.of("<white>" + duration(plugin.getPlayerManager().getLivePlaytimeSeconds(data.getUuid())) + "</white>", "", "<gray>Includes this live session.</gray>")), null);
        setItem(slot("activity", 30), item(Material.WRITABLE_BOOK, "<green><bold>ACTIVITY</bold></green>", List.of("<gray>• Quests completed → <white>" + NUMBER.format(data.getQuestsCompleted()) + "</white></gray>", "<gray>• Trades completed → <white>" + NUMBER.format(data.getTrades()) + "</white></gray>", "<gray>• Zones unlocked → <white>" + NUMBER.format(data.getUnlockedZones().size()) + "</white></gray>")), null);
        setItem(slot("detail", 41), detailCard(data), event -> { view = View.SUMMARY; refresh(); });
        setItem(slot("close", getInventory().getSize() - 5), item(Material.BARRIER, "<red><bold>CLOSE</bold></red>", List.of("<gray>Close this menu.</gray>")), event -> viewer.closeInventory());
        setItem(slot("refresh", getInventory().getSize() - 3), item(Material.COMPASS, "<aqua><bold>REFRESH</bold></aqua>", List.of("<gray>Update live statistics.</gray>")), event -> refresh());
    }

    private ItemStack totalStats(PlayerData data) { return item(Material.BOOK, "<green><bold>TOTAL STATS</bold></green>", List.of("<red>❤ Health: <white>" + decimal(attribute(Attribute.MAX_HEALTH, 20)) + "</white></red>", "<red>⚔ Damage: <white>" + decimal(plugin.getMobManager().weaponDamage(subject) * data.getDamageMultiplier()) + "</white></red>", "<gold>✦ Critical: <white>" + decimal(data.getCriticalChance() * 100) + "%</white></gold>", "<aqua>✦ Speed: <white>" + decimal(attribute(Attribute.MOVEMENT_SPEED, .1) * 100) + "</white></aqua>", "<yellow>↗ Fortune: <white>" + decimal((data.getCoinMultiplier() + data.getGemMultiplier()) / 2) + "x</white></yellow>", "<green>→ Efficiency: <white>" + decimal(data.getExpMultiplier()) + "x</white></green>", "", "<green>Left-click → breakdown</green>", "<yellow>Right-click → information</yellow>")); }
    private ItemStack detailCard(PlayerData data) { if (view == View.INFO) return item(Material.KNOWLEDGE_BOOK, "<yellow><bold>STAT INFORMATION</bold></yellow>", List.of("<gray>Health, damage, critical chance, speed, fortune, and efficiency are calculated from this player's live GrowAPet state.</gray>", "", "<yellow>Click → return</yellow>")); if (view == View.BREAKDOWN) return item(Material.MAP, "<green><bold>STAT BREAKDOWN</bold></green>", List.of("<gray>• Base weapon damage → <white>" + decimal(plugin.getMobManager().weaponDamage(subject)) + "</white></gray>", "<gray>• Player damage bonus → <white>" + decimal(data.getDamageMultiplier()) + "x</white></gray>", "<gray>• Critical damage → <white>" + decimal(data.getCriticalDamage()) + "x</white></gray>", "<gray>• Coin multiplier → <white>" + decimal(data.getCoinMultiplier()) + "x</white></gray>", "<gray>• Gem multiplier → <white>" + decimal(data.getGemMultiplier()) + "x</white></gray>", "<gray>• EXP multiplier → <white>" + decimal(data.getExpMultiplier()) + "x</white></gray>", "", "<green>Click → return</green>")); return item(Material.PAPER, "<white><bold>HOW TO USE</bold></white>", List.of("<gray>Left-click the book for a breakdown.</gray>", "<gray>Right-click the book for explanations.</gray>")); }
    private double attribute(Attribute attribute, double fallback) { AttributeInstance instance = subject.getAttribute(attribute); return instance == null ? fallback : instance.getValue(); }
    private void fill() { ItemStack pane = item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()); for (int slot = 0; slot < getInventory().getSize(); slot++) setItem(slot, pane, null); }
    private int slot(String key, int fallback) { int size = getInventory().getSize(); int configured = plugin.getConfigManager().stats().getInt("slots." + key, fallback); if (configured >= 0 && configured < size) return configured; return Math.max(0, Math.min(size - 1, fallback)); }
    private static ItemStack item(Material material, String name, List<String> lore) { return new ItemBuilder(material).name(Messages.parse(name)).loreComponents(lore.stream().map(Messages::parse).toList()).build(); }
    private static int validSize(int size) { return size >= 27 && size <= 54 && size % 9 == 0 ? size : 54; }
    private static String decimal(double value) { return String.format(Locale.US, "%.1f", value); }
    private static String safe(String value) { return value == null ? "" : value.replace("<", "‹").replace(">", "›"); }
    static String duration(long seconds) { long days = seconds / 86400, hours = seconds % 86400 / 3600, minutes = seconds % 3600 / 60; return (days > 0 ? days + "d " : "") + (hours > 0 || days > 0 ? hours + "h " : "") + minutes + "m"; }
    private enum View { SUMMARY, BREAKDOWN, INFO }
}
