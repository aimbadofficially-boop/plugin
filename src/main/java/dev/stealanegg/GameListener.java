package dev.stealanegg;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class GameListener implements Listener {
    private final StealAnEgg pl;
    private final Map<UUID, Long> click = new HashMap<>();

    public GameListener(StealAnEgg pl) { this.pl = pl; }

    private boolean inWorld(Entity e) { return e.getWorld().equals(pl.world); }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        e.joinMessage(null);
        Player p = e.getPlayer();
        if (pl.ready) pl.bases.join(p);
        else pl.getServer().getScheduler().runTaskLater(pl, () -> { if (p.isOnline() && pl.ready) pl.bases.join(p); }, 40L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        e.quitMessage(null);
        if (pl.ready) pl.bases.quit(e.getPlayer());
    }

    private boolean dedupe(Player p) {
        long now = System.currentTimeMillis();
        Long l = click.get(p.getUniqueId());
        if (l != null && now - l < 150) return false;
        click.put(p.getUniqueId(), now);
        return true;
    }

    private void handle(Player p, Entity target) {
        if (!pl.ready || !dedupe(p)) return;
        if (target instanceof Player victim) { pl.bases.stealFromPlayer(p, victim); return; }
        String type = Util.type(target);
        if (type == null) return;
        String data = Util.data(target);
        switch (type) {
            case "station" -> pl.bases.station(p, data);
            case "slot" -> pl.bases.slotClick(p, data);
            case "nest" -> pl.nests.steal(p, Integer.parseInt(data));
            case "pet" -> p.sendActionBar(Util.c("&7A pet from a base. Use the Pets menu to manage pets."));
            default -> {}
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        if (!inWorld(e.getRightClicked())) return;
        if (e.getHand() != EquipmentSlot.HAND) { e.setCancelled(true); return; }
        Entity t = e.getRightClicked();
        if (t instanceof Player || Util.type(t) != null) {
            e.setCancelled(true);
            handle(e.getPlayer(), t);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!inWorld(e.getEntity())) return;
        Entity v = e.getEntity();
        if (e.getDamager() instanceof Player p) {
            if (v instanceof Player || Util.type(v) != null) {
                e.setCancelled(true);
                if (v instanceof Interaction) handle(p, v);
                else if (v instanceof Player) handle(p, v);
            }
        }
    }

    @EventHandler
    public void onAnyDamage(EntityDamageEvent e) {
        if (!inWorld(e.getEntity())) return;
        if (e.getEntity() instanceof Player && (e.getCause() == EntityDamageEvent.DamageCause.FALL
                || e.getCause() == EntityDamageEvent.DamageCause.DROWNING)) e.setCancelled(true);
        if (Util.type(e.getEntity()) != null) e.setCancelled(true);
    }

    @EventHandler
    public void onTarget(EntityTargetLivingEntityEvent e) {
        if (!inWorld(e.getEntity())) return;
        String t = Util.type(e.getEntity());
        if ("pet".equals(t)) e.setCancelled(true);
        if ("guardian".equals(t) && !(e.getTarget() instanceof Player)) e.setCancelled(true);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Location to = e.getTo(), from = e.getFrom();
        if (to == null) return;
        if (to.getBlockX() == from.getBlockX() && to.getBlockY() == from.getBlockY() && to.getBlockZ() == from.getBlockZ()) return;
        Player p = e.getPlayer();
        if (!pl.ready || !p.getWorld().equals(pl.world)) return;
        PlayerData pd = pl.players.get(p.getUniqueId());
        if (pd == null) return;
        if (to.getY() < 40) {
            p.teleport(pl.bases.respawnLoc(pd));
            return;
        }
        boolean bypass = pl.bypass.contains(p.getUniqueId());
        if (!bypass) {
            double req = GameData.requiredAt(to.getZ());
            if (req > pd.speed && to.getZ() > from.getZ() - 0.001 && GameData.requiredAt(from.getZ()) <= pd.speed) {
                e.setCancelled(true);
                p.sendActionBar(Util.c("&cYou need &b" + Util.fmt(req) + " Speed &cto go further! (you have " + Util.fmt(pd.speed) + ")"));
                return;
            }
        }
        if (Layout.isSafe(to) && !Layout.isSafe(from)) {
            p.setHealth(p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue());
            p.setFoodLevel(20);
        }
        int plot = Layout.plotAt(to);
        if (plot >= 0) {
            BaseManager.Plot pt = pl.bases.plots[plot];
            if (pt.owner != null && !pt.owner.equals(p.getUniqueId())) {
                PlayerData owner = pl.players.get(pt.owner);
                if (owner != null && owner.locked && !bypass && Layout.plotAt(from) != plot) {
                    e.setCancelled(true);
                    p.sendActionBar(Util.c("&cThis base is locked!"));
                }
            } else if (plot == pd.plot && Layout.dOf(plot, to) >= 1) pl.bases.tryDeposit(p, pd);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        if (!inWorld(p)) return;
        e.deathMessage(null);
        e.setKeepInventory(true);
        e.getDrops().clear();
        e.setKeepLevel(true);
        e.setDroppedExp(0);
        PlayerData pd = pl.players.get(p.getUniqueId());
        if (pd != null) pl.bases.loseCarry(p, pd);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        PlayerData pd = pl.players.get(e.getPlayer().getUniqueId());
        e.setRespawnLocation(pl.bases.respawnLoc(pd));
    }

    @EventHandler
    public void onGui(InventoryClickEvent e) {
        if (e.getInventory().getHolder() instanceof Gui g) {
            e.setCancelled(true);
            if (e.getClickedInventory() == e.getInventory()) g.click(e);
        }
    }

    @EventHandler
    public void onGuiDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Gui) e.setCancelled(true);
    }

    private boolean admin(Player p) { return p.getGameMode() == GameMode.CREATIVE && p.hasPermission("stealanegg.admin"); }

    @EventHandler public void onBreak(BlockBreakEvent e) { if (inWorld(e.getPlayer()) && !admin(e.getPlayer())) e.setCancelled(true); }
    @EventHandler public void onPlace(BlockPlaceEvent e) { if (inWorld(e.getPlayer()) && !admin(e.getPlayer())) e.setCancelled(true); }
    @EventHandler public void onDrop(PlayerDropItemEvent e) { if (inWorld(e.getPlayer())) e.setCancelled(true); }
    @EventHandler public void onFood(FoodLevelChangeEvent e) { if (inWorld(e.getEntity())) e.setCancelled(true); }
    @EventHandler public void onIgnite(BlockIgniteEvent e) { if (e.getBlock().getWorld().equals(pl.world)) e.setCancelled(true); }
    @EventHandler public void onBurn(BlockBurnEvent e) { if (e.getBlock().getWorld().equals(pl.world)) e.setCancelled(true); }
    @EventHandler public void onExplode(EntityExplodeEvent e) { if (inWorld(e.getEntity())) e.blockList().clear(); }
    @EventHandler public void onMobChange(EntityChangeBlockEvent e) { if (inWorld(e.getEntity()) && !(e.getEntity() instanceof Player)) e.setCancelled(true); }

    @EventHandler
    public void onSpawn(CreatureSpawnEvent e) {
        if (!inWorld(e.getEntity())) return;
        switch (e.getSpawnReason()) {
            case DEFAULT, CUSTOM -> {}
            default -> e.setCancelled(true);
        }
    }
}
