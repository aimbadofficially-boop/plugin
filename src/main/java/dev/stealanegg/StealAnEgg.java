package dev.stealanegg;

import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.util.*;

public final class StealAnEgg extends JavaPlugin {
    public Db db;
    public World world;
    public final Map<UUID, PlayerData> players = new HashMap<>();
    public final Set<UUID> bypass = new HashSet<>();
    public WorldBuilder builder;
    public BaseManager bases;
    public NestManager nests;
    public Training training;
    public boolean ready;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Util.init(this);
        GameData.load(getConfig());
        getDataFolder().mkdirs();
        try {
            db = new Db(new File(getDataFolder(), "data.db"), getLogger());
        } catch (Exception ex) {
            getLogger().severe("SQLite failed: " + ex);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        db.resetHatching();
        world = Bukkit.getWorld(Layout.WORLD);
        if (world == null) world = new WorldCreator(Layout.WORLD).generator(new Layout.VoidGen()).createWorld();
        if (world == null) {
            getLogger().severe("Could not create world " + Layout.WORLD);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        Commands cmd = new Commands(this);
        getCommand("sae").setExecutor(cmd);
        getCommand("sae").setTabCompleter(cmd);
        getServer().getPluginManager().registerEvents(new GameListener(this), this);
        Bukkit.getScheduler().runTask(this, this::startup);
    }

    private void startup() {
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        world.setGameRule(GameRule.KEEP_INVENTORY, true);
        world.setGameRule(GameRule.MOB_GRIEFING, false);
        world.setGameRule(GameRule.DO_FIRE_TICK, false);
        world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
        world.setGameRule(GameRule.RANDOM_TICK_SPEED, 0);
        world.setTime(6000);
        world.setSpawnLocation(Layout.spawn(world));

        if (getConfig().getBoolean("settings.forceload-map", true)) {
            int last = (Layout.BIOME_START + Layout.BIOMES * Layout.BIOME_LEN) / 16 + 2;
            for (int cz = -4; cz <= last; cz++) {
                int from = cz <= 1 ? -7 : -3, to = cz <= 1 ? 6 : 2;
                for (int cx = from; cx <= to; cx++) world.setChunkForceLoaded(cx, cz, true);
            }
        }
        builder = new WorldBuilder(world);
        if (!"1".equals(db.meta("map_v"))) {
            getLogger().info("Building the T-shaped map for the first time (this takes a few seconds)...");
            builder.buildAll();
            db.setMeta("map_v", "1");
            getLogger().info("Map built.");
        }
        for (Entity e : world.getEntities()) if (Util.type(e) != null) e.remove();
        bases = new BaseManager(this);
        nests = new NestManager(this);
        training = new Training(this);
        bases.spawnPlotEntities();
        nests.spawnAll();

        new BukkitRunnable() {
            @Override public void run() { bases.tickSecond(); nests.tickSecond(); }
        }.runTaskTimer(this, 20, 20);
        new BukkitRunnable() {
            @Override public void run() { nests.guardianAI(); }
        }.runTaskTimer(this, 10, 10);
        new BukkitRunnable() {
            @Override public void run() { training.tick(); bases.followCarry(); }
        }.runTaskTimer(this, 2, 2);
        new BukkitRunnable() {
            @Override public void run() { bases.autosave(); }
        }.runTaskTimer(this, 600, 600);

        ready = true;
        for (Player p : Bukkit.getOnlinePlayers()) bases.join(p);
        getLogger().info("Steal An Egg is ready.");
    }

    @Override
    public void onDisable() {
        ready = false;
        if (bases != null) for (Player p : new ArrayList<>(Bukkit.getOnlinePlayers())) bases.quit(p);
        if (world != null) for (Entity e : world.getEntities()) if (Util.type(e) != null) e.remove();
        if (db != null) db.close();
    }
}
