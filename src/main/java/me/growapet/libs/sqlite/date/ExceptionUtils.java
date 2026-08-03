/*
 * Decompiled with CFR 0.152.
 */
package me.growapet.libs.sqlite.date;

public class ExceptionUtils {
    public static <R> R rethrow(Throwable throwable) {
        return ExceptionUtils.typeErasure(throwable);
    }

    private static <R, T extends Throwable> R typeErasure(Throwable throwable) throws T {
        throw throwable;
    }
}

