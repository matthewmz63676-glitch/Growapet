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
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
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
        this.meta.displayName(nonItalic(LegacyComponentSerializer.legacySection().deserialize(Utils.colorize(name))));
        return this;
    }

    public ItemBuilder name(Component name) {
        this.meta.displayName(nonItalic(name));
        return this;
    }

    public ItemBuilder lore(String ... lines) {
        ArrayList<String> lore = new ArrayList<String>();
        for (String line : lines) {
            lore.add(Utils.colorize(line));
        }
        this.meta.lore(lore.stream().map(line -> nonItalic(LegacyComponentSerializer.legacySection().deserialize(line))).toList());
        return this;
    }

    public ItemBuilder lore(List<String> lines) {
        this.meta.lore(lines.stream().map(line -> nonItalic(LegacyComponentSerializer.legacySection().deserialize(Utils.colorize(line)))).toList());
        return this;
    }

    public ItemBuilder loreComponents(List<Component> lines) {
        this.meta.lore(lines.stream().map(ItemBuilder::nonItalic).toList());
        return this;
    }

    private static Component nonItalic(Component component) {
        return component.decoration(TextDecoration.ITALIC, TextDecoration.State.FALSE);
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
