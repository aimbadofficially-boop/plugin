package dev.stealanegg;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;

import java.util.Random;

/** Geometry of the T-shaped map. Bar (bases) at z<20, stem (path + biomes) runs toward +z. */
public final class Layout {
    public static final String WORLD = "sae_world";
    public static final int GY = 64;
    public static final int[] PX = {-85, -55, -25, 25, 55, 85};
    public static final int R1_DOOR = -31, R2_DOOR = -10;
    public static final int PLOTS = 12;
    public static final int STEM_START = 20, BIOME_START = 40, BIOME_LEN = 80, BIOME_A = 44, BIOMES = 11;
    public static final int[] TRAILS = {3, 4}, UPGRADES = {6, 4}, PETS = {9, 4}, COLLECT = {12, 4}, LOCK = {-6, 3}, TREAD = {-12, 11};
    public static final int SLOT_D = 14;
    public static final int SHOP_X = -4, SHOP_Z = -20;

    public static final class VoidGen extends ChunkGenerator {
        @Override
        public Location getFixedSpawnLocation(World w, Random r) { return spawn(w); }
    }

    public static Location spawn(World w) { return new Location(w, 0.5, GY + 1, -14.5, 0, 0); }

    public static int cx(int plot) { return PX[plot % 6]; }
    public static boolean row1(int plot) { return plot < 6; }
    public static int bz(int plot, int d) { return row1(plot) ? R1_DOOR - d : R2_DOOR + d; }
    public static int bx(int plot, int u) { return cx(plot) + u; }
    public static int slotU(int s) { return -12 + 3 * s; }

    public static double zAt(int plot, double d) { return row1(plot) ? R1_DOOR - d + 0.5 : R2_DOOR + d + 0.5; }

    public static Location loc(World w, int plot, double u, double d, double yoff) {
        return new Location(w, cx(plot) + u + 0.5, GY + 1 + yoff, zAt(plot, d));
    }

    public static Location home(World w, int plot) {
        Location l = loc(w, plot, 0, 3, 0);
        l.setYaw(row1(plot) ? 180f : 0f);
        return l;
    }

    public static int plotAt(Location l) {
        int x = l.getBlockX(), z = l.getBlockZ();
        for (int i = 0; i < PLOTS; i++) {
            int c = cx(i);
            if (x < c - 15 || x > c + 14) continue;
            if (row1(i)) { if (z >= R1_DOOR - 29 && z <= R1_DOOR) return i; }
            else if (z >= R2_DOOR && z <= R2_DOOR + 29) return i;
        }
        return -1;
    }

    public static int uOf(int plot, Location l) { return l.getBlockX() - cx(plot); }
    public static int dOf(int plot, Location l) { return row1(plot) ? R1_DOOR - l.getBlockZ() : l.getBlockZ() - R2_DOOR; }

    public static boolean isSafe(Location l) { return l.getZ() < STEM_START; }

    public static int gateZ(int i) { return BIOME_START + i * BIOME_LEN; }
    public static int centerZ(int i) { return gateZ(i) + BIOME_LEN / 2; }

    public static int biomeAtZ(double z) {
        if (z < BIOME_START) return -1;
        int i = (int) ((z - BIOME_START) / BIOME_LEN);
        return i >= BIOMES ? -1 : i;
    }

    public static boolean inside(int i, int x, int z) {
        double dx = x / (double) BIOME_A, dz = (z - centerZ(i)) / (BIOME_LEN / 2.0);
        return dx * dx + dz * dz <= 1.0;
    }

    public static int[] nestXZ(int i, int k) {
        int side = (k % 2 == 0) ? -1 : 1;
        return new int[]{side * (15 + 4 * (k / 2)), centerZ(i) - 25 + k * 10};
    }

    public static int[] guardHome(int i, int j) {
        return new int[]{(j % 2 == 0 ? -13 : 13), centerZ(i) - 18 + j * 18};
    }

    public static final int POND_R = 5;
    public static int[] pond() { return new int[]{-28, centerZ(1) - 16}; }
    public static int[][] pools() { int cz = centerZ(5); return new int[][]{{-34, cz - 5}, {34, cz - 5}}; }

    private static int sq(int a) { return a * a; }

    private static int hRaw(int i, int x, int z) {
        if (Math.abs(x) <= 11) return 0;
        double amp = Math.min(1.0, (Math.abs(x) - 11) / 6.0);
        return (int) Math.round(amp * (2.2 * Math.sin(x / 6.5 + i) + 1.8 * Math.cos(z / 8.0)));
    }

    public static int h(int i, int x, int z) {
        for (int k = 0; k < 6; k++) {
            int[] n = nestXZ(i, k);
            if (sq(x - n[0]) + sq(z - n[1]) <= 16) return hRaw(i, n[0], n[1]);
        }
        if (i == 1) {
            int[] p = pond();
            if (sq(x - p[0]) + sq(z - p[1]) <= 64) return hRaw(i, p[0], p[1]);
        }
        if (i == 5) for (int[] p : pools()) if (sq(x - p[0]) + sq(z - p[1]) <= 100) return hRaw(i, p[0], p[1]);
        return hRaw(i, x, z);
    }

    public static int surfaceY(int i, int x, int z) {
        if (i == 6) return (x >= -10 && x <= 9) ? GY : 56;
        return GY + h(i, x, z);
    }
}
