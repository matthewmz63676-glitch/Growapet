/*
 * Decompiled with CFR 0.152.
 */
package me.growapet.libs.sqlite;

public interface SQLiteCommitListener {
    public void onCommit();

    public void onRollback();
}

