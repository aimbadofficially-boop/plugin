package dev.stealanegg;

import dev.stealanegg.GameData.TrailDef;
import dev.stealanegg.GameData.TreadmillDef;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

/** Treadmill training (Speed gain), trail particles and walk-speed handling. */
public final class Training {
    private final StealAnEgg pl;
    private final Map<UUID, Location> last = new HashMap<>();
    private final Map<UUID, Location> lastTrail = new HashMap<>();
    private final Map<UUID, Double> acc = new HashMap<>();
    private final Map<UUID, Double> gained = new HashMap<>();
    private final Map<Integer, Integer> phase = new HashMap<>();
    private final Set<Integer> animating = new HashSet<>();
    private int tick;

    public Training(StealAnEgg pl) { this.pl = pl; }

    public void applyWalkSpeed(Player p, PlayerData pd) {
        double w = GameData.walk(pd.speed);
        PlayerData.EggRow c = pl.bases.carried(pd);
        if (c != null) w *= 1.0 / (1.0 + 0.1 * (c.size - 1));
        p.setWalkSpeed((float) Math.max(0.05, Math.min(1.0, w)));
    }

    public double trailMult(PlayerData pd) {
        TrailDef t = GameData.trail(pd.trail);
        return t == null ? 1.0 : t.mult();
    }

    public void tick() {
        tick++;
        Set<Integer> activeNow = new HashSet<>();
        double bps = pl.getConfig().getDouble("settings.treadmill-blocks-per-step", 1.0);
        for (Player p : pl.world.getPlayers()) {
            PlayerData pd = pl.players.get(p.getUniqueId());
            if (pd == null) continue;
            UUID id = p.getUniqueId();
            Location loc = p.getLocation();
            trail(p, pd, loc);
            if (pl.world.getBlockAt(loc).getY() < 0) continue;
            if (loc.getBlockZ() > 0 && tick % 20 == 0) {
                // abyss: breathing
                int bi = Layout.biomeAtZ(loc.getZ());
                if (bi == 6) p.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 60, 0, false, false, false));
            }
            if (pd.plot < 0) { last.remove(id); continue; }
            int u = Layout.uOf(pd.plot, loc), d = Layout.dOf(pd.plot, loc);
            boolean in = Layout.plotAt(loc) == pd.plot && u >= -13 && u <= -11 && d >= 2 && d <= 10 && loc.getY() < Layout.GY + 2.5;
            if (!in) { last.remove(id); continue; }
            activeNow.add(pd.plot);
            Location prev = last.get(id);
            if (prev != null) {
                double dx = loc.getX() - prev.getX(), dz = loc.getZ() - prev.getZ();
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > 0.01 && dist < 2.0) {
                    double a = acc.getOrDefault(id, 0.0) + dist;
                    TreadmillDef t = GameData.TREADMILLS.get(Math.min(pd.treadmill, GameData.TREADMILLS.size() - 1));
                    double gain = 0;
                    while (a >= bps) { a -= bps; gain += t.perStep() * trailMult(pd); }
                    acc.put(id, a);
                    if (gain > 0) {
                        pd.speed += gain;
                        applyWalkSpeed(p, pd);
                        gained.merge(id, gain, Double::sum);
                    }
                    if (tick % 3 == 0) p.getWorld().spawnParticle(Particle.CLOUD, loc, 1, 0.15, 0, 0.15, 0.01);
                    if (tick % 4 == 0) p.playSound(loc, Sound.BLOCK_WOOL_STEP, 0.4f, 1.0f);
                }
            }
            if (d >= 10) {
                Location back = loc.clone();
                back.setZ(Layout.zAt(pd.plot, 2.5));
                p.teleport(back);
                last.put(id, back);
            } else last.put(id, loc);
            if (tick % 5 == 0 && gained.containsKey(id)) {
                p.sendActionBar(Util.c("&b+" + Util.fmt(gained.remove(id)) + " Speed &8| &7Total: &f" + Util.fmt(pd.speed)
                        + (trailMult(pd) > 1 ? " &8| &dTrail x" + trailMult(pd) : "")));
            }
        }
        // belt animation
        for (int plot : activeNow) {
            int ph = phase.merge(plot, 1, Integer::sum);
            if (tick % 3 == 0) {
                PlayerData owner = pl.players.get(pl.bases.plots[plot].owner);
                if (owner != null) pl.builder.renderTreadmill(plot, owner.treadmill, ph);
            }
        }
        for (Iterator<Integer> it = animating.iterator(); it.hasNext(); ) {
            int plot = it.next();
            if (!activeNow.contains(plot)) {
                PlayerData owner = pl.bases.plots[plot].owner == null ? null : pl.players.get(pl.bases.plots[plot].owner);
                if (owner != null) pl.builder.renderTreadmill(plot, owner.treadmill, 0);
                it.remove();
            }
        }
        animating.addAll(activeNow);
    }

    private void trail(Player p, PlayerData pd, Location loc) {
        TrailDef t = GameData.trail(pd.trail);
        UUID id = p.getUniqueId();
        Location prev = lastTrail.put(id, loc);
        if (t == null || prev == null || !prev.getWorld().equals(loc.getWorld())) return;
        double dx = loc.getX() - prev.getX(), dz = loc.getZ() - prev.getZ();
        if (dx * dx + dz * dz < 0.0025) return;
        double yaw = Math.toRadians(loc.getYaw());
        Location at = loc.clone().add(Math.sin(yaw) * 0.6, 0.15, -Math.cos(yaw) * 0.6);
        if (t.particle() == Particle.DUST && t.color() != null)
            p.getWorld().spawnParticle(Particle.DUST, at, 3, 0.15, 0.05, 0.15, 0, new Particle.DustOptions(t.color(), 1.4f));
        else p.getWorld().spawnParticle(t.particle(), at, 2, 0.15, 0.05, 0.15, 0.01);
    }
}
