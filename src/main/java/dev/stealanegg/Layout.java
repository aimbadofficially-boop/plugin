package dev.stealanegg;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.generator.ChunkGenerator;

import java.util.Random;

/**
 * Geometry for Steal an Egg.
 *
 * Section 1 = plaza map (used by Game, Guis, MapBuilder): plaza, shop stalls, conveyor,
 *             treadmill gym and player bases.
 * Section 2 = the older T-shaped map helpers, kept so any other file that still uses them compiles.
 */
public final class Layout {

    // ====================================================================================
    // SECTION 1 - plaza map
    // ====================================================================================

    public static final String WORLD = "sae_world";
    public static final int GY = 64;
    public static final int FLOOR = GY;

    // ---- field (where the bases are, north of the plaza) ----
    public static final int FIELD_X = 44;
    public static final int FIELD_Z1 = -105;
    public static final int FIELD_Z2 = -26;

    // ---- bases: 2 columns x 3 rows ----
    public static final int BASE_COUNT = 6;
    public static final int SLOTS = 10;

    public static final String[] BASE_NAMES = {
            "Red Base", "Blue Base", "Green Base", "Yellow Base", "Purple Base", "Orange Base"
    };
    public static final Material[] BASE_COLORS = {
            Material.RED_CONCRETE, Material.BLUE_CONCRETE, Material.LIME_CONCRETE,
            Material.YELLOW_CONCRETE, Material.PURPLE_CONCRETE, Material.ORANGE_CONCRETE
    };

    public static int baseCx(int i) { return (i % 2 == 0) ? -24 : 24; }
    public static int baseCz(int i) { return -45 - 25 * (i / 2); }

    /** 5 pedestals per row, 2 rows inside each base. */
    public static int slotX(int cx, int s) { return cx - 6 + (s % 5) * 3; }
    public static int slotZ(int cz, int s) { return cz - 5 + (s / 5) * 4; }

    // ---- conveyor (in the plaza, west to east) ----
    public static final int CONV_X1 = -14, CONV_X2 = 14;
    public static final int CONV_Z1 = 8, CONV_Z2 = 12;
    public static final double CONV_EGG_Z = 10.5;

    // ---- treadmill gym ----
    public static final int TM_COUNT = 4;
    public static final int TM_Z1 = 28, TM_Z2 = 45;

    public static int tmX(int i) { return -18 + 12 * i; }

    /** Which treadmill lane the location is on, or -1. */
    public static int treadmillAt(Location l) {
        if (l.getY() > FLOOR + 3) return -1;
        int x = l.getBlockX(), z = l.getBlockZ();
        if (z < TM_Z1 || z > TM_Z2) return -1;
        for (int i = 0; i < TM_COUNT; i++) {
            if (Math.abs(x - tmX(i)) <= 1) return i;
        }
        return -1;
    }

    /** Plaza + gym is the safe zone (no stealing / knocking). */
    public static boolean isSafe(Location l) {
        return l.getZ() > -25 && Math.abs(l.getX()) < 25;
    }

    // ---- shop stalls ----

    /**
     * A shop stall. Local coords: lx runs left/right along the counter, lz runs from the
     * customer side (negative) to the back panel (positive). facing = the way customers face.
     */
    public record Stall(String id, int ox, int oz, BlockFace facing) {
        public int wx(int lx, int lz) {
            return switch (facing) {
                case NORTH -> ox + lx;
                case SOUTH -> ox - lx;
                case EAST -> ox - lz;
                default -> ox + lz; // WEST
            };
        }

        public int wz(int lx, int lz) {
            return switch (facing) {
                case NORTH -> oz + lz;
                case SOUTH -> oz - lz;
                case EAST -> oz + lx;
                default -> oz - lx; // WEST
            };
        }
    }

    public static final Stall PVP = new Stall("pvp", -16, 17, BlockFace.NORTH);
    public static final Stall TRAIL = new Stall("trail", 0, 17, BlockFace.NORTH);
    public static final Stall INDEX = new Stall("index", 16, 17, BlockFace.NORTH);

    // ====================================================================================
    // SECTION 2 - older T-shaped map helpers (kept for compatibility)
    // ====================================================================================

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

    public static Location spawn(World w) { return new Location(w, 0.5, GY + 1, 4.5, 0, 0); }

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
