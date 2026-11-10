package dev.stealanegg;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;

import java.util.ArrayList;
import java.util.List;

/** All coordinates of the generated map live here so the builder and the game logic always agree. */
public final class Layout {
    private Layout() {}

    /** Y of the floor blocks; players stand at FLOOR + 1. */
    public static final int FLOOR = 63;

    // Plaza + gym ("hub") rectangle
    public static final int HUB_X = 24;
    public static final int HUB_Z1 = -24;
    public static final int HUB_Z2 = 48;

    public static final int SLOTS = 8;
    public static final int BASE_COUNT = 12;
    public static final int BASE_HALF = 7;

    // Base field (grass area north of the plaza)
    public static final int FIELD_X = 42;
    public static final int FIELD_Z1 = -106;
    public static final int FIELD_Z2 = -25;

    public static final Material[] BASE_COLORS = {
            Material.RED_CONCRETE, Material.BLUE_CONCRETE, Material.LIME_CONCRETE, Material.YELLOW_CONCRETE,
            Material.PURPLE_CONCRETE, Material.ORANGE_CONCRETE, Material.CYAN_CONCRETE, Material.PINK_CONCRETE
    };
    public static final String[] BASE_NAMES = {
            "Red Base", "Blue Base", "Green Base", "Yellow Base",
            "Purple Base", "Orange Base", "Cyan Base", "Pink Base"
    };

    // ---- stalls (local coords: lx = sideways, lz = depth, 0 = front, growing towards the back) ----
    public record Stall(String id, int cx, int cz, BlockFace facing) {
        public int wx(int lx, int lz) {
            return switch (facing) {
                case SOUTH -> cx + lx;
                case NORTH -> cx - lx;
                case EAST -> cx - lz;
                default -> cx + lz;
            };
        }

        public int wz(int lx, int lz) {
            return switch (facing) {
                case SOUTH -> cz - lz;
                case NORTH -> cz + lz;
                case EAST -> cz + lx;
                default -> cz - lx;
            };
        }

        public float yaw() {
            return switch (facing) {
                case SOUTH -> 0f;
                case WEST -> 90f;
                case NORTH -> 180f;
                default -> 270f;
            };
        }
    }

    public static final Stall PVP = new Stall("pvp", -17, 0, BlockFace.EAST);
    public static final Stall TRAIL = new Stall("trail", 17, 0, BlockFace.WEST);
    public static final Stall INDEX = new Stall("index", 0, -17, BlockFace.SOUTH);

    // ---- egg conveyor ----
    public static final int CONV_X1 = -14;
    public static final int CONV_X2 = 14;
    public static final int CONV_Z1 = 9;
    public static final int CONV_Z2 = 11;
    public static final double CONV_EGG_Z = 10.5;

    // ---- treadmills ----
    public static final int TM_COUNT = 4;
    public static final int TM_Z1 = 28;
    public static final int TM_Z2 = 41;

    public static int tmX(int lane) {
        return -15 + 10 * lane;
    }

    /** Lane index the location is standing on, or -1. */
    public static int treadmillAt(Location l) {
        int bx = l.getBlockX();
        int bz = l.getBlockZ();
        if (bz < TM_Z1 || bz > TM_Z2) return -1;
        if (l.getY() < FLOOR + 0.5 || l.getY() > FLOOR + 4) return -1;
        for (int i = 0; i < TM_COUNT; i++) {
            if (Math.abs(bx - tmX(i)) <= 1) return i;
        }
        return -1;
    }

    public record Belt(int x1, int x2, int z1, int z2, boolean alongX, int dir) {}

    public static List<Belt> belts() {
        List<Belt> list = new ArrayList<>();
        list.add(new Belt(CONV_X1, CONV_X2, CONV_Z1, CONV_Z2, true, 1));
        for (int i = 0; i < TM_COUNT; i++) {
            list.add(new Belt(tmX(i) - 1, tmX(i) + 1, TM_Z1, TM_Z2, false, -1));
        }
        return list;
    }

    // ---- bases ----
    private static final int[] BASE_X = {-30, -10, 10, 30};

    public static int baseCx(int index) {
        return BASE_X[index % 4];
    }

    public static int baseCz(int index) {
        return -45 - 25 * (index / 4);
    }

    public static int slotX(int baseCx, int slot) {
        return baseCx - 6 + 4 * (slot % 4);
    }

    public static int slotZ(int baseCz, int slot) {
        return baseCz + (slot < 4 ? -5 : -1);
    }

    // ---- zones ----
    public static boolean isSafe(Location l) {
        return l.getX() >= -26 && l.getX() <= 26 && l.getZ() >= -26 && l.getZ() <= 50;
    }
}
