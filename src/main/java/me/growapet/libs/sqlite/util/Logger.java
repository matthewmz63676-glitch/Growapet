/*
 * Decompiled with CFR 0.152.
 */
package me.growapet.libs.sqlite.util;

public interface Logger {
    public boolean isTraceEnabled();

    public void trace(String var1, Object var2, Object var3);

    public void info(String var1, Object var2, Object var3);

    public void warn(String var1);

    public void error(String var1, Throwable var2);

    public void error(String var1, Object var2, Throwable var3);

    public void error(String var1, Object var2, Object var3, Throwable var4);
}

