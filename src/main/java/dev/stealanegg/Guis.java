package dev.stealanegg;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** Chest-style menus. These work for Bedrock (Geyser turns them into normal chest screens). */
public final class Guis {
    public enum Type { PVP, TRAIL, INDEX }

    public static final class Holder implements InventoryHolder {
        public final Type type;
        private Inventory inv;

        Holder(Type type) {
            this.type = type;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inv;
        }
    }

    private final StealAnEgg pl;

    public Guis(StealAnEgg pl) {
        this.pl = pl;
    }

    private static ItemStack item(Material m, int amount, String name, String... lore) {
        ItemStack it = new ItemStack(m, amount);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Msg.c(name));
        List<Component> l = new ArrayList<>();
        for (String s : lore) l.add(Msg.c(s));
        meta.lore(l);
        it.setItemMeta(meta);
        return it;
    }

    private static ItemStack glass() {
        return item(Material.GRAY_STAINED_GLASS_PANE, 1, " ");
    }

    private Inventory create(Type t, String title) {
        Holder h = new Holder(t);
        Inventory inv = Bukkit.createInventory(h, 27, Msg.c(title));
        h.inv = inv;
        for (int i = 0; i < 27; i++) inv.setItem(i, glass());
        return inv;
    }

    // ------------------------------------------------------------------ open

    public void open(Player p, Layout.Stall stall) {
        switch (stall.id()) {
            case "pvp" -> openPvp(p);
            case "trail" -> openTrail(p);
            case "index" -> openIndex(p);
        }
    }

    public void openPvp(Player p) {
        PlayerData d = pl.data.get(p.getUniqueId());
        if (d == null) return;
        Inventory inv = create(Type.PVP, "&c&lPVP SHOP");
        var cfg = pl.getConfig();
        int stick = cfg.getInt("shop.knock-stick-price", 500);
        int snow = cfg.getInt("shop.snowball-price", 250);
        int shield = cfg.getInt("shop.shield-price", 1000);
        int secs = cfg.getInt("shop.shield-seconds", 30);

        inv.setItem(11, d.knockStick
                ? item(Material.STICK, 1, "&c&lKnock Stick", "&aOwned", "&7Hit egg carriers to knock", "&7the egg loose!")
                : item(Material.STICK, 1, "&c&lKnock Stick", "&7Hit egg carriers to knock", "&7the egg loose!", "", "&6Price: $" + stick, "&eClick to buy"));
        inv.setItem(13, item(Material.SNOWBALL, 3, "&b&lFreeze Snowballs &7(x3)", "&7Slows whoever you hit", "", "&6Price: $" + snow, "&eClick to buy"));
        inv.setItem(15, item(Material.SHIELD, 1, "&9&lEgg Shield", "&7Nobody can steal from you", "&7for &f" + secs + "s", "", "&6Price: $" + shield, "&eClick to buy"));
        inv.setItem(22, item(Material.GOLD_INGOT, 1, "&aCash: &6$" + Msg.fmt(d.cash)));
        p.openInventory(inv);
    }

    public void openTrail(Player p) {
        PlayerData d = pl.data.get(p.getUniqueId());
        if (d == null) return;
        Inventory inv = create(Type.TRAIL, "&b&lTRAIL SHOP");
        Trail[] all = Trail.values();
        for (int i = 1; i < all.length; i++) {
            Trail t = all[i];
            String status;
            if (d.trail == t) status = "&a&lEQUIPPED";
            else if (d.ownedTrails.contains(t)) status = "&eClick to equip";
            else status = "&6Price: $" + Msg.fmt(t.price) + " &7- click to buy";
            inv.setItem(9 + i, item(t.icon, 1, "&b&l" + t.display,
                    "&7Speed bonus: &a+" + (int) Math.round(t.bonus * 100) + "%", "", status));
        }
        inv.setItem(16, item(Material.BARRIER, 1, "&cRemove trail", d.trail == Trail.NONE ? "&aNo trail equipped" : "&eClick to unequip"));
        inv.setItem(22, item(Material.GOLD_INGOT, 1, "&aCash: &6$" + Msg.fmt(d.cash)));
        p.openInventory(inv);
    }

    public void openIndex(Player p) {
        PlayerData d = pl.data.get(p.getUniqueId());
        if (d == null) return;
        Inventory inv = create(Type.INDEX, "&6&lEGG INDEX");
        EggType[] all = EggType.values();
        for (int i = 0; i < all.length; i++) {
            EggType t = all[i];
            if (d.discovered.contains(t)) {
                inv.setItem(11 + i, item(t.material, 1, t.colored(),
                        "&7Income: &a+$" + Msg.fmt(t.income) + "/s", "&7Cost: &6$" + Msg.fmt(t.cost), "&aDiscovered"));
            } else {
                inv.setItem(11 + i, item(Material.GRAY_DYE, 1, "&8???", "&7Own this egg to discover it", "&7Cost: &6$" + Msg.fmt(t.cost)));
            }
        }
        double bonus = pl.getConfig().getDouble("income.index-bonus-per-egg-type", 0.05) * d.discovered.size() * 100;
        inv.setItem(22, item(Material.BOOK, 1, "&eIndex bonus: &a+" + Math.round(bonus) + "% income",
                "&7Discovered " + d.discovered.size() + "/" + all.length));
        p.openInventory(inv);
    }

    // ------------------------------------------------------------------ clicks

    public void click(Player p, Holder h, int slot) {
        PlayerData d = pl.data.get(p.getUniqueId());
        if (d == null) return;
        switch (h.type) {
            case PVP -> clickPvp(p, d, slot);
            case TRAIL -> clickTrail(p, d, slot);
            case INDEX -> {}
        }
    }

    private void clickPvp(Player p, PlayerData d, int slot) {
        var cfg = pl.getConfig();
        if (slot == 11) {
            if (d.knockStick) {
                Msg.send(p, "&7You already own the Knock Stick.");
                return;
            }
            if (pl.game.charge(p, d, cfg.getInt("shop.knock-stick-price", 500))) {
                d.knockStick = true;
                pl.game.giveKnockStick(p);
                Msg.send(p, "&aBought the &cKnock Stick&a!");
            }
        } else if (slot == 13) {
            if (pl.game.charge(p, d, cfg.getInt("shop.snowball-price", 250))) {
                p.getInventory().addItem(new ItemStack(Material.SNOWBALL, 3));
                Msg.send(p, "&aBought 3 Freeze Snowballs!");
            }
        } else if (slot == 15) {
            if (pl.game.charge(p, d, cfg.getInt("shop.shield-price", 1000))) {
                pl.game.activateShield(p, d, cfg.getInt("shop.shield-seconds", 30));
            }
        } else {
            return;
        }
        openPvp(p);
    }

    private void clickTrail(Player p, PlayerData d, int slot) {
        Trail[] all = Trail.values();
        Trail picked = null;
        if (slot == 16) picked = Trail.NONE;
        else if (slot >= 10 && slot < 10 + all.length - 1) picked = all[slot - 9];
        if (picked == null) return;

        if (!d.ownedTrails.contains(picked)) {
            if (!pl.game.charge(p, d, picked.price)) return;
            d.ownedTrails.add(picked);
            Msg.send(p, "&aBought the &b" + picked.display + "&a!");
        }
        d.trail = picked;
        pl.game.applySpeed(p, d);
        Msg.send(p, picked == Trail.NONE ? "&7Trail removed." : "&aEquipped &b" + picked.display + "&a (+" + (int) Math.round(picked.bonus * 100) + "% speed)");
        openTrail(p);
    }
}
