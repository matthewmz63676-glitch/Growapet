/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  lombok.Generated
 */
package me.growapet.managers;

import lombok.Generated;

public class PluginManager {
    private static final PluginManager instance = new PluginManager();

    private PluginManager() {
    }

    public void initialize() {
    }

    @Generated
    public static PluginManager getInstance() {
        return instance;
    }
}

