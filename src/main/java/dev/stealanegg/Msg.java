package dev.stealanegg;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class Msg {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private Msg() {}

    /** '&' colour codes to a Component (italics off so item names look normal). */
    public static Component c(String s) {
        return LEGACY.deserialize(s).decoration(TextDecoration.ITALIC, false);
    }

    public static void send(CommandSender to, String s) {
        to.sendMessage(c(s));
    }

    public static void bar(Player p, String s) {
        p.sendActionBar(c(s));
    }

    /** '&' codes to legacy section codes (for scoreboard entries). */
    public static String sec(String s) {
        return s.replace('&', '§');
    }

    public static String fmt(double v) {
        if (v >= 1e12) return String.format("%.2fT", v / 1e12);
        if (v >= 1e9) return String.format("%.2fB", v / 1e9);
        if (v >= 1e6) return String.format("%.2fM", v / 1e6);
        if (v >= 1e4) return String.format("%.1fK", v / 1e3);
        if (v == Math.floor(v)) return String.valueOf((long) v);
        return String.format("%.1f", v);
    }
}
