/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Material
 *  org.bukkit.entity.Player
 *  org.bukkit.inventory.ItemStack
 */
package me.growapet.gui;

import java.text.NumberFormat;
import java.util.Locale;
import me.growapet.GrowAPet;
import me.growapet.gui.ItemBuilder;
import me.growapet.gui.Menu;
import me.growapet.models.PlayerData;
import me.growapet.store.StoreOffer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class StoreMenu
extends Menu {
    private static final NumberFormat FORMAT = NumberFormat.getIntegerInstance(Locale.US);
    private final GrowAPet plugin;

    public StoreMenu(GrowAPet plugin, Player viewer) {
        super(viewer, "&8Store", 27);
        this.plugin = plugin;
    }

    @Override
    public void build() {
        PlayerData data = this.plugin.getPlayerManager().get(this.viewer);
        if (data == null) {
            return;
        }
        int slot = 11;
        for (StoreOffer offer : StoreOffer.values()) {
            boolean afford = this.plugin.getStoreManager().canAfford(data, offer);
            ItemStack item = new ItemBuilder(offer.getIcon()).name((afford ? "&e" : "&c") + offer.getDisplayName()).lore("&7Reward: " + offer.getRewardLabel(), "", "&7Price: " + (afford ? "&b" : "&c") + FORMAT.format(offer.getCreditPrice()) + " Credits", afford ? "&aClick to purchase!" : "&cNot enough Credits.").build();
            this.setItem(slot, item, e -> {
                if (this.plugin.getStoreManager().purchase(this.viewer, offer)) {
                    this.refresh();
                }
            });
            slot += 2;
        }
        ItemStack balance = new ItemBuilder(Material.PLAYER_HEAD).name("&fYour Balance").lore("&7Credits: &b" + FORMAT.format(data.getCredits())).build();
        this.setItem(4, balance, null);
    }
}

