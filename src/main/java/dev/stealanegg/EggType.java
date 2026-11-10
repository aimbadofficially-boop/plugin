package dev.stealanegg;

import org.bukkit.Material;

import java.util.concurrent.ThreadLocalRandom;

public enum EggType {
    COMMON("Common Egg", Material.EGG, "&f", 50, 1, 60),
    UNCOMMON("Uncommon Egg", Material.TURTLE_EGG, "&a", 250, 5, 25),
    RARE("Rare Egg", Material.SNIFFER_EGG, "&b", 1500, 25, 10),
    EPIC("Epic Egg", Material.DRAGON_EGG, "&d", 10000, 150, 4),
    LEGENDARY("Legendary Egg", Material.NETHER_STAR, "&6", 75000, 1000, 1);

    public final String display;
    public final Material material;
    public final String color;
    public final double cost;
    public final double income;
    public final int weight;

    EggType(String display, Material material, String color, double cost, double income, int weight) {
        this.display = display;
        this.material = material;
        this.color = color;
        this.cost = cost;
        this.income = income;
        this.weight = weight;
    }

    public String colored() {
        return color + display;
    }

    public static EggType roll() {
        int total = 0;
        for (EggType t : values()) total += t.weight;
        int r = ThreadLocalRandom.current().nextInt(total);
        for (EggType t : values()) {
            r -= t.weight;
            if (r < 0) return t;
        }
        return COMMON;
    }
}
