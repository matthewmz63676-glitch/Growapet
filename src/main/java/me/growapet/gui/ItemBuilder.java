/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Material
 *  org.bukkit.enchantments.Enchantment
 *  org.bukkit.inventory.ItemFlag
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.inventory.meta.ItemMeta
 */
package me.growapet.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import me.growapet.utils.Utils;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class ItemBuilder {
    private final ItemStack item;
    private final ItemMeta meta;

    public ItemBuilder(Material material) {
        this.item = new ItemStack(material);
        this.meta = this.item.getItemMeta();
    }

    public ItemBuilder(Material material, int amount) {
        this.item = new ItemStack(material, Math.max(1, amount));
        this.meta = this.item.getItemMeta();
    }

    public ItemBuilder name(String name) {
        this.meta.setDisplayName(Utils.colorize(name));
        return this;
    }

    public ItemBuilder lore(String ... lines) {
        ArrayList<String> lore = new ArrayList<String>();
        for (String line : lines) {
            lore.add(Utils.colorize(line));
        }
        this.meta.setLore(lore);
        return this;
    }

    public ItemBuilder lore(List<String> lines) {
        this.meta.setLore(lines.stream().map(Utils::colorize).collect(Collectors.toList()));
        return this;
    }

    public ItemBuilder glow(boolean glow) {
        if (glow) {
            this.meta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true);
            this.meta.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ENCHANTS});
        }
        return this;
    }

    public ItemStack build() {
        this.item.setItemMeta(this.meta);
        return this.item;
    }
}

