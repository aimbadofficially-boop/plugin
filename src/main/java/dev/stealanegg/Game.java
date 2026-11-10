package dev.stealanegg;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Runtime game state + all the tick loops. */
public final class Game {
    public record Carry(EggType type, UUID from) {}

    private static final class ConvEgg {
        ArmorStand stand;
        EggType type;
        double x;
    }

    private static final int F = Layout.FLOOR;

    private final StealAnEgg pl;
    private final World world;

    public final NamespacedKey KIND, OWNER, SLOT, EGG, STICK;

    private final UUID[] baseOwner = new UUID[Layout.BASE_COUNT];
    private final Map<UUID, Integer> baseOf = new HashMap<>();
    private final Map<UUID, ArmorStand[]> slotStands = new HashMap<>();
    private final Map<UUID, Carry> carrying = new HashMap<>();
    private final Map<UUID, Long> shield = new HashMap<>();
    private final Map<UUID, Long> clickCd = new HashMap<>();
    private final Map<UUID, Long> knockCd = new HashMap<>();
    private final Map<UUID, Location> lastLoc = new HashMap<>();
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private final Set<UUID> moving = new HashSet<>();
    private final List<ConvEgg> conv = new ArrayList<>();

    private int spawnTimer;
    private int fastCount;
    private int secondCount;

    public Game(StealAnEgg pl) {
        this.pl = pl;
        this.world = pl.world;
        KIND = new NamespacedKey(pl, "kind");
        OWNER = new NamespacedKey(pl, "owner");
        SLOT = new NamespacedKey(pl, "slot");
        EGG = new NamespacedKey(pl, "egg");
        STICK = new NamespacedKey(pl, "knock_stick");
    }

    // ================================================================== lifecycle

    public void start() {
        Bukkit.getScheduler().runTaskTimer(pl, this::fastTick, 2L, 2L);
        Bukkit.getScheduler().runTaskTimer(pl, this::secondTick, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(pl, this::trailTick, 4L, 4L);
    }

    public void shutdown() {
        for (ConvEgg c : conv) if (c.stand != null) c.stand.remove();
        conv.clear();
        for (ArmorStand[] arr : slotStands.values()) for (ArmorStand s : arr) if (s != null) s.remove();
        slotStands.clear();
        for (UUID id : new ArrayList<>(carrying.keySet())) returnCarry(id, true);
    }

    public Location spawn() {
        return new Location(world, 0.5, F + 1, 4.5, 0f, 0f);
    }

    public static boolean isBedrock(Player p) {
        // Floodgate gives Bedrock players UUIDs with a zero most-significant half
        return p.getUniqueId().getMostSignificantBits() == 0L;
    }

    // ================================================================== join / quit

    public void join(Player p) {
        PlayerData d = pl.data.getOrCreate(p);
        p.setGameMode(GameMode.ADVENTURE);
        p.getInventory().clear();
        p.setHealth(20);
        p.setFoodLevel(20);
        p.setFireTicks(0);
        for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
        if (d.knockStick) p.getInventory().setItem(0, knockStickItem());

        assignBase(p);
        renderBase(p.getUniqueId());
        applySpeed(p, d);
        createBoard(p);
        p.teleport(spawn());

        Msg.send(p, "&6&lSTEAL AN EGG &7- buy eggs from the conveyor, defend them, and steal from others!");
        if (isBedrock(p)) {
            Msg.send(p, "&7Bedrock tip: &etap&7 eggs and shop signs. Use &e/base&7 to go home.");
        } else {
            Msg.send(p, "&7Right-click eggs and shop signs. Use &e/base&7 to go home.");
        }
        if (baseOf.get(p.getUniqueId()) == null) {
            Msg.send(p, "&cAll bases are taken right now - you can run the gym until one frees up.");
        }
    }

    public void quit(Player p) {
        UUID id = p.getUniqueId();
        returnCarry(id, true);
        clearBase(id);
        Integer idx = baseOf.remove(id);
        if (idx != null) {
            baseOwner[idx] = null;
            setBaseSign(idx, null);
        }
        boards.remove(id);
        lastLoc.remove(id);
        moving.remove(id);
        shield.remove(id);
        p.setWalkSpeed(0.2f);
        pl.data.save();
    }

    // ================================================================== bases

    private void assignBase(Player p) {
        UUID id = p.getUniqueId();
        if (baseOf.containsKey(id)) return;
        for (int i = 0; i < Layout.BASE_COUNT; i++) {
            if (baseOwner[i] == null) {
                baseOwner[i] = id;
                baseOf.put(id, i);
                setBaseSign(i, p.getName());
                return;
            }
        }
    }

    private void setBaseSign(int idx, String owner) {
        Block b = world.getBlockAt(Layout.baseCx(idx), F + 2, Layout.baseCz(idx) - 7);
        String title = "&l" + Layout.BASE_NAMES[idx % Layout.BASE_NAMES.length];
        if (owner == null) Signs.write(b, title, "&7Free base");
        else Signs.write(b, title, "&a" + owner);
    }

    public Location baseSpawn(Player p) {
        Integer idx = baseOf.get(p.getUniqueId());
        if (idx == null) return null;
        return new Location(world, Layout.baseCx(idx) + 0.5, F + 1, Layout.baseCz(idx) + 4.5, 180f, 0f);
    }

    private boolean inBase(Location l, int idx) {
        return Math.abs(l.getX() - (Layout.baseCx(idx) + 0.5)) <= 7.5
                && Math.abs(l.getZ() - (Layout.baseCz(idx) + 0.5)) <= 7.5;
    }

    private void clearBase(UUID id) {
        ArmorStand[] arr = slotStands.remove(id);
        if (arr == null) return;
        for (ArmorStand s : arr) if (s != null) s.remove();
    }

    private void renderBase(UUID id) {
        clearBase(id);
        Integer idx = baseOf.get(id);
        PlayerData d = pl.data.get(id);
        if (idx == null || d == null) return;
        ArmorStand[] arr = new ArmorStand[Layout.SLOTS];
        for (int i = 0; i < Layout.SLOTS; i++) {
            if (d.slots[i] != null) arr[i] = spawnSlotStand(idx, i, d.slots[i], id);
        }
        slotStands.put(id, arr);
    }

    private ArmorStand spawnSlotStand(int baseIdx, int slot, EggType t, UUID owner) {
        Location loc = new Location(world, Layout.slotX(Layout.baseCx(baseIdx), slot) + 0.5, F + 1.2,
                Layout.slotZ(Layout.baseCz(baseIdx), slot) + 0.5);
        String name = t.colored() + " &7(+$" + Msg.fmt(t.income) + "/s)";
        return spawnEggStand(loc, t, name, "slot", owner, slot);
    }

    private ArmorStand spawnEggStand(Location loc, EggType t, String name, String kind, UUID owner, int slot) {
        return world.spawn(loc, ArmorStand.class, as -> {
            as.setInvisible(true);
            as.setSmall(true);
            as.setGravity(false);
            as.setBasePlate(false);
            as.setArms(false);
            as.setSilent(true);
            as.setPersistent(false);
            as.setCanPickupItems(false);
            as.customName(Msg.c(name));
            as.setCustomNameVisible(true);
            as.getEquipment().setHelmet(new ItemStack(t.material));
            var pdc = as.getPersistentDataContainer();
            pdc.set(KIND, PersistentDataType.STRING, kind);
            pdc.set(EGG, PersistentDataType.STRING, t.name());
            pdc.set(SLOT, PersistentDataType.INTEGER, slot);
            if (owner != null) pdc.set(OWNER, PersistentDataType.STRING, owner.toString());
        });
    }

    public boolean isOurStand(Entity e) {
        return e instanceof ArmorStand a && a.getPersistentDataContainer().has(KIND, PersistentDataType.STRING);
    }

    private int freeSlot(PlayerData d) {
        for (int i = 0; i < Layout.SLOTS; i++) if (d.slots[i] == null) return i;
        return -1;
    }

    private void placeEgg(PlayerData d, int slot, EggType t) {
        d.slots[slot] = t;
        d.discovered.add(t);
        Integer idx = baseOf.get(d.id);
        if (idx == null) return;
        ArmorStand[] arr = slotStands.computeIfAbsent(d.id, k -> new ArmorStand[Layout.SLOTS]);
        if (arr[slot] != null) arr[slot].remove();
        arr[slot] = spawnSlotStand(idx, slot, t, d.id);
    }

    // ================================================================== clicks (buy / steal)

    /** Called when a player right-clicks or punches one of our egg stands. */
    public void clickStand(Player p, ArmorStand as) {
        long now = System.currentTimeMillis();
        Long last = clickCd.get(p.getUniqueId());
        if (last != null && now - last < 300) return;
        clickCd.put(p.getUniqueId(), now);

        var pdc = as.getPersistentDataContainer();
        String kind = pdc.get(KIND, PersistentDataType.STRING);
        if (kind == null) return;
        if (kind.equals("conv")) buyConveyorEgg(p, as);
        else if (kind.equals("slot")) clickSlotEgg(p, as);
    }

    private void buyConveyorEgg(Player p, ArmorStand as) {
        ConvEgg ce = null;
        for (ConvEgg c : conv) if (c.stand == as) ce = c;
        if (ce == null) return;
        PlayerData d = pl.data.get(p.getUniqueId());
        if (d == null) return;
        if (!baseOf.containsKey(p.getUniqueId())) {
            Msg.send(p, "&cYou don't have a base yet.");
            return;
        }
        int slot = freeSlot(d);
        if (slot < 0) {
            Msg.send(p, "&cYour base is full (" + Layout.SLOTS + " eggs).");
            return;
        }
        if (d.cash < ce.type.cost) {
            Msg.send(p, "&cYou need &6$" + Msg.fmt(ce.type.cost) + "&c for that egg. You have &6$" + Msg.fmt(d.cash));
            return;
        }
        d.cash -= ce.type.cost;
        boolean first = !d.discovered.contains(ce.type);
        placeEgg(d, slot, ce.type);
        ce.stand.remove();
        conv.remove(ce);
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
        Msg.send(p, "&aBought " + ce.type.colored() + "&a for &6$" + Msg.fmt(ce.type.cost) + "&a!"
                + (first ? " &e&lNEW EGG - Index bonus up!" : ""));
    }

    private void clickSlotEgg(Player p, ArmorStand as) {
        var pdc = as.getPersistentDataContainer();
        String ownerStr = pdc.get(OWNER, PersistentDataType.STRING);
        Integer slot = pdc.get(SLOT, PersistentDataType.INTEGER);
        if (ownerStr == null || slot == null) return;
        UUID ownerId = UUID.fromString(ownerStr);

        if (ownerId.equals(p.getUniqueId())) {
            Msg.send(p, "&7That's your own egg. Other players will try to steal it - keep an eye on it!");
            return;
        }
        if (carrying.containsKey(p.getUniqueId())) {
            Msg.send(p, "&cYou're already carrying an egg - take it to your base first!");
            return;
        }
        PlayerData victim = pl.data.get(ownerId);
        if (victim == null || victim.slots[slot] == null) return;
        if (!baseOf.containsKey(p.getUniqueId())) {
            Msg.send(p, "&cYou need a base to bring eggs back to.");
            return;
        }
        if (p.getLocation().distance(as.getLocation()) > 5) {
            Msg.send(p, "&cGet closer.");
            return;
        }
        Long sh = shield.get(ownerId);
        if (sh != null && sh > System.currentTimeMillis()) {
            Msg.send(p, "&9That base is shielded for " + ((sh - System.currentTimeMillis()) / 1000 + 1) + "s!");
            return;
        }

        EggType t = victim.slots[slot];
        victim.slots[slot] = null;
        ArmorStand[] arr = slotStands.get(ownerId);
        if (arr != null) arr[slot] = null;
        as.remove();

        carrying.put(p.getUniqueId(), new Carry(t, ownerId));
        p.getInventory().setHelmet(new ItemStack(t.material));
        p.setGlowing(true);
        p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.5f);
        Msg.send(p, "&c&lYou stole " + t.colored() + "&c&l! &7Run back to your base before someone knocks it loose!");

        Player vp = Bukkit.getPlayer(ownerId);
        if (vp != null) {
            Msg.send(vp, "&c&l" + p.getName() + " stole your " + t.colored() + "&c&l! &7Knock them with a stick to get it back.");
            vp.playSound(vp.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 0.8f);
        }
    }

    public void returnCarry(UUID carrierId, boolean toOwner) {
        Carry c = carrying.remove(carrierId);
        if (c == null) return;
        Player cp = Bukkit.getPlayer(carrierId);
        if (cp != null) {
            cp.getInventory().setHelmet(null);
            cp.setGlowing(false);
        }
        if (!toOwner) return;
        PlayerData od = pl.data.get(c.from());
        if (od == null) return;
        int s = freeSlot(od);
        if (s < 0) return; // owner filled the gap meanwhile - egg is lost
        placeEgg(od, s, c.type());
        Player op = Bukkit.getPlayer(c.from());
        if (op != null) Msg.send(op, "&aYour " + c.type().colored() + "&a was returned to your base!");
    }

    public boolean isCarrying(Player p) {
        return carrying.containsKey(p.getUniqueId());
    }

    // ================================================================== PvP items

    public ItemStack knockStickItem() {
        ItemStack it = new ItemStack(Material.STICK);
        ItemMeta m = it.getItemMeta();
        m.displayName(Msg.c("&c&lKnock Stick"));
        m.lore(List.of(Msg.c("&7Hit a player carrying an egg"), Msg.c("&7to knock it loose!")));
        m.setEnchantmentGlintOverride(true);
        m.getPersistentDataContainer().set(STICK, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    public boolean isKnockStick(ItemStack it) {
        return it != null && it.getType() == Material.STICK && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(STICK, PersistentDataType.BYTE);
    }

    public void knock(Player attacker, Player victim) {
        long now = System.currentTimeMillis();
        Long last = knockCd.get(attacker.getUniqueId());
        if (last != null && now - last < 1200) return;
        knockCd.put(attacker.getUniqueId(), now);
        if (Layout.isSafe(victim.getLocation())) {
            Msg.bar(attacker, "&aThat player is in the safe zone.");
            return;
        }
        Vector dir = victim.getLocation().toVector().subtract(attacker.getLocation().toVector());
        dir.setY(0);
        if (dir.lengthSquared() < 0.0001) dir = new Vector(0, 0, 1);
        victim.setVelocity(dir.normalize().multiply(1.1).setY(0.45));
        victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1f, 1f);
        if (isCarrying(victim)) {
            Carry c = carrying.get(victim.getUniqueId());
            returnCarry(victim.getUniqueId(), true);
            Msg.send(victim, "&c" + attacker.getName() + " knocked your egg loose!");
            Msg.send(attacker, "&aYou knocked the " + c.type().colored() + "&a loose - it went back to its owner!");
        }
    }

    public void freeze(Player victim) {
        if (Layout.isSafe(victim.getLocation())) return;
        victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 3, false, true));
        Msg.bar(victim, "&b&lFROZEN!");
    }

    public void activateShield(Player p, PlayerData d, int seconds) {
        shield.put(p.getUniqueId(), System.currentTimeMillis() + seconds * 1000L);
        Msg.send(p, "&9Shield active for " + seconds + "s - nobody can steal from your base.");
    }

    public boolean charge(Player p, PlayerData d, double price) {
        if (d.cash < price) {
            Msg.send(p, "&cYou need &6$" + Msg.fmt(price) + "&c. You have &6$" + Msg.fmt(d.cash));
            return false;
        }
        d.cash -= price;
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
        return true;
    }

    // ================================================================== speed / income

    public double speedMultiplier(PlayerData d) {
        double per = pl.getConfig().getDouble("speed.per-point", 0.0005);
        double max = pl.getConfig().getDouble("speed.max-multiplier", 3.0);
        return Math.min(max, 1.0 + d.speedPoints * per + d.trail.bonus);
    }

    public void applySpeed(Player p, PlayerData d) {
        p.setWalkSpeed((float) Math.min(1.0, 0.2 * speedMultiplier(d)));
    }

    public double income(PlayerData d) {
        double sum = 0;
        for (EggType t : d.slots) if (t != null) sum += t.income;
        double bonus = pl.getConfig().getDouble("income.index-bonus-per-egg-type", 0.05);
        return sum * (1.0 + d.discovered.size() * bonus);
    }

    // ================================================================== ticking

    private void fastTick() {
        if (world.getPlayers().isEmpty()) return;
        fastCount++;

        tickConveyor();

        List<Double> gains = pl.getConfig().getDoubleList("treadmills.gain-per-second");
        List<Double> reqs = pl.getConfig().getDoubleList("treadmills.required-points");
        List<String> names = pl.getConfig().getStringList("treadmills.names");
        double push = pl.getConfig().getDouble("treadmills.push", 0.045);

        for (Player p : world.getPlayers()) {
            UUID id = p.getUniqueId();
            PlayerData d = pl.data.get(id);
            if (d == null) continue;
            Location l = p.getLocation();

            if (l.getY() < 40) {
                p.teleport(spawn());
                continue;
            }

            Location prev = lastLoc.put(id, l.clone());
            double moved = 0;
            if (prev != null && prev.getWorld() == l.getWorld()) {
                moved = Math.hypot(l.getX() - prev.getX(), l.getZ() - prev.getZ());
            }
            if (moved >= 0.1) moving.add(id);
            else moving.remove(id);

            // deliver a stolen egg to your own base
            Carry c = carrying.get(id);
            Integer bi = baseOf.get(id);
            if (c != null && bi != null && inBase(l, bi)) {
                int s = freeSlot(d);
                if (s >= 0) {
                    carrying.remove(id);
                    p.getInventory().setHelmet(null);
                    p.setGlowing(false);
                    placeEgg(d, s, c.type());
                    d.stolen++;
                    p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                    Msg.send(p, "&a&lSTOLEN! &7" + c.type().colored() + "&7 is now yours.");
                } else if (fastCount % 10 == 0) {
                    Msg.bar(p, "&cYour base is full!");
                }
            }

            // treadmills
            int lane = Layout.treadmillAt(l);
            if (lane < 0) continue;
            double req = lane < reqs.size() ? reqs.get(lane) : 0;
            String name = lane < names.size() ? names.get(lane) : "Treadmill";
            if (d.speedPoints < req) {
                if (fastCount % 10 == 0) Msg.bar(p, "&c" + name + " needs &e" + Msg.fmt(req) + "&c speed points (you have &e" + Msg.fmt(d.speedPoints) + "&c)");
                continue;
            }
            if (p.isOnGround()) {
                p.setVelocity(new Vector(0, 0, -push));
            }
            if (moved >= 0.15) {
                double gain = lane < gains.size() ? gains.get(lane) : 1;
                d.speedPoints += gain * 0.1; // this task runs every 0.1s
                if (fastCount % 5 == 0) {
                    applySpeed(p, d);
                    Msg.bar(p, "&b" + name + " &7| &a+" + Msg.fmt(gain) + " speed/s &7| &eSpeed &fx" + String.format("%.2f", speedMultiplier(d))
                            + " &7| &fPoints: " + Msg.fmt(d.speedPoints));
                }
            } else if (fastCount % 10 == 0) {
                Msg.bar(p, "&7Run &aforward&7 on the " + name + " treadmill!");
            }
        }
    }

    private void tickConveyor() {
        double speed = pl.getConfig().getDouble("conveyor.speed-blocks-per-second", 2.0);
        int interval = Math.max(20, pl.getConfig().getInt("conveyor.spawn-interval-ticks", 60));

        spawnTimer += 2;
        if (spawnTimer >= interval) {
            spawnTimer = 0;
            ConvEgg ce = new ConvEgg();
            ce.type = EggType.roll();
            ce.x = Layout.CONV_X1 + 0.5;
            String name = ce.type.colored() + " &7- &6$" + Msg.fmt(ce.type.cost);
            ce.stand = spawnEggStand(convLoc(ce.x), ce.type, name, "conv", null, 0);
            conv.add(ce);
        }

        var it = conv.iterator();
        while (it.hasNext()) {
            ConvEgg ce = it.next();
            ce.x += speed * 0.1;
            if (ce.x > Layout.CONV_X2 + 0.5 || ce.stand == null || !ce.stand.isValid()) {
                if (ce.stand != null) ce.stand.remove();
                it.remove();
            } else {
                ce.stand.teleport(convLoc(ce.x));
            }
        }
    }

    private Location convLoc(double x) {
        return new Location(world, x, F + 0.75, Layout.CONV_EGG_Z);
    }

    private void secondTick() {
        secondCount++;
        if (secondCount % 5 == 0) {
            world.setTime(6000);
            if (world.hasStorm()) world.setStorm(false);
        }
        for (Player p : world.getPlayers()) {
            PlayerData d = pl.data.get(p.getUniqueId());
            if (d == null) continue;
            d.cash += income(d);
            applySpeed(p, d);
            updateBoard(p, d);
        }
    }

    private void trailTick() {
        for (Player p : world.getPlayers()) {
            PlayerData d = pl.data.get(p.getUniqueId());
            if (d == null || d.trail.particle == null || !moving.contains(p.getUniqueId())) continue;
            world.spawnParticle(d.trail.particle, p.getLocation().add(0, 0.15, 0), 3, 0.2, 0.05, 0.2, 0.01);
        }
    }

    // ================================================================== scoreboard

    private void createBoard(Player p) {
        Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective o = sb.registerNewObjective("sae", Criteria.DUMMY, Msg.c("&e&lSTEAL AN EGG"));
        o.setDisplaySlot(DisplaySlot.SIDEBAR);
        boards.put(p.getUniqueId(), sb);
        p.setScoreboard(sb);
        PlayerData d = pl.data.get(p.getUniqueId());
        if (d != null) updateBoard(p, d);
    }

    private void updateBoard(Player p, PlayerData d) {
        Scoreboard sb = boards.get(p.getUniqueId());
        if (sb == null) return;
        Objective o = sb.getObjective("sae");
        if (o == null) return;
        for (String e : new ArrayList<>(sb.getEntries())) sb.resetScores(e);
        String[] lines = {
                "&r",
                "&aCash: &f$" + Msg.fmt(d.cash),
                "&eIncome: &f$" + Msg.fmt(income(d)) + "/s",
                "&bEggs: &f" + d.eggCount() + "/" + Layout.SLOTS,
                "&dSpeed: &fx" + String.format("%.2f", speedMultiplier(d)),
                "&cTrail: &f" + d.trail.display,
                "&6Index: &f" + d.discovered.size() + "/" + EggType.values().length,
                "&7Stolen: &f" + d.stolen,
                "&r&r"
        };
        for (int i = 0; i < lines.length; i++) {
            o.getScore(Msg.sec(lines[i])).setScore(lines.length - i);
        }
    }

    // ================================================================== misc

    public Layout.Stall stallOf(Block b) {
        if (b.getWorld() != world) return null;
        if (b.getY() != F + 1 && b.getY() != F + 2) return null;
        for (Layout.Stall s : List.of(Layout.PVP, Layout.TRAIL, Layout.INDEX)) {
            for (int lx = -3; lx <= 3; lx++) {
                if (b.getX() == s.wx(lx, 1) && b.getZ() == s.wz(lx, 1)) return s;
            }
        }
        return null;
    }

    public void teleportToBase(Player p) {
        Location l = baseSpawn(p);
        if (l == null) Msg.send(p, "&cYou don't have a base.");
        else p.teleport(l);
    }

    public void giveKnockStick(Player p) {
        p.getInventory().addItem(knockStickItem());
    }
}
