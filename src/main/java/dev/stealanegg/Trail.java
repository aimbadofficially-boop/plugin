package dev.stealanegg;

import org.bukkit.Material;
import org.bukkit.Particle;

public enum Trail {
    NONE("No trail", Material.BARRIER, 0, 0.00, null),
    CLOUD("Cloud Trail", Material.WHITE_WOOL, 1000, 0.05, Particle.CLOUD),
    FLAME("Flame Trail", Material.BLAZE_POWDER, 5000, 0.10, Particle.FLAME),
    HEART("Heart Trail", Material.PINK_DYE, 20000, 0.15, Particle.HEART),
    STAR("Star Trail", Material.END_ROD, 75000, 0.20, Particle.END_ROD),
    SOUL("Soul Trail", Material.SOUL_LANTERN, 250000, 0.30, Particle.SOUL_FIRE_FLAME);

    public final String display;
    public final Material icon;
    public final double price;
    /** Extra speed multiplier added on top of treadmill speed. 0.10 = +10% */
    public final double bonus;
    public final Particle particle;

    Trail(String display, Material icon, double price, double bonus, Particle particle) {
        this.display = display;
        this.icon = icon;
        this.price = price;
        this.bonus = bonus;
        this.particle = particle;
    }
}
