package dev.stealanegg;

import org.bukkit.entity.LivingEntity;

import java.util.*;

public final class PlayerData {
    public final UUID uuid;
    public String name;
    public double money, pending, speed;
    public int treadmill, plot = -1, penLevel, slotLevel, hatched;
    public String trail = "";
    public final Set<String> trails = new LinkedHashSet<>();
    public boolean locked;
    public long lastSeen;
    public final List<PetRow> pets = new ArrayList<>();
    public final List<EggRow> eggs = new ArrayList<>();

    public PlayerData(UUID u, String n) { uuid = u; name = n; }

    public double income() {
        double s = 0;
        for (PetRow p : pets) s += p.income();
        return s;
    }

    public static final class PetRow {
        public long id;
        public String type;
        public String mutation = "NONE";
        public double size = 1;
        public LivingEntity ent;

        public double income() {
            GameData.EggType t = GameData.EGGS.get(type);
            if (t == null) return 0;
            return t.income() * GameData.mut(mutation).mult * GameData.sizeMult(size) * GameData.incomeMult;
        }
    }

    public static final class EggRow {
        public String id, type, state;
        public double size = 1;
        public int slot = -1;
        public long readyAt;
    }
}
