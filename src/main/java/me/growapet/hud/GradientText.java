/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.md_5.bungee.api.ChatColor
 */
package me.growapet.hud;

import java.awt.Color;
import net.md_5.bungee.api.ChatColor;

public final class GradientText {
    private GradientText() {
    }

    public static String apply(String text, String startHex, String endHex, double phase) {
        Color start = Color.decode(startHex);
        Color end = Color.decode(endHex);
        StringBuilder result = new StringBuilder();
        int length = text.length();
        for (int i = 0; i < length; ++i) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                result.append(c);
                continue;
            }
            double t = length <= 1 ? 0.0 : (double)i / (double)(length - 1);
            double shifted = GradientText.pingPong(t + phase);
            result.append(ChatColor.of((Color)GradientText.lerp(start, end, shifted))).append(c);
        }
        return result.toString();
    }

    private static double pingPong(double x) {
        double m = x % 2.0;
        if (m < 0.0) {
            m += 2.0;
        }
        return m > 1.0 ? 2.0 - m : m;
    }

    private static Color lerp(Color a, Color b, double t) {
        int r = (int)Math.round((double)a.getRed() + (double)(b.getRed() - a.getRed()) * t);
        int g = (int)Math.round((double)a.getGreen() + (double)(b.getGreen() - a.getGreen()) * t);
        int bChannel = (int)Math.round((double)a.getBlue() + (double)(b.getBlue() - a.getBlue()) * t);
        return new Color(r, g, bChannel);
    }
}

