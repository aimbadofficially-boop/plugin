package dev.stealanegg;

import dev.stealanegg.GameData.*;
import dev.stealanegg.PlayerData.PetRow;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class Menus {
    public static void trails(StealAnEgg pl, Player p, PlayerData pd) {
        Gui g = new Gui(2, "&dTrails &8- &7Wallet: &a$" + Util.fmt(pd.money));
        int slot = 0;
        for (TrailDef t : GameData.TRAILS) {
            boolean owned = pd.trails.contains(t.id());
            boolean eq = t.id().equals(pd.trail);
            String state = owned ? (eq ? "&aEquipped - click to unequip" : "&eClick to equip")
                    : (pd.money >= t.cost() ? "&aClick to buy for $" + Util.fmt(t.cost()) : "&cPrice: $" + Util.fmt(t.cost()));
            g.set(slot++, Gui.item(t.icon(), t.rarity().color + t.name(), "&7Rarity: " + t.rarity().label(),
                    "&7Treadmill Speed: &bx" + t.mult(), "", state), e -> {
                if (owned) pd.trail = eq ? "" : t.id();
                else if (pd.money >= t.cost()) {
                    pd.money -= t.cost();
                    pd.trails.add(t.id());
                    pd.trail = t.id();
                    p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
                    pl.db.save(pd);
                } else {
                    p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    return;
                }
                if (pd.plot >= 0) pl.bases.refreshPlot(pd.plot);
                trails(pl, p, pd);
            });
        }
        p.openInventory(g.getInventory());
    }

    public static void upgrades(StealAnEgg pl, Player p, PlayerData pd) {
        Gui g = new Gui(3, "&bBase Upgrades &8- &7Wallet: &a$" + Util.fmt(pd.money));
        int maxPen = pl.getConfig().getInt("upgrades.pen.max-level", 12);
        int maxSlot = Math.min(pl.getConfig().getInt("upgrades.incubator.max-level", 7), 9 - pl.getConfig().getInt("settings.base-incubator-slots", 2));
        double pc = pl.bases.penCost(pd), sc = pl.bases.slotCost(pd);
        g.set(11, Gui.item(Material.OAK_FENCE, "&aPet Capacity",
                "&7Pets: &f" + pd.pets.size() + "/" + pl.bases.petCap(pd), "&7Level: &f" + pd.penLevel + "/" + maxPen,
                "", pd.penLevel >= maxPen ? "&6MAX" : "&eClick: +1 pet slot for $" + Util.fmt(pc)), e -> {
            if (pd.penLevel >= maxPen || pd.money < pc) { p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f); return; }
            pd.money -= pc; pd.penLevel++;
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            pl.db.save(pd); pl.bases.refreshPlot(pd.plot); upgrades(pl, p, pd);
        });
        g.set(13, Gui.item(Material.SEA_LANTERN, "&bEgg Incubators",
                "&7Slots: &f" + pl.bases.slots(pd) + "/9", "", pd.slotLevel >= maxSlot ? "&6MAX" : "&eClick: +1 incubator for $" + Util.fmt(sc)), e -> {
            if (pd.slotLevel >= maxSlot || pd.money < sc) { p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f); return; }
            pd.money -= sc; pd.slotLevel++;
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            pl.db.save(pd); pl.bases.refreshPlot(pd.plot); upgrades(pl, p, pd);
        });
        TreadmillDef cur = GameData.TREADMILLS.get(Math.min(pd.treadmill, GameData.TREADMILLS.size() - 1));
        boolean hasNext = pd.treadmill + 1 < GameData.TREADMILLS.size();
        TreadmillDef nx = hasNext ? GameData.TREADMILLS.get(pd.treadmill + 1) : null;
        g.set(15, Gui.item(cur.stripe(), "&eTreadmill: " + cur.name(), "&7Speed per step: &b+" + Util.fmt(cur.perStep()), "",
                hasNext ? "&eClick: " + nx.name() + " (+" + Util.fmt(nx.perStep()) + "/step) for $" + Util.fmt(nx.cost()) : "&6MAX TIER"), e -> {
            pl.bases.buyTreadmill(p, pd);
            upgrades(pl, p, pd);
        });
        p.openInventory(g.getInventory());
    }

    public static void pets(StealAnEgg pl, Player p, PlayerData pd) {
        Gui g = new Gui(6, "&aYour Pets &8(" + pd.pets.size() + "/" + pl.bases.petCap(pd) + ")");
        List<PetRow> list = new ArrayList<>(pd.pets);
        list.sort(Comparator.comparingDouble(PetRow::income).reversed());
        int slot = 0;
        for (PetRow pr : list) {
            if (slot >= 54) break;
            EggType t = GameData.EGGS.get(pr.type);
            if (t == null) continue;
            Mutation m = GameData.mut(pr.mutation);
            g.set(slot++, Gui.item(Ents.eggMat(t.mob()), pl.bases.petName(pr),
                    "&7Rarity: " + t.rarity().label(), "&7Mutation: " + (m == Mutation.NONE ? "&8None" : m.color + m.label),
                    "&7Size: &f" + GameData.sizeName(pr.size), "", "&cShift-click to release for $" + Util.fmt(pr.income() * 60)), e -> {
                if (e.getClick() == ClickType.SHIFT_LEFT || e.getClick() == ClickType.SHIFT_RIGHT) {
                    pl.bases.releasePet(p, pd, pr);
                    pets(pl, p, pd);
                }
            });
        }
        p.openInventory(g.getInventory());
    }
}
