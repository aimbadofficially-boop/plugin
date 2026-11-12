package dev.stealanegg;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;

import java.util.Locale;

public final class Util {
    public static NamespacedKey K_TYPE, K_DATA;
    private static final String[] SUF = {"", "K", "M", "B", "T", "Qa", "Qi", "Sx", "Sp", "Oc", "No", "Dc"};

    public static void init(StealAnEgg p) {
        K_TYPE = new NamespacedKey(p, "sae_type");
        K_DATA = new NamespacedKey(p, "sae_data");
    }

    public static String fmt(double v) {
        if (v < 1000) return v == Math.floor(v) ? String.valueOf((long) v) : String.format(Locale.US, "%.1f", v);
        int i = 0;
        while (v >= 1000 && i < SUF.length - 1) { v /= 1000; i++; }
        String s = v >= 100 ? String.format(Locale.US, "%.0f", v)
                : v >= 10 ? String.format(Locale.US, "%.1f", v) : String.format(Locale.US, "%.2f", v);
        return s + SUF[i];
    }

    public static double parse(String s) {
        s = s.trim().toLowerCase(Locale.ROOT);
        String[] sf = {"qi", "qa", "t", "b", "m", "k"};
        double[] mul = {1e18, 1e15, 1e12, 1e9, 1e6, 1e3};
        for (int i = 0; i < sf.length; i++)
            if (s.endsWith(sf[i])) return Double.parseDouble(s.substring(0, s.length() - sf[i].length())) * mul[i];
        return Double.parseDouble(s);
    }

    public static String dur(long sec) {
        if (sec < 0) sec = 0;
        if (sec >= 3600) return (sec / 3600) + "h " + String.format("%02d", (sec % 3600) / 60) + "m";
        return (sec / 60) + ":" + String.format("%02d", sec % 60);
    }

    public static String pretty(String s) {
        StringBuilder b = new StringBuilder();
        for (String part : s.toLowerCase(Locale.ROOT).split("_")) {
            if (part.isEmpty()) continue;
            if (b.length() > 0) b.append(' ');
            b.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return b.toString();
    }

    public static Component c(String s) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(s).decoration(TextDecoration.ITALIC, false);
    }

    public static void tag(Entity e, String type, String data) {
        e.getPersistentDataContainer().set(K_TYPE, PersistentDataType.STRING, type);
        e.getPersistentDataContainer().set(K_DATA, PersistentDataType.STRING, data == null ? "" : data);
    }

    public static String type(Entity e) {
        return e.getPersistentDataContainer().get(K_TYPE, PersistentDataType.STRING);
    }

    public static String data(Entity e) {
        String s = e.getPersistentDataContainer().get(K_DATA, PersistentDataType.STRING);
        return s == null ? "" : s;
    }
}
