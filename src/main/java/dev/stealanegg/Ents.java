package dev.stealanegg;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Helpers that spawn the plugin's non-persistent display / interaction entities. */
public final class Ents {
    private static final Map<UUID, String> LAST = new HashMap<>();

    public static TextDisplay holo(Location l, String text) {
        return l.getWorld().spawn(l, TextDisplay.class, t -> {
            t.text(Util.c(text));
            t.setBillboard(Display.Billboard.CENTER);
            t.setPersistent(false);
            t.setViewRange(0.45f);
            t.setShadowed(true);
            t.setDefaultBackground(false);
            t.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            t.setLineWidth(220);
            Util.tag(t, "holo", "");
        });
    }

    public static void setText(TextDisplay t, String s) {
        if (t == null || !t.isValid()) return;
        if (s.equals(LAST.get(t.getUniqueId()))) return;
        LAST.put(t.getUniqueId(), s);
        t.text(Util.c(s));
    }

    public static Interaction hit(Location l, float w, float h, String type, String data) {
        return l.getWorld().spawn(l, Interaction.class, i -> {
            i.setInteractionWidth(w);
            i.setInteractionHeight(h);
            i.setResponsive(true);
            i.setPersistent(false);
            Util.tag(i, type, data);
        });
    }

    public static Material eggMat(EntityType t) {
        Material m = Material.matchMaterial(t.name() + "_SPAWN_EGG");
        return m == null ? Material.EGG : m;
    }

    public static ItemDisplay item(Location l, Material m, float scale, String type, String data) {
        return l.getWorld().spawn(l, ItemDisplay.class, d -> {
            d.setItemStack(new ItemStack(m));
            d.setBillboard(Display.Billboard.VERTICAL);
            d.setPersistent(false);
            d.setViewRange(0.5f);
            d.setTeleportDuration(2);
            Util.tag(d, type, data);
        });
    }

    public static void transform(ItemDisplay d, float s, float rotZ) {
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(2);
        d.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotateZ(rotZ),
                new Vector3f(s, s, s), new Quaternionf()));
    }
}
