package dev.stealanegg;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Random;

public final class StealAnEgg extends JavaPlugin {
    public static final String WORLD_NAME = "steal_an_egg";

    public World world;
    public DataStore data;
    public Game game;
    public Guis guis;
    private BeltAnimator belts;

    /** Completely empty world; MapBuilder fills it in. */
    private static final class VoidGenerator extends ChunkGenerator {
        @Override
        public Location getFixedSpawnLocation(@NotNull World world, @NotNull Random random) {
            return new Location(world, 0.5, Layout.FLOOR + 1, 4.5);
        }
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        data = new DataStore(this);
        data.load();

        WorldCreator wc = new WorldCreator(WORLD_NAME);
        wc.generator(new VoidGenerator());
        wc.generateStructures(false);
        world = wc.createWorld();
        if (world == null) {
            getLogger().severe("Could not create the game world - disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        world.getWorldBorder().setCenter(0, -30);
        world.getWorldBorder().setSize(400);

        if (world.getBlockAt(0, Layout.FLOOR, 0).getType() == Material.AIR) {
            getLogger().info("Generating the Steal an Egg map...");
            new MapBuilder(this, world).build();
            getLogger().info("Map done.");
        }

        game = new Game(this);
        guis = new Guis(this);
        game.start();
        belts = new BeltAnimator(this);
        belts.start();
        getServer().getPluginManager().registerEvents(new GameListener(this), this);

        Bukkit.getScheduler().runTaskTimer(this, () -> data.save(), 20L * 300, 20L * 300);

        for (Player p : Bukkit.getOnlinePlayers()) game.join(p);

        if (Bukkit.getPluginManager().isPluginEnabled("Geyser-Spigot")
                || Bukkit.getPluginManager().isPluginEnabled("floodgate")) {
            getLogger().info("Geyser/Floodgate detected - Bedrock players are supported.");
        }
    }

    @Override
    public void onDisable() {
        if (game != null) game.shutdown();
        if (data != null) data.save();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        switch (cmd.getName().toLowerCase()) {
            case "base" -> {
                if (sender instanceof Player p) {
                    p.teleport(game.baseSpawn(p) != null ? game.baseSpawn(p) : game.spawn());
                }
                return true;
            }
            case "spawn" -> {
                if (sender instanceof Player p) p.teleport(game.spawn());
                return true;
            }
            case "sae" -> {
                if (!sender.hasPermission("stealanegg.admin")) {
                    Msg.send(sender, "&cNo permission.");
                    return true;
                }
                if (args.length == 0) {
                    Msg.send(sender, "&e/sae rebuildmap &7| &e/sae give <player> <cash> &7| &e/sae speed <player> <points>");
                    return true;
                }
                switch (args[0].toLowerCase()) {
                    case "rebuildmap" -> {
                        Msg.send(sender, "&eRebuilding map (players keep their eggs, visuals are redrawn on rejoin)...");
                        new MapBuilder(this, world).build();
                        Msg.send(sender, "&aDone.");
                    }
                    case "give" -> {
                        Player t = args.length > 2 ? Bukkit.getPlayerExact(args[1]) : null;
                        if (t == null) {
                            Msg.send(sender, "&cUsage: /sae give <player> <cash>");
                            return true;
                        }
                        try {
                            data.getOrCreate(t).cash += Double.parseDouble(args[2]);
                            Msg.send(sender, "&aGave cash to " + t.getName());
                        } catch (NumberFormatException ex) {
                            Msg.send(sender, "&cNot a number.");
                        }
                    }
                    case "speed" -> {
                        Player t = args.length > 2 ? Bukkit.getPlayerExact(args[1]) : null;
                        if (t == null) {
                            Msg.send(sender, "&cUsage: /sae speed <player> <points>");
                            return true;
                        }
                        try {
                            PlayerData d = data.getOrCreate(t);
                            d.speedPoints += Double.parseDouble(args[2]);
                            game.applySpeed(t, d);
                            Msg.send(sender, "&aGave speed points to " + t.getName());
                        } catch (NumberFormatException ex) {
                            Msg.send(sender, "&cNot a number.");
                        }
                    }
                    default -> Msg.send(sender, "&cUnknown subcommand.");
                }
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    @Nullable
    public PlayerData dataOf(Player p) {
        return data.get(p.getUniqueId());
    }
}
