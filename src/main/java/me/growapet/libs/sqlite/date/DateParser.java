/*
 * Decompiled with CFR 0.152.
 */
package me.growapet.libs.sqlite.date;

import java.text.ParseException;
import java.text.ParsePosition;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public interface DateParser {
    public Date parse(String var1) throws ParseException;

    public Date parse(String var1, ParsePosition var2);

    public String getPattern();

    public TimeZone getTimeZone();

    public Locale getLocale();

    public Object parseObject(String var1) throws ParseException;

    public Object parseObject(String var1, ParsePosition var2);
}

