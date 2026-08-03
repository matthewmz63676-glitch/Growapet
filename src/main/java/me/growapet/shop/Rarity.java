/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  lombok.Generated
 */
package me.growapet.shop;

import lombok.Generated;

public enum Rarity {
    COMMON("&7Common", "&7"),
    UNCOMMON("&aUncommon", "&a"),
    RARE("&bRare", "&b"),
    EPIC("&5Epic", "&5"),
    LEGENDARY("&6Legendary", "&6"),
    MYTHIC("&dMythic", "&d"),
    DIVINE("&eDivine", "&e");

    private final String label;
    private final String color;

    private Rarity(String label, String color) {
        this.label = label;
        this.color = color;
    }

    @Generated
    public String getLabel() {
        return this.label;
    }

    @Generated
    public String getColor() {
        return this.color;
    }
}

