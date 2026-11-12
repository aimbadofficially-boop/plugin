package dev.stealanegg;

import dev.stealanegg.GameData.*;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public final class Commands implements CommandExecutor, TabCompleter {
    private final StealAnEgg pl;

    public Commands(StealAnEgg pl) { this.pl = pl; }

    private void say(CommandSender s, String m) { s.sendMessage(Util.c(m)); }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!pl.ready) { say(s, "&cThe game is still starting up."); return true; }
        String sub = a.length == 0 ? "help" : a[0].toLowerCase(Locale.ROOT);
        boolean admin = s.hasPermission("stealanegg.admin");
        try {
            switch (sub) {
                case "help" -> help(s, admin);
                case "home" -> {
                    Player p = player(s); PlayerData pd = pl.players.get(p.getUniqueId());
                    p.teleport(pl.bases.respawnLoc(pd));
                }
                case "spawn" -> player(s).teleport(Layout.spawn(pl.world));
                case "stats" -> {
                    Player p = player(s); PlayerData pd = pl.players.get(p.getUniqueId());
                    say(s, "&6Money &a$" + Util.fmt(pd.money) + " &7| &6Pending &a$" + Util.fmt(pd.pending) + " &7| &6Speed &b" + Util.fmt(pd.speed)
                            + " &7| &6Income &a$" + Util.fmt(pd.income()) + "/s &7| &6Pets &f" + pd.pets.size() + "/" + pl.bases.petCap(pd));
                }
                case "collect" -> {
                    Player p = player(s); PlayerData pd = pl.players.get(p.getUniqueId());
                    pd.money += pd.pending; say(s, "&aCollected $" + Util.fmt(pd.pending)); pd.pending = 0;
                }
                default -> {
                    if (!admin) { say(s, "&cYou don't have permission."); return true; }
                    adminCmd(s, sub, a);
                }
            }
        } catch (IllegalStateException | NumberFormatException ex) {
            say(s, "&c" + (ex instanceof NumberFormatException ? "Invalid number." : ex.getMessage()));
        } catch (ArrayIndexOutOfBoundsException ex) {
            say(s, "&cMissing arguments. See /sae help");
        }
        return true;
    }

    private Player player(CommandSender s) {
        if (s instanceof Player p && pl.players.containsKey(p.getUniqueId())) return p;
        throw new IllegalStateException("Players only.");
    }

    private Player target(String name) {
        Player p = Bukkit.getPlayerExact(name);
        if (p == null || !pl.players.containsKey(p.getUniqueId())) throw new IllegalStateException("Player not online: " + name);
        return p;
    }

    private void help(CommandSender s, boolean admin) {
        say(s, "&6&lSteal An Egg &7- commands");
        say(s, "&e/sae home &7- go to your base   &e/sae spawn &7- hub   &e/sae stats   &e/sae collect");
        if (!admin) return;
        say(s, "&c&lAdmin:");
        say(s, "&e/sae give <player> money|speed <amt> &7(1.5K, 2M, 3B, 4T...)");
        say(s, "&e/sae give <player> egg <eggId> [size]   &e/sae give <player> pet <eggId> [SILVER|GOLDEN|RAINBOW]");
        say(s, "&e/sae set <player> money|speed|treadmill|penlevel|slotlevel <n>");
        say(s, "&e/sae trail <player> give|remove <trailId|all>   &e/sae reset <player>   &e/sae info <player>");
        say(s, "&e/sae plot <player> <1-12|none>   &e/sae tp <biome>   &e/sae bypass   &e/sae resetnests");
        say(s, "&e/sae eggs [biome]   &e/sae rebuild confirm   &e/sae reload");
    }

    private void adminCmd(CommandSender s, String sub, String[] a) {
        switch (sub) {
            case "give" -> {
                Player p = target(a[1]); PlayerData pd = pl.players.get(p.getUniqueId());
                switch (a[2].toLowerCase(Locale.ROOT)) {
                    case "money" -> { pd.money += Util.parse(a[3]); say(s, "&aGave money."); }
                    case "speed" -> { pd.speed += Util.parse(a[3]); pl.training.applyWalkSpeed(p, pd); say(s, "&aGave speed."); }
                    case "egg" -> {
                        EggType t = GameData.EGGS.get(a[3].toLowerCase(Locale.ROOT));
                        if (t == null) throw new IllegalStateException("Unknown egg id. Use /sae eggs");
                        double size = a.length > 4 ? Double.parseDouble(a[4]) : 1.0;
                        if (pl.bases.grantEgg(p, pd, t, size) == null) throw new IllegalStateException("That player is already carrying an egg.");
                        say(s, "&aGave " + t.name() + " egg (carried).");
                    }
                    case "pet" -> {
                        EggType t = GameData.EGGS.get(a[3].toLowerCase(Locale.ROOT));
                        if (t == null) throw new IllegalStateException("Unknown egg id. Use /sae eggs");
                        String mut = a.length > 4 ? a[4].toUpperCase(Locale.ROOT) : "NONE";
                        if (pl.bases.grantPet(p, pd, t.id(), GameData.mut(mut).name(), 1.0) == null) throw new IllegalStateException("Pet pen is full.");
                        say(s, "&aGave pet.");
                    }
                    default -> throw new IllegalStateException("give money|speed|egg|pet");
                }
                pl.db.save(pd);
            }
            case "set" -> {
                Player p = target(a[1]); PlayerData pd = pl.players.get(p.getUniqueId());
                double v = Util.parse(a[3]);
                switch (a[2].toLowerCase(Locale.ROOT)) {
                    case "money" -> pd.money = v;
                    case "speed" -> { pd.speed = v; pl.training.applyWalkSpeed(p, pd); }
                    case "treadmill" -> {
                        pd.treadmill = (int) Math.max(0, Math.min(GameData.TREADMILLS.size() - 1, v));
                        if (pd.plot >= 0) pl.builder.renderTreadmill(pd.plot, pd.treadmill, 0);
                    }
                    case "penlevel" -> pd.penLevel = (int) v;
                    case "slotlevel" -> pd.slotLevel = (int) v;
                    default -> throw new IllegalStateException("set money|speed|treadmill|penlevel|slotlevel");
                }
                pl.db.save(pd);
                if (pd.plot >= 0) pl.bases.refreshPlot(pd.plot);
                say(s, "&aUpdated.");
            }
            case "trail" -> {
                Player p = target(a[1]); PlayerData pd = pl.players.get(p.getUniqueId());
                boolean give = a[2].equalsIgnoreCase("give");
                if (a[3].equalsIgnoreCase("all")) { if (give) GameData.TRAILS.forEach(t -> pd.trails.add(t.id())); else { pd.trails.clear(); pd.trail = ""; } }
                else {
                    TrailDef t = GameData.trail(a[3].toLowerCase(Locale.ROOT));
                    if (t == null) throw new IllegalStateException("Unknown trail.");
                    if (give) pd.trails.add(t.id()); else { pd.trails.remove(t.id()); if (t.id().equals(pd.trail)) pd.trail = ""; }
                }
                pl.db.save(pd);
                say(s, "&aTrails updated.");
            }
            case "reset" -> {
                Player p = target(a[1]); PlayerData pd = pl.players.get(p.getUniqueId());
                pl.bases.quit(p);
                pl.db.wipe(p.getUniqueId());
                pl.bases.join(p);
                say(s, "&aWiped " + p.getName() + ".");
            }
            case "info" -> {
                Player p = target(a[1]); PlayerData pd = pl.players.get(p.getUniqueId());
                say(s, "&6" + pd.name + " &7plot=" + (pd.plot + 1) + " money=$" + Util.fmt(pd.money) + " pending=$" + Util.fmt(pd.pending)
                        + " speed=" + Util.fmt(pd.speed) + " pets=" + pd.pets.size() + " eggs=" + pd.eggs.size() + " treadmill=" + pd.treadmill
                        + " trail=" + pd.trail + " hatched=" + pd.hatched);
            }
            case "plot" -> {
                Player p = target(a[1]); PlayerData pd = pl.players.get(p.getUniqueId());
                int n = a[2].equalsIgnoreCase("none") ? -1 : Integer.parseInt(a[2]) - 1;
                if (n >= Layout.PLOTS) throw new IllegalStateException("Plots are 1-" + Layout.PLOTS);
                pl.bases.quit(p);
                PlayerData fresh = pl.db.load(p.getUniqueId());
                if (fresh != null) { fresh.plot = n; pl.db.save(fresh); }
                pl.bases.join(p);
                say(s, "&aPlot changed.");
            }
            case "tp" -> {
                Player p = player(s);
                for (BiomeDef b : GameData.BIOMES)
                    if (b.id.equalsIgnoreCase(a[1])) { p.teleport(new org.bukkit.Location(pl.world, 0.5, Layout.GY + 1, Layout.gateZ(b.index) + 6.5)); return; }
                throw new IllegalStateException("Biomes: " + GameData.BIOMES.stream().map(b -> b.id).collect(Collectors.joining(", ")));
            }
            case "bypass" -> {
                Player p = player(s);
                if (!pl.bypass.remove(p.getUniqueId())) { pl.bypass.add(p.getUniqueId()); say(s, "&aSpeed gates bypass ON."); }
                else say(s, "&cSpeed gates bypass OFF.");
            }
            case "resetnests" -> { pl.nests.resetAll(); say(s, "&aNests reset."); }
            case "eggs" -> {
                for (BiomeDef b : GameData.BIOMES) {
                    if (a.length > 1 && !b.id.equalsIgnoreCase(a[1])) continue;
                    say(s, "&6" + b.name + " &7(speed " + Util.fmt(b.speed) + "): " + b.eggs.stream().map(e -> e.rarity().color + e.id()).collect(Collectors.joining("&7, ")));
                }
            }
            case "rebuild" -> {
                if (a.length < 2 || !a[1].equalsIgnoreCase("confirm")) throw new IllegalStateException("This rebuilds the whole map. Run /sae rebuild confirm");
                say(s, "&eRebuilding map...");
                pl.builder.buildAll();
                say(s, "&aDone. Restart the server to respawn all station entities cleanly.");
            }
            case "reload" -> {
                pl.reloadConfig();
                GameData.load(pl.getConfig());
                pl.nests.resetAll();
                say(s, "&aConfig reloaded.");
            }
            default -> say(s, "&cUnknown subcommand. /sae help");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        List<String> out = new ArrayList<>();
        boolean admin = s.hasPermission("stealanegg.admin");
        if (a.length == 1) {
            out.addAll(List.of("help", "home", "spawn", "stats", "collect"));
            if (admin) out.addAll(List.of("give", "set", "trail", "reset", "info", "plot", "tp", "bypass", "resetnests", "eggs", "rebuild", "reload"));
        } else if (admin) {
            String sub = a[0].toLowerCase(Locale.ROOT);
            if (a.length == 2 && List.of("give", "set", "trail", "reset", "info", "plot").contains(sub))
                Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
            else if (a.length == 2 && (sub.equals("tp") || sub.equals("eggs"))) GameData.BIOMES.forEach(b -> out.add(b.id));
            else if (sub.equals("give") && a.length == 3) out.addAll(List.of("money", "speed", "egg", "pet"));
            else if (sub.equals("give") && a.length == 4 && (a[2].equals("egg") || a[2].equals("pet"))) out.addAll(GameData.EGGS.keySet());
            else if (sub.equals("give") && a.length == 5 && a[2].equals("pet")) out.addAll(List.of("SILVER", "GOLDEN", "RAINBOW"));
            else if (sub.equals("set") && a.length == 3) out.addAll(List.of("money", "speed", "treadmill", "penlevel", "slotlevel"));
            else if (sub.equals("trail") && a.length == 3) out.addAll(List.of("give", "remove"));
            else if (sub.equals("trail") && a.length == 4) { out.add("all"); GameData.TRAILS.forEach(t -> out.add(t.id())); }
        }
        String last = a[a.length - 1].toLowerCase(Locale.ROOT);
        return out.stream().filter(x -> x.toLowerCase(Locale.ROOT).startsWith(last)).collect(Collectors.toList());
    }
}
