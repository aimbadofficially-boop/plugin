package dev.stealanegg;

import org.bukkit.GameMode;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class GameListener implements Listener {
    private final StealAnEgg pl;

    public GameListener(StealAnEgg pl) {
        this.pl = pl;
    }

    private boolean inGame(Entity e) {
        return e.getWorld() == pl.world;
    }

    private boolean isBuilder(Player p) {
        return p.hasPermission("stealanegg.build") && p.getGameMode() == GameMode.CREATIVE;
    }

    // ------------------------------------------------------------------ join / quit

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        pl.game.join(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (inGame(e.getPlayer())) pl.game.quit(e.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        e.setRespawnLocation(pl.game.spawn());
    }

    // ------------------------------------------------------------------ eggs: tap / click

    @EventHandler
    public void onInteractEntity(PlayerInteractAtEntityEvent e) {
        if (!(e.getRightClicked() instanceof ArmorStand as) || !pl.game.isOurStand(as)) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;
        pl.game.clickStand(e.getPlayer(), as);
    }

    @EventHandler
    public void onArmorStand(PlayerArmorStandManipulateEvent e) {
        if (inGame(e.getPlayer()) && !isBuilder(e.getPlayer())) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ damage

    @EventHandler
    public void onHit(EntityDamageByEntityEvent e) {
        if (!inGame(e.getEntity())) return;
        e.setCancelled(true); // there is no real damage in this game

        if (e.getDamager() instanceof Player attacker) {
            // Bedrock touch controls often "attack" when you tap an entity - treat that as a click
            if (e.getEntity() instanceof ArmorStand as && pl.game.isOurStand(as)) {
                pl.game.clickStand(attacker, as);
                return;
            }
            if (e.getEntity() instanceof Player victim && pl.game.isKnockStick(attacker.getInventory().getItemInMainHand())) {
                pl.game.knock(attacker, victim);
            }
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (!inGame(e.getEntity())) return;
        if (e.getEntity() instanceof Player || e.getEntity() instanceof ArmorStand) e.setCancelled(true);
    }

    @EventHandler
    public void onSnowball(ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof Snowball) || !inGame(e.getEntity())) return;
        if (e.getHitEntity() instanceof Player victim) pl.game.freeze(victim);
    }

    // ------------------------------------------------------------------ shops

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getClickedBlock() == null || !inGame(e.getPlayer())) return;
        if (e.getHand() != null && e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK && e.getAction() != Action.LEFT_CLICK_BLOCK) return;
        Layout.Stall s = pl.game.stallOf(e.getClickedBlock());
        if (s == null) return;
        e.setCancelled(true);
        pl.guis.open(e.getPlayer(), s);
    }

    @EventHandler
    public void onInvClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || !inGame(p)) return;
        if (e.getView().getTopInventory().getHolder() instanceof Guis.Holder h) {
            e.setCancelled(true);
            if (e.getClickedInventory() == e.getView().getTopInventory()) pl.guis.click(p, h, e.getSlot());
            return;
        }
        if (!isBuilder(p)) e.setCancelled(true); // keep the egg helmet + kit in place
    }

    @EventHandler
    public void onInvDrag(InventoryDragEvent e) {
        if (e.getWhoClicked() instanceof Player p && inGame(p) && !isBuilder(p)) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ world protection

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        if (inGame(e.getPlayer()) && !isBuilder(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        if (inGame(e.getPlayer()) && !isBuilder(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (inGame(e.getPlayer()) && !isBuilder(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (inGame(e.getPlayer()) && !isBuilder(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler
    public void onFood(FoodLevelChangeEvent e) {
        if (inGame(e.getEntity())) e.setCancelled(true);
    }

    @EventHandler
    public void onSpawn(CreatureSpawnEvent e) {
        if (!inGame(e.getEntity())) return;
        if (e.getEntity() instanceof ArmorStand) return;
        e.setCancelled(true);
    }
}
