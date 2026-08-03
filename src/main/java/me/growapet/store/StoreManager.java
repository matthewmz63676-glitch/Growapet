/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.configuration.file.FileConfiguration
 *  org.bukkit.entity.Player
 */
package me.growapet.store;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.Random;
import me.growapet.GrowAPet;
import me.growapet.hud.IconResolver;
import me.growapet.models.PlayerData;
import me.growapet.store.StoreOffer;
import me.growapet.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public class StoreManager {
    private static final NumberFormat FORMAT = NumberFormat.getIntegerInstance(Locale.US);
    private final Random random = new Random();
    private final GrowAPet plugin;

    public StoreManager(GrowAPet plugin) {
        this.plugin = plugin;
    }

    public boolean canAfford(PlayerData data, StoreOffer offer) {
        return data.getCredits() >= offer.getCreditPrice();
    }

    public boolean purchase(Player player, StoreOffer offer) {
        PlayerData data = this.plugin.getPlayerManager().get(player);
        if (data == null) {
            return false;
        }
        if (data.getCredits() < offer.getCreditPrice()) {
            player.sendMessage("\u00a7cYou need \u00a7e" + offer.getCreditPrice() + " Credits \u00a7cfor " + offer.getDisplayName() + "!");
            return false;
        }
        data.setCredits(data.getCredits() - offer.getCreditPrice());
        data.setDirty(true);
        switch (offer) {
            case COIN_SURGE: {
                data.addCoins(5000L);
                break;
            }
            case GEM_SURGE: {
                data.addGems(250L);
                break;
            }
            case MYSTERY_CRATE: {
                long bonusCoins = 1000 + this.random.nextInt(9000);
                long bonusGems = 50 + this.random.nextInt(200);
                data.addCoins(bonusCoins);
                data.addGems(bonusGems);
                player.sendMessage("\u00a7dYour Mystery Crate contained \u00a76" + FORMAT.format(bonusCoins) + " coins \u00a7dand \u00a7a" + FORMAT.format(bonusGems) + " gems\u00a7d!");
            }
        }
        player.sendMessage("\u00a7aPurchased \u00a7e" + offer.getDisplayName() + "\u00a7a!");
        this.broadcastPurchase(player, offer);
        return true;
    }

    private void broadcastPurchase(Player player, StoreOffer offer) {
        FileConfiguration hud = this.plugin.getConfigManager().hud();
        String template = "&8[&7\u2620&8] &f" + player.getName() + " &7just purchased &e" + offer.getDisplayName() + " &7for %icon_credit%&b" + FORMAT.format(offer.getCreditPrice()) + " Credits&7!";
        String message = Utils.colorize(IconResolver.apply(hud, template));
        Bukkit.broadcastMessage((String)message);
    }
}

