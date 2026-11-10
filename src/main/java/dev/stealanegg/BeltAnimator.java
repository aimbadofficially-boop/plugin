package dev.stealanegg;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/** Scrolls the striped conveyor / treadmill belts so they look like they are moving. */
public final class BeltAnimator {
    private final StealAnEgg pl;
    private int phase;

    public BeltAnimator(StealAnEgg pl) {
        this.pl = pl;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(pl, () -> {
            if (pl.world.getPlayers().isEmpty()) return;
            phase++;
            render(pl.world, phase);
        }, 8L, 8L);
    }

    public static void render(World w, int phase) {
        for (Layout.Belt b : Layout.belts()) {
            for (int x = b.x1(); x <= b.x2(); x++) {
                for (int z = b.z1(); z <= b.z2(); z++) {
                    int idx = b.alongX() ? x : z;
                    boolean dark = Math.floorMod(idx - b.dir() * phase, 4) < 2;
                    Material m = dark ? Material.BLACK_CONCRETE : Material.GRAY_CONCRETE;
                    Block bl = w.getBlockAt(x, Layout.FLOOR, z);
                    if (bl.getType() != m) bl.setType(m, false);
                }
            }
        }
    }
}
