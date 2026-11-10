package dev.stealanegg;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;

import java.util.List;

/** Generates the whole Steal an Egg map: plaza, shops, conveyor, treadmill gym and player bases. */
public final class MapBuilder {
    private static final int F = Layout.FLOOR;

    private final StealAnEgg pl;
    private final World w;

    public MapBuilder(StealAnEgg pl, World w) {
        this.pl = pl;
        this.w = w;
    }

    public void build() {
        clear();
        field();
        plaza();
        gym();
        conveyor();
        stalls();
        treadmills();
        bases();
        walls();
        decor();
        BeltAnimator.render(w, 0);
    }

    // ------------------------------------------------------------------ helpers

    private void set(int x, int y, int z, Material m) {
        w.getBlockAt(x, y, z).setType(m, false);
    }

    private void fill(int x1, int y1, int z1, int x2, int y2, int z2, Material m) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++)
                    set(x, y, z, m);
    }

    private void wallSign(int x, int y, int z, BlockFace facing, String... lines) {
        Block b = w.getBlockAt(x, y, z);
        b.setType(Material.OAK_WALL_SIGN, false);
        Directional d = (Directional) b.getBlockData();
        d.setFacing(facing);
        b.setBlockData(d, false);
        Signs.write(b, lines);
    }

    // ------------------------------------------------------------------ pieces

    private void clear() {
        for (int x = -46; x <= 46; x++)
            for (int z = -110; z <= 52; z++)
                for (int y = F - 1; y <= F + 14; y++) {
                    Block b = w.getBlockAt(x, y, z);
                    if (b.getType() != Material.AIR) b.setType(Material.AIR, false);
                }
    }

    private void field() {
        fill(-Layout.FIELD_X, F, Layout.FIELD_Z1, Layout.FIELD_X, F, Layout.FIELD_Z2, Material.GRASS_BLOCK);
        // dirt paths: central road + cross roads between base rows
        fill(-2, F, Layout.FIELD_Z1 + 1, 2, F, Layout.FIELD_Z2, Material.DIRT_PATH);
        for (int row = 0; row < 3; row++) {
            int z = -45 - 25 * row + 12;
            fill(-Layout.FIELD_X + 1, F, z - 1, Layout.FIELD_X - 1, F, z + 1, Material.DIRT_PATH);
        }
    }

    private void plaza() {
        for (int x = -24; x <= 24; x++) {
            for (int z = -24; z <= 24; z++) {
                boolean tile = (((x + 24) >> 2) + ((z + 24) >> 2)) % 2 == 0;
                set(x, F, z, tile ? Material.POLISHED_ANDESITE : Material.SMOOTH_STONE);
            }
        }
        // spawn compass rose
        fill(-1, F, 3, 1, F, 5, Material.GOLD_BLOCK);
        set(0, F, 4, Material.EMERALD_BLOCK);
        // central road to bases
        fill(-3, F, -24, 3, F, -6, Material.STONE_BRICKS);
    }

    private void gym() {
        for (int x = -24; x <= 24; x++) {
            for (int z = 25; z <= 48; z++) {
                set(x, F, z, Material.SMOOTH_QUARTZ);
            }
        }
        fill(-24, F, 25, 24, F, 25, Material.LIGHT_BLUE_CONCRETE);
        fill(-24, F, 48, 24, F, 48, Material.LIGHT_BLUE_CONCRETE);
        wallSign(0, F + 2, 24, BlockFace.NORTH, "&b&lSPEED GYM", "&7Run on a", "&7treadmill to", "&7get FASTER!");
    }

    private void conveyor() {
        // rails
        fill(Layout.CONV_X1 - 1, F + 1, Layout.CONV_Z1 - 1, Layout.CONV_X2 + 1, F + 1, Layout.CONV_Z1 - 1, Material.SMOOTH_STONE_SLAB);
        fill(Layout.CONV_X1 - 1, F + 1, Layout.CONV_Z2 + 1, Layout.CONV_X2 + 1, F + 1, Layout.CONV_Z2 + 1, Material.SMOOTH_STONE_SLAB);
        // egg machine (west end)
        fill(-17, F + 1, 8, -16, F + 3, 12, Material.IRON_BLOCK);
        fill(-17, F + 4, 8, -16, F + 4, 12, Material.GOLD_BLOCK);
        wallSign(-15, F + 2, 10, BlockFace.EAST, "&6&lEGG MACHINE", "&7Tap an egg", "&7to BUY it!");
        // drain (east end)
        fill(Layout.CONV_X2 + 1, F + 1, Layout.CONV_Z1, Layout.CONV_X2 + 1, F + 2, Layout.CONV_Z2, Material.IRON_BARS);
    }

    /** Builds the three shop stalls. */
    private void stalls() {
        stall(Layout.PVP, Material.RED_WOOL, Material.WHITE_WOOL,
                new String[]{"&c&lPVP SHOP", "&7knock eggs", "&7loose", "&eClick me!"});
        stall(Layout.TRAIL, Material.LIGHT_BLUE_WOOL, Material.WHITE_WOOL,
                new String[]{"&b&lTRAIL SHOP", "&7speed trails", "", "&eClick me!"});
        stall(Layout.INDEX, Material.YELLOW_WOOL, Material.WHITE_WOOL,
                new String[]{"&6&lEGG INDEX", "&7your", "&7collection", "&eClick me!"});
    }

    private void stall(Layout.Stall s, Material c1, Material c2, String[] sign) {
        // posts
        for (int lx : new int[]{-4, 4}) {
            for (int lz : new int[]{-1, 4}) {
                for (int y = F + 1; y <= F + 4; y++) set(s.wx(lx, lz), y, s.wz(lx, lz), Material.STRIPPED_OAK_LOG);
            }
        }
        // striped roof
        for (int lx = -5; lx <= 5; lx++) {
            for (int lz = -2; lz <= 5; lz++) {
                set(s.wx(lx, lz), F + 5, s.wz(lx, lz), (lx & 1) == 0 ? c1 : c2);
            }
        }
        set(s.wx(-3, 1), F + 5, s.wz(-3, 1), Material.SEA_LANTERN);
        set(s.wx(3, 1), F + 5, s.wz(3, 1), Material.SEA_LANTERN);
        // back panel
        for (int lx = -4; lx <= 4; lx++) {
            for (int lz = 2; lz <= 3; lz++) {
                for (int y = F + 1; y <= F + 4; y++) set(s.wx(lx, lz), y, s.wz(lx, lz), Material.DARK_OAK_PLANKS);
            }
        }
        // counter + barrel
        for (int lx = -3; lx <= 3; lx++) set(s.wx(lx, 1), F + 1, s.wz(lx, 1), Material.SPRUCE_PLANKS);
        set(s.wx(0, 1), F + 1, s.wz(0, 1), Material.BARREL);
        // signs (attached to the back panel)
        wallSign(s.wx(0, 1), F + 2, s.wz(0, 1), s.facing(), sign);
        wallSign(s.wx(-2, 1), F + 2, s.wz(-2, 1), s.facing(), "&7Tap the", "&7sign or the", "&7counter");
        wallSign(s.wx(2, 1), F + 2, s.wz(2, 1), s.facing(), "&7Works on", "&aBedrock", "&7too!");
    }

    private void treadmills() {
        List<String> names = pl.getConfig().getStringList("treadmills.names");
        List<Double> gain = pl.getConfig().getDoubleList("treadmills.gain-per-second");
        List<Double> req = pl.getConfig().getDoubleList("treadmills.required-points");
        Material[] colors = {Material.LIME_CONCRETE, Material.YELLOW_CONCRETE, Material.ORANGE_CONCRETE, Material.RED_CONCRETE};

        for (int i = 0; i < Layout.TM_COUNT; i++) {
            int x0 = Layout.tmX(i);
            Material c = colors[i % colors.length];
            String name = i < names.size() ? names.get(i) : "Treadmill " + (i + 1);
            double g = i < gain.size() ? gain.get(i) : 1;
            double r = i < req.size() ? req.get(i) : 0;

            // side rails (2 high so nobody hops out)
            fill(x0 - 2, F + 1, Layout.TM_Z1, x0 - 2, F + 2, Layout.TM_Z2, c);
            fill(x0 + 2, F + 1, Layout.TM_Z1, x0 + 2, F + 2, Layout.TM_Z2, c);
            // back wall
            fill(x0 - 2, F + 1, Layout.TM_Z2 + 1, x0 + 2, F + 4, Layout.TM_Z2 + 2, Material.GRAY_CONCRETE);
            wallSign(x0, F + 3, Layout.TM_Z2, BlockFace.NORTH, "&l" + name, "&a+" + Msg.fmt(g) + " speed/s", "&7Run forward!");
            // entrance arch
            for (int y = F + 1; y <= F + 4; y++) {
                set(x0 - 2, y, Layout.TM_Z1 - 1, c);
                set(x0 + 2, y, Layout.TM_Z1 - 1, c);
            }
            fill(x0 - 2, F + 4, Layout.TM_Z1 - 1, x0 + 2, F + 4, Layout.TM_Z1 - 1, c);
            wallSign(x0, F + 4, Layout.TM_Z1 - 2, BlockFace.NORTH,
                    "&l" + name, "&a+" + Msg.fmt(g) + " speed/s", r <= 0 ? "&7Free to use" : "&cNeeds " + Msg.fmt(r), "&7speed points");
            set(x0 - 2, F + 5, Layout.TM_Z1 - 1, Material.SEA_LANTERN);
            set(x0 + 2, F + 5, Layout.TM_Z1 - 1, Material.SEA_LANTERN);
        }
    }

    private void bases() {
        for (int i = 0; i < Layout.BASE_COUNT; i++) {
            int cx = Layout.baseCx(i);
            int cz = Layout.baseCz(i);
            Material color = Layout.BASE_COLORS[i % Layout.BASE_COLORS.length];

            for (int x = cx - 7; x <= cx + 7; x++) {
                for (int z = cz - 7; z <= cz + 7; z++) {
                    boolean ring = Math.abs(x - cx) == 7 || Math.abs(z - cz) == 7;
                    set(x, F, z, ring ? color : Material.SMOOTH_STONE);
                }
            }
            // walls: west, east, north (south side is the open entrance)
            for (int y = F + 1; y <= F + 3; y++) {
                fill(cx - 8, y, cz - 8, cx - 8, y, cz + 8, Material.STONE_BRICKS);
                fill(cx + 8, y, cz - 8, cx + 8, y, cz + 8, Material.STONE_BRICKS);
                fill(cx - 8, y, cz - 8, cx + 8, y, cz - 8, Material.STONE_BRICKS);
            }
            fill(cx - 8, F + 4, cz - 8, cx - 8, F + 4, cz + 8, color);
            fill(cx + 8, F + 4, cz - 8, cx + 8, F + 4, cz + 8, color);
            fill(cx - 8, F + 4, cz - 8, cx + 8, F + 4, cz - 8, color);
            set(cx - 8, F + 5, cz + 8, Material.LANTERN);
            set(cx + 8, F + 5, cz + 8, Material.LANTERN);

            // egg pedestals
            for (int s = 0; s < Layout.SLOTS; s++) {
                set(Layout.slotX(cx, s), F + 1, Layout.slotZ(cz, s), Material.POLISHED_BLACKSTONE_SLAB);
            }
            // owner sign
            wallSign(cx, F + 2, cz - 7, BlockFace.SOUTH, "&l" + Layout.BASE_NAMES[i % Layout.BASE_NAMES.length], "&7Free base");
        }
    }

    private void walls() {
        Material m = Material.DEEPSLATE_BRICKS;
        // plaza + gym sides
        fill(-25, F + 1, -24, -25, F + 2, 49, m);
        fill(25, F + 1, -24, 25, F + 2, 49, m);
        fill(-25, F + 1, 49, 25, F + 2, 49, m);
        // field outer walls
        fill(-Layout.FIELD_X - 1, F + 1, Layout.FIELD_Z1 - 1, -Layout.FIELD_X - 1, F + 2, Layout.FIELD_Z2, m);
        fill(Layout.FIELD_X + 1, F + 1, Layout.FIELD_Z1 - 1, Layout.FIELD_X + 1, F + 2, Layout.FIELD_Z2, m);
        fill(-Layout.FIELD_X - 1, F + 1, Layout.FIELD_Z1 - 1, Layout.FIELD_X + 1, F + 2, Layout.FIELD_Z1 - 1, m);
        // wall between plaza and the field, with a gate in the middle
        fill(-Layout.FIELD_X - 1, F + 1, -25, -5, F + 2, -25, m);
        fill(5, F + 1, -25, Layout.FIELD_X + 1, F + 2, -25, m);
        for (int y = F + 1; y <= F + 4; y++) {
            set(-5, y, -25, Material.CHISELED_STONE_BRICKS);
            set(5, y, -25, Material.CHISELED_STONE_BRICKS);
        }
        fill(-5, F + 5, -25, 5, F + 5, -25, Material.CHISELED_STONE_BRICKS);
        wallSign(-6, F + 2, -24, BlockFace.SOUTH, "&a&lSAFE ZONE", "&7No stealing", "&7in the plaza");
        wallSign(6, F + 2, -24, BlockFace.SOUTH, "&c&lBASES", "&7Eggs can be", "&7stolen there!");
    }

    private void decor() {
        // lanterns on the plaza corners
        for (int x : new int[]{-24, 24}) {
            for (int z : new int[]{-24, 48}) {
                fill(x, F + 1, z, x, F + 3, z, Material.POLISHED_DEEPSLATE);
                set(x, F + 4, z, Material.SEA_LANTERN);
            }
        }
    }
}
