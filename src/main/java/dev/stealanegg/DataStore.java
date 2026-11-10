package dev.stealanegg;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class DataStore {
    private final StealAnEgg pl;
    private final File file;
    private final Map<UUID, PlayerData> map = new HashMap<>();
    private boolean mapBuilt;

    public DataStore(StealAnEgg pl) {
        this.pl = pl;
        this.file = new File(pl.getDataFolder(), "data.yml");
    }

    public void load() {
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        mapBuilt = yml.getBoolean("map-built", false);
        ConfigurationSection players = yml.getConfigurationSection("players");
        if (players == null) return;
        for (String key : players.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                ConfigurationSection s = players.getConfigurationSection(key);
                if (s != null) map.put(id, PlayerData.read(id, s));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public synchronized void save() {
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("map-built", mapBuilt);
        for (PlayerData d : map.values()) {
            d.write(yml.createSection("players." + d.id));
        }
        try {
            pl.getDataFolder().mkdirs();
            yml.save(file);
        } catch (IOException e) {
            pl.getLogger().warning("Could not save data.yml: " + e.getMessage());
        }
    }

    public PlayerData get(UUID id) {
        return map.get(id);
    }

    public PlayerData getOrCreate(Player p) {
        PlayerData d = map.get(p.getUniqueId());
        if (d == null) {
            d = new PlayerData(p.getUniqueId(), p.getName());
            d.cash = pl.getConfig().getDouble("start-cash", 100);
            map.put(p.getUniqueId(), d);
        }
        d.name = p.getName();
        return d;
    }

    public boolean isMapBuilt() {
        return mapBuilt;
    }

    public void setMapBuilt(boolean b) {
        mapBuilt = b;
    }
}
