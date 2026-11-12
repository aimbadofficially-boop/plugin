package dev.stealanegg;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;

import java.util.*;

/** All static game definitions: biomes, eggs/pets, treadmills, trails. */
public final class GameData {
    public enum Rarity {
        COMMON("&f", 100), UNCOMMON("&a", 60), RARE("&9", 30), EPIC("&5", 12), LEGENDARY("&6", 5),
        COSMIC("&d", 2), SECRET("&8", 1), ETERNAL("&b", 0.5), DIVINE("&e", 0.2);
        public final String color;
        public final double weight;
        Rarity(String c, double w) { color = c; weight = w; }
        public String label() { return color + Util.pretty(name()); }
    }

    public enum Mutation {
        NONE(1.0, "", ""), SILVER(1.2, "Silver", "&7"), GOLDEN(2.5, "Golden", "&6"), RAINBOW(3.5, "Rainbow", "&d");
        public final double mult;
        public final String label, color;
        Mutation(double m, String l, String c) { mult = m; label = l; color = c; }
    }

    public record EggType(String id, String name, Rarity rarity, EntityType mob, double income, int grow,
                          double speed, String biome) {
        public double cash() { return income * 100; }
    }

    public static final class BiomeDef {
        public final int index;
        public final String id, name;
        public double speed;
        public final EntityType[] guards;
        public final Material floor, alt, sub, trunk, leaf;
        public final List<EggType> eggs = new ArrayList<>();
        BiomeDef(int i, String id, String name, double speed, Material floor, Material alt, Material sub,
                 Material trunk, Material leaf, EntityType... guards) {
            this.index = i; this.id = id; this.name = name; this.speed = speed; this.floor = floor; this.alt = alt;
            this.sub = sub; this.trunk = trunk; this.leaf = leaf; this.guards = guards;
        }
    }

    public record TreadmillDef(String name, double perStep, double cost, Material belt, Material stripe, Material frame) {}

    public record TrailDef(String id, String name, Rarity rarity, double mult, double cost, Particle particle,
                           Color color, Material icon) {}

    public static final List<BiomeDef> BIOMES = new ArrayList<>();
    public static final Map<String, EggType> EGGS = new LinkedHashMap<>();
    public static final List<TreadmillDef> TREADMILLS = new ArrayList<>();
    public static final List<TrailDef> TRAILS = new ArrayList<>();
    public static double incomeMult = 1, growMult = 1, walkCap = 0.8;

    // biome|mob|rarity|income $/s|grow seconds|speed reward   (cash reward = 100s of income, like the real game)
    // Forest/Lake numbers follow the wiki; the later biomes are scaled to match - edit in config.yml.
    private static final String EGG_TABLE = """
forest|CHICKEN|COMMON|1|10|420
forest|RABBIT|COMMON|2|15|460
forest|PARROT|UNCOMMON|8|25|500
forest|CAT|RARE|35|40|560
forest|FOX|EPIC|180|90|720
forest|PANDA|EPIC|240|120|820
forest|WOLF|LEGENDARY|1800|300|1000
lake|FROG|COMMON|3|15|2000
lake|COD|UNCOMMON|12|30|2500
lake|TURTLE|RARE|60|50|2700
lake|SQUID|EPIC|260|90|3000
lake|GLOW_SQUID|EPIC|320|120|3500
lake|AXOLOTL|LEGENDARY|2800|300|4000
lake|DOLPHIN|COSMIC|220000|1500|5000
desert|CAMEL|COMMON|20|20|7200
desert|ARMADILLO|UNCOMMON|60|40|8000
desert|HORSE|RARE|300|60|8800
desert|GOAT|EPIC|1500|120|9600
desert|SNIFFER|LEGENDARY|12000|600|11000
desert|SKELETON_HORSE|COSMIC|280000|1800|12000
jungle|OCELOT|COMMON|100|30|18000
jungle|BEE|UNCOMMON|400|60|20000
jungle|PANDA|RARE|3000|90|22000
jungle|SLIME|EPIC|15000|180|25000
jungle|CREEPER|LEGENDARY|100000|900|30000
jungle|IRON_GOLEM|COSMIC|3500000|5400|40000
snow|SNOW_GOLEM|COMMON|600|40|12800
snow|GOAT|UNCOMMON|2500|60|14000
snow|POLAR_BEAR|RARE|20000|120|16000
snow|FOX|EPIC|90000|240|18000
snow|WOLF|LEGENDARY|700000|900|22000
snow|ALLAY|COSMIC|65000000|14400|40000
volcano|MAGMA_CUBE|COMMON|2000|60|24000
volcano|STRIDER|UNCOMMON|8000|90|26000
volcano|BLAZE|RARE|70000|180|28000
volcano|PIGLIN|EPIC|400000|300|32000
volcano|GHAST|LEGENDARY|5000000|1200|36000
volcano|ZOGLIN|COSMIC|100000000|18000|50000
abyss|PUFFERFISH|COMMON|25000|60|60000
abyss|GLOW_SQUID|UNCOMMON|90000|90|66000
abyss|TURTLE|RARE|600000|180|72000
abyss|GUARDIAN|EPIC|4000000|600|80000
abyss|ELDER_GUARDIAN|LEGENDARY|30000000|1800|90000
abyss|DOLPHIN|COSMIC|500000000|28800|120000
prehistoric|FROG|COMMON|100000|60|90000
prehistoric|ARMADILLO|UNCOMMON|400000|90|100000
prehistoric|SNIFFER|RARE|3000000|300|110000
prehistoric|RAVAGER|EPIC|20000000|600|120000
prehistoric|ZOGLIN|LEGENDARY|150000000|1800|140000
prehistoric|SKELETON_HORSE|COSMIC|2000000000|43200|170000
cosmic|ENDERMITE|COMMON|1000000|90|200000
cosmic|PHANTOM|UNCOMMON|5000000|120|220000
cosmic|ALLAY|RARE|30000000|240|240000
cosmic|SHULKER|EPIC|200000000|600|260000
cosmic|ENDERMAN|LEGENDARY|1500000000|1800|300000
cosmic|BREEZE|COSMIC|10000000000|86400|400000
cherry|BEE|COMMON|5000000|120|500000
cherry|FOX|UNCOMMON|25000000|180|550000
cherry|ALLAY|RARE|150000000|300|600000
cherry|CAT|EPIC|1000000000|600|700000
cherry|PANDA|LEGENDARY|8000000000|1800|800000
cherry|SNIFFER|COSMIC|60000000000|7200|1000000
titan|IRON_GOLEM|COMMON|50000000|300|2000000
titan|RAVAGER|UNCOMMON|250000000|600|2500000
titan|ENDERMAN|RARE|1500000000|1200|3000000
titan|SHULKER|EPIC|10000000000|1800|3500000
titan|ELDER_GUARDIAN|LEGENDARY|80000000000|3600|4000000
titan|WARDEN|COSMIC|500000000000|14400|5000000
""";

    private static BiomeDef b(int i, String id, String name, double sp, Material fl, Material alt, Material sub,
                              Material tr, Material lf, EntityType... g) {
        BiomeDef d = new BiomeDef(i, id, name, sp, fl, alt, sub, tr, lf, g);
        BIOMES.add(d);
        return d;
    }

    public static void load(FileConfiguration c) {
        BIOMES.clear(); EGGS.clear(); TREADMILLS.clear(); TRAILS.clear();
        incomeMult = c.getDouble("settings.income-multiplier", 1);
        growMult = c.getDouble("settings.growth-multiplier", 1);
        walkCap = c.getDouble("settings.walk-speed-cap", 0.8);

        b(0, "forest", "Forest", 0, Material.GRASS_BLOCK, Material.PODZOL, Material.DIRT, Material.OAK_LOG, Material.OAK_LEAVES, EntityType.WOLF);
        b(1, "lake", "Lake", 900, Material.GRASS_BLOCK, Material.SAND, Material.DIRT, Material.BIRCH_LOG, Material.BIRCH_LEAVES, EntityType.DROWNED);
        b(2, "desert", "Desert", 10000, Material.SAND, Material.SMOOTH_SANDSTONE, Material.SANDSTONE, Material.CUT_SANDSTONE, Material.CHISELED_SANDSTONE, EntityType.HUSK);
        b(3, "jungle", "Jungle", 40000, Material.GRASS_BLOCK, Material.MOSS_BLOCK, Material.DIRT, Material.JUNGLE_LOG, Material.JUNGLE_LEAVES, EntityType.SPIDER);
        b(4, "snow", "Snow", 170000, Material.SNOW_BLOCK, Material.PACKED_ICE, Material.STONE, Material.SPRUCE_LOG, Material.SPRUCE_LEAVES, EntityType.POLAR_BEAR, EntityType.STRAY);
        b(5, "volcano", "Volcano", 700000, Material.BLACKSTONE, Material.SMOOTH_BASALT, Material.BLACKSTONE, Material.OBSIDIAN, Material.CRYING_OBSIDIAN, EntityType.BLAZE, EntityType.MAGMA_CUBE);
        b(6, "abyss", "Abyss Ocean", 5000000, Material.SAND, Material.GRAVEL, Material.SANDSTONE, Material.PRISMARINE_BRICKS, Material.SEA_LANTERN, EntityType.GUARDIAN, EntityType.DROWNED);
        b(7, "prehistoric", "Prehistoric", 50000000, Material.COARSE_DIRT, Material.PODZOL, Material.DIRT, Material.DARK_OAK_LOG, Material.DARK_OAK_LEAVES, EntityType.RAVAGER);
        b(8, "cosmic", "Cosmic", 500000000, Material.END_STONE, Material.PURPUR_BLOCK, Material.END_STONE, Material.OBSIDIAN, Material.CRYING_OBSIDIAN, EntityType.ENDERMAN, EntityType.SHULKER);
        b(9, "cherry", "Cherry Blossom", 2500000000.0, Material.GRASS_BLOCK, Material.PINK_CONCRETE_POWDER, Material.DIRT, Material.CHERRY_LOG, Material.CHERRY_LEAVES, EntityType.VINDICATOR);
        b(10, "titan", "Titan Temple", 25000000000.0, Material.STONE_BRICKS, Material.CHISELED_STONE_BRICKS, Material.STONE, Material.QUARTZ_PILLAR, Material.CHISELED_QUARTZ_BLOCK, EntityType.WARDEN);
        for (BiomeDef d : BIOMES) d.speed = c.getDouble("biomes." + d.id + ".speed", d.speed);

        for (String line : EGG_TABLE.split("\n")) {
            line = line.trim();
            if (line.isEmpty()) continue;
            String[] p = line.split("\\|");
            try {
                BiomeDef bd = BIOMES.stream().filter(x -> x.id.equals(p[0])).findFirst().orElseThrow();
                EntityType mob = EntityType.valueOf(p[1]);
                String id = p[0] + "_" + p[1].toLowerCase(Locale.ROOT);
                double inc = c.getDouble("eggs." + id + ".income", Double.parseDouble(p[3]));
                int grow = c.getInt("eggs." + id + ".grow", Integer.parseInt(p[4]));
                double sp = c.getDouble("eggs." + id + ".speed", Double.parseDouble(p[5]));
                EggType e = new EggType(id, Util.pretty(p[1]), Rarity.valueOf(p[2]), mob, inc, grow, sp, p[0]);
                EGGS.put(id, e);
                bd.eggs.add(e);
            } catch (Exception ex) {
                System.out.println("[StealAnEgg] skipped egg row '" + line + "': " + ex);
            }
        }

        // Treadmills: base/+5/+12/+30 and the last two tiers follow the wiki. Tiers 4-6 are estimated.
        tm("Basic Treadmill", 2, 0, Material.BLACK_CONCRETE, Material.GRAY_CONCRETE, Material.IRON_BLOCK, c, 0);
        tm("Sci-Fi Treadmill", 5, 15000, Material.BLACK_CONCRETE, Material.CYAN_CONCRETE, Material.SEA_LANTERN, c, 1);
        tm("Flame Treadmill", 12, 250000, Material.BLACK_CONCRETE, Material.ORANGE_CONCRETE, Material.MAGMA_BLOCK, c, 2);
        tm("Celebrity Treadmill", 30, 5e6, Material.BLACK_CONCRETE, Material.MAGENTA_CONCRETE, Material.PURPUR_BLOCK, c, 3);
        tm("Golden Treadmill", 80, 1.5e8, Material.BLACK_CONCRETE, Material.YELLOW_CONCRETE, Material.GOLD_BLOCK, c, 4);
        tm("The Freeze Treadmill", 100, 2e9, Material.BLACK_CONCRETE, Material.LIGHT_BLUE_CONCRETE, Material.BLUE_ICE, c, 5);
        tm("Apex Treadmill", 2000, 1e15, Material.BLACK_CONCRETE, Material.RED_CONCRETE, Material.NETHERITE_BLOCK, c, 6);
        tm("Astral Treadmill", 3000, 1.5e16, Material.BLACK_CONCRETE, Material.WHITE_CONCRETE, Material.DIAMOND_BLOCK, c, 7);

        // Trails: Grey/Green/Blue/Purple, Secret x7, Eternal x10, Divine x14, Moonbloom x20 follow the wiki;
        // Golden and Cosmic and the Secret price are estimated.
        tr("grey", "Grey Trail", Rarity.COMMON, 1.5, 100, Particle.DUST, Color.fromRGB(160, 160, 160), Material.GRAY_DYE, c);
        tr("green", "Green Trail", Rarity.UNCOMMON, 2, 5000, Particle.DUST, Color.fromRGB(60, 220, 60), Material.LIME_DYE, c);
        tr("blue", "Blue Trail", Rarity.RARE, 2.5, 75000, Particle.DUST, Color.fromRGB(60, 100, 255), Material.BLUE_DYE, c);
        tr("purple", "Purple Trail", Rarity.EPIC, 3, 1.5e6, Particle.DUST, Color.fromRGB(160, 60, 220), Material.PURPLE_DYE, c);
        tr("golden", "Golden Trail", Rarity.LEGENDARY, 4, 5e7, Particle.FLAME, null, Material.ORANGE_DYE, c);
        tr("cosmic", "Cosmic Trail", Rarity.COSMIC, 5.5, 3e9, Particle.END_ROD, null, Material.MAGENTA_DYE, c);
        tr("secret", "Secret Trail", Rarity.SECRET, 7, 1e11, Particle.ENCHANT, null, Material.BLACK_DYE, c);
        tr("eternal", "Eternal Trail", Rarity.ETERNAL, 10, 12.5e12, Particle.SOUL_FIRE_FLAME, null, Material.CYAN_DYE, c);
        tr("divine", "Divine Trail", Rarity.DIVINE, 14, 3e14, Particle.TOTEM_OF_UNDYING, null, Material.YELLOW_DYE, c);
        tr("moonbloom", "Moonbloom Trail", Rarity.DIVINE, 20, 5e15, Particle.CHERRY_LEAVES, null, Material.PINK_DYE, c);
    }

    private static void tm(String n, double ps, double cost, Material belt, Material stripe, Material frame, FileConfiguration c, int idx) {
        TREADMILLS.add(new TreadmillDef(n, c.getDouble("treadmills." + idx + ".per-step", ps),
                c.getDouble("treadmills." + idx + ".cost", cost), belt, stripe, frame));
    }

    private static void tr(String id, String n, Rarity r, double m, double cost, Particle p, Color col, Material ic, FileConfiguration c) {
        TRAILS.add(new TrailDef(id, n, r, c.getDouble("trails." + id + ".mult", m), c.getDouble("trails." + id + ".cost", cost), p, col, ic));
    }

    public static TrailDef trail(String id) {
        for (TrailDef t : TRAILS) if (t.id().equals(id)) return t;
        return null;
    }

    public static Mutation mut(String s) {
        try { return Mutation.valueOf(s); } catch (Exception e) { return Mutation.NONE; }
    }

    public static double sizeMult(double size) { return Math.pow(size, 0.8); }

    public static String sizeName(double s) {
        if (s >= 5) return "Colossal";
        if (s >= 3) return "Giant";
        if (s >= 2) return "Huge";
        if (s >= 1.5) return "Large";
        return "Normal";
    }

    /** Walk speed (Bukkit scale, 0.2 = vanilla) for a given Speed stat. */
    public static double walk(double speed) {
        return Math.min(walkCap, 0.2 + 0.08 * Math.log10(1 + Math.max(0, speed)));
    }

    public static double requiredAt(double z) {
        double r = 0;
        for (BiomeDef d : BIOMES) if (z >= Layout.gateZ(d.index)) r = Math.max(r, d.speed);
        return r;
    }
}
