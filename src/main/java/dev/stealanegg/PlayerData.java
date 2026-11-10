package dev.stealanegg;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

public final class PlayerData {
    public final UUID id;
    public String name;
    public double cash;
    public double speedPoints;
    public Trail trail = Trail.NONE;
    public final EnumSet<Trail> ownedTrails = EnumSet.of(Trail.NONE);
    public final EnumSet<EggType> discovered = EnumSet.noneOf(EggType.class);
    public final EggType[] slots = new EggType[Layout.SLOTS];
    public int stolen;
    public boolean knockStick;

    public PlayerData(UUID id, String name) {
        this.id = id;
        this.name = name;
    }

    public int eggCount() {
        int n = 0;
        for (EggType t : slots) if (t != null) n++;
        return n;
    }

    public void write(ConfigurationSection s) {
        s.set("name", name);
        s.set("cash", cash);
        s.set("speed-points", speedPoints);
        s.set("trail", trail.name());
        s.set("stolen", stolen);
        s.set("knock-stick", knockStick);
        List<String> owned = new ArrayList<>();
        for (Trail t : ownedTrails) owned.add(t.name());
        s.set("owned-trails", owned);
        List<String> disc = new ArrayList<>();
        for (EggType t : discovered) disc.add(t.name());
        s.set("discovered", disc);
        List<String> sl = new ArrayList<>();
        for (EggType t : slots) sl.add(t == null ? "-" : t.name());
        s.set("slots", sl);
    }

    public static PlayerData read(UUID id, ConfigurationSection s) {
        PlayerData d = new PlayerData(id, s.getString("name", "?"));
        d.cash = s.getDouble("cash");
        d.speedPoints = s.getDouble("speed-points");
        d.stolen = s.getInt("stolen");
        d.knockStick = s.getBoolean("knock-stick");
        try {
            d.trail = Trail.valueOf(s.getString("trail", "NONE"));
        } catch (IllegalArgumentException ignored) {
        }
        for (String n : s.getStringList("owned-trails")) {
            try {
                d.ownedTrails.add(Trail.valueOf(n));
            } catch (IllegalArgumentException ignored) {
            }
        }
        d.ownedTrails.add(Trail.NONE);
        for (String n : s.getStringList("discovered")) {
            try {
                d.discovered.add(EggType.valueOf(n));
            } catch (IllegalArgumentException ignored) {
            }
        }
        List<String> sl = s.getStringList("slots");
        for (int i = 0; i < Layout.SLOTS && i < sl.size(); i++) {
            try {
                d.slots[i] = sl.get(i).equals("-") ? null : EggType.valueOf(sl.get(i));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return d;
    }
}
