package dev.stealanegg;

import dev.stealanegg.GameData.BiomeDef;
import dev.stealanegg.GameData.EggType;
import dev.stealanegg.GameData.Rarity;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.*;

import java.time.Duration;
import java.util.*;

/** Guarded nests in every biome + vanilla-mob guardians that chase egg thieves. */
public final class NestManager {
    public static final class Nest {
        int biome;
        Location loc;
        EggType egg;
        double size;
        boolean taken;
        ItemDisplay disp;
        TextDisplay label;
    }

    public static final class Guard {
        int biome;
        Location home;
        EntityType type;
        LivingEntity ent;
        long chase;
    }

    private final StealAnEgg pl;
    public final List<Nest> nests = new ArrayList<>();
    public final List<Guard> guards = new ArrayList<>();
    private final TextDisplay[] gate = new TextDisplay[Layout.BIOMES];
    private long nextReset;
    private final Random rnd = new Random();
    private static final double[] SIZES = {1.0, 1.5, 2.0, 3.0, 5.0};
    private static final double[] SIZE_W = {60, 25, 10, 4, 1};

    public NestManager(StealAnEgg pl) { this.pl = pl; }

    public void spawnAll() {
        World w = pl.world;
        for (BiomeDef b : GameData.BIOMES) {
            int i = b.index;
            for (int k = 0; k < 6; k++) {
                int[] xz = Layout.nestXZ(i, k);
                int sy = Layout.surfaceY(i, xz[0], xz[1]);
                Nest n = new Nest();
                n.biome = i;
                n.loc = new Location(w, xz[0] + 0.5, sy + 1, xz[1] + 0.5);
                Ents.hit(n.loc, 1.5f, 1.6f, "nest", String.valueOf(nests.size()));
                nests.add(n);
            }
            for (int j = 0; j < 3; j++) {
                int[] h = Layout.guardHome(i, j);
                int sy = Layout.surfaceY(i, h[0], h[1]);
                Guard g = new Guard();
                g.biome = i;
                g.home = new Location(w, h[0] + 0.5, sy + 1, h[1] + 0.5);
                g.type = b.guards[j % b.guards.length];
                spawnGuard(g);
                guards.add(g);
            }
            gate[i] = Ents.holo(new Location(w, 0.5, Layout.GY + 14.5, Layout.gateZ(i) + 0.5), "");
        }
        resetAll();
    }

    private EggType roll(BiomeDef b) {
        double tot = 0;
        for (EggType e : b.eggs) tot += e.rarity().weight;
        double r = rnd.nextDouble() * tot;
        for (EggType e : b.eggs) { r -= e.rarity().weight; if (r <= 0) return e; }
        return b.eggs.get(0);
    }

    private double rollSize() {
        double tot = 0;
        for (double d : SIZE_W) tot += d;
        double r = rnd.nextDouble() * tot;
        for (int i = 0; i < SIZES.length; i++) { r -= SIZE_W[i]; if (r <= 0) return SIZES[i]; }
        return 1.0;
    }

    public void resetAll() {
        for (Nest n : nests) {
            BiomeDef b = GameData.BIOMES.get(n.biome);
            n.egg = b.eggs.isEmpty() ? null : roll(b);
            n.size = rollSize();
            n.taken = false;
            render(n);
        }
        nextReset = System.currentTimeMillis() + pl.getConfig().getLong("settings.nest-reset-seconds", 300) * 1000L;
    }

    private void render(Nest n) {
        if (n.disp != null) n.disp.remove();
        if (n.label != null) n.label.remove();
        n.disp = null; n.label = null;
        if (n.taken || n.egg == null) return;
        n.disp = Ents.item(n.loc.clone().add(0, 0.9, 0), Ents.eggMat(n.egg.mob()), 1f, "egg", "nest");
        Ents.transform(n.disp, (float) (0.8 + 0.3 * n.size), 0f);
        double val = n.egg.income() * GameData.sizeMult(n.size) * GameData.incomeMult;
        n.label = Ents.holo(n.loc.clone().add(0, 1.9 + 0.15 * n.size, 0),
                n.egg.rarity().color + n.egg.name() + " Egg\n&a$" + Util.fmt(val) + "/s &8| &7" + GameData.sizeName(n.size)
                        + "\n&7Hatch: &f" + Util.dur((long) (n.egg.grow() * GameData.growMult)));
    }

    public long secondsLeft() { return Math.max(0, (nextReset - System.currentTimeMillis()) / 1000); }

    public void tickSecond() {
        if (System.currentTimeMillis() >= nextReset) {
            resetAll();
            for (Player p : pl.world.getPlayers()) p.sendActionBar(Util.c("&e&lNests have reset! Fresh eggs are waiting."));
        }
        for (BiomeDef b : GameData.BIOMES)
            Ents.setText(gate[b.index], "&6&l" + b.name.toUpperCase() + "\n&7Speed required: &b" + Util.fmt(b.speed)
                    + "\n&7Nests reset in &f" + Util.dur(secondsLeft()));
    }

    // ------------------------------------------------------------------ stealing
    public void steal(Player p, int idx) {
        if (idx < 0 || idx >= nests.size()) return;
        Nest n = nests.get(idx);
        PlayerData pd = pl.players.get(p.getUniqueId());
        if (pd == null) return;
        BiomeDef b = GameData.BIOMES.get(n.biome);
        if (pd.speed < b.speed && !pl.bypass.contains(p.getUniqueId())) {
            p.sendActionBar(Util.c("&cYou need " + Util.fmt(b.speed) + " Speed to steal here!"));
            return;
        }
        if (pl.bases.carried(pd) != null) { p.sendActionBar(Util.c("&cYou're already carrying an egg! Bring it home first.")); return; }
        if (n.taken || n.egg == null) { p.sendActionBar(Util.c("&cThis nest is empty. Reset in " + Util.dur(secondsLeft()))); return; }
        if (!pl.bases.cooldown(p, "steal", 800)) return;
        EggType t = n.egg;
        double size = n.size;
        if (pl.bases.grantEgg(p, pd, t, size) == null) return;
        n.taken = true;
        render(n);
        alert(n.biome, p);
        p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.6f);
        p.showTitle(Title.title(Util.c("&c&lRUN!"), Util.c("&7The guardians are chasing you - get to your base!"),
                Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(1500), Duration.ofMillis(300))));
    }

    /** Every guardian of the biome targets the thief for 20 seconds. */
    public void alert(int biome, Player thief) {
        long until = System.currentTimeMillis() + 20000;
        for (Guard g : guards) {
            if (g.biome != biome || g.ent == null || !g.ent.isValid()) continue;
            g.chase = until;
            aim(g.ent, thief);
        }
    }

    private void aim(LivingEntity e, Player p) {
        if (e instanceof Warden wd) wd.setAnger(p, 120);
        if (e instanceof Mob m) m.setTarget(p);
    }

    // ------------------------------------------------------------------ guardians
    private void spawnGuard(Guard g) {
        World w = g.home.getWorld();
        Entity e = w.spawnEntity(g.home, g.type);
        if (!(e instanceof LivingEntity le)) { e.remove(); return; }
        g.ent = le;
        BiomeDef b = GameData.BIOMES.get(g.biome);
        le.setPersistent(false);
        le.setRemoveWhenFarAway(false);
        le.setInvulnerable(true);
        le.setCanPickupItems(false);
        if (le instanceof Ageable a) a.setAdult();
        if (le instanceof Slime s) s.setSize(2);
        double gm = Math.max(0.12, GameData.walk(b.speed) / 2.0);
        attr(le, Attribute.MOVEMENT_SPEED, gm);
        attr(le, Attribute.FLYING_SPEED, gm);
        attr(le, Attribute.FOLLOW_RANGE, 48);
        attr(le, Attribute.ATTACK_DAMAGE, pl.getConfig().getDouble("settings.guardian-damage-base", 2)
                + pl.getConfig().getDouble("settings.guardian-damage-per-biome", 1) * g.biome);
        le.customName(Util.c("&c&l" + Util.pretty(g.type.name()) + " &7Guardian"));
        le.setCustomNameVisible(true);
        Util.tag(le, "guardian", String.valueOf(g.biome));
    }

    private void attr(LivingEntity e, Attribute a, double v) {
        AttributeInstance ai = e.getAttribute(a);
        if (ai != null) ai.setBaseValue(v);
    }

    public void guardianAI() {
        long now = System.currentTimeMillis();
        double aggro = pl.getConfig().getDouble("settings.guardian-aggro-radius", 12);
        for (Guard g : guards) {
            if (g.ent == null || !g.ent.isValid()) { spawnGuard(g); continue; }
            LivingEntity e = g.ent;
            Player best = null;
            double bd = Double.MAX_VALUE;
            for (Player p : pl.world.getPlayers()) {
                if (p.getGameMode() == GameMode.SPECTATOR || p.isDead() || Layout.isSafe(p.getLocation())) continue;
                PlayerData pd = pl.players.get(p.getUniqueId());
                double r = (pd != null && pl.bases.carried(pd) != null) ? aggro * 2 : aggro;
                double d = p.getLocation().distanceSquared(e.getLocation());
                if (d < r * r && d < bd) { bd = d; best = p; }
            }
            if (best != null) { g.chase = now + 4000; aim(e, best); }
            else if (now > g.chase && e instanceof Mob m && m.getTarget() != null) m.setTarget(null);
            if (e instanceof Mob m && m.getTarget() == null && e.getLocation().distanceSquared(g.home) > 16)
                m.getPathfinder().moveTo(g.home);
            if (e.getLocation().distanceSquared(g.home) > 90 * 90) {
                e.teleport(g.home);
                if (e instanceof Mob m) m.setTarget(null);
            }
        }
    }
}
