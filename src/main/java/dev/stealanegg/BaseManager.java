package dev.stealanegg;

import dev.stealanegg.GameData.*;
import dev.stealanegg.PlayerData.EggRow;
import dev.stealanegg.PlayerData.PetRow;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;
import java.util.*;

/** Bases: plot entities, incubators, hatching, pets (real vanilla mobs), income, carrying eggs, stealing. */
public final class BaseManager {
    public static final class Plot {
        public final int idx;
        public UUID owner;
        public String ownerName = "";
        public TextDisplay title, collect, treadLabel;
        public final ItemDisplay[] eggDisp = new ItemDisplay[9];
        public final TextDisplay[] eggTxt = new TextDisplay[9];
        Plot(int i) { idx = i; }
    }

    private static final Set<EntityType> PASSIVE = EnumSet.of(EntityType.CHICKEN, EntityType.RABBIT, EntityType.PARROT,
            EntityType.CAT, EntityType.OCELOT, EntityType.FOX, EntityType.FROG, EntityType.TURTLE, EntityType.ARMADILLO,
            EntityType.HORSE, EntityType.SKELETON_HORSE, EntityType.CAMEL, EntityType.SNIFFER, EntityType.ALLAY,
            EntityType.PANDA, EntityType.WOLF, EntityType.BEE, EntityType.STRIDER);

    private final StealAnEgg pl;
    public final Plot[] plots = new Plot[Layout.PLOTS];
    private final Map<UUID, ItemDisplay> carryDisp = new HashMap<>();
    private final Map<String, BukkitRunnable> anims = new HashMap<>();
    private final Map<String, Long> cool = new HashMap<>();
    private final Random rnd = new Random();

    public BaseManager(StealAnEgg pl) { this.pl = pl; }

    // ------------------------------------------------------------------ setup
    public void spawnPlotEntities() {
        World w = pl.world;
        for (int i = 0; i < Layout.PLOTS; i++) {
            Plot p = new Plot(i);
            plots[i] = p;
            p.title = Ents.holo(Layout.loc(w, i, 0, 1, 3.5), "&7Unclaimed base");
            p.collect = station(w, i, "COLLECT", Layout.COLLECT, "&6&lCollect Money", 0);
            station(w, i, "TRAILS", Layout.TRAILS, "&d&lTrail Manager", 0);
            station(w, i, "UPGRADES", Layout.UPGRADES, "&b&lBase Upgrades", 0);
            station(w, i, "PETS", Layout.PETS, "&a&lPets", 0);
            station(w, i, "LOCK", Layout.LOCK, "&c&lBase Lock", 0);
            p.treadLabel = station(w, i, "TREADMILL", Layout.TREAD, "&e&lTreadmill", 1);
            for (int s = 0; s < 9; s++) {
                Location l = Layout.loc(w, i, Layout.slotU(s), Layout.SLOT_D, 0);
                Ents.hit(l, 1.3f, 1.6f, "slot", i + ":" + s);
                p.eggTxt[s] = Ents.holo(l.clone().add(0, 2.8, 0), "");
            }
        }
        Location s = new Location(w, Layout.SHOP_X + 0.5, Layout.GY + 1, Layout.SHOP_Z + 0.5);
        Ents.hit(s, 1.2f, 1.6f, "station", "-1:SHOP");
        Ents.holo(s.clone().add(0, 2.3, 0), "&d&lTRAIL SHOP\n&7Trails multiply treadmill Speed");
        Ents.holo(new Location(w, 0.5, Layout.GY + 4, -40.5), "&e&lSTEAL AN EGG\n&7Head down the path to find eggs!");
    }

    private TextDisplay station(World w, int plot, String kind, int[] ud, String label, double yoff) {
        Location l = Layout.loc(w, plot, ud[0], ud[1], yoff);
        Ents.hit(l, 1.1f, 1.6f, "station", plot + ":" + kind);
        return Ents.holo(l.clone().add(0, 2.3, 0), label);
    }

    // ------------------------------------------------------------------ helpers
    public int petCap(PlayerData pd) { return pl.getConfig().getInt("settings.base-pet-capacity", 6) + pd.penLevel; }

    public int slots(PlayerData pd) { return Math.min(9, pl.getConfig().getInt("settings.base-incubator-slots", 2) + pd.slotLevel); }

    public double penCost(PlayerData pd) {
        return pl.getConfig().getDouble("upgrades.pen.base-cost", 50000) * Math.pow(pl.getConfig().getDouble("upgrades.pen.multiplier", 10), pd.penLevel);
    }

    public double slotCost(PlayerData pd) {
        return pl.getConfig().getDouble("upgrades.incubator.base-cost", 25000) * Math.pow(pl.getConfig().getDouble("upgrades.incubator.multiplier", 8), pd.slotLevel);
    }

    public EggRow carried(PlayerData pd) {
        for (EggRow e : pd.eggs) if (e.state.equals("CARRIED")) return e;
        return null;
    }

    private EggRow eggAt(PlayerData pd, int slot) {
        for (EggRow e : pd.eggs) if (e.slot == slot && (e.state.equals("INCUBATING") || e.state.equals("HATCHING"))) return e;
        return null;
    }

    private int freeSlot(PlayerData pd) {
        int n = slots(pd);
        for (int s = 0; s < n; s++) if (eggAt(pd, s) == null) return s;
        return -1;
    }

    public boolean cooldown(Player p, String key, long ms) {
        String k = p.getUniqueId() + key;
        long now = System.currentTimeMillis();
        Long last = cool.get(k);
        if (last != null && now - last < ms) return false;
        cool.put(k, now);
        return true;
    }

    private void msg(Player p, String s) { p.sendMessage(Util.c(s)); }

    // ------------------------------------------------------------------ join / quit
    public void join(Player p) {
        UUID id = p.getUniqueId();
        PlayerData pd = pl.db.load(id);
        boolean fresh = pd == null;
        if (fresh) {
            pd = new PlayerData(id, p.getName());
            pd.speed = pl.getConfig().getDouble("settings.starting-speed", 10);
            pd.money = pl.getConfig().getDouble("settings.starting-money", 0);
        }
        pd.name = p.getName();
        assignPlot(pd);
        long now = System.currentTimeMillis();
        pl.players.put(id, pd);
        if (pd.plot >= 0) { plots[pd.plot].owner = id; plots[pd.plot].ownerName = pd.name; }

        if (!fresh && pl.getConfig().getBoolean("offline-income.enabled", true) && pd.lastSeen > 0) {
            long off = (now - pd.lastSeen) / 1000;
            long cap = (long) (pl.getConfig().getDouble("offline-income.max-hours", 24) * 3600);
            off = Math.min(off, cap);
            double gain = pd.income() * off * pl.getConfig().getDouble("offline-income.rate", 1.0);
            if (gain > 0 && off > 30) {
                pd.pending += gain;
                msg(p, "&6While you were away (&f" + Util.dur(off) + "&6) your pets earned &a$" + Util.fmt(gain) + "&6. Collect it at your base!");
            }
        }
        pd.lastSeen = now;
        pl.db.save(pd);

        p.setGameMode(GameMode.ADVENTURE);
        p.teleport(pd.plot >= 0 ? Layout.home(pl.world, pd.plot) : Layout.spawn(pl.world));
        if (pd.plot < 0) msg(p, "&cAll bases are taken right now. Ask an admin or try again later.");
        else msg(p, "&aWelcome! Your base is plot #" + (pd.plot + 1) + ". Use /sae help.");
        spawnPets(p, pd);
        EggRow c = carried(pd);
        if (c != null) { showCarry(p, c); msg(p, "&eYou are still carrying an egg - bring it home!"); }
        pl.training.applyWalkSpeed(p, pd);
        if (pd.plot >= 0) refreshPlot(pd.plot);
    }

    private void assignPlot(PlayerData pd) {
        Set<Integer> used = pl.db.usedPlots(pd.uuid);
        if (pd.plot >= 0 && !used.contains(pd.plot)) return;
        pd.plot = -1;
        for (int i = 0; i < Layout.PLOTS; i++) if (!used.contains(i)) { pd.plot = i; break; }
        if (pd.plot < 0) {
            long days = pl.getConfig().getLong("settings.plot-expire-days", 14);
            int f = pl.db.freeStalePlot(System.currentTimeMillis() - days * 86400000L, pd.uuid);
            if (f >= 0) pd.plot = f;
        }
    }

    public void quit(Player p) {
        PlayerData pd = pl.players.remove(p.getUniqueId());
        if (pd == null) return;
        for (EggRow e : pd.eggs) { BukkitRunnable r = anims.remove(e.id); if (r != null) r.cancel(); }
        pl.db.revertHatching(pd.uuid);
        pd.lastSeen = System.currentTimeMillis();
        pl.db.save(pd);
        removePets(pd);
        hideCarry(p);
        if (pd.plot >= 0) refreshPlot(pd.plot);
        p.setWalkSpeed(0.2f);
    }

    public void autosave() {
        for (PlayerData pd : pl.players.values()) { pd.lastSeen = System.currentTimeMillis(); pl.db.save(pd); }
    }

    public Location respawnLoc(PlayerData pd) {
        return pd != null && pd.plot >= 0 ? Layout.home(pl.world, pd.plot) : Layout.spawn(pl.world);
    }

    // ------------------------------------------------------------------ per-second tick
    private int secs;

    public void tickSecond() {
        secs++;
        long now = System.currentTimeMillis();
        for (PlayerData pd : new ArrayList<>(pl.players.values())) {
            Player p = Bukkit.getPlayer(pd.uuid);
            if (p == null) continue;
            pd.pending += pd.income();
            if (pd.plot < 0) continue;
            tickEggs(p, pd, now);
            if (secs % 5 == 0) ensurePets(p, pd);
            refreshPlot(pd.plot);
        }
    }

    private void tickEggs(Player p, PlayerData pd, long now) {
        for (EggRow e : new ArrayList<>(pd.eggs)) {
            if (!e.state.equals("INCUBATING")) continue;
            long rem = e.readyAt - now;
            if (rem <= 0) {
                if (pd.pets.size() >= petCap(pd)) continue;
                startHatch(p, pd, e);
            } else if (rem <= 3000) {
                Plot pt = plots[pd.plot];
                if (e.slot >= 0 && pt.eggDisp[e.slot] != null)
                    p.playSound(pt.eggDisp[e.slot].getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f, 1.2f + (3000 - rem) / 2000f);
            }
        }
    }

    // ------------------------------------------------------------------ labels / visuals
    public void refreshPlot(int idx) {
        Plot p = plots[idx];
        World w = pl.world;
        PlayerData pd = p.owner == null ? null : pl.players.get(p.owner);
        if (p.owner == null) Ents.setText(p.title, "&7Unclaimed base");
        else if (pd == null) Ents.setText(p.title, "&7" + p.ownerName + "'s base &8(offline)");
        else Ents.setText(p.title, "&e&l" + p.ownerName + "'s Base\n&7Pets &f" + pd.pets.size() + "/" + petCap(pd)
                + " &8| &7Income &a$" + Util.fmt(pd.income()) + "/s" + (pd.locked ? "\n&c&lLOCKED" : ""));
        if (pd != null) {
            Ents.setText(p.collect, "&6&lCollect Money\n&a$" + Util.fmt(pd.pending) + "\n&7(+$" + Util.fmt(pd.income()) + "/s)");
            TreadmillDef t = GameData.TREADMILLS.get(Math.min(pd.treadmill, GameData.TREADMILLS.size() - 1));
            TrailDef tr = GameData.trail(pd.trail);
            String next = pd.treadmill + 1 < GameData.TREADMILLS.size()
                    ? "&aClick to upgrade: $" + Util.fmt(GameData.TREADMILLS.get(pd.treadmill + 1).cost()) : "&6MAX TIER";
            Ents.setText(p.treadLabel, "&e&l" + t.name() + "\n&b+" + Util.fmt(t.perStep()) + " Speed/step"
                    + (tr != null ? " &7x" + tr.mult() : "") + "\n" + next);
        } else {
            Ents.setText(p.collect, "&6&lCollect Money");
            Ents.setText(p.treadLabel, "&e&lTreadmill");
        }
        int unlocked = pd == null ? 0 : slots(pd);
        long now = System.currentTimeMillis();
        for (int s = 0; s < 9; s++) {
            EggRow er = pd == null ? null : eggAt(pd, s);
            Location l = Layout.loc(w, idx, Layout.slotU(s), Layout.SLOT_D, 0);
            Block ped = l.getBlock();
            Material pm = pd == null ? Material.GRAY_CONCRETE : (s < unlocked ? Material.SEA_LANTERN : Material.RED_CONCRETE);
            if (ped.getType() != pm) ped.setType(pm, false);
            ItemDisplay d = p.eggDisp[s];
            EggType t = er == null ? null : GameData.EGGS.get(er.type);
            if (er == null || t == null) {
                if (d != null) { d.remove(); p.eggDisp[s] = null; }
                Ents.setText(p.eggTxt[s], pd == null ? "" : (s < unlocked ? "&8Empty incubator" : "&cLocked"));
                continue;
            }
            if (d == null || !d.isValid() || !er.id.equals(Util.data(d))) {
                if (d != null) d.remove();
                d = Ents.item(l.clone().add(0, 1.9, 0), Ents.eggMat(t.mob()), 1f, "egg", er.id);
                Ents.transform(d, (float) (0.9 + 0.25 * er.size), 0f);
                p.eggDisp[s] = d;
            }
            String status;
            long rem = er.readyAt - now;
            if (er.state.equals("HATCHING")) status = "&a&lHatching!";
            else if (rem <= 0) status = "&cPen full!";
            else status = "&7Hatches in &f" + Util.dur((rem + 999) / 1000);
            Ents.setText(p.eggTxt[s], t.rarity().color + t.name() + " Egg\n&7" + GameData.sizeName(er.size) + "\n" + status);
        }
    }

    // ------------------------------------------------------------------ pets
    private void spawnPets(Player p, PlayerData pd) {
        if (pd.plot < 0) return;
        for (PetRow pr : pd.pets) if (pr.ent == null || !pr.ent.isValid()) pr.ent = spawnPet(pd, pr);
    }

    public void removePets(PlayerData pd) {
        for (PetRow pr : pd.pets) { if (pr.ent != null) pr.ent.remove(); pr.ent = null; }
    }

    private Location penPos(int plot) {
        double u = -12 + rnd.nextDouble() * 24, d = 19 + rnd.nextDouble() * 8;
        return Layout.loc(pl.world, plot, u, d, 0);
    }

    public String petName(PetRow pr) {
        EggType t = GameData.EGGS.get(pr.type);
        Mutation m = GameData.mut(pr.mutation);
        return t.rarity().color + t.name() + (m != Mutation.NONE ? " " + m.color + "[" + m.label + "]" : "") + " &a$" + Util.fmt(pr.income()) + "/s";
    }

    private LivingEntity spawnPet(PlayerData pd, PetRow pr) {
        EggType t = GameData.EGGS.get(pr.type);
        if (t == null || pd.plot < 0) return null;
        Entity e = pl.world.spawnEntity(penPos(pd.plot), t.mob());
        if (!(e instanceof LivingEntity le)) { e.remove(); return null; }
        le.setPersistent(false);
        le.setRemoveWhenFarAway(false);
        le.setInvulnerable(true);
        le.setSilent(true);
        le.setCollidable(false);
        le.setCanPickupItems(false);
        le.setAI(PASSIVE.contains(t.mob()));
        if (le instanceof Ageable a) a.setAdult();
        if (le instanceof Slime s) s.setSize(2);
        if (le instanceof Piglin pg) pg.setImmuneToZombification(true);
        if (!(le instanceof Slime)) {
            AttributeInstance sc = le.getAttribute(Attribute.SCALE);
            if (sc != null) sc.setBaseValue(Math.min(2.5, 0.75 + 0.25 * pr.size));
        }
        le.customName(Util.c(petName(pr)));
        le.setCustomNameVisible(true);
        if (!GameData.mut(pr.mutation).equals(Mutation.NONE)) le.setGlowing(true);
        Util.tag(le, "pet", pd.uuid + ":" + pr.id);
        return le;
    }

    private void ensurePets(Player p, PlayerData pd) {
        for (PetRow pr : pd.pets) {
            if (pr.ent == null || !pr.ent.isValid()) { pr.ent = spawnPet(pd, pr); continue; }
            Location l = pr.ent.getLocation();
            int plot = Layout.plotAt(l);
            int d = plot == pd.plot ? Layout.dOf(pd.plot, l) : -1;
            if (plot != pd.plot || d < 17 || l.getY() < Layout.GY - 1) pr.ent.teleport(penPos(pd.plot));
        }
    }

    public PetRow grantPet(Player p, PlayerData pd, String type, String mut, double size) {
        if (pd.pets.size() >= petCap(pd)) return null;
        long id = pl.db.insertPet(pd.uuid, type, mut, size);
        if (id < 0) return null;
        PetRow pr = new PetRow();
        pr.id = id; pr.type = type; pr.mutation = mut; pr.size = size;
        pd.pets.add(pr);
        pr.ent = spawnPet(pd, pr);
        return pr;
    }

    public void releasePet(Player p, PlayerData pd, PetRow pr) {
        if (!pl.db.deletePet(pr.id, pd.uuid)) return;
        if (pr.ent != null) pr.ent.remove();
        pd.pets.remove(pr);
        double cash = pr.income() * 60;
        pd.money += cash;
        msg(p, "&7Released pet for &a$" + Util.fmt(cash));
        refreshPlot(pd.plot);
    }

    // ------------------------------------------------------------------ carrying
    public EggRow grantEgg(Player p, PlayerData pd, EggType t, double size) {
        if (carried(pd) != null) return null;
        EggRow e = new EggRow();
        e.id = UUID.randomUUID().toString(); e.type = t.id(); e.size = size; e.state = "CARRIED";
        if (!pl.db.insertEgg(e.id, e.type, e.size, pd.uuid, "CARRIED", -1, 0)) return null;
        pd.eggs.add(e);
        showCarry(p, e);
        pl.training.applyWalkSpeed(p, pd);
        return e;
    }

    private void showCarry(Player p, EggRow e) {
        hideCarry(p);
        EggType t = GameData.EGGS.get(e.type);
        if (t == null) return;
        ItemDisplay d = Ents.item(p.getLocation().add(0, 2.4, 0), Ents.eggMat(t.mob()), 1f, "carry", p.getUniqueId().toString());
        Ents.transform(d, (float) (0.7 + 0.2 * e.size), 0f);
        carryDisp.put(p.getUniqueId(), d);
    }

    private void hideCarry(Player p) {
        ItemDisplay d = carryDisp.remove(p.getUniqueId());
        if (d != null) d.remove();
    }

    public void followCarry() {
        for (Map.Entry<UUID, ItemDisplay> en : carryDisp.entrySet()) {
            Player p = Bukkit.getPlayer(en.getKey());
            ItemDisplay d = en.getValue();
            if (p == null || !d.isValid()) continue;
            d.teleport(p.getLocation().add(0, 2.4, 0));
        }
    }

    public void tryDeposit(Player p, PlayerData pd) {
        EggRow e = carried(pd);
        if (e == null) return;
        int slot = freeSlot(pd);
        if (slot < 0) {
            if (cooldown(p, "full", 3000)) msg(p, "&cYour incubators are full! Upgrade them or wait for a hatch.");
            return;
        }
        EggType t = GameData.EGGS.get(e.type);
        if (t == null) return;
        long ready = System.currentTimeMillis() + (long) (t.grow() * 1000L * GameData.growMult);
        if (!pl.db.eggTransition(e.id, "CARRIED", "INCUBATING", pd.uuid, slot, ready)) return;
        e.state = "INCUBATING"; e.slot = slot; e.readyAt = ready;
        hideCarry(p);
        pl.training.applyWalkSpeed(p, pd);
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
        p.showTitle(Title.title(Util.c("&a&lEgg secured!"), Util.c(t.rarity().color + t.name() + " &7is incubating"),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1200), Duration.ofMillis(300))));
        refreshPlot(pd.plot);
    }

    /** Carried egg is lost (death) - removed permanently. */
    public void loseCarry(Player p, PlayerData pd) {
        EggRow e = carried(pd);
        if (e == null) return;
        pl.db.deleteEgg(e.id);
        pd.eggs.remove(e);
        hideCarry(p);
        pl.training.applyWalkSpeed(p, pd);
        msg(p, "&cYou dropped the egg and it was lost!");
    }

    // ------------------------------------------------------------------ stealing from other players
    public void stealFromPlayer(Player thief, Player victim) {
        if (!pl.getConfig().getBoolean("settings.steal-from-carriers", true)) return;
        PlayerData t = pl.players.get(thief.getUniqueId()), v = pl.players.get(victim.getUniqueId());
        if (t == null || v == null || t == v) return;
        if (Layout.isSafe(victim.getLocation()) || Layout.isSafe(thief.getLocation())) {
            thief.sendActionBar(Util.c("&cNo stealing inside the safe zone!"));
            return;
        }
        EggRow e = carried(v);
        if (e == null) return;
        if (carried(t) != null) { thief.sendActionBar(Util.c("&cYou are already carrying an egg!")); return; }
        if (!cooldown(thief, "pvpsteal", pl.getConfig().getLong("settings.steal-cooldown-ms", 3000))) return;
        if (!pl.db.eggSteal(e.id, v.uuid, "CARRIED", t.uuid)) return;
        v.eggs.remove(e); t.eggs.add(e);
        hideCarry(victim); showCarry(thief, e);
        pl.training.applyWalkSpeed(victim, v); pl.training.applyWalkSpeed(thief, t);
        thief.playSound(thief.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1f, 1.4f);
        victim.playSound(victim.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 0.8f);
        msg(thief, "&aYou stole " + victim.getName() + "'s egg! Run home!");
        msg(victim, "&c" + thief.getName() + " stole your egg!");
    }

    // ------------------------------------------------------------------ station / slot clicks
    public void slotClick(Player p, String data) {
        String[] sp = data.split(":");
        Plot plot = plots[Integer.parseInt(sp[0])];
        int slot = Integer.parseInt(sp[1]);
        if (plot.owner == null) return;
        PlayerData victim = pl.players.get(plot.owner);
        if (victim == null) return;
        EggRow er = eggAt(victim, slot);
        if (plot.owner.equals(p.getUniqueId())) {
            if (er == null) p.sendActionBar(Util.c(slot < slots(victim) ? "&7Empty incubator - carry an egg here." : "&cLocked - buy incubator slots in Base Upgrades."));
            else p.sendActionBar(Util.c("&7Your egg is growing..."));
            return;
        }
        PlayerData thief = pl.players.get(p.getUniqueId());
        if (thief == null || er == null || !er.state.equals("INCUBATING")) return;
        if (!pl.getConfig().getBoolean("settings.steal-from-bases", true)) { msg(p, "&cStealing from bases is disabled."); return; }
        if (carried(thief) != null) { p.sendActionBar(Util.c("&cYou are already carrying an egg!")); return; }
        if (er.readyAt - System.currentTimeMillis() < 2000) { p.sendActionBar(Util.c("&cIt's about to hatch - too late!")); return; }
        if (!cooldown(p, "basesteal", pl.getConfig().getLong("settings.steal-cooldown-ms", 3000))) return;
        if (!pl.db.eggSteal(er.id, victim.uuid, "INCUBATING", thief.uuid)) return;
        victim.eggs.remove(er);
        er.state = "CARRIED"; er.slot = -1; er.readyAt = 0;
        thief.eggs.add(er);
        showCarry(p, er);
        pl.training.applyWalkSpeed(p, thief);
        refreshPlot(victim.plot);
        p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1f, 1.4f);
        msg(p, "&aYou stole an egg from " + plot.ownerName + "'s base! Run to your base!");
        Player vp = Bukkit.getPlayer(victim.uuid);
        if (vp != null) msg(vp, "&c" + p.getName() + " stole an egg from your base!");
    }

    public void station(Player p, String data) {
        String[] sp = data.split(":");
        int plot = Integer.parseInt(sp[0]);
        String kind = sp[1];
        PlayerData pd = pl.players.get(p.getUniqueId());
        if (pd == null) return;
        if (kind.equals("SHOP")) { Menus.trails(pl, p, pd); return; }
        if (plots[plot].owner == null || !plots[plot].owner.equals(p.getUniqueId())) {
            p.sendActionBar(Util.c("&cThat's not your base!"));
            return;
        }
        switch (kind) {
            case "COLLECT" -> {
                double g = pd.pending;
                if (g < 1) { p.sendActionBar(Util.c("&7Nothing to collect yet.")); return; }
                pd.money += g; pd.pending = 0;
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
                p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, p.getLocation().add(0, 1, 0), 15, 0.4, 0.5, 0.4, 0);
                p.sendActionBar(Util.c("&a+$" + Util.fmt(g) + " &7(wallet: $" + Util.fmt(pd.money) + ")"));
                pl.db.save(pd);
            }
            case "TRAILS" -> Menus.trails(pl, p, pd);
            case "UPGRADES", "TREADMILL" -> Menus.upgrades(pl, p, pd);
            case "PETS" -> Menus.pets(pl, p, pd);
            case "LOCK" -> {
                pd.locked = !pd.locked;
                msg(p, pd.locked ? "&cYour base is now LOCKED to other players." : "&aYour base is now open (eggs can be stolen).");
                refreshPlot(pd.plot);
            }
            default -> {}
        }
    }

    public void buyTreadmill(Player p, PlayerData pd) {
        if (pd.treadmill + 1 >= GameData.TREADMILLS.size()) return;
        TreadmillDef n = GameData.TREADMILLS.get(pd.treadmill + 1);
        if (pd.money < n.cost()) { msg(p, "&cYou need $" + Util.fmt(n.cost()) + "."); return; }
        pd.money -= n.cost();
        pd.treadmill++;
        pl.builder.renderTreadmill(pd.plot, pd.treadmill, 0);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        msg(p, "&aTreadmill upgraded to &e" + n.name() + "&a!");
        pl.db.save(pd);
        refreshPlot(pd.plot);
    }

    // ------------------------------------------------------------------ hatching
    private Mutation rollMutation() {
        double r = rnd.nextDouble() * 100;
        if (r < 1) return Mutation.RAINBOW;
        if (r < 5) return Mutation.GOLDEN;
        if (r < 11) return Mutation.SILVER;
        return Mutation.NONE;
    }

    private void startHatch(Player p, PlayerData pd, EggRow e) {
        EggType t = GameData.EGGS.get(e.type);
        if (t == null) return;
        if (!pl.db.eggTransition(e.id, "INCUBATING", "HATCHING", pd.uuid, e.slot, e.readyAt)) return;
        e.state = "HATCHING";
        Plot plot = plots[pd.plot];
        ItemDisplay disp = plot.eggDisp[e.slot];
        Location at = disp != null ? disp.getLocation() : p.getLocation();
        float base = (float) (0.9 + 0.25 * e.size);
        BukkitRunnable r = new BukkitRunnable() {
            int t0 = 0;
            @Override public void run() {
                t0 += 2;
                if (!p.isOnline()) { cancel(); anims.remove(e.id); return; }
                if (disp != null && disp.isValid()) {
                    float ang = (float) Math.sin(t0 * 1.3) * 0.5f * (0.3f + t0 / 40f);
                    Ents.transform(disp, base * (1f + t0 / 80f), ang);
                }
                at.getWorld().spawnParticle(Particle.CRIT, at, 4, 0.3, 0.3, 0.3, 0.1);
                at.getWorld().playSound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 0.5f + t0 / 30f);
                if (t0 >= 40) { cancel(); anims.remove(e.id); finishHatch(p, pd, e, t, at); }
            }
        };
        anims.put(e.id, r);
        r.runTaskTimer(pl, 0, 2);
    }

    private void finishHatch(Player p, PlayerData pd, EggRow e, EggType t, Location at) {
        Mutation m = rollMutation();
        long id = pl.db.hatch(e.id, pd.uuid, e.type, m.name(), e.size);
        if (id < 0) return;
        pd.eggs.remove(e);
        PetRow pr = new PetRow();
        pr.id = id; pr.type = e.type; pr.mutation = m.name(); pr.size = e.size;
        pd.pets.add(pr);
        pd.hatched++;
        pd.speed += t.speed();
        pd.money += t.cash();
        pr.ent = spawnPet(pd, pr);
        pl.training.applyWalkSpeed(p, pd);
        World w = at.getWorld();
        w.spawnParticle(Particle.TOTEM_OF_UNDYING, at, 60, 0.5, 0.6, 0.5, 0.3);
        w.spawnParticle(Particle.FIREWORK, at, 30, 0.4, 0.5, 0.4, 0.1);
        w.playSound(at, Sound.ENTITY_CHICKEN_EGG, 1f, 1f);
        w.playSound(at, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        if (t.rarity().ordinal() >= Rarity.LEGENDARY.ordinal()) w.playSound(at, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        p.showTitle(Title.title(Util.c(t.rarity().color + "&l" + t.name() + (m != Mutation.NONE ? " " + m.color + "[" + m.label + "]" : "")),
                Util.c("&7" + t.rarity().label() + " &8| &a$" + Util.fmt(pr.income()) + "/s &8| &b+" + Util.fmt(t.speed()) + " Speed &8| &6+$" + Util.fmt(t.cash())),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2500), Duration.ofMillis(500))));
        if (t.rarity().ordinal() >= Rarity.EPIC.ordinal())
            Bukkit.broadcast(Util.c("&e" + p.getName() + " &7hatched a " + t.rarity().color + t.name() + (m != Mutation.NONE ? " " + m.color + m.label : "") + "&7!"));
        pl.db.save(pd);
        refreshPlot(pd.plot);
    }
}
