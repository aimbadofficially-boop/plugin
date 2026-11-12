package dev.stealanegg;

import dev.stealanegg.GameData.BiomeDef;
import dev.stealanegg.GameData.TreadmillDef;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.Random;

/** Builds the T-shaped map: hub bar with 12 bases, long stem path, 11 biomes. */
public final class WorldBuilder {
    private final World w;
    private static final int GY = Layout.GY;

    public WorldBuilder(World w) { this.w = w; }

    private void set(int x, int y, int z, Material m) { w.getBlockAt(x, y, z).setType(m, false); }

    private void fill(int x1, int y1, int z1, int x2, int y2, int z2, Material m) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) set(x, y, z, m);
    }

    public void buildAll() {
        hub();
        for (int i = 0; i < Layout.PLOTS; i++) buildPlot(i);
        stem();
        for (int i = 0; i < Layout.BIOMES; i++) buildBiome(i);
    }

    // ---------------------------------------------------------------- hub
    private void hub() {
        for (int x = -100; x <= 99; x++)
            for (int z = -30; z <= -11; z++) {
                set(x, GY, z, ((x + z) & 1) == 0 ? Material.POLISHED_ANDESITE : Material.STONE_BRICKS);
                set(x, GY - 1, z, Material.STONE);
            }
        for (int x = -10; x <= 9; x++)
            for (int z = -60; z <= 19; z++) {
                set(x, GY, z, ((x + z) & 1) == 0 ? Material.POLISHED_DIORITE : Material.POLISHED_ANDESITE);
                set(x, GY - 1, z, Material.STONE);
            }
        // fountain
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -3; dz <= 3; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                int x = dx, z = -40 + dz;
                if (d <= 1.6) set(x, GY, z, Material.WATER);
                else if (d <= 2.6) { set(x, GY, z, Material.STONE_BRICKS); set(x, GY + 1, z, Material.STONE_BRICK_WALL); }
            }
        set(0, GY + 1, -40, Material.SEA_LANTERN);
        // trail shop
        set(Layout.SHOP_X, GY + 1, Layout.SHOP_Z, Material.AMETHYST_BLOCK);
    }

    // ---------------------------------------------------------------- bases
    private void buildPlot(int i) {
        int cx = Layout.cx(i);
        for (int u = -15; u <= 14; u++)
            for (int d = 0; d <= 29; d++) {
                int x = cx + u, z = Layout.bz(i, d);
                set(x, GY, z, Material.GRASS_BLOCK);
                set(x, GY - 1, z, Material.DIRT);
                set(x, GY - 2, z, Material.STONE);
            }
        for (int d = 0; d <= 29; d++) { fence(i, -15, d); fence(i, 14, d); }
        for (int u = -15; u <= 14; u++) {
            fence(i, u, 29);
            if (Math.abs(u) > 2) fence(i, u, 0);
        }
        for (int u = -14; u <= 13; u++) if (Math.abs(u) > 1) fence(i, u, 17);
        for (int d = 0; d <= 16; d++)
            for (int u = -1; u <= 1; u++) set(cx + u, GY, Layout.bz(i, d), Material.STONE_BRICKS);
        for (int d = 2; d <= 10; d++)
            for (int y = 1; y <= 2; y++) {
                set(cx - 14, GY + y, Layout.bz(i, d), Material.IRON_BARS);
                set(cx - 10, GY + y, Layout.bz(i, d), Material.IRON_BARS);
            }
        renderTreadmill(i, 0, 0);
        block(i, Layout.TRAILS, Material.AMETHYST_BLOCK);
        block(i, Layout.UPGRADES, Material.SMITHING_TABLE);
        block(i, Layout.PETS, Material.HAY_BLOCK);
        block(i, Layout.COLLECT, Material.GOLD_BLOCK);
        block(i, Layout.LOCK, Material.LODESTONE);
        for (int s = 0; s < 9; s++)
            set(Layout.bx(i, Layout.slotU(s)), GY + 1, Layout.bz(i, Layout.SLOT_D), Material.RED_CONCRETE);
    }

    private void fence(int plot, int u, int d) {
        set(Layout.bx(plot, u), GY + 1, Layout.bz(plot, d), Material.DARK_OAK_FENCE);
    }

    private void block(int plot, int[] ud, Material m) {
        set(Layout.bx(plot, ud[0]), GY + 1, Layout.bz(plot, ud[1]), m);
    }

    /** Treadmill belt (animated by phase) + tier-coloured frame and console. */
    public void renderTreadmill(int plot, int tier, int phase) {
        TreadmillDef t = GameData.TREADMILLS.get(Math.max(0, Math.min(tier, GameData.TREADMILLS.size() - 1)));
        int cx = Layout.cx(plot);
        for (int d = 2; d <= 10; d++) {
            int z = Layout.bz(plot, d);
            boolean stripe = (((d + phase) % 4) + 4) % 4 < 2;
            for (int u = -13; u <= -11; u++) set(cx + u, GY, z, stripe ? t.stripe() : t.belt());
            set(cx - 14, GY, z, t.frame());
            set(cx - 10, GY, z, t.frame());
        }
        int z = Layout.bz(plot, 11);
        for (int u = -14; u <= -10; u++) {
            set(cx + u, GY, z, t.frame());
            set(cx + u, GY + 1, z, t.frame());
        }
        set(cx - 12, GY + 2, z, Material.SEA_LANTERN);
    }

    // ---------------------------------------------------------------- stem + biomes
    private void stem() {
        for (int z = Layout.STEM_START; z < Layout.BIOME_START; z++)
            for (int x = -10; x <= 9; x++) {
                set(x, GY, z, (x >= -3 && x <= 2) ? Material.STONE_BRICKS : Material.POLISHED_ANDESITE);
                set(x, GY - 1, z, Material.STONE);
            }
    }

    private Material pick(BiomeDef b, int x, int z, boolean stem) {
        if (stem) return (x >= -3 && x <= 2) ? b.alt : b.floor;
        return Math.floorMod(x * 31 + z * 17 + x * z, 10) < 2 ? b.alt : b.floor;
    }

    private void buildBiome(int i) {
        BiomeDef b = GameData.BIOMES.get(i);
        int z0 = Layout.gateZ(i), z1 = z0 + Layout.BIOME_LEN - 1;
        for (int z = z0; z <= z1; z++)
            for (int x = -46; x <= 46; x++) {
                boolean stem = x >= -10 && x <= 9;
                if (!stem && !Layout.inside(i, x, z)) continue;
                column(i, b, x, z, stem);
            }
        features(i, b);
    }

    private void column(int i, BiomeDef b, int x, int z, boolean stem) {
        if (i == 6) {
            if (stem) {
                for (int y = 56; y < GY; y++) set(x, y, z, Material.PRISMARINE_BRICKS);
                set(x, GY, z, (x >= -3 && x <= 2) ? Material.DARK_PRISMARINE : Material.PRISMARINE_BRICKS);
                return;
            }
            set(x, 56, z, Material.SAND);
            set(x, 55, z, Material.SANDSTONE);
            boolean edge = false;
            int[][] nb = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] n : nb) {
                int nx = x + n[0], nz = z + n[1];
                if (!Layout.inside(i, nx, nz) && !(nx >= -10 && nx <= 9)) edge = true;
            }
            for (int y = 57; y < GY; y++) set(x, y, z, edge ? Material.PRISMARINE : Material.WATER);
            if (edge) set(x, GY, z, Material.PRISMARINE_BRICKS);
            return;
        }
        int sy = Layout.surfaceY(i, x, z);
        set(x, sy, z, pick(b, x, z, stem));
        set(x, sy - 1, z, b.sub);
        set(x, sy - 2, z, b.sub);
        set(x, sy - 3, z, Material.STONE);
    }

    private void features(int i, BiomeDef b) {
        int cz = Layout.centerZ(i);
        // lake pond
        if (i == 1) {
            int[] p = Layout.pond();
            int sy = Layout.surfaceY(i, p[0], p[1]);
            for (int dx = -7; dx <= 7; dx++)
                for (int dz = -7; dz <= 7; dz++) {
                    double d = Math.sqrt(dx * dx + dz * dz);
                    int x = p[0] + dx, z = p[1] + dz;
                    if (!Layout.inside(i, x, z)) continue;
                    if (d <= Layout.POND_R) {
                        set(x, sy - 3, z, Material.SAND);
                        set(x, sy - 2, z, Material.WATER);
                        set(x, sy - 1, z, Material.WATER);
                        set(x, sy, z, Material.WATER);
                    } else if (d <= Layout.POND_R + 1.5) set(x, sy, z, Material.SAND);
                }
        }
        // volcano lava pools
        if (i == 5) {
            for (int[] p : Layout.pools()) {
                int sy = Layout.surfaceY(i, p[0], p[1]);
                for (int dx = -7; dx <= 7; dx++)
                    for (int dz = -7; dz <= 7; dz++) {
                        double d = Math.sqrt(dx * dx + dz * dz);
                        int x = p[0] + dx, z = p[1] + dz;
                        if (!Layout.inside(i, x, z)) continue;
                        if (d <= 5) {
                            set(x, sy - 2, z, Material.OBSIDIAN);
                            set(x, sy - 1, z, Material.LAVA);
                            set(x, sy, z, Material.LAVA);
                        } else if (d <= 6.5) {
                            set(x, sy, z, Material.POLISHED_BLACKSTONE);
                            if (d > 5.7) set(x, sy + 1, z, Material.NETHER_BRICK_FENCE);
                        }
                    }
            }
        }
        // nests
        for (int k = 0; k < 6; k++) {
            int[] n = Layout.nestXZ(i, k);
            int sy = Layout.surfaceY(i, n[0], n[1]);
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++) set(n[0] + dx, sy, n[1] + dz, Material.HAY_BLOCK);
            set(n[0], sy, n[1], Material.ORANGE_TERRACOTTA);
        }
        decorate(i, b, cz);
        gate(i, b);
    }

    private boolean near(int x, int z, int cx, int cz, int r) {
        return (x - cx) * (x - cx) + (z - cz) * (z - cz) <= r * r;
    }

    private void decorate(int i, BiomeDef b, int cz) {
        Random r = new Random(1000L + i);
        for (int n = 0; n < 70; n++) {
            int x = r.nextInt(85) - 42, z = cz - 36 + r.nextInt(72);
            if (Math.abs(x) < 13 || !Layout.inside(i, x, z)) continue;
            boolean bad = false;
            for (int k = 0; k < 6; k++) { int[] q = Layout.nestXZ(i, k); if (near(x, z, q[0], q[1], 6)) bad = true; }
            if (i == 1) { int[] p = Layout.pond(); if (near(x, z, p[0], p[1], 9)) bad = true; }
            if (i == 5) for (int[] p : Layout.pools()) if (near(x, z, p[0], p[1], 10)) bad = true;
            if (bad) continue;
            int sy = Layout.surfaceY(i, x, z);
            switch (i) {
                case 0 -> { if (r.nextInt(10) < 7) tree(x, sy, z, 4 + r.nextInt(3), 2, b.trunk, b.leaf); else set(x, sy + 1, z, r.nextBoolean() ? Material.POPPY : Material.DANDELION); }
                case 1 -> tree(x, sy, z, 4 + r.nextInt(2), 2, b.trunk, b.leaf);
                case 2 -> {
                    int t = r.nextInt(10);
                    if (t < 5) { int h = 1 + r.nextInt(3); for (int y = 1; y <= h; y++) set(x, sy + y, z, Material.CACTUS); }
                    else if (t < 8) set(x, sy + 1, z, Material.DEAD_BUSH);
                    else pillar(x, sy, z, 4, b.trunk, b.leaf);
                }
                case 3 -> tree(x, sy, z, 8 + r.nextInt(5), 3, b.trunk, b.leaf);
                case 4 -> { if (r.nextInt(10) < 6) tree(x, sy, z, 5 + r.nextInt(4), 2, b.trunk, b.leaf); else spike(x, sy, z, 6 + r.nextInt(7), r.nextBoolean() ? Material.BLUE_ICE : Material.PACKED_ICE); }
                case 5 -> spike(x, sy, z, 5 + r.nextInt(6), r.nextInt(4) == 0 ? b.leaf : b.trunk);
                case 6 -> {
                    if (r.nextBoolean()) pillar(x, sy, z, 6 + r.nextInt(5), b.trunk, b.leaf);
                    else for (int c = 0; c < 4; c++) set(x + r.nextInt(3) - 1, sy + 1, z + r.nextInt(3) - 1, new Material[]{Material.TUBE_CORAL_BLOCK, Material.BRAIN_CORAL_BLOCK, Material.FIRE_CORAL_BLOCK, Material.HORN_CORAL_BLOCK}[r.nextInt(4)]);
                }
                case 7 -> {
                    if (r.nextInt(10) < 6) tree(x, sy, z, 7 + r.nextInt(4), 3, b.trunk, b.leaf);
                    else { fill(x, sy + 1, z, x, sy + 6, z, Material.BONE_BLOCK); fill(x + 4, sy + 1, z, x + 4, sy + 6, z, Material.BONE_BLOCK); fill(x, sy + 7, z, x + 4, sy + 7, z, Material.BONE_BLOCK); }
                }
                case 8 -> {
                    pillar(x, sy, z, 8 + r.nextInt(7), b.trunk, b.leaf);
                    if (r.nextBoolean()) set(x + 2, sy + 9 + r.nextInt(3), z + 1, Material.AMETHYST_BLOCK);
                }
                case 9 -> tree(x, sy, z, 5 + r.nextInt(3), 3, b.trunk, b.leaf);
                default -> pillar(x, sy, z, 10 + r.nextInt(5), b.trunk, b.leaf);
            }
        }
    }

    private void tree(int x, int y, int z, int h, int rad, Material log, Material leaf) {
        for (int k = 1; k <= h; k++) set(x, y + k, z, log);
        for (int dx = -rad; dx <= rad; dx++)
            for (int dy = -rad; dy <= rad; dy++)
                for (int dz = -rad; dz <= rad; dz++) {
                    if (dx * dx + dy * dy * 1.5 + dz * dz > rad * rad + 1) continue;
                    if (dx == 0 && dz == 0 && dy <= 0) continue;
                    int px = x + dx, py = y + h + dy, pz = z + dz;
                    if (w.getBlockAt(px, py, pz).getType().isAir()) set(px, py, pz, leaf);
                }
    }

    private void pillar(int x, int y, int z, int h, Material m, Material cap) {
        for (int k = 1; k <= h; k++) set(x, y + k, z, m);
        set(x, y + h + 1, z, cap);
    }

    private void spike(int x, int y, int z, int h, Material m) {
        for (int k = 0; k < h; k++) {
            int rad = (int) Math.round(2.0 * (h - k) / h);
            for (int dx = -rad; dx <= rad; dx++)
                for (int dz = -rad; dz <= rad; dz++)
                    if (dx * dx + dz * dz <= rad * rad) set(x + dx, y + 1 + k, z + dz, m);
        }
    }

    private void gate(int i, BiomeDef b) {
        int z = Layout.gateZ(i);
        for (int x : new int[]{-12, -11, 10, 11}) fill(x, GY - 2, z, x, GY + 10, z, Material.STONE_BRICKS);
        fill(-12, GY + 11, z, 11, GY + 11, z, b.trunk);
        fill(-12, GY + 12, z, 11, GY + 12, z, Material.POLISHED_ANDESITE);
    }
}
